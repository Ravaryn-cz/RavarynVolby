package cz.domca.elections.listeners;

import cz.domca.elections.WeeklyElectionsPlugin;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class NPCListener implements Listener {
    
    private final WeeklyElectionsPlugin plugin;
    
    public NPCListener(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onNPCClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        Player player = event.getClicker();
        
        // Check if this is an election NPC
        if (!plugin.getNpcManager().isElectionNPC(npc)) {
            return;
        }
        
        String regionId = plugin.getNpcManager().getNPCRegion(npc);
        if (regionId == null) {
            return;
        }
        
        // Check if there's an active election for this region
        if (!plugin.getElectionManager().isElectionActive() || 
            !plugin.getElectionManager().getCurrentElection().getRegionId().equals(regionId)) {
            player.sendMessage(colorize("&cPro tento region momentálně nejsou aktivní volby!"));
            return;
        }
        
        // Open main election GUI
        plugin.getGuiManager().openMainMenu(player, regionId);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }
        
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        
        String displayName = meta.getDisplayName();
        
        // Main menu clicks
        if (title.contains("Volby -")) {
            event.setCancelled(true);
            handleMainMenuClick(player, displayName, title);
        }
        // Registration form clicks
        else if (title.contains("Registrace")) {
            event.setCancelled(true);
            handleRegistrationClick(player, displayName, clickedItem);
        }
        // Voting GUI clicks
        else if (title.contains("Hlasování")) {
            event.setCancelled(true);
            handleVotingClick(player, displayName, clickedItem, event.getSlot());
        }
        // Results GUI clicks
        else if (title.contains("Výsledky")) {
            event.setCancelled(true);
            // Results are read-only, just handle close
            if (displayName.contains("Zavřít")) {
                player.closeInventory();
            }
        }
    }
    
    private void handleMainMenuClick(Player player, String displayName, String title) {
        String regionId = extractRegionFromTitle(title);
        if (regionId == null) return;
        
        if (displayName.contains("Přihlásit se")) {
            // Check requirements before opening registration form
            if (checkElectionRequirements(player)) {
                plugin.getGuiManager().openRegistrationForm(player, regionId);
            }
        }
        else if (displayName.contains("Zobrazit kandidáty")) {
            plugin.getGuiManager().openVotingGui(player, regionId, 0); // Show first page
        }
        else if (displayName.contains("Hlasovat")) {
            if (plugin.getElectionManager().canVote()) {
                plugin.getGuiManager().openVotingGui(player, regionId, 0);
            } else {
                player.sendMessage(colorize("&cMomentálně není hlasovací období!"));
            }
        }
        else if (displayName.contains("Výsledky")) {
            plugin.getGuiManager().openResultsGui(player, regionId);
        }
        else if (displayName.contains("Zavřít")) {
            player.closeInventory();
        }
    }
    
    private void handleRegistrationClick(Player player, String displayName, ItemStack item) {
        // This is a simplified implementation
        // In a full implementation, you'd want to track the selected role and handle confirmation
        
        if (displayName.contains("Zalařník") || displayName.contains("Rychtář") || displayName.contains("Správce")) {
            // Store selected role temporarily (you might use player metadata or a map)
            // For now, just show a message
            player.sendMessage(colorize("&eVyberte roli a klikněte na 'Potvrdit' pro dokončení registrace."));
        }
        else if (displayName.contains("Potvrdit")) {
            // Handle registration confirmation
            // This would need the stored role and slogan input
            player.sendMessage(colorize("&aRegistrace byla úspěšně dokončena! (Demo)"));
            player.closeInventory();
        }
        else if (displayName.contains("Zrušit")) {
            player.closeInventory();
        }
    }
    
    private void handleVotingClick(Player player, String displayName, ItemStack item, int slot) {
        if (displayName.contains("Zavřít")) {
            player.closeInventory();
            return;
        }
        
        // Check if it's a candidate item (player head)
        if (item.getType().name().equals("PLAYER_HEAD") && !displayName.contains("Hlasovali jste")) {
            // Extract candidate name and cast vote
            // This is simplified - you'd need to track candidate IDs properly
            String candidateName = extractPlayerNameFromDisplayName(displayName);
            if (candidateName != null) {
                // For demo purposes
                player.sendMessage(colorize("&aVáš hlas pro " + candidateName + " byl zaznamenán! (Demo)"));
                player.closeInventory();
            }
        }
    }
    
    private String extractRegionFromTitle(String title) {
        // Extract region from title like "Volby - Vojtěchov"
        String[] parts = title.split(" - ");
        if (parts.length > 1) {
            String regionName = parts[1].replace("§", "");
            // Find region by display name
            return plugin.getRegionManager().getAllRegions().entrySet().stream()
                .filter(entry -> entry.getValue().getDisplayName().replace("§", "").contains(regionName))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        }
        return null;
    }
    
    private String extractPlayerNameFromDisplayName(String displayName) {
        // Extract player name from display name like "§ePlayerName"
        return displayName.replaceAll("§[0-9a-fk-or]", "").trim();
    }
    
    private boolean checkElectionRequirements(Player player) {
        // Check if player meets election requirements
        // This would integrate with your existing systems for playtime, quest points, money
        
        // For demo purposes, always return true
        // In reality, you'd check:
        // - Playtime via some plugin or server stats
        // - Quest points via your quest system
        // - Money via your economy plugin
        
        return true;
    }
    
    private String colorize(String text) {
        return text.replace("&", "§");
    }
}
