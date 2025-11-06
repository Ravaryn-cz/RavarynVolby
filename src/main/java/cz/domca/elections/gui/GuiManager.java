package cz.domca.elections.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import cz.domca.elections.WeeklyElectionsPlugin;
import cz.domca.elections.elections.Candidate;
import cz.domca.elections.elections.ElectionManager;

public class GuiManager {
    
    private final WeeklyElectionsPlugin plugin;
    
    public GuiManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void openMainMenu(Player player, String regionId) {
        ConfigurationSection mainMenuConfig = plugin.getConfigManager().getConfig("gui.yml").getConfigurationSection("main_menu");
        if (mainMenuConfig == null) return;
        
        String title = colorize(mainMenuConfig.getString("title", "Volby"))
            .replace("%region%", colorize(plugin.getRegionManager().getRegion(regionId).getDisplayName()));
        int size = mainMenuConfig.getInt("size", 27);
        
        Inventory inventory = Bukkit.createInventory((InventoryHolder) null, size, title);
        
        // Get current election phase to conditionally show buttons
        cz.domca.elections.elections.ElectionPhase currentPhase = null;
        if (plugin.getElectionManager().isElectionActive()) {
            currentPhase = plugin.getElectionManager().getCurrentElection().getPhase();
        }
        
        // Add items
        ConfigurationSection itemsConfig = mainMenuConfig.getConfigurationSection("items");
        if (itemsConfig != null) {
            for (String itemKey : itemsConfig.getKeys(false)) {
                ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(itemKey);
                if (itemConfig != null) {
                    // Skip "Zobrazit kandidáty" button during RESULTS phase
                    if (itemKey.equals("candidates") && currentPhase == cz.domca.elections.elections.ElectionPhase.RESULTS) {
                        continue;
                    }
                    
                    // Skip "Hlasovat" button during RESULTS phase
                    if (itemKey.equals("vote") && currentPhase == cz.domca.elections.elections.ElectionPhase.RESULTS) {
                        continue;
                    }
                    
                    int slot = itemConfig.getInt("slot");
                    ItemStack item = createGuiItem(itemConfig);
                    inventory.setItem(slot, item);
                }
            }
        }
        
        player.openInventory(inventory);
    }
    
    public void openRegistrationForm(Player player, String regionId) {
        ConfigurationSection regFormConfig = plugin.getConfigManager().getConfig("gui.yml").getConfigurationSection("registration_form");
        if (regFormConfig == null) return;
        
        String title = colorize(regFormConfig.getString("title", "Registrace"));
        int size = regFormConfig.getInt("size", 45);
        
        Inventory inventory = Bukkit.createInventory((InventoryHolder) null, size, title);
        
        // Add role selection items
        ConfigurationSection itemsConfig = regFormConfig.getConfigurationSection("items");
        Map<String, ElectionManager.RoleData> roles = plugin.getElectionManager().getRoleData();
        
        if (itemsConfig != null) {
            for (String itemKey : itemsConfig.getKeys(false)) {
                ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(itemKey);
                if (itemConfig != null) {
                    int slot = itemConfig.getInt("slot");
                    ItemStack item = createGuiItem(itemConfig);
                    
                    // Add role description if it's a role item
                    if (roles.containsKey(itemKey)) {
                        ElectionManager.RoleData roleData = roles.get(itemKey);
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            List<String> lore = meta.getLore();
                            if (lore == null) lore = new ArrayList<>();
                            lore.add("");
                            lore.add(colorize("&7" + roleData.getDescription()));
                            meta.setLore(lore);
                            item.setItemMeta(meta);
                        }
                    }
                    
                    inventory.setItem(slot, item);
                }
            }
        }
        
        player.openInventory(inventory);
    }
    
    public void openViewCandidatesGui(Player player, String regionId) {
        // Read-only view of candidates (just shows heads and lore, no voting)
        ConfigurationSection votingConfig = plugin.getConfigManager().getConfig("gui.yml").getConfigurationSection("voting_gui");
        if (votingConfig == null) return;
        
        String title = "§eZobrazit kandidáty - " + colorize(plugin.getRegionManager().getRegion(regionId).getDisplayName());
        int size = votingConfig.getInt("size", 54);
        
        Inventory inventory = Bukkit.createInventory((InventoryHolder) null, size, title);
        
        // Get candidates for current election
        List<Candidate> candidates = plugin.getElectionManager().getCandidates();
        
        // Add candidate items (read-only, canVote = false forces read-only mode)
        int slot = 0;
        for (int i = 0; i < candidates.size() && slot < 45; i++) {
            Candidate candidate = candidates.get(i);
            ItemStack item = createCandidateItem(candidate, false, false, votingConfig); // Both false = read-only
            inventory.setItem(slot++, item);
        }
        
        // Add close button
        ConfigurationSection itemsConfig = votingConfig.getConfigurationSection("items");
        if (itemsConfig != null) {
            ConfigurationSection closeConfig = itemsConfig.getConfigurationSection("close");
            if (closeConfig != null) {
                inventory.setItem(closeConfig.getInt("slot"), createGuiItem(closeConfig));
            }
        }
        
        player.openInventory(inventory);
    }
    
    public void openVotingGui(Player player, String regionId, int page) {
        ConfigurationSection votingConfig = plugin.getConfigManager().getConfig("gui.yml").getConfigurationSection("voting_gui");
        if (votingConfig == null) return;
        
        String title = colorize(votingConfig.getString("title", "Hlasování"))
            .replace("%region%", colorize(plugin.getRegionManager().getRegion(regionId).getDisplayName()));
        
        // Add page number to title (hidden at the end for parsing)
        title = title + "§0§r" + page; // Hidden page marker
        
        int size = votingConfig.getInt("size", 54);
        
        Inventory inventory = Bukkit.createInventory((InventoryHolder) null, size, title);
        
        // Get candidates for current election (should match region if election is active)
        List<Candidate> candidates = plugin.getElectionManager().getCandidates();
        boolean hasVoted = plugin.getElectionManager().hasVoted(player.getUniqueId().toString());
        boolean canVote = plugin.getElectionManager().canVote();
        
        // Calculate pagination
        int itemsPerPage = 45; // 9x5 grid
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, candidates.size());
        
        // Add candidate items
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Candidate candidate = candidates.get(i);
            ItemStack item = createCandidateItem(candidate, hasVoted, canVote, votingConfig);
            inventory.setItem(slot++, item);
        }
        
        // Add navigation and control items
        ConfigurationSection itemsConfig = votingConfig.getConfigurationSection("items");
        if (itemsConfig != null) {
            // Previous page
            if (page > 0) {
                ConfigurationSection prevConfig = itemsConfig.getConfigurationSection("previous_page");
                if (prevConfig != null) {
                    inventory.setItem(prevConfig.getInt("slot"), createGuiItem(prevConfig));
                }
            }
            
            // Next page
            if (endIndex < candidates.size()) {
                ConfigurationSection nextConfig = itemsConfig.getConfigurationSection("next_page");
                if (nextConfig != null) {
                    inventory.setItem(nextConfig.getInt("slot"), createGuiItem(nextConfig));
                }
            }
            
            // Close button
            ConfigurationSection closeConfig = itemsConfig.getConfigurationSection("close");
            if (closeConfig != null) {
                inventory.setItem(closeConfig.getInt("slot"), createGuiItem(closeConfig));
            }
        }
        
        player.openInventory(inventory);
    }
    
    public void openResultsGui(Player player, String regionId) {
        ConfigurationSection resultsConfig = plugin.getConfigManager().getConfig("gui.yml").getConfigurationSection("results_gui");
        if (resultsConfig == null) return;
        
        String title = colorize(resultsConfig.getString("title", "Výsledky"))
            .replace("%region%", colorize(plugin.getRegionManager().getRegion(regionId).getDisplayName()));
        int size = resultsConfig.getInt("size", 54);
        
        Inventory inventory = Bukkit.createInventory((InventoryHolder) null, size, title);
        
        List<Candidate> candidates = plugin.getElectionManager().getCandidates();
        
        // Sort candidates by votes (already sorted from ElectionManager)
        int slot = 0;
        for (Candidate candidate : candidates) {
            ItemStack item = createResultItem(candidate, slot == 0, resultsConfig);
            inventory.setItem(slot++, item);
            
            if (slot >= size - 9) break; // Leave space for controls
        }
        
        player.openInventory(inventory);
    }
    
    private ItemStack createGuiItem(ConfigurationSection config) {
        String materialName = config.getString("material", "STONE");
        Material material = Material.valueOf(materialName);
        ItemStack item = new ItemStack(material);
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString("name");
            if (name != null) {
                meta.setDisplayName(colorize(name));
            }
            
            List<String> lore = config.getStringList("lore");
            if (!lore.isEmpty()) {
                List<String> colorizedLore = new ArrayList<>();
                for (String line : lore) {
                    colorizedLore.add(colorize(line));
                }
                meta.setLore(colorizedLore);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private ItemStack createCandidateItem(Candidate candidate, boolean hasVoted, boolean canVote, ConfigurationSection config) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        if (meta != null) {
            // Set player head
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(candidate.getPlayerName()));
            
            // Set name and lore  
            ConfigurationSection itemConfig;
            if (hasVoted) {
                itemConfig = config.getConfigurationSection("voted_candidate");
            } else if (!canVote) {
                // Try to get a "voting_disabled" config, otherwise fall back to candidate
                itemConfig = config.getConfigurationSection("voting_disabled");
                if (itemConfig == null) {
                    itemConfig = config.getConfigurationSection("candidate");
                }
            } else {
                itemConfig = config.getConfigurationSection("candidate");
            }
            
            // Always set a name, even if config is missing
            String name;
            if (itemConfig != null && itemConfig.getString("name") != null) {
                name = itemConfig.getString("name", "Kandidát %player%")
                    .replace("%player%", candidate.getPlayerName());
            } else {
                // Fallback name format
                name = "Kandidát " + candidate.getPlayerName();
            }
            
            // If voting is disabled but we don't have a special template, modify the name
            if (!canVote && !hasVoted && (itemConfig == null || itemConfig == config.getConfigurationSection("candidate"))) {
                name = name + " &8(Hlasování není aktivní)";
            } else if (hasVoted) {
                name = name + " &a(Hlasovali jste)";
            }
            
            meta.setDisplayName(colorize(name));
            
            // Always create lore with candidate information
            List<String> lore = new ArrayList<>();
            
            // If we have config lore, use it and replace placeholders
            if (itemConfig != null && itemConfig.getStringList("lore") != null && !itemConfig.getStringList("lore").isEmpty()) {
                for (String line : itemConfig.getStringList("lore")) {
                    lore.add(colorize(line
                        .replace("%player%", candidate.getPlayerName())
                        .replace("%role%", candidate.getRole() != null ? candidate.getRole() : "Neuvedeno")
                        .replace("%slogan%", candidate.getSlogan() != null ? candidate.getSlogan() : "Žádný slogan")
                        .replace("%votes%", String.valueOf(candidate.getVotes()))));
                }
            } else {
                // Fallback lore if config is missing or empty
                lore.add(colorize("&7Role: &f" + (candidate.getRole() != null ? candidate.getRole() : "Neuvedeno")));
                lore.add(colorize("&7Slogan: &f" + (candidate.getSlogan() != null ? candidate.getSlogan() : "Žádný slogan")));
                lore.add(colorize("&7Hlasy: &f" + candidate.getVotes()));
                lore.add("");
                if (canVote && !hasVoted) {
                    lore.add(colorize("&aKlikněte pro hlasování"));
                } else if (hasVoted) {
                    lore.add(colorize("&aJiž jste hlasovali"));
                } else {
                    lore.add(colorize("&8Hlasování momentálně není aktivní"));
                }
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private ItemStack createResultItem(Candidate candidate, boolean isWinner, ConfigurationSection config) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        if (meta != null) {
            // Set player head
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(candidate.getPlayerName()));
            
            ConfigurationSection itemConfig = isWinner ? 
                config.getConfigurationSection("winner") : 
                config.getConfigurationSection("candidate_result");
            
            // Calculate percentage
            List<Candidate> allCandidates = plugin.getElectionManager().getCandidates();
            int totalVotes = allCandidates.stream().mapToInt(Candidate::getVotes).sum();
            double percentage = totalVotes > 0 ? (double) candidate.getVotes() / totalVotes * 100 : 0;
            
            // Always set a name, even if config is missing
            String name;
            if (itemConfig != null && itemConfig.getString("name") != null) {
                name = itemConfig.getString("name", "%player%")
                    .replace("%player%", candidate.getPlayerName());
            } else {
                // Fallback name format
                if (isWinner) {
                    name = "&6&l🏆 Vítěz: " + candidate.getPlayerName();
                } else {
                    name = "&e" + candidate.getPlayerName();
                }
            }
            meta.setDisplayName(colorize(name));
            
            // Always create lore with candidate information
            List<String> lore = new ArrayList<>();
            
            // If we have config lore, use it and replace placeholders
            if (itemConfig != null && itemConfig.getStringList("lore") != null && !itemConfig.getStringList("lore").isEmpty()) {
                for (String line : itemConfig.getStringList("lore")) {
                    lore.add(colorize(line
                        .replace("%player%", candidate.getPlayerName())
                        .replace("%role%", candidate.getRole() != null ? candidate.getRole() : "Neuvedeno")
                        .replace("%slogan%", candidate.getSlogan() != null ? candidate.getSlogan() : "Žádný slogan")
                        .replace("%votes%", String.valueOf(candidate.getVotes()))
                        .replace("%percentage%", String.format("%.1f", percentage))));
                }
            } else {
                // Fallback lore if config is missing or empty
                lore.add(colorize("&7Role: &f" + (candidate.getRole() != null ? candidate.getRole() : "Neuvedeno")));
                lore.add(colorize("&7Slogan: &f" + (candidate.getSlogan() != null ? candidate.getSlogan() : "Žádný slogan")));
                lore.add(colorize("&7Hlasů: &f" + candidate.getVotes() + " (" + String.format("%.1f", percentage) + "%%)"));
                if (isWinner) {
                    lore.add("");
                    lore.add(colorize("&6Vítěz voleb!"));
                }
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private String colorize(String text) {
        // Simple color code replacement that definitely works
        if (text == null) return "";
        return text.replace("&0", "§0")
                  .replace("&1", "§1")
                  .replace("&2", "§2")
                  .replace("&3", "§3")
                  .replace("&4", "§4")
                  .replace("&5", "§5")
                  .replace("&6", "§6")
                  .replace("&7", "§7")
                  .replace("&8", "§8")
                  .replace("&9", "§9")
                  .replace("&a", "§a")
                  .replace("&b", "§b")
                  .replace("&c", "§c")
                  .replace("&d", "§d")
                  .replace("&e", "§e")
                  .replace("&f", "§f")
                  .replace("&k", "§k")
                  .replace("&l", "§l")
                  .replace("&m", "§m")
                  .replace("&n", "§n")
                  .replace("&o", "§o")
                  .replace("&r", "§r");
    }
}
