package nl.samskeee.ezgbank;

import nl.samskeee.ezgbank.Managers.LicenseManager;
import nl.samskeee.ezgbank.commands.BankAdminCommand;
import nl.samskeee.ezgbank.commands.HeistCommand;
import nl.samskeee.ezgbank.update.UpdateChecker;
import nl.samskeee.ezgbank.utils.RegionUtils;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    // VERVANG DIT MET JE ECHTE API_SECRET (vereist door LicenseGate)
    private static final String LICENSE_API_SECRET = "ee1eb49a-f5f5-4b2c-afdd-491cc59a3ada";

    // Hardcoded GitHub repo voor updatechecker (owner/repo)
    private static final String GITHUB_REPO = "owner/bankheist-plugin";

    private static Main instance;
    private Economy economy;
    private LicenseManager licenseManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        getLogger().info("[EZG-Bank] Starten...");

        // License check
        String licenseKey = getConfig().getString("license-key", "").trim();
        licenseManager = new LicenseManager(LICENSE_API_SECRET, this);

        try {
            boolean valid = licenseManager.verifyLicense(licenseKey);
            if (!valid) {
                getLogger().severe("[EZG-Bank] Ongeldige of ontbrekende license-key. Plugin wordt uitgeschakeld.");
                getPluginLoader().disablePlugin(this);
                return;
            } else {
                getLogger().info("[EZG-Bank] Licentie geldig.");
            }
        } catch (Exception e) {
            getLogger().severe("[EZG-Bank] Fout bij licentiecontrole: " + e.getMessage());
            getPluginLoader().disablePlugin(this);
            return;
        }

        // Setup Vault economie (nog steeds gebruikt als fallback)
        if (!setupEconomy()) {
            getLogger().info("[EZG-Bank] Vault/economy provider niet gevonden. Economy-fallback uitschakelen (item beloningen blijven werken).");
        } else {
            getLogger().info("[EZG-Bank] Vault gekoppeld.");
        }

        // WorldGuard runtime detectie
        boolean wg = RegionUtils.setupWorldGuard();
        if (wg) getLogger().info("[EZG-Bank] WorldGuard gedetecteerd (runtime).");
        else getLogger().info("[EZG-Bank] WorldGuard niet gedetecteerd - coördinaten fallback actief.");

        // Register commands
        getCommand("bankoverval").setExecutor(new HeistCommand());
        getCommand("bankadmin").setExecutor(new BankAdminCommand());

        // UpdateChecker async
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                UpdateChecker.checkForUpdates(this, GITHUB_REPO);
            } catch (Exception ex) {
                getLogger().warning("[EZG-Bank] UpdateChecker fout: " + ex.getMessage());
            }
        });

        getLogger().info("[EZG-Bank] Ingeschakeld.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[EZG-Bank] Uitgeschakeld.");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public static Main getInstance() {
        return instance;
    }

    public LicenseManager getLicenseManager() {
        return licenseManager;
    }
}
