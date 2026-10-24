# XaeroHeadTracker

Minecraft mod that shows other players' heads on [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map), with their name and a status tag (live, or time since the last position update).

Download: [Modrinth](https://modrinth.com/mod/xaeroheadtracker) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/xaeroheadtracker)

## Features

- Player heads, names and status (Live / time since last update) on the full-screen world map.
- Settings screen opened from the eye button on the map (or a keybinding), shown over the map. Choose who is visible and whether you share your own position.
- Settings are stored per server (singleplayer worlds have their own).

The mod must be installed on both the client and the server, since the server relays positions. Xaero's World Map is required on the client.

## Versions and branches

One branch per loader and Minecraft version. Current release: **2.1.x**.

| Loader | Minecraft | Branch | Version |
|---|---|---|---|
| Fabric | 1.21.1 | `v2/fabric/1.21.1` | 2.1.0 |
| Fabric | 1.21.11 | `v2/fabric/1.21.11` | 2.1.0 |
| Fabric | 26.2 | `v2/fabric/26.2` | 2.1.0 |
| Fabric | 26.3 | `v2/fabric/26.3` | 2.1.0 |
| NeoForge | 1.21.1 | `v2/neoforge/1.21.1` | 2.1.0 |
| NeoForge | 26.3 | `v2/neoforge/26.3` | 2.1.0 |
| Forge | 1.20.1 | `v2/forge/1.20.1` | 2.1.1 |

Jars for each release are attached to the [GitHub releases](../../releases), and tags are named `<version>+<loader>-<mc>`.
The 1.x versions are archived under the tags `v1/<loader>/<mc>`.

## Development

Each branch is a standalone Gradle project (`./gradlew build`, `./gradlew runClient`). See `AGENTS.md` on a v2 branch for the dev workflow (test world, simulation commands such as `/xht simulate live`).

## License

MIT.
