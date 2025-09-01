package cz.domca.elections.commands;

import cz.domca.elections.WeeklyElectionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ElectionCommands implements CommandExecutor {
    
    private final WeeklyElectionsPlugin plugin;
    
    public ElectionCommands(WeeklyElectionsPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (args.length == 0) {
            if (sender instanceof Player) {
                // Open main GUI for current region
                Player player = (Player) sender;
                String currentRegion = plugin.getRegionManager().getCurrentActiveRegion();
                if (currentRegion != null) {
                    plugin.getGuiManager().openMainMenu(player, currentRegion);
                } else {
                    player.sendMessage(colorize("&cMomentálně nejsou aktivní žádné volby!"));
                }
            } else {
                sender.sendMessage("Tento příkaz může použít pouze hráč!");
            }
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                return handleReload(sender);
                
            case "rotate":
                return handleRotate(sender);
                
            case "reputation":
                return handleReputation(sender, args);
                
            case "fixnpcs":
                return handleFixNPCs(sender);
                
            default:
                // Check if it's a region ID for creating NPC
                if (plugin.getRegionManager().getRegion(args[0]) != null) {
                    return handleCreateNPC(sender, args[0]);
                } else {
                    sender.sendMessage(colorize("&cNeznámý příkaz! Použijte: /volby [reload|rotate|reputation|fixnpcs|<region>]"));
                }
        }
        
        return true;
    }
    
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        try {
            plugin.getConfigManager().reloadConfigs();
            plugin.getRegionManager().loadRegions();
            sender.sendMessage(colorize("&aKonfigurace byla úspěšně znovu načtena!"));
        } catch (Exception e) {
            sender.sendMessage(colorize("&cChyba při načítání konfigurace: " + e.getMessage()));
        }
        
        return true;
    }
    
    private boolean handleRotate(CommandSender sender) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        try {
            plugin.getElectionManager().progressElection();
            sender.sendMessage(colorize("&aVolby byly ručně ukončeny a cyklus přesunut do dalšího regionu!"));
        } catch (Exception e) {
            sender.sendMessage(colorize("&cChyba při rotaci voleb: " + e.getMessage()));
        }
        
        return true;
    }
    
    private boolean handleReputation(CommandSender sender, String[] args) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(colorize("&cPoužití: /volby reputation <hráč> <±množství>"));
            return true;
        }
        
        String playerName = args[1];
        Player target = plugin.getServer().getPlayer(playerName);
        
        if (target == null) {
            sender.sendMessage(colorize("&cHráč nebyl nalezen!"));
            return true;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            String reason = "Upraveno administrátorem";
            
            plugin.getReputationManager().addReputation(
                target.getUniqueId().toString(),
                target.getName(),
                amount,
                reason
            );
            
            sender.sendMessage(colorize("&aReputace hráče " + target.getName() + " byla upravena o " + amount + " bodů!"));
            
        } catch (NumberFormatException e) {
            sender.sendMessage(colorize("&cNeplatné číslo!"));
        }
        
        return true;
    }
    
    private boolean handleFixNPCs(CommandSender sender) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        try {
            plugin.getNpcManager().fixMissingNPCs();
            sender.sendMessage(colorize("&aChybějící NPC byli obnoveni!"));
        } catch (Exception e) {
            sender.sendMessage(colorize("&cChyba při opravě NPC: " + e.getMessage()));
        }
        
        return true;
    }
    
    private boolean handleCreateNPC(CommandSender sender, String regionId) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tento příkaz může použít pouze hráč!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (plugin.getNpcManager().hasNPC(regionId)) {
            player.sendMessage(colorize("&cNPC pro tento region již existuje!"));
            return true;
        }
        
        try {
            boolean success = plugin.getNpcManager().createNPC(regionId, player.getLocation());
            if (success) {
                String regionName = plugin.getRegionManager().getRegion(regionId).getDisplayName();
                player.sendMessage(colorize("&aNPC pro volby byl vytvořen v regionu " + regionName + "!"));
            } else {
                player.sendMessage(colorize("&cNepodařilo se vytvořit NPC!"));
            }
        } catch (Exception e) {
            player.sendMessage(colorize("&cChyba při vytváření NPC: " + e.getMessage()));
        }
        
        return true;
    }
    
    private String colorize(String text) {
        return text.replace("&", "§");
    }
}
