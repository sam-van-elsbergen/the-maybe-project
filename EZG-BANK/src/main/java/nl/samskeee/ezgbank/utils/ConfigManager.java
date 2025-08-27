package nl.samskeee.ezgbank.utils;

import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // placeholder voor eventuele toekomstige config helpers. Voor nu enkel wrapper.
    public JavaPlugin getPlugin() {
        return plugin;
    }
}
