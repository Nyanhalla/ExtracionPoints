package com.nyanhalla.extractionpoints.managers;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.ConfigManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

/**
 * Gestiona los puntos de extracción: spawn, remoción, tracking de jugadores.
 */
public class PointManager {

    private final ExtractionPoints plugin;
    private final List<ExtractionPoint> activePoints = new ArrayList<>();
    private final Map<UUID, ExtractionPoint> extractingPlayers = new HashMap<>();
    private final Map<UUID, Long> extractionStartTimes = new HashMap<>();

    public PointManager(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    /**
     * Genera los puntos iniciales al arrancar el plugin.
     */
    public void initializePoints() {
        activePoints.clear();
        ConfigManager cfg = plugin.getConfigManager();
        World world = Bukkit.getWorld(cfg.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Mundo '" + cfg.getWorldName() + "' no encontrado. No se generan puntos.");
            return;
        }

        for (int i = 0; i < cfg.getMaxPoints(); i++) {
            spawnRandomPoint();
        }

        plugin.getLogger().info("Generados " + activePoints.size() + " puntos de extracción.");
    }

    /**
     * Genera un punto de extracción en posición aleatoria.
     */
    public ExtractionPoint spawnRandomPoint() {
        ConfigManager cfg = plugin.getConfigManager();
        World world = Bukkit.getWorld(cfg.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Mundo '" + cfg.getWorldName() + "' no encontrado.");
            return null;
        }

        Location spawnLoc = world.getSpawnLocation();
        Random random = new Random();

        int attempts = 0;
        while (attempts < 50) {
            attempts++;
            // Random X, Z within spawn radius
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * cfg.getSpawnRadius();
            int x = (int) (spawnLoc.getX() + distance * Math.cos(angle));
            int z = (int) (spawnLoc.getZ() + distance * Math.sin(angle));

            // Find safe Y
            int y = findSafeY(world, x, z, cfg.getMinY(), cfg.getMaxY());
            if (y < 0) continue;

            Location pointLoc = new Location(world, x + 0.5, y, z + 0.5);
            ExtractionPoint point = new ExtractionPoint(pointLoc, cfg.getPointRadius());
            activePoints.add(point);
            return point;
        }

        plugin.getLogger().warning("No se pudo generar un punto seguro tras 50 intentos.");
        return null;
    }

    /**
     * Busca una Y segura (bloque sólido debajo, aire arriba).
     */
    private int findSafeY(World world, int x, int z, int minY, int maxY) {
        int highest = world.getHighestBlockYAt(x, z);
        // Use highest block but clamp to min/max
        int y = Math.max(minY, Math.min(maxY, highest));
        return y;
    }

    /**
     * Inicia la extracción para un jugador.
     */
    public void startExtraction(Player player, ExtractionPoint point) {
        UUID uuid = player.getUniqueId();
        extractingPlayers.put(uuid, point);
        extractionStartTimes.put(uuid, System.currentTimeMillis());

        String msg = plugin.getConfigManager().getMessage("extraction-start")
                .replace("{time}", String.valueOf(plugin.getConfigManager().getExtractionTime()));
        player.sendMessage(net.kyori.adventure.text.Component.text(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', msg)));
    }

    /**
     * Cancela la extracción de un jugador.
     */
    public void cancelExtraction(Player player) {
        UUID uuid = player.getUniqueId();
        if (extractingPlayers.containsKey(uuid)) {
            extractingPlayers.remove(uuid);
            extractionStartTimes.remove(uuid);

            String msg = plugin.getConfigManager().getMessage("extraction-cancelled");
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', msg)));
        }
    }

    /**
     * Completa la extracción: ejecuta comandos, elimina punto, genera nuevo.
     */
    public void completeExtraction(Player player) {
        UUID uuid = player.getUniqueId();
        ExtractionPoint point = extractingPlayers.remove(uuid);
        extractionStartTimes.remove(uuid);

        if (point == null) return;

        // Execute commands
        executeExtractionCommands(player, point);

        // Play sound
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(
                    plugin.getConfigManager().getExtractionSound());
            player.playSound(player.getLocation(), sound,
                    plugin.getConfigManager().getExtractionSoundVolume(),
                    plugin.getConfigManager().getExtractionSoundPitch());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Sonido inválido: " + plugin.getConfigManager().getExtractionSound());
        }

        // Send success message
        String msg = plugin.getConfigManager().getMessage("extraction-success");
        player.sendMessage(net.kyori.adventure.text.Component.text(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', msg)));

        // Remove point and spawn a new one
        removePoint(point);
        spawnRandomPoint();
    }

    /**
     * Ejecuta los comandos configurados de extracción.
     */
    private void executeExtractionCommands(Player player, ExtractionPoint point) {
        ConfigManager cfg = plugin.getConfigManager();
        Location pLoc = player.getLocation();
        Location ptLoc = point.getLocation();

        for (ConfigManager.CommandEntry entry : cfg.getCommands()) {
            String cmd = entry.getCommand()
                    .replace("{player}", player.getName())
                    .replace("{x}", String.valueOf(pLoc.getBlockX()))
                    .replace("{y}", String.valueOf(pLoc.getBlockY()))
                    .replace("{z}", String.valueOf(pLoc.getBlockZ()))
                    .replace("{world}", pLoc.getWorld().getName())
                    .replace("{point-x}", String.valueOf(ptLoc.getBlockX()))
                    .replace("{point-y}", String.valueOf(ptLoc.getBlockY()))
                    .replace("{point-z}", String.valueOf(ptLoc.getBlockZ()));

            if (entry.getDelayTicks() > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        dispatchCommand(cmd, entry.isAsConsole(), player), entry.getDelayTicks());
            } else {
                dispatchCommand(cmd, entry.isAsConsole(), player);
            }
        }
    }

    private void dispatchCommand(String cmd, boolean asConsole, Player player) {
        if (asConsole) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else {
            player.performCommand(cmd);
        }
    }

    /**
     * Remueve un punto de extracción.
     */
    public void removePoint(ExtractionPoint point) {
        point.setActive(false);
        activePoints.remove(point);
        // Cancel any players extracting at this point
        Iterator<Map.Entry<UUID, ExtractionPoint>> it = extractingPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ExtractionPoint> entry = it.next();
            if (entry.getValue().equals(point)) {
                extractionStartTimes.remove(entry.getKey());
                it.remove();
            }
        }
    }

    /**
     * Remueve el punto más cercano a una location.
     */
    public ExtractionPoint removeNearestPoint(Location loc) {
        if (activePoints.isEmpty()) return null;

        ExtractionPoint nearest = null;
        double minDist = Double.MAX_VALUE;

        for (ExtractionPoint point : activePoints) {
            if (!point.getLocation().getWorld().equals(loc.getWorld())) continue;
            double dist = point.getLocation().distanceSquared(loc);
            if (dist < minDist) {
                minDist = dist;
                nearest = point;
            }
        }

        if (nearest != null) {
            removePoint(nearest);
        }
        return nearest;
    }

    /**
     * Busca un punto de extracción que contenga la location dada.
     */
    public ExtractionPoint getPointAt(Location loc) {
        for (ExtractionPoint point : activePoints) {
            if (point.isActive() && point.isInRange(loc)) {
                return point;
            }
        }
        return null;
    }

    /**
     * Verifica si un jugador está en proceso de extracción.
     */
    public boolean isExtracting(UUID uuid) {
        return extractingPlayers.containsKey(uuid);
    }

    /**
     * Obtiene el punto donde un jugador se está extrayendo.
     */
    public ExtractionPoint getExtractionPoint(UUID uuid) {
        return extractingPlayers.get(uuid);
    }

    /**
     * Obtiene el timestamp de inicio de extracción.
     */
    public Long getExtractionStartTime(UUID uuid) {
        return extractionStartTimes.get(uuid);
    }

    /**
     * Limpia toda la información de un jugador (al desconectarse, etc).
     */
    public void clearPlayer(UUID uuid) {
        extractingPlayers.remove(uuid);
        extractionStartTimes.remove(uuid);
    }

    public List<ExtractionPoint> getActivePoints() {
        return Collections.unmodifiableList(activePoints);
    }

    /**
     * Retorna una copia del mapa de jugadores en extracción (para iterar seguro).
     */
    public Map<UUID, ExtractionPoint> getExtractingPlayersCopy() {
        return new HashMap<>(extractingPlayers);
    }

    /**
     * Limpia todos los puntos y datos de jugadores.
     */
    public void shutdown() {
        activePoints.clear();
        extractingPlayers.clear();
        extractionStartTimes.clear();
    }

    /**
     * Asegura que existan max-points activos. Llamado periódicamente.
     */
    public void ensurePoints() {
        ConfigManager cfg = plugin.getConfigManager();
        while (activePoints.size() < cfg.getMaxPoints()) {
            spawnRandomPoint();
        }
    }
}
