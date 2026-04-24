package com.nyanhalla.extractionpoints.commands;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completer para /extractionpoints (alias /ep).
 */
public class ExtractionTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "list", "spawn", "remove", "worlds");

    private final ExtractionPoints plugin;

    public ExtractionTabCompleter(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("extractionpoints.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("list") || sub.equals("spawn") || sub.equals("remove")) {
                String prefix = args[1].toLowerCase();
                return Bukkit.getWorlds().stream()
                        .map(w -> w.getName())
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}
