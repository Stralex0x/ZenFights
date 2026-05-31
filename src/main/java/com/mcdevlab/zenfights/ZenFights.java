package com.mcdevlab.zenfights;

import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

public class ZenFights extends JavaPlugin {

    private SandboxManager sandboxManager;
    private DiscordBotManager discordBotManager;

    @Override
    public void onEnable() {
        // Step 1: Cleanly copy the commented config.yml out of our JAR file
        // This will ONLY create the file if it doesn't already exist on the server.
        // It completely preserves all of your setup instructions and comments!
        saveDefaultConfig();

        // Step 2: Spin Up Core Framework Managers
        this.sandboxManager = new SandboxManager(this);
        this.discordBotManager = new DiscordBotManager(this);
        this.discordBotManager.startBot();

        // Step 3: Register Command Executors and Tab Completers
        CommandFight cmdFight = new CommandFight(this);
        CommandZF cmdZF = new CommandZF(this);

        getCommand("fight").setExecutor(cmdFight);
        getCommand("fight").setTabCompleter(cmdFight);
        getCommand("zf").setExecutor(cmdZF);
        getCommand("zf").setTabCompleter(cmdZF);

        // Step 4: Register Internal Spigot Event Listeners
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelCombatListener(this), this);

        // Step 5: Hook into PlaceholderAPI Engine Framework safely
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ZenFightsPlaceholders(this).register();
            getLogger().info("Successfully hooked into PlaceholderAPI and registered custom expansions!");
        } else {
            getLogger().warning("PlaceholderAPI was not found! Custom %zenfights_% placeholders will be unavailable.");
        }

        int pluginId = 31719;
        Metrics metrics = new Metrics(this, pluginId);

        printAsciiArt();
    }

    @Override
    public void onDisable() {
        // Step 1: Execute bulletproof fail-safe rollbacks
        // This instantly de-spawns remaining practice zombies and returns active spectators
        if (this.sandboxManager != null) {
            this.sandboxManager.shutdownCleanup();
        }

        // Step 2: Gracefully shut down the external Discord systems
        if (this.discordBotManager != null) {
            this.discordBotManager.stopBot();
        }

        getLogger().info("ZenFights Core Engine Module has been shut down successfully.");
    }

    public SandboxManager getSandbox() {
        return this.sandboxManager;
    }

    private void printAsciiArt() {
        getLogger().info("\n" +
                "███████╗███████╗██╗░██████╗░██╗░░██╗████████╗░██████╗\n" +
                "╚════██║██╔════╝██║██╔════╝░██║░░██║╚══██╔══╝██╔════╝\n" +
                "░░███╔═╝█████╗░░██║██║░░██╗░███████║░░░██║░░░╚█████╗░\n" +
                "██╔══╝░░██╔══╝░░██║██║░░╚██╗██╔══██║░░░██║░░░░╚═══██╗\n" +
                "███████╗██║░░░░░██║╚██████╔╝██║░░██║░░░██║░░░██████╔╝\n" +
                "╚══════╝╚═╝░░░░░╚═╝░╚═════╝░╚═╝░░╚═╝░░░╚═╝░░░╚═════╝░");
        getLogger().info("ZenFights Core Engine Module Initialized Successfully.");
    }
}
