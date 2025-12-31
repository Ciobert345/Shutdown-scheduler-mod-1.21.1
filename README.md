# 🕒 Shutdown Scheduler (Fabric Mod)

A powerful **Fabric mod for Minecraft 1.21.1** that allows you to automatically shut down your server at specific times using a flexible grouping system and an in-game GUI.

Perfect for private or dedicated servers that need to power off automatically at defined hours to save resources.

---

## 🚀 Features

- **Scheduled Shutdowns**: Configure automatic shutdowns for any day of the week.
- **Group System**: Organize your schedules into groups (e.g., "Weekdays", "Maintenance") and toggle them on/off independently.
- **In-Game GUI**: Manage everything visually without editing files manually.
- **Multi-language**: Supports English and Italian.
- **Warning System**: Customizable countdown warnings before shutdown.

---

## 🛠️ Usage

### Commands

The mod uses two main command aliases:

- `/ss`: The primary command for quick actions.
- `/shutdownscheduler`: Opens the configuration menu directly.

| Command | Description |
|:---|:---|
| `/shutdownscheduler` | Opens the **Config GUI** to manage groups, schedules, and settings. |
| `/ss` | Opens the **Config GUI** (same as above). |
| `/ss cancel` | Cancels a pending shutdown sequence (if the countdown has started). |
| `/ss sync` | Forces a configuration sync (useful if you manually edited the config file). |

### Configuration GUI

Run `/ss` or `/shutdownscheduler` to open the menu. From there you can:
1. **Create Groups**: Add new schedule groups.
2. **Toggle Groups**: Enable or disable groups with a single click.
3. **Manage Schedules**: Click on a group to add or remove shutdown times (Day + Time).
4. **Settings**: Change the warning time (minutes) and language.

### Configuration File (`config/shutdown_scheduler.json`)

Advanced users can edit the config file directly.
Example structure:

```json
{
  "warning_minutes": 5,
  "language": "en",
  "groups": {
    "default": {
      "enabled": true,
      "shutdowns": {
        "0": [ "23:00" ], // Monday
        "5": [ "02:00" ]  // Saturday
      }
    }
  }
}
```

**Days Index:**
0: Monday, 1: Tuesday, ..., 6: Sunday

---

### 🧩 Requirements
- **Minecraft 1.21.1**
- **Fabric Loader**
- **Fabric API**
