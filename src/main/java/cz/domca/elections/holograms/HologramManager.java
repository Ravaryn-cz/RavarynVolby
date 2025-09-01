package cz.domca.elections.holograms;

import cz.domca.elections.WeeklyElectionsPlugin;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

public class HologramManager {
    
    private final WeeklyElectionsPlugin plugin;
    private final Map<String, Object> holograms;
    private final boolean holographicDisplaysEnabled;
    
    public HologramManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
        this.holograms = new HashMap<>();
        this.holographicDisplaysEnabled = plugin.getServer().getPluginManager().isPluginEnabled("HolographicDisplays");
        
        if (holographicDisplaysEnabled) {
            plugin.getLogger().info("HolographicDisplays found - enabling hologram support");
        } else {
            plugin.getLogger().info("HolographicDisplays not found - using basic text display");
        }
    }
    
    public void createElectionHologram(String regionId, Location location) {
        if (holograms.containsKey(regionId)) {
            removeHologram(regionId);
        }
        
        String regionName = plugin.getRegionManager().getRegion(regionId).getDisplayName();
        
        if (holographicDisplaysEnabled) {
            createHolographicDisplaysHologram(regionId, location, regionName);
        } else {
            // Fallback: Create armor stand or other entity with custom name
            createArmorStandHologram(regionId, location, regionName);
        }
    }
    
    private void createHolographicDisplaysHologram(String regionId, Location location, String regionName) {
        try {
            // Use reflection to avoid compile-time dependency on HolographicDisplays
            Class<?> holographicDisplaysAPIClass = Class.forName("me.filoghost.holographicdisplays.api.HolographicDisplaysAPI");
            Class<?> hologramClass = Class.forName("me.filoghost.holographicdisplays.api.hologram.Hologram");
            
            Object api = holographicDisplaysAPIClass.getMethod("get", org.bukkit.plugin.Plugin.class).invoke(null, plugin);
            Object hologram = holographicDisplaysAPIClass.getMethod("createHologram", Location.class).invoke(api, location.add(0, 2.5, 0));
            
            // Add lines
            Object textLine1 = hologramClass.getMethod("getLines").invoke(hologram);
            Class<?> hologramLinesClass = Class.forName("me.filoghost.holographicdisplays.api.hologram.line.HologramLines");
            hologramLinesClass.getMethod("appendText", String.class).invoke(textLine1, "§a§lVolby");
            hologramLinesClass.getMethod("appendText", String.class).invoke(textLine1, "§7Oblast: " + regionName);
            
            holograms.put(regionId, hologram);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create HolographicDisplays hologram, falling back to armor stand: " + e.getMessage());
            createArmorStandHologram(regionId, location, regionName);
        }
    }
    
    private void createArmorStandHologram(String regionId, Location location, String regionName) {
        try {
            Location hologramLoc = location.clone().add(0, 2.5, 0);
            org.bukkit.entity.ArmorStand armorStand = hologramLoc.getWorld().spawn(hologramLoc, org.bukkit.entity.ArmorStand.class);
            
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setCanPickupItems(false);
            armorStand.setCustomName("§a§lVolby\n§7Oblast: " + regionName);
            armorStand.setCustomNameVisible(true);
            armorStand.setBasePlate(false);
            armorStand.setArms(false);
            armorStand.setInvulnerable(true);
            armorStand.setMarker(true);
            
            // Add metadata to identify this as election hologram
            armorStand.setMetadata("election_hologram", new org.bukkit.metadata.FixedMetadataValue(plugin, regionId));
            
            holograms.put(regionId, armorStand);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create hologram for region " + regionId + ": " + e.getMessage());
        }
    }
    
    public void removeHologram(String regionId) {
        Object hologram = holograms.get(regionId);
        if (hologram == null) return;
        
        try {
            if (holographicDisplaysEnabled && hologram.getClass().getSimpleName().equals("HologramImpl")) {
                // Remove HolographicDisplays hologram
                hologram.getClass().getMethod("delete").invoke(hologram);
            } else if (hologram instanceof org.bukkit.entity.ArmorStand) {
                // Remove armor stand
                ((org.bukkit.entity.ArmorStand) hologram).remove();
            }
            
            holograms.remove(regionId);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove hologram for region " + regionId + ": " + e.getMessage());
        }
    }
    
    public void updateHologram(String regionId) {
        if (!holograms.containsKey(regionId)) return;
        
        // For now, just recreate the hologram
        // In a more advanced implementation, you could update the text based on election phase
        Object hologram = holograms.get(regionId);
        if (hologram instanceof org.bukkit.entity.ArmorStand) {
            org.bukkit.entity.ArmorStand armorStand = (org.bukkit.entity.ArmorStand) hologram;
            Location location = armorStand.getLocation();
            removeHologram(regionId);
            createElectionHologram(regionId, location);
        }
    }
    
    public void removeAllHolograms() {
        for (String regionId : new HashMap<>(holograms).keySet()) {
            removeHologram(regionId);
        }
    }
    
    public boolean hasHologram(String regionId) {
        return holograms.containsKey(regionId);
    }
}
