package nl.samskeee.ezgbank.update;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

public class UpdateChecker {

    public static void checkForUpdates(JavaPlugin plugin, String repo) throws Exception {
        if (repo == null || repo.trim().isEmpty()) {
            plugin.getLogger().info("[EZG-Bank] Geen GitHub repo geconfigureerd voor updatecheck.");
            return;
        }

        String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";
        HttpURLConnection conn = null;
        try {
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            String token = System.getenv("GITHUB_TOKEN");
            if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "token " + token);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

            int code = conn.getResponseCode();
            if (code != 200) {
                plugin.getLogger().info("[EZG-Bank] UpdateChecker: GitHub API response " + code);
                return;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String body = in.lines().collect(Collectors.joining());
            in.close();

            String tag = parseTagName(body);
            if (tag == null || tag.isEmpty()) {
                plugin.getLogger().info("[EZG-Bank] UpdateChecker: tag_name niet gevonden.");
                return;
            }

            String current = plugin.getDescription().getVersion();
            if (!current.equals(tag)) plugin.getLogger().warning("[EZG-Bank] Nieuwe versie beschikbaar: " + tag + " (huidig: " + current + ")");
            else plugin.getLogger().info("[EZG-Bank] Plugin is up-to-date (" + current + ")");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String parseTagName(String json) {
        String search = "\"tag_name\":\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
