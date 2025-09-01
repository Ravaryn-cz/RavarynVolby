package cz.domca.elections.registration;

import cz.domca.elections.WeeklyElectionsPlugin;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    
    private boolean checkRegistrationRequirements(Player player) {
        // This would integrate with your existing systems
        // For now, return true as placeholder
        
        // In real implementation:
        // - Check playtime via Statistics API or custom tracking
        // - Check quest points via your quest system
        // - Check money via Vault/economy plugin
        
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
