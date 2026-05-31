package com.mcdevlab.zenfights;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class DuelCombatListener implements Listener {

    private final ZenFights plugin;

    public DuelCombatListener(ZenFights plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawnImmunityProtection(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        SandboxManager.DuelSession session = plugin.getSandbox().getSession(player);
        if (session == null) return;

        if (System.currentTimeMillis() < session.immunityEndTime) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcPracticeStrikes(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Zombie npc && event.getDamager() instanceof Player attacker) {
            SandboxManager.DuelSession session = plugin.getSandbox().getSession(attacker);
            if (session != null && session.isPractice && session.npcEntity.equals(npc)) {
                double rawDamage = event.getFinalDamage();
                session.botVirtualHealth -= rawDamage;

                if (session.botVirtualHealth <= 0.0) {
                    event.setCancelled(true);
                    plugin.getSandbox().endPractice(attacker, true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreDeathDamageInterception(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        SandboxManager.DuelSession session = plugin.getSandbox().getSession(target);
        if (session == null) return;

        double finalDamage = event.getFinalDamage();

        if (target.getHealth() - finalDamage <= 0.0) {
            ItemStack main = target.getInventory().getItemInMainHand();
            ItemStack off = target.getInventory().getItemInOffHand();
            boolean hasTotem = (main != null && main.getType() == Material.TOTEM_OF_UNDYING) ||
                    (off != null && off.getType() == Material.TOTEM_OF_UNDYING);

            if (hasTotem) return;

            event.setCancelled(true);
            target.setHealth(20.0);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (session.isPractice) {
                    plugin.getSandbox().endPractice(target, false);
                } else {
                    Player winner = plugin.getSandbox().getOpponent(target);
                    if (winner != null) plugin.getSandbox().endDuel(winner, target);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemPopTracking(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        SandboxManager.DuelSession session = plugin.getSandbox().getSession(p);
        if (session == null) return;

        if (p.getUniqueId().equals(session.p1)) {
            session.p1Totems++;
        } else if (session.p2 != null && p.getUniqueId().equals(session.p2)) {
            session.p2Totems++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLateJoinVisibilityFilter(PlayerJoinEvent event) {
        plugin.getSandbox().handleNewPlayerVisibility(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRageQuitInterception(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (plugin.getSandbox().isSpectating(p)) {
            plugin.getSandbox().stopSpectating(p);
            return;
        }

        SandboxManager.DuelSession session = plugin.getSandbox().getSession(p);
        if (session == null) return;

        if (session.isPractice) {
            plugin.getSandbox().endPractice(p, false);
        } else {
            Player opponent = plugin.getSandbox().getOpponent(p);
            if (opponent != null) {
                plugin.getSandbox().endDuel(opponent, p);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpectatorCommandFilter(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getSandbox().isSpectating(p)) return;

        if (p.hasPermission("zenfights.spectate.bypasscommands")) return;

        String cmd = event.getMessage().toLowerCase().trim();
        // Allow the exact spectator leave command routing to bypass the lock
        if (!cmd.equals("/fight spec exit") && !cmd.equals("/fight spectate exit")) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You cannot execute external commands while locked in spectator mode!");
            p.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.GREEN + "/fight spec exit" + ChatColor.YELLOW + " to disconnect.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnderChestInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getSandbox().isInFight(player)) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (event.getClickedBlock().getType() == Material.ENDER_CHEST) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot open Ender Chests during active combat duels!");
            }
        }
    }

    // --- UPDATED BOUNDARY CHECK (NOW INCLUDES SPECTATORS) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArenaBoundaryCheck(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        SandboxManager.DuelSession session = plugin.getSandbox().getSession(p);

        // If they aren't fighting, check if they are locked into spectating someone who IS fighting
        if (session == null && plugin.getSandbox().isSpectating(p)) {
            UUID targetId = plugin.getSandbox().getSpectatorTarget(p);
            if (targetId != null) {
                session = plugin.getSandbox().getSessionByUUID(targetId);
            }
        }

        if (session == null) return;

        Location to = event.getTo();
        if (to == null) return;

        double maxRadius = plugin.getConfig().getDouble("sandbox-radius", 32.0);
        Location center = session.center;

        if (Math.abs(to.getX() - center.getX()) > maxRadius || Math.abs(to.getZ() - center.getZ()) > maxRadius) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You cannot leave the localized sandbox boundary!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCollateralDamageProtection(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target) {
            boolean targetInFight = plugin.getSandbox().isInFight(target);
            if (event.getDamager() instanceof Player attacker) {
                if (targetInFight != plugin.getSandbox().isInFight(attacker)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSandboxBlockBreak(BlockBreakEvent event) {
        if (plugin.getSandbox().isInFight(event.getPlayer())) {
            Block block = event.getBlock();
            plugin.getSandbox().recordAndHideBlock(block.getLocation(), block.getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSandboxBlockPlace(BlockPlaceEvent event) {
        if (plugin.getSandbox().isInFight(event.getPlayer())) {
            plugin.getSandbox().recordAndHideBlock(event.getBlock().getLocation(), event.getBlockReplacedState().getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSandboxEntityExplosion(EntityExplodeEvent event) {
        if (plugin.getSandbox().hasActiveFights()) {
            for (Block block : event.blockList()) {
                plugin.getSandbox().recordAndHideBlock(block.getLocation(), block.getBlockData());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSandboxBlockExplosion(BlockExplodeEvent event) {
        if (plugin.getSandbox().hasActiveFights()) {
            for (Block block : event.blockList()) {
                plugin.getSandbox().recordAndHideBlock(block.getLocation(), block.getBlockData());
            }
        }
    }
}