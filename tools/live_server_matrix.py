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
REPORT_RE = re.compile(r'Report saved successfully')
CRASH_RE = re.compile(r'(CrashReport|Fatal error|Exception in server thread|OutOfMemoryError)')


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mc-version", required=True)
    parser.add_argument("--loader", required=True, choices=("fabric", "forge"))
    parser.add_argument(
        "--leak-fixture",
        required=True,
        choices=("fabric", "forge1122", "forge1710"),
    )
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
        names = (
            "testmod-leak-chunkcache-1.0.0.jar",
            "testmod-leak-omnitrack-1.0.0.jar",
        )
    elif fixture == "forge1122":
        names = ("testmod-leak-forge1122-1.0.0.jar",)
    else:
        names = ("testmod-leak-forge1710-1.0.0.jar",)

    result = []
    for name in names:
        path = root / "build" / "testmods" / name
        if not path.is_file():
            raise RuntimeError(f"Required live leak fixture was not built: {path}")
        result.append(path)
    return result


def stage_mods(root: Path, fixture: str) -> None:
    mods = root / "run" / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    for jar in mods.glob("*.jar"):
        jar.unlink()
    shutil.copy2(find_primary_jar(root), mods / find_primary_jar(root).name)
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
    (root / "run" / "eula.txt").write_text(
        "# Disposable CI server only; this file is never committed.\neula=true\n",
        encoding="utf-8",
    )
    stage_mods(root, args.leak_fixture)

    if args.loader == "fabric":
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
    reports_before = {str(p) for p in (root / "run").rglob("*.json")}
    process = subprocess.Popen(
        ["./gradlew", "runServer", "--no-daemon"],
        cwd=root,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        start_new_session=(os.name == "posix"),
    )
    output_lines = start_output_reader(process)

    deadline = time.monotonic() + args.boot_timeout
    try:
        while time.monotonic() < deadline and process.poll() is None:
            line = take_line(output_lines, deadline)
            if line is None:
                break
            command_output.append(line)
            print(line.rstrip(), flush=True)
            if CRASH_RE.search(line):
                raise RuntimeError(f"Server crash signature during boot: {line.strip()}")
            if READY_RE.search(line):
                ready = True
                break
        if not ready:
            raise RuntimeError("Dedicated server did not reach the ready marker")

        time.sleep(2)
        for command in commands:
            send(process, command)
            wait_for_report = command.startswith("hh run ")
            command_deadline = time.monotonic() + (args.run_timeout if wait_for_report else 5)
            saw_report = False
            saw_unsupported = False
            while time.monotonic() < command_deadline and process.poll() is None:
                line = take_line(output_lines, command_deadline)
                if line is None:
                    break
                command_output.append(line)
                print(line.rstrip(), flush=True)
                if CRASH_RE.search(line):
                    raise RuntimeError(f"Server crash signature: {line.strip()}")
                if wait_for_report and REPORT_RE.search(line):
                    saw_report = True
                    break
                if wait_for_report and re.search(r'unsupported by this platform|not supported', line, re.IGNORECASE):
                    saw_unsupported = True
                    break
            if wait_for_report and not saw_report and not saw_unsupported:
                raise RuntimeError(f"No report completion marker after command: {command}")
            time.sleep(0.5)
    finally:
        stop_process(process)
        (evidence / "server.log").write_text("".join(command_output), encoding="utf-8")

    reports = read_reports(root)
    new_reports = [item for item in reports if item["path"] not in reports_before]
    if not new_reports:
        raise RuntimeError("No new HeapHammer report JSON was produced")

    summary = {
        "minecraft": args.mc_version,
        "loader": args.loader,
        "fixture": args.leak_fixture,
        "reports": [],
    }
    suspicious = False
    for item in new_reports:
        report = item["report"]
        detection = report.get("detection", {})
        classification = str(detection.get("classification", ""))
        suspicious = suspicious or classification in {"SUSPICIOUS", "FAIL"}
        summary["reports"].append(
            {
                "path": item["path"],
                "runId": report.get("runId"),
                "scenario": report.get("spec", {}).get("scenarioId"),
                "classification": classification,
                "slopeBytesPerCycle": detection.get("slopeBytesPerCycle"),
                "rSquared": detection.get("rSquared"),
                "cleanupValidation": report.get("cleanupValidation"),
            }
        )
    (evidence / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    if args.leak_fixture != "forge1710" and not suspicious:
        raise RuntimeError("Leak fixture did not produce a SUSPICIOUS/FAIL report")
    print(json.dumps(summary, indent=2), flush=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"[LIVE MATRIX FAILURE] {exc}", file=sys.stderr, flush=True)
        sys.exit(1)
