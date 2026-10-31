package com.nyanhalla.extractionpoints;

import com.nyanhalla.extractionpoints.commands.ExtractionCommand;
import com.nyanhalla.extractionpoints.commands.ExtractionTabCompleter;
import com.nyanhalla.extractionpoints.config.ConfigManager;
import com.nyanhalla.extractionpoints.listeners.PlayerListener;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.services.AttachmentPermissionService;
import com.nyanhalla.extractionpoints.services.BeaconStructureService;
import com.nyanhalla.extractionpoints.services.DecentHologramsService;
import com.nyanhalla.extractionpoints.services.HologramService;
import com.nyanhalla.extractionpoints.services.LuckPermsPermissionService;
import com.nyanhalla.extractionpoints.services.PermissionService;
import com.nyanhalla.extractionpoints.services.StructureService;
import com.nyanhalla.extractionpoints.services.TextDisplayHologramService;
import com.nyanhalla.extractionpoints.services.WorldEditStructureService;
import com.nyanhalla.extractionpoints.tasks.ExtractionTask;
import com.nyanhalla.extractionpoints.tasks.ParticleTask;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal de ExtractionPoints.
 * Sistema de puntos de extracción para server PVP con islas skyblock.
 */
public class ExtractionPoints extends JavaPlugin {

    private ConfigManager configManager;
    private PointManager pointManager;
    private ExtractionTask extractionTask;
    private HologramService hologramService;
    private PermissionService permissionService;
    private StructureService structureService;

    public ExtractionTask getExtractionTask() { return extractionTask; }
    public StructureService getStructureService() { return structureService; }

    @Override
    public void onEnable() {
        // Load configuration
        configManager = new ConfigManager(this);
        configManager.load();

        // Initialize integration services
        hologramService = createHologramService();
        permissionService = createPermissionService();
        structureService = createStructureService();
        getLogger().info("Hologramas: " + hologramService.getBackendName()
                + " | Permisos: " + permissionService.getBackendName()
                + " | Estructura: " + structureService.getBackendName());

        // Initialize point manager
        pointManager = new PointManager(this);
        pointManager.initializePoints();

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Register commands
        PluginCommand cmd = getCommand("extractionpoints");
        if (cmd != null) {
            ExtractionCommand cmdHandler = new ExtractionCommand(this);
            cmd.setExecutor(cmdHandler);
            cmd.setTabCompleter(new ExtractionTabCompleter(this));
        }

        // Start particle task (every 10 ticks = 0.5 seconds)
        ParticleTask particleTask = new ParticleTask(this);
        particleTask.runTaskTimer(this, 20L, 10L);

        // Start extraction check task (every 20 ticks = 1 second)
        extractionTask = new ExtractionTask(this);
        extractionTask.runTaskTimer(this, 20L, 20L);

        // Start periodic point maintenance task
        getServer().getScheduler().runTaskTimer(this, () -> {
            pointManager.ensurePoints();
        }, 1200L, 1200L); // Check every 60 seconds

        // Tick de cooldowns: refresca contadores en holograma cada segundo
        getServer().getScheduler().runTaskTimer(this, () -> {
            pointManager.tickCooldowns();
            pointManager.tickProximity();
        }, 20L, 20L);

        getLogger().info("ExtractionPoints habilitado. Puntos activos: " + pointManager.getActivePoints().size());
    }

    @Override
    public void onDisable() {
        if (extractionTask != null) {
            extractionTask.cleanup();
        }
        if (pointManager != null) {
            pointManager.shutdown();
        }
        if (hologramService != null) {
            hologramService.removeAll();
        }
        getLogger().info("ExtractionPoints deshabilitado.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PointManager getPointManager() {
        return pointManager;
    }

    public HologramService getHologramService() {
        return hologramService;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    private HologramService createHologramService() {
        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            try {
                return new DecentHologramsService();
            } catch (Throwable t) {
                getLogger().warning("DecentHolograms detectado pero no se pudo inicializar: " + t.getMessage());
            }
        }
        return new TextDisplayHologramService(this);
    }

    private PermissionService createPermissionService() {
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                return new LuckPermsPermissionService();
            } catch (Throwable t) {
                getLogger().warning("LuckPerms detectado pero no se pudo inicializar: " + t.getMessage());
            }
        }
        return new AttachmentPermissionService(this);
    }

    private StructureService createStructureService() {
        boolean weAvailable = Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
        if (weAvailable) {
            try {
                return new WorldEditStructureService(this);
            } catch (Throwable t) {
                getLogger().warning("WorldEdit detectado pero no se pudo inicializar StructureService: " + t.getMessage());
            }
        }
        return new BeaconStructureService();
    }
}
