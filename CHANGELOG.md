# Changelog

All notable changes to the **GenSprout** project will be documented in this file.

## [1.0.1] - 2026-07-10

### Added
- Enforced `gensprout.admin` permission on the **Farm Selector Stick** usage and admin commands. This ensures only administrators can define/save farm regions or use administrative subcommands like reload, addxp, etc.
- Added dynamic price calculation inside `/genshop`. When players click **Check Price**, the UI will refresh and display the total calculated cost for the chosen tier and quantity directly in the dialog box (without resetting the selected parameters).
- Added a `drop-material` configuration option to `config.yml` under each generator tier. This allows complete server customization of what items are produced by each generator tier directly from the configuration file.
- Added a `/prestige` command, which directly opens the Prestige Menu dialog.
- Added integration with Minecraft's pause menu additions (`minecraft:pause_screen_additions` tag) and quick actions (`minecraft:quick_actions` tag) via the Paper Dialog API, presenting a clean layout to instantly open shops and menus.
- Added a `menus.main-menu-title` configuration key to customize the main menu dialog header, defaulting to the dynamic server's name.
- Added a `commands.gensprout` configuration key to allow complete customization of the plugin's primary command name.

### Changed
- Upgraded the plugin version to `1.0.1` in `pom.xml` and `paper-plugin.yml`.
- Configured crop generator tiers (1-9) to use full block types (e.g. `BROWN_MUSHROOM_BLOCK` for the Cocoa Generator) and dynamically load their drop item materials (e.g. `COCOA_BEANS`) via the new `drop-material` key in `config.yml`.
- Renamed "Prestige Shop" to "Prestige Menu" across all UI headers, dialogues, and buttons.
- Configured the Minecraft pause screen additions and quick actions launcher menus to include a "View Level & Stats" button to open the dynamic menu on the server side, resolving the client-side scoreboard resolution issue.
- Renamed "Farming Level" and "Farming XP" to just "Level" and "XP" inside the dynamic main menu.
- Replaced all hardcoded "GenSprout" occurrences in player-facing messaging, command output, selector stick lore, and help strings with dynamic configuration options resolved using `server.name` and the configured primary command.

### Removed
- Removed all other player-level permissions and checks. Generator placement, breaking/picking up, upgrading, crop harvesting, and the `/sell` and `/genshop` commands are now fully public and accessible to everyone by default without any permission node requirements.
