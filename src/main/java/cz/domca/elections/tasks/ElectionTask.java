package cz.domca.elections.tasks;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.bukkit.scheduler.BukkitRunnable;

import cz.domca.elections.WeeklyElectionsPlugin;
import cz.domca.elections.elections.Election;
import cz.domca.elections.elections.ElectionPhase;

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
                }
                break;
                
            case VOTING:
                // Voting phase starts after registration and lasts for configured days
                int totalRegistrationAndVotingDays = plugin.getConfigManager().getRegistrationDuration() 
                    + plugin.getConfigManager().getVotingDuration();
                if (daysSinceStart >= totalRegistrationAndVotingDays) {
                    plugin.getElectionManager().progressElection();
                }
                break;
                
            case RESULTS:
                // Results phase lasts for configured mandate duration
                // The mandate duration starts after voting ends
                int totalCycleTime = plugin.getConfigManager().getRegistrationDuration() 
                    + plugin.getConfigManager().getVotingDuration() 
                    + plugin.getConfigManager().getMandateDuration();
                if (daysSinceStart >= totalCycleTime) {
                    // End current election and start new one in next region
                    plugin.getElectionManager().progressElection(); // This will end current and start new
                }
                break;
        }
    }
}
