package com.nyanhalla.extractionpoints.services;

import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;

/** Implementación fallback: coloca el beacon + base piramidal vanilla. */
public class BeaconStructureService implements StructureService {

    @Override
    public boolean place(ExtractionPoint point, WorldConfig wcfg) {
        point.placeBeacon(wcfg);
        return point.isBeaconPlaced();
    }

    @Override
    public void remove(ExtractionPoint point, WorldConfig wcfg) {
        point.removeBeacon(wcfg);
    }

    @Override
    public String getBackendName() {
        return "Beacon";
    }
}
