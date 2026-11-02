## ⚠️ Please Update to v0.25.1 (NEW!!!)

The TL event stages (TL-4/7/8/9) only appear in the stage list on v0.25.1 or later. On older versions, entering them manually marks them as not open.

The November 1, 2026 game update redesigned the home screen. On versions before v0.25.0, every task entered from the home screen fails.

Update to v0.25.1 or later in `Settings → Update`.

---

## Reclamation Algorithm Has Moved (NEW!!!)

Starting with v0.25.0, Reclamation Algorithm has moved into Tools along with upstream MAA and is no longer in the task list.

Location: Tools → Useful Tasks → Reclamation Algorithm

After updating, Reclamation Algorithm is removed from every task profile and its settings carry over to the new location. Scheduled runs no longer include it; start it manually from Tools when needed.

---

## Black Forest Money Farming

~~The map is too complex, so here is a quick money-farming script first.~~ The auto-roguelike money-farming strategy is out now, but this one is still handy, so it stays.

Location: Tools → Useful Tasks → Black Forest money farming

---

## Runtime Notes

Do not use **eye-care mode** or **smart resolution** while MaaMeow is running, as they interfere with recognition.

For auto-battle, enable high frame rate in-game and prefer high graphics quality to avoid recognition errors.

Some Android 11 devices report "storage directory inaccessible". Follow the prompt to `Settings → Other → MaaCore data directory`, switch to the separate directory and restart. In that mode, viewing core logs requires the elevated service to be running, and uninstalling the app does not remove the separate directory; use the clear button next to it.

---

## User Guide

**📖 Docs & FAQ**  
When something goes wrong, check `Settings → About → FAQ` first. Most issues already have an answer there.

**🛟 When You Encounter Issues**  
Go to `Settings → Logs` to review the task history and error logs and identify the cause.  
Use `Settings → Logs → Export Log Archive` to generate a log package, then submit it via `Settings → About → Issue Report`.

**⚠️ Feedback Guidelines**  
After confirming the issue is not covered by the FAQ, file it on [GitHub](https://github.com/Aliothmoon/MAA-Meow/issues) following the [issue template](https://github.com/Aliothmoon/MAA-Meow/issues/new/choose), with the log archive attached.

**💡 Contributing**  
Contributions on [GitHub](https://github.com/Aliothmoon/MAA-Meow) are welcome. Code contributions are encouraged over issue reports.

---

## Update Explanation

**App**: UI and feature updates for the application itself.  
**MAA Resources**: Asset data updates, including new stage maps, drop icons, and operator tags for recruitment.  
**Navigation Hot Update**: Triggered automatically to update event notices and stage entries.

Each app release includes the latest resources at the time of release. If you don't need features requiring the latest resources, you can postpone updating; when needed, update resources in `Settings → Update`.

**Download Source**: Choose GitHub or [MirrorChyan](https://mirrorchyan.com/?source=maameow-anno-en). With a CDK, MirrorChyan enables automatic updates for both the app and resources, with priority downloads via high-speed CDN.

**Update Channel**: Switch between **Stable** and **Beta** in `Settings → Update → Update Channel` (beta versions have `-beta.x` in the version number). Beta builds may have more issues; Stable is recommended unless you have specific needs.

---

## [MAA × MirrorChyan] Community Sponsorship Program

MAA has integrated [MirrorChyan](https://mirrorchyan.com/?source=maa-anno-en). Revenue from MirrorChyan is used to maintain the [MAA Official Website](https://maa.plus/), [Copilot Station](https://prts.plus/), and servers. After deducting necessary operational costs, the MAA Team donates its share of revenue, along with additional sponsorship funds provided by MirrorChyan, **entirely** to support quality Arknights community projects. MAA does not participate in the internal operations of sponsored projects.

Server costs are currently covered for the following community projects:

- [Penguin Statistics](https://penguin-stats.cn/)
- [Arknights Yituliu](https://ark.yituliu.cn/)
- [ARKREC Wiki](https://wiki.arkrec.com/)
- [Arknights Roguelike Tactics](https://arkrog.com/)
- [PRTS.Map](https://map.ark-nights.com/)
- [AmiyaBot](https://www.amiyabot.com/)
- [Arknights Toolbox](https://arkntools.app/)
- [Lungmen Dragons](https://lungmendragons.com/)
- [ak-roster](https://www.krooster.com/)

📜 [Full Announcement](https://github.com/MaaAssistantArknights/MaaAssistantArknights/issues/12328)

---

## Important Notice

Please do not discuss any MAA-related content under **official posts** by Arknights and HyperGryph on any platform (including but not limited to: Skland, Bilibili, Weibo).

Do not participate in spreading insider information. It is strictly prohibited to share insider information in any MAA group.

---

## Safe Download Reminder

Recently, third parties have been found creating fake MAA repositories, tampering with source code, and bundling malicious programs (stealing personal information, planting trojans, etc.). Please be careful.

MaaMeow is completely free and open-source. We **never** require "follow, like, share, and DM" to access the installer. Please download exclusively from **official channels**:

- **GitHub Release**: [github.com/Aliothmoon/MAA-Meow](https://github.com/Aliothmoon/MAA-Meow/releases)
- **Official QQ Group**: announcements and files within the group (join via `Settings → About → QQ Group`; do not join through other channels)
- **MirrorChyan**: the officially supported mirror download service

**Avoid downloading from network drives, forum posts, or any unofficial sources.**

Be cautious with all-in-one packages, modified builds, or repackaged versions. Only use them if you fully trust the source and have completed your own security checks.

Please stay vigilant about where your files come from to protect your account and device. Feel free to pass this along to friends to help them stay safe too.
