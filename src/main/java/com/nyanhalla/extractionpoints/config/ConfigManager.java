package com.nyanhalla.extractionpoints.config;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Level;

/**
 * Gestiona la carga y acceso a la configuración del plugin.
 * Soporta config por mundo con overrides globales.
 */
public class ConfigManager {

    private final ExtractionPoints plugin;
    private FileConfiguration config;

    // Cached admin messages
    private final Map<String, String> adminMessages = new HashMap<>();

    public ConfigManager(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Load admin messages
        adminMessages.clear();
        ConfigurationSection adminMsgSec = config.getConfigurationSection("admin.messages");
        if (adminMsgSec != null) {
            for (String key : adminMsgSec.getKeys(false)) {
                adminMessages.put(key, adminMsgSec.getString(key, ""));
            }
        }
    }

    /**
     * Resuelve la configuración efectiva para un mundo específico.
     * Combina defaults globales con overrides del mundo.
     */
    public WorldConfig getWorldConfig(String worldName) {
        WorldConfig wcfg = new WorldConfig(worldName);

        ConfigurationSection globalSec = config.getConfigurationSection("global");
        if (globalSec == null) {
            // Fallback defaults if no global section
            applyDefaults(wcfg);
            return wcfg;
        }

        // Load global defaults
        applySection(wcfg, globalSec);

        // Apply per-world overrides
        ConfigurationSection worldsSec = config.getConfigurationSection("worlds");
        if (worldsSec != null) {
            ConfigurationSection worldSec = worldsSec.getConfigurationSection(worldName);
            if (worldSec != null) {
                applySection(wcfg, worldSec);
            }
        }

        return wcfg;
    }

    private void applyDefaults(WorldConfig wcfg) {
        wcfg.setEnabled(false);
        wcfg.setUseWorldBorder(true);
        wcfg.setSpawnCenter("spawn");
        wcfg.setSpawnRadius(500);
        wcfg.setPointRadius(5.0);
        wcfg.setExtractionTime(20);
        wcfg.setMaxPoints(3);
        wcfg.setMinY(60);
        wcfg.setMaxY(120);
        wcfg.setBorderMargin(10);
        wcfg.setMinDistanceBetweenPoints(50);
        wcfg.setCommands(List.of(new WorldConfig.CommandEntry("home {player}", true, 0)));
        wcfg.setMessages(Map.of());
        wcfg.setBeamParticle("END_ROD");
        wcfg.setBeamParticleCount(1);
        wcfg.setBeamHeight(30);
        wcfg.setBeamSpacing(2);
        wcfg.setCircleParticle("FLAME");
        wcfg.setCircleParticleCount(1);
        wcfg.setCircleDensity(20);
        wcfg.setParticleRenderDistance(64);
        wcfg.setExtractionSound("ENTITY_ENDERMAN_TELEPORT");
        wcfg.setExtractionSoundVolume(1.0f);
        wcfg.setExtractionSoundPitch(1.0f);
        wcfg.setStartSound("BLOCK_NOTE_BLOCK_PLING");
        wcfg.setStartSoundVolume(1.0f);
        wcfg.setStartSoundPitch(1.5f);
        wcfg.setCancelSound("ENTITY_ITEM_BREAK");
        wcfg.setCancelSoundVolume(0.5f);
        wcfg.setCancelSoundPitch(1.0f);
        wcfg.setDisplayMode("actionbar");
        wcfg.setBossbarColor("GREEN");
        wcfg.setBossbarStyle("SEGMENTED_10");
        wcfg.setBossbarTitle("&aExtrayendo &e{remaining}s &arestates");
        wcfg.setPlayerCooldown(0);
        wcfg.setUsePermission("");
        // Beacon defaults
        wcfg.setBeaconEnabled(true);
        wcfg.setBeaconBaseMaterial("IRON_BLOCK");
        wcfg.setBeaconPlaceBeacon(true);
        wcfg.setBeaconRestoreBlocks(true);
        // Display entity defaults
        wcfg.setDisplayEntityEnabled(true);
        wcfg.setDisplayEntityHeightAbove(3.0);
        wcfg.setDisplayEntityUpdateInterval(20);
        wcfg.setDisplayEntityIdleText("&b⚔ Punto de Extracción");
        wcfg.setDisplayEntityProgressGreen("&a⏳ {remaining}s");
        wcfg.setDisplayEntityProgressYellow("&e⏳ {remaining}s");
        wcfg.setDisplayEntityProgressRed("&c⏳ {remaining}s");
        wcfg.setDisplayEntityYellowThreshold(0.6);
        wcfg.setDisplayEntityRedThreshold(0.3);
        wcfg.setDisplayEntityBackgroundColor("BLACK@0.5");
        wcfg.setDisplayEntitySeeThrough(false);
        // Ambient sound defaults
        wcfg.setAmbientSound("BLOCK_BEACON_AMBIENT");
        wcfg.setAmbientSoundInterval(40);
        wcfg.setAmbientSoundVolume(0.3f);
        wcfg.setAmbientSoundPitch(1.0f);
    }

    private void applySection(WorldConfig wcfg, ConfigurationSection sec) {
        if (sec.contains("enabled")) wcfg.setEnabled(sec.getBoolean("enabled"));
        if (sec.contains("use-world-border")) wcfg.setUseWorldBorder(sec.getBoolean("use-world-border"));
        if (sec.contains("spawn-center")) wcfg.setSpawnCenter(sec.getString("spawn-center"));
        if (sec.contains("spawn-radius")) wcfg.setSpawnRadius(sec.getInt("spawn-radius"));
        if (sec.contains("point-radius")) wcfg.setPointRadius(sec.getDouble("point-radius"));
        if (sec.contains("extraction-time")) wcfg.setExtractionTime(sec.getInt("extraction-time"));
        if (sec.contains("max-points")) wcfg.setMaxPoints(sec.getInt("max-points"));
        if (sec.contains("min-y")) wcfg.setMinY(sec.getInt("min-y"));
        if (sec.contains("max-y")) wcfg.setMaxY(sec.getInt("max-y"));
        if (sec.contains("border-margin")) wcfg.setBorderMargin(sec.getInt("border-margin"));
        if (sec.contains("min-distance-between-points")) wcfg.setMinDistanceBetweenPoints(sec.getInt("min-distance-between-points"));
        if (sec.contains("player-cooldown")) wcfg.setPlayerCooldown(sec.getInt("player-cooldown"));
        if (sec.contains("use-permission")) wcfg.setUsePermission(sec.getString("use-permission", ""));

        // Commands
        if (sec.contains("commands")) {
            wcfg.setCommands(loadCommands(sec));
        }

        // Messages
        ConfigurationSection msgSec = sec.getConfigurationSection("messages");
        if (msgSec != null) {
            Map<String, String> messages = new HashMap<>(wcfg.getMessages() != null ? wcfg.getMessages() : Map.of());
            for (String key : msgSec.getKeys(false)) {
                messages.put(key, msgSec.getString(key, ""));
            }
            wcfg.setMessages(messages);
        }

        // Beacon
        ConfigurationSection beaconSec = sec.getConfigurationSection("beacon");
        if (beaconSec != null) {
            if (beaconSec.contains("enabled")) wcfg.setBeaconEnabled(beaconSec.getBoolean("enabled"));
            if (beaconSec.contains("base-material")) wcfg.setBeaconBaseMaterial(beaconSec.getString("base-material"));
            if (beaconSec.contains("place-beacon")) wcfg.setBeaconPlaceBeacon(beaconSec.getBoolean("place-beacon"));
            if (beaconSec.contains("restore-blocks")) wcfg.setBeaconRestoreBlocks(beaconSec.getBoolean("restore-blocks"));
        }

        // Display entity
        ConfigurationSection displayEntitySec = sec.getConfigurationSection("display-entity");
        if (displayEntitySec != null) {
            if (displayEntitySec.contains("enabled")) wcfg.setDisplayEntityEnabled(displayEntitySec.getBoolean("enabled"));
            if (displayEntitySec.contains("height-above")) wcfg.setDisplayEntityHeightAbove(displayEntitySec.getDouble("height-above"));
            if (displayEntitySec.contains("update-interval")) wcfg.setDisplayEntityUpdateInterval(displayEntitySec.getInt("update-interval"));
            if (displayEntitySec.contains("idle-text")) wcfg.setDisplayEntityIdleText(displayEntitySec.getString("idle-text"));
            if (displayEntitySec.contains("see-through")) wcfg.setDisplayEntitySeeThrough(displayEntitySec.getBoolean("see-through"));
            if (displayEntitySec.contains("background-color")) wcfg.setDisplayEntityBackgroundColor(displayEntitySec.getString("background-color"));

            ConfigurationSection fmtSec = displayEntitySec.getConfigurationSection("extracting-format");
            if (fmtSec != null) {
                if (fmtSec.contains("progress-green")) wcfg.setDisplayEntityProgressGreen(fmtSec.getString("progress-green"));
                if (fmtSec.contains("progress-yellow")) wcfg.setDisplayEntityProgressYellow(fmtSec.getString("progress-yellow"));
                if (fmtSec.contains("progress-red")) wcfg.setDisplayEntityProgressRed(fmtSec.getString("progress-red"));
                if (fmtSec.contains("yellow-threshold")) wcfg.setDisplayEntityYellowThreshold(fmtSec.getDouble("yellow-threshold"));
                if (fmtSec.contains("red-threshold")) wcfg.setDisplayEntityRedThreshold(fmtSec.getDouble("red-threshold"));
            }
        }

        // Effects
        ConfigurationSection effectsSec = sec.getConfigurationSection("effects");
        if (effectsSec != null) {
            if (effectsSec.contains("beam-particle")) wcfg.setBeamParticle(effectsSec.getString("beam-particle"));
            if (effectsSec.contains("beam-particle-count")) wcfg.setBeamParticleCount(effectsSec.getInt("beam-particle-count"));
            if (effectsSec.contains("beam-height")) wcfg.setBeamHeight(effectsSec.getInt("beam-height"));
            if (effectsSec.contains("beam-spacing")) wcfg.setBeamSpacing(effectsSec.getInt("beam-spacing"));
            if (effectsSec.contains("circle-particle")) wcfg.setCircleParticle(effectsSec.getString("circle-particle"));
            if (effectsSec.contains("circle-particle-count")) wcfg.setCircleParticleCount(effectsSec.getInt("circle-particle-count"));
            if (effectsSec.contains("circle-density")) wcfg.setCircleDensity(effectsSec.getInt("circle-density"));
            if (effectsSec.contains("particle-render-distance")) wcfg.setParticleRenderDistance(effectsSec.getInt("particle-render-distance"));
            if (effectsSec.contains("extraction-sound")) wcfg.setExtractionSound(effectsSec.getString("extraction-sound"));
            if (effectsSec.contains("extraction-sound-volume")) wcfg.setExtractionSoundVolume((float) effectsSec.getDouble("extraction-sound-volume"));
            if (effectsSec.contains("extraction-sound-pitch")) wcfg.setExtractionSoundPitch((float) effectsSec.getDouble("extraction-sound-pitch"));
            if (effectsSec.contains("start-sound")) wcfg.setStartSound(effectsSec.getString("start-sound"));
            if (effectsSec.contains("start-sound-volume")) wcfg.setStartSoundVolume((float) effectsSec.getDouble("start-sound-volume"));
            if (effectsSec.contains("start-sound-pitch")) wcfg.setStartSoundPitch((float) effectsSec.getDouble("start-sound-pitch"));
            if (effectsSec.contains("cancel-sound")) wcfg.setCancelSound(effectsSec.getString("cancel-sound"));
            if (effectsSec.contains("cancel-sound-volume")) wcfg.setCancelSoundVolume((float) effectsSec.getDouble("cancel-sound-volume"));
            if (effectsSec.contains("cancel-sound-pitch")) wcfg.setCancelSoundPitch((float) effectsSec.getDouble("cancel-sound-pitch"));
            // Ambient sound
            if (effectsSec.contains("ambient-sound")) wcfg.setAmbientSound(effectsSec.getString("ambient-sound"));
            if (effectsSec.contains("ambient-sound-interval")) wcfg.setAmbientSoundInterval(effectsSec.getInt("ambient-sound-interval"));
            if (effectsSec.contains("ambient-sound-volume")) wcfg.setAmbientSoundVolume((float) effectsSec.getDouble("ambient-sound-volume"));
            if (effectsSec.contains("ambient-sound-pitch")) wcfg.setAmbientSoundPitch((float) effectsSec.getDouble("ambient-sound-pitch"));
        }

        // Display
        ConfigurationSection displaySec = sec.getConfigurationSection("display");
        if (displaySec != null) {
            if (displaySec.contains("mode")) wcfg.setDisplayMode(displaySec.getString("mode"));
            if (displaySec.contains("bossbar-color")) wcfg.setBossbarColor(displaySec.getString("bossbar-color"));
            if (displaySec.contains("bossbar-style")) wcfg.setBossbarStyle(displaySec.getString("bossbar-style"));
            if (displaySec.contains("bossbar-title")) wcfg.setBossbarTitle(displaySec.getString("bossbar-title"));
        }
    }

    private List<WorldConfig.CommandEntry> loadCommands(ConfigurationSection parentSec) {
        List<WorldConfig.CommandEntry> commands = new ArrayList<>();
        List<?> cmdList = parentSec.getList("commands");
        if (cmdList != null) {
            for (Object obj : cmdList) {
                if (obj instanceof ConfigurationSection sec) {
                    commands.add(new WorldConfig.CommandEntry(
                            sec.getString("command", "home {player}"),
                            sec.getBoolean("as-console", true),
                            sec.getLong("delay-ticks", 0)
                    ));
                } else if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) obj;
                    commands.add(new WorldConfig.CommandEntry(
                            String.valueOf(map.getOrDefault("command", "home {player}")),
                            Boolean.parseBoolean(String.valueOf(map.getOrDefault("as-console", true))),
                            Long.parseLong(String.valueOf(map.getOrDefault("delay-ticks", 0)))
                    ));
                }
            }
        }
        if (commands.isEmpty()) {
            commands.add(new WorldConfig.CommandEntry("home {player}", true, 0));
        }
        return commands;
    }

    /**
     * Obtiene todos los mundos que tienen extracción habilitada.
     */
    public Set<String> getEnabledWorlds() {
        Set<String> enabled = new LinkedHashSet<>();

        // Check per-world overrides
        ConfigurationSection worldsSec = config.getConfigurationSection("worlds");
        if (worldsSec != null) {
            for (String worldName : worldsSec.getKeys(false)) {
                WorldConfig wcfg = getWorldConfig(worldName);
                if (wcfg.isEnabled()) {
                    enabled.add(worldName);
                }
            }
        }

        // Also check all loaded worlds in case global enabled=true
        ConfigurationSection globalSec = config.getConfigurationSection("global");
        if (globalSec != null && globalSec.getBoolean("enabled", false)) {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                String name = world.getName();
                WorldConfig wcfg = getWorldConfig(name);
                if (wcfg.isEnabled()) {
                    enabled.add(name);
                }
            }
        }

        return enabled;
    }

    /**
     * Obtiene todos los mundos configurados en la sección worlds.
     */
    public Set<String> getConfiguredWorldNames() {
        ConfigurationSection worldsSec = config.getConfigurationSection("worlds");
        if (worldsSec == null) return Set.of();
        return worldsSec.getKeys(false);
    }

    public String getAdminMessage(String key) {
        return adminMessages.getOrDefault(key, "");
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
