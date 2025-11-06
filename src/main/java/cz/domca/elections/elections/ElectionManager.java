package cz.domca.elections.elections;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;

import cz.domca.elections.WeeklyElectionsPlugin;

public class ElectionManager {
    
    private final WeeklyElectionsPlugin plugin;
    private Election currentElection;
    
    public ElectionManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        loadCurrentElection();
    }
    
    private void loadCurrentElection() {
        if (plugin.getDatabaseManager() == null) {
            plugin.getLogger().warning("Database manager not initialized yet, skipping election loading");
            return;
        }
        
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT * FROM elections WHERE end_time IS NULL OR end_time > ? ORDER BY start_time DESC LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setLong(1, Instant.now().getEpochSecond());
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    currentElection = new Election(
                        rs.getInt("id"),
                        rs.getString("region_id"),
                        ElectionPhase.valueOf(rs.getString("phase")),
                        Instant.ofEpochSecond(rs.getLong("start_time")),
                        rs.getLong("end_time") > 0 ? Instant.ofEpochSecond(rs.getLong("end_time")) : null
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load current election", e);
        }
    }
    
    public Election getCurrentElection() {
        return currentElection;
    }
    
    public boolean isElectionActive() {
        return currentElection != null && !currentElection.isEnded();
    }
    
    public boolean canRegister() {
        return isElectionActive() && currentElection.getPhase() == ElectionPhase.REGISTRATION;
    }
    
    public boolean canVote() {
        return isElectionActive() && currentElection.getPhase() == ElectionPhase.VOTING;
    }
    
    public boolean registerCandidate(String playerUuid, String playerName, String role, String slogan) {
        if (!canRegister()) {
            return false;
        }
        
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "INSERT INTO candidates (election_id, player_uuid, player_name, role, slogan) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, currentElection.getId());
                stmt.setString(2, playerUuid);
                stmt.setString(3, playerName);
                stmt.setString(4, role);
                stmt.setString(5, slogan);
                stmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register candidate", e);
            return false;
        }
    }
    
    public boolean castVote(String voterUuid, int candidateId) {
        if (!canVote()) {
            return false;
        }
        
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            conn.setAutoCommit(false);
            
            // Insert vote
            String insertVote = "INSERT INTO votes (election_id, voter_uuid, candidate_id) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertVote)) {
                stmt.setInt(1, currentElection.getId());
                stmt.setString(2, voterUuid);
                stmt.setInt(3, candidateId);
                stmt.executeUpdate();
            }
            
            // Update candidate vote count
            String updateCount = "UPDATE candidates SET votes = votes + 1 WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateCount)) {
                stmt.setInt(1, candidateId);
                stmt.executeUpdate();
            }
            
            conn.commit();
            return true;
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to cast vote", e);
            return false;
        }
    }
    
    public List<Candidate> getCandidates() {
        if (currentElection == null) {
            return new ArrayList<>();
        }
        
        List<Candidate> candidates = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT * FROM candidates WHERE election_id = ? ORDER BY votes DESC, player_name ASC";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, currentElection.getId());
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    candidates.add(new Candidate(
                        rs.getInt("id"),
                        rs.getString("player_uuid"),
                        rs.getString("player_name"),
                        rs.getString("role"),
                        rs.getString("slogan"),
                        rs.getInt("votes")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load candidates", e);
        }
        
        return candidates;
    }
    
    public boolean hasVoted(String playerUuid) {
        if (currentElection == null) {
            return false;
        }
        
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT COUNT(*) FROM votes WHERE election_id = ? AND voter_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, currentElection.getId());
                stmt.setString(2, playerUuid);
                ResultSet rs = stmt.executeQuery();
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check vote status", e);
            return false;
        }
    }
    
    public boolean isRegistered(String playerUuid) {
        if (currentElection == null) {
            return false;
        }
        
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT COUNT(*) FROM candidates WHERE election_id = ? AND player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, currentElection.getId());
                stmt.setString(2, playerUuid);
                ResultSet rs = stmt.executeQuery();
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check registration status", e);
            return false;
        }
    }
    
    public void startNewElection(String regionId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "INSERT INTO elections (region_id, phase, start_time) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, regionId);
                stmt.setString(2, ElectionPhase.REGISTRATION.name());
                stmt.setLong(3, Instant.now().getEpochSecond());
                stmt.executeUpdate();
                
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int electionId = rs.getInt(1);
                    currentElection = new Election(
                        electionId,
                        regionId,
                        ElectionPhase.REGISTRATION,
                        Instant.now(),
                        null
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start new election", e);
        }
    }
    
    public void progressElection() {
        if (currentElection == null) {
            return;
        }
        
        ElectionPhase nextPhase = currentElection.getPhase().getNext();
        if (nextPhase == null) {
            // Election ended, start new one in next region
            String currentRegion = currentElection.getRegionId();
            endCurrentElection();
            
            String nextRegion = plugin.getRegionManager().getNextRegion(currentRegion);
            if (nextRegion != null && !nextRegion.equals(currentRegion)) {
                try {
                    startNewElection(nextRegion);
                    plugin.getLogger().info("Started new election in region: " + nextRegion);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to start new election in region " + nextRegion + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                plugin.getLogger().warning("No next region available or same region returned: " + nextRegion);
            }
        } else {
            // Progress to next phase
            updateElectionPhase(nextPhase);
            plugin.getLogger().info("Progressed election to phase: " + nextPhase.getDisplayName());
        }
    }
    
    private void updateElectionPhase(ElectionPhase phase) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "UPDATE elections SET phase = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, phase.name());
                stmt.setInt(2, currentElection.getId());
                stmt.executeUpdate();
                
                currentElection = new Election(
                    currentElection.getId(),
                    currentElection.getRegionId(),
                    phase,
                    currentElection.getStartTime(),
                    currentElection.getEndTime()
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update election phase", e);
        }
    }
    
    public void endCurrentElection() {
        if (currentElection == null) {
            return;
        }
        
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "UPDATE elections SET end_time = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setLong(1, Instant.now().getEpochSecond());
                stmt.setInt(2, currentElection.getId());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to end election", e);
        }
        
        currentElection = null;
    }
    
    public Map<String, RoleData> getRoleData() {
        Map<String, RoleData> roles = new HashMap<>();
        ConfigurationSection rolesConfig = plugin.getConfigManager().getConfig("regions.yml").getConfigurationSection("roles");
        
        if (rolesConfig != null) {
            for (String roleId : rolesConfig.getKeys(false)) {
                ConfigurationSection roleSection = rolesConfig.getConfigurationSection(roleId);
                if (roleSection != null) {
                    roles.put(roleId, new RoleData(
                        roleId,
                        roleSection.getString("name"),
                        roleSection.getString("display_name"),
                        roleSection.getString("description"),
                        roleSection.getString("luckperms_group"),
                        roleSection.getInt("reputation_reward")
                    ));
                }
            }
        }
        
        return roles;
    }
    
    public static class RoleData {
        private final String id;
        private final String name;
        private final String displayName;
        private final String description;
        private final String luckpermsGroup;
        private final int reputationReward;
        
        public RoleData(String id, String name, String displayName, String description, String luckpermsGroup, int reputationReward) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.description = description;
            this.luckpermsGroup = luckpermsGroup;
            this.reputationReward = reputationReward;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getLuckpermsGroup() { return luckpermsGroup; }
        public int getReputationReward() { return reputationReward; }
    }
}
