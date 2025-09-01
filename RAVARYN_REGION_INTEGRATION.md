# RavarynRegion Integration Notes

## Integration Required for Region Boundaries

The instructions specify that region boundaries should be handled through **RavarynRegion** integration. This is a custom/proprietary region plugin that needs to be integrated into the WeeklyElections plugin.

### Required Implementation:

1. **Dependency Addition**: Add RavarynRegion as a dependency in `plugin.yml`
2. **API Integration**: Import and use RavarynRegion's API to:
   - Check if a player is within a specific region
   - Get region boundaries for permission validation
   - Validate that role powers only work within region boundaries

### Integration Points:

1. **RoleAssignmentManager**: When checking if a player can use role powers, verify they are within the appropriate region boundaries using RavarynRegion API.

2. **ElectionManager**: Validate that only players within a region can participate in that region's elections.

3. **NPCManager**: Optionally validate that NPCs are placed within their designated region boundaries.

### Sample Integration Code (Placeholder):

```java
// In RoleAssignmentManager or a new RegionValidator class
public boolean isPlayerInRegion(Player player, String regionId) {
    // This would use RavarynRegion API
    // RavarynRegionAPI.getRegion(regionId).contains(player.getLocation())
    return true; // Placeholder - implement with actual API
}

public boolean canUseRolePowers(Player player, String regionId) {
    return hasActiveRole(player.getUniqueId().toString(), regionId) && 
           isPlayerInRegion(player, regionId);
}
```

### Configuration Required:

Update `regions.yml` to include region boundary definitions that match RavarynRegion's region IDs:

```yaml
regions:
  vojtechov:
    name: "Vojtěchov"
    display_name: "&e&lVojtěchov"
    ravaryn_region_id: "vojtechov_region"  # RavarynRegion ID
```

### Missing API Documentation:

Since RavarynRegion is a custom plugin, we need:
- RavarynRegion API documentation
- Maven/Gradle dependency information
- Example usage patterns
- Permission nodes and context requirements

**Note**: The current implementation provides all other required functionality but lacks RavarynRegion integration due to unavailable API documentation.
