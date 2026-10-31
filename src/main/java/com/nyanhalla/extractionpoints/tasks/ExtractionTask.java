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
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tarea periódica que avanza la extracción de cada punto en estado EXTRACTING.
 *
 * El timer es ahora compartido por punto: todos los jugadores que estén dentro
 * del área cuando el contador llegue a 0 reciben la extracción.
 * Si en algún momento no queda nadie dentro, la extracción se cancela.
 */
public class ExtractionTask extends BukkitRunnable {

    private final ExtractionPoints plugin;
    /** Bossbar por punto, compartido entre los jugadores presentes. */
    private final Map<String, BossBar> pointBossBars = new HashMap<>();
    /** Última actualización del holograma del punto, para limitar updates. */
    private final Map<String, Integer> lastHologramSecond = new HashMap<>();

    public ExtractionTask(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        PointManager pointManager = plugin.getPointManager();

        // Limpia bossbars de puntos que ya no están EXTRACTING
        Set<String> currentlyExtracting = new HashSet<>();

        for (ExtractionPoint point : pointManager.getExtractingPoints()) {
            currentlyExtracting.add(point.getId());

            String worldName = point.getLocation().getWorld().getName();
            WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);
            int extractionTime = wcfg.getExtractionTime();
            int elapsedSeconds = point.getExtractionElapsedSeconds();

            List<Player> playersInPoint = pointManager.getPlayersInPoint(point);

            // Sin nadie dentro: cancela
            if (playersInPoint.isEmpty()) {
                disposeBossBar(point.getId());
                pointManager.cancelExtraction(point);
                continue;
            }

            // Completado
            if (elapsedSeconds >= extractionTime) {
                disposeBossBar(point.getId());
                pointManager.completeExtraction(point);
                continue;
            }

            int remaining = extractionTime - elapsedSeconds;

            // Holograma: actualiza solo cuando cambia el segundo redondeado
            Integer lastSec = lastHologramSecond.get(point.getId());
            if (lastSec == null || lastSec != remaining) {
                lastHologramSecond.put(point.getId(), remaining);
                point.updateHologram(plugin.getHologramService(), remaining, extractionTime, wcfg);
            }

            // Display: muestra a todos los presentes
            showProgressForPlayers(point, playersInPoint, wcfg, remaining, extractionTime);

            // Glow effect: makes players in the area visible to each other
            for (Player p : playersInPoint) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, false));
            }

            // Heartbeat sound + countdown titles in final seconds
            playExtractionHeartbeat(playersInPoint, remaining);
            showCountdownTitle(playersInPoint, wcfg, remaining);
        }

        // Cualquier bossbar que ya no corresponde a un punto activo se elimina
        if (!pointBossBars.isEmpty()) {
            pointBossBars.keySet().removeIf(id -> {
                if (!currentlyExtracting.contains(id)) {
                    BossBar bar = pointBossBars.get(id);
                    if (bar != null) bar.removeAll();
                    return true;
                }
                return false;
            });
            // Mismo para lastHologramSecond
            lastHologramSecond.keySet().removeIf(id -> !currentlyExtracting.contains(id));
        }
    }

    private void showProgressForPlayers(ExtractionPoint point, List<Player> players,
                                         WorldConfig wcfg, int remaining, int totalTime) {
        String mode = wcfg.getDisplayMode() != null ? wcfg.getDisplayMode().toLowerCase() : "actionbar";

        switch (mode) {
            case "bossbar" -> updateBossBar(point, players, wcfg, remaining, totalTime);
            case "title" -> {
                String msg = formatProgressMessage(wcfg, remaining, totalTime);
                String colored = ChatColor.translateAlternateColorCodes('&', msg);
                for (Player p : players) p.sendTitle(colored, "", 0, 25, 0);
            }
            case "actionbar" -> {
                String msg = formatProgressMessage(wcfg, remaining, totalTime);
                String colored = ChatColor.translateAlternateColorCodes('&', msg);
                for (Player p : players) p.sendActionBar(Component.text(colored));
            }
            case "none" -> { /* no display */ }
            default -> {
                String msg = formatProgressMessage(wcfg, remaining, totalTime);
                String colored = ChatColor.translateAlternateColorCodes('&', msg);
                for (Player p : players) p.sendActionBar(Component.text(colored));
            }
        }
    }

    private String formatProgressMessage(WorldConfig wcfg, int remaining, int totalTime) {
        return wcfg.getMessage("extraction-progress")
                .replace("{remaining}", String.valueOf(remaining))
                .replace("{time}", String.valueOf(totalTime))
                .replace("{max}", String.valueOf(totalTime));
    }

    private void updateBossBar(ExtractionPoint point, List<Player> players, WorldConfig wcfg,
                                int remaining, int totalTime) {
        BossBar bar = pointBossBars.get(point.getId());
        String title = wcfg.getBossbarTitle()
                .replace("{remaining}", String.valueOf(remaining))
                .replace("{time}", String.valueOf(remaining))
                .replace("{max}", String.valueOf(totalTime));
        String coloredTitle = ChatColor.translateAlternateColorCodes('&', title);

        if (bar == null) {
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
            bar = Bukkit.createBossBar(coloredTitle, color, style);
            pointBossBars.put(point.getId(), bar);
        } else {
            bar.setTitle(coloredTitle);
        }

        // Sync de jugadores: añade los presentes, quita los que ya no están
        Set<UUID> currentSubscribers = new HashSet<>();
        for (Player viewer : bar.getPlayers()) currentSubscribers.add(viewer.getUniqueId());
        Set<UUID> shouldSubscribe = new HashSet<>();
        for (Player p : players) shouldSubscribe.add(p.getUniqueId());

        for (Player p : players) {
            if (!currentSubscribers.contains(p.getUniqueId())) bar.addPlayer(p);
        }
        for (UUID uuid : currentSubscribers) {
            if (!shouldSubscribe.contains(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) bar.removePlayer(p);
            }
        }

        float progress = totalTime > 0 ? (float) (totalTime - remaining) / totalTime : 1.0f;
        bar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
    }

    private void playExtractionHeartbeat(List<Player> players, int remaining) {
        if (remaining > 10 || players.isEmpty()) return;
        boolean pulse = remaining <= 3
                || (remaining <= 6 && remaining % 2 == 0)
                || (remaining <= 10 && remaining % 3 == 0);
        if (!pulse) return;
        float pitch = 0.6f + (float)(10 - Math.max(0, remaining)) / 10f * 1.0f;
        for (Player p : players) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, pitch);
        }
    }

    private void showCountdownTitle(List<Player> players, WorldConfig wcfg, int remaining) {
        if (remaining > 5 || remaining <= 0 || players.isEmpty()) return;
        // Don't override if the display mode is already "title"
        String mode = wcfg.getDisplayMode() != null ? wcfg.getDisplayMode().toLowerCase() : "actionbar";
        if ("title".equals(mode)) return;

        String color = remaining <= 2 ? "&c&l" : (remaining <= 3 ? "&6&l" : "&e&l");
        String title = ChatColor.translateAlternateColorCodes('&', color + remaining);
        for (Player p : players) {
            p.sendTitle(title, "", 0, 22, 0);
        }
    }

    private void disposeBossBar(String pointId) {
        BossBar bar = pointBossBars.remove(pointId);
        if (bar != null) bar.removeAll();
        lastHologramSecond.remove(pointId);
    }

    /**
     * Limpieza al deshabilitar el plugin.
     */
    public void cleanup() {
        for (BossBar bar : pointBossBars.values()) bar.removeAll();
        pointBossBars.clear();
        lastHologramSecond.clear();
    }
}
