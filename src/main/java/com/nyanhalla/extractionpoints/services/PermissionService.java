package com.nyanhalla.extractionpoints.services;

import org.bukkit.entity.Player;

/**
 * Servicio para ejecutar acciones con permisos elevados de forma temporal.
 * Implementaciones: LuckPerms (preferida) y PermissionAttachment de Bukkit (fallback).
 */
public interface PermissionService {

    /**
     * Ejecuta {@code action} concediendo al jugador el permiso wildcard durante
     * la ejecución. Garantiza limpieza incluso si la acción lanza excepciones.
     */
    void runWithBypass(Player player, Runnable action);

    /**
     * Nombre legible del backend para logs.
     */
    String getBackendName();
}
