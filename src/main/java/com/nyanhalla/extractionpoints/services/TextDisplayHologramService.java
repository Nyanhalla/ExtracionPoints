package com.nyanhalla.extractionpoints.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend fallback usando TextDisplay nativos de Paper.
 *
 * Marca cada entity con un PersistentDataContainer key para poder localizar y
 * limpiar TextDisplays huérfanos al iniciar (caso de crash o desactivación
 * sin shutdown completo).
 */
public class TextDisplayHologramService implements HologramService {

    private final Plugin plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey idKey;
    private final Map<String, TextDisplay> entities = new HashMap<>();

    public TextDisplayHologramService(Plugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "ep_owner");
        this.idKey = new NamespacedKey(plugin, "ep_id");
        sweepOrphans();
    }

    @Override
    public void create(String id, Location location, List<String> lines) {
        remove(id);
        World world = location.getWorld();
        if (world == null) return;

        TextDisplay display = world.spawn(location, TextDisplay.class, e -> {
            e.setBillboard(Billboard.CENTER);
            e.setAlignment(TextAlignment.CENTER);
            e.setShadowed(true);
            e.text(joinLines(lines));
            e.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, plugin.getName());
            e.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        });
        entities.put(id, display);
    }

    @Override
    public void update(String id, List<String> lines) {
        TextDisplay display = entities.get(id);
        if (display == null || display.isDead()) return;
        display.text(joinLines(lines));
    }

    @Override
    public void remove(String id) {
        TextDisplay display = entities.remove(id);
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    @Override
    public void removeAll() {
        for (TextDisplay display : entities.values()) {
            if (display != null && !display.isDead()) display.remove();
        }
        entities.clear();
    }

    @Override
    public String getBackendName() {
        return "TextDisplay (fallback)";
    }

    private void sweepOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity e : world.getEntitiesByClass(TextDisplay.class)) {
                String owner = e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                if (plugin.getName().equals(owner)) {
                    e.remove();
                }
            }
        }
    }

    /**
     * Une líneas con \n para que TextDisplay las renderice como párrafo.
     */
    private Component joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return Component.empty();
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
        Component result = Component.empty();
        boolean first = true;
        for (String line : lines) {
            if (line == null) line = "";
            if (!first) result = result.append(Component.newline());
            result = result.append(serializer.deserialize(line));
            first = false;
        }
        return result;
    }
}
