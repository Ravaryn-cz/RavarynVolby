package cz.domca.elections.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import cz.domca.elections.WeeklyElectionsPlugin;

public class ConfigManager {
    
    private final WeeklyElectionsPlugin plugin;
    private final Map<String, FileConfiguration> configs;
    private final Map<String, File> configFiles;
    
    public ConfigManager(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();
        this.configFiles = new HashMap<>();
    }
    
    public void loadConfigs() {
        // Main config
        loadConfig("config.yml");
        
        // Other configs
        loadConfig("regions.yml");
        loadConfig("elections_requirement.yml");
        loadConfig("gui.yml");
        loadConfig("reputation_rewards.yml");
        
        plugin.getLogger().info("All configuration files loaded successfully!");
    }
    
    private void loadConfig(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        configs.put(fileName, config);
        configFiles.put(fileName, configFile);
        
        plugin.getLogger().info("Loaded configuration: " + fileName);
    }
    
    public FileConfiguration getConfig(String fileName) {
        return configs.get(fileName);
    }
    
    public void saveConfig(String fileName) {
        FileConfiguration config = configs.get(fileName);
        File configFile = configFiles.get(fileName);
        
        if (config != null && configFile != null) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save config: " + fileName, e);
            }
        }
    }
    
    public void reloadConfigs() {
        configs.clear();
        configFiles.clear();
        loadConfigs();
        plugin.getLogger().info("All configuration files reloaded!");
    }
    
    // Convenience methods for main config
    public String getDatabaseType() {
        return getConfig("config.yml").getString("database.type", "sqlite");
    }
    
    public String getDatabaseFile() {
        return getConfig("config.yml").getString("database.file", "elections.db");
    }
    
    public int getRegistrationDuration() {
        return getConfig("config.yml").getInt("election.registration_duration", 7);
    }
    
    public int getVotingDuration() {
        return getConfig("config.yml").getInt("election.voting_duration", 7);
    }
    
    public int getMandateDuration() {
        return getConfig("config.yml").getInt("election.mandate_duration", 30);
    }
    
    public boolean isDebugEnabled() {
        return getConfig("config.yml").getBoolean("debug", false);
    }
}
