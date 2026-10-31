package com.nyanhalla.extractionpoints.services;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Backend de hologramas usando DecentHolograms.
 * Los hologramas se crean con saveToFile=false para no persistir entre reinicios
 * (evita hologramas huérfanos si el plugin se desactiva sin cleanup).
 */
public class DecentHologramsService implements HologramService {

    private final Set<String> ownedIds = new HashSet<>();

    @Override
    public void create(String id, Location location, List<String> lines) {
        Hologram existing = DHAPI.getHologram(id);
        if (existing != null) existing.delete();

        DHAPI.createHologram(id, location, false, sanitize(lines));
        ownedIds.add(id);
    }

    @Override
    public void update(String id, List<String> lines) {
        Hologram holo = DHAPI.getHologram(id);
        if (holo == null) return;
        DHAPI.setHologramLines(holo, sanitize(lines));
    }

    @Override
    public void remove(String id) {
        Hologram holo = DHAPI.getHologram(id);
        if (holo != null) holo.delete();
        ownedIds.remove(id);
    }

    @Override
    public void removeAll() {
        for (String id : new HashSet<>(ownedIds)) {
            Hologram holo = DHAPI.getHologram(id);
            if (holo != null) holo.delete();
        }
        ownedIds.clear();
    }

    @Override
    public String getBackendName() {
        return "DecentHolograms";
    }

    private List<String> sanitize(List<String> lines) {
        List<String> out = new ArrayList<>();
        if (lines == null) {
            out.add("");
            return out;
        }
        for (String line : lines) {
            out.add(line == null ? "" : line);
        }
        if (out.isEmpty()) out.add("");
        return out;
    }
}
