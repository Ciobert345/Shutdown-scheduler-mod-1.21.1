# 🕒 Shutdown Scheduler (Fabric Mod)

A simple **Fabric mod for Minecraft 1.21.1** that allows you to automatically shut down your server at specific times, either through commands or scheduled logic.  
Perfect for private or dedicated servers that need to power off automatically at a defined hour.


---

## 🚀 Features

- Automatically shuts down the server at scheduled times.  
- Supports running commands before shutdown.  
- Clear console logging for monitoring actions.  
- Compatible with **Fabric Loader 0.17.2+** and **Minecraft 1.21.1**.

---

### 🧩 Requirements
- **Minecraft 1.21.1**
- **Fabric Loader 0.17.2+**
- **Fabric API** (must be installed on the server)

---

## 🛠️ Available Commands

### 🛠️ Available Commands

| Command | Description |
|----------|--------------|
| `/shutdownscheduler add <day> <hour> <minute>` | Adds a scheduled shutdown time.<br>Example: `/shutdownscheduler add monday 23 30` |
| `/shutdownscheduler remove <day> <hour> <minute>` | Removes a previously scheduled shutdown time.<br>Example: `/shutdownscheduler remove friday 18 00` |
| `/shutdownscheduler list` | Lists all scheduled shutdown times. |
| `/shutdownscheduler reload` | Reloads the `shutdown_scheduler.json` configuration file without restarting the server. |
| `/shutdownscheduler test` | Runs an immediate test, showing the next scheduled shutdown in chat. |
| `/shutdownscheduler force` | Forces an immediate server shutdown (equivalent to `/stop`). |
| `/shutdownscheduler language <it/en>` | Changes the mod’s language between Italian (`it`) and English (`en`). |
| `/shutdownscheduler skipnext` | Skips the next scheduled shutdown (useful for maintenance or testing). |


---
