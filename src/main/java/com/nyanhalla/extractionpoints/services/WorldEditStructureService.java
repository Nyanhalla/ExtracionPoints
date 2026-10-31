package com.nyanhalla.extractionpoints.services;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coloca la estructura de un punto de extracción usando un schematic de WorldEdit.
 * Guarda snapshot de los bloques originales para restauración perfecta.
 *
 * El schematic debe tener el beacon en su origen (posición que se fija con //copy
 * estando el jugador encima del bloque beacon).
 *
 * Fallback automático a beacon vanilla si WE no está disponible o el schematic no existe.
 */
public class WorldEditStructureService implements StructureService {

    private final ExtractionPoints plugin;
    private final BeaconStructureService fallback = new BeaconStructureService();

    /** pointId → lista de bloques capturados antes del paste */
    private final Map<String, List<BlockSnapshot>> snapshots = new HashMap<>();

    public WorldEditStructureService(ExtractionPoints plugin) {
        this.plugin = plugin;
        // Crea la carpeta de schematics si no existe
        File schemsDir = new File(plugin.getDataFolder(), "schematics");
        if (!schemsDir.exists()) {
            schemsDir.mkdirs();
            plugin.getLogger().info("Carpeta 'schematics/' creada en " + plugin.getDataFolder().getPath());
        }
    }

    @Override
    public boolean place(ExtractionPoint point, WorldConfig wcfg) {
        if (!wcfg.isUseSchematic()) {
            return fallback.place(point, wcfg);
        }

        String schemName = wcfg.getSchematicName();
        File schemFile = resolveSchematicFile(schemName);

        if (schemFile == null || !schemFile.exists()) {
            plugin.getLogger().warning("[ExtractionPoints] Schematic '" + schemName
                    + "' no encontrado en schematics/ — usando beacon vanilla.");
            return fallback.place(point, wcfg);
        }

        try {
            return pasteSchematic(point, wcfg, schemFile);
        } catch (Throwable t) {
            plugin.getLogger().severe("[ExtractionPoints] Error al pegar schematic '"
                    + schemName + "': " + t.getMessage() + " — usando beacon vanilla.");
            return fallback.place(point, wcfg);
        }
    }

    @Override
    public void remove(ExtractionPoint point, WorldConfig wcfg) {
        List<BlockSnapshot> snap = snapshots.remove(point.getId());
        if (snap == null) {
            // Fue colocado por el fallback
            fallback.remove(point, wcfg);
            return;
        }
        for (BlockSnapshot bs : snap) {
            Location loc = bs.location();
            if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                loc.getBlock().setBlockData(bs.data());
            }
        }
        point.setBeaconPlaced(false);
    }

    @Override
    public String getBackendName() {
        return "WorldEdit/Schematic";
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private boolean pasteSchematic(ExtractionPoint point, WorldConfig wcfg, File schemFile) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
        if (format == null) {
            plugin.getLogger().warning("[ExtractionPoints] Formato de schematic desconocido: " + schemFile.getName());
            return fallback.place(point, wcfg);
        }

        Location loc = point.getLocation();
        org.bukkit.World world = loc.getWorld();
        if (world == null) return false;

        try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
            Clipboard clipboard = reader.read();

            BlockVector3 origin = clipboard.getOrigin();
            BlockVector3 min    = clipboard.getMinimumPoint();
            BlockVector3 max    = clipboard.getMaximumPoint();

            int dx = loc.getBlockX() - origin.x();
            int dy = loc.getBlockY() - origin.y();
            int dz = loc.getBlockZ() - origin.z();

            // Captura bloques originales ANTES del paste (solo los no-aire del clipboard)
            List<BlockSnapshot> snap = new ArrayList<>();
            for (int bx = min.x(); bx <= max.x(); bx++) {
                for (int by = min.y(); by <= max.y(); by++) {
                    for (int bz = min.z(); bz <= max.z(); bz++) {
                        BlockVector3 cv = BlockVector3.at(bx, by, bz);
                        if (clipboard.getBlock(cv).getBlockType().getMaterial().isAir()) continue;
                        Location wl = new Location(world, bx + dx, by + dy, bz + dz);
                        snap.add(new BlockSnapshot(wl.clone(), wl.getBlock().getBlockData()));
                    }
                }
            }

            // Pega el schematic (sin actor → límite ilimitado por defecto)
            try (EditSession editSession = WorldEdit.getInstance()
                    .newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(world))
                    .build()) {

                Operation op = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()))
                        .build();
                Operations.complete(op);
            }

            snapshots.put(point.getId(), snap);
            point.setBeaconPlaced(true);

            // Glass de estado sobre el beacon (misma lógica que el modo vanilla)
            point.updateBeaconGlass(wcfg, ExtractionPoint.State.IDLE);
            return true;
        }
    }

    private File resolveSchematicFile(String name) {
        if (name == null || name.isEmpty()) return null;
        File schemsDir = new File(plugin.getDataFolder(), "schematics");
        // Prueba con extensiones comunes
        for (String ext : new String[]{"schem", "schematic"}) {
            File f = new File(schemsDir, name + "." + ext);
            if (f.exists()) return f;
        }
        return null;
    }

    private record BlockSnapshot(Location location, BlockData data) {}
}
