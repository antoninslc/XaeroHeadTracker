# XaeroHeadTracker

XaeroHeadTracker is a Minecraft mod designed to display custom player head icons and status indicators as an overlay on top of Xaero's World Map interface. It allows players on a server to visualize each other's live positions and status directly on the map screen.

## Features

- **Player Head Overlay**: Renders player head icons on the full-screen Xaero's World Map.
- **Status Indicators**: Displays player usernames and last-updated status tags (e.g., Live activity or time elapsed since the last position update).
- **In-Game Settings**: Features an interactive overlay button (eye icon) on the map interface to configure display options on the fly.
- **Keybinding Integration**: Includes configurable keybindings to access settings directly.

## Dependencies

### Client Side
- **Minecraft** (Supported version matching your build, e.g., 1.21.x)
- **Fabric Loader**
- **Fabric API**
- **Xaero's World Map**

### Server Side
- **Fabric Loader**
- **Fabric API**

> **Note**: Both client and server must have the mod installed to communicate player positions across the network.

## Installation

### Server Installation
1. Ensure your server is running the Fabric Loader.
2. Place `fabric-api.jar` into your server's `mods` folder.
3. Place `xaeroheadtracker-[version].jar` into the server's `mods` folder.
4. Restart the server.

### Client Installation
1. Install Fabric Loader for your Minecraft launcher.
2. Place `fabric-api.jar` into your `.minecraft/mods` directory.
3. Place `XaerosWorldMap_[version].jar` into your `.minecraft/mods` directory.
4. Place `xaeroheadtracker-[version].jar` into your `.minecraft/mods` directory.
5. Launch the game using the Fabric profile.

## Setup & Development

For setting up the development environment, please refer to the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) corresponding to your IDE.

## License

This project is available under the MIT license.
