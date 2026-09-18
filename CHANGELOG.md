# Changelog

All notable changes to RexGraves are documented in this file.

## [1.0.1] - 2026-09-19

### Added
- Storage backends: YAML (default), SQLite, and MySQL
- Periodic autosave (`storage.autosave-seconds`, default 30)
- Atomic YAML writes (temp file + replace)
- `blacklisted-death-causes` (empty by default)
- AxGraves converter via `/rexgraves admin convert axgraves [path]`

### Fixed
- Void deaths place the grave on the same X/Z at the first standable Y above the void
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
