package cz.domca.elections.tasks;

import cz.domca.elections.WeeklyElectionsPlugin;
import cz.domca.elections.elections.Election;
import cz.domca.elections.elections.ElectionPhase;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ElectionTask extends BukkitRunnable {
    
    private final WeeklyElectionsPlugin plugin;
    
    public ElectionTask(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void run() {
        // Check for expired roles first
        plugin.getRoleAssignmentManager().checkExpiredRoles();
        
        Election currentElection = plugin.getElectionManager().getCurrentElection();
        
        if (currentElection == null) {
            // No active election - don't start automatically
            // Elections must be started manually with /volby start
            return;
        }
        
        // Check if current phase should progress
        Instant now = Instant.now();
        Instant phaseStartTime = currentElection.getStartTime();
        long daysSinceStart = ChronoUnit.DAYS.between(phaseStartTime, now);
        
        ElectionPhase currentPhase = currentElection.getPhase();
        
        switch (currentPhase) {
            case REGISTRATION:
                // Registration phase lasts for configured days
                int registrationDays = plugin.getConfigManager().getRegistrationDuration();
                if (daysSinceStart >= registrationDays) {
                    plugin.getElectionManager().progressElection();
                    plugin.getLogger().info("Election progressed to VOTING phase");
                    broadcastMessage("&6Registrace kandidátů byla ukončena! Začíná hlasovací období.");
                }
                break;
                
            case VOTING:
                // Voting phase starts after registration and lasts for configured days
                int totalRegistrationAndVotingDays = plugin.getConfigManager().getRegistrationDuration() 
                    + plugin.getConfigManager().getVotingDuration();
                if (daysSinceStart >= totalRegistrationAndVotingDays) {
                    plugin.getElectionManager().progressElection();
                    plugin.getLogger().info("Election progressed to RESULTS phase");
                    broadcastMessage("&6Hlasování bylo ukončeno! Výsledky jsou k dispozici.");
                    
                    // Give reputation rewards
                    plugin.getReputationManager().giveElectionRewards(currentElection.getRegionId());
                    
                    // Assign roles to winners
                    plugin.getRoleAssignmentManager().assignElectionRoles(
                        currentElection.getRegionId(), 
                        plugin.getElectionManager().getCandidates()
                    );
                }
                break;
                
            case RESULTS:
                // Results phase lasts for configured mandate duration
                int mandateDays = plugin.getConfigManager().getMandateDuration();
                if (daysSinceStart >= mandateDays) {
                    // End current election and start new one in next region
                    String nextRegion = plugin.getRegionManager().getNextRegion(currentElection.getRegionId());
                    plugin.getElectionManager().progressElection(); // This will end current and start new
                    plugin.getLogger().info("Election mandate ended, starting new election in region: " + nextRegion);
                    broadcastMessage("&6Mandát byl ukončen! Začínají nové volby v regionu: " + 
                        plugin.getRegionManager().getRegion(nextRegion).getDisplayName());
                }
                break;
        }
    }
    
    private void broadcastMessage(String message) {
        String colorized = message.replace("&", "§");
        plugin.getServer().broadcastMessage(colorized);
    }
}
