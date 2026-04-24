package com.nyanhalla.extractionpoints.config;

import java.util.List;
import java.util.Map;

/**
 * Resolved configuration for a specific world (global defaults + per-world overrides).
 * All getters return the effective value for this world.
 */
public class WorldConfig {

    private final String worldName;
    private boolean enabled;
    private boolean useWorldBorder;
    private String spawnCenter;
    private int spawnRadius;
    private double pointRadius;
    private int extractionTime;
    private int maxPoints;
    private int minY;
    private int maxY;
    private int borderMargin;
    private int minDistanceBetweenPoints;
    private List<CommandEntry> commands;
    private Map<String, String> messages;
    private String beamParticle;
    private int beamParticleCount;
    private int beamHeight;
    private int beamSpacing;
    private String circleParticle;
    private int circleParticleCount;
    private int circleDensity;
    private int particleRenderDistance;
    private String extractionSound;
    private float extractionSoundVolume;
    private float extractionSoundPitch;
    private String startSound;
    private float startSoundVolume;
    private float startSoundPitch;
    private String cancelSound;
    private float cancelSoundVolume;
    private float cancelSoundPitch;
    private String displayMode;
    private String bossbarColor;
    private String bossbarStyle;
    private String bossbarTitle;
    private int playerCooldown;
    private String usePermission;

    // Beacon config
    private boolean beaconEnabled;
    private String beaconBaseMaterial;
    private boolean beaconPlaceBeacon;
    private boolean beaconRestoreBlocks;

    // Display entity config
    private boolean displayEntityEnabled;
    private double displayEntityHeightAbove;
    private int displayEntityUpdateInterval;
    private String displayEntityIdleText;
    private String displayEntityProgressGreen;
    private String displayEntityProgressYellow;
    private String displayEntityProgressRed;
    private double displayEntityYellowThreshold;
    private double displayEntityRedThreshold;
    private String displayEntityBackgroundColor;
    private boolean displayEntitySeeThrough;

    // Ambient sound config
    private String ambientSound;
    private int ambientSoundInterval;
    private float ambientSoundVolume;
    private float ambientSoundPitch;

    public WorldConfig(String worldName) {
        this.worldName = worldName;
    }

    // --- Getters and Setters ---

    public String getWorldName() { return worldName; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean useWorldBorder() { return useWorldBorder; }
    public void setUseWorldBorder(boolean useWorldBorder) { this.useWorldBorder = useWorldBorder; }

    public String getSpawnCenter() { return spawnCenter; }
    public void setSpawnCenter(String spawnCenter) { this.spawnCenter = spawnCenter; }

    public int getSpawnRadius() { return spawnRadius; }
    public void setSpawnRadius(int spawnRadius) { this.spawnRadius = spawnRadius; }

    public double getPointRadius() { return pointRadius; }
    public void setPointRadius(double pointRadius) { this.pointRadius = pointRadius; }

    public int getExtractionTime() { return extractionTime; }
    public void setExtractionTime(int extractionTime) { this.extractionTime = extractionTime; }

    public int getMaxPoints() { return maxPoints; }
    public void setMaxPoints(int maxPoints) { this.maxPoints = maxPoints; }

    public int getMinY() { return minY; }
    public void setMinY(int minY) { this.minY = minY; }

    public int getMaxY() { return maxY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }

    public int getBorderMargin() { return borderMargin; }
    public void setBorderMargin(int borderMargin) { this.borderMargin = borderMargin; }

    public int getMinDistanceBetweenPoints() { return minDistanceBetweenPoints; }
    public void setMinDistanceBetweenPoints(int minDistanceBetweenPoints) { this.minDistanceBetweenPoints = minDistanceBetweenPoints; }

    public List<CommandEntry> getCommands() { return commands; }
    public void setCommands(List<CommandEntry> commands) { this.commands = commands; }

    public Map<String, String> getMessages() { return messages; }
    public void setMessages(Map<String, String> messages) { this.messages = messages; }

    public String getMessage(String key) {
        return messages != null ? messages.getOrDefault(key, "") : "";
    }

    public String getBeamParticle() { return beamParticle; }
    public void setBeamParticle(String beamParticle) { this.beamParticle = beamParticle; }

    public int getBeamParticleCount() { return beamParticleCount; }
    public void setBeamParticleCount(int beamParticleCount) { this.beamParticleCount = beamParticleCount; }

    public int getBeamHeight() { return beamHeight; }
    public void setBeamHeight(int beamHeight) { this.beamHeight = beamHeight; }

    public int getBeamSpacing() { return beamSpacing; }
    public void setBeamSpacing(int beamSpacing) { this.beamSpacing = beamSpacing; }

    public String getCircleParticle() { return circleParticle; }
    public void setCircleParticle(String circleParticle) { this.circleParticle = circleParticle; }

    public int getCircleParticleCount() { return circleParticleCount; }
    public void setCircleParticleCount(int circleParticleCount) { this.circleParticleCount = circleParticleCount; }

    public int getCircleDensity() { return circleDensity; }
    public void setCircleDensity(int circleDensity) { this.circleDensity = circleDensity; }

    public int getParticleRenderDistance() { return particleRenderDistance; }
    public void setParticleRenderDistance(int particleRenderDistance) { this.particleRenderDistance = particleRenderDistance; }

    public String getExtractionSound() { return extractionSound; }
    public void setExtractionSound(String extractionSound) { this.extractionSound = extractionSound; }

    public float getExtractionSoundVolume() { return extractionSoundVolume; }
    public void setExtractionSoundVolume(float extractionSoundVolume) { this.extractionSoundVolume = extractionSoundVolume; }

    public float getExtractionSoundPitch() { return extractionSoundPitch; }
    public void setExtractionSoundPitch(float extractionSoundPitch) { this.extractionSoundPitch = extractionSoundPitch; }

    public String getStartSound() { return startSound; }
    public void setStartSound(String startSound) { this.startSound = startSound; }

    public float getStartSoundVolume() { return startSoundVolume; }
    public void setStartSoundVolume(float startSoundVolume) { this.startSoundVolume = startSoundVolume; }

    public float getStartSoundPitch() { return startSoundPitch; }
    public void setStartSoundPitch(float startSoundPitch) { this.startSoundPitch = startSoundPitch; }

    public String getCancelSound() { return cancelSound; }
    public void setCancelSound(String cancelSound) { this.cancelSound = cancelSound; }

    public float getCancelSoundVolume() { return cancelSoundVolume; }
    public void setCancelSoundVolume(float cancelSoundVolume) { this.cancelSoundVolume = cancelSoundVolume; }

    public float getCancelSoundPitch() { return cancelSoundPitch; }
    public void setCancelSoundPitch(float cancelSoundPitch) { this.cancelSoundPitch = cancelSoundPitch; }

    public String getDisplayMode() { return displayMode; }
    public void setDisplayMode(String displayMode) { this.displayMode = displayMode; }

    public String getBossbarColor() { return bossbarColor; }
    public void setBossbarColor(String bossbarColor) { this.bossbarColor = bossbarColor; }

    public String getBossbarStyle() { return bossbarStyle; }
    public void setBossbarStyle(String bossbarStyle) { this.bossbarStyle = bossbarStyle; }

    public String getBossbarTitle() { return bossbarTitle; }
    public void setBossbarTitle(String bossbarTitle) { this.bossbarTitle = bossbarTitle; }

    public int getPlayerCooldown() { return playerCooldown; }
    public void setPlayerCooldown(int playerCooldown) { this.playerCooldown = playerCooldown; }

    public String getUsePermission() { return usePermission; }
    public void setUsePermission(String usePermission) { this.usePermission = usePermission; }

    // --- Beacon getters/setters ---

    public boolean isBeaconEnabled() { return beaconEnabled; }
    public void setBeaconEnabled(boolean beaconEnabled) { this.beaconEnabled = beaconEnabled; }

    public String getBeaconBaseMaterial() { return beaconBaseMaterial; }
    public void setBeaconBaseMaterial(String beaconBaseMaterial) { this.beaconBaseMaterial = beaconBaseMaterial; }

    public boolean isBeaconPlaceBeacon() { return beaconPlaceBeacon; }
    public void setBeaconPlaceBeacon(boolean beaconPlaceBeacon) { this.beaconPlaceBeacon = beaconPlaceBeacon; }

    public boolean isBeaconRestoreBlocks() { return beaconRestoreBlocks; }
    public void setBeaconRestoreBlocks(boolean beaconRestoreBlocks) { this.beaconRestoreBlocks = beaconRestoreBlocks; }

    // --- Display Entity getters/setters ---

    public boolean isDisplayEntityEnabled() { return displayEntityEnabled; }
    public void setDisplayEntityEnabled(boolean displayEntityEnabled) { this.displayEntityEnabled = displayEntityEnabled; }

    public double getDisplayEntityHeightAbove() { return displayEntityHeightAbove; }
    public void setDisplayEntityHeightAbove(double displayEntityHeightAbove) { this.displayEntityHeightAbove = displayEntityHeightAbove; }

    public int getDisplayEntityUpdateInterval() { return displayEntityUpdateInterval; }
    public void setDisplayEntityUpdateInterval(int displayEntityUpdateInterval) { this.displayEntityUpdateInterval = displayEntityUpdateInterval; }

    public String getDisplayEntityIdleText() { return displayEntityIdleText; }
    public void setDisplayEntityIdleText(String displayEntityIdleText) { this.displayEntityIdleText = displayEntityIdleText; }

    public String getDisplayEntityProgressGreen() { return displayEntityProgressGreen; }
    public void setDisplayEntityProgressGreen(String displayEntityProgressGreen) { this.displayEntityProgressGreen = displayEntityProgressGreen; }

    public String getDisplayEntityProgressYellow() { return displayEntityProgressYellow; }
    public void setDisplayEntityProgressYellow(String displayEntityProgressYellow) { this.displayEntityProgressYellow = displayEntityProgressYellow; }

    public String getDisplayEntityProgressRed() { return displayEntityProgressRed; }
    public void setDisplayEntityProgressRed(String displayEntityProgressRed) { this.displayEntityProgressRed = displayEntityProgressRed; }

    public double getDisplayEntityYellowThreshold() { return displayEntityYellowThreshold; }
    public void setDisplayEntityYellowThreshold(double displayEntityYellowThreshold) { this.displayEntityYellowThreshold = displayEntityYellowThreshold; }

    public double getDisplayEntityRedThreshold() { return displayEntityRedThreshold; }
    public void setDisplayEntityRedThreshold(double displayEntityRedThreshold) { this.displayEntityRedThreshold = displayEntityRedThreshold; }

    public String getDisplayEntityBackgroundColor() { return displayEntityBackgroundColor; }
    public void setDisplayEntityBackgroundColor(String displayEntityBackgroundColor) { this.displayEntityBackgroundColor = displayEntityBackgroundColor; }

    public boolean isDisplayEntitySeeThrough() { return displayEntitySeeThrough; }
    public void setDisplayEntitySeeThrough(boolean displayEntitySeeThrough) { this.displayEntitySeeThrough = displayEntitySeeThrough; }

    // --- Ambient sound getters/setters ---

    public String getAmbientSound() { return ambientSound; }
    public void setAmbientSound(String ambientSound) { this.ambientSound = ambientSound; }

    public int getAmbientSoundInterval() { return ambientSoundInterval; }
    public void setAmbientSoundInterval(int ambientSoundInterval) { this.ambientSoundInterval = ambientSoundInterval; }

    public float getAmbientSoundVolume() { return ambientSoundVolume; }
    public void setAmbientSoundVolume(float ambientSoundVolume) { this.ambientSoundVolume = ambientSoundVolume; }

    public float getAmbientSoundPitch() { return ambientSoundPitch; }
    public void setAmbientSoundPitch(float ambientSoundPitch) { this.ambientSoundPitch = ambientSoundPitch; }

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
