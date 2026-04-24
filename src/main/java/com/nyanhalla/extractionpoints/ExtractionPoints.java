package com.nyanhalla.extractionpoints;

import com.nyanhalla.extractionpoints.commands.ExtractionCommand;
import com.nyanhalla.extractionpoints.config.ConfigManager;
import com.nyanhalla.extractionpoints.listeners.PlayerListener;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.tasks.ExtractionTask;
import com.nyanhalla.extractionpoints.tasks.ParticleTask;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal de ExtractionPoints.
 * Sistema de puntos de extracción para server PVP con islas skyblock.
 */
public class ExtractionPoints extends JavaPlugin {

    private ConfigManager configManager;
    private PointManager pointManager;

    @Override
    public void onEnable() {
        // Load configuration
        configManager = new ConfigManager(this);
        configManager.load();

        // Initialize point manager
        pointManager = new PointManager(this);
        pointManager.initializePoints();

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Register commands
        ExtractionCommand cmdHandler = new ExtractionCommand(this);
        getCommand("extractionpoints").setExecutor(cmdHandler);

        // Start particle task (every 10 ticks = 0.5 seconds)
        ParticleTask particleTask = new ParticleTask(this);
        particleTask.runTaskTimer(this, 20L, 10L);

        // Start extraction check task (every 20 ticks = 1 second)
        ExtractionTask extractionTask = new ExtractionTask(this);
        extractionTask.runTaskTimer(this, 20L, 20L);

        // Start periodic point maintenance task
        getServer().getScheduler().runTaskTimer(this, () -> {
            pointManager.ensurePoints();
        }, 1200L, 1200L); // Check every 60 seconds

        getLogger().info("ExtractionPoints habilitado. Puntos activos: " + pointManager.getActivePoints().size());
    }

    @Override
    public void onDisable() {
        if (pointManager != null) {
            pointManager.shutdown();
        }
        getLogger().info("ExtractionPoints deshabilitado.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PointManager getPointManager() {
        return pointManager;
    }
}
