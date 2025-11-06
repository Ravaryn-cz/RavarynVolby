package cz.domca.elections.listeners;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check for pending win notifications after a short delay
        // to ensure player is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getRoleAssignmentManager().checkAndNotifyWinners(player);
        }, 40L); // 2 seconds delay
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
                handleVotingClick(player, displayName, clickedItem, title);
            } else if (title.contains("Výsledky")) {
                handleResultsClick(player, displayName);
            } else if (title.contains("Zobrazit kandidáty")) {
                handleViewCandidatesClick(player, displayName);
            }
        }
    }
    
    private boolean isElectionGUI(String title) {
        return title.contains("Volby -") || 
               title.contains("Registrace") || 
               title.contains("Hlasování") || 
               title.contains("Výsledky") ||
               title.contains("Zobrazit kandidáty");
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
            // The requirements check is now handled inside the registration manager
            plugin.getGuiManager().openRegistrationForm(player, regionId);
        }
        else if (displayName.contains("Zobrazit kandidáty")) {
            // Allow viewing candidates only during VOTING phase (not during RESULTS)
            if (plugin.getElectionManager().isElectionActive()) {
                cz.domca.elections.elections.ElectionPhase phase = plugin.getElectionManager().getCurrentElection().getPhase();
                
                if (phase == cz.domca.elections.elections.ElectionPhase.VOTING) {
                    // During voting phase, show candidates in read-only mode (show heads and lore, but can't vote)
                    plugin.getGuiManager().openViewCandidatesGui(player, regionId);
                } else if (phase == cz.domca.elections.elections.ElectionPhase.REGISTRATION) {
                    player.sendMessage(colorize("&cKandidáti ještě nejsou k dispozici! Počkejte na hlasovací fázi."));
                } else {
                    // During RESULTS phase, this button shouldn't even be visible
                    player.sendMessage(colorize("&cVolby již skončily! Použijte tlačítko 'Výsledky'."));
                }
            } else {
                player.sendMessage(colorize("&cNejsou aktivní žádné volby!"));
            }
        }
        else if (displayName.contains("Hlasovat")) {
            // Check requirements before allowing voting
            if (!plugin.getRegistrationManager().meetsRequirements(player)) {
                player.sendMessage(colorize("&cNesplňujete požadavky pro hlasování!"));
                return;
            }
            
            if (plugin.getElectionManager().canVote()) {
                plugin.getGuiManager().openVotingGui(player, regionId, 0);
            } else {
                player.sendMessage(colorize("&cMomentálně není hlasovací období!"));
            }
        }
        else if (displayName.contains("Výsledky")) {
            // Show results only if we're in RESULTS phase or later
            if (plugin.getElectionManager().isElectionActive() && 
                plugin.getElectionManager().getCurrentElection().getPhase() == cz.domca.elections.elections.ElectionPhase.RESULTS) {
                plugin.getGuiManager().openResultsGui(player, regionId);
            } else {
                player.sendMessage(colorize("&cVýsledky ještě nejsou k dispozici! Počkejte na ukončení hlasování."));
            }
        }
        else if (displayName.contains("Zavřít")) {
            // In main menu, close inventory (this is the top level)
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
            
        } else if (displayName.contains("Zrušit") || displayName.contains("Zavřít")) {
            // Return to main menu
            String currentRegion = extractRegionFromCurrentElection();
            if (currentRegion != null) {
                plugin.getGuiManager().openMainMenu(player, currentRegion);
            } else {
                player.closeInventory();
            }
        }
    }
    
    private String extractRegionFromCurrentElection() {
        if (plugin.getElectionManager().isElectionActive()) {
            return plugin.getElectionManager().getCurrentElection().getRegionId();
        }
        return null;
    }
    
    private void handleVotingClick(Player player, String displayName, ItemStack item, String title) {
        if (displayName.contains("Zavřít")) {
            // Return to main menu
            String regionId = extractRegionFromCurrentElection();
            if (regionId != null) {
                plugin.getGuiManager().openMainMenu(player, regionId);
            } else {
                player.closeInventory();
            }
            return;
        }
        
        // Check if it's a navigation button
        if (displayName.contains("Předchozí stránka")) {
            // Extract page number from title and go to previous page
            int currentPage = extractPageFromTitle(title);
            if (currentPage > 0) {
                String regionId = extractRegionFromCurrentElection();
                if (regionId != null) {
                    plugin.getGuiManager().openVotingGui(player, regionId, currentPage - 1);
                }
            }
            return;
        }
        
        if (displayName.contains("Další stránka")) {
            // Extract page number from title and go to next page
            int currentPage = extractPageFromTitle(title);
            String regionId = extractRegionFromCurrentElection();
            if (regionId != null) {
                plugin.getGuiManager().openVotingGui(player, regionId, currentPage + 1);
            }
            return;
        }
        
        // Check if it's a candidate item (player head) and not already voted
        if (item.getType() == Material.PLAYER_HEAD && !displayName.contains("Hlasovali jste") && !displayName.contains("není aktivní")) {
            // Extract candidate name and cast vote
            String candidateName = extractPlayerNameFromDisplayName(displayName);
            
            if (candidateName != null && !candidateName.isEmpty()) {
                // Check if voting is currently allowed first
                if (!plugin.getElectionManager().canVote()) {
                    player.sendMessage(colorize("&cHlasování není momentálně aktivní! Volby možná nejsou ve fázi hlasování."));
                    return;
                }
                
                // Check if player can vote
                if (plugin.getElectionManager().hasVoted(player.getUniqueId().toString())) {
                    player.sendMessage(colorize("&cJiž jste hlasovali v těchto volbách!"));
                    return;
                }
                
                // Get candidate ID by finding the candidate in the list
                int candidateId = findCandidateIdByName(candidateName);
                
                if (candidateId >= 0) {
                    boolean voteSuccess = plugin.getElectionManager().castVote(player.getUniqueId().toString(), candidateId);
                    if (voteSuccess) {
                        player.sendMessage(colorize("&aVáš hlas pro " + candidateName + " byl zaznamenán!"));
                        player.closeInventory();
                        
                        // Reopen voting GUI to show updated vote status
                        String regionId = extractRegionFromCurrentElection();
                        if (regionId != null) {
                            plugin.getGuiManager().openVotingGui(player, regionId, 0);
                        }
                    } else {
                        player.sendMessage(colorize("&cChyba při hlasování! Zkuste to znovu."));
                    }
                } else {
                    player.sendMessage(colorize("&cKandidát nebyl nalezen!"));
                    plugin.getLogger().warning("Failed to find candidate '" + candidateName + "' for voting by player " + player.getName());
                }
            } else {
                player.sendMessage(colorize("&cChyba při zpracování jména kandidáta!"));
                plugin.getLogger().warning("Failed to extract candidate name from display name: '" + displayName + "'");
            }
        } else if (item.getType() == Material.PLAYER_HEAD) {
            // It's a candidate but voting is disabled or already voted
            if (displayName.contains("není aktivní")) {
                player.sendMessage(colorize("&cHlasování není momentálně aktivní!"));
            } else if (displayName.contains("Hlasovali jste")) {
                player.sendMessage(colorize("&cJiž jste hlasovali v těchto volbách!"));
            }
        }
    }
    
    private void handleResultsClick(Player player, String displayName) {
        // Results are read-only, only handle close button
        if (displayName.contains("Zavřít")) {
            // Return to main menu
            String regionId = extractRegionFromCurrentElection();
            if (regionId != null) {
                plugin.getGuiManager().openMainMenu(player, regionId);
            } else {
                player.closeInventory();
            }
        }
    }
    
    private void handleViewCandidatesClick(Player player, String displayName) {
        // View candidates GUI is read-only, only handle close button
        if (displayName.contains("Zavřít")) {
            // Return to main menu
            String regionId = extractRegionFromCurrentElection();
            if (regionId != null) {
                plugin.getGuiManager().openMainMenu(player, regionId);
            } else {
                player.closeInventory();
            }
        }
        // Ignore all other clicks (candidate heads are not clickable in view mode)
    }
    
    private int findCandidateIdByName(String candidateName) {
        List<cz.domca.elections.elections.Candidate> candidates = plugin.getElectionManager().getCandidates();
        
        for (cz.domca.elections.elections.Candidate candidate : candidates) {
            if (candidate.getPlayerName().equals(candidateName)) {
                return candidate.getId();
            }
        }
        
        // Log only when candidate is not found to help with debugging
        plugin.getLogger().warning("Candidate not found: '" + candidateName + "' among " + candidates.size() + " candidates");
        return -1; // Candidate not found
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
    
    private int extractPageFromTitle(String title) {
        // Extract page number from hidden marker at the end of title
        // Format: "Title§0§r0" where 0 is the page number
        try {
            // Find the last occurrence of the hidden marker
            int markerIndex = title.lastIndexOf("§0§r");
            if (markerIndex != -1) {
                String pageStr = title.substring(markerIndex + 4); // Skip "§0§r"
                // Remove any remaining color codes
                pageStr = pageStr.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
                return Integer.parseInt(pageStr);
            }
        } catch (Exception e) {
            // If parsing fails, return page 0
        }
        return 0;
    }
    
    private String extractPlayerNameFromDisplayName(String displayName) {
        // Extract player name from display name like "§eKandidát PlayerName" or "§eKandidát PlayerName §a(Hlasovali jste)"
        String cleanName = displayName.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        
        // Remove "Kandidát " prefix
        if (cleanName.startsWith("Kandidát ")) {
            cleanName = cleanName.substring("Kandidát ".length());
        }
        
        // Remove various suffixes that might be present
        String[] suffixesToRemove = {
            " (Hlasovali jste)",
            " (Hlasování není aktivní)",
            " &8(Hlasování není aktivní)",
            " &a(Hlasovali jste)"
        };
        
        for (String suffix : suffixesToRemove) {
            if (cleanName.contains(suffix)) {
                cleanName = cleanName.substring(0, cleanName.indexOf(suffix));
                break;
            }
        }
        
        // Additional cleanup - remove any remaining color codes that might have been missed
        cleanName = cleanName.replaceAll("&[0-9a-fk-orA-FK-OR]", "").trim();
        
        return cleanName.trim();
    }
    
    private String colorize(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}
