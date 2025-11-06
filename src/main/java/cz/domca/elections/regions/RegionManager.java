package cz.domca.elections.regions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import cz.domca.elections.WeeklyElectionsPlugin;

public class RegionManager {
    
    private final WeeklyElectionsPlugin plugin;
    private final Map<String, Region> regions;
    private final List<String> regionRotation;
    
    public RegionManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
        this.regions = new HashMap<>();
        this.regionRotation = new ArrayList<>();
    }
    
    public void loadRegions() {
        ConfigurationSection regionsConfig = plugin.getConfigManager().getConfig("regions.yml").getConfigurationSection("regions");
        List<String> rotationOrder = plugin.getConfigManager().getConfig("config.yml").getStringList("regions");
        
        if (regionsConfig != null) {
            for (String regionId : regionsConfig.getKeys(false)) {
                ConfigurationSection regionSection = regionsConfig.getConfigurationSection(regionId);
                if (regionSection != null) {
                    // Load boundary information if present
                    RegionBoundary boundary = null;
                    ConfigurationSection boundarySection = regionSection.getConfigurationSection("boundary");
                    if (boundarySection != null) {
                        String world = boundarySection.getString("world");
                        if (world != null) {
                            double minX = boundarySection.getDouble("min_x");
                            double minY = boundarySection.getDouble("min_y");
                            double minZ = boundarySection.getDouble("min_z");
                            double maxX = boundarySection.getDouble("max_x");
                            double maxY = boundarySection.getDouble("max_y");
                            double maxZ = boundarySection.getDouble("max_z");
                            
                            boundary = new RegionBoundary(world, minX, minY, minZ, maxX, maxY, maxZ);
                        }
                    }
                    
                    Region region = new Region(
                        regionId,
                        regionSection.getString("name"),
                        regionSection.getString("display_name"),
                        boundary
                    );
                    regions.put(regionId, region);
                }
            }
        }
        
        // Load rotation order
        regionRotation.clear();
        regionRotation.addAll(rotationOrder);
        
        plugin.getLogger().info("Loaded " + regions.size() + " regions");
    }
    
    public Region getRegion(String regionId) {
        return regions.get(regionId);
    }
    
    public Map<String, Region> getAllRegions() {
        return new HashMap<>(regions);
    }
    
    public List<String> getRegionRotation() {
        return new ArrayList<>(regionRotation);
    }
    
    public String getNextRegion(String currentRegion) {
        int currentIndex = regionRotation.indexOf(currentRegion);
        if (currentIndex == -1) {
            return regionRotation.get(0); // Return first region if current not found
        }
        
        int nextIndex = (currentIndex + 1) % regionRotation.size();
        return regionRotation.get(nextIndex);
    }
    
    public String getCurrentActiveRegion() {
        // Get the region from current active election
        if (plugin.getElectionManager() != null && plugin.getElectionManager().isElectionActive()) {
            return plugin.getElectionManager().getCurrentElection().getRegionId();
        }
        // If no active election, return the first region as fallback
        return regionRotation.isEmpty() ? null : regionRotation.get(0);
    }
    
    /**
     * Check if a player is within a specific region
     */
    public boolean isPlayerInRegion(Location playerLocation, String regionId) {
        Region region = regions.get(regionId);
        if (region == null || region.getBoundary() == null) {
            return false; // No boundary defined, assume player is not in region
        }
        
        return region.getBoundary().contains(playerLocation);
    }
    
    /**
     * Get the region that contains the given location
     */
    public String getRegionAt(Location location) {
        for (Region region : regions.values()) {
            if (region.getBoundary() != null && region.getBoundary().contains(location)) {
                return region.getId();
            }
        }
        return null; // No region found
    }
    
    /**
     * Check if a player has permission to use role abilities in their current location
     */
    public boolean canUseRoleAbilities(Location playerLocation, String regionId) {
        return isPlayerInRegion(playerLocation, regionId);
    }
    
    public static class Region {
        private final String id;
        private final String name;
        private final String displayName;
        private final RegionBoundary boundary;
        
        public Region(String id, String name, String displayName, RegionBoundary boundary) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.boundary = boundary;
        }
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public RegionBoundary getBoundary() {
            return boundary;
        }
        
        public boolean hasBoundary() {
            return boundary != null;
        }
    }
    
    public static class RegionBoundary {
        private final String worldName;
        private final double minX, minY, minZ;
        private final double maxX, maxY, maxZ;
        
        public RegionBoundary(String worldName, double minX, double minY, double minZ, 
                            double maxX, double maxY, double maxZ) {
            this.worldName = worldName;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }
        
        public boolean contains(Location location) {
            if (location == null || location.getWorld() == null) {
                return false;
            }
            
            if (!location.getWorld().getName().equals(worldName)) {
                return false;
            }
            
            double x = location.getX();
            double y = location.getY();
            double z = location.getZ();
            
            return x >= minX && x <= maxX && 
                   y >= minY && y <= maxY && 
                   z >= minZ && z <= maxZ;
        }
        
        public String getWorldName() {
            return worldName;
        }
        
        public double getMinX() { return minX; }
        public double getMinY() { return minY; }
        public double getMinZ() { return minZ; }
        public double getMaxX() { return maxX; }
        public double getMaxY() { return maxY; }
        public double getMaxZ() { return maxZ; }
    }
}
