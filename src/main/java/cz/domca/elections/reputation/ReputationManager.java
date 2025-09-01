package cz.domca.elections.reputation;

import cz.domca.elections.WeeklyElectionsPlugin;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

public class ReputationManager {
    
    private final WeeklyElectionsPlugin plugin;
    
    public ReputationManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    public int getReputation(String playerUuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT reputation FROM reputation WHERE player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, playerUuid);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return rs.getInt("reputation");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get reputation", e);
        }
        
        return 0;
    }
    
    public void addReputation(String playerUuid, String playerName, int amount, String reason) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // Update or insert reputation
            String upsertQuery = """
                INSERT INTO reputation (player_uuid, player_name, reputation) 
                VALUES (?, ?, ?) 
                ON CONFLICT(player_uuid) DO UPDATE SET 
                    reputation = reputation + ?, 
                    player_name = ?,
                    last_updated = strftime('%s', 'now')
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(upsertQuery)) {
                stmt.setString(1, playerUuid);
                stmt.setString(2, playerName);
                stmt.setInt(3, amount);
                stmt.setInt(4, amount);
                stmt.setString(5, playerName);
                stmt.executeUpdate();
            }
            
            // Check for prefix upgrade
            checkPrefixUpgrade(playerUuid, playerName);
            
            // Notify player if online
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null) {
                String message = plugin.getConfigManager().getConfig("reputation_rewards.yml")
                    .getString("messages.reputation_gained", "&a+%amount% reputace! (%reason%)")
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%reason%", reason);
                player.sendMessage(colorize(message));
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add reputation", e);
        }
    }
    
    public void removeReputation(String playerUuid, String playerName, int amount, String reason) {
        addReputation(playerUuid, playerName, -amount, reason);
    }
    
    private void checkPrefixUpgrade(String playerUuid, String playerName) {
        int currentReputation = getReputation(playerUuid);
        
        ConfigurationSection prefixLevels = plugin.getConfigManager().getConfig("reputation_rewards.yml")
            .getConfigurationSection("prefix_levels");
        
        if (prefixLevels == null) return;
        
        // Find the highest prefix level the player qualifies for
        TreeMap<Integer, String> levels = new TreeMap<>();
        for (String key : prefixLevels.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                String prefix = prefixLevels.getString(key + ".prefix", "");
                levels.put(level, prefix);
            } catch (NumberFormatException e) {
                // Skip invalid keys
            }
        }
        
        String newPrefix = "";
        String newPrefixName = "";
        
        for (Map.Entry<Integer, String> entry : levels.descendingMap().entrySet()) {
            if (currentReputation >= entry.getKey()) {
                newPrefix = entry.getValue();
                newPrefixName = prefixLevels.getString(entry.getKey() + ".name", "");
                break;
            }
        }
        
        // Apply prefix via LuckPerms
        updatePlayerPrefix(playerUuid, newPrefix, newPrefixName);
    }
    
    private void updatePlayerPrefix(String playerUuid, String prefix, String prefixName) {
        try {
            User user = plugin.getLuckPerms().getUserManager().loadUser(java.util.UUID.fromString(playerUuid)).join();
            
            if (user != null) {
                // Remove old reputation prefix
                user.data().clear(node -> node.getKey().startsWith("prefix.") && node.getKey().contains("reputation"));
                
                // Add new prefix if not empty
                if (!prefix.isEmpty()) {
                    Node prefixNode = Node.builder("prefix.100.reputation." + prefix)
                        .build();
                    user.data().add(prefixNode);
                    
                    // Notify player if online
                    Player player = plugin.getServer().getPlayer(playerUuid);
                    if (player != null) {
                        String message = plugin.getConfigManager().getConfig("reputation_rewards.yml")
                            .getString("messages.prefix_upgraded", "&6Gratulujeme! Získali jste nový titul: %prefix%")
                            .replace("%prefix%", prefixName);
                        player.sendMessage(colorize(message));
                    }
                }
                
                // Save changes
                plugin.getLuckPerms().getUserManager().saveUser(user);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update player prefix", e);
        }
    }
    
    public void giveElectionRewards(String regionId) {
        // Get election results and give reputation rewards
        // This will be called when elections end
        
        ConfigurationSection reputationConfig = plugin.getConfigManager().getConfig("regions.yml")
            .getConfigurationSection("reputation");
        
        if (reputationConfig == null) return;
        
        int winnerReward = reputationConfig.getInt("winner", 10);
        int candidateReward = reputationConfig.getInt("candidate", 2);
        int voterReward = reputationConfig.getInt("voter", 1);
        
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            int electionId = plugin.getElectionManager().getCurrentElection().getId();
            
            // Reward winner
            String winnerQuery = """
                SELECT c.player_uuid, c.player_name FROM candidates c 
                WHERE c.election_id = ? 
                ORDER BY c.votes DESC LIMIT 1
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(winnerQuery)) {
                stmt.setInt(1, electionId);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    addReputation(rs.getString("player_uuid"), rs.getString("player_name"), 
                        winnerReward, "Vítězství ve volbách");
                }
            }
            
            // Reward all candidates (including winner, but they get both rewards)
            String candidatesQuery = "SELECT player_uuid, player_name FROM candidates WHERE election_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(candidatesQuery)) {
                stmt.setInt(1, electionId);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    addReputation(rs.getString("player_uuid"), rs.getString("player_name"), 
                        candidateReward, "Účast ve volbách");
                }
            }
            
            // Reward all voters
            String votersQuery = "SELECT DISTINCT voter_uuid FROM votes WHERE election_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(votersQuery)) {
                stmt.setInt(1, electionId);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    String voterUuid = rs.getString("voter_uuid");
                    Player voter = plugin.getServer().getPlayer(voterUuid);
                    String voterName = voter != null ? voter.getName() : "Unknown";
                    
                    addReputation(voterUuid, voterName, voterReward, "Hlasování ve volbách");
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to give election rewards", e);
        }
    }
    
    private String colorize(String text) {
        return text.replace("&", "§");
    }
}
