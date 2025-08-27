package nl.samskeee.ezgbank.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;

public class RegionUtils {

    private static boolean hasWG = false;
    private static boolean initialized = false;

    public static boolean setupWorldGuard() {
        if (initialized) return hasWG;
        initialized = true;
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null) {
            hasWG = false;
            return false;
        }
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            hasWG = true;
            return true;
        } catch (Throwable t) {
            hasWG = false;
            return false;
        }
    }

    public static boolean hasWorldGuard() {
        return hasWG;
    }

    public static boolean isInAllowedRegion(Location loc, List<String> allowedRegionNames) {
        if (!hasWG) return false;
        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Method getPlatform = worldGuardClass.getMethod("getPlatform");
            Object platform = getPlatform.invoke(null);

            Method getRegionContainer = platform.getClass().getMethod("getRegionContainer");
            Object regionContainer = getRegionContainer.invoke(platform);

            Method createQuery = regionContainer.getClass().getMethod("createQuery");
            Object query = createQuery.invoke(regionContainer);

            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method asBlockVector3 = bukkitAdapter.getMethod("asBlockVector", org.bukkit.Location.class);
            Object blockVector3 = asBlockVector3.invoke(null, loc);

            Class<?> regionQueryClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
            Method getApplicableRegions = regionQueryClass.getMethod("getApplicableRegions", Class.forName("com.sk89q.worldedit.math.BlockVector3"));
            Object applicableSet = getApplicableRegions.invoke(query, blockVector3);

            try {
                Method iteratorMethod = applicableSet.getClass().getMethod("iterator");
                java.util.Iterator<?> it = (java.util.Iterator<?>) iteratorMethod.invoke(applicableSet);
                while (it.hasNext()) {
                    Object protectedRegion = it.next();
                    Method getId = protectedRegion.getClass().getMethod("getId");
                    String id = (String) getId.invoke(protectedRegion);
                    if (allowedRegionNames.contains(id)) return true;
                }
            } catch (NoSuchMethodException nsme) {
                Method getRegions = applicableSet.getClass().getMethod("getRegions");
                Object regionsCollection = getRegions.invoke(applicableSet);
                java.lang.Iterable<?> iterable = (java.lang.Iterable<?>) regionsCollection;
                for (Object protectedRegion : iterable) {
                    Method getId = protectedRegion.getClass().getMethod("getId");
                    String id = (String) getId.invoke(protectedRegion);
                    if (allowedRegionNames.contains(id)) return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
