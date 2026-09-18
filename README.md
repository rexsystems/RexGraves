# RexGraves

Modern death graves for Paper, Purpur, and Folia. When you die, your items (and optional XP) are stored in a protected grave you can reclaim later.

<p>
  <img src="https://img.shields.io/badge/Minecraft-1.20.4%2B-62B47A?style=for-the-badge" alt="Minecraft 1.20.4+">
  <img src="https://img.shields.io/badge/Java-21-blue?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/License-AGPL%20v3-blue?style=for-the-badge" alt="AGPL v3">
</p>

## Features

- Graves store inventory, armor, and offhand on death
- Optional XP storage with configurable percent
- Player-head markers and MiniMessage holograms
- Loot GUI or auto-loot on right-click
- List, locate, teleport, and compass to graves
- Expiration with drop or destroy
- Max graves per player, world blacklist/whitelist
- Owner-only access with admin bypass
- Persistent across restarts and chunk loads
- YAML, SQLite, or MySQL storage with periodic autosave
- Folia-compatible
- PlaceholderAPI soft-depend for hologram text

## Storage

Set `storage.type` in `config.yml`:

| Type | Notes |
|------|-------|
| `yaml` | Default. Writes `plugins/RexGraves/graves.yml` atomically |
| `sqlite` | Local `plugins/RexGraves/graves.db` (imports existing YAML once if DB is empty) |
| `mysql` | Configure host/database/credentials under `storage.mysql` |

`storage.autosave-seconds` (default `30`) runs a backup save in addition to saves after create/loot/remove. Set to `0` to disable the timer.

## Requirements

- Java 21
- Paper / Purpur / Folia 1.20.4+

## Installation

1. Drop the JAR into `plugins/`
2. Restart the server
3. Edit `plugins/RexGraves/config.yml`
4. Run `/rexgraves reload`

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/rexgraves help` | Show help | `rexgraves.use` |
| `/rexgraves list` | List your graves | `rexgraves.list` |
| `/rexgraves locate` | Locate nearest grave | `rexgraves.locate` |
| `/rexgraves tp [index]` | Teleport to a grave | `rexgraves.teleport` |
| `/rexgraves compass [index]` | Point compass to a grave | `rexgraves.compass` |
| `/rexgraves reload` | Reload config | `rexgraves.reload` |
| `/rexgraves admin list [player] [page]` | List all graves or a player's | `rexgraves.admin` |
| `/rexgraves admin remove <id>` | Remove a grave by id | `rexgraves.admin` |
| `/rexgraves admin tp <id>` | Teleport to any grave | `rexgraves.admin` |
| `/rexgraves admin convert axgraves [path]` | Import AxGraves `data.json` | `rexgraves.admin` |

Aliases: `/rxg`, `/grave`, `/graves`

### Migrating from AxGraves

1. Stop the server (or disable AxGraves) so `plugins/AxGraves/data.json` is on disk
2. Install RexGraves and start
3. Run `/rexgraves admin convert axgraves`
4. Or copy `data.json` to `plugins/RexGraves/axgraves-data.json` / `plugins/RexGraves/import/axgraves-data.json` and convert

Worlds referenced by graves must be loaded.

## Permissions

| Permission | Default |
|------------|---------|
| `rexgraves.use` | true |
| `rexgraves.list` | true |
| `rexgraves.locate` | true |
| `rexgraves.teleport` | op |
| `rexgraves.compass` | true |
| `rexgraves.reload` | op |
| `rexgraves.admin` | op |
| `rexgraves.bypass` | op |
| `rexgraves.break` | op |

## License

GNU Affero General Public License v3.0 - see [LICENSE](LICENSE).

Website: [rexsystems.me](https://rexsystems.me)
