# XaeroHeadTracker

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## Dev workflow (to set up at the start of every new version)

When starting a new Minecraft version or a new loader branch, take the time to set this up first, before any other work:

1. **Direct world launch** in `build.gradle`, so `runClient` from IntelliJ opens the test world without menus:
   - Fabric: in `loom.runs.client`, add `programArgs "--quickPlaySingleplayer", "New World (6)"`.
   - NeoForge: in `neoForge.runs.client`, add `programArguments.addAll('--quickPlaySingleplayer', 'New World (6)')`.
2. **Simulation commands** in `XaeroHeadTrackerClient.java` (dev environment only), adapted to the version's API
   (`ClientCommands` on Fabric, `RegisterClientCommandsEvent` on NeoForge):
   - `/xht simulate live`: spawns `BotTest` 15 blocks away with the player's head texture, status Live.
   - `/xht simulate disconnect`: simulates the disconnection of `BotTest` (tests the skin cache, shows `< 1 min`).
   - `/xht simulate old`: sets the disconnection counter of `BotTest` to 5 min.
   - `/xht simulate clear`: removes `BotTest` from the map.

Reference implementations: branches `fabric/26.3` and `neoforge/26.3`.

After a Gradle reload, run `runClient` (preferably in Debug mode for HotSwap with Ctrl + F9).

## License

This template is available under the MIT license.
