package cz.domca.elections.roles;

import cz.domca.elections.WeeklyElectionsPlugin;
import cz.domca.elections.elections.Candidate;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class RoleAssignmentManager {
    
    private final WeeklyElectionsPlugin plugin;
    
    public RoleAssignmentManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void assignElectionRoles(String regionId, List<Candidate> winners) {
        for (Candidate winner : winners) {
            assignRole(winner, regionId);
        }
    }
    
    private void assignRole(Candidate winner, String regionId) {
        try {
            UUID playerUuid = UUID.fromString(winner.getPlayerUuid());
            String roleGroup = getRoleGroup(winner.getRole());
            
            if (roleGroup == null) {
                plugin.getLogger().warning("No LuckPerms group found for role: " + winner.getRole());
                return;
            }
            
            // Load user
            CompletableFuture<User> userFuture = plugin.getLuckPerms().getUserManager().loadUser(playerUuid);
            User user = userFuture.join();
            
            if (user == null) {
                plugin.getLogger().warning("Failed to load user for role assignment: " + winner.getPlayerName());
                return;
            }
            
            // Create context set for region
            ImmutableContextSet contextSet = ImmutableContextSet.builder()
                .add("region", regionId)
                .build();
            
            // Create inheritance node with region context and expiry
            Instant expiry = Instant.now().plus(plugin.getConfigManager().getMandateDuration(), ChronoUnit.DAYS);
            
            InheritanceNode roleNode = InheritanceNode.builder(roleGroup)
                .context(contextSet)
                .expiry(expiry)
                .build();
            
            // Add the node to user
            user.data().add(roleNode);
            
            // Save user
            plugin.getLuckPerms().getUserManager().saveUser(user);
            
            // Record in database
            recordRoleAssignment(winner, regionId, expiry);
            
            plugin.getLogger().info("Assigned role " + winner.getRole() + " to " + winner.getPlayerName() + " in region " + regionId);
            
            // Notify player if online
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null) {
                player.sendMessage("§6Gratulujeme! Byli jste zvoleni na pozici " + winner.getRole() + " v regionu " + regionId + "!");
                player.sendMessage("§7Vaše pravomoci budou platné po dobu " + plugin.getConfigManager().getMandateDuration() + " dní.");
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to assign role to " + winner.getPlayerName(), e);
        }
    }
    
    private String getRoleGroup(String roleId) {
        return plugin.getConfigManager().getConfig("regions.yml")
            .getString("roles." + roleId + ".luckperms_group");
    }
    
    private void recordRoleAssignment(Candidate winner, String regionId, Instant expiry) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = """
                INSERT INTO role_holders (player_uuid, player_name, region_id, role, start_time, end_time, active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, winner.getPlayerUuid());
                stmt.setString(2, winner.getPlayerName());
                stmt.setString(3, regionId);
                stmt.setString(4, winner.getRole());
                stmt.setLong(5, Instant.now().getEpochSecond());
                stmt.setLong(6, expiry.getEpochSecond());
                stmt.setBoolean(7, true);
                stmt.executeUpdate();
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to record role assignment", e);
        }
    }
    
    public void checkExpiredRoles() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // Find expired active roles
            String query = "SELECT * FROM role_holders WHERE active = TRUE AND end_time <= ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setLong(1, Instant.now().getEpochSecond());
                var rs = stmt.executeQuery();
                
                while (rs.next()) {
                    String playerUuid = rs.getString("player_uuid");
                    String playerName = rs.getString("player_name");
                    String regionId = rs.getString("region_id");
                    String role = rs.getString("role");
                    int roleId = rs.getInt("id");
                    
                    removeExpiredRole(playerUuid, playerName, regionId, role, roleId);
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check expired roles", e);
        }
    }
    
    private void removeExpiredRole(String playerUuidStr, String playerName, String regionId, String role, int roleId) {
        try {
            UUID playerUuid = UUID.fromString(playerUuidStr);
            String roleGroup = getRoleGroup(role);
            
            if (roleGroup == null) {
                plugin.getLogger().warning("No LuckPerms group found for expired role: " + role);
                return;
            }
            
            // Load user
            CompletableFuture<User> userFuture = plugin.getLuckPerms().getUserManager().loadUser(playerUuid);
            User user = userFuture.join();
            
            if (user != null) {
                // Create context set for region
                ImmutableContextSet contextSet = ImmutableContextSet.builder()
                    .add("region", regionId)
                    .build();
                
                // Remove all inheritance nodes for this group with this context
                user.data().clear(node -> {
                    if (node.getType() == NodeType.INHERITANCE) {
                        InheritanceNode inheritanceNode = (InheritanceNode) node;
                        return inheritanceNode.getGroupName().equals(roleGroup) &&
                               node.getContexts().equals(contextSet);
                    }
                    return false;
                });
                
                // Save user
                plugin.getLuckPerms().getUserManager().saveUser(user);
                
                plugin.getLogger().info("Removed expired role " + role + " from " + playerName + " in region " + regionId);
                
                // Notify player if online
                org.bukkit.entity.Player player = plugin.getServer().getPlayer(playerUuid);
                if (player != null) {
                    player.sendMessage("§cVáš mandát na pozici " + role + " v regionu " + regionId + " vypršel.");
                }
            }
            
            // Mark as inactive in database
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String updateQuery = "UPDATE role_holders SET active = FALSE WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                    stmt.setInt(1, roleId);
                    stmt.executeUpdate();
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove expired role from " + playerName, e);
        }
    }
    
    public boolean hasActiveRole(String playerUuid, String regionId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT COUNT(*) FROM role_holders WHERE player_uuid = ? AND region_id = ? AND active = TRUE AND end_time > ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, playerUuid);
                stmt.setString(2, regionId);
                stmt.setLong(3, Instant.now().getEpochSecond());
                var rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check active role", e);
        }
        
        return false;
    }
}
