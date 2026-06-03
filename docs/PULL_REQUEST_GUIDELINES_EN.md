# Pull Request Guidelines

This document describes the basic expectations for pull requests to MAA Meow. It helps contributors and maintainers understand the change scope, verification results, and merge risks more quickly.

## Basic Principles

- **Target branch**: Use `main` by default.
- **Focused scope**: Keep each PR focused on one clear problem. Avoid mixing unrelated refactors, formatting-only changes, or dependency updates.
- **Clear description**: Explain why the change is needed, what it affects, how it was verified, and any known risks.
- **Draft early**: If the requirement or implementation direction is still uncertain, open a Draft PR first for early feedback.
- **Own your changes**: AI assistance is fine, but the submitter must understand and be able to explain the key changes.

## Branches and Commits

### Branch Names

Recommended formats:

| Type | Example | Use case |
|------|---------|----------|
| `feat/<name>` | `feat/schedule-profile` | New features |
| `fix/<name>` | `fix/background-service` | Bug fixes |
| `docs/<name>` | `docs/pr-guidelines` | Documentation changes |
| `refactor/<name>` | `refactor/task-runner` | Behavior-preserving refactors |
| `chore/<name>` | `chore/update-gradle` | Build, dependency, or tooling maintenance |

### Commit Messages

Following [Conventional Commits](https://www.conventionalcommits.org/) is recommended:

```text
<type>(<scope>): <subject>
```

Examples:

```text
feat(schedule): add scheduled task profile
fix(background): fix background task startup
docs: add pull request guidelines
```

`scope` is optional. Prefer a short subject without a trailing period.

## PR Title

Use a title consistent with the commit message so maintainers can quickly understand the change type.

| Recommended | Not recommended |
|-------------|-----------------|
| `feat(schedule): add scheduled task profile` | `add feature` |
| `fix(background): fix background task startup` | `fix background` |
| `docs: update build guide` | `update docs` |

If the PR is still in progress, use GitHub Draft PR instead of keeping `WIP` in the title for a long time.

## PR Description

The PR description should include at least the following sections.

### Related Issue

- Link the issue with `Closes #123`, `Fixes #123`, or `Related #123`
- If there is no issue, explain the source of the request, reproduction steps, or why the change is needed

### Summary

Use 2–5 bullet points to describe what changed, for example:

- Adjust background task startup flow
- Add Profile configuration validation
- Update the JDK requirement in the build guide

### Verification

Describe what was verified. Do not only write “tested”; include the device, Android version, permission mode, command, or operation path.

Recommended format:

```markdown
## Verification

- [x] Android Studio Sync Project with Gradle Files succeeded
- [x] `./gradlew assembleDebug` succeeded
- [x] Verified background task startup on Android 14 with Shizuku
```

### Screenshots, Logs, and Notes

For UI changes, permission behavior, background services, task execution, or bug fixes, provide useful evidence when possible:

- Before/after screenshots or recordings
- Crash stack traces, key Logcat snippets, or GitHub Actions links
- Reproduction steps, test device, Android version, and permission mode (Shizuku / Root)
- Impact on MAA Core, resource files, or third-party dependencies

## Change Requirements

### Android / Kotlin Changes

- Keep code paths clear; avoid mixing UI, permissions, background services, and task scheduling logic in one large function.
- Explain compatibility impact when changing permissions, background execution, notifications, accessibility, Shizuku, or Root-related logic.
- Update related documentation or screenshots for user-visible configuration, text, or interaction changes.
- Consider cancellation, error handling, and resource cleanup for long-running tasks.

### Build and Dependency Changes

- Explain the motivation and impact when changing Gradle, JDK, Android SDK, NDK, CMake, or workflow configuration.
- Dependency upgrades should include related configuration changes and note whether they affect the minimum Android version or ABI.
- Do not commit local build outputs, IDE caches, downloaded large resources, or debug files.

### Documentation Changes

- Update docs when user-visible behavior, build steps, permission requirements, or automation APIs change.
- Keep Chinese and English README entries aligned when both contain the same entry point.
- Use repository-relative links instead of hardcoding a branch or personal fork URL.

## Pre-submit Checklist

- [ ] The PR targets `main`
- [ ] The PR title is clear and preferably follows Conventional Commits
- [ ] The change scope is focused and does not include unrelated changes
- [ ] The branch is up to date with `origin/main`
- [ ] The reason, impact, and verification result are described
- [ ] Android behavior changes include the test device, Android version, and permission mode
- [ ] Build, dependency, or workflow changes include command output or Actions verification
- [ ] Documentation is updated for user-visible behavior or development convention changes
- [ ] No cache, build output, debug file, or large resource file is committed

## Review and Merge

- Maintainers may ask for logs, screenshots, reproduction steps, or verification records. Please continue updating the same PR.
- If review comments involve design direction, confirm the approach before making large changes.
- After the PR is merged, open a new issue or PR for follow-up problems instead of extending the merged PR with unrelated discussions.
