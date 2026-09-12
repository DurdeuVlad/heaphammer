# Security Policy

The HeapHammer maintainers take security, memory safety, and server denial-of-service risks seriously. This document outlines our security commitment, supported versions, and procedure for reporting vulnerabilities.

---

## Supported Versions

HeapHammer provides active security updates and patch releases for the following versions:

| Mod Version | Minecraft Version | Git Branch / Line | Support Status |
|---|---|---|---|
| `1.0.x` | `1.21.1` | `master` | :white_check_mark: Active Production |
| `1.0.x` | `1.20.1` | `ver/1.20.1` | :white_check_mark: Modern LTS Gold Standard |
| `1.0.x` | `1.18.2` | `ver/1.18.2` | :white_check_mark: World-Gen Overhaul LTS |
| `1.0.x` | `1.16.5` | `ver/1.16.5` | :white_check_mark: Nether Legacy LTS |
| `1.0.x` | `1.12.2` | `ver/1.12.2-forge` | :white_check_mark: Classic Titan LTS |
| `< 1.0.0` | Any | Pre-release | :x: End of Life |

---

## Reporting a Vulnerability

If you discover a security vulnerability in HeapHammer—including potential server crash exploits, remote code execution vectors via diagnostic commands, arbitrary file write vulnerabilities in report storage, or permission escalation flaws—**please do not open a public GitHub issue**.

### Reporting Channel
Please report all security vulnerabilities privately to:
- **Email**: `security@dwurdy.com`
- **Subject**: `[SECURITY] HeapHammer Vulnerability Report`

Alternatively, you may submit a **Private Vulnerability Report** directly via GitHub:
- Navigate to the repository's **Security** tab.
- Click **Advisories** $\rightarrow$ **Report a vulnerability**.

### What to Include in Your Report
To help us triage and resolve the issue quickly, please include:
1. **Description**: Clear explanation of the vulnerability and its potential impact.
2. **Environment**: Minecraft version, Fabric Loader version, Java version, and HeapHammer version.
3. **Reproduction Steps**: Step-by-step instructions or reproduction plan/seed to trigger the issue.
4. **Proof of Concept (PoC)**: Sample commands, network packets, or minimal reproducible test cases.
5. **Mitigation**: Any known workarounds or suggested fixes.

---

## Our Commitment

- **Initial Response**: We will acknowledge receipt of your vulnerability report within **48 hours**.
- **Assessment & Triage**: We will provide a preliminary evaluation of the severity and impact within **5 business days**.
- **Coordinated Disclosure**: We adhere to responsible coordinated disclosure. Once a fix is validated, we will publish a security patch release alongside a GitHub Security Advisory crediting the reporter (unless anonymity is requested).
