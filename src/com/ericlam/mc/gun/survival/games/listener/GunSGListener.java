package com.ericlam.mc.gun.survival.games.listener;

import com.ericlam.mc.gun.survival.games.main.GunSG;
import com.ericlam.mc.minigames.core.character.GamePlayer;
import com.ericlam.mc.minigames.core.event.player.CrackShotDeathEvent;
import com.ericlam.mc.minigames.core.event.player.GamePlayerDeathEvent;
import com.ericlam.mc.minigames.core.event.player.GamePlayerJoinEvent;
import com.ericlam.mc.minigames.core.event.player.GamePlayerQuitEvent;
import com.ericlam.mc.minigames.core.game.GameState;
import com.ericlam.mc.minigames.core.main.MinigamesCore;
import com.ericlam.mc.minigames.core.manager.PlayerManager;
import com.hypernite.mc.hnmc.core.managers.ConfigManager;
import com.shampaggon.crackshot.CSDirector;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.golde.bukkit.corpsereborn.CorpseAPI.CorpseAPI;

import java.util.Locale;

public class GunSGListener implements Listener {

    @EventHandler
    public void onGamePlayerQuit(GamePlayerQuitEvent e) {
        GamePlayer gamePlayer = e.getGamePlayer();
        if (gamePlayer.getStatus() != GamePlayer.Status.GAMING) return;
        if (e.getGameState() != GameState.IN_GAME && e.getGameState() != GameState.PRESTART) return;
        Player player = gamePlayer.getPlayer();
        MinigamesCore.getApi().getGameStatsManager().addDeaths(gamePlayer, 1);
        Bukkit.broadcastMessage(GunSG.config().getMessage("quit-in-game").replace("<player>", player.getDisplayName()));
        GunSG.getPlugin(GunSG.class).getWantedManager().onBountyFail(player);
        handleDeath(gamePlayer);
    }

    private void handleDeath(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        PlayerInventory inventory = player.getInventory();
        if (GunSG.corpseEnabled) {
            CorpseAPI.spawnCorpse(player, player.getLocation(), inventory.getContents(), inventory.getHelmet(), inventory.getChestplate(), inventory.getLeggings(), inventory.getBoots(), inventory.getItemInMainHand());
        } else {
            final World world = player.getWorld();
            ItemStack[] items = inventory.getStorageContents();
            if (items.length > 0) {
                for (ItemStack item : items) {
                    if (item == null || item.getType() == Material.AIR) continue;
                    world.dropItem(player.getLocation(), item);
                }
            }
        }
        Location playerLoc = player.getLocation();
        player.getWorld().strikeLightningEffect(playerLoc);
        player.getWorld().playSound(playerLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 50, 1);
        MinigamesCore.getApi().getPlayerManager().setSpectator(gamePlayer);
        GunSG.getPlugin(GunSG.class).getWantedManager().removeGamer(gamePlayer);
        if (player.isOnGround()) player.sendTitle(GunSG.config().getPureMessage("die-title"), "", 20, 60, 20);
    }

    @EventHandler
    public void onSpectatorChat(AsyncPlayerChatEvent e) {
        PlayerManager playerManager = MinigamesCore.getApi().getPlayerManager();
        playerManager.findPlayer(e.getPlayer()).ifPresent(g -> {
            if (g.getStatus() != GamePlayer.Status.SPECTATING) return;
            e.getRecipients().removeIf(p -> playerManager.findPlayer(p).map(gx -> gx.getStatus() == GamePlayer.Status.GAMING).orElse(false));
            e.setFormat("§9觀戰§8//§r" + e.getFormat());
        });
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (GunSG.getPlugin(GunSG.class).isPeaceState(MinigamesCore.getApi().getGameManager().getInGameState())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onGamePlayerDeath(GamePlayerDeathEvent e) {
        GamePlayer gamePlayer = e.getGamePlayer();
        if (gamePlayer.getStatus() != GamePlayer.Status.GAMING) return;
        Player player = gamePlayer.getPlayer();
        handleDeath(gamePlayer);
        ConfigManager cf = GunSG.config();
        if (e.getKiller() == null) {
            Bukkit.broadcastMessage(cf.getMessage("death-msg.normal").replace("<victim>", player.getDisplayName()).replace("<action>", e.getAction()));
            return;
        }
        String itemName;
        ItemStack item = e.getKiller().getPlayer().getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            itemName = "拳頭";
        } else if (item.getItemMeta() != null && !item.getItemMeta().getDisplayName().isBlank()) {
            itemName = item.getItemMeta().getDisplayName();
        } else {
            itemName = item.getType().toString().toLowerCase(Locale.TRADITIONAL_CHINESE);
        }
        Player killer = e.getKiller().getPlayer();
        CSDirector csDirector = CSDirector.getPlugin(CSDirector.class);
        if (e.getDeathCause() == GamePlayerDeathEvent.DeathCause.BUKKIT_DEATH) {
            Bukkit.broadcastMessage(cf.getMessage("death-msg.unknown-kill-player")
                    .replace("<attacker>", e.getKiller().getPlayer().getDisplayName())
                    .replace("<victim>", player.getDisplayName()));
        } else if (e instanceof CrackShotDeathEvent) {
            CrackShotDeathEvent cs = (CrackShotDeathEvent) e;
            Entity entity = cs.getBullet();

            boolean grenade = entity instanceof TNTPrimed;
            Bukkit.broadcastMessage(cf.getMessage("death-msg.kill-player")
                    .replace("<attacker>", killer.getDisplayName())
                    .replace("<item>", grenade ? cs.getWeaponTitle() : csDirector.getPureName(itemName))
                    .replace("<victim>", player.getDisplayName())
                    .replace("<action>", e.getAction()));
        }
        else {
            Bukkit.broadcastMessage(cf.getMessage("death-msg.kill-player")
                    .replace("<attacker>", killer.getDisplayName())
                    .replace("<item>", csDirector.getPureName(itemName))
                    .replace("<victim>", player.getDisplayName())
                    .replace("<action>", e.getAction()));
        }
        GunSG.getPlugin(GunSG.class).getWantedManager().onBountyKill(killer, player);
    }

    @EventHandler
    public void freezePlayer(PlayerMoveEvent e) {
        if (MinigamesCore.getApi().getGameManager().getGameState() != GameState.PRESTART) return;
        if (GunSG.getPlugin(GunSG.class).isPeaceState(MinigamesCore.getApi().getGameManager().getInGameState()))
            return;
        Player player = e.getPlayer();
        MinigamesCore.getApi().getPlayerManager().findPlayer(player).ifPresent(g -> {
            if (g.getStatus() != GamePlayer.Status.GAMING) return;
            Location before = e.getFrom().clone();
            Location after = e.getTo().clone();
            before.setPitch(0);
            before.setYaw(0);
            after.setPitch(0);
            after.setYaw(0);
            if (before.getY() > after.getY()) return;
            if (!before.equals(after)) e.setCancelled(true);
        });
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        Block block = e.getClickedBlock();
        MinigamesCore.getApi().getPlayerManager().findPlayer(e.getPlayer()).ifPresent(g -> {
            if (g.getStatus() != GamePlayer.Status.GAMING) return;
            Player player = e.getPlayer();
            Inventory inventory = GunSG.getPlugin(GunSG.class).getChestsManager().getFakeInventory(block);
            if (inventory == null) return;
            Sound sound;
            switch (block.getType()) {
                case TRAPPED_CHEST:
                case CHEST:
                    sound = Sound.BLOCK_CHEST_OPEN;
                    break;
                case ENDER_CHEST:
                    sound = Sound.BLOCK_ENDER_CHEST_OPEN;
                    break;
                default:
                    return;
            }
            e.setCancelled(true);
            player.openInventory(inventory);
            player.getWorld().playSound(g.getPlayer().getLocation(), sound, 2, 1);
        });

    }

    @EventHandler
    public void onPlayerJoin(GamePlayerJoinEvent e) {
        if (e.getGameState() == GameState.VOTING) return;
        MinigamesCore.getApi().getPlayerManager().setSpectator(e.getGamePlayer());
    }

}
