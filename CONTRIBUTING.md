# Contributing to BB-8 Controller

Thank you for your interest in this project. BB-8 Controller is a community-maintained Android app for the Sphero BB-8 droid. We welcome bug reports, feature ideas, documentation fixes, and code contributions.

**Please read this guide before opening a pull request.**

---

## Quick links

| Document | Purpose |
|----------|---------|
| [README](README.md) | Overview, install, usage |
| [SECURITY.md](SECURITY.md) | Report vulnerabilities privately |
| [PRIVACY.md](PRIVACY.md) | What data the app uses and stores |
| [LEGAL.md](LEGAL.md) | Trademarks, disclaimers, liability |
| [docs/PROTOCOL.md](docs/PROTOCOL.md) | Sphero BLE V1 protocol notes |

---

## We encourage issues first

**Before writing code, open an issue** so we can align on scope and avoid duplicate work.

- **Bug?** Use the [Bug report](https://github.com/Alexei-Simons/bb8-app/issues/new?template=bug_report.yml) template.
- **New feature?** Use the [Feature request](https://github.com/Alexei-Simons/bb8-app/issues/new?template=feature_request.yml) template.
- **Hardware / battery question?** Use the [Hardware support](https://github.com/Alexei-Simons/bb8-app/issues/new?template=hardware_support.yml) template.

Even a short issue helps other owners search for known problems. You do not need to be a developer to contribute: reproduction steps, logs, and videos are valuable.

---

## Pull request workflow

`main` is protected. All changes merge through **pull requests** with **code owner review**.

### 1. Find or file an issue

Every developer PR should **link an issue**:

- Reference it in the PR description: `Closes #42` or `Fixes #42`
- If no issue exists yet, open one first and wait for a quick acknowledgment (or label) before large work

This keeps history traceable and helps reviewers understand intent.

### 2. Fork and branch

```bash
git clone https://github.com/Alexei-Simons/bb8-app.git
cd bb8-app
git checkout -b fix/issue-42-battery-parser
```

Branch naming (recommended):

- `fix/issue-<n>-short-description`
- `feat/issue-<n>-short-description`
- `docs/short-description`

### 3. Develop

- **Scope:** One logical change per PR when possible.
- **Style:** Match existing Kotlin and Compose patterns in the repo.
- **Tests:** Add or update unit tests for protocol, parsing, or business logic changes.
- **Docs:** Update README or `docs/PROTOCOL.md` if behavior or protocol handling changes.
- **Assets:** Do **not** add Sphero copyrighted sounds, animations, logos, or branding.

### 4. Build and test locally

```bash
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
.\gradlew.bat test assembleDebug

# macOS / Linux
./gradlew test assembleDebug
```

### 5. Commit messages

Write clear, imperative subject lines:

```
Fix voltage field swap for BB-8 power state payloads

Parse extended_mask locator bits per Sphero API 1.20.
Closes #42.
```

### 6. Open the pull request

Use the [pull request template](.github/pull_request_template.md). Include:

- Linked issue (`Closes #…`)
- Summary of what changed and why
- Test plan (device model, Android version, BB-8 behavior observed)
- Screenshots or screen recordings for UI changes

### 7. Review

- Address review feedback with new commits on the same branch.
- After approval, a maintainer will merge (linear history required; prefer **Squash and merge**).

---

## Code guidelines

### Kotlin / Android

- Min SDK 26, target SDK 35, JDK 17.
- Jetpack Compose for UI; avoid introducing unrelated UI frameworks.
- Keep BLE and protocol logic in `ble/` and `sphero/` packages.
- Prefer small, focused diffs over large refactors unless discussed in the issue.

### Bluetooth and safety

- Never send drive commands in tight loops without throttling.
- Respect `diagnosticsOnly` battery mode: do not bypass safety gates for weak cells.
- Document any new Sphero commands in `docs/PROTOCOL.md` with DID/CID references.

### What we will not merge

- Sphero or Lucasfilm copyrighted assets.
- Cloud analytics, tracking SDKs, or unnecessary network permissions.
- Changes that weaken battery safety warnings or legal disclosures.
- Unrelated drive-by formatting across the whole codebase.

---

## Development setup

See [README - Build from source](README.md#build-from-source).

Optional protocol references live under `vendor/` (see `vendor/README.md`). Clone `spherov2.py` locally for cross-checking packet layouts; it is not bundled in the APK.

---

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). Be respectful and constructive in issues and reviews.

---

## Questions?

- Open a [Discussion](https://github.com/Alexei-Simons/bb8-app/discussions) or a labeled issue if enabled.
- For security issues, see [SECURITY.md](SECURITY.md) (do not file public issues for vulnerabilities).

Maintainer: [@Alexei-Simons](https://github.com/Alexei-Simons)
