package com.nyanhalla.extractionpoints.tasks;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

/**
 * Tarea periódica que dibuja las partículas de los puntos de extracción.
 * Itera puntos de todos los mundos y solo renderiza si hay jugadores cerca.
 * Incluye animaciones mejoradas: círculo pulsante, rotación, espiral ascendente.
 */
public class ParticleTask extends BukkitRunnable {

    private final ExtractionPoints plugin;
    private int tickCounter = 0;

    public ParticleTask(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCounter++;
        PointManager pointManager = plugin.getPointManager();

        for (ExtractionPoint point : pointManager.getActivePoints()) {
            if (!point.isActive()) continue;

            Location loc = point.getLocation();
            if (loc.getWorld() == null) continue;

            String worldName = loc.getWorld().getName();
            WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);
            if (!wcfg.isEnabled()) continue;

            // Optimization: only render particles if there are players nearby
            int renderDistance = wcfg.getParticleRenderDistance();
            double renderDistSq = (double) renderDistance * renderDistance;
            boolean hasNearbyPlayer = false;

            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(loc) <= renderDistSq) {
                    hasNearbyPlayer = true;
                    break;
                }
            }

            if (!hasNearbyPlayer) continue;

            // Resolve particles
            Particle beamParticle;
            Particle circleParticle;
            try {
                beamParticle = Particle.valueOf(wcfg.getBeamParticle());
            } catch (IllegalArgumentException e) {
                beamParticle = Particle.END_ROD;
            }
            try {
                circleParticle = Particle.valueOf(wcfg.getCircleParticle());
            } catch (IllegalArgumentException e) {
                circleParticle = Particle.FLAME;
            }

            int beamHeight = wcfg.getBeamHeight();
            int beamSpacing = Math.max(1, wcfg.getBeamSpacing());
            int beamCount = wcfg.getBeamParticleCount();
            int circleDensity = wcfg.getCircleDensity();
            int circleCount = wcfg.getCircleParticleCount();
            double pointRadius = wcfg.getPointRadius();

            // --- Pulsing circle radius ---
            double pulseRadius = pointRadius * (1.0 + 0.15 * Math.sin(tickCounter * 0.1));

            // --- Rotation angle ---
            double rotation = tickCounter * 0.05;

            // Draw vertical beam (if no real beacon placed, otherwise skip to avoid overlap)
            if (!point.isBeaconPlaced()) {
                for (int y = 0; y <= beamHeight; y += beamSpacing) {
                    Location beamLoc = loc.clone().add(0, y, 0);
                    loc.getWorld().spawnParticle(beamParticle, beamLoc, beamCount, 0.0, 0.0, 0.0, 0.0);
                }
            }

            // Draw pulsing, rotating circle on ground
            for (int i = 0; i < circleDensity; i++) {
                double baseAngle = (2 * Math.PI / circleDensity) * i;
                double angle = baseAngle + rotation;
                double x = loc.getX() + pulseRadius * Math.cos(angle);
                double z = loc.getZ() + pulseRadius * Math.sin(angle);
                Location circleLoc = new Location(loc.getWorld(), x, loc.getY() + 0.1, z);
                loc.getWorld().spawnParticle(circleParticle, circleLoc, circleCount, 0.0, 0.0, 0.0, 0.0);
            }

            // --- Ascending spiral when someone is extracting at this point ---
            boolean isBeingUsed = isPointBeingExtracted(point, pointManager);
            if (isBeingUsed) {
                drawAscendingSpiral(loc, beamParticle, beamCount);
            }

            // --- Ambient sound for nearby players ---
            playAmbientSound(loc, wcfg, pointRadius);
        }
    }

    /**
     * Check if any player is currently extracting at this point.
     */
    private boolean isPointBeingExtracted(ExtractionPoint point, PointManager pointManager) {
        for (Map.Entry<UUID, ExtractionPoint> entry : pointManager.getExtractingPlayersCopy().entrySet()) {
            if (entry.getValue().equals(point)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Draws particles ascending in a spiral (helix) around the center.
     */
    private void drawAscendingSpiral(Location center, Particle particle, int count) {
        double spiralRadius = 1.5;
        int spiralHeight = 20;
        int spiralPoints = 12;
        double spiralRotation = tickCounter * 0.15;

        for (int i = 0; i < spiralPoints; i++) {
            double t = (double) i / spiralPoints;
            double angle = t * 2 * Math.PI * 2 + spiralRotation; // 2 full rotations
            double y = t * spiralHeight;
            double x = center.getX() + spiralRadius * Math.cos(angle);
            double z = center.getZ() + spiralRadius * Math.sin(angle);
            Location spiralLoc = new Location(center.getWorld(), x, center.getY() + y, z);
            center.getWorld().spawnParticle(particle, spiralLoc, count, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Plays ambient sound to nearby players who are NOT extracting.
     */
    private void playAmbientSound(Location loc, WorldConfig wcfg, double pointRadius) {
        String soundName = wcfg.getAmbientSound();
        if (soundName == null || soundName.isEmpty()) return;

        int interval = wcfg.getAmbientSoundInterval();
        if (interval <= 0 || tickCounter % interval != 0) return;

        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            return;
        }

        float volume = wcfg.getAmbientSoundVolume();
        float pitch = wcfg.getAmbientSoundPitch();
        double radiusSq = pointRadius * pointRadius;

        for (Player player : loc.getWorld().getPlayers()) {
            if (!plugin.getPointManager().isExtracting(player.getUniqueId())) {
                if (player.getLocation().distanceSquared(loc) <= radiusSq) {
                    player.playSound(loc, sound, volume, pitch);
                }
            }
        }
    }

    /**
     * Spawns a dramatic completion explosion of particles at a location.
     */
    public static void spawnCompletionEffect(Location loc) {
        if (loc.getWorld() == null) return;

        // TOTEM_OF_UNDYING explosion
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 100, 1.0, 2.0, 1.0, 0.1);

        // FIREWORK spark ring
        for (int i = 0; i < 36; i++) {
            double angle = (2 * Math.PI / 36) * i;
            double x = loc.getX() + 3.0 * Math.cos(angle);
            double z = loc.getZ() + 3.0 * Math.sin(angle);
            Location sparkLoc = new Location(loc.getWorld(), x, loc.getY() + 1.0, z);
            loc.getWorld().spawnParticle(Particle.FIREWORK, sparkLoc, 5, 0.1, 0.5, 0.1, 0.05);
        }

        // End rod vertical burst
        for (int y = 0; y < 15; y++) {
            Location burstLoc = loc.clone().add(0, y, 0);
            loc.getWorld().spawnParticle(Particle.END_ROD, burstLoc, 3, 0.3, 0.0, 0.3, 0.0);
        }
    }
}
