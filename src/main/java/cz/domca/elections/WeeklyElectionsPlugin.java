package cz.domca.elections;

import java.util.logging.Level;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import cz.domca.elections.commands.ElectionCommands;
import cz.domca.elections.config.ConfigManager;
import cz.domca.elections.database.DatabaseManager;
import cz.domca.elections.elections.ElectionManager;
import cz.domca.elections.gui.GuiManager;
import cz.domca.elections.holograms.HologramManager;
import cz.domca.elections.listeners.NPCListener;
import cz.domca.elections.npc.NPCManager;
import cz.domca.elections.regions.RegionManager;
import cz.domca.elections.registration.CandidateRegistrationManager;
import cz.domca.elections.reputation.ReputationManager;
import cz.domca.elections.roles.RoleAssignmentManager;
import cz.domca.elections.tasks.ElectionTask;
import net.luckperms.api.LuckPerms;

public class WeeklyElectionsPlugin extends JavaPlugin {
    
    private static WeeklyElectionsPlugin instance;
    
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private RegionManager regionManager;
    private ElectionManager electionManager;
    private NPCManager npcManager;
    private GuiManager guiManager;
    private HologramManager hologramManager;
    private ReputationManager reputationManager;
    private CandidateRegistrationManager registrationManager;
    private RoleAssignmentManager roleAssignmentManager;
    private LuckPerms luckPerms;
    
    @Override
    public void onEnable() {
        instance = this;
        
        getLogger().info("Enabling WeeklyElections plugin...");
        
        if (!setupLuckPerms()) {
            getLogger().severe("LuckPerms not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        if (!checkCitizens()) {
            getLogger().severe("Citizens not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        try {
            // Initialize managers
            this.configManager = new ConfigManager(this);
            this.databaseManager = new DatabaseManager(this);
            this.regionManager = new RegionManager(this);
            this.reputationManager = new ReputationManager(this);
            this.electionManager = new ElectionManager(this);
            this.hologramManager = new HologramManager(this);
            this.npcManager = new NPCManager(this);
            this.guiManager = new GuiManager(this);
            this.registrationManager = new CandidateRegistrationManager(this);
            this.roleAssignmentManager = new RoleAssignmentManager(this);
            
            // Load configurations
            configManager.loadConfigs();
            
            // Initialize database
            databaseManager.initialize();
            
            // Load regions
            regionManager.loadRegions();
            
            // Initialize NPCs
            npcManager.initializeNPCs();
            
            // Register listeners
            getServer().getPluginManager().registerEvents(new NPCListener(this), this);
            
            // Register commands
            getCommand("volby").setExecutor(new ElectionCommands(this));
            
            // Start election task
            new ElectionTask(this).runTaskTimer(this, 20L, 1200L); // Run every minute
            
            getLogger().info("WeeklyElections plugin enabled successfully!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Disabling WeeklyElections plugin...");
        
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        
        if (npcManager != null) {
            npcManager.shutdown();
        }
        
        getLogger().info("WeeklyElections plugin disabled!");
    }
    
    private boolean setupLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
            return true;
        }
        return false;
    }
    
    private boolean checkCitizens() {
        return getServer().getPluginManager().isPluginEnabled("Citizens");
    }
    
    public static WeeklyElectionsPlugin getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public RegionManager getRegionManager() {
        return regionManager;
    }
    
    public ElectionManager getElectionManager() {
        return electionManager;
    }
    
    public NPCManager getNpcManager() {
        return npcManager;
    }
    
    public GuiManager getGuiManager() {
        return guiManager;
    }
    
    public HologramManager getHologramManager() {
        return hologramManager;
    }
    
    public ReputationManager getReputationManager() {
        return reputationManager;
    }
    
    public CandidateRegistrationManager getRegistrationManager() {
        return registrationManager;
    }
    
    public RoleAssignmentManager getRoleAssignmentManager() {
        return roleAssignmentManager;
    }
    
    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
}
