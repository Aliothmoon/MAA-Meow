# Issue Guidelines

These guidelines standardize MAA Meow issue titles so contributors and maintainers can identify the issue type, affected area, and concrete problem directly from the issue list.

Both Chinese and English issue forms are available with the same fields.

## Title Format

```text
[Type][Area] Concrete problem or request
```

When you use the Bug Report or Feature Request form, **write only the concrete problem or request as the title**. After submission the prefix is added automatically from the form type and the selected area. For example, the title `App crashes after starting a task` with the area `Background Mode` becomes `[Bug][Background Mode] App crashes after starting a task`.

Hand-written type tags such as `[Bug]`, `【BUG】`, or `[Feature]` are replaced; other prefixes (such as `[Global]`) are kept.

### Type

| Label | Source | Use case |
|-------|--------|----------|
| `[Bug]` | Bug Report form | Existing behavior is broken, crashes, or differs from expectations |
| `[Feature]` | Feature Request form | A new feature or enhancement to existing behavior |

For documentation, build, or usage questions, open a blank issue or use [Discussions](https://github.com/Aliothmoon/MAA-Meow/discussions).

### Area

Pick where the failure **first appears**. If a problem spans several areas, describe the others in the issue body.

| Area | Scope |
|------|-------|
| `Login` / `Combat` / `Auto Recruit` / `Base` / `Credit Store` / `Collect Rewards` / `Auto I.S.` | Recognition, actions, or results of that task in the task list; Visit Friends belongs to `Credit Store` |
| `Other Tasks` | Update doctor data, Depot Maintain, Operator Training, Switch Theme, close game, custom tasks, etc. |
| `Copilot` | Features on the Copilot page, such as copilot operations and Paradox Simulation |
| `Tools` | Recruitment recognition, depot recognition, operator recognition, gacha, etc. |
| `Reclamation Algorithm` | Reclamation Algorithm under Tools > Useful Tasks |
| `Schedule` | Scheduled triggers, wake-up, tasks not starting on time |
| `Foreground Mode` | Problems specific to foreground mode, such as landscape or primary display |
| `Background Mode` | Virtual display issues: black screen, game moving back to the main display, process killed |
| `Permissions & Service` | Shizuku / Root authorization, service startup failures, MAA service crashes or disconnects |
| `Notifications` | System notifications and external notification delivery |
| `Installation & Updates` | APK installation, app updates, resource download and initialization |
| `UI & Settings` | Display and interaction of Home, Settings, the overlay, and the control panel |
| `Other` | None of the above |

### Summary

- State what happens and, when useful, under which condition. Avoid vague wording such as “broken,” “doesn't work,” or “has a problem.”
- Describe the actual behavior instead of only stating the expected behavior.
- Keep the title concise. Put device models, version numbers, logs, and full reproduction steps in the issue body.
- Do not end the title with a period.

## Examples

| Title you enter | Selected area | Final title |
|-----------------|---------------|-------------|
| `App crashes after starting a task` | Background Mode | `[Bug][Background Mode] App crashes after starting a task` |
| `[Bug] Task does not start at the configured time` | Schedule | `[Bug][Schedule] Task does not start at the configured time` |
| `Filter copilot operations by operator class` | Copilot | `[Feature][Copilot] Filter copilot operations by operator class` |

| Not recommended | Reason |
|-----------------|--------|
| `Something is wrong`, `[Bug]` | Says nothing about what happened |
| `Scheduled tasks do not work` | Does not say whether it failed to trigger, failed to wake, or failed during execution |
| `Please add a filter` | Does not say where or by what |

## Information Required for Feature Requests

A feature request should provide enough information for maintainers to assess its value and scope:

- **Use case and current problem**: Under which conditions you run into the problem and why the current workflow is insufficient.
- **Proposed behavior**: The desired behavior or interaction; a code-level implementation is not required.
- **Acceptance criteria** (optional): What would count as this request being done.
- **Alternatives** (optional): Any workaround or alternative considered and why it is insufficient.

Keep one primary request per issue. Submit unrelated requests separately so they can be evaluated, tracked, and closed independently.

## Pre-submit Checklist

- [ ] The title states the concrete problem or request
- [ ] The selected area matches the issue body
- [ ] Existing issues were searched to avoid duplicates

Maintainers may rename titles that do not follow the convention. Split unrelated problems into separate issues.
