package cz.domca.elections.registration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationAbandonedListener;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.ConversationPrefix;
import org.bukkit.conversations.MessagePrompt;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;

import cz.domca.elections.WeeklyElectionsPlugin;

public class CandidateRegistrationManager {
    
    private final WeeklyElectionsPlugin plugin;
    private final ConversationFactory conversationFactory;
    private final Map<UUID, RegistrationData> pendingRegistrations;
    
    public CandidateRegistrationManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
        this.pendingRegistrations = new ConcurrentHashMap<>();
        
        this.conversationFactory = new ConversationFactory(plugin)
            .withModality(true)
            .withPrefix(new CustomConversationPrefix())
            .withFirstPrompt(new SloganPrompt())
            .withEscapeSequence("zrušit")
            .withTimeout(60)
            .addConversationAbandonedListener(new RegistrationAbandonedListener());
    }
    
    public void startRegistration(Player player, String regionId, String role) {
        // Check if player already has pending registration
        if (pendingRegistrations.containsKey(player.getUniqueId())) {
            player.sendMessage(colorize("&cMáte již probíhající registraci! Dokončete ji nebo napište 'zrušit'."));
            return;
        }
        
        // Check requirements
        if (!checkRegistrationRequirements(player)) {
            return;
        }
        
        // Check if already registered
        if (plugin.getElectionManager().isRegistered(player.getUniqueId().toString())) {
            player.sendMessage(colorize("&cJste již registrován jako kandidát v těchto volbách!"));
            return;
        }
        
        // Store registration data
        RegistrationData data = new RegistrationData(regionId, role);
        pendingRegistrations.put(player.getUniqueId(), data);
        
        // Start conversation for slogan input
        Conversation conversation = conversationFactory.buildConversation(player);
        conversation.getContext().setSessionData("registration_data", data);
        conversation.begin();
    }
    
    /**
     * Public method to check if a player meets election requirements
     * Can be used by other managers (e.g., GUI, NPCs)
     */
    public boolean meetsRequirements(Player player) {
        return checkRegistrationRequirements(player);
    }
    
    private boolean checkRegistrationRequirements(Player player) {
        org.bukkit.configuration.file.FileConfiguration reqConfig = 
            plugin.getConfigManager().getConfig("elections_requirement.yml");
        
        if (reqConfig == null) {
            plugin.getLogger().warning("Requirements config not loaded, allowing registration");
            return true;
        }
        
        // Get requirements
        int minPlaytime = reqConfig.getInt("requirements.min_playtime", 20);
        @SuppressWarnings("unused")
        int minQuestPoints = reqConfig.getInt("requirements.min_quest_points", 1);
        int minMoney = reqConfig.getInt("requirements.min_money", 1000);
        
        // Check playtime (in hours)
        long playTimeTicks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        long playTimeHours = playTimeTicks / (20 * 60 * 60); // Convert ticks to hours
        
        if (playTimeHours < minPlaytime) {
            String message = reqConfig.getString("messages.insufficient_playtime", 
                "&cNemáte dostatek herního času! Potřebujete alespoň %hours% hodin.");
            if (message != null) {
                player.sendMessage(colorize(message.replace("%hours%", String.valueOf(minPlaytime))));
            }
            return false;
        }
        
        // Quest points check - requires integration with quest system
        // Note: This is a placeholder. Integrate with your quest system if available
        // For now, skip this check with a log message
        if (minQuestPoints > 0) {
            plugin.getLogger().fine("Quest points check skipped (requires quest system integration) - minimum: " + minQuestPoints);
        }
        
        // Money check using Vault
        if (minMoney > 0) {
            if (plugin.hasEconomy()) {
                double balance = plugin.getEconomy().getBalance(player);
                if (balance < minMoney) {
                    String message = reqConfig.getString("messages.insufficient_money", 
                        "&cNemáte dostatek peněz! Potřebujete alespoň %amount%.");
                    if (message != null) {
                        player.sendMessage(colorize(message.replace("%amount%", String.valueOf(minMoney))));
                    }
                    return false;
                }
            } else {
                // Vault not available, skip money check with warning
                plugin.getLogger().fine("Money check skipped (Vault not available) - minimum: " + minMoney);
            }
        }
        
        // All requirements met
        String message = reqConfig.getString("messages.requirements_met", "&aVšechny požadavky jsou splněny!");
        player.sendMessage(colorize(message));
        return true;
    }
    
    private void completeRegistration(Player player, String slogan) {
        RegistrationData data = pendingRegistrations.remove(player.getUniqueId());
        if (data == null) {
            player.sendMessage(colorize("&cChyba: Registrační data nebyla nalezena!"));
            return;
        }
        
        // Validate slogan length
        if (slogan.length() > 100) {
            player.sendMessage(colorize("&cSlogan je příliš dlouhý! Maximum je 100 znaků."));
            // Start new conversation for slogan re-entry
            startSloganConversation(player, data);
            return;
        }
        
        // Register candidate
        boolean success = plugin.getElectionManager().registerCandidate(
            player.getUniqueId().toString(),
            player.getName(),
            data.getRole(),
            slogan
        );
        
        if (success) {
            player.sendMessage(colorize("&aÚspěšně jste se zaregistrovali jako kandidát!"));
            player.sendMessage(colorize("&7Role: &e" + data.getRole()));
            player.sendMessage(colorize("&7Slogan: &e" + slogan));
        } else {
            player.sendMessage(colorize("&cChyba při registraci! Zkuste to prosím znovu."));
        }
    }
    
    private void startSloganConversation(Player player, RegistrationData data) {
        pendingRegistrations.put(player.getUniqueId(), data);
        Conversation conversation = conversationFactory.buildConversation(player);
        conversation.getContext().setSessionData("registration_data", data);
        conversation.begin();
    }
    
    private String colorize(String text) {
        return text.replace("&", "§");
    }
    
    // Inner classes for conversation system
    
    private static class RegistrationData {
        private final String regionId;
        private final String role;
        
        public RegistrationData(String regionId, String role) {
            this.regionId = regionId;
            this.role = role;
        }
        
        public String getRegionId() { return regionId; }
        public String getRole() { return role; }
    }
    
    private static class CustomConversationPrefix implements ConversationPrefix {
        @Override
        public String getPrefix(ConversationContext context) {
            return "§6[Registrace] §7";
        }
    }
    
    private class SloganPrompt extends StringPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return "§aNapište váš volební slogan (max. 100 znaků, nebo 'zrušit' pro ukončení):";
        }
        
        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (input.equalsIgnoreCase("zrušit")) {
                return new CancelledPrompt();
            }
            
            Player player = (Player) context.getForWhom();
            completeRegistration(player, input);
            
            return END_OF_CONVERSATION;
        }
    }
    
    private static class CancelledPrompt extends MessagePrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return "§cRegistrace byla zrušena.";
        }
        
        @Override
        protected Prompt getNextPrompt(ConversationContext context) {
            return END_OF_CONVERSATION;
        }
    }
    
    private class RegistrationAbandonedListener implements ConversationAbandonedListener {
        @Override
        public void conversationAbandoned(ConversationAbandonedEvent event) {
            if (event.getContext().getForWhom() instanceof Player) {
                Player player = (Player) event.getContext().getForWhom();
                pendingRegistrations.remove(player.getUniqueId());
                
                if (event.gracefulExit()) {
                    return; // Normal completion
                }
                
                player.sendMessage(colorize("&cRegistrace byla ukončena z důvodu nečinnosti."));
            }
        }
    }
}
