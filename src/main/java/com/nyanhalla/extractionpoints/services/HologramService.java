package com.nyanhalla.extractionpoints.services;

import org.bukkit.Location;

import java.util.List;

/**
 * Servicio de hologramas. Implementaciones: DecentHolograms (preferida) y
 * TextDisplay nativo de Paper como fallback.
 */
public interface HologramService {

    /**
     * Crea un holograma identificado por id en la location dada con las líneas
     * iniciales. Si ya existe uno con ese id, lo elimina y lo recrea.
     */
    void create(String id, Location location, List<String> lines);

    /**
     * Actualiza las líneas del holograma. No-op si no existe.
     */
    void update(String id, List<String> lines);

    /**
     * Elimina el holograma. No-op si no existe.
     */
    void remove(String id);

    /**
     * Elimina todos los hologramas creados por este plugin.
     * Útil al deshabilitar el plugin para no dejar entidades huérfanas.
     */
    void removeAll();

    /**
     * Nombre legible del backend para logs.
     */
    String getBackendName();
}
