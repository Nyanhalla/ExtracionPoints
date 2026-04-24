package com.nyanhalla.extractionpoints.commands;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.ConfigManager;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Comando admin /extractionpoints (alias /ep).
 * Subcomandos: reload, list, spawn, remove, worlds
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
            case "list" -> handleList(sender, args);
            case "spawn" -> handleSpawn(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "worlds" -> handleWorlds(sender);
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
        plugin.getPointManager().shutdown();
        plugin.getPointManager().initializePoints();

        String msg = plugin.getConfigManager().getAdminMessage("reload");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private void handleList(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();

        if (args.length >= 2) {
            // List points in specific world
            String worldName = args[1];
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                String msg = cfg.getAdminMessage("world-not-found").replace("{world}", worldName);
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                return;
            }
            listPointsInWorld(sender, worldName, cfg);
        } else if (sender instanceof Player player) {
            // List points in player's current world
            listPointsInWorld(sender, player.getWorld().getName(), cfg);
        } else {
            // Console: list all
            listAllPoints(sender, cfg);
        }
    }

    private void listPointsInWorld(CommandSender sender, String worldName, ConfigManager cfg) {
        List<ExtractionPoint> points = plugin.getPointManager().getPointsInWorld(worldName);
        String header = cfg.getAdminMessage("points-list")
                .replace("{world}", worldName)
                .replace("{count}", String.valueOf(points.size()));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', header));

        for (int i = 0; i < points.size(); i++) {
            ExtractionPoint point = points.get(i);
            Location loc = point.getLocation();
            String entry = cfg.getAdminMessage("points-list-entry")
                    .replace("{index}", String.valueOf(i + 1))
                    .replace("{x}", String.valueOf(loc.getBlockX()))
                    .replace("{y}", String.valueOf(loc.getBlockY()))
                    .replace("{z}", String.valueOf(loc.getBlockZ()));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', entry));
        }

        if (points.isEmpty()) {
            String noPoints = cfg.getAdminMessage("no-points");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPoints));
        }
    }

    private void listAllPoints(CommandSender sender, ConfigManager cfg) {
        List<ExtractionPoint> allPoints = plugin.getPointManager().getActivePoints();
        for (String worldName : plugin.getConfigManager().getEnabledWorlds()) {
            listPointsInWorld(sender, worldName, cfg);
        }
        if (allPoints.isEmpty()) {
            String noPoints = cfg.getAdminMessage("no-points");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPoints));
        }
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        String worldName;

        if (args.length >= 2) {
            worldName = args[1];
        } else if (sender instanceof Player player) {
            worldName = player.getWorld().getName();
        } else {
            String msg = cfg.getAdminMessage("spawn-usage");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            String msg = cfg.getAdminMessage("world-not-found").replace("{world}", worldName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }

        ExtractionPoint point = plugin.getPointManager().spawnRandomPoint(worldName);
        if (point != null) {
            Location loc = point.getLocation();
            String msg = cfg.getAdminMessage("point-spawned")
                    .replace("{x}", String.valueOf(loc.getBlockX()))
                    .replace("{y}", String.valueOf(loc.getBlockY()))
                    .replace("{z}", String.valueOf(loc.getBlockZ()))
                    .replace("{world}", worldName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        } else {
            sender.sendMessage(ChatColor.RED + "No se pudo generar el punto en '" + worldName + "'. ¿Está cargado el mundo?");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();

        if (args.length >= 2) {
            // Remove in specific world
            String worldName = args[1];
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                String msg = cfg.getAdminMessage("world-not-found").replace("{world}", worldName);
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                return;
            }
            ExtractionPoint removed = plugin.getPointManager().removeNearestPoint(worldName);
            if (removed != null) {
                String msg = cfg.getAdminMessage("point-removed");
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            } else {
                sender.sendMessage(ChatColor.RED + "No hay puntos de extracción en '" + worldName + "'.");
            }
        } else if (sender instanceof Player player) {
            // Remove nearest point to player
            PointManager pm = plugin.getPointManager();
            ExtractionPoint removed = pm.removeNearestPoint(player.getLocation());
            if (removed != null) {
                String msg = cfg.getAdminMessage("point-removed");
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            } else {
                sender.sendMessage(ChatColor.RED + "No hay puntos de extracción cerca.");
            }
        } else {
            String msg = cfg.getAdminMessage("remove-usage");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    private void handleWorlds(CommandSender sender) {
        ConfigManager cfg = plugin.getConfigManager();
        java.util.Set<String> enabledWorlds = cfg.getEnabledWorlds();
        String worldList = String.join(", ", enabledWorlds);
        String msg = cfg.getAdminMessage("worlds-list").replace("{worlds}", worldList);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }
}
