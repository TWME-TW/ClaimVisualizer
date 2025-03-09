# ClaimVisualizer

## Introduction
ClaimVisualizer is a Minecraft server plugin that visualizes claim boundaries created by the GriefDefender plugin using particle effects. Through intuitive visual effects, players can clearly see the range and boundaries of claims, avoiding accidental trespassing or land disputes.

## Features
- Uses different colored particles to mark different types of claims (basic, admin, subdivision, town)
- Supports four display modes: corners only (CORNERS), outline only (OUTLINE), complete boundary (FULL), wall surface (WALL)
- Complete 3D claim visualization, including top, bottom, sides, and horizontal lines at player height
- Each player can individually enable/disable visualization effects and select their preferred display mode
- Highly customizable settings including particle types, colors, density, and update frequency
- Performance optimizations: asynchronous processing, claim caching, smart render distance limits, batch particle display
- Smart display system: only shows claim parts near the player, reducing server load

## System Requirements
- Minecraft server version: Paper 1.21+
- Dependencies: GriefDefender

## Installation
1. Download the latest version of ClaimVisualizer.jar
2. Place the file in your server's `plugins` folder
3. Make sure GriefDefender plugin is installed
4. Restart the server or use a plugin manager to load the plugin
5. Done! The plugin will automatically create default configuration files

## Command List
- `/claimvisual` or `/cv` or `/claimviz` - Toggle claim visualization on/off
- `/claimvisual on` - Enable claim visualization
- `/claimvisual off` - Disable claim visualization
- `/claimvisual mode <mode>` - Set particle display mode (CORNERS, OUTLINE, FULL, WALL)
- `/claimvisual reload` - Reload plugin configuration
- `/claimvisual help` - Show help message

## Permissions
- `claimvisualizer.use` - Allow using basic visualization features (default: true)
- `claimvisualizer.reload` - Allow reloading plugin configuration (default: op)
- `claimvisualizer.autoenable` - Automatically enable claim visualization when joining the server (default: false)

## Configuration File
The plugin provides highly customizable configuration options. The configuration file is located at `plugins/ClaimVisualizer/config.yml`.

### Main Configuration Sections:

#### Particle Settings
```yaml
particles:
  # Update frequency (in ticks)
  update-interval: 10
  # Render distance (in blocks)
  render-distance: 20
  # Particle spacing (in blocks)
  spacing: 1.0
  # Particle display height (relative to ground)
  height: 1.0
  # Particle display interval (in ticks) - How often to display a batch of particles
  display-interval: 1
```

#### Claim Type Particle Settings
Each claim type (admin, basic, subdivision, town) can have customized particle effects for different parts:
- `bottom`: Bottom frame
- `top`: Top frame
- `horizontal`: Horizontal line at player's height
- `vertical`: Vertical connecting lines

For example:
```yaml
claim-types:
  basic:
    bottom:
      particle: REDSTONE
      color:
        red: 0
        green: 255
        blue: 0
    top:
      particle: REDSTONE
      color:
        red: 0
        green: 200
        blue: 0
```

#### Performance Settings
```yaml
performance:
  # Maximum number of claims rendered simultaneously
  max-claims: 10
  # Use asynchronous rendering
  async-rendering: true
  # Cache time (in seconds)
  cache-time: 10
```

#### Display Settings
```yaml
display:
  # Display mode: CORNERS (corner only), OUTLINE (outline only), FULL (complete boundary), WALL (wall surface)
  mode: OUTLINE
  # Show own claims
  show-own-claims: true
  # Show others' claims
  show-others-claims: true
  # Show admin claims
  show-admin-claims: true
  # Show town claims
  show-town-claims: true
  # Wall mode display radius
  wall-radius: 3.0
  # OUTLINE mode display radius
  outline-radius: 4.0
```

## Display Modes Explained
- **CORNERS**: Only shows the corners of the claim, most performance-friendly
- **OUTLINE**: Only shows the claim outline near the player, suitable for general use
- **FULL**: Shows the complete claim boundary, including all edges
- **WALL**: Shows particles in a wall style when player is near a claim, most intuitive

## FAQ
1. **Particle effects not showing?**
   - Make sure you've enabled visualization with `/cv on`
   - Check if you have the `claimvisualizer.use` permission
   - Confirm you're in a world with GriefDefender claims
   - Verify claims are within your render distance (`render-distance`)

2. **Plugin affecting server performance?**
   - Increase the `update-interval` value in the configuration
   - Decrease the `render-distance`
   - Increase `spacing` for particles
   - Reduce `max-claims` to limit simultaneous claim rendering
   - Use CORNERS or OUTLINE display mode

3. **Want to change particle colors?**
   - Modify the color values for the corresponding claim type in the `claim-types` section of the config file

4. **How to personalize particle display?**
   - Use the `/claimvisual mode <mode>` command to set your preferred display mode

## Performance Optimization Features
- Batch particle display to avoid lag from displaying too many particles simultaneously
- Smart rendering that only shows claim parts near the player
- Asynchronous processing of claim data calculations
- Distance-aware system that dynamically adjusts display content based on player position

## Author and Contributions
- Developer: twme
- If you find any issues or have suggestions, please submit an Issue or Pull Request

## License
This plugin is released under the MIT License.

---

Thank you for using ClaimVisualizer! We hope this plugin adds useful visualization features to your Minecraft server.
