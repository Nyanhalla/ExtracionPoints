package com.nyanhalla.extractionpoints.config;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestiona la carga y acceso a la configuración del plugin.
 */
public class ConfigManager {

    private final ExtractionPoints plugin;
    private FileConfiguration config;

    // General settings
    private String worldName;
    private int spawnRadius;
    private double pointRadius;
    private int extractionTime;
    private int maxPoints;
    private int respawnInterval;
    private int minY;
    private int maxY;

    // Commands
    private final List<CommandEntry> commands = new ArrayList<>();

    // Messages
    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, String> adminMessages = new HashMap<>();

    // Effects
    private String beamParticle;
    private String circleParticle;
    private int beamHeight;
    private int circleDensity;
    private String extractionSound;
    private float extractionSoundVolume;
    private float extractionSoundPitch;

    public ConfigManager(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // General
        worldName = config.getString("extraction-points.world", "server");
        spawnRadius = config.getInt("extraction-points.spawn-radius", 500);
        pointRadius = config.getDouble("extraction-points.point-radius", 5.0);
        extractionTime = config.getInt("extraction-points.extraction-time", 20);
        maxPoints = config.getInt("extraction-points.max-points", 3);
        respawnInterval = config.getInt("extraction-points.respawn-interval", 60);
        minY = config.getInt("extraction-points.min-y", 60);
        maxY = config.getInt("extraction-points.max-y", 120);

        // Commands
        commands.clear();
        List<?> cmdList = config.getList("extraction-points.commands");
        if (cmdList != null) {
            for (Object obj : cmdList) {
                if (obj instanceof ConfigurationSection sec) {
                    commands.add(new CommandEntry(
                            sec.getString("command", "essentials:home {player}"),
                            sec.getBoolean("as-console", true),
                            sec.getLong("delay-ticks", 0)
                    ));
                } else if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) obj;
                    commands.add(new CommandEntry(
                            String.valueOf(map.getOrDefault("command", "essentials:home {player}")),
                            Boolean.parseBoolean(String.valueOf(map.getOrDefault("as-console", true))),
                            Long.parseLong(String.valueOf(map.getOrDefault("delay-ticks", 0)))
                    ));
                }
            }
        }
        if (commands.isEmpty()) {
            commands.add(new CommandEntry("essentials:home {player}", true, 0));
        }

        // Messages
        messages.clear();
        ConfigurationSection msgSec = config.getConfigurationSection("extraction-points.messages");
        if (msgSec != null) {
            for (String key : msgSec.getKeys(false)) {
                messages.put(key, msgSec.getString(key, ""));
            }
        }

        // Admin messages
        adminMessages.clear();
        ConfigurationSection adminMsgSec = config.getConfigurationSection("admin.messages");
        if (adminMsgSec != null) {
            for (String key : adminMsgSec.getKeys(false)) {
                adminMessages.put(key, adminMsgSec.getString(key, ""));
            }
        }

        // Effects
        beamParticle = config.getString("extraction-points.effects.beam-particle", "END_ROD");
        circleParticle = config.getString("extraction-points.effects.circle-particle", "FLAME");
        beamHeight = config.getInt("extraction-points.effects.beam-height", 30);
        circleDensity = config.getInt("extraction-points.effects.circle-density", 20);
        extractionSound = config.getString("extraction-points.effects.extraction-sound", "ENTITY_ENDERMAN_TELEPORT");
        extractionSoundVolume = (float) config.getDouble("extraction-points.effects.extraction-sound-volume", 1.0);
        extractionSoundPitch = (float) config.getDouble("extraction-points.effects.extraction-sound-pitch", 1.0);
    }

    // --- Getters ---

    public String getWorldName() { return worldName; }
    public int getSpawnRadius() { return spawnRadius; }
    public double getPointRadius() { return pointRadius; }
    public int getExtractionTime() { return extractionTime; }
    public int getMaxPoints() { return maxPoints; }
    public int getRespawnInterval() { return respawnInterval; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public List<CommandEntry> getCommands() { return commands; }
    public String getBeamParticle() { return beamParticle; }
    public String getCircleParticle() { return circleParticle; }
    public int getBeamHeight() { return beamHeight; }
    public int getCircleDensity() { return circleDensity; }
    public String getExtractionSound() { return extractionSound; }
    public float getExtractionSoundVolume() { return extractionSoundVolume; }
    public float getExtractionSoundPitch() { return extractionSoundPitch; }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "");
    }

    public String getAdminMessage(String key) {
        return adminMessages.getOrDefault(key, "");
    }

    /**
     * Entrada de comando configurable.
     */
    public static class CommandEntry {
        private final String command;
        private final boolean asConsole;
        private final long delayTicks;

        public CommandEntry(String command, boolean asConsole, long delayTicks) {
            this.command = command;
            this.asConsole = asConsole;
            this.delayTicks = delayTicks;
        }

        public String getCommand() { return command; }
        public boolean isAsConsole() { return asConsole; }
        public long getDelayTicks() { return delayTicks; }
    }
}
