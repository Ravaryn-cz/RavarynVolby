package cz.domca.elections.reputation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import cz.domca.elections.WeeklyElectionsPlugin;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;

public class ReputationManager {
    
    private final WeeklyElectionsPlugin plugin;
    
    public ReputationManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    public int getReputation(String playerUuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            return getReputation(conn, playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get reputation", e);
        }
        return 0;
    }

    private int getReputation(Connection conn, String playerUuid) throws SQLException {
        String query = "SELECT reputation FROM reputation WHERE player_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, playerUuid);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("reputation");
            }
        }
        return 0;
    }
    
    public void addReputation(String playerUuid, String playerName, int amount, String reason) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            addReputation(conn, playerUuid, playerName, amount, reason);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add reputation", e);
        }
    }

    private void addReputation(Connection conn, String playerUuid, String playerName, int amount, String reason) throws SQLException {
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
        checkPrefixUpgrade(conn, playerUuid);
        
        Player player = plugin.getServer().getPlayer(java.util.UUID.fromString(playerUuid));
        if (player != null) {
            String template = plugin.getConfigManager().getConfig("reputation_rewards.yml")
                .getString("messages.reputation_gained", "&a+%amount% reputace! (%reason%)");
            if (template != null) {
                player.sendMessage(colorize(template
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%reason%", reason)));
            }
        }
    }
    
    public void removeReputation(String playerUuid, String playerName, int amount, String reason) {
        addReputation(playerUuid, playerName, -amount, reason);
    }
    
    private void checkPrefixUpgrade(Connection conn, String playerUuid) throws SQLException {
        int currentReputation = getReputation(conn, playerUuid);
        
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
                // Remove old reputation prefix regardless of setting so it stays hidden
                user.data().clear(node -> node.getKey().startsWith("prefix.") && node.getKey().contains("reputation"));

                var rewardsConfig = plugin.getConfigManager().getConfig("reputation_rewards.yml");
                boolean showPrefixes = rewardsConfig.getBoolean("settings.show_prefixes", true);
                
                if (showPrefixes && !prefix.isEmpty()) {
                    Node prefixNode = Node.builder("prefix.100.reputation." + prefix)
                        .build();
                    user.data().add(prefixNode);
                    
                    Player player = plugin.getServer().getPlayer(java.util.UUID.fromString(playerUuid));
                    if (player != null) {
                        String template = rewardsConfig.getString(
                            "messages.prefix_upgraded",
                            "&6Gratulujeme! Získali jste nový titul: %prefix%"
                        );
                        if (template != null) {
                            player.sendMessage(colorize(template.replace("%prefix%", prefixName)));
                        }
                    }
                }
                
                plugin.getLuckPerms().getUserManager().saveUser(user);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update player prefix", e);
        }
    }
    
    public void giveElectionRewards(String regionId) {
        ConfigurationSection reputationConfig = plugin.getConfigManager().getConfig("regions.yml")
            .getConfigurationSection("reputation");
        if (reputationConfig == null) {
            return;
        }

        var currentElection = plugin.getElectionManager().getCurrentElection();
        if (currentElection == null) {
            plugin.getLogger().warning("Attempted to grant rewards without an active election");
            return;
        }

        int winnerReward = reputationConfig.getInt("winner", 10);
        int candidateReward = reputationConfig.getInt("candidate", 2);
        int voterReward = reputationConfig.getInt("voter", 1);

        List<RewardEntry> queuedRewards = new ArrayList<>();
        List<WinnerLogEntry> winnerLogs = new ArrayList<>();

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            int electionId = currentElection.getId();

            String winnersQuery = """
                SELECT c.player_uuid, c.player_name, c.role
                FROM candidates c
                WHERE c.election_id = ?
                AND c.votes = (
                    SELECT MAX(c2.votes)
                    FROM candidates c2
                    WHERE c2.election_id = c.election_id
                    AND c2.role = c.role
                )
                """;

            try (PreparedStatement stmt = conn.prepareStatement(winnersQuery)) {
                stmt.setInt(1, electionId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String playerUuid = rs.getString("player_uuid");
                    String playerName = rs.getString("player_name");
                    String role = rs.getString("role");

                    queuedRewards.add(new RewardEntry(
                        playerUuid,
                        playerName,
                        winnerReward,
                        "Vítězství ve volbách (role: " + role + ")"
                    ));
                    winnerLogs.add(new WinnerLogEntry(playerName, role));
                }
            }

            String candidatesQuery = "SELECT player_uuid, player_name FROM candidates WHERE election_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(candidatesQuery)) {
                stmt.setInt(1, electionId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    queuedRewards.add(new RewardEntry(
                        rs.getString("player_uuid"),
                        rs.getString("player_name"),
                        candidateReward,
                        "Účast ve volbách"
                    ));
                }
            }

            String votersQuery = "SELECT DISTINCT voter_uuid FROM votes WHERE election_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(votersQuery)) {
                stmt.setInt(1, electionId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String voterUuid = rs.getString("voter_uuid");
                    Player voter = plugin.getServer().getPlayer(java.util.UUID.fromString(voterUuid));
                    String voterName = voter != null ? voter.getName() : "Unknown";

                    queuedRewards.add(new RewardEntry(
                        voterUuid,
                        voterName,
                        voterReward,
                        "Hlasování ve volbách"
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load election rewards", e);
            return;
        }

        for (RewardEntry reward : queuedRewards) {
            addReputation(reward.playerUuid(), reward.playerName(), reward.amount(), reward.reason());
        }

        for (WinnerLogEntry logEntry : winnerLogs) {
            plugin.getLogger().log(Level.INFO,
                "Awarded winner reputation to {0} for role {1}",
                new Object[] {logEntry.playerName(), logEntry.role()});
        }
        plugin.getLogger().log(Level.INFO,
            "Election rewards distributed for region: {0}", regionId);
    }

    private record RewardEntry(String playerUuid, String playerName, int amount, String reason) {}
    private record WinnerLogEntry(String playerName, String role) {}
    
    private String colorize(String text) {
        return text.replace("&", "§");
    }
}
