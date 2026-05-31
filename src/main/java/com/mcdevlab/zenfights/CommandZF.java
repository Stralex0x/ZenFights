package com.mcdevlab.zenfights;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandZF implements CommandExecutor, TabCompleter {
    private final ZenFights plugin;
    private final String PREFIX = ChatColor.GOLD + "[ZenFights] " + ChatColor.RESET;

    public CommandZF(ZenFights plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // --- 1. Globally Available Player Commands ---
        if (args.length > 0 && args[0].equalsIgnoreCase("loadkit")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This sub-command configuration layer is player-exclusive.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(PREFIX + ChatColor.RED + "Usage: /zf loadkit [kitName]");
                return true;
            }
            String kitName = args[1];
            if (!plugin.getConfig().contains("kits." + kitName)) {
                player.sendMessage(PREFIX + ChatColor.RED + "The selected kit '" + kitName + "' does not exist.");
                return true;
            }
            if (plugin.getSandbox().isInFight(player)) {
                player.sendMessage(PREFIX + ChatColor.RED + "You cannot swap kit templates while locked within active combat coordinates!");
                return true;
            }
            plugin.getSandbox().applyKit(player, kitName);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Successfully loaded gear profile: " + kitName);
            return true;
        }

        // --- 2. Strict Admin Clearance Gate ---
        if (!sender.hasPermission("zenfights.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Inadequate execution clearances detected.");
            return true;
        }

        // --- 3. Restored Administrative Command Switch ---
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "reload":
                    plugin.reloadConfig();
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration parameters synchronized successfully.");
                    return true;

                case "createkit":
                case "savekit":
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("You must be an in-game player to save kit profiles from an inventory layout.");
                        return true;
                    }
                    if (args.length < 2) {
                        player.sendMessage(PREFIX + ChatColor.RED + "Usage: /zf createkit [name]");
                        return true;
                    }

                    String newKitName = args[1];
                    for (int i = 0; i < 36; i++) {
                        plugin.getConfig().set("kits." + newKitName + ".inv." + i, player.getInventory().getItem(i));
                    }
                    for (int i = 0; i < 4; i++) {
                        plugin.getConfig().set("kits." + newKitName + ".armor." + i, player.getInventory().getArmorContents()[i]);
                    }
                    plugin.getConfig().set("kits." + newKitName + ".offhand", player.getInventory().getItemInOffHand());
                    plugin.saveConfig();
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Successfully created and stored gear kit layout: " + newKitName);
                    return true;

                case "deletekit":
                    if (args.length < 2) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /zf deletekit [name]");
                        return true;
                    }
                    String targetKit = args[1];
                    if (!plugin.getConfig().contains("kits." + targetKit)) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "That kit does not exist.");
                        return true;
                    }
                    plugin.getConfig().set("kits." + targetKit, null);
                    plugin.saveConfig();
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Kit profile '" + targetKit + "' has been permanently erased.");
                    return true;

                case "listkits":
                    ConfigurationSection section = plugin.getConfig().getConfigurationSection("kits");
                    if (section == null || section.getKeys(false).isEmpty()) {
                        sender.sendMessage(PREFIX + ChatColor.YELLOW + "There are currently no kit profiles registered.");
                        return true;
                    }
                    sender.sendMessage(ChatColor.GOLD + "=== Registered Kits ===");
                    for (String key : section.getKeys(false)) {
                        sender.sendMessage(ChatColor.YELLOW + "- " + key);
                    }
                    return true;
            }
        }

        // --- 4. Restored Dynamic Help Interface Menu ---
        sender.sendMessage(ChatColor.GOLD + "============= " + ChatColor.YELLOW + "ZenFights Admin Controls" + ChatColor.GOLD + " =============");
        sender.sendMessage(ChatColor.YELLOW + "/zf loadkit [kit] " + ChatColor.GRAY + "- Apply a kit template profile directly");
        sender.sendMessage(ChatColor.YELLOW + "/zf createkit [name] " + ChatColor.GRAY + "- Convert current inventory setup into a saved kit");
        sender.sendMessage(ChatColor.YELLOW + "/zf deletekit [name] " + ChatColor.GRAY + "- Permanently drop a kit template profile");
        sender.sendMessage(ChatColor.YELLOW + "/zf listkits " + ChatColor.GRAY + "- View a listing of all currently active setups");
        sender.sendMessage(ChatColor.YELLOW + "/zf reload " + ChatColor.GRAY + "- Force core systems definitions cache refresh");
        sender.sendMessage(ChatColor.GOLD + "=====================================================");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("loadkit");
            if (sender.hasPermission("zenfights.admin")) {
                options.addAll(Arrays.asList("reload", "createkit", "deletekit", "listkits"));
            }
            return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("loadkit") || args[0].equalsIgnoreCase("deletekit")) {
                ConfigurationSection section = plugin.getConfig().getConfigurationSection("kits");
                if (section != null) {
                    return StringUtil.copyPartialMatches(args[1], new ArrayList<>(section.getKeys(false)), new ArrayList<>());
                }
            }
        }
        return Collections.emptyList();
    }
}