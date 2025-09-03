package cz.domca.elections.npc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import cz.domca.elections.WeeklyElectionsPlugin;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;

public class NPCManager {
    
    private final WeeklyElectionsPlugin plugin;
    private final NPCRegistry npcRegistry;
    private final Map<String, NPC> electionNPCs;
    
    public NPCManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
        this.npcRegistry = CitizensAPI.getNPCRegistry();
        this.electionNPCs = new HashMap<>();
    }
    
    public void initializeNPCs() {
        loadExistingNPCs();
        plugin.getLogger().info("NPC Manager initialized");
    }
    
    private void loadExistingNPCs() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT * FROM npc_locations";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    try {
                        String regionId = rs.getString("region_id");
                        int npcId = rs.getInt("npc_id");
                        
                        if (npcId > 0) {
                            NPC npc = npcRegistry.getById(npcId);
                            if (npc != null && npc.isSpawned()) {
                                electionNPCs.put(regionId, npc);
                                plugin.getLogger().info("Loaded existing NPC for region: " + regionId);
                            } else {
                                // NPC doesn't exist anymore, recreate it
                                plugin.getLogger().warning("NPC " + npcId + " for region " + regionId + " not found, recreating...");
                                recreateNPC(regionId, rs);
                            }
                        } else {
                            // No NPC ID stored, try to recreate
                            plugin.getLogger().info("No NPC ID for region " + regionId + ", recreating...");
                            recreateNPC(regionId, rs);
                        }
                    } catch (Exception ex) {
                        plugin.getLogger().log(Level.WARNING, "Failed to load NPC for region " + rs.getString("region_id") + ", skipping...", ex);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load existing NPCs", e);
        }
    }
    
    private void recreateNPC(String regionId, ResultSet rs) {
        try {
            String world = rs.getString("world");
            double x = rs.getDouble("x");
            double y = rs.getDouble("y");
            double z = rs.getDouble("z");
            float yaw = rs.getFloat("yaw");
            float pitch = rs.getFloat("pitch");
            
            Location location = new Location(
                plugin.getServer().getWorld(world),
                x, y, z, yaw, pitch
            );
            
            if (location.getWorld() != null) {
                createNPC(regionId, location);
            } else {
                plugin.getLogger().warning("Cannot recreate NPC for region " + regionId + ": World " + world + " not loaded");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read NPC data for region " + regionId, e);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to recreate NPC for region " + regionId, e);
        }
    }
    
    public boolean createNPC(String regionId, Location location) {
        if (electionNPCs.containsKey(regionId)) {
            return false; // NPC already exists
        }
        
        // Validate location
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("Cannot create NPC for region " + regionId + ": Invalid location or world not loaded");
            return false;
        }
        
        try {
            // Create NPC
            NPC npc = npcRegistry.createNPC(EntityType.PLAYER, "Volební komisař");
            npc.spawn(location);
            
            // Configure NPC
            npc.data().set(NPC.Metadata.NAMEPLATE_VISIBLE, true);
            npc.data().set(NPC.Metadata.COLLIDABLE, false);
            
            // Configure entity properties if available
            if (npc.getEntity() != null) {
                npc.getEntity().setGravity(false);
            } else {
                // Schedule entity configuration for later when it's available
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (npc.getEntity() != null) {
                        npc.getEntity().setGravity(false);
                    }
                }, 1L); // Wait 1 tick
            }
            
            // Set metadata for identification
            npc.data().set("region_id", regionId);
            npc.data().set("role", "election_commissioner");
            
            // Store in database
            saveNPCLocation(regionId, location, npc.getId());
            
            // Store in memory
            electionNPCs.put(regionId, npc);
            
            // Create hologram
            plugin.getHologramManager().createElectionHologram(regionId, location);
            
            plugin.getLogger().info("Created election NPC for region: " + regionId);
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create NPC for region " + regionId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private void saveNPCLocation(String regionId, Location location, int npcId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "INSERT OR REPLACE INTO npc_locations (region_id, world, x, y, z, yaw, pitch, npc_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, regionId);
                stmt.setString(2, location.getWorld().getName());
                stmt.setDouble(3, location.getX());
                stmt.setDouble(4, location.getY());
                stmt.setDouble(5, location.getZ());
                stmt.setFloat(6, location.getYaw());
                stmt.setFloat(7, location.getPitch());
                stmt.setInt(8, npcId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save NPC location", e);
        }
    }
    
    public NPC getNPC(String regionId) {
        return electionNPCs.get(regionId);
    }
    
    public boolean hasNPC(String regionId) {
        return electionNPCs.containsKey(regionId);
    }
    
    public void fixMissingNPCs() {
        int fixed = 0;
        for (String regionId : plugin.getRegionManager().getRegionRotation()) {
            if (!hasNPC(regionId)) {
                // Try to recreate from database
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    String query = "SELECT * FROM npc_locations WHERE region_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(query)) {
                        stmt.setString(1, regionId);
                        ResultSet rs = stmt.executeQuery();
                        
                        if (rs.next()) {
                            recreateNPC(regionId, rs);
                            fixed++;
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to fix NPC for region: " + regionId, e);
                }
            }
        }
        
        if (fixed > 0) {
            plugin.getLogger().info("Fixed " + fixed + " missing NPCs");
        }
    }
    
    public void shutdown() {
        electionNPCs.clear();
        plugin.getLogger().info("NPC Manager shutdown");
    }
    
    public boolean isElectionNPC(NPC npc) {
        return npc.data().has("role") && "election_commissioner".equals(npc.data().get("role"));
    }
    
    public String getNPCRegion(NPC npc) {
        return npc.data().get("region_id");
    }
}
