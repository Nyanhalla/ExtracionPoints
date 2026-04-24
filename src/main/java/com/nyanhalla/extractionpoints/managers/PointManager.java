package com.nyanhalla.extractionpoints.managers;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

/**
 * Gestiona los puntos de extracción en múltiples mundos.
 */
public class PointManager {

    private final ExtractionPoints plugin;
    private final List<ExtractionPoint> activePoints = new ArrayList<>();
    private final Map<UUID, ExtractionPoint> extractingPlayers = new HashMap<>();
    private final Map<UUID, Long> extractionStartTimes = new HashMap<>();
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private final Random random = new Random();

    public PointManager(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    /**
     * Genera los puntos iniciales al arrancar el plugin para todos los mundos habilitados.
     */
    public void initializePoints() {
        activePoints.clear();
        extractingPlayers.clear();
        extractionStartTimes.clear();

        for (String worldName : plugin.getConfigManager().getEnabledWorlds()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Mundo '" + worldName + "' no encontrado o no cargado. Se intentará más tarde.");
                continue;
            }
            WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);
            if (!wcfg.isEnabled()) continue;

            int spawned = 0;
            for (int i = 0; i < wcfg.getMaxPoints(); i++) {
                if (spawnRandomPoint(worldName) != null) {
                    spawned++;
                }
            }
            plugin.getLogger().info("Generados " + spawned + "/" + wcfg.getMaxPoints() + " puntos en mundo '" + worldName + "'.");
        }

        plugin.getLogger().info("Total puntos activos: " + activePoints.size());
    }

    /**
     * Genera un punto de extracción en un mundo específico.
     */
    public ExtractionPoint spawnRandomPoint(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Mundo '" + worldName + "' no encontrado.");
            return null;
        }

        WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);

        int attempts = 0;
        while (attempts < 50) {
            attempts++;
            Location loc = generateRandomLocation(world, wcfg);
            if (loc == null) continue;

            // Check min distance between points
            if (wcfg.getMinDistanceBetweenPoints() > 0) {
                boolean tooClose = false;
                for (ExtractionPoint existing : getPointsInWorld(worldName)) {
                    double dist = existing.distance2D(loc);
                    if (dist < wcfg.getMinDistanceBetweenPoints()) {
                        tooClose = true;
                        break;
                    }
                }
                if (tooClose) continue;
            }

            ExtractionPoint point = new ExtractionPoint(loc, wcfg.getPointRadius());
            activePoints.add(point);
            return point;
        }

        plugin.getLogger().warning("No se pudo generar un punto válido en '" + worldName + "' tras 50 intentos.");
        return null;
    }

    /**
     * Genera una ubicación aleatoria basándose en la config del mundo.
     */
    private Location generateRandomLocation(World world, WorldConfig wcfg) {
        Location center;
        double radius;

        if (wcfg.useWorldBorder()) {
            WorldBorder border = world.getWorldBorder();
            center = border.getCenter();
            radius = border.getSize() / 2.0 - wcfg.getBorderMargin();
            if (radius <= 0) {
                plugin.getLogger().warning("World border demasiado pequeña o margin demasiado grande en '" + world.getName() + "'.");
                return null;
            }
        } else {
            center = resolveSpawnCenter(world, wcfg);
            radius = wcfg.getSpawnRadius();
        }

        // Generate random point within radius from center (uniform distribution)
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        int x = (int) (center.getX() + distance * Math.cos(angle));
        int z = (int) (center.getZ() + distance * Math.sin(angle));
        int y = findSafeY(world, x, z, wcfg.getMinY(), wcfg.getMaxY());
        if (y < 0) return null;

        return new Location(world, x + 0.5, y, z + 0.5);
    }

    /**
     * Resuelve el centro de spawn basándose en la config.
     */
    private Location resolveSpawnCenter(World world, WorldConfig wcfg) {
        String spawnCenter = wcfg.getSpawnCenter();
        if (spawnCenter == null || spawnCenter.equalsIgnoreCase("spawn")) {
            return world.getSpawnLocation();
        }
        // Try parsing "x,z" format
        String[] parts = spawnCenter.split(",");
        if (parts.length == 2) {
            try {
                double cx = Double.parseDouble(parts[0].trim());
                double cz = Double.parseDouble(parts[1].trim());
                return new Location(world, cx, world.getHighestBlockYAt((int) cx, (int) cz), cz);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("spawn-center inválido: '" + spawnCenter + "'. Usando spawn del mundo.");
            }
        }
        return world.getSpawnLocation();
    }

    /**
     * Busca una Y segura (bloque sólido debajo, aire arriba).
     */
    private int findSafeY(World world, int x, int z, int minY, int maxY) {
        int highest = world.getHighestBlockYAt(x, z);
        return Math.max(minY, Math.min(maxY, highest));
    }

    /**
     * Inicia la extracción para un jugador.
     */
    public void startExtraction(Player player, ExtractionPoint point) {
        UUID uuid = player.getUniqueId();
        WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(point.getLocation().getWorld().getName());

        // Check cooldown
        if (wcfg.getPlayerCooldown() > 0) {
            Long lastExtraction = playerCooldowns.get(uuid);
            if (lastExtraction != null) {
                long remaining = (lastExtraction + (wcfg.getPlayerCooldown() * 1000L)) - System.currentTimeMillis();
                if (remaining > 0) {
                    return; // Still on cooldown
                }
            }
        }

        // Check permission
        String perm = wcfg.getUsePermission();
        if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
            return;
        }

        extractingPlayers.put(uuid, point);
        extractionStartTimes.put(uuid, System.currentTimeMillis());

        String msg = wcfg.getMessage("extraction-start")
                .replace("{time}", String.valueOf(wcfg.getExtractionTime()));
        player.sendMessage(net.kyori.adventure.text.Component.text(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', msg)));

        // Play start sound
        playSound(player, wcfg.getStartSound(), wcfg.getStartSoundVolume(), wcfg.getStartSoundPitch());
    }

    /**
     * Cancela la extracción de un jugador.
     */
    public void cancelExtraction(Player player) {
        UUID uuid = player.getUniqueId();
        if (extractingPlayers.containsKey(uuid)) {
            extractingPlayers.remove(uuid);
            extractionStartTimes.remove(uuid);

            WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(player.getWorld().getName());
            String msg = wcfg.getMessage("extraction-cancelled");
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', msg)));

            // Play cancel sound
            playSound(player, wcfg.getCancelSound(), wcfg.getCancelSoundVolume(), wcfg.getCancelSoundPitch());
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

        String worldName = point.getLocation().getWorld().getName();
        WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);

        // Set cooldown
        if (wcfg.getPlayerCooldown() > 0) {
            playerCooldowns.put(uuid, System.currentTimeMillis());
        }

        // Execute commands
        executeExtractionCommands(player, point, wcfg);

        // Play extraction sound
        playSound(player, wcfg.getExtractionSound(), wcfg.getExtractionSoundVolume(), wcfg.getExtractionSoundPitch());

        // Send success message
        String msg = wcfg.getMessage("extraction-success");
        player.sendMessage(net.kyori.adventure.text.Component.text(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', msg)));

        // Remove point and spawn a new one in the same world
        removePoint(point);
        spawnRandomPoint(worldName);
    }

    /**
     * Ejecuta los comandos configurados de extracción.
     */
    private void executeExtractionCommands(Player player, ExtractionPoint point, WorldConfig wcfg) {
        Location pLoc = player.getLocation();
        Location ptLoc = point.getLocation();

        for (WorldConfig.CommandEntry entry : wcfg.getCommands()) {
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

    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Sonido inválido: " + soundName);
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
     * Remueve el punto más cercano a una location en un mundo específico.
     */
    public ExtractionPoint removeNearestPoint(Location loc) {
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
            String worldName = nearest.getLocation().getWorld().getName();
            removePoint(nearest);
            // Spawn replacement in same world
            spawnRandomPoint(worldName);
        }
        return nearest;
    }

    /**
     * Remueve el punto más cercano en un mundo específico (por nombre).
     */
    public ExtractionPoint removeNearestPoint(String worldName) {
        List<ExtractionPoint> worldPoints = getPointsInWorld(worldName);
        if (worldPoints.isEmpty()) return null;

        ExtractionPoint point = worldPoints.get(0);
        removePoint(point);
        spawnRandomPoint(worldName);
        return point;
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
     * Obtiene los puntos activos en un mundo específico.
     */
    public List<ExtractionPoint> getPointsInWorld(String worldName) {
        List<ExtractionPoint> result = new ArrayList<>();
        for (ExtractionPoint point : activePoints) {
            if (point.isActive() && point.getLocation().getWorld().getName().equals(worldName)) {
                result.add(point);
            }
        }
        return result;
    }

    public boolean isExtracting(UUID uuid) {
        return extractingPlayers.containsKey(uuid);
    }

    public ExtractionPoint getExtractionPoint(UUID uuid) {
        return extractingPlayers.get(uuid);
    }

    public Long getExtractionStartTime(UUID uuid) {
        return extractionStartTimes.get(uuid);
    }

    public void clearPlayer(UUID uuid) {
        extractingPlayers.remove(uuid);
        extractionStartTimes.remove(uuid);
    }

    public List<ExtractionPoint> getActivePoints() {
        return Collections.unmodifiableList(activePoints);
    }

    public Map<UUID, ExtractionPoint> getExtractingPlayersCopy() {
        return new HashMap<>(extractingPlayers);
    }

    /**
     * Asegura que existan max-points activos en todos los mundos habilitados.
     */
    public void ensurePoints() {
        for (String worldName : plugin.getConfigManager().getEnabledWorlds()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);
            if (!wcfg.isEnabled()) continue;

            List<ExtractionPoint> worldPoints = getPointsInWorld(worldName);
            while (worldPoints.size() < wcfg.getMaxPoints()) {
                ExtractionPoint newPoint = spawnRandomPoint(worldName);
                if (newPoint == null) break;
                worldPoints.add(newPoint);
            }
        }
    }

    /**
     * Limpia todos los puntos y datos de jugadores.
     */
    public void shutdown() {
        activePoints.clear();
        extractingPlayers.clear();
        extractionStartTimes.clear();
    }
}
