package com.mcdevlab.zenfights;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import java.util.*;

public class CommandFight implements CommandExecutor, TabCompleter {
    private final ZenFights plugin;
    private final String PREFIX = ChatColor.GOLD + "[ZenFights] " + ChatColor.RESET;

    public CommandFight(ZenFights plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only online players can issue arena execution commands.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            player.sendMessage(ChatColor.GOLD + "========== " + ChatColor.YELLOW + "ZenFights Command Index" + ChatColor.GOLD + " ==========");
            player.sendMessage(ChatColor.YELLOW + "/fight [player] " + ChatColor.GRAY + "- Send a match challenge request using GUI selection");
            player.sendMessage(ChatColor.YELLOW + "/fight accept [player] " + ChatColor.GRAY + "- Accept an active battle invite");
            player.sendMessage(ChatColor.YELLOW + "/fight practice [kit] " + ChatColor.GRAY + "- Start an offline match vs an AI Bot");
            player.sendMessage(ChatColor.YELLOW + "/fight practice exit " + ChatColor.GRAY + "- Forcibly leave your active practice match");
            player.sendMessage(ChatColor.YELLOW + "/fight spec [player] " + ChatColor.GRAY + "- Request to spectate someone's active match");
            player.sendMessage(ChatColor.YELLOW + "/fight spec accept [player] " + ChatColor.GRAY + "- Allow someone to watch you");
            player.sendMessage(ChatColor.YELLOW + "/fight spec exit " + ChatColor.GRAY + "- Leave your spectator perspective");
            player.sendMessage(ChatColor.GOLD + "===============================================");
            return true;
        }

        // --- NEW SPECTATOR COMMANDS ---
        if (args[0].equalsIgnoreCase("spec") || args[0].equalsIgnoreCase("spectate")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + ChatColor.RED + "Usage: /fight spec [playerName] OR /fight spec exit OR /fight spec accept [playerName]");
                return true;
            }

            if (args[1].equalsIgnoreCase("exit")) {
                if (!plugin.getSandbox().isSpectating(player)) {
                    player.sendMessage(PREFIX + ChatColor.RED + "You are not currently spectating a fight.");
                    return true;
                }
                plugin.getSandbox().stopSpectating(player);
                return true;
            }

            if (args[1].equalsIgnoreCase("accept")) {
                if (args.length < 3) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Usage: /fight spec accept [playerName]");
                    return true;
                }
                Player requester = Bukkit.getPlayer(args[2]);
                if (requester == null) {
                    player.sendMessage(PREFIX + ChatColor.RED + "That player is not online.");
                    return true;
                }
                plugin.getSandbox().acceptSpectate(player, requester);
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(PREFIX + ChatColor.RED + "Specified matchmaking duelist target is offline.");
                return true;
            }

            plugin.getSandbox().requestSpectate(player, target);
            return true;
        }
        // --- END SPECTATOR COMMANDS ---

        if (args[0].equalsIgnoreCase("practice")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + ChatColor.RED + "Usage: /fight practice [kitName] OR /fight practice exit");
                return true;
            }

            if (args[1].equalsIgnoreCase("exit")) {
                SandboxManager.DuelSession currentSession = plugin.getSandbox().getSession(player);
                if (currentSession == null || !currentSession.isPractice) {
                    player.sendMessage(PREFIX + ChatColor.RED + "You are not currently inside an active practice match.");
                    return true;
                }
                plugin.getSandbox().endPractice(player, false);
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Exited simulation early.");
                return true;
            }

            if (plugin.getSandbox().isInFight(player)) {
                player.sendMessage(PREFIX + ChatColor.RED + "You are already locked inside an active combat session!");
                return true;
            }

            String kitName = args[1];
            if (!plugin.getConfig().contains("kits." + kitName)) {
                player.sendMessage(PREFIX + ChatColor.RED + "The selected kit '" + kitName + "' does not exist.");
                return true;
            }

            plugin.getSandbox().startPractice(player, kitName);
            return true;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + ChatColor.RED + "Usage: /fight accept [playerName]");
                return true;
            }
            Player challenger = Bukkit.getPlayer(args[1]);
            if (challenger == null) {
                player.sendMessage(PREFIX + ChatColor.RED + "That player is not online.");
                return true;
            }
            plugin.getSandbox().acceptRequest(player, challenger);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Specified matchmaking duelist target is offline.");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You cannot challenge yourself.");
            return true;
        }

        GUIListener.open(player, target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>(Arrays.asList("help", "accept", "practice", "spec"));
            for (Player p : Bukkit.getOnlinePlayers()) sub.add(p.getName());
            return StringUtil.copyPartialMatches(args[0], sub, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("practice")) {
            List<String> practiceSub = new ArrayList<>();
            practiceSub.add("exit");
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("kits");
            if (section != null) practiceSub.addAll(section.getKeys(false));
            return StringUtil.copyPartialMatches(args[1], practiceSub, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }

        // New Spectator Tab Complete Routing
        if (args.length == 2 && (args[0].equalsIgnoreCase("spec") || args[0].equalsIgnoreCase("spectate"))) {
            List<String> specSub = new ArrayList<>();
            specSub.add("exit");
            specSub.add("accept");
            for (Player p : Bukkit.getOnlinePlayers()) specSub.add(p.getName());
            return StringUtil.copyPartialMatches(args[1], specSub, new ArrayList<>());
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("spec") || args[0].equalsIgnoreCase("spectate")) && args[1].equalsIgnoreCase("accept")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return StringUtil.copyPartialMatches(args[2], names, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}