package com.nyanhalla.extractionpoints.tasks;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tarea periódica que dibuja las partículas de los puntos de extracción.
 * Itera puntos de todos los mundos y solo renderiza si hay jugadores cerca.
 */
public class ParticleTask extends BukkitRunnable {

    private final ExtractionPoints plugin;

    public ParticleTask(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (ExtractionPoint point : plugin.getPointManager().getActivePoints()) {
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

            // Draw vertical beam
            for (int y = 0; y <= beamHeight; y += beamSpacing) {
                Location beamLoc = loc.clone().add(0, y, 0);
                loc.getWorld().spawnParticle(beamParticle, beamLoc, beamCount, 0.0, 0.0, 0.0, 0.0);
            }

            // Draw circle on ground
            for (int i = 0; i < circleDensity; i++) {
                double angle = (2 * Math.PI / circleDensity) * i;
                double x = loc.getX() + pointRadius * Math.cos(angle);
                double z = loc.getZ() + pointRadius * Math.sin(angle);
                Location circleLoc = new Location(loc.getWorld(), x, loc.getY() + 0.1, z);
                loc.getWorld().spawnParticle(circleParticle, circleLoc, circleCount, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}
