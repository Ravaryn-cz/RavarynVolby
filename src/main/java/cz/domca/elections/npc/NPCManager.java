package cz.domca.elections.npc;

import cz.domca.elections.WeeklyElectionsPlugin;
import cz.domca.elections.holograms.HologramManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

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
                    String regionId = rs.getString("region_id");
                    int npcId = rs.getInt("npc_id");
                    
                    if (npcId > 0) {
                        NPC npc = npcRegistry.getById(npcId);
                        if (npc != null && npc.isSpawned()) {
                            electionNPCs.put(regionId, npc);
                        } else {
                            // NPC doesn't exist anymore, recreate it
                            recreateNPC(regionId, rs);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load existing NPCs", e);
        }
    }
    
    private void recreateNPC(String regionId, ResultSet rs) throws SQLException {
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
        }
    }
    
    public boolean createNPC(String regionId, Location location) {
        if (electionNPCs.containsKey(regionId)) {
            return false; // NPC already exists
        }
        
        // Create NPC
        NPC npc = npcRegistry.createNPC(EntityType.PLAYER, "§a§lVolby");
        npc.spawn(location);
        
        // Configure NPC
        npc.data().set(NPC.Metadata.NAMEPLATE_VISIBLE, true);
        npc.data().set(NPC.Metadata.COLLIDABLE, false);
        npc.getEntity().setGravity(false);
        
        // Set metadata for identification
        npc.data().set("region_id", regionId);
        npc.data().set("role", "election_commissioner");
        
        // Store in database
        saveNPCLocation(regionId, location, npc.getId());
        
        // Store in memory
        electionNPCs.put(regionId, npc);
        
        plugin.getLogger().info("Created election NPC for region: " + regionId);
        return true;
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
