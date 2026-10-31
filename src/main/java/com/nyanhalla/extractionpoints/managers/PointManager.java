package com.nyanhalla.extractionpoints.managers;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import com.nyanhalla.extractionpoints.tasks.ParticleTask;
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
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private final Map<UUID, Long> lastApproachNotified = new HashMap<>();
    private final java.util.Set<String> warnedSounds = new java.util.HashSet<>();
    private final Random random = new Random();

    private static final long APPROACH_NOTIFY_COOLDOWN_MS = 8000L;

    public PointManager(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    /**
     * Genera los puntos iniciales al arrancar el plugin para todos los mundos habilitados.
     */
    public void initializePoints() {
        activePoints.clear();

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

            // Place structure + holograma
            plugin.getStructureService().place(point, wcfg);
            point.createHologram(plugin.getHologramService(), wcfg);

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
     * Busca una Y segura para colocar la estructura:
     * - El beacon se coloca UNA posición ENCIMA de la superficie, no reemplazándola.
     * - Rechaza: void (sin bloques sólidos), agua/lava, techo encima.
     * - Requiere al menos 5 bloques de aire sobre el punto de spawn.
     *
     * @return Y válida (beacon aquí, base en Y-1), o -1 si no hay ubicación válida.
     */
    private int findSafeY(World world, int x, int z, int minY, int maxY) {
        int surfaceY = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);

        // Void o por debajo del suelo mínimo del mundo
        if (surfaceY <= world.getMinHeight()) return -1;

        org.bukkit.block.Block surface = world.getBlockAt(x, surfaceY, z);

        // Rechazar superficie no sólida (agua, lava, plantas, etc.)
        if (!surface.getType().isSolid()) return -1;
        if (surface.isLiquid()) return -1;
        try {
            if (surface.getType() == org.bukkit.Material.WATER
                    || surface.getType() == org.bukkit.Material.LAVA) return -1;
        } catch (Exception ignored) {}

        // El beacon va encima de la superficie (Y+1); la base de minerales ocupa la superficie (Y)
        int beaconY = surfaceY + 1;
        beaconY = Math.max(minY, Math.min(maxY, beaconY));

        // Necesitamos al menos 5 bloques de aire por encima del beacon (para partículas y estructura)
        for (int dy = 0; dy < 5; dy++) {
            org.bukkit.block.Block above = world.getBlockAt(x, beaconY + dy, z);
            if (above.getType().isSolid() && !above.getType().isTransparent()) return -1;
        }

        return beaconY;
    }

    /**
     * Inicia la extracción para un jugador.
     */
    public void startExtraction(Player player, ExtractionPoint point) {
        UUID uuid = player.getUniqueId();
        WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(point.getLocation().getWorld().getName());

        // Estado del punto: rechaza si está en cooldown
        ExtractionPoint.State pState = point.getState();
        if (pState == ExtractionPoint.State.COOLDOWN) {
            String msg = wcfg.getMessage("point-on-cooldown");
            if (msg != null && !msg.isEmpty()) {
                String text = msg.replace("{remaining}", String.valueOf(point.getCooldownRemainingSeconds()));
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', text)));
            }
            playSound(player, wcfg.getCancelSound(), wcfg.getCancelSoundVolume(), wcfg.getCancelSoundPitch());
            return;
        }
        // Ya hay extracción en curso: ignorar (el iniciador o cualquiera dentro continuará)
        if (pState == ExtractionPoint.State.EXTRACTING) {
            return;
        }

        // Cooldown del jugador para iniciar
        if (wcfg.getPlayerCooldown() > 0) {
            Long lastExtraction = playerCooldowns.get(uuid);
            if (lastExtraction != null) {
                long remaining = (lastExtraction + (wcfg.getPlayerCooldown() * 1000L)) - System.currentTimeMillis();
                if (remaining > 0) return;
            }
        }

        // Permission
        String perm = wcfg.getUsePermission();
        if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
            return;
        }

        point.beginExtraction(uuid);
        point.updateBeaconGlass(wcfg, ExtractionPoint.State.EXTRACTING);

        String msgKey = wcfg.getMessage("extraction-start")
                .replace("{time}", String.valueOf(wcfg.getExtractionTime()));
        // Mensaje a todos los presentes (no sólo al iniciador)
        for (Player nearby : getPlayersInPoint(point)) {
            nearby.sendMessage(net.kyori.adventure.text.Component.text(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', msgKey)));
            playSound(nearby, wcfg.getStartSound(), wcfg.getStartSoundVolume(), wcfg.getStartSoundPitch());
        }
    }

    /**
     * Cancela la extracción del punto (no hay nadie dentro o un admin la para).
     */
    public void cancelExtraction(ExtractionPoint point) {
        if (point == null) return;
        if (point.getState() != ExtractionPoint.State.EXTRACTING) return;

        String worldName = point.getLocation().getWorld().getName();
        WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);

        // Save initiator BEFORE clearExtraction() nulls it out
        UUID initiator = point.getInitiator();

        point.clearExtraction();
        point.setState(ExtractionPoint.State.IDLE);
        point.updateBeaconGlass(wcfg, ExtractionPoint.State.IDLE);
        point.refreshHologramForState(plugin.getHologramService(), wcfg);

        String msg = wcfg.getMessage("extraction-cancelled");
        if (initiator != null) {
            Player p = Bukkit.getPlayer(initiator);
            if (p != null && p.isOnline()) {
                p.sendMessage(net.kyori.adventure.text.Component.text(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', msg)));
                playSound(p, wcfg.getCancelSound(), wcfg.getCancelSoundVolume(), wcfg.getCancelSoundPitch());
            }
        }
    }

    /**
     * Devuelve la lista de jugadores actualmente dentro del área del punto.
     */
    public List<Player> getPlayersInPoint(ExtractionPoint point) {
        List<Player> result = new ArrayList<>();
        if (point == null || point.getLocation().getWorld() == null) return result;
        for (Player p : point.getLocation().getWorld().getPlayers()) {
            if (point.isInRange(p.getLocation())) result.add(p);
        }
        return result;
    }

    /**
     * Completa la extracción del punto: ejecuta comandos para TODOS los
     * jugadores que estén dentro del área en el momento de la finalización.
     */
    public void completeExtraction(ExtractionPoint point) {
        if (point == null) return;
        if (point.getState() != ExtractionPoint.State.EXTRACTING) return;

        String worldName = point.getLocation().getWorld().getName();
        WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);

        List<Player> players = getPlayersInPoint(point);

        // Limpia el timer del punto antes de transicionar (evita reentrada)
        point.clearExtraction();

        // Spawn completion particle effect (centrado en el punto)
        ParticleTask.spawnCompletionEffect(point.getLocation(), wcfg);

        // Para cada jugador: cooldown personal, comandos, sonido, mensaje, título
        String successMsg = wcfg.getMessage("extraction-success");
        String successTitle = wcfg.getMessage("extraction-success-title");
        String successSubtitle = wcfg.getMessage("extraction-success-subtitle");
        long now = System.currentTimeMillis();
        for (Player player : players) {
            UUID uuid = player.getUniqueId();
            if (wcfg.getPlayerCooldown() > 0) {
                playerCooldowns.put(uuid, now);
            }
            executeExtractionCommands(player, point, wcfg);
            playSound(player, wcfg.getExtractionSound(), wcfg.getExtractionSoundVolume(), wcfg.getExtractionSoundPitch());
            if (successMsg != null && !successMsg.isEmpty()) {
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', successMsg)));
            }
            if (successTitle != null && !successTitle.isEmpty()) {
                player.sendTitle(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', successTitle),
                        successSubtitle != null ? org.bukkit.ChatColor.translateAlternateColorCodes('&', successSubtitle) : "",
                        10, 60, 20);
            }
        }

        // Transición de estado del punto
        if (wcfg.isRespawnOnExtraction()) {
            removePoint(point);
            spawnRandomPoint(worldName);
        } else {
            int cooldown = Math.max(0, wcfg.getPointCooldown());
            if (cooldown > 0) {
                point.enterCooldown(cooldown);
                point.updateBeaconGlass(wcfg, ExtractionPoint.State.COOLDOWN);
            } else {
                point.setState(ExtractionPoint.State.IDLE);
                point.updateBeaconGlass(wcfg, ExtractionPoint.State.IDLE);
            }
            point.refreshHologramForState(plugin.getHologramService(), wcfg);
        }
    }

    /**
     * Avanza el reloj de cooldowns: refresca el holograma de los puntos en
     * COOLDOWN para mostrar el contador y los devuelve a IDLE cuando expiran.
     * Llamado periódicamente desde el scheduler del plugin.
     */
    public void tickCooldowns() {
        for (ExtractionPoint point : activePoints) {
            if (!point.isActive()) continue;
            if (point.getState() != ExtractionPoint.State.COOLDOWN) continue;

            String worldName = point.getLocation().getWorld().getName();
            WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);

            boolean justExpired = point.tickCooldown();
            point.refreshHologramForState(plugin.getHologramService(), wcfg);
            if (justExpired) {
                point.updateBeaconGlass(wcfg, ExtractionPoint.State.IDLE);
                playPointReadySound(point.getLocation(), wcfg);
            }
        }
    }

    /**
     * Notifica a jugadores que se acercan a un punto IDLE con el mensaje
     * "point-approaching". Limitado a una notificación cada 8 segundos por jugador.
     */
    public void tickProximity() {
        long now = System.currentTimeMillis();
        for (ExtractionPoint point : activePoints) {
            if (!point.isActive()) continue;
            if (point.getState() != ExtractionPoint.State.IDLE) continue;

            Location loc = point.getLocation();
            if (loc.getWorld() == null) continue;

            String worldName = loc.getWorld().getName();
            WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);
            if (!wcfg.isEnabled()) continue;

            String msgTemplate = wcfg.getMessage("point-approaching");
            if (msgTemplate == null || msgTemplate.isEmpty()) continue;

            double approachDist = wcfg.getPointRadius() * 4.0;
            double approachDistSq = approachDist * approachDist;
            double pointRadiusSq = wcfg.getPointRadius() * wcfg.getPointRadius();

            for (Player player : loc.getWorld().getPlayers()) {
                double distSq = player.getLocation().distanceSquared(loc);
                // Within approach range but not already inside the extraction area
                if (distSq > approachDistSq || distSq <= pointRadiusSq) continue;

                Long lastNotified = lastApproachNotified.get(player.getUniqueId());
                if (lastNotified != null && (now - lastNotified) < APPROACH_NOTIFY_COOLDOWN_MS) continue;

                lastApproachNotified.put(player.getUniqueId(), now);
                int distance = (int) Math.sqrt(distSq);
                String msg = msgTemplate.replace("{distance}", String.valueOf(distance));
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', msg)));
            }
        }
    }

    private void playPointReadySound(Location loc, WorldConfig wcfg) {
        if (loc.getWorld() == null) return;
        loc.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.4f);
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
                        dispatchCommand(cmd, entry.isAsConsole(), entry.isBypassPermissions(), player), entry.getDelayTicks());
            } else {
                dispatchCommand(cmd, entry.isAsConsole(), entry.isBypassPermissions(), player);
            }
        }
    }

    private void dispatchCommand(String cmd, boolean asConsole, boolean bypassPermissions, Player player) {
        if (asConsole) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return;
        }

        if (bypassPermissions) {
            plugin.getPermissionService().runWithBypass(player, () -> player.performCommand(cmd));
        } else {
            player.performCommand(cmd);
        }
    }

    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            if (warnedSounds.add(soundName)) {
                plugin.getLogger().warning("Sonido inválido (se omitirá en adelante): " + soundName);
            }
        }
    }

    /**
     * Remueve un punto de extracción.
     */
    public void removePoint(ExtractionPoint point) {
        point.setActive(false);
        point.clearExtraction();

        String worldName = point.getLocation().getWorld().getName();
        WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);

        plugin.getStructureService().remove(point, wcfg);
        point.removeHologram(plugin.getHologramService());

        activePoints.remove(point);
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
     * Busca un punto de extracción cuyo beacon o base coincida con el bloque dado.
     * Sirve para proteger bloques del beacon frente a interacciones del jugador.
     */
    public ExtractionPoint getPointByBeaconBlock(Location blockLoc) {
        for (ExtractionPoint point : activePoints) {
            if (!point.isActive() || !point.isBeaconPlaced()) continue;
            Location pl = point.getLocation();
            if (pl.getWorld() == null || !pl.getWorld().equals(blockLoc.getWorld())) continue;

            int bx = blockLoc.getBlockX();
            int by = blockLoc.getBlockY();
            int bz = blockLoc.getBlockZ();
            int px = pl.getBlockX();
            int py = pl.getBlockY();
            int pz = pl.getBlockZ();

            // Beacon block
            if (bx == px && by == py && bz == pz) return point;
            // 3x3 base at y-1
            if (by == py - 1 && Math.abs(bx - px) <= 1 && Math.abs(bz - pz) <= 1) return point;
        }
        return null;
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

    /**
     * ¿Está el jugador dentro de un punto que esté en estado EXTRACTING?
     */
    public boolean isExtracting(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return false;
        return getExtractionPointFor(p) != null;
    }

    /**
     * Devuelve el punto en estado EXTRACTING que contiene al jugador, o null.
     */
    public ExtractionPoint getExtractionPointFor(Player player) {
        if (player == null || player.getWorld() == null) return null;
        for (ExtractionPoint point : activePoints) {
            if (!point.isActive()) continue;
            if (point.getState() != ExtractionPoint.State.EXTRACTING) continue;
            if (!point.getLocation().getWorld().equals(player.getWorld())) continue;
            if (point.isInRange(player.getLocation())) return point;
        }
        return null;
    }

    /**
     * Lista los puntos actualmente en estado EXTRACTING.
     */
    public List<ExtractionPoint> getExtractingPoints() {
        List<ExtractionPoint> result = new ArrayList<>();
        for (ExtractionPoint point : activePoints) {
            if (point.isActive() && point.getState() == ExtractionPoint.State.EXTRACTING) {
                result.add(point);
            }
        }
        return result;
    }

    public List<ExtractionPoint> getActivePoints() {
        return Collections.unmodifiableList(activePoints);
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
        for (ExtractionPoint point : activePoints) {
            String worldName = point.getLocation().getWorld().getName();
            WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);
            plugin.getStructureService().remove(point, wcfg);
            point.removeHologram(plugin.getHologramService());
        }
        activePoints.clear();
        playerCooldowns.clear();
        lastApproachNotified.clear();
    }
}
