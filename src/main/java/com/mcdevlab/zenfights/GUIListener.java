package com.mcdevlab.zenfights;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Collections;

public class GUIListener implements Listener {

    private final ZenFights plugin;
    private final String PREFIX = ChatColor.GOLD + "[ZenFights] " + ChatColor.RESET;

    public GUIListener(ZenFights plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the Kit Selection GUI for a player challenging someone else.
     */
    public static void open(Player challenger, String targetName) {
        // Create a standard chest inventory layout with 27 slots matching the string prefix check
        String title = "Select Kit: " + targetName;
        Inventory gui = Bukkit.createInventory(null, 27, title);

        ZenFights plugin = (ZenFights) Bukkit.getPluginManager().getPlugin("ZenFights");
        if (plugin == null) return;

        ConfigurationSection kits = plugin.getConfig().getConfigurationSection("kits");
        int slot = 10; // Start placing items in the middle row

        if (kits != null) {
            for (String kitName : kits.getKeys(false)) {
                if (slot > 16) break; // Keep items inside the middle row bounds

                // Get preview icon from kit item 0 or default to diamond sword
                ItemStack icon = plugin.getConfig().getItemStack("kits." + kitName + ".inv.0");
                if (icon == null || icon.getType() == Material.AIR) {
                    icon = new ItemStack(Material.DIAMOND_SWORD);
                } else {
                    icon = icon.clone();
                }

                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GREEN + kitName);
                    meta.setLore(Collections.singletonList(ChatColor.GRAY + "Click to issue challenge invitation."));
                    icon.setItemMeta(meta);
                }

                gui.setItem(slot, icon);
                slot++;
            }
        }

        // Fill empty slots with gray stained glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }

        challenger.openInventory(gui);
    }

    /**
     * Handles all click interactions inside the Kit Selection interface.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        String title = view.getTitle();

        // Target specifically our custom duel kit selection interface
        if (!title.startsWith("Select Kit: ")) {
            return;
        }

        // 1. Defensively lock down the menu: Cancel all clicks immediately
        event.setCancelled(true);

        // 2. Ignore invalid clicks outside the window bounds
        if (event.getClickedInventory() == null) {
            return;
        }

        Player challenger = (Player) event.getWhoClicked();

        // 3. Block any attempt to interact with the player's own bottom inventory while menu is open
        if (event.getClickedInventory().equals(challenger.getInventory())) {
            return;
        }

        // 4. Block malicious click types designed to bypass inventory cancellation mechanics
        if (event.getClick() == ClickType.SHIFT_LEFT ||
                event.getClick() == ClickType.SHIFT_RIGHT ||
                event.getClick() == ClickType.NUMBER_KEY ||
                event.getClick() == ClickType.DOUBLE_CLICK ||
                event.getClick() == ClickType.SWAP_OFFHAND) {
            return;
        }

        // 5. Verify that selected item actually exists and contains metadata
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        if (!clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }

        // 6. Safe runtime evaluation of data strings
        String kitName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        String targetName = title.replace("Select Kit: ", "");
        Player target = Bukkit.getPlayer(targetName);

        // Close the screen right before dispatching the request to prevent double-click glitches
        challenger.closeInventory();

        // 7. Verify target availability status
        if (target == null || !target.isOnline()) {
            challenger.sendMessage(PREFIX + ChatColor.RED + "That player went offline.");
            return;
        }

        // 8. Re-evaluate live combat states right before generating request payload
        if (plugin.getSandbox().isInFight(challenger)) {
            challenger.sendMessage(PREFIX + ChatColor.RED + "You cannot challenge someone because you are already in a fight.");
            return;
        }

        if (plugin.getSandbox().isInFight(target)) {
            challenger.sendMessage(PREFIX + ChatColor.RED + "That player is already in an active fight.");
            return;
        }

        // 9. Dispatch payload safely through the Sandbox manager core
        plugin.getSandbox().sendRequest(challenger, target, kitName);
    }

    /**
     * Prevents players from multi-slot dragging items to bypass click cancellation.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryView view = event.getView();
        if (view.getTitle().startsWith("Select Kit: ")) {
            event.setCancelled(true);
        }
    }
}