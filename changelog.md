# Changelog

## [2.0.0]

### ✨ New Features
- **Shutdown Groups**: Introduced a new system to organize shutdown schedules into groups.
  - Create custom groups (e.g., "Weekdays", "Events").
  - Enable or disable entire groups independently.
- **Enhanced Configuration**: The configuration structure has been updated to support the new grouping system.
- **GUI Introduction**: Added a brand new in-game configuration menu. No need to edit JSON files manually anymore!
  - Manage groups visually.
  - Add/Remove schedules with a user-friendly interface.
  - Change settings (Warning time, Language) directly in-game.

### 🔧 Changes
- **Command Update**:
  - `/ss`: Main command for quick actions (`cancel`, `sync`) and opening the menu.
  - `/shutdownscheduler`: Alias to open the configuration menu directly (no subcommands).
- **Scheduler Logic**: The scheduler now iterates through all *enabled* groups to trigger shutdowns.

### 🐛 Fixes
- **General Stability**: Improved error handling and configuration synchronization.
