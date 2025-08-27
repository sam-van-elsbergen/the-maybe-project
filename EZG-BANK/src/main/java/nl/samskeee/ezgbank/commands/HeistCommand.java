package nl.samskeee.ezgbank.commands;

import nl.samskeee.ezgbank.Main;
import nl.samskeee.ezgbank.utils.RegionUtils;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HeistCommand implements CommandExecutor {

    private static final Map<UUID, Long> lastHeist = new ConcurrentHashMap<>();
    private static final Set<UUID> inHeist = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    @SuppressWarnings("unchecked")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        Main plugin = Main.getInstance();

        int duration = plugin.getConfig().getInt("heist.duration", 60);
        int cooldown = plugin.getConfig().getInt("heist.cooldown", 300);
        double maxDistance = plugin.getConfig().getDouble("heist.max-distance", 100.0);

        // REQUIRED ITEMS from config (safely read)
        List<Map<?, ?>> requiredListRaw = plugin.getConfig().getMapList("required-items");
        boolean consumeRequired = plugin.getConfig().getBoolean("consume-required-items", true);

        // Reward item config
        String rewardMatStr = plugin.getConfig().getString("reward.item.material", "PAPER");
        int rewardMin = plugin.getConfig().getInt("reward.item.min", 1);
        int rewardMax = plugin.getConfig().getInt("reward.item.max", 3);
        String rewardDisplay = plugin.getConfig().getString("reward.item.display-name", "").trim();

        if (args.length == 0 || args[0].equalsIgnoreCase("start")) {

            if (inHeist.contains(uuid)) {
                player.sendMessage(ChatColor.YELLOW + translate("already_heisting", "Je bent al bezig met een overval."));
                return true;
            }

            if (lastHeist.containsKey(uuid)) {
                long last = lastHeist.get(uuid);
                long remaining = (last + cooldown * 1000L) - System.currentTimeMillis();
                if (remaining > 0) {
                    long sec = (remaining + 999) / 1000;
                    player.sendMessage(ChatColor.RED + translate("on_cooldown", "Wacht nog " + sec + "s voordat je opnieuw kunt overvallen."));
                    return true;
                }
            }

            // Check required items (safe reading from Map<?,?>)
            if (requiredListRaw != null && !requiredListRaw.isEmpty()) {
                Map<Material, Integer> requiredMap = new HashMap<>();
                for (Map<?, ?> m : requiredListRaw) {
                    try {
                        Object matObj = m.get("material");
                        String mat = matObj != null ? String.valueOf(matObj).toUpperCase() : "";
                        Object amtObj = m.get("amount");
                        String amtStr = amtObj != null ? String.valueOf(amtObj) : "1";
                        int amt = Integer.parseInt(amtStr);
                        Material material = Material.matchMaterial(mat);
                        if (material == null) continue;
                        requiredMap.put(material, requiredMap.getOrDefault(material, 0) + amt);
                    } catch (Exception ignored) {}
                }

                // Verify player inventory has required amounts
                boolean ok = true;
                for (Map.Entry<Material, Integer> e : requiredMap.entrySet()) {
                    Material mat = e.getKey();
                    int needed = e.getValue();
                    int found = 0;
                    for (ItemStack is : player.getInventory().getContents()) {
                        if (is == null) continue;
                        if (is.getType() == mat) found += is.getAmount();
                    }
                    if (found < needed) {
                        ok = false;
                        player.sendMessage(ChatColor.RED + translate("require_item_missing", "Je mist vereiste items: " + needed + "x " + mat.name()));
                    }
                }
                if (!ok) return true;

                // Consume items if configured
                if (consumeRequired) {
                    for (Map.Entry<Material, Integer> e : requiredMap.entrySet()) {
                        int toRemove = e.getValue();
                        Material mat = e.getKey();
                        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                            ItemStack is = player.getInventory().getItem(slot);
                            if (is == null) continue;
                            if (is.getType() != mat) continue;
                            int amt = is.getAmount();
                            if (amt <= toRemove) {
                                player.getInventory().setItem(slot, null);
                                toRemove -= amt;
                            } else {
                                is.setAmount(amt - toRemove);
                                player.getInventory().setItem(slot, is);
                                toRemove = 0;
                            }
                            if (toRemove <= 0) break;
                        }
                    }
                    player.updateInventory();
                }
            }

            // Location check
            Location loc = player.getLocation();
            boolean allowed = false;

            List<String> allowedRegions = plugin.getConfig().getStringList("banks.regions");
            if (RegionUtils.hasWorldGuard()) {
                try {
                    if (RegionUtils.isInAllowedRegion(loc, allowedRegions)) allowed = true;
                } catch (Throwable t) { /* fallback */ }
            }
            if (!allowed) {
                boolean coordinateMatched = false;
                Object raw = plugin.getConfig().get("banks.coordinates");
                if (raw instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> coords = (List<Object>) raw;
                    for (Object obj : coords) {
                        if (!(obj instanceof Map)) continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cs = (Map<String, Object>) obj;
                        try {
                            String worldName = cs.containsKey("world") ? String.valueOf(cs.get("world")) : loc.getWorld().getName();
                            double x = cs.containsKey("x") ? Double.parseDouble(String.valueOf(cs.get("x"))) : loc.getX();
                            double y = cs.containsKey("y") ? Double.parseDouble(String.valueOf(cs.get("y"))) : loc.getY();
                            double z = cs.containsKey("z") ? Double.parseDouble(String.valueOf(cs.get("z"))) : loc.getZ();
                            if (!loc.getWorld().getName().equals(worldName)) continue;
                            Location bankLoc = new Location(loc.getWorld(), x, y, z);
                            if (bankLoc.distance(loc) <= maxDistance) {
                                coordinateMatched = true;
                                break;
                            }
                        } catch (Exception ignore) {}
                    }
                }
                if (coordinateMatched) allowed = true;
            }

            if (!allowed) {
                player.sendMessage(ChatColor.RED + translate("not_in_bank", "Je bevindt je niet in een bank-regio of nabij een bank-coördinaat."));
                return true;
            }

            // Start heist
            inHeist.add(uuid);
            Location startLoc = player.getLocation();
            player.sendMessage(ChatColor.GREEN + translate("heist_started", "Bankoverval gestart! Blijf in bereik en wacht tot de overval voorbij is."));

            new BukkitRunnable() {
                int secondsLeft = duration;

                @Override
                public void run() {
                    if (!inHeist.contains(uuid)) { cancel(); return; }
                    if (!player.isOnline()) {
                        inHeist.remove(uuid);
                        lastHeist.put(uuid, System.currentTimeMillis());
                        cancel();
                        return;
                    }
                    if (player.getLocation().distance(startLoc) > maxDistance) {
                        inHeist.remove(uuid);
                        lastHeist.put(uuid, System.currentTimeMillis());
                        player.sendMessage(ChatColor.RED + translate("heist_failed_move", "De overval is afgebroken omdat je te ver bent gelopen."));
                        cancel();
                        return;
                    }
                    secondsLeft--;
                    if (secondsLeft <= 0) {
                        inHeist.remove(uuid);
                        lastHeist.put(uuid, System.currentTimeMillis());

                        // Reward: give configured item(s)
                        Material rewardMat = Material.matchMaterial(rewardMatStr.toUpperCase());
                        int rewardAmount = rewardMin;
                        if (rewardMax > rewardMin) {
                            rewardAmount = new Random().nextInt(rewardMax - rewardMin + 1) + rewardMin;
                        }
                        if (rewardMat != null) {
                            ItemStack reward = new ItemStack(rewardMat, rewardAmount);
                            if (!rewardDisplay.isEmpty()) {
                                ItemMeta meta = reward.getItemMeta();
                                if (meta != null) {
                                    meta.setDisplayName(rewardDisplay.replace("&", "§"));
                                    reward.setItemMeta(meta);
                                }
                            }
                            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
                            if (!leftover.isEmpty()) {
                                leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                            }
                            player.sendMessage(ChatColor.GREEN + translate("heist_success", "Overval geslaagd! Je ontving " + rewardAmount + "x " + rewardMat.name()));
                        } else {
                            if (Main.getInstance().getEconomy() != null) {
                                int money = rewardMin;
                                EconomyResponse resp = Main.getInstance().getEconomy().depositPlayer(player, money);
                                if (resp.transactionSuccess()) {
                                    player.sendMessage(ChatColor.GREEN + translate("heist_success_money", "Overval geslaagd! Je ontving " + money + " (geld)."));
                                } else {
                                    player.sendMessage(ChatColor.RED + translate("heist_reward_fail", "De overval eindigde maar er trad een fout op bij het geven van beloning."));
                                }
                            } else {
                                player.sendMessage(ChatColor.RED + translate("heist_reward_fail", "De overval eindigde maar er trad een fout op bij het geven van beloning (geen reward-materiaal en geen economy)."));
                            }
                        }

                        cancel();
                    }
                }
            }.runTaskTimer(Main.getInstance(), 20L, 20L);

            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("status")) {
            if (inHeist.contains(uuid)) player.sendMessage(ChatColor.GREEN + translate("status_busy", "Je bent bezig met een overval."));
            else player.sendMessage(ChatColor.YELLOW + translate("status_idle", "Je bent niet in een overval."));
            return true;
        }

        player.sendMessage(ChatColor.RED + translate("usage", "Gebruik: /bankoverval start"));
        return true;
    }

    private String translate(String key, String def) {
        Main plugin = Main.getInstance();
        String lang = plugin.getConfig().getString("language", "nl");
        if ("en".equalsIgnoreCase(lang)) {
            switch (key) {
                case "already_heisting": return "You are already performing a heist.";
                case "on_cooldown": return def;
                case "not_in_bank": return "You are not inside a bank region or near a bank coordinate.";
                case "heist_started": return "Heist started! Stay close to the bank until it's finished.";
                case "heist_failed_move": return "Heist aborted because you moved too far away.";
                case "heist_success": return def;
                case "heist_reward_fail": return def;
                case "status_busy": return "You are currently doing a heist.";
                case "status_idle": return "You are not doing a heist.";
                case "usage": return "Usage: /bankoverval start";
                default: return def;
            }
        } else {
            return def;
        }
    }
}
