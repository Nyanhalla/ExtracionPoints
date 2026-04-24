package com.nyanhalla.extractionpoints.tasks;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.ConfigManager;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

/**
 * Tarea periódica que verifica el progreso de extracción de los jugadores.
 * Se ejecuta cada segundo (20 ticks).
 */
public class ExtractionTask extends BukkitRunnable {

    private final ExtractionPoints plugin;

    public ExtractionTask(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        PointManager pointManager = plugin.getPointManager();
        ConfigManager cfg = plugin.getConfigManager();
        int extractionTime = cfg.getExtractionTime();
        long now = System.currentTimeMillis();

        // Iterate over a copy to avoid ConcurrentModificationException
        for (Map.Entry<UUID, ExtractionPoint> entry : pointManager.getExtractingPlayersCopy().entrySet()) {
            UUID uuid = entry.getKey();
            ExtractionPoint point = entry.getValue();

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                pointManager.clearPlayer(uuid);
                continue;
            }

            // Verify player is still in the point area
            if (!point.isInRange(player.getLocation())) {
                pointManager.cancelExtraction(player);
                continue;
            }

            // Calculate elapsed time
            Long startTime = pointManager.getExtractionStartTime(uuid);
            if (startTime == null) {
                pointManager.cancelExtraction(player);
                continue;
            }

            long elapsedMillis = now - startTime;
            int elapsedSeconds = (int) (elapsedMillis / 1000);

            if (elapsedSeconds >= extractionTime) {
                // Extraction complete!
                pointManager.completeExtraction(player);
            } else {
                // Show progress via action bar (non-spammy)
                int remaining = extractionTime - elapsedSeconds;
                String msg = cfg.getMessage("extraction-progress")
                        .replace("{remaining}", String.valueOf(remaining))
                        .replace("{time}", String.valueOf(remaining));
                String colored = ChatColor.translateAlternateColorCodes('&', msg);

                player.sendActionBar(net.kyori.adventure.text.Component.text(colored));
            }
        }
    }
}
