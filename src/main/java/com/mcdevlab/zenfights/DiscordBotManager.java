package com.mcdevlab.zenfights;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import java.awt.Color;
import java.util.*;
import java.util.stream.Collectors;

public class DiscordBotManager extends ListenerAdapter {

    private final ZenFights plugin;
    private JDA jda;

    public DiscordBotManager(ZenFights plugin) {
        this.plugin = plugin;
    }

    public void startBot() {
        if (!plugin.getConfig().getBoolean("discord-bot.enabled", false)) return;

        String token = plugin.getConfig().getString("discord-bot.bot-token", "");
        if (token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("Discord Bot is enabled but token is unset inside config.yml!");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                this.jda = JDABuilder.createDefault(token)
                        .addEventListeners(this)
                        .build();
                this.jda.awaitReady();

                // Bind updated interactive command configurations
                this.jda.updateCommands().addCommands(
                        Commands.slash("zfbot-top", "Display the top 10 ranked duelists across the server network"),
                        Commands.slash("zfbot-stats", "Check standard competitive statistics and rolling game history for a player")
                                .addOption(OptionType.STRING, "username", "Target alphanumeric player name", true)
                ).queue();

                plugin.getLogger().info("Successfully authenticated and loaded ZenFights global Discord Gateway Connection.");
            } catch (Exception e) {
                plugin.getLogger().severe("Fatal error connecting to Discord Application programming gateway: " + e.getMessage());
            }
        });
    }

    public void stopBot() {
        if (this.jda != null) {
            this.jda.shutdownNow();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("zfbot-top")) {
            event.deferReply().queue();

            ConfigurationSection stats = plugin.getConfig().getConfigurationSection("stats");
            if (stats == null || stats.getKeys(false).isEmpty()) {
                event.getHook().sendMessage("❌ There are currently no active player files recorded inside the server database.").queue();
                return;
            }

            Map<UUID, Integer> leaderboardMap = new HashMap<>();
            for (String key : stats.getKeys(false)) {
                try { leaderboardMap.put(UUID.fromString(key), stats.getInt(key)); } catch (IllegalArgumentException ignored) {}
            }

            List<Map.Entry<UUID, Integer>> sorted = leaderboardMap.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(10)
                    .collect(Collectors.toList());

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("🏆 ZenFights Absolute Top 10 Masters");
            embed.setColor(Color.decode(plugin.getConfig().getString("discord-bot.embed-color-leaderboard", "#FFD700")));
            embed.setThumbnail("https://minotar.net/helm/Steve/100.png");

            StringBuilder sb = new StringBuilder();
            int index = 1;
            for (Map.Entry<UUID, Integer> entry : sorted) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                String name = op.getName() != null ? op.getName() : "UnknownPlayer";
                sb.append("`#").append(index).append("` **").append(name).append("** » `").append(entry.getValue()).append(" Zen` \n");
                index++;
            }
            embed.setDescription(sb.toString());
            embed.setFooter("Live Arena Engine Tracking Protocol • Sync Verified", event.getJDA().getSelfUser().getEffectiveAvatarUrl());

            event.getHook().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        if (event.getName().equals("zfbot-stats")) {
            event.deferReply().queue();
            OptionMapping option = event.getOption("username");
            if (option == null) {
                event.getHook().sendMessage("❌ Argument exception parameter error.").queue();
                return;
            }

            String targetName = option.getAsString();
            ConfigurationSection stats = plugin.getConfig().getConfigurationSection("stats");

            UUID matchUUID = null;
            int currentZen = plugin.getConfig().getInt("default-zen", 3000);

            if (stats != null) {
                for (String key : stats.getKeys(false)) {
                    try {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(key));
                        if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) {
                            matchUUID = UUID.fromString(key);
                            currentZen = stats.getInt(key);
                            targetName = op.getName();
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (matchUUID == null) {
                event.getHook().sendMessage("❌ Unable to trace player match indices for `" + targetName + "` on the filesystem data logs.").queue();
                return;
            }

            List<String> history = plugin.getConfig().getStringList("history." + matchUUID.toString());
            StringBuilder historyStr = new StringBuilder();
            if (history == null || history.isEmpty()) {
                historyStr.append("*No recent combat log history indexed inside active tables.*");
            } else {
                for (String log : history) {
                    historyStr.append(log.startsWith("WIN") ? "🟢 " : "🔴 ").append("`").append(log).append("`\n");
                }
            }

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("⚔️ Combat Diagnostics Profile: " + targetName);
            embed.setColor(Color.decode(plugin.getConfig().getString("discord-bot.embed-color-stats", "#00FF7F")));
            embed.setThumbnail("https://minotar.net/helm/" + targetName + "/100.png");

            embed.addField("Current Rating Metrics", "• **Zen Level:** `" + currentZen + "`", false);
            embed.addField("Recent Activity Log Tracker (Last 5)", historyStr.toString(), false);
            embed.setFooter("Data telemetry pipeline established", event.getJDA().getSelfUser().getEffectiveAvatarUrl());

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        }
    }
}