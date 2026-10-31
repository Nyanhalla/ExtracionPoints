package com.nyanhalla.extractionpoints.services;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;

/**
 * Backend de permisos basado en LuckPerms.
 * Concede un nodo transitorio "*" al jugador durante la ejecución de la acción
 * y lo retira al finalizar. Los nodos transitorios no se persisten en disco.
 */
public class LuckPermsPermissionService implements PermissionService {

    private final LuckPerms api;

    public LuckPermsPermissionService() {
        this.api = LuckPermsProvider.get();
    }

    @Override
    public void runWithBypass(Player player, Runnable action) {
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            // Usuario no cargado por LuckPerms; ejecutamos sin bypass
            action.run();
            return;
        }

        Node wildcard = Node.builder("*").build();
        boolean added = user.transientData().add(wildcard).wasSuccessful();
        try {
            if (added) {
                player.recalculatePermissions();
            }
            action.run();
        } finally {
            if (added) {
                user.transientData().remove(wildcard);
                player.recalculatePermissions();
            }
        }
    }

    @Override
    public String getBackendName() {
        return "LuckPerms";
    }
}
