package cz.domca.elections.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import cz.domca.elections.WeeklyElectionsPlugin;
import cz.domca.elections.elections.Candidate;
import cz.domca.elections.elections.Election;
import cz.domca.elections.elections.ElectionPhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ElectionCommands implements CommandExecutor, TabCompleter {
    
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
            case "help":
                return handleHelp(sender);
                
            case "start":
                return handleStartElection(sender);
                
            case "progress":
                return handleProgressPhase(sender);
                
            case "reload":
                return handleReload(sender);
                
            case "rotate":
                return handleRotate(sender);
                
            case "cycle":
                return handleCycleRotation(sender);
                
            case "status":
                return handleStatus(sender);
                
            case "reputation":
                return handleReputation(sender, args);
                
            case "fixnpcs":
                return handleFixNPCs(sender);
                
            case "removenpc":
                return handleRemoveNPC(sender, args);
                
            case "cleanupnpcs":
                return handleCleanupNPCs(sender);
                
            case "region":
                return handleRegionCommands(sender, args);
                
            case "whereami":
                return handleWhereAmI(sender);
                
            default:
                // Check if it's a region ID for creating NPC
                if (plugin.getRegionManager().getRegion(args[0]) != null) {
                    return handleCreateNPC(sender, args[0]);
                } else {
                    sender.sendMessage(colorize("&cNeznámý příkaz! Použijte: /volby help pro nápovědu"));
                    return true;
                }
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("help", "start", "progress", "reload", "rotate", "cycle", "status", "reputation", "fixnpcs", "removenpc", "cleanupnpcs", "region", "whereami");
            
            // Add basic commands for all users
            completions.add("help");
            completions.add("whereami");
            completions.add("status");
            
            // Add admin commands only if player has permission
            if (sender.hasPermission("elections.admin")) {
                completions.addAll(subCommands);
                // Add region IDs for NPC creation
                completions.addAll(plugin.getRegionManager().getRegionRotation());
            }
            
            // Filter by what the player has typed so far
            return completions.stream()
                    .filter(comp -> comp.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
                    
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reputation")) {
            // Tab complete player names for reputation command
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("removenpc")) {
            // Tab complete region IDs for removenpc command
            return plugin.getRegionManager().getRegionRotation().stream()
                    .filter(region -> region.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("region")) {
            // Tab complete region subcommands
            return Arrays.asList("info", "check").stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("region") && args[1].equalsIgnoreCase("info")) {
            // Tab complete region IDs for info command
            return plugin.getRegionManager().getRegionRotation().stream()
                    .filter(region -> region.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return completions;
    }
    
    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(colorize("&6=== Nápověda pro volby ==="));
        sender.sendMessage(colorize("&e/volby &7- Otevřít hlavní menu voleb"));
        sender.sendMessage(colorize("&e/volby help &7- Zobrazit tuto nápovědu"));
        sender.sendMessage(colorize("&e/volby status &7- Zobrazit stav aktuálních voleb"));
        sender.sendMessage(colorize("&e/volby whereami &7- Zobrazit aktuální region"));
        
        if (sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&6=== Admin příkazy ==="));
            sender.sendMessage(colorize("&e/volby start &7- Spustit nové volby"));
            sender.sendMessage(colorize("&e/volby progress &7- Posunout fázi voleb"));
            sender.sendMessage(colorize("&e/volby reload &7- Znovu načíst konfiguraci"));
            sender.sendMessage(colorize("&e/volby rotate &7- Manuálně posunout volby"));
            sender.sendMessage(colorize("&e/volby cycle &7- Ukončit a přejít na další region"));
            sender.sendMessage(colorize("&e/volby reputation <hráč> <±množství> &7- Upravit reputaci"));
            sender.sendMessage(colorize("&e/volby fixnpcs &7- Opravit chybějící NPC"));
            sender.sendMessage(colorize("&e/volby removenpc <region> &7- Odstranit NPC a hologram"));
            sender.sendMessage(colorize("&e/volby cleanupnpcs &7- Vyčistit osiřelé hologramy"));
            sender.sendMessage(colorize("&e/volby region <info|check> &7- Informace o regionech"));
            sender.sendMessage(colorize("&e/volby <region> &7- Vytvořit NPC v regionu"));
        }
        
        return true;
    }

    private boolean handleStartElection(CommandSender sender) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        if (plugin.getElectionManager().isElectionActive()) {
            sender.sendMessage(colorize("&cVolby již jsou aktivní!"));
            return true;
        }
        
        try {
            String firstRegion = plugin.getRegionManager().getRegionRotation().get(0);
            plugin.getElectionManager().startNewElection(firstRegion);
            sender.sendMessage(colorize("&aVolby byly spuštěny v regionu: " + 
                plugin.getRegionManager().getRegion(firstRegion).getDisplayName()));
        } catch (Exception e) {
            sender.sendMessage(colorize("&cChyba při spuštění voleb: " + e.getMessage()));
            plugin.getLogger().severe("Failed to start election: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleProgressPhase(CommandSender sender) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        if (!plugin.getElectionManager().isElectionActive()) {
            sender.sendMessage(colorize("&cNejsou aktivní žádné volby!"));
            return true;
        }
        
        try {
            plugin.getElectionManager().progressElection();
            sender.sendMessage(colorize("&aFáze voleb byla posunuta vpřed!"));
        } catch (Exception e) {
            sender.sendMessage(colorize("&cChyba při posunu fáze: " + e.getMessage()));
            plugin.getLogger().severe("Failed to progress election phase: " + e.getMessage());
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
            sender.sendMessage(colorize("&aVolby byly manuálně posunuty do další fáze!"));
        } catch (Exception e) {
            sender.sendMessage(colorize("&cChyba při posunu voleb: " + e.getMessage()));
            plugin.getLogger().severe("Failed to rotate election: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleCycleRotation(CommandSender sender) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        try {
            // End current election and start new one in next region
            if (plugin.getElectionManager().isElectionActive()) {
                String currentRegion = plugin.getElectionManager().getCurrentElection().getRegionId();
                String nextRegion = plugin.getRegionManager().getNextRegion(currentRegion);
                
                // End current election properly
                plugin.getElectionManager().endCurrentElection();
                
                // Start new election in next region
                plugin.getElectionManager().startNewElection(nextRegion);
                
                sender.sendMessage(colorize("&aVolby byly ukončeny a cyklus byl přesunut do regionu: " + 
                    plugin.getRegionManager().getRegion(nextRegion).getDisplayName()));
            } else {
                sender.sendMessage(colorize("&cŽádné aktivní volby k ukončení!"));
            }
        } catch (Exception e) {
            sender.sendMessage(colorize("&cChyba při rotaci cyklu: " + e.getMessage()));
            plugin.getLogger().severe("Failed to rotate cycle: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleStatus(CommandSender sender) {
        Election currentElection = plugin.getElectionManager().getCurrentElection();
        
        if (currentElection == null) {
            sender.sendMessage(colorize("&cMomentálně neprobíhají žádné volby."));
            sender.sendMessage(colorize("&7Použijte &e/volby start &7pro zahájení nových voleb."));
            return true;
        }
        
        sender.sendMessage(colorize("&6=== Stav voleb ==="));
        sender.sendMessage(colorize("&eRegion: &f" + currentElection.getRegionId()));
        sender.sendMessage(colorize("&eFáze: &f" + currentElection.getPhase().name()));
        
        long now = System.currentTimeMillis();
        long endTimeMillis = currentElection.getEndTime().toEpochMilli();
        long timeLeft = endTimeMillis - now;
        
        if (timeLeft > 0) {
            long days = timeLeft / (1000 * 60 * 60 * 24);
            long hours = (timeLeft % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
            sender.sendMessage(colorize("&eČas do konce voleb: &f" + days + " dní, " + hours + " hodin"));
        } else {
            sender.sendMessage(colorize("&cVolby již skončily!"));
        }
        
        // Show candidates if in voting or results phase
        if (currentElection.getPhase() == ElectionPhase.VOTING || currentElection.getPhase() == ElectionPhase.RESULTS) {
            List<Candidate> candidates = plugin.getElectionManager().getCandidates();
            
            if (!candidates.isEmpty()) {
                sender.sendMessage(colorize("&6=== Kandidáti ==="));
                
                // Group by role
                Map<String, List<Candidate>> byRole = new java.util.HashMap<>();
                for (Candidate candidate : candidates) {
                    byRole.computeIfAbsent(candidate.getRole(), k -> new ArrayList<>()).add(candidate);
                }
                
                for (Map.Entry<String, List<Candidate>> entry : byRole.entrySet()) {
                    sender.sendMessage(colorize("&e" + entry.getKey() + ":"));
                    for (Candidate candidate : entry.getValue()) {
                        sender.sendMessage(colorize("  &f- " + candidate.getPlayerName() + 
                            " &7(" + candidate.getVotes() + " hlasů)"));
                    }
                }
            }
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
    
    private boolean handleRemoveNPC(CommandSender sender, String[] args) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(colorize("&cPoužití: /volby removenpc <region>"));
            return true;
        }
        
        String regionId = args[1];
        
        if (plugin.getRegionManager().getRegion(regionId) == null) {
            sender.sendMessage(colorize("&cRegion '" + regionId + "' neexistuje!"));
            return true;
        }
        
        if (!plugin.getNpcManager().hasNPC(regionId)) {
            sender.sendMessage(colorize("&cNPC pro tento region neexistuje!"));
            return true;
        }
        
        try {
            boolean success = plugin.getNpcManager().removeNPC(regionId);
            if (success) {
                String regionName = plugin.getRegionManager().getRegion(regionId).getDisplayName();
                sender.sendMessage(colorize("&aNPC a hologram pro region " + regionName + " byly odstraněny!"));
            } else {
                sender.sendMessage(colorize("&cNepodařilo se odstranit NPC!"));
            }
        } catch (Exception e) {
            sender.sendMessage(colorize("&cChyba při odstraňování NPC: " + e.getMessage()));
        }
        
        return true;
    }
    
    private boolean handleCleanupNPCs(CommandSender sender) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        try {
            sender.sendMessage(colorize("&eČistím osiřelé hologramy..."));
            plugin.getNpcManager().cleanupOrphanedHolograms();
            sender.sendMessage(colorize("&aOsiřelé hologramy byly vyčištěny!"));
        } catch (Exception e) {
            sender.sendMessage(colorize("&cChyba při čištění hologramů: " + e.getMessage()));
            plugin.getLogger().severe("Failed to cleanup orphaned holograms: " + e.getMessage());
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
    
    private boolean handleRegionCommands(CommandSender sender, String[] args) {
        if (!sender.hasPermission("elections.admin")) {
            sender.sendMessage(colorize("&cNemáte oprávnění k tomuto příkazu!"));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tento příkaz může použít pouze hráč!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            sender.sendMessage(colorize("&cPoužití: /volby region <info|check> [region]"));
            return true;
        }
        
        String subAction = args[1].toLowerCase();
        
        switch (subAction) {
            case "info":
                if (args.length < 3) {
                    sender.sendMessage(colorize("&cPoužití: /volby region info <region>"));
                    return true;
                }
                return showRegionInfo(player, args[2]);
                
            case "check":
                return checkPlayerRegion(player);
                
            default:
                sender.sendMessage(colorize("&cNeznámá akce! Použijte: info, check"));
                return true;
        }
    }
    
    private boolean handleWhereAmI(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tento příkaz může použít pouze hráč!");
            return true;
        }
        
        Player player = (Player) sender;
        String region = plugin.getRegionManager().getRegionAt(player.getLocation());
        
        if (region != null) {
            String regionName = plugin.getRegionManager().getRegion(region).getDisplayName();
            player.sendMessage(colorize("&aJste v regionu: " + regionName + " (" + region + ")"));
        } else {
            player.sendMessage(colorize("&7Nejste v žádném definovaném regionu."));
        }
        
        return true;
    }
    
    private boolean showRegionInfo(Player player, String regionId) {
        var region = plugin.getRegionManager().getRegion(regionId);
        if (region == null) {
            player.sendMessage(colorize("&cRegion '" + regionId + "' neexistuje!"));
            return true;
        }
        
        player.sendMessage(colorize("&6=== Informace o regionu ==="));
        player.sendMessage(colorize("&eID: &f" + region.getId()));
        player.sendMessage(colorize("&eNázev: &f" + region.getName()));
        player.sendMessage(colorize("&eZobrazovaný název: " + region.getDisplayName()));
        
        if (region.hasBoundary()) {
            var boundary = region.getBoundary();
            player.sendMessage(colorize("&eGranice:"));
            player.sendMessage(colorize("&7  Svět: &f" + boundary.getWorldName()));
            player.sendMessage(colorize("&7  X: &f" + boundary.getMinX() + " až " + boundary.getMaxX()));
            player.sendMessage(colorize("&7  Y: &f" + boundary.getMinY() + " až " + boundary.getMaxY()));
            player.sendMessage(colorize("&7  Z: &f" + boundary.getMinZ() + " až " + boundary.getMaxZ()));
        } else {
            player.sendMessage(colorize("&cŽádné hranice nejsou definovány!"));
        }
        
        return true;
    }
    
    private boolean checkPlayerRegion(Player player) {
        String region = plugin.getRegionManager().getRegionAt(player.getLocation());
        
        if (region != null) {
            var regionObj = plugin.getRegionManager().getRegion(region);
            player.sendMessage(colorize("&aJste v regionu: " + regionObj.getDisplayName() + " (" + region + ")"));
            
            // Check if there's an active election for this region
            if (plugin.getElectionManager().isElectionActive()) {
                String activeRegion = plugin.getElectionManager().getCurrentElection().getRegionId();
                if (activeRegion.equals(region)) {
                    player.sendMessage(colorize("&6V tomto regionu jsou aktivní volby!"));
                } else {
                    player.sendMessage(colorize("&7Volby jsou aktivní v jiném regionu."));
                }
            } else {
                player.sendMessage(colorize("&7Momentálně nejsou aktivní žádné volby."));
            }
        } else {
            player.sendMessage(colorize("&7Nejste v žádném definovaném regionu."));
            player.sendMessage(colorize("&7Pozice: " + 
                String.format("%.1f, %.1f, %.1f", 
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ())));
        }
        
        return true;
    }
    
    private String colorize(String text) {
        // Use Adventure API for modern color handling
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
