package cz.domca.elections.regions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                    Region region = new Region(
                        regionId,
                        regionSection.getString("name"),
                        regionSection.getString("display_name")
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
        // This will be implemented with the election manager
        // For now, return the first region
        return regionRotation.isEmpty() ? null : regionRotation.get(0);
    }
    
    public static class Region {
        private final String id;
        private final String name;
        private final String displayName;
        
        public Region(String id, String name, String displayName) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
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
    }
}
