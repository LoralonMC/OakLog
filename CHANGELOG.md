# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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
- `biome-groups.yml` for organizing biomes by dimension and climate in the upcoming Biomes tab
