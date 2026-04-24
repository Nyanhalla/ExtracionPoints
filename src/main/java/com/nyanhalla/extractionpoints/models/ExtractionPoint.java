package com.nyanhalla.extractionpoints.models;

import org.bukkit.Location;

/**
 * Representa un punto de extracción activo en el mundo.
 */
public class ExtractionPoint {

    private final Location location;
    private final double radius;
    private boolean active;

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

    @Override
    public String toString() {
        return String.format("ExtractionPoint{world=%s, x=%.1f, y=%.1f, z=%.1f, r=%.1f, active=%s}",
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                radius, active);
    }
}
