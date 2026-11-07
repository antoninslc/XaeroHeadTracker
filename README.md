# XaeroHeadTracker

Minecraft mod that shows other players' heads on [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map), with their name and a status tag (live, or time since the last position update).

Download: [Modrinth](https://modrinth.com/mod/xaero-head-tracker) | [CurseForge](https://www.curseforge.com/projects/1597062)

## Features

- Player heads, names and status (Live / time since last update) on the full-screen world map.
- Settings screen opened from the eye button on the map (or a keybinding), shown over the map. Choose who is visible and whether you share your own position.
- Settings are stored per server (singleplayer worlds have their own).

The mod must be installed on both the client and the server, since the server relays positions. Xaero's World Map is required on the client.

## Versions and branches

One branch per loader and Minecraft version; Fabric builds all its versions from the single branch `v2/fabric/all` (Stonecutter). Current release: **2.3.0 (beta)**.

| Loader | Minecraft | Branch | Version |
|---|---|---|---|
| Fabric | 1.21.1 | `v2/fabric/all` | 2.3.0 (beta) |
| Fabric | 1.21.11 | `v2/fabric/all` | 2.3.0 (beta) |
| Fabric | 26.2 | `v2/fabric/all` | 2.3.0 (beta) |
| Fabric | 26.3 | `v2/fabric/all` | 2.3.0 (beta) |
| NeoForge | 1.21.1 | `v2/neoforge/1.21.1` | 2.3.0 (beta) |
| NeoForge | 26.3 | `v2/neoforge/26.3` | 2.3.0 (beta) |
| Forge | 1.20.1 | `v2/forge/1.20.1` | 2.3.0 (beta) |

Jars for each release are attached to the [GitHub releases](../../releases), with one tag per version (for example `2.3.0`).
The 1.x versions are archived under the tags `v1/<loader>/<mc>`.

## Development

Each branch is a standalone Gradle project (`./gradlew build`, `./gradlew runClient`); on `v2/fabric/all`, `./gradlew chiseledBuild` builds every Fabric version. In the dev client, `/xht simulate live|disconnect|old|clear` fakes a second player on the map.

## License

MIT.
