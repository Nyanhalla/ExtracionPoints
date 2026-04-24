package com.nyanhalla.extractionpoints.models;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.WorldConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;

import java.util.HashMap;
import java.util.Map;

/**
 * Representa un punto de extracción activo en el mundo.
 */
public class ExtractionPoint {

    private final Location location;
    private final double radius;
    private boolean active;

    // Beacon & display visual data
    private final Map<Location, Material> originalBlocks = new HashMap<>();
    private TextDisplay textDisplay = null;
    private boolean beaconPlaced = false;

    public ExtractionPoint(Location location, double radius) {
        this.location = location;
        this.radius = radius;
        this.active = true;
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

    public boolean isBeaconPlaced() {
        return beaconPlaced;
    }

    public TextDisplay getTextDisplay() {
        return textDisplay;
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

        // Save and place beacon block
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

        beaconPlaced = true;
    }

    /**
     * Saves the original block at a location before overwriting it.
     */
    private void saveOriginalBlock(Location loc) {
        // Only save if not already saved (avoid overwriting first original)
        if (!originalBlocks.containsKey(loc)) {
            originalBlocks.put(loc.clone(), loc.getBlock().getType());
        }
    }

    /**
     * Removes the beacon and restores original blocks.
     */
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

        // Restore all original blocks in reverse order (beacon first, then base)
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
     * Creates a TextDisplay entity above the beacon showing idle text.
     */
    public void createTextDisplay(WorldConfig wcfg) {
        if (!wcfg.isDisplayEntityEnabled()) return;

        World world = location.getWorld();
        if (world == null) return;

        // Remove existing if any
        removeTextDisplay();

        String idleText = wcfg.getDisplayEntityIdleText();
        if (idleText == null || idleText.isEmpty()) return;

        Location displayLoc = location.clone().add(0, wcfg.getDisplayEntityHeightAbove(), 0);

        textDisplay = world.spawn(displayLoc, TextDisplay.class, display -> {
            display.setBillboard(Billboard.CENTER);
            display.setAlignment(TextAlignment.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(wcfg.isDisplayEntitySeeThrough());
            // Keep default transformation (no scaling changes needed)

            // Parse background color
            applyBackgroundColor(display, wcfg.getDisplayEntityBackgroundColor());

            // Set text
            Component text = LegacyComponentSerializer.legacyAmpersand().deserialize(idleText);
            display.text(text);
        });
    }

    /**
     * Updates the TextDisplay with extracting progress text.
     * @param remainingSeconds seconds remaining
     * @param totalTime total extraction time
     * @param wcfg world config
     */
    public void updateTextDisplay(int remainingSeconds, int totalTime, WorldConfig wcfg) {
        if (textDisplay == null || textDisplay.isDead()) return;
        if (!wcfg.isDisplayEntityEnabled()) return;

        double progress = totalTime > 0 ? (double) remainingSeconds / totalTime : 0.0;

        String format;
        if (progress > wcfg.getDisplayEntityYellowThreshold()) {
            format = wcfg.getDisplayEntityProgressGreen();
        } else if (progress > wcfg.getDisplayEntityRedThreshold()) {
            format = wcfg.getDisplayEntityProgressYellow();
        } else {
            format = wcfg.getDisplayEntityProgressRed();
        }

        String text = format.replace("{remaining}", String.valueOf(remainingSeconds));
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        textDisplay.text(component);
    }

    /**
     * Resets the TextDisplay back to idle text.
     */
    public void resetTextDisplay(WorldConfig wcfg) {
        if (textDisplay == null || textDisplay.isDead()) return;
        if (!wcfg.isDisplayEntityEnabled()) return;

        String idleText = wcfg.getDisplayEntityIdleText();
        if (idleText == null || idleText.isEmpty()) {
            textDisplay.text(Component.empty());
        } else {
            Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(idleText);
            textDisplay.text(component);
        }
    }

    /**
     * Removes the TextDisplay entity.
     */
    public void removeTextDisplay() {
        if (textDisplay != null) {
            if (!textDisplay.isDead()) {
                textDisplay.remove();
            }
            textDisplay = null;
        }
    }

    /**
     * Full cleanup: remove beacon + text display.
     */
    public void cleanup(WorldConfig wcfg) {
        removeBeacon(wcfg);
        removeTextDisplay();
    }

    /**
     * Parses a background color string like "BLACK@0.5" into ARGB color.
     */
    private void applyBackgroundColor(TextDisplay display, String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) return;
        try {
            String[] parts = colorStr.split("@");
            String colorName = parts[0].toUpperCase();
            int alpha = 255;
            if (parts.length > 1) {
                alpha = (int) (Double.parseDouble(parts[1]) * 255);
            }

            // Map common color names to RGB
            int rgb = 0;
            switch (colorName) {
                case "BLACK" -> rgb = 0x000000;
                case "WHITE" -> rgb = 0xFFFFFF;
                case "RED" -> rgb = 0xFF0000;
                case "GREEN" -> rgb = 0x00FF00;
                case "BLUE" -> rgb = 0x0000FF;
                case "YELLOW" -> rgb = 0xFFFF00;
                case "PURPLE" -> rgb = 0x800080;
                case "CYAN" -> rgb = 0x00FFFF;
                case "GRAY", "GREY" -> rgb = 0x808080;
                default -> {
                    // Try parsing as hex
                    if (colorName.startsWith("0x") || colorName.startsWith("#")) {
                        rgb = Integer.parseInt(colorName.replace("0x", "").replace("#", ""), 16);
                    }
                }
            }

            Color backgroundColor = Color.fromARGB(alpha, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            display.setBackgroundColor(backgroundColor);
        } catch (Exception e) {
            // Ignore color parsing errors
        }
    }

    @Override
    public String toString() {
        return String.format("ExtractionPoint{world=%s, x=%.1f, y=%.1f, z=%.1f, r=%.1f, active=%s}",
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                radius, active);
    }
}
