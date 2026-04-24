package com.nyanhalla.extractionpoints.commands;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.ConfigManager;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Comando admin /extractionpoints (alias /ep).
 */
public class ExtractionCommand implements CommandExecutor {

    private final ExtractionPoints plugin;

    public ExtractionCommand(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        ConfigManager cfg = plugin.getConfigManager();

        if (!sender.hasPermission("extractionpoints.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    cfg.getAdminMessage("no-permission")));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "spawn" -> handleSpawn(sender);
            case "remove" -> handleRemove(sender);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        String msg = plugin.getConfigManager().getAdminMessage("usage");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().load();
        // Reinitialize points with new config
        plugin.getPointManager().shutdown();
        plugin.getPointManager().initializePoints();

        String msg = plugin.getConfigManager().getAdminMessage("reload");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private void handleList(CommandSender sender) {
        var points = plugin.getPointManager().getActivePoints();
        String msg = plugin.getConfigManager().getAdminMessage("points-list")
                .replace("{count}", String.valueOf(points.size()));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));

        for (int i = 0; i < points.size(); i++) {
            ExtractionPoint point = points.get(i);
            Location loc = point.getLocation();
            sender.sendMessage(ChatColor.YELLOW + "  #" + (i + 1) + ": " +
                    ChatColor.WHITE + String.format("%.0f, %.0f, %.0f (%s)",
                    loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName()));
        }
    }

    private void handleSpawn(CommandSender sender) {
        ExtractionPoint point = plugin.getPointManager().spawnRandomPoint();
        if (point != null) {
            Location loc = point.getLocation();
            String msg = plugin.getConfigManager().getAdminMessage("point-spawned")
                    .replace("{x}", String.valueOf(loc.getBlockX()))
                    .replace("{y}", String.valueOf(loc.getBlockY()))
                    .replace("{z}", String.valueOf(loc.getBlockZ()));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        } else {
            sender.sendMessage(ChatColor.RED + "No se pudo generar el punto. ¿Está cargado el mundo?");
        }
    }

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return;
        }

        PointManager pm = plugin.getPointManager();
        ExtractionPoint removed = pm.removeNearestPoint(player.getLocation());
        if (removed != null) {
            String msg = plugin.getConfigManager().getAdminMessage("point-removed");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            // Spawn a replacement
            pm.spawnRandomPoint();
        } else {
            sender.sendMessage(ChatColor.RED + "No hay puntos de extracción en este mundo.");
        }
    }
}
