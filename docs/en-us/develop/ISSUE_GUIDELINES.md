# Issue Guidelines

These guidelines standardize MAA Meow issue titles so contributors and maintainers can identify the issue type, affected area, and concrete problem directly from the issue list.

Both Chinese and English issue forms are available. They use the same title structure and information requirements; use the matching language for the area name and issue content.

## Title Format

```text
[Type][Area] Concrete problem or request
```

Each title has three parts:

1. **Type**: Use one of the fixed English labels.
2. **Area**: Use a stable, concise product area.
3. **Summary**: Describe an observable symptom or a specific request.

### Type

| Label | Use case |
|-------|----------|
| `[Bug]` | Existing behavior is broken, crashes, or differs from expectations |
| `[Feature]` | A new feature or enhancement to existing behavior |
| `[Docs]` | Missing, incorrect, or improvable documentation |

### Area

Prefer one of these names: `Home`, `Task Config`, `Foreground Mode`, `Background Mode`, `Scheduled Tasks`, `Auto Battle`, `Toolbox`, `Settings`, `Install Update`, `Build`, or `Docs`. Use `Other` only when none applies.

Use one primary area per issue. If a problem spans several areas, choose where the failure first appears or where its impact is greatest, then describe the other affected areas in the issue body.

### Summary

- State what happens and, when useful, under which condition. Avoid vague wording such as “broken,” “doesn't work,” or “has a problem.”
- Describe the actual behavior instead of only stating the expected behavior.
- Keep the title concise. Put device models, version numbers, logs, and full reproduction steps in the issue body.
- Do not end the title with a period.

## Examples

| Recommended | Not recommended | Reason |
|-------------|-----------------|--------|
| `[Bug][Background Mode] App crashes after starting a task` | `[Bug] Background problem` | Missing a standard area and a concrete symptom |
| `[Bug][Scheduled Tasks] Task does not start at the configured time` | `Scheduled tasks do not work` | Missing the type and a specific failure |
| `[Feature][Auto Battle] Support filtering by operator class` | `Please add a filter` | Missing the area and a concrete request |
| `[Docs][Build] Document JDK setup on Windows` | `Update docs` | The affected documentation is unclear |

## Information Required for Feature Requests

In addition to following the title convention, a feature request should provide enough information for maintainers to assess its value, scope, and completion criteria:

- **Use case and current problem**: Who encounters the problem, under which conditions, and why the current workflow is insufficient.
- **Proposed behavior**: The desired behavior or interaction; a code-level implementation is not required.
- **Acceptance criteria**: Verifiable outcomes instead of vague goals such as “better usability.”
- **Alternatives**: Any workaround or alternative considered and why it is insufficient.

Keep one primary request per issue. Submit unrelated requests separately so they can be evaluated, tracked, and closed independently.

## Pre-submit Checklist

- [ ] The title follows `[Type][Area] Concrete problem or request`
- [ ] The type and area match the issue body
- [ ] The primary problem or request is understandable from the title alone
- [ ] Existing issues were searched to avoid duplicates

Maintainers may rename titles that do not follow the convention. Split unrelated problems into separate issues.
