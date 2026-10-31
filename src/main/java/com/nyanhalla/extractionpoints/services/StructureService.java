package com.nyanhalla.extractionpoints.services;

import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;

/**
 * Abstracción para colocar y restaurar la estructura visual del punto de extracción.
 * Implementaciones: BeaconStructureService (vanilla) y WorldEditStructureService (schem).
 */
public interface StructureService {

    /**
     * Coloca la estructura en el mundo.
     * @return true si se colocó correctamente.
     */
    boolean place(ExtractionPoint point, WorldConfig wcfg);

    /** Restaura los bloques originales (deshace la estructura). */
    void remove(ExtractionPoint point, WorldConfig wcfg);

    String getBackendName();
}
