# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- HMCWraps integration: every wrap defined in `plugins/HMCWraps/wraps/*.yml` becomes one entry in the Items category, collected when the physical wrap token enters the player's inventory. Detection uses HMCWraps' `Wrapper.getPhysicalWrapper` API so already-applied wraps don't count — only the consumable wrap token does. Registered between Shopkeepers and Nexo in the adapter chain to claim wraps whose visuals come from Nexo skin items.
- Initial project skeleton (gradle build, paper-plugin.yml, config.yml, messages.yml)
- Main class lifecycle with ConfigManager, MessageManager, and DebugLogger wired through OakheartLib
- SQLite storage layer with WAL mode, prepared-statement cache, single-thread async executor, and schema version tracking
- Entry registry with vanilla enumeration: blocks, items, entities, biomes, structures, effects, enchantments
- Variant resolvers split variant-bearing mobs into per-variant entries: sheep colors, cow climate variants, villager type×profession, panda genes, wolf variants, frog variants, axolotl colors, cat types, llama colors (including trader llamas), parrot variants, horse color×style, rabbit types, mooshroom colors, fox types, tropical fish presets (22), painting motifs
- Registry sync: diff against DB on enable + reload, inserts new entries, marks vanished entries retired (player progress preserved), revives retired entries that reappear, refreshes display data
- Per-player progress cache loaded on join, evicted on quit (configurable), with dirty-set tracking and scheduled async batch flush every `storage.flush-interval-ticks`
- Item and block detection via Paper's `PlayerInventorySlotChangeEvent` — single hook covers pickup, crafting, /give, trades, and any other source that lands an item in player inventory
- `ProgressEvent` fired on the main thread when a player collects a new entry (other plugins can listen to award rewards or fire effects)
- `/oaklog` command (alias `/log`, configurable) opens the collection log GUI for the running player
- `/oaklog <player>` opens another player's log in read-only view (permission `oaklog.view-others`)
- 54-slot Hub GUI: player head with overall progress, centered category buttons, action buttons for Recent/Sets/Search/Leaderboard (placeholders, full implementations in upcoming releases)
- 54-slot Category GUI: persistent tab strip across the top, 36 paginated entry slots, page navigation, All/Collected/Uncollected filter chips
- IconRenderer: builds entry icons with collected/uncollected name styling and lore from messages.yml templates
- Custom item adapter framework: `EntryAdapter` interface + `AdapterRegistry` enumerate-and-detect chain
- `PdcAdapter` reads `custom-entries.yml` for admin-defined items keyed by PersistentDataContainer (covers Nexo's `nexo:id`, ExecutableItems' `executableitems:executableitem_id`, OakTools, or any other PDC-keyed custom items)
- ItemBlockListener checks adapter detection first (custom items take precedence) before falling back to the vanilla material fast path
- `sets.yml` defines orthogonal collection sets via pattern matchers (vanilla materials, entry-key globs, Nexo IDs, ExecutableItems IDs). Set memberships recomputed on every registry sync
- Sets overview GUI with completion meter per set + dedicated Set view showing all entries in a single set
- Recent activity GUI showing the most recent collections (per-player) sorted by collection timestamp
- Leaderboard GUI showing top players globally and per-category, with the viewing player's rank pinned at the bottom
- LeaderboardManager with async refresh every `leaderboard.refresh-interval-seconds`
- Entity detection via `EntityDeathEvent` (player killer, including indirect kills) and `PlayerInteractEntityEvent` (name-tagging a mob). Variants are resolved on the entity instance, so killing a white sheep marks `sheep_white` collected
- Biome detection via a scheduled tick task that checks each online player's current biome every `detection.biome-check-interval` ticks
- Structure detection on chunk boundary crossing — `world.getStructures(chunk)` is consulted and every generated structure in the entered chunk is recorded
- Effect detection via `EntityPotionEffectEvent` (covers potions, splash, lingering, beacon, conduit, suspicious stew, area-effect clouds)
- Enchantment detection on `PlayerInventorySlotChangeEvent` — both applied enchantments and enchanted-book stored enchantments are recognised
- Full Brigadier command tree:
  - `/oaklog toast <on|off|milestones>` — toggle personal toast preference (defers to the BetterGUI-driven settings menu via `oaklog.toast` permission)
  - `/oaklog grant <player> <entry|category|set|all>` — admin grants; entry suggestions auto-complete from the in-memory registry (capped at 50)
  - `/oaklog revoke <player> <entry|category|set|all confirm>` — wiping all entries requires the literal `confirm` token
  - `/oaklog reload` — re-reads config.yml, messages.yml, biome-groups.yml, sets.yml, custom-entries.yml and re-runs the registry sync
  - `/oaklog info <entry>` — show entry id, display name, category, set memberships, and live/retired status
- Date-rotated audit log at `plugins/OakLog/audit-YYYY-MM-DD.log` writes one line per grant/revoke/reload action async; console gets a one-line summary
- PacketEvents-driven toast notifications: ToastService listens to ProgressEvent and dispatches a synthetic advancement packet that renders as a toast in the top-right of the screen. The packet is cleaned up 2 ticks later so the synthetic advancement doesn't pollute the player's advancement screen
- Toast frames escalate: per-entry collection = TASK, milestone (every N entries) = GOAL, category completion + full log completion = CHALLENGE
- Toast preference (`on`/`off`/`milestones`) per player, default `off` (configurable via `toasts.default-state`)
- Graceful no-op + one-time warning if PacketEvents isn't installed
- Search via chat (`config.yml > search.input: chat`) — Hub search button or `/oaklog search` (coming) prompts the player; their next chat message is captured and the search results show in a paginated GUI. Sign and anvil input modes are stubbed in v1 and fall back to chat — they'll land in a follow-up release
- PlaceholderAPI expansion (`oaklog`): `%oaklog_total_collected%`, `%oaklog_total_entries%`, `%oaklog_total_percent%`, `%oaklog_progress_<category>%`, `%oaklog_percent_<category>%`, `%oaklog_toast_state%`, `%oaklog_rank%`. Drop-in for BetterGUI player-settings menus, holograms, and the OakheartWeb website
- bStats metrics (relocated to `dev.oakheart.oaklog.bstats`) — Bukkit plugin ID placeholder until submission
- `biome-groups.yml` for organizing biomes by dimension and climate in the upcoming Biomes tab
