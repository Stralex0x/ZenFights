package com.mcdevlab.zenfights;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class KitStatsManager {

    private final ZenFights plugin;

    public KitStatsManager(ZenFights plugin) {
        this.plugin = plugin;
    }

    // --- KIT MANAGEMENT ---

    public void saveKit(String name, Player p) {
        // Saves the main inventory (including hotbar and offhand) and armor
        plugin.getConfig().set("kits." + name.toLowerCase() + ".inventory", p.getInventory().getContents());
        plugin.getConfig().set("kits." + name.toLowerCase() + ".armor", p.getInventory().getArmorContents());
        plugin.saveConfig();
    }

    public void deleteKit(String name) {
        plugin.getConfig().set("kits." + name.toLowerCase(), null);
        plugin.saveConfig();
    }

    public void applyKit(Player p, String name) {
        String path = "kits." + name.toLowerCase();

        if (!plugin.getConfig().contains(path)) {
            p.sendMessage(ChatColor.RED + "That kit does not exist!");
            return;
        }

        // Clear their current inventory before applying the kit
        p.getInventory().clear();

        // Fetch arrays from the config. (Bukkit handles ItemStack serialization automatically)
        List<?> inventoryList = plugin.getConfig().getList(path + ".inventory");
        List<?> armorList = plugin.getConfig().getList(path + ".armor");

        if (inventoryList != null) {
            p.getInventory().setContents(inventoryList.toArray(new ItemStack[0]));
        }
        if (armorList != null) {
            p.getInventory().setArmorContents(armorList.toArray(new ItemStack[0]));
        }

        p.updateInventory(); // Force update so the client sees the items immediately
    }

    // --- ZEN STATS MANAGEMENT ---

    public int getZen(Player p) {
        // Fetches the UUID from the config. If they don't exist yet, it defaults to +3000.
        return plugin.getConfig().getInt("stats." + p.getUniqueId().toString(), 3000);
    }

    public void addZen(Player p, int amount) {
        int currentZen = getZen(p);
        int newZen = currentZen + amount;

        // Save the new value back to the config
        plugin.getConfig().set("stats." + p.getUniqueId().toString(), newZen);
        plugin.saveConfig();
    }
}