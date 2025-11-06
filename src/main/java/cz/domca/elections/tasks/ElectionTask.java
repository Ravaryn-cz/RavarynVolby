package cz.domca.elections.tasks;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import cz.domca.elections.WeeklyElectionsPlugin;
import cz.domca.elections.elections.Candidate;
import cz.domca.elections.elections.Election;
import cz.domca.elections.elections.ElectionPhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

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
                    
                    // Broadcast that voting ended
                    broadcastMessage("&6&l═══════════════════════════════");
                    broadcastMessage("&e&lHlasování bylo ukončeno!");
                    broadcastMessage("&7Výsledky voleb jsou nyní k dispozici.");
                    broadcastMessage("&6&l═══════════════════════════════");
                    
                    // Give reputation rewards
                    plugin.getReputationManager().giveElectionRewards(currentElection.getRegionId());
                    
                    // Assign roles to winners
                    plugin.getRoleAssignmentManager().assignElectionRoles(
                        currentElection.getRegionId(), 
                        plugin.getElectionManager().getCandidates()
                    );
                    
                    // Announce winners with fireworks
                    announceWinners(currentElection);
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
    
    private void announceWinners(Election election) {
        List<Candidate> candidates = plugin.getElectionManager().getCandidates();
        
        // Group candidates by role and find winners (highest votes per role)
        Map<String, Candidate> winnersByRole = new HashMap<>();
        
        for (Candidate candidate : candidates) {
            String role = candidate.getRole();
            Candidate currentWinner = winnersByRole.get(role);
            
            if (currentWinner == null || candidate.getVotes() > currentWinner.getVotes()) {
                winnersByRole.put(role, candidate);
            }
        }
        
        // Broadcast winners to all players
        broadcastMessage("");
        broadcastMessage("&6&l🏆 VÍTĚZOVÉ VOLEB 🏆");
        broadcastMessage("&eRegion: &f" + plugin.getRegionManager().getRegion(election.getRegionId()).getDisplayName());
        broadcastMessage("");
        
        for (Map.Entry<String, Candidate> entry : winnersByRole.entrySet()) {
            String role = entry.getKey();
            Candidate winner = entry.getValue();
            broadcastMessage("&6" + role + ": &f" + winner.getPlayerName() + " &7(" + winner.getVotes() + " hlasů)");
            
            // Send title and fireworks to winner if online
            Player winnerPlayer = plugin.getServer().getPlayer(UUID.fromString(winner.getPlayerUuid()));
            if (winnerPlayer != null && winnerPlayer.isOnline()) {
                sendWinnerTitle(winnerPlayer, role);
                launchFireworks(winnerPlayer.getLocation(), 5);
            }
        }
        
        broadcastMessage("");
    }
    
    private void sendWinnerTitle(Player player, String role) {
        // Send title using Adventure API (Paper/Spigot 1.20+)
        Component mainTitle = Component.text("§6§l✨ VYHRÁLI JSTE! ✨");
        Component subtitle = Component.text("§eGratulujeme k vítězství v roli §f" + role);
        
        Title title = Title.title(
            mainTitle,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(500),  // fade in
                Duration.ofMillis(3500), // stay
                Duration.ofMillis(1000)  // fade out
            )
        );
        
        player.showTitle(title);
        player.sendMessage("§6§l═══════════════════════════════");
        player.sendMessage("§e§lGRATULUJEME K VÍTĚZSTVÍ!");
        player.sendMessage("§7Vyhráli jste volby v roli: §f" + role);
        player.sendMessage("§7Vaše role byla aktivována a můžete ji používat.");
        player.sendMessage("§6§l═══════════════════════════════");
    }
    
    private void launchFireworks(Location location, int count) {
        for (int i = 0; i < count; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Firework firework = location.getWorld().spawn(location, Firework.class);
                    FireworkMeta meta = firework.getFireworkMeta();
                    
                    // Random colors
                    Color[] colors = {Color.RED, Color.YELLOW, Color.ORANGE, Color.LIME, Color.AQUA, Color.FUCHSIA};
                    Color color1 = colors[new Random().nextInt(colors.length)];
                    Color color2 = colors[new Random().nextInt(colors.length)];
                    
                    FireworkEffect effect = FireworkEffect.builder()
                        .withColor(color1, color2)
                        .withFade(Color.WHITE)
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .trail(true)
                        .flicker(true)
                        .build();
                    
                    meta.addEffect(effect);
                    meta.setPower(1);
                    firework.setFireworkMeta(meta);
                }
            }.runTaskLater(plugin, i * 10L); // Spread fireworks over time
        }
    }
}
