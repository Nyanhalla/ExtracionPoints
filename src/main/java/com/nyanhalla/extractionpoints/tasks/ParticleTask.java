package com.nyanhalla.extractionpoints.tasks;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.ConfigManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tarea periódica que dibuja las partículas de los puntos de extracción.
 * Se ejecuta cada 10 ticks (0.5 segundos).
 */
public class ParticleTask extends BukkitRunnable {

    private final ExtractionPoints plugin;

    public ParticleTask(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ConfigManager cfg = plugin.getConfigManager();

        Particle beamParticle;
        Particle circleParticle;
        try {
            beamParticle = Particle.valueOf(cfg.getBeamParticle());
        } catch (IllegalArgumentException e) {
            beamParticle = Particle.END_ROD;
        }
        try {
            circleParticle = Particle.valueOf(cfg.getCircleParticle());
        } catch (IllegalArgumentException e) {
            circleParticle = Particle.FLAME;
        }

        int beamHeight = cfg.getBeamHeight();
        int circleDensity = cfg.getCircleDensity();
        double pointRadius = cfg.getPointRadius();

        for (ExtractionPoint point : plugin.getPointManager().getActivePoints()) {
            if (!point.isActive()) continue;

            Location loc = point.getLocation();
            if (loc.getWorld() == null) continue;
            if (!loc.getWorld().getName().equals(cfg.getWorldName())) continue;

            // Draw vertical beam
            for (int y = 0; y <= beamHeight; y += 2) {
                Location beamLoc = loc.clone().add(0, y, 0);
                loc.getWorld().spawnParticle(beamParticle, beamLoc, 1, 0.0, 0.0, 0.0, 0.0);
            }

            // Draw circle on ground
            for (int i = 0; i < circleDensity; i++) {
                double angle = (2 * Math.PI / circleDensity) * i;
                double x = loc.getX() + pointRadius * Math.cos(angle);
                double z = loc.getZ() + pointRadius * Math.sin(angle);
                Location circleLoc = new Location(loc.getWorld(), x, loc.getY() + 0.1, z);
                loc.getWorld().spawnParticle(circleParticle, circleLoc, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}
