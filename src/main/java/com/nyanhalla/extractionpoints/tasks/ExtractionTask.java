package com.nyanhalla.extractionpoints.tasks;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tarea periódica que verifica el progreso de extracción de los jugadores.
 * Se ejecuta cada segundo (20 ticks).
 * Soporta display modes: actionbar, bossbar, title, none.
 * También actualiza el TextDisplay entity del punto con el tiempo restante.
 */
public class ExtractionTask extends BukkitRunnable {

    private final ExtractionPoints plugin;
    private final Map<UUID, org.bukkit.boss.BossBar> activeBossBars = new ConcurrentHashMap<>();

    public ExtractionTask(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        PointManager pointManager = plugin.getPointManager();
        long now = System.currentTimeMillis();

        // Iterate over a copy to avoid ConcurrentModificationException
        for (Map.Entry<UUID, ExtractionPoint> entry : pointManager.getExtractingPlayersCopy().entrySet()) {
            UUID uuid = entry.getKey();
            ExtractionPoint point = entry.getValue();

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                removeBossBar(uuid);
                pointManager.clearPlayer(uuid);
                continue;
            }

            // Verify player is still in the point area
            if (!point.isInRange(player.getLocation())) {
                removeBossBar(uuid);
                pointManager.cancelExtraction(player);
                continue;
            }

            // Calculate elapsed time
            Long startTime = pointManager.getExtractionStartTime(uuid);
            if (startTime == null) {
                removeBossBar(uuid);
                pointManager.cancelExtraction(player);
                continue;
            }

            String worldName = point.getLocation().getWorld().getName();
            WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);
            int extractionTime = wcfg.getExtractionTime();

            long elapsedMillis = now - startTime;
            int elapsedSeconds = (int) (elapsedMillis / 1000);

            if (elapsedSeconds >= extractionTime) {
                // Extraction complete!
                removeBossBar(uuid);
                pointManager.completeExtraction(player);
            } else {
                // Show progress
                int remaining = extractionTime - elapsedSeconds;
                String displayMode = wcfg.getDisplayMode();
                showProgress(player, uuid, wcfg, remaining, extractionTime, elapsedSeconds);

                // Update TextDisplay on the extraction point
                point.updateTextDisplay(remaining, extractionTime, wcfg);
            }
        }
    }

    private void showProgress(Player player, UUID uuid, WorldConfig wcfg, int remaining, int totalTime, int elapsed) {
        String mode = wcfg.getDisplayMode() != null ? wcfg.getDisplayMode().toLowerCase() : "actionbar";

        String msg = wcfg.getMessage("extraction-progress")
                .replace("{remaining}", String.valueOf(remaining))
                .replace("{time}", String.valueOf(remaining))
                .replace("{max}", String.valueOf(totalTime));

        switch (mode) {
            case "bossbar" -> showBossBar(player, uuid, wcfg, remaining, totalTime);
            case "title" -> {
                String colored = ChatColor.translateAlternateColorCodes('&', msg);
                player.sendTitle(colored, "", 0, 25, 0);
            }
            case "actionbar" -> {
                String colored = ChatColor.translateAlternateColorCodes('&', msg);
                player.sendActionBar(Component.text(colored));
            }
            case "none" -> { /* No display */ }
            default -> {
                String colored = ChatColor.translateAlternateColorCodes('&', msg);
                player.sendActionBar(Component.text(colored));
            }
        }
    }

    private void showBossBar(Player player, UUID uuid, WorldConfig wcfg, int remaining, int totalTime) {
        org.bukkit.boss.BossBar bossBar = activeBossBars.get(uuid);

        String title = wcfg.getBossbarTitle()
                .replace("{remaining}", String.valueOf(remaining))
                .replace("{time}", String.valueOf(remaining))
                .replace("{max}", String.valueOf(totalTime));
        String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);

        if (bossBar == null) {
            BarColor color;
            try {
                color = BarColor.valueOf(wcfg.getBossbarColor());
            } catch (IllegalArgumentException e) {
                color = BarColor.GREEN;
            }
            BarStyle style;
            try {
                style = BarStyle.valueOf(wcfg.getBossbarStyle());
            } catch (IllegalArgumentException e) {
                style = BarStyle.SEGMENTED_10;
            }
            bossBar = Bukkit.createBossBar(coloredTitle, color, style);
            bossBar.addPlayer(player);
            activeBossBars.put(uuid, bossBar);
        } else {
            bossBar.setTitle(coloredTitle);
        }

        // Update progress
        float progress = totalTime > 0 ? (float) (totalTime - remaining) / totalTime : 1.0f;
        bossBar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
    }

    private void removeBossBar(UUID uuid) {
        org.bukkit.boss.BossBar bossBar = activeBossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * Clean up all boss bars on plugin disable.
     */
    public void cleanup() {
        for (org.bukkit.boss.BossBar bossBar : activeBossBars.values()) {
            bossBar.removeAll();
        }
        activeBossBars.clear();
    }
}
