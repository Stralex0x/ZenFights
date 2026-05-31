package com.mcdevlab.zenfights;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class ZenFightsPlaceholders extends PlaceholderExpansion {

    private final ZenFights plugin;

    public ZenFightsPlaceholders(ZenFights plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "MCDevLab";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "zenfights";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // %zenfights_zen%
        if (params.equalsIgnoreCase("zen")) {
            int defaultZen = plugin.getConfig().getInt("default-zen", 3000);
            return String.valueOf(plugin.getConfig().getInt("stats." + player.getUniqueId(), defaultZen));
        }

        // %zenfights_wins%
        if (params.equalsIgnoreCase("wins")) {
            List<String> history = plugin.getConfig().getStringList("history." + player.getUniqueId());
            long wins = history.stream().filter(entry -> entry.startsWith("WIN")).count();
            return String.valueOf(wins);
        }

        // %zenfights_losses% OR %zenfights_deaths%
        if (params.equalsIgnoreCase("losses") || params.equalsIgnoreCase("deaths")) {
            List<String> history = plugin.getConfig().getStringList("history." + player.getUniqueId());
            long losses = history.stream().filter(entry -> entry.startsWith("LOSS")).count();
            return String.valueOf(losses);
        }

        // %zenfights_matches_played%
        if (params.equalsIgnoreCase("matches_played")) {
            int matchCount = plugin.getConfig().getStringList("history." + player.getUniqueId()).size();
            return String.valueOf(matchCount);
        }

        // %zenfights_status%
        if (params.equalsIgnoreCase("status")) {
            boolean inFight = plugin.getSandbox().getMatchHistoryEntry(player.getUniqueId()) != null;
            return inFight ? "§bIn Fight" : "§7Idle";
        }

        return null;
    }
}