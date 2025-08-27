package nl.samskeee.ezgbank.Managers;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class LicenseManager {

    private final String apiSecret;
    private final JavaPlugin plugin;

    public LicenseManager(String apiSecret, JavaPlugin plugin) {
        this.apiSecret = apiSecret;
        this.plugin = plugin;
    }

    /**
     * Verifieer licentie-key. Retourneert true als VALID.
     */
    public boolean verifyLicense(String licenseKey) throws Exception {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            plugin.getLogger().warning("[LicenseManager] Geen license-key gevonden in config.");
            return false;
        }

        // 1) Kijk of de gebruiker in config een user-id heeft geplaatst (aanbevolen door LicenseGate docs)
        String configUserId = plugin.getConfig().getString("license-user-id", "a1c71").trim();

        // Als user-id aanwezig -> probeer dat pad
        if (!configUserId.isEmpty()) {
            String urlStr = "https://api.licensegate.io/license/" + encode(configUserId) + "/" + encode(licenseKey) + "/verify";
            plugin.getLogger().info("[LicenseManager] Proberen met license-user-id uit config...");
            Boolean res = doVerifyRequest(urlStr);
            if (res != null) return res; // true = valid, false = invalid
            // res == null => verzoek gaf geen definitieve respons (bv. HTTP error) -> ga verder naar fallback
        }

        // 2) Fallback: probeer het pad met apiSecret in pad (oude methode) **(minder betrouwbaar)**
        if (apiSecret != null && !apiSecret.isEmpty()) {
            String urlStr = "https://api.licensegate.io/license/" + encode(apiSecret) + "/" + encode(licenseKey) + "/verify";
            plugin.getLogger().info("[LicenseManager] Proberen met API_SECRET in pad (fallback)...");
            Boolean res = doVerifyRequest(urlStr);
            if (res != null) return res;
        }

        // 3) Als we hier zijn: we hebben geen sluitend antwoord gekregen.
        plugin.getLogger().severe("[LicenseManager] Licentie-verificatie kon niet worden voltooid. " +
                "Controleer je network/LicenseGate instellingen en/of voeg 'license-user-id' toe aan config.yml.");
        plugin.getLogger().severe("[LicenseManager] Raadpleeg docs: https://docs.licensegate.io/getting-started (endpoint: /license/{user-id}/{license-key}/verify).");
        return false;
    }

    /**
     * Voert een GET uit op de gegeven URL met Authorization header (indien apiSecret aanwezig).
     * Retourneert:
     *  - true  => valid
     *  - false => definitief invalid
     *  - null  => inconclusive (bv. HTTP error code of parse problem) -> laat caller fallback proberen
     */
    private Boolean doVerifyRequest(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);

            // Authorization header met API_SECRET helpt bij sommige setups (we voegen 'Bearer' header toe)
            if (apiSecret != null && !apiSecret.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiSecret);
            }
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            BufferedReader in;
            if (code >= 200 && code < 400) {
                in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                // lees error stream als die er is
                if (conn.getErrorStream() != null) {
                    in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                } else {
                    plugin.getLogger().warning("[LicenseManager] HTTP response code: " + code + " voor " + urlStr);
                    return null;
                }
            }

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            String body = sb.toString();

            plugin.getLogger().info("[LicenseManager] LicenseGate response (HTTP " + code + "): " + (body.length() > 300 ? body.substring(0, 300) + "..." : body));

            String low = body.toLowerCase();
            if (low.contains("\"valid\":true") || low.contains("\"result\":\"valid\"") || low.contains("\"status\":\"valid\"")) {
                return true;
            }
            if (low.contains("\"valid\":false") || low.contains("\"result\":\"not_found\"") || low.contains("\"result\":\"invalid\"") || low.contains("\"status\":\"invalid\"")) {
                // definitief ongeldig of niet gevonden
                // Als het resultaat NOT_FOUND is en we probeerden met API_SECRET-in-pad, dat betekent vaak:
                // - verkeerde user-id in pad (gebruik user-id uit account instellingen)
                // - of license-key bestaat niet
                plugin.getLogger().warning("[LicenseManager] Licentie ongeldig of niet gevonden volgens LicenseGate: " + body);
                return false;
            }

            // Onzeker antwoord: geen expliciete valid flag -> inconclusive
            return null;
        } catch (Exception ex) {
            plugin.getLogger().severe("[LicenseManager] Fout bij LicenseGate request: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String encode(String s) {
        if (s == null) return "";
        return s.replace(" ", "%20");
    }
}
