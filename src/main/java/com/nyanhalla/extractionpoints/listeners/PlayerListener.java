package com.nyanhalla.extractionpoints.listeners;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;

/**
 * Listener de eventos de jugador: activación por movimiento, desconexión,
 * teletransporte y protección de bloques del beacon.
 */
public class PlayerListener implements Listener {

    private final ExtractionPoints plugin;

    public PlayerListener(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    // ──────────────────────────── Activation ────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Skip head-rotation-only events (no block crossing)
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        checkAndTriggerExtraction(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Run 1 tick later so the player's location is already updated
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> checkAndTriggerExtraction(player), 1L);
    }

    private void checkAndTriggerExtraction(Player player) {
        if (!player.isOnline()) return;
        String worldName = player.getWorld().getName();
        WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);
        if (!wcfg.isEnabled()) return;

        String perm = wcfg.getUsePermission();
        if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) return;

        PointManager pointManager = plugin.getPointManager();
        ExtractionPoint point = pointManager.getPointAt(player.getLocation());
        if (point == null) return;
        if (point.getState() != ExtractionPoint.State.IDLE) return;

        pointManager.startExtraction(player, point);
    }

    // ──────────────────────────── Beacon protection ────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        ExtractionPoint protectedPoint = plugin.getPointManager()
                .getPointByBeaconBlock(event.getClickedBlock().getLocation());
        if (protectedPoint != null) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    // ──────────────────────────── Disconnect ────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quitting = event.getPlayer();
        ExtractionPoint point = plugin.getPointManager().getExtractionPointFor(quitting);
        if (point == null) return;

        // If this player was the last one inside, cancel immediately
        List<Player> remaining = plugin.getPointManager().getPlayersInPoint(point);
        remaining.removeIf(p -> p.equals(quitting));
        if (remaining.isEmpty()) {
            plugin.getPointManager().cancelExtraction(point);
        }
    }

    // ──────────────────────────── Block protection ────────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().isOp()) return;
        if (plugin.getPointManager().getPointByBeaconBlock(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().isOp()) return;
        Block placed = event.getBlockPlaced();
        Block against = event.getBlockAgainst();
        if (plugin.getPointManager().getPointByBeaconBlock(placed.getLocation()) != null
                || plugin.getPointManager().getPointByBeaconBlock(against.getLocation()) != null) {
            event.setCancelled(true);
        }
    }
}
