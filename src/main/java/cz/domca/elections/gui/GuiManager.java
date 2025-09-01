package cz.domca.elections.gui;

import cz.domca.elections.WeeklyElectionsPlugin;
import cz.domca.elections.elections.Candidate;
import cz.domca.elections.elections.ElectionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GuiManager {
    
    private final WeeklyElectionsPlugin plugin;
    
    public GuiManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void openMainMenu(Player player, String regionId) {
        ConfigurationSection mainMenuConfig = plugin.getConfigManager().getConfig("gui.yml").getConfigurationSection("main_menu");
        if (mainMenuConfig == null) return;
        
        String title = colorize(mainMenuConfig.getString("title", "Volby"))
            .replace("%region%", plugin.getRegionManager().getRegion(regionId).getDisplayName());
        int size = mainMenuConfig.getInt("size", 27);
        
        Inventory inventory = Bukkit.createInventory(null, size, title);
        
        // Add items
        ConfigurationSection itemsConfig = mainMenuConfig.getConfigurationSection("items");
        if (itemsConfig != null) {
            for (String itemKey : itemsConfig.getKeys(false)) {
                ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(itemKey);
                if (itemConfig != null) {
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
        
        Inventory inventory = Bukkit.createInventory(null, size, title);
        
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
    
    public void openVotingGui(Player player, String regionId, int page) {
        ConfigurationSection votingConfig = plugin.getConfigManager().getConfig("gui.yml").getConfigurationSection("voting_gui");
        if (votingConfig == null) return;
        
        String title = colorize(votingConfig.getString("title", "Hlasování"))
            .replace("%region%", plugin.getRegionManager().getRegion(regionId).getDisplayName());
        int size = votingConfig.getInt("size", 54);
        
        Inventory inventory = Bukkit.createInventory(null, size, title);
        
        List<Candidate> candidates = plugin.getElectionManager().getCandidates();
        boolean hasVoted = plugin.getElectionManager().hasVoted(player.getUniqueId().toString());
        
        // Calculate pagination
        int itemsPerPage = 45; // 9x5 grid
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, candidates.size());
        
        // Add candidate items
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Candidate candidate = candidates.get(i);
            ItemStack item = createCandidateItem(candidate, hasVoted, votingConfig);
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
            .replace("%region%", plugin.getRegionManager().getRegion(regionId).getDisplayName());
        int size = resultsConfig.getInt("size", 54);
        
        Inventory inventory = Bukkit.createInventory(null, size, title);
        
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
    
    private ItemStack createCandidateItem(Candidate candidate, boolean hasVoted, ConfigurationSection config) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        if (meta != null) {
            // Set player head
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(candidate.getPlayerName()));
            
            // Set name and lore
            ConfigurationSection itemConfig = hasVoted ? 
                config.getConfigurationSection("voted_candidate") : 
                config.getConfigurationSection("candidate");
            
            if (itemConfig != null) {
                String name = itemConfig.getString("name", "%player%")
                    .replace("%player%", candidate.getPlayerName());
                meta.setDisplayName(colorize(name));
                
                List<String> lore = new ArrayList<>();
                for (String line : itemConfig.getStringList("lore")) {
                    lore.add(colorize(line
                        .replace("%player%", candidate.getPlayerName())
                        .replace("%role%", candidate.getRole())
                        .replace("%slogan%", candidate.getSlogan() != null ? candidate.getSlogan() : "Žádný slogan")));
                }
                meta.setLore(lore);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private ItemStack createResultItem(Candidate candidate, boolean isWinner, ConfigurationSection config) {
        ItemStack item;
        ItemMeta meta;
        
        ConfigurationSection itemConfig = isWinner ? 
            config.getConfigurationSection("winner") : 
            config.getConfigurationSection("candidate_result");
        
        if (itemConfig != null) {
            String materialName = itemConfig.getString("material", "PLAYER_HEAD");
            Material material = Material.valueOf(materialName);
            item = new ItemStack(material);
            meta = item.getItemMeta();
            
            if (meta != null) {
                String name = itemConfig.getString("name", "%player%")
                    .replace("%player%", candidate.getPlayerName());
                meta.setDisplayName(colorize(name));
                
                // Calculate percentage
                List<Candidate> allCandidates = plugin.getElectionManager().getCandidates();
                int totalVotes = allCandidates.stream().mapToInt(Candidate::getVotes).sum();
                double percentage = totalVotes > 0 ? (double) candidate.getVotes() / totalVotes * 100 : 0;
                
                List<String> lore = new ArrayList<>();
                for (String line : itemConfig.getStringList("lore")) {
                    lore.add(colorize(line
                        .replace("%player%", candidate.getPlayerName())
                        .replace("%role%", candidate.getRole())
                        .replace("%votes%", String.valueOf(candidate.getVotes()))
                        .replace("%percentage%", String.format("%.1f", percentage))));
                }
                meta.setLore(lore);
                
                item.setItemMeta(meta);
            }
        } else {
            item = new ItemStack(Material.PLAYER_HEAD);
            meta = item.getItemMeta();
        }
        
        return item;
    }
    
    private String colorize(String text) {
        return text.replace("&", "§");
    }
}
