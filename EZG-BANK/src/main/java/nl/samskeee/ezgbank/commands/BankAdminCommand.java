package nl.samskeee.ezgbank.commands;

import nl.samskeee.ezgbank.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BankAdminCommand implements CommandExecutor {

    // Permissions: ezgbank.admin
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ezgbank.admin")) {
            sender.sendMessage(ChatColor.RED + "Geen permissie.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "BankAdmin commands:");
            sender.sendMessage(ChatColor.YELLOW + "/bankadmin addregion <regionId>");
            sender.sendMessage(ChatColor.YELLOW + "/bankadmin removeregion <regionId>");
            sender.sendMessage(ChatColor.YELLOW + "/bankadmin addcoord <x> <y> <z>");
            sender.sendMessage(ChatColor.YELLOW + "/bankadmin removecoord <index>");
            sender.sendMessage(ChatColor.YELLOW + "/bankadmin list");
            return true;
        }

        Main plugin = Main.getInstance();
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "addregion":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Gebruik: /bankadmin addregion <regionId>");
                    return true;
                }
                String regionId = args[1];
                List<String> regions = plugin.getConfig().getStringList("banks.regions");
                if (regions.contains(regionId)) {
                    sender.sendMessage(ChatColor.RED + "Region bestaat al.");
                    return true;
                }
                regions.add(regionId);
                plugin.getConfig().set("banks.regions", regions);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Region toegevoegd: " + regionId);
                return true;

            case "removeregion":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Gebruik: /bankadmin removeregion <regionId>");
                    return true;
                }
                String remId = args[1];
                List<String> regions2 = plugin.getConfig().getStringList("banks.regions");
                if (!regions2.contains(remId)) {
                    sender.sendMessage(ChatColor.RED + "Region niet gevonden.");
                    return true;
                }
                regions2.remove(remId);
                plugin.getConfig().set("banks.regions", regions2);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Region verwijderd: " + remId);
                return true;

            case "addcoord":
                if (!(sender instanceof Player) && args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Gebruik (console): /bankadmin addcoord <world> <x> <y> <z>");
                    return true;
                }
                if (sender instanceof Player && args.length == 1) {
                    // use player location
                    Player p = (Player) sender;
                    double x = p.getLocation().getX();
                    double y = p.getLocation().getY();
                    double z = p.getLocation().getZ();
                    String world = p.getWorld().getName();
                    addCoord(plugin, world, x, y, z, sender);
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Gebruik: /bankadmin addcoord <x> <y> <z> (of als console: addcoord <world> <x> <y> <z>)");
                    return true;
                }
                try {
                    String worldName;
                    int off = 0;
                    if (args.length == 4) {
                        worldName = (sender instanceof Player) ? ((Player) sender).getWorld().getName() : plugin.getServer().getWorlds().get(0).getName();
                    } else {
                        worldName = args[1];
                        off = 1;
                    }
                    double x = Double.parseDouble(args[1 + off]);
                    double y = Double.parseDouble(args[2 + off]);
                    double z = Double.parseDouble(args[3 + off]);
                    addCoord(plugin, worldName, x, y, z, sender);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Ongeldige coördinaten.");
                }
                return true;

            case "removecoord":
                // safe mutable copy of map-list
                List<Map<String, Object>> coordsListRem = new ArrayList<>();
                List<Map<?, ?>> rawListRem = plugin.getConfig().getMapList("banks.coordinates");
                if (rawListRem != null) {
                    for (Map<?, ?> m : rawListRem) {
                        coordsListRem.add(new HashMap<>((Map) m));
                    }
                }

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Gebruik: /bankadmin removecoord <index>");
                    return true;
                }
                try {
                    int idx = Integer.parseInt(args[1]);
                    if (idx < 0 || idx >= coordsListRem.size()) {
                        sender.sendMessage(ChatColor.RED + "Index out of range. Gebruik /bankadmin list om indices te bekijken.");
                        return true;
                    }
                    coordsListRem.remove(idx);
                    plugin.getConfig().set("banks.coordinates", coordsListRem);
                    plugin.saveConfig();
                    sender.sendMessage(ChatColor.GREEN + "Coördinaat verwijderd (index " + idx + ").");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Ongeldige index.");
                }
                return true;

            case "list":
                sender.sendMessage(ChatColor.AQUA + "=== Banks ===");
                List<String> regs = plugin.getConfig().getStringList("banks.regions");
                sender.sendMessage(ChatColor.YELLOW + "Regions:");
                if (regs.isEmpty()) sender.sendMessage(ChatColor.GRAY + "  (geen)");
                else regs.forEach(r -> sender.sendMessage(ChatColor.GRAY + "  - " + r));

                List<Map<?, ?>> coordsListRaw = plugin.getConfig().getMapList("banks.coordinates");
                sender.sendMessage(ChatColor.YELLOW + "Coordinates:");
                if (coordsListRaw == null || coordsListRaw.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "  (geen)");
                } else {
                    for (int i = 0; i < coordsListRaw.size(); i++) {
                        Map<?, ?> m = coordsListRaw.get(i);
                        Object worldObj = m.get("world");
                        Object xObj = m.get("x");
                        Object yObj = m.get("y");
                        Object zObj = m.get("z");
                        String worldStr = worldObj != null ? String.valueOf(worldObj) : "";
                        String xStr = xObj != null ? String.valueOf(xObj) : "";
                        String yStr = yObj != null ? String.valueOf(yObj) : "";
                        String zStr = zObj != null ? String.valueOf(zObj) : "";
                        sender.sendMessage(ChatColor.GRAY + "  [" + i + "] " + worldStr + " " + xStr + ", " + yStr + ", " + zStr);
                    }
                }
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Onbekend subcommand.");
                return true;
        }
    }

    private void addCoord(Main plugin, String world, double x, double y, double z, CommandSender sender) {
        List<Map<String, Object>> coords = new ArrayList<>();

        // copy existing coords into mutable typed list
        List<Map<?, ?>> existing = plugin.getConfig().getMapList("banks.coordinates");
        if (existing != null) {
            for (Map<?, ?> m : existing) {
                coords.add(new HashMap<>((Map) m));
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("world", world);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        coords.add(map);

        plugin.getConfig().set("banks.coordinates", coords);
        plugin.saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Coördinaat toegevoegd: " + world + " " + x + "," + y + "," + z);
    }
}
