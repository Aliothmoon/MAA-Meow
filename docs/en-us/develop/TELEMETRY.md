# Telemetry

MaaMeow uses [Sentry](https://sentry.io) to report crashes and task results anonymously, so we can see which tasks fail and at which step.

The setting is **Settings → Third-party services → Help improve this project**. It is on by default. When it is off, Sentry is never initialized and no request is sent.

## What is sent

| Kind | When | Content |
|---|---|---|
| Run statistics | Every run | Name, duration and result of each task chain, plus the parameter summary described below |
| Task failure | A task chain fails | The failing subtask and the recognition node it was stuck on; the logs of that task; in background mode, also the game screenshot MaaCore saved on failure: downscaled before upload, sent only once per run for the same failure, and on stable releases attached to only about one failure in five |
| Start failure | Resource loading, instance creation, virtual display or connection fails | The failing stage; the logs of that run |
| Service death | The Shizuku / Root process exits unexpectedly during a run | The task chain that was running; the logs of that run and MaaCore's `crash.log`; the next time the service connects, the system log (logcat) lines that mention that process (by pid or process name) from one minute before to 15 seconds after its death; other kill records in that window are reported only as a count, without other apps' names; with Shizuku as the backend, also the Shizuku state about half a second after the death |
| Stuck service call | A virtual display call (start, stop, resize, …) does not return in time and the app restarts the elevated process | Which call was stuck; the stack traces of the elevated process's threads, taken before the restart; the logs of that run |
| Scheduled launch failure | A scheduled or externally triggered launch never reaches the tasks: validation fails, the UI cannot be brought up, the device cannot be unlocked (not reported when the system has a lock screen password but no PIN or gesture is configured), or the start fails | The result and reason, how late the trigger fired, and the trigger log of that attempt; with Shizuku as the backend, also the Shizuku state described below; when the start fails, also the latest part of the service connection diagnostics; when the device cannot be unlocked, also the chosen unlock method, whether a PIN is set or a gesture is recorded, whether the system has a lock screen password, and why unlocking failed, with the trigger log also carrying the screen and lock state before waking, when the gesture was recorded, and the timing of each unlock step, never the PIN, its length, the gesture trajectory or its step count |
| Shizuku not running | Schedules are enabled but Shizuku is still not running a few minutes after boot, or a minute after it stopped, and the app shows a reminder | Whether it never started after boot or stopped later, how many schedules are enabled, and the Shizuku state described below; no logs |
| App crash | Uncaught Java exception | Stack trace; for a crash caused by a foreground service not starting in time, also how long before the crash the task progress service was last requested to start, entered, and put in the foreground |
| App not responding | The system reports an ANR because the main thread is blocked | Stack traces of the app's threads; the timeout reason the system recorded (such as an input timeout, or which service or broadcast timed out, taken from `ApplicationExitInfo`, Android 11+) |
| Activity | App goes to foreground or background | Session start and end, used for daily active users and crash rate |

"Logs" means the MaaCore log (`asst.log`), the run log of that run and the app error log, plus the service connection diagnostics for start failures and service deaths. Only the part written around the incident is taken, with a little preceding context, 1 MiB in total at most.

"Shizuku state" means whether the current app process ever got a Shizuku connection, how long ago it was lost, whether Shizuku last ran as root or adb, whether the official Shizuku app or Sui is installed, and how long the device has been up.

Every record carries:

- An anonymous device ID: a salted SHA-256 of `ANDROID_ID`, which cannot be reversed. The same value appears as `Telemetry ID` in the `device_info.txt` of an exported log package; include it in bug reports so the matching records can be found
- App version, MaaCore version, resource version and build type
- Client type, game version, run mode, background resolution and data location
- In background mode, whether the virtual display mode is Legacy or VDM, and whether it was created with full permissions or fell back to compatibility mode because the system lacks them
- Elevation backend, whether Shizuku runs as root or adb, and whether the app is exempt from battery optimization
- Whether each of four behavior-changing switches is on: MAA task override, force fullscreen on the virtual display, deploy-with-pause, and turning off display power for Screen Off
- Whether the display was already turned off by Screen Off when the run started, and whether that was done manually or by a schedule
- Device model, OS version, vendor ROM name and version, SoC, total memory and ABI
- The device and OS information the Sentry SDK adds on its own: battery level, free memory and storage, screen size, language and time zone, connection type, whether the device is rooted, and similar

## What is not sent

- Logs and screenshots are not touched unless something failed
- No screenshot in foreground mode: it would capture the phone's main screen, which may show other apps
- Task parameters are summarized: booleans and numbers are sent as-is, enum-like values such as stage, theme and client are sent as-is, any other string (account, reporting ID, file path) is reported only as filled or empty, and lists only by length
- Logs are redacted before they leave the device: the values of `account_name`, `penguin_id` and `yituliu_id`, and the stored Penguin Statistics ID, Yituliu token, MirrorChyan CDK and notification channel secrets are replaced with `***`
- No view hierarchy, tap or navigation trail, network request trail, or system event trail (screen on/off, battery, connectivity changes) is collected
- No IP address: default PII is disabled in the SDK and IP storage is disabled in the Sentry project

Three things redaction cannot remove:

- MaaCore logs contain recognition results, which may include in-game friend names
- The failure screenshot is the game screen on the virtual display at that moment, which may show your Doctor name and level
- The trigger log sent with a scheduled launch failure contains the names you gave that schedule and its task profile

Turn off "Help improve this project" if you are not comfortable with that.
