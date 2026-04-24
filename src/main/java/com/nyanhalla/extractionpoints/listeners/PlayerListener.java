package com.nyanhalla.extractionpoints.listeners;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.ConfigManager;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Listener de eventos de jugador: interacción, movimiento, desconexión.
 */
public class PlayerListener implements Listener {

    private final ExtractionPoints plugin;

    public PlayerListener(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ConfigManager cfg = plugin.getConfigManager();

        // Solo funciona en el mundo configurado
        if (!player.getWorld().getName().equals(cfg.getWorldName())) {
            return;
        }

        PointManager pointManager = plugin.getPointManager();
        UUID uuid = player.getUniqueId();

        // Si ya está en proceso de extracción, no hacer nada (lo maneja ExtractionTask)
        if (pointManager.isExtracting(uuid)) {
            return;
        }

        // Verificar si está en un punto de extracción
        ExtractionPoint point = pointManager.getPointAt(player.getLocation());
        if (point == null) {
            return;
        }

        // Iniciar extracción
        pointManager.startExtraction(player, point);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Solo checkear si cambió posición significativamente (no solo cabeza)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        PointManager pointManager = plugin.getPointManager();
        UUID uuid = player.getUniqueId();

        if (!pointManager.isExtracting(uuid)) {
            return;
        }

        // Verificar si sigue en el punto
        ExtractionPoint point = pointManager.getExtractionPoint(uuid);
        if (point == null) return;

        if (!point.isInRange(event.getTo())) {
            pointManager.cancelExtraction(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPointManager().clearPlayer(event.getPlayer().getUniqueId());
    }
}
