package com.nyanhalla.extractionpoints.listeners;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
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
        String worldName = player.getWorld().getName();
        WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);

        // Check if extraction is enabled in this world
        if (!wcfg.isEnabled()) {
            return;
        }

        // Check permission
        String perm = wcfg.getUsePermission();
        if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
            return;
        }

        PointManager pointManager = plugin.getPointManager();
        UUID uuid = player.getUniqueId();

        // If already extracting, let ExtractionTask handle it
        if (pointManager.isExtracting(uuid)) {
            return;
        }

        // Check cooldown
        if (wcfg.getPlayerCooldown() > 0) {
            // Cooldown is checked in startExtraction, but we can early-return here too
        }

        // Check if player is in an extraction point
        ExtractionPoint point = pointManager.getPointAt(player.getLocation());
        if (point == null) {
            return;
        }

        // Start extraction
        pointManager.startExtraction(player, point);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if position changed significantly (not just head movement)
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

        // Check if player is still in the extraction point
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
