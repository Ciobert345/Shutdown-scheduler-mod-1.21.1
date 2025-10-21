# 🕒 Shutdown Scheduler (Fabric Mod)

A simple **Fabric mod for Minecraft 1.21.1** that allows you to automatically shut down your server at specific times, either through commands or scheduled logic.  
Perfect for private or dedicated servers that need to power off automatically at a defined hour.

> ⚠️ **Note:** This mod is currently in **Italian**, including command arguments and messages.  
> English translation may be added in a future update.

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

| Command | Description |
|----------|-------------|
| `/shutdownscheduler add <giorno> <ora> <minuti>` | Adds a scheduled shutdown time. <br> Example: `/shutdownscheduler add lunedi 23 30` |
| `/shutdownscheduler remove <giorno> <ora> <minuti>` | Removes a previously added shutdown time. <br> Example: `/shutdownscheduler remove venerdi 18 00` |
| `/shutdownscheduler list` | Lists all currently scheduled shutdowns. |
| `/shutdownscheduler reload` | Reloads the `shutdown_scheduler.json` configuration file without restarting the server. |
| `/shutdownscheduler test` | Performs an immediate test, showing the next scheduled shutdown in chat. |
| `/shutdownscheduler force` | Forces an immediate server shutdown (equivalent to `/stop`). |

---
