"""Run the bounded 1.1.0 live-server verification for one loader/version branch."""

from __future__ import annotations

import argparse
import json
import os
import re
import signal
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path
from queue import Empty, Queue


READY_RE = re.compile(r'Done \([0-9.]+s\)! For help, type "help"')
CRASH_RE = re.compile(r'(CrashReport|Fatal error|Exception in server thread|OutOfMemoryError)')
VERSION_RE = re.compile(r'HeapHammer v([^ ]+)')
PLAYER_RETENTION_RE = re.compile(
    r"\[TestMod-OmniTrack\] Status: enabled=true, chunks=\d+, entities=\d+, ticks=\d+, players=(\d+)"
)
PLAYER_FIXTURE_STATUS_RE = re.compile(
    r"\[TestMod-PlayerSessionLeak\] mode=(\w+), retained_sessions=(\d+), joins=(\d+), disconnects=(\d+)"
)
PERSISTENT_FIXTURE_STATUS_RE = re.compile(
    r"\[TestMod-PersistentEntityLeak\] mode=(\w+), retained_entities=(\d+), loads=(\d+), unloads=(\d+)"
)
FIXTURE_STATUS_RES = {
    "fabric": (
        re.compile(r"\[TestMod-ChunkCache\] Status: enabled=true, cached_chunks=(\d+)"),
        re.compile(r"\[TestMod-OmniTrack\] Status: enabled=true, chunks=(\d+), entities=(\d+), ticks=(\d+)(?:, players=\d+)?"),
    ),
    "forge1122": (
        re.compile(r"\[HHLeak-Forge1122\] retained_chunks=(\d+)"),
    ),
    "forge1710": (
        re.compile(r"\[HHLeak-Forge1710\] retained_chunks=(\d+)"),
    ),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mc-version", required=True)
    parser.add_argument("--loader", required=True, choices=("fabric", "forge"))
    parser.add_argument(
        "--leak-fixture",
        required=True,
        choices=("fabric", "forge1122", "forge1710"),
    )
    parser.add_argument("--expected-mod-version", required=True)
    parser.add_argument("--boot-timeout", type=int, default=480)
    parser.add_argument("--run-timeout", type=int, default=180)
    return parser.parse_args()


def find_primary_jar(root: Path) -> Path:
    jars = sorted(
        p
        for p in (root / "build" / "libs").glob("*.jar")
        if "sources" not in p.name and "dev" not in p.name
    )
    if not jars:
        raise RuntimeError("No primary mod jar found in build/libs")
    return jars[0]


def fixture_jars(root: Path, fixture: str) -> list[Path]:
    if fixture == "fabric":
        required_names = (
            "testmod-leak-chunkcache-1.0.0.jar",
            "testmod-leak-omnitrack-1.0.0.jar",
        )
        advanced_names = (
            (
                "testmod-leak-playersession-1.0.0.jar",
                "testmod-leak-persistententity-1.0.0.jar",
            )
            if is_canonical_fabric_build(root, fixture)
            else ()
        )
    elif fixture == "forge1122":
        required_names = ("testmod-leak-forge1122-1.0.0.jar",)
        advanced_names = ()
    else:
        required_names = ("testmod-leak-forge1710-1.0.0.jar",)
        advanced_names = ()

    result = []
    for name in required_names:
        path = root / "build" / "testmods" / name
        if not path.is_file():
            raise RuntimeError(f"Required live leak fixture was not built: {path}")
        result.append(path)
    for name in advanced_names:
        path = root / "build" / "testmods" / name
        if path.is_file():
            result.append(path)
        elif is_canonical_fabric_build(root, fixture):
            raise RuntimeError(f"Required canonical advanced fixture was not built: {path}")
    return result


def is_canonical_fabric_build(root: Path, fixture: str) -> bool:
    """Advanced fixtures are intentionally required only by the 1.21.1 root build."""
    if fixture != "fabric" or not (root / "gradle.properties").is_file():
        return False
    properties = (root / "gradle.properties").read_text(encoding="utf-8")
    return "minecraft_version=1.21.1" in properties


def stage_mods(root: Path, loader: str, fixture: str) -> None:
    mods = root / "run" / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    for jar in mods.glob("*.jar"):
        jar.unlink()
    # Modern Fabric's runServer task does not put the built mod on its runtime
    # classpath, so stage it explicitly. RetroFuturaGradle's legacy Forge
    # launcher already adds build/libs/*; copying the primary jar there would
    # make Forge abort with DuplicateModsFoundException.
    if loader == "fabric":
        primary = find_primary_jar(root)
        shutil.copy2(primary, mods / primary.name)
    for jar in fixture_jars(root, fixture):
        shutil.copy2(jar, mods / jar.name)


def read_reports(root: Path) -> list[dict]:
    reports = []
    for path in sorted((root / "run").rglob("*.json")):
        if "report" not in str(path).lower():
            continue
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if isinstance(payload, dict) and "detection" in payload:
            reports.append({"path": str(path), "report": payload})
    return reports


def stop_process(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    try:
        process.stdin.write("stop\n")
        process.stdin.flush()
    except (BrokenPipeError, OSError):
        pass
    try:
        process.wait(timeout=30)
    except subprocess.TimeoutExpired:
        if os.name == "posix":
            os.killpg(process.pid, signal.SIGKILL)
        else:
            process.kill()
        process.wait(timeout=15)


def send(process: subprocess.Popen[str], command: str) -> None:
    print(f"[COMMAND] {command}", flush=True)
    process.stdin.write(command + "\n")
    process.stdin.flush()


def start_output_reader(process: subprocess.Popen[str]) -> Queue[str]:
    lines: Queue[str] = Queue()

    def read_output() -> None:
        for line in iter(process.stdout.readline, ""):
            lines.put(line)

    threading.Thread(target=read_output, name="server-output", daemon=True).start()
    return lines


def has_new_report(root: Path, before: set[str]) -> bool:
    return any(item["path"] not in before for item in read_reports(root))


def take_line(lines: Queue[str], deadline: float) -> str | None:
    while time.monotonic() < deadline:
        try:
            return lines.get(timeout=min(0.5, max(0.01, deadline - time.monotonic())))
        except Empty:
            continue
    return None


def main() -> int:
    args = parse_args()
    root = Path.cwd()
    evidence = root / "build" / "live-server"
    evidence.mkdir(parents=True, exist_ok=True)
    disposable_world = f"../build/live-server/world-{os.getpid()}"
    (root / "run").mkdir(parents=True, exist_ok=True)
    (root / "run" / "eula.txt").write_text(
        "# Disposable CI server only; this file is never committed.\neula=true\n",
        encoding="utf-8",
    )
    (root / "run" / "server.properties").write_text(
        "# Disposable CI server only; this file is never committed.\n"
        "online-mode=false\n"
        f"level-name={disposable_world}\n"
        "pause-when-empty-seconds=0\n",
        encoding="utf-8",
    )
    stage_mods(root, args.loader, args.leak_fixture)

    advanced_fixtures = False
    if args.loader == "fabric":
        advanced_fixtures = is_canonical_fabric_build(root, args.leak_fixture) and all(
            (root / "build" / "testmods" / name).is_file()
            for name in (
                "testmod-leak-playersession-1.0.0.jar",
                "testmod-leak-persistententity-1.0.0.jar",
            )
        )
        commands = [
            "hh version",
            "hh capabilities",
            "hh scenario list",
            "hh diagnostics histogram",
            "chunkcacheleak status",
            "omnitrack status",
            "hh run chunks --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true --diagnostics=retention,histogram,event-metrics,world-store",
            "hh run entities --iterations=5 --batch=15 --hold=5 --settle=10 --explicit-gc=true --diagnostics=retention,histogram,event-metrics,world-store",
            "hh run players --iterations=2 --logins-per-cycle=1 --actions=join,quit --diagnostics=event-metrics",
            "hh report show last",
            "hh cleanup",
            "chunkcacheleak status",
            "omnitrack status",
        ]
        if advanced_fixtures:
            commands[8:8] = [
                "playersessionleak status",
                "persistententityleak status",
                "playersessionleak mode leak",
                "persistententityleak mode leak",
                "hh run players --iterations=3 --logins-per-cycle=1 --actions=join,quit --explicit-gc=true --diagnostics=retention,histogram,event-metrics",
                "hh run entities --profile=persistent --iterations=5 --batch=15 --hold=5 --settle=10 --explicit-gc=true --diagnostics=retention,histogram,event-metrics,world-store",
            ]
            commands.extend([
                "playersessionleak status",
                "persistententityleak status",
                "playersessionleak reset",
                "persistententityleak reset",
            ])
    elif args.leak_fixture == "forge1122":
        commands = [
            "hh version",
            "hh capabilities",
            "hh diagnostics histogram",
            "hh run chunks --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true",
            "hh run entities --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true",
            "hh run blockentities --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true",
            "hh report show last",
            "hh cleanup",
            "hhleak1122 status",
        ]
    else:
        commands = [
            "hh version",
            "hh diagnostics histogram",
            "hh run chunks --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true",
            "hh run entities --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true",
            "hhleak1710 status",
        ]

    command_output = []
    ready = False
    observed_mod_version = None
    player_scenario_run = False
    reports_before = {str(p) for p in (root / "run").rglob("*.json")}
    gradle_wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    process = subprocess.Popen(
        [
            gradle_wrapper,
            "runServer",
            f"-Pmod_version={args.expected_mod_version}",
            "--no-daemon",
        ],
        cwd=root,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        start_new_session=(os.name == "posix"),
    )
    output_lines = start_output_reader(process)
    if args.loader == "forge":
        # Forge 1.12.2 asks this interactively before it reaches the normal
        # server console. CI runs offline and the disposable eula above is
        # already explicit, so answer the prompt deterministically. Forge
        # 1.7.10 uses the same launcher prompt.
        process.stdin.write("n\n")
        process.stdin.flush()

    deadline = time.monotonic() + args.boot_timeout
    try:
        while time.monotonic() < deadline and process.poll() is None:
            line = take_line(output_lines, deadline)
            if line is None:
                break
            command_output.append(line)
            print(line.rstrip(), flush=True)
            version_match = VERSION_RE.search(line)
            if version_match:
                observed_mod_version = version_match.group(1)
            if CRASH_RE.search(line):
                raise RuntimeError(f"Server crash signature during boot: {line.strip()}")
            if READY_RE.search(line):
                ready = True
                break
        if not ready:
            raise RuntimeError("Dedicated server did not reach the ready marker")

        time.sleep(2)
        for command in commands:
            if command.startswith("hh run players") and not any(
                "- players (v1.1):" in line for line in command_output
            ):
                print("[SKIP] players scenario is not exposed by this branch", flush=True)
                continue

            reports_before_command = {item["path"] for item in read_reports(root)}
            send(process, command)
            wait_for_report = command.startswith("hh run ")
            command_deadline = time.monotonic() + (args.run_timeout if wait_for_report else 5)
            saw_report = False
            saw_unsupported = False
            while time.monotonic() < command_deadline and process.poll() is None:
                if wait_for_report and has_new_report(root, reports_before_command):
                    saw_report = True
                    break
                line = take_line(
                    output_lines,
                    min(command_deadline, time.monotonic() + 0.5),
                )
                if line is None:
                    continue
                command_output.append(line)
                print(line.rstrip(), flush=True)
                version_match = VERSION_RE.search(line)
                if version_match:
                    observed_mod_version = version_match.group(1)
                if CRASH_RE.search(line):
                    raise RuntimeError(f"Server crash signature: {line.strip()}")
                if wait_for_report and has_new_report(root, reports_before_command):
                    # Some historical loader/logging combinations write the
                    # JSON report before forwarding the completion message to
                    # the Gradle console. The file is the authoritative signal.
                    saw_report = True
                    break
                if wait_for_report and re.search(r'unsupported by this platform|not supported', line, re.IGNORECASE):
                    saw_unsupported = True
                    break
            if wait_for_report and not saw_report and not saw_unsupported:
                raise RuntimeError(f"No report completion marker after command: {command}")
            if command.startswith("hh run players") and saw_report:
                player_scenario_run = True
            time.sleep(0.5)
    finally:
        stop_process(process)
        (evidence / "server.log").write_text("".join(command_output), encoding="utf-8")

    reports = read_reports(root)
    new_reports = [item for item in reports if item["path"] not in reports_before]
    if not new_reports:
        raise RuntimeError("No new HeapHammer report JSON was produced")
    if observed_mod_version != args.expected_mod_version:
        raise RuntimeError(
            f"Expected HeapHammer v{args.expected_mod_version}, "
            f"observed {observed_mod_version or 'no version output'}"
        )

    summary = {
        "minecraft": args.mc_version,
        "loader": args.loader,
        "fixture": args.leak_fixture,
        "modVersion": observed_mod_version,
        "reports": [],
    }
    suspicious = False
    incomplete_reports = []
    fixture_status_lines = []
    player_retention_counts = []
    player_fixture_status = []
    persistent_fixture_status = []
    advanced_player_reports = []
    advanced_persistent_reports = []
    for line in command_output:
        if any(pattern.search(line) for pattern in FIXTURE_STATUS_RES[args.leak_fixture]):
            fixture_status_lines.append(line.strip())
        player_match = PLAYER_RETENTION_RE.search(line)
        if player_match:
            player_retention_counts.append(int(player_match.group(1)))
        player_fixture_match = PLAYER_FIXTURE_STATUS_RE.search(line)
        if player_fixture_match:
            player_fixture_status.append(
                {
                    "mode": player_fixture_match.group(1),
                    "retainedSessions": int(player_fixture_match.group(2)),
                    "joins": int(player_fixture_match.group(3)),
                    "disconnects": int(player_fixture_match.group(4)),
                }
            )
        persistent_fixture_match = PERSISTENT_FIXTURE_STATUS_RE.search(line)
        if persistent_fixture_match:
            persistent_fixture_status.append(
                {
                    "mode": persistent_fixture_match.group(1),
                    "retainedEntities": int(persistent_fixture_match.group(2)),
                    "loads": int(persistent_fixture_match.group(3)),
                    "unloads": int(persistent_fixture_match.group(4)),
                }
            )
    fixture_status_observed = bool(fixture_status_lines)
    for item in new_reports:
        report = item["report"]
        detection = report.get("detection", {})
        classification = str(detection.get("classification", ""))
        status = str(report.get("status", ""))
        spec = report.get("spec", {})
        collectors = {
            str(collector).upper() for collector in spec.get("diagnosticCollectors", [])
        }
        if spec.get("scenarioId") == "players" and "RETENTION" in collectors:
            advanced_player_reports.append(item)
        if (
            spec.get("scenarioId") == "entities"
            and str(spec.get("entityProfile", "")).upper() == "PERSISTENT"
            and "RETENTION" in collectors
        ):
            advanced_persistent_reports.append(item)
        suspicious = suspicious or classification in {"SUSPICIOUS", "FAIL"}
        cleanup_values = [
            checkpoint.get("cleanupValid")
            for checkpoint in report.get("checkpoints", [])
            if isinstance(checkpoint.get("cleanupValid"), bool)
        ]
        cleanup_validation = all(cleanup_values) if cleanup_values else None
        status = str(report.get("status", ""))
        if status != "COMPLETED" or cleanup_validation is not True:
            incomplete_reports.append(
                f"{report.get('runId', item['path'])}:status={status or 'missing'},"
                f"cleanup={cleanup_validation}"
            )
        summary["reports"].append(
            {
                "path": item["path"],
                "runId": report.get("runId"),
                "status": status,
                "scenario": report.get("spec", {}).get("scenarioId"),
                "classification": classification,
                "slopeBytesPerCycle": detection.get("slopeBytesPerCycle"),
                "rSquared": detection.get("rSquared"),
                "cleanupValidation": cleanup_validation,
            }
        )
    summary["fixtureStatusObserved"] = fixture_status_observed
    summary["fixtureStatusLines"] = fixture_status_lines
    summary["advancedFixtureStatus"] = {
        "playerSession": player_fixture_status,
        "persistentEntity": persistent_fixture_status,
    }
    summary["advancedReports"] = [
        {
            "runId": item["report"].get("runId"),
            "scenario": item["report"].get("spec", {}).get("scenarioId"),
            "classification": item["report"].get("detection", {}).get("classification"),
        }
        for item in advanced_player_reports + advanced_persistent_reports
    ]
    summary["playerScenarioRun"] = player_scenario_run
    summary["playerRetentionCounts"] = player_retention_counts
    summary["leakDetectedBySlope"] = suspicious
    (evidence / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    if not fixture_status_observed:
        raise RuntimeError("Leak fixture did not report retained state")
    if player_scenario_run and not player_retention_counts:
        raise RuntimeError("Player scenario ran but the player-retention fixture did not report a count")
    if player_scenario_run and max(player_retention_counts) <= 0:
        raise RuntimeError("Player scenario ran but the player-retention fixture retained no players")
    if advanced_fixtures:
        if not player_fixture_status:
            raise RuntimeError("Canonical run did not report player-session fixture state")
        if not persistent_fixture_status:
            raise RuntimeError("Canonical run did not report persistent-entity fixture state")
        if not any(
            state["mode"] == "LEAK" and state["retainedSessions"] > 0
            for state in player_fixture_status
        ):
            raise RuntimeError("Player-session fixture reported no retained leak records")
        if not any(
            state["mode"] == "LEAK"
            and state["retainedEntities"] > 0
            and state["loads"] > 0
            and state["unloads"] > 0
            for state in persistent_fixture_status
        ):
            raise RuntimeError("Persistent-entity fixture reported no retained leak records")
        if not advanced_player_reports:
            raise RuntimeError("Canonical run did not produce an advanced player diagnostic report")
        if not advanced_persistent_reports:
            raise RuntimeError("Canonical run did not produce an advanced persistent-entity report")
        if not any(
            item["report"].get("detection", {}).get("classification") in {"SUSPICIOUS", "FAIL"}
            for item in advanced_player_reports
        ):
            raise RuntimeError("Player-session fixture report did not detect a retained leak")
        if not any(
            item["report"].get("detection", {}).get("classification") in {"SUSPICIOUS", "FAIL"}
            for item in advanced_persistent_reports
        ):
            raise RuntimeError("Persistent-entity fixture report did not detect a retained leak")
    if incomplete_reports:
        raise RuntimeError("Incomplete or uncleared report(s): " + "; ".join(incomplete_reports))
    print(json.dumps(summary, indent=2), flush=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"[LIVE MATRIX FAILURE] {exc}", file=sys.stderr, flush=True)
        sys.exit(1)
