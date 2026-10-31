package com.nyanhalla.extractionpoints.models;

import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.services.HologramService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Representa un punto de extracción activo en el mundo.
 */
public class ExtractionPoint {

    public enum State { IDLE, EXTRACTING, COOLDOWN }

    private final String id;
    private final Location location;
    private final double radius;
    private boolean active;
    private State state = State.IDLE;
    private long cooldownUntil = 0L;
    private long extractionStartedAt = 0L;
    private UUID initiator = null;

    // Beacon visual data
    private final Map<Location, Material> originalBlocks = new HashMap<>();
    private boolean beaconPlaced = false;
    private boolean hologramCreated = false;

    public ExtractionPoint(Location location, double radius) {
        this.id = "ep_" + UUID.randomUUID();
        this.location = location;
        this.radius = radius;
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public double getRadius() {
        return radius;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isBeaconPlaced() { return beaconPlaced; }
    public void setBeaconPlaced(boolean beaconPlaced) { this.beaconPlaced = beaconPlaced; }

    /**
     * Estado actual sin efectos secundarios. La expiración del cooldown debe
     * gestionarse llamando a {@link #tickCooldown()} desde el scheduler.
     */
    public State getState() {
        return state;
    }

    public void setState(State newState) {
        this.state = newState;
    }

    /**
     * Pone el punto en cooldown durante {@code seconds} segundos.
     */
    public void enterCooldown(int seconds) {
        this.state = State.COOLDOWN;
        this.cooldownUntil = System.currentTimeMillis() + (seconds * 1000L);
        this.extractionStartedAt = 0L;
        this.initiator = null;
    }

    /**
     * Inicia el temporizador de extracción del punto y lo pone EXTRACTING.
     */
    public void beginExtraction(UUID initiator) {
        this.state = State.EXTRACTING;
        this.extractionStartedAt = System.currentTimeMillis();
        this.initiator = initiator;
    }

    /**
     * Limpia el timer de extracción.
     */
    public void clearExtraction() {
        this.extractionStartedAt = 0L;
        this.initiator = null;
    }

    public long getExtractionStartedAt() {
        return extractionStartedAt;
    }

    public UUID getInitiator() {
        return initiator;
    }

    /**
     * Segundos transcurridos desde que comenzó la extracción del punto.
     */
    public int getExtractionElapsedSeconds() {
        if (extractionStartedAt == 0L) return 0;
        return (int) ((System.currentTimeMillis() - extractionStartedAt) / 1000L);
    }

    /**
     * Fracción [0..1] de progreso de extracción.
     */
    public double getExtractionProgress(int totalSeconds) {
        if (extractionStartedAt == 0L || totalSeconds <= 0) return 0.0;
        double elapsedMs = System.currentTimeMillis() - extractionStartedAt;
        return Math.max(0.0, Math.min(1.0, elapsedMs / (totalSeconds * 1000.0)));
    }

    /**
     * Si el punto está en cooldown y éste ha expirado, lo pasa a IDLE.
     * @return true si transicionó (acaba de expirar), false en caso contrario.
     */
    public boolean tickCooldown() {
        if (state == State.COOLDOWN && System.currentTimeMillis() >= cooldownUntil) {
            state = State.IDLE;
            return true;
        }
        return false;
    }

    /**
     * Segundos restantes de cooldown (0 si ya está disponible).
     */
    public int getCooldownRemainingSeconds() {
        if (state != State.COOLDOWN) return 0;
        long diff = cooldownUntil - System.currentTimeMillis();
        if (diff <= 0) return 0;
        return (int) Math.ceil(diff / 1000.0);
    }

    /**
     * Verifica si una location está dentro del área circular de este punto.
     * Solo compara X y Z, y que estén en el mismo mundo.
     */
    public boolean isInRange(Location loc) {
        if (!loc.getWorld().equals(location.getWorld())) {
            return false;
        }
        double dx = loc.getX() - location.getX();
        double dz = loc.getZ() - location.getZ();
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    /**
     * Distancia horizontal (2D) a una location.
     */
    public double distance2D(Location loc) {
        if (!loc.getWorld().equals(location.getWorld())) return Double.MAX_VALUE;
        double dx = loc.getX() - location.getX();
        double dz = loc.getZ() - location.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Coloca un beacon real y su base piramidal de minerales.
     * Guarda los bloques originales para restauración posterior.
     */
    public void placeBeacon(WorldConfig wcfg) {
        if (!wcfg.isBeaconEnabled() || !wcfg.isBeaconPlaceBeacon()) return;

        World world = location.getWorld();
        if (world == null) return;

        Material baseMaterial;
        try {
            baseMaterial = Material.valueOf(wcfg.getBeaconBaseMaterial());
        } catch (IllegalArgumentException e) {
            baseMaterial = Material.IRON_BLOCK;
        }

        int beaconX = location.getBlockX();
        int beaconY = location.getBlockY();
        int beaconZ = location.getBlockZ();

        Location beaconLoc = new Location(world, beaconX, beaconY, beaconZ);
        saveOriginalBlock(beaconLoc);
        world.setType(beaconLoc, Material.BEACON);

        // Place 3x3 base pyramid at Y-1
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Location baseLoc = new Location(world, beaconX + dx, beaconY - 1, beaconZ + dz);
                saveOriginalBlock(baseLoc);
                world.setType(baseLoc, baseMaterial);
            }
        }

        // Cristal tintado opcional sobre el beacon para colorear el haz
        Material glass = resolveGlassColor(wcfg.getBeaconGlassColor());
        if (glass != null) {
            Location glassLoc = new Location(world, beaconX, beaconY + 1, beaconZ);
            saveOriginalBlock(glassLoc);
            world.setType(glassLoc, glass);
        }

        beaconPlaced = true;
    }

    private Material resolveGlassColor(String colorName) {
        if (colorName == null) return null;
        String c = colorName.trim().toUpperCase();
        if (c.isEmpty()) return null;
        try {
            return Material.valueOf(c + "_STAINED_GLASS");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void saveOriginalBlock(Location loc) {
        if (!originalBlocks.containsKey(loc)) {
            originalBlocks.put(loc.clone(), loc.getBlock().getType());
        }
    }

    /**
     * Actualiza el cristal tintado sobre el beacon para reflejar el estado del punto.
     * Se llama en cada transición de estado (IDLE/EXTRACTING/COOLDOWN).
     */
    public void updateBeaconGlass(WorldConfig wcfg, State state) {
        if (!beaconPlaced) return;
        World world = location.getWorld();
        if (world == null) return;

        String colorName = switch (state) {
            case EXTRACTING -> wcfg.getBeaconGlassColorExtracting();
            case COOLDOWN -> wcfg.getBeaconGlassColorCooldown();
            default -> wcfg.getBeaconGlassColor();
        };

        Location glassLoc = new Location(world, location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        if (colorName == null || colorName.trim().isEmpty()) {
            glassLoc.getBlock().setType(Material.AIR);
        } else {
            Material glass = resolveGlassColor(colorName);
            if (glass != null) glassLoc.getBlock().setType(glass);
        }
    }

    public void removeBeacon(WorldConfig wcfg) {
        if (!beaconPlaced) return;
        if (!wcfg.isBeaconRestoreBlocks()) {
            beaconPlaced = false;
            return;
        }

        World world = location.getWorld();
        if (world == null) {
            beaconPlaced = false;
            return;
        }

        for (Map.Entry<Location, Material> entry : originalBlocks.entrySet()) {
            Location loc = entry.getKey();
            Material mat = entry.getValue();
            if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                loc.getBlock().setType(mat);
            }
        }
        originalBlocks.clear();
        beaconPlaced = false;
    }

    /**
     * Crea el holograma sobre el beacon a través del HologramService.
     */
    public void createHologram(HologramService hologramService, WorldConfig wcfg) {
        if (!wcfg.isDisplayEntityEnabled()) return;
        Location displayLoc = location.clone().add(0.5, wcfg.getDisplayEntityHeightAbove(), 0.5);
        hologramService.create(id, displayLoc, buildIdleLines(wcfg));
        hologramCreated = true;
    }

    /**
     * Actualiza el holograma con el progreso de extracción (estado EXTRACTING).
     */
    public void updateHologram(HologramService hologramService, int remainingSeconds, int totalTime, WorldConfig wcfg) {
        if (!hologramCreated || !wcfg.isDisplayEntityEnabled()) return;

        double progress = totalTime > 0 ? (double) remainingSeconds / totalTime : 0.0;
        String format;
        if (progress > wcfg.getDisplayEntityYellowThreshold()) {
            format = wcfg.getDisplayEntityProgressGreen();
        } else if (progress > wcfg.getDisplayEntityRedThreshold()) {
            format = wcfg.getDisplayEntityProgressYellow();
        } else {
            format = wcfg.getDisplayEntityProgressRed();
        }

        String title = format.replace("{remaining}", String.valueOf(remainingSeconds));
        String subtitle = nullSafe(wcfg.getDisplayEntitySubtitleExtracting())
                .replace("{remaining}", String.valueOf(remainingSeconds))
                .replace("{progress-bar}", buildProgressBar(progress));
        hologramService.update(id, lines(title, subtitle));
    }

    /**
     * Vuelve el holograma al texto idle (o cooldown si toca).
     */
    public void resetHologram(HologramService hologramService, WorldConfig wcfg) {
        if (!hologramCreated || !wcfg.isDisplayEntityEnabled()) return;
        if (getState() == State.COOLDOWN) {
            hologramService.update(id, buildCooldownLines(wcfg));
        } else {
            hologramService.update(id, buildIdleLines(wcfg));
        }
    }

    /**
     * Refresca el holograma según el estado actual del punto.
     * El task de partículas/cooldown lo invoca periódicamente.
     */
    public void refreshHologramForState(HologramService hologramService, WorldConfig wcfg) {
        if (!hologramCreated || !wcfg.isDisplayEntityEnabled()) return;
        switch (getState()) {
            case IDLE -> hologramService.update(id, buildIdleLines(wcfg));
            case COOLDOWN -> hologramService.update(id, buildCooldownLines(wcfg));
            case EXTRACTING -> { /* gestionado por updateHologram en cada tick de extracción */ }
        }
    }

    private List<String> buildIdleLines(WorldConfig wcfg) {
        return lines(nullSafe(wcfg.getDisplayEntityIdleText()),
                nullSafe(wcfg.getDisplayEntitySubtitleIdle()));
    }

    private List<String> buildCooldownLines(WorldConfig wcfg) {
        int remaining = getCooldownRemainingSeconds();
        String title = nullSafe(wcfg.getDisplayEntityCooldownText())
                .replace("{remaining}", String.valueOf(remaining));
        String subtitle = nullSafe(wcfg.getDisplayEntitySubtitleCooldown())
                .replace("{remaining}", String.valueOf(remaining));
        return lines(title, subtitle);
    }

    private List<String> lines(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isEmpty()) out.add(v);
        }
        if (out.isEmpty()) out.add("");
        return out;
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    /**
     * Barra de progreso con gradiente de color: verde → amarillo → rojo.
     * remainingFraction: 1.0 = recién empezado, 0.0 = casi completo.
     */
    private String buildProgressBar(double remainingFraction) {
        int total = 10;
        int filled = (int) Math.round((1.0 - remainingFraction) * total);
        filled = Math.max(0, Math.min(total, filled));

        // Color of filled blocks based on how much time remains
        String filledColor;
        if (remainingFraction > 0.6) filledColor = "&a";       // green: plenty of time
        else if (remainingFraction > 0.3) filledColor = "&e";  // yellow: getting urgent
        else filledColor = "&c";                                // red: nearly done

        StringBuilder sb = new StringBuilder(filledColor);
        for (int i = 0; i < filled; i++) sb.append("▰");
        sb.append("&8");
        for (int i = filled; i < total; i++) sb.append("▱");
        return sb.toString();
    }

    /**
     * Elimina el holograma.
     */
    public void removeHologram(HologramService hologramService) {
        if (!hologramCreated) return;
        hologramService.remove(id);
        hologramCreated = false;
    }

    /**
     * Cleanup completo: beacon + holograma.
     */
    public void cleanup(HologramService hologramService, WorldConfig wcfg) {
        removeBeacon(wcfg);
        removeHologram(hologramService);
    }

    @Override
    public String toString() {
        return String.format("ExtractionPoint{id=%s, world=%s, x=%.1f, y=%.1f, z=%.1f, r=%.1f, active=%s}",
                id, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                radius, active);
    }
}
