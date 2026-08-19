# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.2.1] - 2026-08-19

### Fixed

- Paintings are collectable. Every painting variant had an entry (`VanillaEnumerator#isCollectableEntity` whitelists `PAINTING`) and no way to reach it: a painting is not a `LivingEntity`, so `EntityDeathEvent` never fires for one, and breaking it goes to `HangingBreakByEntityEvent`, which nothing listened for. All 51 variants sat at zero collections across 442 players. Detection now credits the painting's variant both when a player hangs one (`HangingPlaceEvent`, where vanilla has already rolled the variant in `Painting#create` and applied any variant stored on the item, so the art is final) and when a player breaks one.
- Instant Health and Instant Damage are collectable. Vanilla splits potion application in `PotionContents#applyToLivingEntity`: an instantaneous effect goes straight to `MobEffect#applyInstantenousEffect` and never touches `LivingEntity#addEffect`, which is the only place CraftBukkit fires `EntityPotionEffectEvent`. Neither effect could ever be seen. They are now read off the potion itself on the three paths a player meets one: drinking (`PlayerItemConsumeEvent`), being splashed (`PotionSplashEvent`, skipping anyone whose intensity another plugin zeroed), and standing in a lingering cloud (`AreaEffectCloudApplyEvent`). Timed effects from those same potions are still left to the effect event rather than marked twice.
- Structure bounding boxes are no longer a block short. Vanilla stores structure boxes as inclusive block coordinates and `CraftGeneratedStructure#getBoundingBox` copies `minX..maxX` straight into a Bukkit `BoundingBox`, whose max is exclusive, so every box lost its `+X/+Y/+Z` edge. Buried Treasure is a single 1x1x1 piece around the chest, which collapsed the box to zero volume: containment demanded exactly integral player coordinates, so standing on the chest at `x.5, z.5` never matched. Confirmed against live data, where 45 players held a Heart of the Sea, which drops from nowhere else, and none had the structure. Boxes are now expanded by one block on the max corner before any containment or footprint test.

### Changed

- Default `detection.excluded-entries` now covers three entity groups that cannot occur in survival, verified against the 26.1.2 sources: baby parrots (`Parrot#isBaby()` is hard-coded to `false`, and the mob can neither breed nor spawn as a baby), the baby zombie horse (its natural spawn is `minCount = maxCount = 1`, and `AgeableMob#finalizeSpawn` only rolls a baby from the second mob of a group on; it also cannot breed or age up), and the four painting variants outside the `#placeable` tag (Earth, Wind, Fire, Water), which no vanilla structure template places either.

## [1.2.0] - 2026-08-01

### Fixed

- Structures whose recorded bounding box sits at the wrong height are now detectable. Vanilla's surface template pieces re-derive the terrain height when they place their blocks, shift the template down to it, write, and then restore the original position — so the saved `StructureStart` box keeps the height that was sampled at the chunk centre before the move. On flat ground the two agree; on a slope they diverge by the slope delta. Confirmed live on an igloo whose box was `y75..94` while the building's floor was at `y63` — a 27-block error, with nothing but air throughout the recorded box. No amount of standing inside such a structure could ever satisfy a 3D containment test.
- Detection now matches structures on X/Z alone in two cases. First, a horizontal footprint of `detection.structure-small-footprint` (default 16) blocks or less — the move that breaks the box is vertical only, so X/Z are always correct, and for a structure a few blocks wide, being inside its column is conclusive. Second, regardless of size, a box that sits entirely above the terrain: nothing generates floating in the sky, so such a box is provably misplaced. Everything else keeps the full 3D test, preserving the 1.0.0 behaviour where walking over a mineshaft or ancient city doesn't collect it. Trade-off: standing directly beneath an affected structure now counts. Set the size key to 0 to disable the footprint rule; the floating-box rule always applies.
- Audited every region file in all five populated dimensions (866 files, ~6,600 structure starts, 45 block samples spread through each recorded box, restricted to chunks at `Status: minecraft:full`). **Igloo is the only structure with confirmed misplaced boxes**: 9 of 21 sampled igloos, every one of them recording its box at exactly `y90..94` while the terrain sat at y67..81 — a fixed nominal anchor rather than random error, matching the hand-verified case. Everything else came back clean, including Mineshaft, Trial Chambers, Fortress, Bastion, Ancient City, Monument, Stronghold, Mansion, every village type, Desert Pyramid, Jungle Pyramid and Pillager Outpost, so the 3D check is preserved everywhere it matters. Two other non-zero results were investigated and are not this bug: Nether Fossil starts that placed no blocks at all (verified — no bone blocks anywhere in the column), and one Buried Treasure whose chest had presumably been dug up. All confirmed cases have a footprint of 10x10 or less, so the size rule alone would have covered them; the floating-box rule is retained as a guard for wider cases rather than because one was found.

### Changed

- `/oaklog debug structures` now reports the world surface height at the player's column, whether each structure is being matched `xz-only` or `3d`, and flags any structure the player is inside horizontally but not vertically as a likely wrong-height box.

## [1.1.1] - 2026-08-01

### Added

- `/oaklog debug structures` (permission `oaklog.admin.info`) dumps every input the structure detection path uses at the player's current position: position and chunk, whether the poll task is running and at what interval, the master switch, game mode and whether it is suppressed, whether the player's progress is loaded, and for the 3×3 chunk neighbourhood every `GeneratedStructure` with its bounding box, whether the box contains the player, the resolved entry id, and whether it is already collected. Bypasses the per-player box memo and re-queries, so the output reflects the world rather than the cache.

## [1.1.0] - 2026-08-01

### Added

- `detection.structure-check-interval` config option (default `20` ticks) polls whether each online player is standing inside a structure's bounding box, alongside the existing chunk-cross / join / teleport / world-change samples. Set to `0` for event-only sampling; takes effect on restart. `detection.structure-check-on-chunk-change` is now the master on/off switch for structure detection and gates the poll too.

### Changed

- Structure bounding boxes for the chunk a player occupies are memoized per player and invalidated on chunk change or quit, so the poll costs a map lookup and a long compare in the common case rather than a `Chunk#getStructures()` query per player per interval.

### Fixed

- Structures small enough to fit inside a single chunk (Igloo, Swamp Hut, Jungle Pyramid) were credited only by luck. Detection sampled the player's position when they crossed a chunk boundary, and the bounding-box containment requirement added in 1.0.0 means that sample has to land *inside* the structure. A real igloo measured off the live world spans x[4144..4150] z[3358..3367] with its dome entirely inside one chunk, so the ordinary walk-in / stand-around / walk-out path produced one sample, at the chunk border, outside the box. Credit happened only when a chunk line ran through the part of the structure the player was in, or when they logged in / teleported directly inside. The poll makes it reliable. Large structures were never affected, since you can't walk through a village or mansion without also crossing chunk lines.
- Note: this does not backfill. A structure visited before this release stays uncollected until the player is inside its bounding box again.
- Structure detection no longer risks a synchronous chunk load on the main thread. `PlayerTeleportEvent` fires before the player arrives, so `getTo()` can point at an unloaded chunk (long-range warps, RTP), and reading `Location#getChunk()` there would load/generate it inline. The listener now skips unloaded chunks and lets the poll pick the structure up once the player is actually there.
- The `PlayerMoveEvent` handler compares chunk coordinates arithmetically instead of resolving two `Chunk` objects per move packet.

## [1.0.0] - 2026-07-11

### Added

- `detection.ignored-gamemodes` config option (default `[CREATIVE, SPECTATOR]`) suppresses collection logging while a player is in any listed game mode. This closes the exploit where the creative inventory trivially fills the Items/Blocks/Enchantments logs by clicking through the creative menu (which fires the same `PlayerInventorySlotChangeEvent` used for detection), and stops spectators from auto-collecting biomes and structures by flying through them. The gate is enforced at the single detection chokepoint so it covers all categories uniformly. Admin `/oaklog grant` is unaffected, and already-earned advancements still backfill on join regardless of the player's current mode. Set to `[]` to log in every game mode. Existing configs pick up the new key automatically on next startup.

### Changed

- Config now syncs leading-comment changes from the bundled defaults onto existing `config.yml` files on startup (via OakheartLib's `syncComments`), in addition to the existing missing-key merge. A persisted baseline at `plugins/OakLog/.oakheart/config-baseline.yml` is used as the 3-way merge base, so admin-customized comments are never overwritten — only comments the admin left at their default value pick up upstream wording changes. The first run after this update just seeds the baseline; comment syncing takes effect from the next update onward.

### Added

- Entry lore in `messages.yml` (`gui.entry.lore-collected` / `gui.entry.lore-uncollected`) now accepts a list of lines, each rendered as its own lore row — so the "Obtained" date can sit on a line below the collection status instead of sharing one line. A single string still works for one-line lore, and a blank `""` list entry renders as a spacer. The default config now ships the collected/uncollected lore in the two-line list form. Existing servers keep their current single-line value untouched (it is not auto-converted — edit it into a list to opt in).
- Hub GUI layout reflows the eight categories into a 4×2 spread grid across rows 1 and 2 (slots 10/12/14/16 and 19/21/23/25), with the Recent / Sets / Search / Leaderboard action buttons pushed down one row from row 3 to row 4 (slots 37/39/41/43). The single 8-category row introduced last release was left-justified after the Advancements addition; the new grid is symmetric and matches the visual rhythm of the action row below.

- New **Advancements** category (8th category) tracks every player-facing vanilla and datapack advancement. Detection is event-driven via `PlayerAdvancementDoneEvent` and fires a toast on completion. The first time a player joins after this category is enabled, their already-completed advancements are silently backfilled (no toast spam). Recipe advancements (`recipes/*`), root tab nodes (`*/root`), and any advancement without a display block are filtered out — matching how the vanilla Advancements screen decides what to show. Each entry's icon and model data are extracted from the advancement's `display.icon()` so vanilla and custom-pack advancements both render their authentic icon. Hub, Category, and Leaderboard GUIs all include the new tab. The per-category search button moved from row 0 slot 7 to row 5 slot 46 to make room for the eighth tab; the leaderboard "back" button moved from row 0 slot 8 to row 5 slot 53 for the same reason.
- `ProgressManager.markCollectedSilent(uuid, entryId)` for backfill paths that need to mark entries without firing `ProgressEvent` (and therefore without dispatching a toast).
- Taming detection: wolves, cats, parrots and other tameable mobs are now collected the moment they're tamed via `EntityTameEvent`. Horses, donkeys, mules, llamas, trader llamas, and camels are additionally caught on the first right-click after taming as a safety net for the AbstractHorse code path. Previously, tamed mobs were only collected if the player also killed them.
- Villager-trade detection: trading with a villager or wandering trader now collects that entity via `PlayerTradeEvent`. Previously the only way to collect a villager was to kill it or name-tag it.
- Vehicle collection on right-click: right-clicking a boat or minecart now collects it. Previously these were only collectable by killing them.
- Structure detection now runs on player join, teleport, and world change in addition to chunk-cross. Closes the hole where a player logging in or teleporting directly inside a structure (Mineshaft, Trial Chamber, Ancient City) wouldn't get it marked until they walked to a fresh chunk.
- Per-biome and per-structure icon defaults (`enumeration/IconDefaults.java`) replace the blanket `GRASS_BLOCK` / `COBBLESTONE` placeholders. Each vanilla biome and structure now ships with a thematically appropriate block icon — e.g. plains → grass block, desert → sand, swamp → lily pad, cherry grove → cherry sapling, deep dark → sculk, the end → end stone, nether wastes → netherrack, ancient city → sculk shrieker, trial chambers → copper bulb, stronghold → end portal frame, monument → prismarine bricks, mansion → dark oak planks, village (per biome variant) → matching planks/sandstone. 65 biome icons and 34 structure icons in total. Modded biomes/structures and any vanilla entry not in the map continue to fall back to the generic defaults. Existing installs auto-migrate on next startup via the normal registry-sync display-data refresh.

### Fixed

- Collected entries past a player's 64 most-recent collections showed a blank "Obtained" date in their GUI lore, even though the timestamp was stored in the database. On login only the recent-64 buffer carried timestamps into memory while the rest of the collected set was reduced to bare IDs; player progress now retains the collected-at timestamp for the entire collected set, so the `<date>` placeholder resolves for every collected entry.
- OakTools / OakPets / OakTags custom items were silently logged as their underlying vanilla material (e.g. the "Groundbreaker" excavator showing up as `STONE_SHOVEL`) when OakLog happened to enable before the host plugin. The three Oakheart adapters resolve their host via `PluginManager#getPlugins()` at `onEnable` time, and `paper-plugin.yml` didn't declare a load order — so on cold start the host wasn't always present yet and the adapter was dropped, blocking both enumeration and detection. OakTools, OakPets, and OakTags are now declared as `load: BEFORE` soft dependencies.
- Sickle tier detection used the wrong PDC key (`oaktools:tool_tier` instead of `oaktools:tier`), so tiered sickles never matched their `sickle_<tier>` entry and fell back to their vanilla hoe material.

- Name-tag interaction no longer marks an entity unless the held name tag is actually renamed. Previously, right-clicking a mob with a blank name tag would falsely mark it — even though vanilla treats that interaction as a no-op.

- Structure detection no longer credits a player just for walking through a chunk whose bounding box contains an underground structure. Previously, walking on the surface above a Mineshaft or Trial Chamber would mark it collected because `Chunk#getStructures()` returns every structure whose bounding box intersects the chunk, regardless of the player's Y. The listener now requires the player's position to actually be inside the structure's bounding box.

- Typing `cancel` at the search prompt now cancels the search instead of searching for the literal string "cancel" — matching the behaviour the prompt message already advertised. Case-insensitive.

- The search button in the per-category GUI now actually opens the search prompt (previously it printed a "Search coming soon!" placeholder). Cancelling or submitting an empty query returns to the same category/page/filter the player was viewing, rather than bouncing back to the hub.

- Custom items recognized by an adapter (Nexo, ExecutableItems, HMCWraps, etc.) whose entry is excluded via `detection.excluded-entries` no longer fall through to the vanilla material entry. Previously, picking up a custom Nexo elytra whose entry was excluded would silently log the vanilla `minecraft:elytra` entry instead. Adapter recognition is now treated as an identity claim — excluding a custom item means "don't track this item", not "log it as the underlying material instead". Adapter-to-adapter redirect chains (e.g. `oaktools:sickle` excluded → `nexo:wooden_sickle` claims it) still work as before.

### Changed

- Default `config.yml` no longer ships with Oakheart-server-specific entry exclusions (`nm_*`, `md_*_head`, `*_petal_wings`, Spectra/Lands menu items, OakTools/PhoenixCrates/ExecutableItems duplicate redirects, etc.). The default now contains only universally-applicable vanilla-unobtainable-mob exclusions, plus commented examples showing common patterns admins can adapt. Existing installations are unaffected — their live `config.yml` retains any prior exclusions.

### Added

- Debug logging in `ItemBlockListener` for the "adapter recognized but excluded" path — with `debug: true`, the console now prints which adapter recognized the item and which excluded key blocked it, making mis-configured exclusions easy to diagnose.

- New entity variant resolvers for Paper 26.1's variant-bearing mobs:
  - **Pig**: cold / warm / temperate (via `RegistryKey.PIG_VARIANT`)
  - **Chicken**: cold / warm / temperate (via `RegistryKey.CHICKEN_VARIANT`)
  - **Salmon**: small / medium / large (via `Salmon.Variant` enum)
  - **Creeper**: powered (charged) creepers are now a separate entry from normal creepers, using a new `hasBaseEntry()` resolver flag that preserves the existing `entity_creeper` entry alongside `entity_creeper_powered`
- Baby variants now count as separate entries for every Ageable mob — sheep colors, cow climate variants, villager type×profession, horse color×style, zombie/piglin/villager, and every other naturally breedable or natural-spawn-baby mob each get a paired `_baby` entry (display name prefixed with "Baby "). Detected via `Ageable#isAdult()` at kill / name-tag time.

### Changed

- The `Pig`, `Chicken`, and `Salmon` entries are now split by variant. Existing player progress on the old `entity_pig`, `entity_chicken`, and `entity_salmon` keys is retired (preserved in the database but hidden from totals). The variants populate as players encounter them.

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
