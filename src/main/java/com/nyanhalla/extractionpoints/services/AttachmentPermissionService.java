package com.nyanhalla.extractionpoints.services;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

/**
 * Backend fallback de permisos usando PermissionAttachment de Bukkit.
 * Compatible con cualquier provider que respete el sistema de permisos
 * estándar de Bukkit/Paper.
 */
public class AttachmentPermissionService implements PermissionService {

    private final Plugin plugin;

    public AttachmentPermissionService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runWithBypass(Player player, Runnable action) {
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("*", true);
        try {
            action.run();
        } finally {
            try {
                attachment.remove();
            } catch (IllegalStateException ignored) {
                // Attachment ya retirado
            }
        }
    }

    @Override
    public String getBackendName() {
        return "PermissionAttachment (fallback)";
    }
}
