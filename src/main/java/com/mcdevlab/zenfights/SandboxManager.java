package com.mcdevlab.zenfights;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.*;

public class SandboxManager {
    private final ZenFights plugin;
    private final String PREFIX = ChatColor.GOLD + "[ZenFights] " + ChatColor.RESET;

    private final Map<UUID, DuelSession> activeSessions = new HashMap<>();
    private final Map<UUID, DuelRequest> pendingRequests = new HashMap<>();

    // Player State Backups
    private final Map<UUID, ItemStack[]> backupInv = new HashMap<>();
    private final Map<UUID, ItemStack[]> backupArmor = new HashMap<>();
    private final Map<UUID, ItemStack> backupOffhand = new HashMap<>();
    private final Map<UUID, Location> backupLoc = new HashMap<>();
    private final Map<Location, BlockData> modifiedBlocks = new HashMap<>();

    // Spectator Registries
    private final Map<UUID, UUID> spectatorTargets = new HashMap<>();
    private final Map<UUID, Location> backupSpecLoc = new HashMap<>();
    private final Map<UUID, GameMode> backupSpecMode = new HashMap<>();
    private final Map<UUID, ItemStack[]> backupSpecInv = new HashMap<>();
    private final Map<UUID, ItemStack[]> backupSpecArmor = new HashMap<>();
    private final Map<UUID, ItemStack> backupSpecOffhand = new HashMap<>();

    // Target -> (Requester -> Expiry Time)
    private final Map<UUID, Map<UUID, Long>> pendingSpecRequests = new HashMap<>();

    public SandboxManager(ZenFights plugin) {
        this.plugin = plugin;
    }

    public boolean isInFight(Player p) {
        return activeSessions.containsKey(p.getUniqueId());
    }

    public boolean isSpectating(Player p) {
        return spectatorTargets.containsKey(p.getUniqueId());
    }

    public boolean hasActiveFights() {
        return !activeSessions.isEmpty();
    }

    public DuelSession getSession(Player p) {
        return activeSessions.get(p.getUniqueId());
    }

    public DuelSession getSessionByUUID(UUID uuid) {
        return activeSessions.get(uuid);
    }

    public UUID getSpectatorTarget(Player p) {
        return spectatorTargets.get(p.getUniqueId());
    }

    public void sendRequest(Player challenger, Player target, String kitName) {
        DuelRequest request = new DuelRequest(challenger.getUniqueId(), kitName);
        pendingRequests.put(target.getUniqueId(), request);

        challenger.sendMessage(PREFIX + ChatColor.GREEN + "Duel request sent to " + target.getName() + " with kit " + kitName + ".");
        target.sendMessage(PREFIX + ChatColor.GOLD + challenger.getName() + ChatColor.YELLOW + " has challenged you to a duel with kit " + ChatColor.GREEN + kitName + ChatColor.YELLOW + "!");
        target.sendMessage(PREFIX + ChatColor.YELLOW + "Type " + ChatColor.GREEN + "/fight accept " + challenger.getName() + ChatColor.YELLOW + " to accept.");
    }

    public void acceptRequest(Player target, Player challenger) {
        DuelRequest request = pendingRequests.get(target.getUniqueId());
        if (request == null || !request.challenger().equals(challenger.getUniqueId())) {
            target.sendMessage(PREFIX + ChatColor.RED + "You do not have a pending request from this player.");
            return;
        }
        pendingRequests.remove(target.getUniqueId());
        startDuel(challenger, target, request.kitName());
    }

    // --- NEW SPECTATOR LOGIC ---
    public void requestSpectate(Player requester, Player target) {
        if (!isInFight(target)) {
            requester.sendMessage(PREFIX + ChatColor.RED + "That player is not currently inside an active fight.");
            return;
        }
        if (requester.equals(target)) {
            requester.sendMessage(PREFIX + ChatColor.RED + "You cannot spectate yourself!");
            return;
        }
        if (isInFight(requester)) {
            requester.sendMessage(PREFIX + ChatColor.RED + "You cannot spectate someone else while processing your own match!");
            return;
        }
        if (isSpectating(requester)) {
            requester.sendMessage(PREFIX + ChatColor.RED + "You are already spectating a match!");
            return;
        }

        pendingSpecRequests.putIfAbsent(target.getUniqueId(), new HashMap<>());
        pendingSpecRequests.get(target.getUniqueId()).put(requester.getUniqueId(), System.currentTimeMillis() + 60000L);

        requester.sendMessage(PREFIX + ChatColor.GREEN + "Spectate request sent to " + target.getName() + ". Waiting for them to accept...");
        target.sendMessage(PREFIX + ChatColor.GOLD + requester.getName() + ChatColor.YELLOW + " wants to spectate your match!");
        target.sendMessage(PREFIX + ChatColor.YELLOW + "Type " + ChatColor.GREEN + "/fight spec accept " + requester.getName() + ChatColor.YELLOW + " to allow them.");
    }

    public void acceptSpectate(Player target, Player requester) {
        Map<UUID, Long> requests = pendingSpecRequests.get(target.getUniqueId());
        if (requests == null || !requests.containsKey(requester.getUniqueId())) {
            target.sendMessage(PREFIX + ChatColor.RED + "You do not have a pending spectate request from this player.");
            return;
        }
        if (System.currentTimeMillis() > requests.get(requester.getUniqueId())) {
            target.sendMessage(PREFIX + ChatColor.RED + "That spectate request has expired.");
            requests.remove(requester.getUniqueId());
            return;
        }

        requests.remove(requester.getUniqueId());
        startSpectating(requester, target);
    }

    public void startSpectating(Player spectator, Player target) {
        DuelSession session = getSession(target);
        if (session == null) {
            spectator.sendMessage(PREFIX + ChatColor.RED + "That player's match has already ended.");
            return;
        }

        UUID specId = spectator.getUniqueId();

        // Backup State
        backupSpecLoc.put(specId, spectator.getLocation());
        backupSpecMode.put(specId, spectator.getGameMode());
        backupSpecInv.put(specId, spectator.getInventory().getStorageContents());
        backupSpecArmor.put(specId, spectator.getInventory().getArmorContents());
        backupSpecOffhand.put(specId, spectator.getInventory().getItemInOffHand());

        // Clear Inventory completely
        spectator.getInventory().clear();
        spectator.getInventory().setArmorContents(null);
        spectator.getInventory().setItemInOffHand(null);
        spectator.updateInventory();

        spectatorTargets.put(specId, target.getUniqueId());
        session.spectators.add(specId);

        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.teleport(target.getLocation());

        // Set World Border for Spectator
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(session.center);
        border.setSize(plugin.getConfig().getDouble("sandbox-radius", 32.0) * 2.0);
        spectator.setWorldBorder(border);

        // Network Packets
        Player p1 = Bukkit.getPlayer(session.p1);
        Player p2 = session.p2 != null ? Bukkit.getPlayer(session.p2) : null;

        if (p1 != null) { p1.hidePlayer(plugin, spectator); spectator.showPlayer(plugin, p1); }
        if (p2 != null) { p2.hidePlayer(plugin, spectator); spectator.showPlayer(plugin, p2); }
        if (session.isPractice && session.npcEntity != null) { spectator.showEntity(plugin, session.npcEntity); }

        spectator.sendMessage(PREFIX + ChatColor.GREEN + "Now spectating the match environment. Type " + ChatColor.GOLD + "/fight spec exit" + ChatColor.GREEN + " to safely disconnect.");
        if (p1 != null) p1.sendMessage(PREFIX + ChatColor.AQUA + spectator.getName() + " has joined as a spectator.");
        if (p2 != null) p2.sendMessage(PREFIX + ChatColor.AQUA + spectator.getName() + " has joined as a spectator.");
    }

    public void stopSpectating(Player spectator) {
        UUID specId = spectator.getUniqueId();
        UUID targetId = spectatorTargets.remove(specId);
        if (targetId == null) return;

        for (DuelSession s : activeSessions.values()) {
            s.spectators.remove(specId);
        }

        // Restore Locations & Gamemode
        spectator.setGameMode(backupSpecMode.getOrDefault(specId, GameMode.SURVIVAL));
        Location returnLoc = backupSpecLoc.remove(specId);
        if (returnLoc != null) spectator.teleport(returnLoc);
        backupSpecMode.remove(specId);

        // Remove Border
        spectator.setWorldBorder(null);

        // Restore Inventory
        spectator.getInventory().clear();
        if (backupSpecInv.containsKey(specId)) spectator.getInventory().setStorageContents(backupSpecInv.remove(specId));
        if (backupSpecArmor.containsKey(specId)) spectator.getInventory().setArmorContents(backupSpecArmor.remove(specId));
        if (backupSpecOffhand.containsKey(specId)) spectator.getInventory().setItemInOffHand(backupSpecOffhand.remove(specId));
        spectator.updateInventory();

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, spectator);
            spectator.showPlayer(plugin, online);
        }

        spectator.sendMessage(PREFIX + ChatColor.YELLOW + "You have left the spectator perspective view. Your inventory has been restored.");
    }
    // --- END SPECTATOR LOGIC ---

    public void startPractice(Player p, String kitName) {
        saveState(p);
        Location center = p.getLocation().clone();
        preparePlayer(p);
        applyKit(p, kitName);

        double radius = plugin.getConfig().getDouble("sandbox-radius", 32.0);
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(center);
        border.setSize(radius * 2.0);
        p.setWorldBorder(border);

        Location npcLoc = center.clone().add(center.getDirection().multiply(6));
        npcLoc.setY(center.getWorld().getHighestBlockYAt(npcLoc) + 1);

        Zombie npc = (Zombie) center.getWorld().spawnEntity(npcLoc, EntityType.ZOMBIE);
        npc.setCustomName(ChatColor.RED + "Zen Master (Practice Bot)");
        npc.setCustomNameVisible(true);
        npc.setBaby(false);
        npc.setSilent(true);
        npc.setTarget(p);

        // Handling the Attribute cross-version mapping correctly
        AttributeInstance maxHealthAttr;
        try {
            maxHealthAttr = npc.getAttribute(Attribute.valueOf("MAX_HEALTH"));
        } catch (IllegalArgumentException e) {
            maxHealthAttr = npc.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH"));
        }

        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(1024.0);
            npc.setHealth(1024.0);
        }

        npc.getEquipment().setHelmetDropChance(0.0f);
        npc.getEquipment().setChestplateDropChance(0.0f);
        npc.getEquipment().setLeggingsDropChance(0.0f);
        npc.getEquipment().setBootsDropChance(0.0f);
        npc.getEquipment().setItemInMainHandDropChance(0.0f);
        npc.getEquipment().setItemInOffHandDropChance(0.0f);

        ItemStack helmetItem = plugin.getConfig().getItemStack("kits." + kitName + ".armor.3");
        if (helmetItem == null || helmetItem.getType() == Material.AIR) {
            helmetItem = new ItemStack(Material.NETHERITE_HELMET);
        }
        npc.getEquipment().setHelmet(helmetItem);
        npc.getEquipment().setChestplate(plugin.getConfig().getItemStack("kits." + kitName + ".armor.2"));
        npc.getEquipment().setLeggings(plugin.getConfig().getItemStack("kits." + kitName + ".armor.1"));
        npc.getEquipment().setBoots(plugin.getConfig().getItemStack("kits." + kitName + ".armor.0"));
        npc.getEquipment().setItemInMainHand(plugin.getConfig().getItemStack("kits." + kitName + ".inv.0"));
        npc.getEquipment().setItemInOffHand(plugin.getConfig().getItemStack("kits." + kitName + ".offhand"));

        npc.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));

        long immunityEnd = System.currentTimeMillis() + 3000L;
        int timeoutId = Bukkit.getScheduler().runTaskLater(plugin, () -> timeoutDuel(p.getUniqueId(), null), plugin.getConfig().getInt("match-timeout-minutes", 20) * 60L * 20L).getTaskId();

        DuelSession session = new DuelSession(p.getUniqueId(), null, center, timeoutId, immunityEnd, kitName);
        session.isPractice = true;
        session.npcEntity = npc;
        session.botVirtualHealth = 2000.0;

        int npcTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!npc.isValid() || !p.isOnline()) return;
            if (npc.getTarget() == null || !npc.getTarget().equals(p)) {
                npc.setTarget(p);
            }
            if (npc.getHealth() < 800.0) {
                npc.setHealth(1024.0);
            }

            String hpBar = ChatColor.RED + "ZenBot: " + ChatColor.WHITE + (int)session.botVirtualHealth + "/2000 HP";
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(hpBar));
            for (UUID specId : session.spectators) {
                Player spec = Bukkit.getPlayer(specId);
                spec.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(hpBar));
            }
        }, 0L, 10L).getTaskId();

        session.npcAiTaskId = npcTaskId;
        activeSessions.put(p.getUniqueId(), session);

        enforceBotBoundaries(npc, p, center, radius);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(p)) continue;
            online.hidePlayer(plugin, p);
            online.hideEntity(plugin, npc);
            p.hidePlayer(plugin, online);
        }

        runMatchStartCountdown(session);
    }

    private void startDuel(Player p1, Player p2, String kitName) {
        saveState(p1);
        saveState(p2);

        Location duelCenter = p1.getLocation().clone();
        p2.teleport(duelCenter.clone().add(2, 0, 2));

        preparePlayer(p1);
        preparePlayer(p2);
        applyKit(p1, kitName);
        applyKit(p2, kitName);

        double radius = plugin.getConfig().getDouble("sandbox-radius", 32.0);
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(duelCenter);
        border.setSize(radius * 2.0);
        p1.setWorldBorder(border);
        p2.setWorldBorder(border);

        long immunityEnd = System.currentTimeMillis() + 3000L;
        long runTicks = plugin.getConfig().getInt("match-timeout-minutes", 20) * 60L * 20L;

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> timeoutDuel(p1.getUniqueId(), p2.getUniqueId()), runTicks).getTaskId();

        DuelSession session = new DuelSession(p1.getUniqueId(), p2.getUniqueId(), duelCenter, taskId, immunityEnd, kitName);
        activeSessions.put(p1.getUniqueId(), session);
        activeSessions.put(p2.getUniqueId(), session);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(p1) || online.equals(p2)) continue;
            online.hidePlayer(plugin, p1);
            online.hidePlayer(plugin, p2);
            p1.hidePlayer(plugin, online);
            p2.hidePlayer(plugin, online);
        }

        runMatchStartCountdown(session);
    }

    private void runMatchStartCountdown(DuelSession session) {
        Player p1 = Bukkit.getPlayer(session.p1);
        Player p2 = session.p2 != null ? Bukkit.getPlayer(session.p2) : null;

        new org.bukkit.scheduler.BukkitRunnable() {
            int count = 3;
            @Override
            public void run() {
                if (count > 0) {
                    sendTitleToMatch(session, ChatColor.GOLD + String.valueOf(count), ChatColor.YELLOW + "Prepare your hotbar pacing...", 0, 22, 0);
                    playSoundToMatch(session, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                } else {
                    sendTitleToMatch(session, ChatColor.GREEN + "GO!", ChatColor.WHITE + "Match initialized!", 0, 25, 5);
                    playSoundToMatch(session, Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.4f);
                    this.cancel();
                    return;
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void sendTitleToMatch(DuelSession s, String title, String sub, int fadeIn, int stay, int fadeOut) {
        Player p1 = Bukkit.getPlayer(s.p1);
        if (p1 != null && p1.isOnline()) p1.sendTitle(title, sub, fadeIn, stay, fadeOut);
        if (s.p2 != null) {
            Player p2 = Bukkit.getPlayer(s.p2);
            if (p2 != null && p2.isOnline()) p2.sendTitle(title, sub, fadeIn, stay, fadeOut);
        }
        for (UUID specId : s.spectators) {
            Player spec = Bukkit.getPlayer(specId);
            if (spec != null && spec.isOnline()) spec.sendTitle(title, sub, fadeIn, stay, fadeOut);
        }
    }

    private void playSoundToMatch(DuelSession s, Sound sound, float vol, float pitch) {
        Player p1 = Bukkit.getPlayer(s.p1);
        if (p1 != null && p1.isOnline()) p1.playSound(p1.getLocation(), sound, vol, pitch);
        if (s.p2 != null) {
            Player p2 = Bukkit.getPlayer(s.p2);
            if (p2 != null && p2.isOnline()) p2.playSound(p2.getLocation(), sound, vol, pitch);
        }
        for (UUID specId : s.spectators) {
            Player spec = Bukkit.getPlayer(specId);
            if (spec != null && spec.isOnline()) spec.playSound(spec.getLocation(), sound, vol, pitch);
        }
    }

    public void endDuel(Player winner, Player loser) {
        if (winner == null || loser == null) return;

        DuelSession session = activeSessions.remove(winner.getUniqueId());
        activeSessions.remove(loser.getUniqueId());

        if (session != null) {
            Bukkit.getScheduler().cancelTask(session.taskId);
            for (UUID specId : new HashSet<>(session.spectators)) {
                Player spec = Bukkit.getPlayer(specId);
                if (spec != null) stopSpectating(spec);
            }
        }

        winner.setWorldBorder(null);
        loser.setWorldBorder(null);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, winner);
            online.showPlayer(plugin, loser);
            winner.showPlayer(plugin, online);
            loser.showPlayer(plugin, online);
        }

        int baseWinner = plugin.getConfig().getInt("stats." + winner.getUniqueId(), plugin.getConfig().getInt("default-zen", 3000));
        int baseLoser = plugin.getConfig().getInt("stats." + loser.getUniqueId(), plugin.getConfig().getInt("default-zen", 3000));

        int zenShift = calculateZenShift(baseWinner, baseLoser);
        int finalWinnerZen = baseWinner + zenShift;
        int finalLoserZen = Math.max(0, baseLoser - zenShift);

        plugin.getConfig().set("stats." + winner.getUniqueId(), finalWinnerZen);
        plugin.getConfig().set("stats." + loser.getUniqueId(), finalLoserZen);

        long durationSec = (System.currentTimeMillis() - (session != null ? session.startTime : System.currentTimeMillis())) / 1000L;
        int totemsPopped = (session != null) ? (session.p1.equals(loser.getUniqueId()) ? session.p1Totems : session.p2Totems) : 0;

        logMatchHistory(winner.getUniqueId(), "WIN vs " + loser.getName() + " (+" + zenShift + " Zen) [" + (durationSec / 60) + "m " + (durationSec % 60) + "s]");
        logMatchHistory(loser.getUniqueId(), "LOSS vs " + winner.getName() + " (-" + zenShift + " Zen) [" + (durationSec / 60) + "m " + (durationSec % 60) + "s]");
        plugin.saveConfig();

        String breakdown = ChatColor.GOLD + "=== Match Performance Breakdown ===\n" +
                ChatColor.YELLOW + "Duration: " + ChatColor.GREEN + (durationSec / 60) + "m " + (durationSec % 60) + "s\n" +
                ChatColor.YELLOW + "Loser Totems Popped: " + ChatColor.RED + totemsPopped + "\n" +
                ChatColor.YELLOW + "Your Zen Adjustment: " + ChatColor.GOLD + baseWinner + " ➔ " + finalWinnerZen + " (+" + zenShift + ")\n";

        winner.sendMessage(breakdown);
        loser.sendMessage(breakdown.replace("+" + zenShift, "-" + zenShift).replace(baseWinner + " ➔ " + finalWinnerZen, baseLoser + " ➔ " + finalLoserZen));

        TextComponent rematchBtn = new TextComponent("[CLICK HERE TO INSTANTLY REMATCH]");
        rematchBtn.setColor(ChatColor.GREEN);
        rematchBtn.setBold(true);
        rematchBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fight " + loser.getName()));
        rematchBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to instantly send another challenge invitation!").color(ChatColor.GRAY).create()));

        winner.spigot().sendMessage(rematchBtn);
        loser.spigot().sendMessage(rematchBtn);

        rollbackSandbox();
        restoreState(winner);
        restoreState(loser);
        preparePlayer(winner);
        preparePlayer(loser);

        executeWinCommands(winner);
    }

    public void endPractice(Player p, boolean playerWon) {
        DuelSession session = activeSessions.remove(p.getUniqueId());
        if (session == null) return;

        Bukkit.getScheduler().cancelTask(session.taskId);
        Bukkit.getScheduler().cancelTask(session.npcAiTaskId);
        if (session.npcEntity != null) session.npcEntity.remove();

        for (UUID specId : new HashSet<>(session.spectators)) {
            Player spec = Bukkit.getPlayer(specId);
            if (spec != null) stopSpectating(spec);
        }

        p.setWorldBorder(null);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, p);
            p.showPlayer(plugin, online);
        }

        rollbackSandbox();
        restoreState(p);
        preparePlayer(p);

        long durationSec = (System.currentTimeMillis() - session.startTime) / 1000L;

        if (playerWon) {
            p.sendMessage(ChatColor.GOLD + "=== Practice Match Performance Breakdown ===\n" +
                    ChatColor.YELLOW + "Result: " + ChatColor.GREEN + "VICTORY\n" +
                    ChatColor.YELLOW + "Duration: " + ChatColor.GREEN + (durationSec / 60) + "m " + (durationSec % 60) + "s\n" +
                    ChatColor.YELLOW + "Your Totems Popped: " + ChatColor.RED + session.p1Totems + "\n" +
                    ChatColor.GRAY + "No Zen modifications track across practice routines.\n");
        } else {
            p.sendMessage(ChatColor.GOLD + "=== Practice Match Performance Breakdown ===\n" +
                    ChatColor.YELLOW + "Result: " + ChatColor.RED + "DEFEAT\n" +
                    ChatColor.YELLOW + "Duration: " + ChatColor.GREEN + (durationSec / 60) + "m " + (durationSec % 60) + "s\n" +
                    ChatColor.YELLOW + "Your Totems Popped: " + ChatColor.RED + session.p1Totems + "\n" +
                    ChatColor.GRAY + "Keep practicing to refine your pacing!\n");
        }
    }

    public void handleNewPlayerVisibility(Player joined) {
        for (DuelSession session : activeSessions.values()) {
            Player p1 = Bukkit.getPlayer(session.p1);
            if (p1 != null && p1.isOnline()) { joined.hidePlayer(plugin, p1); p1.hidePlayer(plugin, joined); }
            if (session.p2 != null) {
                Player p2 = Bukkit.getPlayer(session.p2);
                if (p2 != null && p2.isOnline()) { joined.hidePlayer(plugin, p2); p2.hidePlayer(plugin, joined); }
            }
            if (session.isPractice && session.npcEntity != null && session.npcEntity.isValid()) {
                joined.hideEntity(plugin, session.npcEntity);
            }
        }
    }

    public void shutdownCleanup() {
        for (DuelSession session : new ArrayList<>(activeSessions.values())) {
            if (session.isPractice && session.npcEntity != null) {
                session.npcEntity.remove();
            }
            for (UUID specId : new HashSet<>(session.spectators)) {
                Player spec = Bukkit.getPlayer(specId);
                if (spec != null) stopSpectating(spec);
            }
        }
        for (UUID specId : new HashSet<>(spectatorTargets.keySet())) {
            Player spec = Bukkit.getPlayer(specId);
            if (spec != null) stopSpectating(spec);
        }
        rollbackSandbox();
    }

    private void timeoutDuel(UUID id1, UUID id2) {
        DuelSession s1 = activeSessions.remove(id1);
        if (id2 != null) activeSessions.remove(id2);

        if (s1 != null) {
            if (s1.isPractice) {
                Bukkit.getScheduler().cancelTask(s1.npcAiTaskId);
                if (s1.npcEntity != null) s1.npcEntity.remove();
            }
            for (UUID specId : new HashSet<>(s1.spectators)) {
                Player spec = Bukkit.getPlayer(specId);
                if (spec != null) stopSpectating(spec);
            }
        }

        rollbackSandbox();
        Player p1 = Bukkit.getPlayer(id1);
        Player p2 = (id2 != null) ? Bukkit.getPlayer(id2) : null;

        if (p1 != null) {
            p1.setWorldBorder(null); restoreState(p1); preparePlayer(p1);
            for (Player o : Bukkit.getOnlinePlayers()) { o.showPlayer(plugin, p1); p1.showPlayer(plugin, o); }
            p1.sendMessage(PREFIX + ChatColor.RED + "Session Timed Out. No modifications saved.");
        }
        if (p2 != null) {
            p2.setWorldBorder(null); restoreState(p2); preparePlayer(p2);
            for (Player o : Bukkit.getOnlinePlayers()) { o.showPlayer(plugin, p2); p2.showPlayer(plugin, o); }
            p2.sendMessage(PREFIX + ChatColor.RED + "Session Timed Out. No modifications saved.");
        }
    }

    private int calculateZenShift(int winnerZen, int loserZen) {
        double k = plugin.getConfig().getDouble("zen-k-factor", 32.0);
        double expected = 1.0 / (1.0 + Math.pow(10, (loserZen - winnerZen) / 400.0));
        return (int) Math.round(k * (1.0 - expected));
    }

    public void recordAndHideBlock(Location loc, BlockData originalData) {
        if (!modifiedBlocks.containsKey(loc)) modifiedBlocks.put(loc, originalData);
        for (Player bystander : loc.getWorld().getPlayers()) {
            if (!isInFight(bystander) && !isSpectating(bystander) && bystander.getLocation().distanceSquared(loc) < 4096) {
                bystander.sendBlockChange(loc, originalData);
            }
        }
    }

    private void rollbackSandbox() {
        for (Map.Entry<Location, BlockData> entry : modifiedBlocks.entrySet()) {
            Location loc = entry.getKey();
            BlockData originalData = entry.getValue();
            loc.getBlock().setBlockData(originalData, true);
            for (Player player : loc.getWorld().getPlayers()) player.sendBlockChange(loc, originalData);
        }
        modifiedBlocks.clear();
    }

    private void saveState(Player p) {
        UUID uuid = p.getUniqueId();
        backupInv.put(uuid, p.getInventory().getStorageContents());
        backupArmor.put(uuid, p.getInventory().getArmorContents());
        backupOffhand.put(uuid, p.getInventory().getItemInOffHand());
        backupLoc.put(uuid, p.getLocation());
    }

    private void restoreState(Player p) {
        UUID uuid = p.getUniqueId();
        p.getInventory().clear();
        if (backupInv.containsKey(uuid)) p.getInventory().setStorageContents(backupInv.remove(uuid));
        if (backupArmor.containsKey(uuid)) p.getInventory().setArmorContents(backupArmor.remove(uuid));
        if (backupOffhand.containsKey(uuid)) p.getInventory().setItemInOffHand(backupOffhand.remove(uuid));
        if (backupLoc.containsKey(uuid)) p.teleport(backupLoc.remove(uuid));
    }

    public void applyKit(Player p, String kitName) {
        p.getInventory().clear();
        for (int i = 0; i < 36; i++) {
            ItemStack item = plugin.getConfig().getItemStack("kits." + kitName + ".inv." + i);
            if (item != null) p.getInventory().setItem(i, item);
        }
        ItemStack[] armor = p.getInventory().getArmorContents();
        for (int i = 0; i < 4; i++) armor[i] = plugin.getConfig().getItemStack("kits." + kitName + ".armor." + i);
        p.getInventory().setArmorContents(armor);
        ItemStack offhand = plugin.getConfig().getItemStack("kits." + kitName + ".offhand");
        if (offhand != null) p.getInventory().setItemInOffHand(offhand);
        p.updateInventory();
    }

    public void preparePlayer(Player p) {
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFreezeTicks(0);
        p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
    }

    public Player getOpponent(Player p) {
        DuelSession session = activeSessions.get(p.getUniqueId());
        if (session == null || session.isPractice) return null;
        return Bukkit.getPlayer(session.p1.equals(p.getUniqueId()) ? session.p2 : session.p1);
    }

    private void executeWinCommands(Player winner) {
        List<String> commands = plugin.getConfig().getStringList("win-commands");
        if (commands == null || commands.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String cmd : commands) {
                String parsedCmd = cmd.replace("%player%", winner.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
            }
        });
    }

    private void enforceBotBoundaries(org.bukkit.entity.LivingEntity bot, org.bukkit.entity.Player player, org.bukkit.Location centerPoint, double maxRadius) {
        bot.setRemoveWhenFarAway(false);
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (bot == null || !bot.isValid() || player == null || !player.isOnline()) {
                    if (bot != null && bot.isValid()) bot.remove();
                    this.cancel();
                    return;
                }
                if (bot instanceof org.bukkit.entity.Monster monster && monster.getTarget() != player) {
                    monster.setTarget(player);
                }
                double dx = bot.getLocation().getX() - centerPoint.getX();
                double dz = bot.getLocation().getZ() - centerPoint.getZ();
                double distanceSquared = (dx * dx) + (dz * dz);
                if (distanceSquared > (maxRadius * maxRadius)) {
                    org.bukkit.util.Vector toCenter = centerPoint.toVector().subtract(bot.getLocation().toVector());
                    toCenter.setY(0);
                    bot.setVelocity(toCenter.normalize().multiply(0.6).setY(0.2));
                    bot.getWorld().playSound(bot.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 0.4f, 1.2f);
                    bot.getWorld().spawnParticle(org.bukkit.Particle.CRIT, bot.getLocation().add(0, 1, 0), 5, 0.2, 0.5, 0.2, 0.05);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void logMatchHistory(UUID uuid, String entry) {
        List<String> history = plugin.getConfig().getStringList("history." + uuid.toString());
        List<String> mutableHistory = new ArrayList<>(history);
        mutableHistory.add(entry);
        plugin.getConfig().set("history." + uuid.toString(), mutableHistory);
    }

    public String getMatchHistoryEntry(UUID uuid) {
        DuelSession session = activeSessions.get(uuid);
        return session != null ? session.kitName : null;
    }

    private record DuelRequest(UUID challenger, String kitName) {}

    public static class DuelSession {
        public final UUID p1;
        public final UUID p2;
        public final Location center;
        public final int taskId;
        public final long immunityEndTime;
        public final long startTime;
        public final String kitName;
        public final Set<UUID> spectators = new HashSet<>();
        public int p1Totems = 0;
        public int p2Totems = 0;

        public boolean isPractice = false;
        public Zombie npcEntity = null;
        public int npcAiTaskId = -1;
        public double botVirtualHealth = 2000.0;

        public DuelSession(UUID p1, UUID p2, Location center, int taskId, long immunityEndTime, String kitName) {
            this.p1 = p1;
            this.p2 = p2;
            this.center = center;
            this.taskId = taskId;
            this.immunityEndTime = immunityEndTime;
            this.startTime = System.currentTimeMillis();
            this.kitName = kitName;
        }
    }
}