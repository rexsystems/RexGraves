# Changelog

All notable changes to RexGraves are documented in this file.

## [1.0.2] - 2026-09-24

### Added
- `settings.public-access` (off by default): graves become lootable by anyone after `after-seconds` (default 24h)
- `{public_in}` / `%rexgraves_nearest_public_in%` placeholder and `denied-public-in` message showing when a grave becomes public
- Startup/reload warning when graves would expire before becoming public

## [1.0.1] - 2026-09-19

### Added
- Storage backends: YAML (default), SQLite, and MySQL
- Periodic autosave (`storage.autosave-seconds`, default 30)
- Atomic YAML writes (temp file + replace)
- `blacklisted-death-causes` (empty by default)
- AxGraves converter via `/rexgraves admin convert axgraves [path]`

### Performance
- Saves are coalesced into one write per tick and autosave skips when nothing changed
- Grave items are serialized once when they change, so saves no longer re-serialize every item of every grave
- A failed save (e.g. MySQL down) is retried by the next autosave instead of being treated as written
- SQL storage only upserts changed rows and deletes removed ones instead of rewriting the whole table
- Grave tick skips unloaded graves, and holograms/particles only update when a player is within 64 blocks
- Holograms skip MiniMessage parsing and entity updates when their text did not change
- AxGraves convert reads and parses the file async, and no longer force-loads chunks to spawn visuals
- `/rexgraves admin list <player>` scans offline players async
- Interacting with non-grave armor stands no longer scans every world

### Fixed
- Experience graves now store the player's real total XP (not vanilla death-drop, which is capped at 100)
- Graves stay exactly where the player died (lava, fire, mid-air) instead of being moved to a "safe" spot, which could end up on the Nether roof
- Void deaths place the grave on the same X/Z at the first standable Y above the void
- Out-of-order async saves could overwrite newer grave data with an older snapshot (e.g. restoring already-looted XP/items after a restart)
- Grave GUI changes are saved on every click, not only on close, so a crash with the GUI open cannot duplicate items
- Open grave GUIs are synced and closed on shutdown
- A grave no longer stays locked forever if another plugin cancels opening its GUI
- Duplicate markers/holograms after restarts (visuals are now reconciled when chunk entities load)
- Curse of Vanishing items are destroyed on death like vanilla instead of being stored in the grave
- Items other plugins keep on death (soulbound etc.) stay in the inventory
- XP is not taken into the grave when another plugin keeps the player's level on death
- AxGraves import no longer expires graves instantly (expiry starts from import time)
- AxGraves item decode no longer silently drops every stack
- AxGraves convert on Paper/Purpur 26+ (use ItemStack.deserializeBytes)
- Keep-inventory and disabled-world messages now send on death
- Nearest-grave lookup no longer crashes across worlds
- `protect-from-explosions` is respected (destroy + drop when disabled)

## [1.0.0] - 2026-07-25

### Added
- Death graves that store inventory, armor, and offhand
- Optional experience storage and restore
- Player-head grave markers with MiniMessage holograms
- Right-click loot GUI and auto-loot option
- Grave list, locate, teleport, and compass commands
- Expiration with drop or destroy modes
- Max graves per player and world blacklist
- Owner-only access with admin bypass
- Persistent graves across restarts and chunk loads
- Folia-compatible scheduling
- Hot reload via `/rexgraves reload`
- PlaceholderAPI soft-depend for hologram placeholders
- GitHub release and nightly workflows
