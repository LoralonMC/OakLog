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
- `biome-groups.yml` for organizing biomes by dimension and climate in the upcoming Biomes tab
