# OakLog

A comprehensive per-player collection log for Paper servers — tracks every collectible thing in Minecraft.

## Features

- **Seven categories**: Blocks, Items, Entities, Biomes, Structures, Effects, Enchantments
- **Mob variants count separately**: sheep colors, cow climate variants, villager type × profession, every horse color × style, tropical fish presets, painting motifs, and more — over 350 entity entries enumerated automatically from the runtime registry
- **Paginated GUI** with hub, per-category browsing, recent activity, sets, leaderboard, and search — all themable via `messages.yml`
- **Custom items** from Nexo, ExecutableItems, OakTools, or any PDC-keyed source via `custom-entries.yml`
- **Sets**: orthogonal groupings (Plushies, Cards, etc.) defined in `sets.yml` with pattern-based matchers (vanilla materials, Nexo ID globs, ExecutableItems ID globs, entry-key globs)
- **Leaderboard** with top-N players overall and per-category, with the viewing player's rank pinned at the bottom
- **Toast notifications** via PacketEvents — per-entry collection, milestones, set completions, and category/full-log completions, each with a distinct frame (TASK / GOAL / CHALLENGE). Toggleable per-player via `/oaklog toast`
- **PlaceholderAPI** expansion for hooking into menus, holograms, and the OakheartWeb dashboard
- **Audit log** of every admin grant/revoke action, async-written to a daily rotating file

## Requirements

- **Paper 1.21.10** or newer (Folia is not currently supported)
- **Java 21**
- Optional: PlaceholderAPI, PacketEvents (for toasts), Nexo, ExecutableItems

## Installation

1. Drop `OakLog-x.y.z.jar` in your `plugins/` folder
2. Start the server (config files extract automatically)
3. Edit `plugins/OakLog/config.yml`, `sets.yml`, `custom-entries.yml`, or `messages.yml` to taste
4. `/oaklog reload` after edits — registry resyncs, player progress is preserved

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/oaklog` | Open your collection log | `oaklog.use` |
| `/oaklog <player>` | View another player's log (read-only) | `oaklog.view-others` |
| `/oaklog toast <on\|off\|milestones>` | Toggle your toast preference | `oaklog.toast` |
| `/oaklog grant <player> <entry\|category\|set\|all>` | Admin grant | `oaklog.admin.grant` |
| `/oaklog revoke <player> <entry\|category\|set\|all confirm>` | Admin revoke (wipe requires `confirm`) | `oaklog.admin.revoke` |
| `/oaklog reload` | Reload configs and resync the registry | `oaklog.admin.reload` |
| `/oaklog info <entry>` | Inspect an entry's metadata | `oaklog.admin.info` |

The command alias defaults to `/log` and is configurable in `config.yml`.

## Permissions

| Node | Description | Default |
|------|-------------|---------|
| `oaklog.use` | Open your own log | `true` |
| `oaklog.view-others` | View other players' logs | `true` |
| `oaklog.toast` | Toggle your toast preference | `true` |
| `oaklog.admin.grant` | Grant entries to players | `op` |
| `oaklog.admin.revoke` | Revoke entries from players | `op` |
| `oaklog.admin.reload` | Reload config + resync registry | `op` |
| `oaklog.admin.info` | Inspect entry metadata | `op` |
| `oaklog.*` | All of the above | `op` |

## Configuration

`config.yml` covers detection intervals, the storage backend, toast defaults, search input mode, leaderboard refresh, and date formatting — see the file for inline comments.

Companion files:

- `messages.yml` — every player-facing string, with MiniMessage formatting and the standard Oakheart palette
- `sets.yml` — collection sets and their matchers
- `custom-entries.yml` — admin-defined entries for plugins not exposed by an adapter
- `biome-groups.yml` — biome ordering by dimension and climate

## Placeholders

If PlaceholderAPI is installed, the following are available:

| Placeholder | Returns |
|-------------|---------|
| `%oaklog_total_collected%` | Number of live entries the player has collected |
| `%oaklog_total_entries%` | Total live (non-retired) entries |
| `%oaklog_total_percent%` | Overall completion as a percentage (e.g. `37.4`) |
| `%oaklog_progress_<category>%` | `23/156` for the named category |
| `%oaklog_percent_<category>%` | Completion percentage for the named category |
| `%oaklog_toast_state%` | `on`, `off`, or `milestones` |
| `%oaklog_rank%` | Player's overall leaderboard rank |

Replace `<category>` with `blocks`, `items`, `entities`, `biomes`, `structures`, `effects`, or `enchantments`.
