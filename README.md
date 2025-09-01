# Weekly Elections Plugin

A comprehensive Minecraft plugin for weekly regional elections with NPC integration, GUI-based interactions, and reputation system.

## Features

### Core Election System
- **4 predefined regions**: Vojtěchov, Třešín, Přibyslav, Drahošov
- **Weekly rotation**: Elections cycle through regions automatically
- **3 phases per election**:
  - Registration phase (7 days) - candidates register
  - Voting phase (7 days) - players vote for candidates  
  - Results/Mandate phase (30 days) - winners hold office

### Available Roles
- **Zalařník** (Jailer) - Manages punishments and jails in the region
- **Rychtář** (Reeve) - Local laws and regional bonuses
- **Správce obchodu** (Trade Manager) - Dynamic price adjustments in regional shop

### Election Requirements
- Minimum 20 hours playtime
- At least 1 quest point
- Minimum 1000 money

### NPC Integration
- Citizens NPCs in each region serve as election commissioners
- Click NPCs to access election interface
- NPCs automatically created and managed

### GUI System
- Main election menu with all options
- Registration form for role selection
- Voting interface with candidate listing
- Results display showing vote counts and percentages

### Reputation System
- **Election winner**: +10 reputation
- **Election candidate**: +2 reputation  
- **Voter**: +1 reputation
- Automatic prefix upgrades based on reputation levels
- LuckPerms integration for role permissions

### Database
- SQLite database for persistent storage
- Tracks elections, candidates, votes, role holders, reputation, and NPC locations
- HikariCP connection pooling for performance

## Installation

1. **Prerequisites**:
   - Paper/Spigot server 1.20.1+
   - LuckPerms plugin
   - Citizens plugin
   - HolographicDisplays (optional)

2. **Installation**:
   - Build the plugin using Maven: `mvn clean package`
   - Place the generated JAR in your `plugins/` folder
   - Restart the server
   - Configure the plugin as needed

## Configuration

### Main Config (`config.yml`)
```yaml
# Database settings
database:
  type: sqlite
  file: elections.db

# Election cycle settings  
election:
  registration_duration: 7  # days
  voting_duration: 7        # days
  mandate_duration: 30      # days

# Region rotation order
regions:
  - vojtechov
  - tresin
  - pribyslav
  - drahosov
```

### Regions (`regions.yml`)
- Configure region names and display names
- Define role details and LuckPerms groups
- Set reputation rewards

### Election Requirements (`elections_requirement.yml`)
- Set minimum requirements for participation
- Configure requirement failure messages

### GUI Configuration (`gui.yml`)
- Customize all GUI layouts and items
- Configure item positions, materials, and lore
- Set GUI titles and sizes

### Reputation System (`reputation_rewards.yml`)
- Configure reputation rewards for different actions
- Set prefix levels and their requirements
- Customize reputation messages

## Commands

### `/volby` (aliases: `/elections`)
- **Usage**: `/volby [subcommand]`
- **Permission**: `elections.use`

#### Subcommands:
- `/volby` - Opens main election GUI for current region
- `/volby reload` - Reloads all configuration files (admin)
- `/volby rotate` - Manually progress to next election phase (admin)
- `/volby reputation <player> <±amount>` - Modify player reputation (admin)
- `/volby fixnpcs` - Restore missing NPCs (admin)
- `/volby <region>` - Create NPC at your location for specified region (admin)

## Permissions

- `elections.use` - Basic permission to use elections (default: true)
- `elections.mod` - Moderator permissions (default: op)
- `elections.admin` - Administrator permissions (default: op)

## API Integration

### LuckPerms
- Automatic group assignment for elected officials
- Reputation-based prefix system
- Permission management for role holders

### Citizens
- NPC creation and management
- Click event handling
- Persistent NPC storage

### HolographicDisplays (Optional)
- Can be used for election information displays
- Supports hologram-based announcements

## Database Schema

The plugin uses SQLite with the following tables:
- `elections` - Election cycles and phases
- `candidates` - Registered candidates per election
- `votes` - Player votes and vote counts
- `role_holders` - Current and past office holders
- `reputation` - Player reputation scores
- `npc_locations` - NPC positions and data

## Development

### Building
```bash
mvn clean package
```

### Dependencies
- Paper API 1.20.1
- LuckPerms API 5.4
- Citizens API 2.0.31
- HolographicDisplays API 3.0.0
- HikariCP 5.0.1
- SQLite JDBC 3.42.0.0

### Architecture
The plugin follows a modular architecture:
- `WeeklyElectionsPlugin` - Main plugin class
- `ConfigManager` - Configuration handling
- `DatabaseManager` - Database operations
- `ElectionManager` - Election logic and data
- `RegionManager` - Region definitions and rotation
- `NPCManager` - NPC creation and management  
- `GuiManager` - GUI creation and handling
- `ReputationManager` - Reputation system
- `ElectionTask` - Automated election progression

## Support

For issues, feature requests, or questions, please:
1. Check the configuration files are properly set up
2. Verify all dependencies are installed
3. Check server logs for error messages
4. Ensure permissions are correctly configured

## License

This plugin is provided as-is for the Roleplay server. All rights reserved.
