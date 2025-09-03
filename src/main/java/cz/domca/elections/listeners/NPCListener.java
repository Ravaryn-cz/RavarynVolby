package cz.domca.elections.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import cz.domca.elections.WeeklyElectionsPlugin;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;

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
        
        // Check if this is one of our election GUIs and cancel the event to prevent item taking
        if (isElectionGUI(title)) {
            event.setCancelled(true);
            
            // Also prevent shift-clicking, number keys, etc.
            if (event.getAction().name().contains("HOTBAR") || 
                event.getAction().name().contains("DROP") ||
                event.getAction().name().contains("MOVE_TO_OTHER_INVENTORY")) {
                return; // Just cancel, don't process further
            }
            
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) {
                return;
            }
            
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) {
                return;
            }
            
            String displayName = meta.getDisplayName();
            
            // Handle different GUI clicks
            if (title.contains("Volby -")) {
                handleMainMenuClick(player, displayName, title);
            } else if (title.contains("Registrace")) {
                handleRegistrationClick(player, displayName, clickedItem);
            } else if (title.contains("Hlasování")) {
                handleVotingClick(player, displayName, clickedItem, event.getSlot());
            } else if (title.contains("Výsledky")) {
                handleResultsClick(player, displayName);
            }
        }
    }
    
    private boolean isElectionGUI(String title) {
        return title.contains("Volby -") || 
               title.contains("Registrace") || 
               title.contains("Hlasování") || 
               title.contains("Výsledky");
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        String title = event.getView().getTitle();
        
        // Prevent dragging items in election GUIs
        if (isElectionGUI(title)) {
            event.setCancelled(true);
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
        String regionId = extractRegionFromCurrentElection();
        if (regionId == null) {
            player.sendMessage(colorize("&cNejsou aktivní volby!"));
            return;
        }
        
        // Check which role was selected
        String selectedRole = null;
        if (displayName.contains("Zalařník")) {
            selectedRole = "zalanik";
        } else if (displayName.contains("Rychtář")) {
            selectedRole = "rychtář";
        } else if (displayName.contains("Správce")) {
            selectedRole = "spravce_obchodu";
        }
        
        if (selectedRole != null) {
            // Show confirmation that role was selected
            player.sendMessage(colorize("&eVybrali jste roli: &f" + displayName));
            player.sendMessage(colorize("&7Nyní klikněte na 'Potvrdit' nebo napište slogan do chatu."));
            
            // Start registration process
            plugin.getRegistrationManager().startRegistration(player, regionId, selectedRole);
            player.closeInventory();
            
        } else if (displayName.contains("Potvrdit")) {
            player.sendMessage(colorize("&ePokračujte v registraci podle instrukcí v chatu."));
            player.closeInventory();
            
        } else if (displayName.contains("Zrušit")) {
            player.sendMessage(colorize("&cRegistrace byla zrušena."));
            player.closeInventory();
        }
    }
    
    private String extractRegionFromCurrentElection() {
        if (plugin.getElectionManager().isElectionActive()) {
            return plugin.getElectionManager().getCurrentElection().getRegionId();
        }
        return null;
    }
    
    private void handleVotingClick(Player player, String displayName, ItemStack item, int slot) {
        if (displayName.contains("Zavřít")) {
            player.closeInventory();
            return;
        }
        
        // Check if it's a navigation button
        if (displayName.contains("Předchozí stránka")) {
            // Handle previous page - you'd need to track current page
            player.sendMessage(colorize("&7Předchozí stránka (demo)"));
            return;
        }
        
        if (displayName.contains("Další stránka")) {
            // Handle next page - you'd need to track current page  
            player.sendMessage(colorize("&7Další stránka (demo)"));
            return;
        }
        
        // Check if it's a candidate item (player head) and not already voted
        if (item.getType().name().equals("PLAYER_HEAD") && !displayName.contains("Hlasovali jste")) {
            // Extract candidate name and cast vote
            String candidateName = extractPlayerNameFromDisplayName(displayName);
            if (candidateName != null) {
                // Check if player can vote
                if (plugin.getElectionManager().hasVoted(player.getUniqueId().toString())) {
                    player.sendMessage(colorize("&cJiž jste hlasovali v těchto volbách!"));
                    return;
                }
                
                // For demo purposes - in real implementation you'd:
                // 1. Get the candidate ID from the item
                // 2. Cast the vote in database
                // 3. Update the GUI to show voted state
                boolean voteSuccess = castVoteForCandidate(player, candidateName);
                if (voteSuccess) {
                    player.sendMessage(colorize("&aVáš hlas pro " + candidateName + " byl zaznamenán!"));
                    player.closeInventory();
                } else {
                    player.sendMessage(colorize("&cChyba při hlasování!"));
                }
            }
        }
    }
    
    private void handleResultsClick(Player player, String displayName) {
        // Results are read-only, only handle close button
        if (displayName.contains("Zavřít")) {
            player.closeInventory();
        }
    }
    
    private boolean castVoteForCandidate(Player player, String candidateName) {
        // This is a simplified implementation
        // In reality, you'd need to:
        // 1. Find the candidate by name in current election
        // 2. Call plugin.getElectionManager().vote(player.getUniqueId().toString(), candidateId)
        // 3. Return the success status
        
        // For demo purposes, always return true
        return true;
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
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}
