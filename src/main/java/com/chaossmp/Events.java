package com.chaossmp;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class Events implements Listener {
    private final ChaosSMP pl;
    private final Map<UUID, Long> tag = new HashMap<>();
    private final Random rnd = new Random();

    private static final String[] JOIN = {
            "<green>+ <white>%p <gray>has spawned into the chaos",
            "<green>+ <white>%p <gray>has arrived, hide your hearts",
            "<green>+ <white>%p <gray>logged in. Nobody is safe.",
            "<green>+ <white>%p <gray>appeared out of nowhere"
    };
    private static final String[] DEATH = {
            "<red>\u2620 <white>%v <gray>skill-issued", "<red>\u2620 <white>%v <gray>forgot how to survive",
            "<red>\u2620 <white>%v <gray>was gently yeeted into the afterlife", "<red>\u2620 <white>%v <gray>lagged into oblivion",
            "<red>\u2620 <white>%v <gray>should've stayed in spawn"
    };
    private static final String[] KILL = {
            "<red>\u2620 <white>%k <gray>deleted <white>%v", "<red>\u2620 <white>%k <gray>stole a heart from <white>%v",
            "<red>\u2620 <white>%v <gray>got folded by <white>%k", "<red>\u2620 <white>%k <gray>sent <white>%v <gray>to the shadow realm"
    };

    public Events(ChaosSMP pl) { this.pl = pl; }

    // ---------- join / quit ----------
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        pl.data.names.put(u, p.getName());
        e.joinMessage(ChaosSMP.mm(JOIN[rnd.nextInt(JOIN.length)].replace("%p", p.getName())));
        if (pl.data.joined.add(u)) {
            pl.data.add(u, pl.getConfig().getDouble("start-money", 5000));
            pl.data.addShards(u, pl.getConfig().getInt("start-shards", 5));
            pl.gui.give(p, new ItemStack(Material.BREAD, 16));
            pl.gui.give(p, new ItemStack(Material.STONE_SWORD));
            pl.gui.give(p, new ItemStack(Material.STONE_PICKAXE));
        }
        pl.board.add(p);
        pl.loading.start(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        pl.loading.cancel(p);
        pl.board.remove(p);
        Long t = tag.remove(p.getUniqueId());
        if (t != null && t > System.currentTimeMillis()) p.setHealth(0); // combat logger
        e.quitMessage(ChaosSMP.mm("<red>- <white>" + p.getName() + " <gray>rage quit"));
    }

    // ---------- loading screen freeze ----------
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!pl.loading.isLoading(e.getPlayer())) return;
        if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY()
                || e.getFrom().getZ() != e.getTo().getZ()) {
            e.setTo(e.getFrom().setDirection(e.getTo().getDirection()));
        }
    }

    @EventHandler
    public void onCmd(PlayerCommandPreprocessEvent e) {
        if (pl.loading.isLoading(e.getPlayer())) {
            e.setCancelled(true);
            pl.msg(e.getPlayer(), "<yellow>Still loading...");
        }
    }

    @EventHandler
    public void onInteractLoading(PlayerInteractEvent e) {
        if (pl.loading.isLoading(e.getPlayer())) { e.setCancelled(true); return; }
        // heart crystal
        if (e.getHand() != EquipmentSlot.HAND || !e.getAction().isRightClick()) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        if (!it.getItemMeta().getPersistentDataContainer().has(pl.heartKey, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        double max = pl.getConfig().getInt("lifesteal.max-hearts", 20) * 2.0;
        if (hp == null || hp.getBaseValue() + 2 > max) { pl.msg(p, "<red>You're already at max hearts."); return; }
        hp.setBaseValue(hp.getBaseValue() + 2);
        it.setAmount(it.getAmount() - 1);
        pl.msg(p, "<light_purple>\u2764 You gained a heart!");
    }

    // ---------- objectives ----------
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Material m = e.getBlock().getType();
        Player p = e.getPlayer();
        if (m == Material.STONE || m == Material.DEEPSLATE) pl.data.addProgress(p, Obj.STONE, 1);
        else if (m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS) pl.data.addProgress(p, Obj.ORES, 1);
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) pl.data.addProgress(e.getPlayer(), Obj.FISH, 1);
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity le = e.getEntity();
        if (le instanceof Player) return;
        Player k = le.getKiller();
        if (k != null && le instanceof Mob) {
            pl.data.addProgress(k, Obj.MOBS, 1);
            pl.data.add(k.getUniqueId(), 5 * pl.chaos.moneyMult());
        }
        if (pl.chaos.is(Chaos.Ev.DOUBLE_DROPS)) {
            for (ItemStack d : new ArrayList<>(e.getDrops())) e.getDrops().add(d.clone());
            e.setDroppedExp(e.getDroppedExp() * 2);
        }
    }

    // ---------- PvP: combat tag, lifesteal, bounty ----------
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player a) attacker = a;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player a) attacker = a;
        if (attacker == null || attacker.equals(victim)) return;
        long until = System.currentTimeMillis() + 15_000;
        tag.put(victim.getUniqueId(), until);
        tag.put(attacker.getUniqueId(), until);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        Player k = v.getKiller();
        tag.remove(v.getUniqueId());
        pl.data.deaths.merge(v.getUniqueId(), 1, Integer::sum);

        if (k != null && !k.equals(v)) {
            e.deathMessage(ChaosSMP.mm(KILL[rnd.nextInt(KILL.length)].replace("%k", k.getName()).replace("%v", v.getName())));
            pl.data.kills.merge(k.getUniqueId(), 1, Integer::sum);
            pl.data.addProgress(k, Obj.PVP, 1);
            pl.data.add(k.getUniqueId(), 250 * pl.chaos.moneyMult());
            pl.data.addShards(k.getUniqueId(), 1);
            pl.msg(k, "<green>Kill bounty: $250 + 1 shard");
            if (pl.getConfig().getBoolean("lifesteal.enabled", true)) {
                double min = pl.getConfig().getInt("lifesteal.min-hearts", 3) * 2.0;
                double max = pl.getConfig().getInt("lifesteal.max-hearts", 20) * 2.0;
                AttributeInstance va = v.getAttribute(Attribute.MAX_HEALTH);
                AttributeInstance ka = k.getAttribute(Attribute.MAX_HEALTH);
                if (va != null && va.getBaseValue() - 2 >= min) {
                    va.setBaseValue(va.getBaseValue() - 2);
                    pl.msg(v, "<red>You lost a heart!");
                }
                if (ka != null && ka.getBaseValue() + 2 <= max) {
                    ka.setBaseValue(ka.getBaseValue() + 2);
                    pl.msg(k, "<light_purple>\u2764 You stole a heart!");
                }
            }
        } else {
            e.deathMessage(ChaosSMP.mm(DEATH[rnd.nextInt(DEATH.length)].replace("%v", v.getName())));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (pl.data.spawn != null && pl.data.spawn.getWorld() != null && !e.isBedSpawn() && !e.isAnchorSpawn())
            e.setRespawnLocation(pl.data.spawn);
    }

    // ---------- meteors: no block damage ----------
    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (e.getEntity() instanceof Fireball && pl.chaos.meteors.remove(e.getEntity().getUniqueId())
                && !pl.getConfig().getBoolean("chaos.meteor-block-damage", false)) {
            e.blockList().clear();
        }
    }

    // ---------- chat ----------
    @EventHandler
    public void onChat(AsyncChatEvent e) {
        int kills = pl.data.kills.getOrDefault(e.getPlayer().getUniqueId(), 0);
        e.renderer(ChatRenderer.viewerUnaware((src, name, msg) ->
                ChaosSMP.mm("<dark_gray>[<red>\u2694" + kills + "<dark_gray>] <white>").append(name)
                        .append(ChaosSMP.mm(" <dark_gray>\u00bb <gray>")).append(msg)));
    }

    // ---------- menus ----------
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == e.getView().getTopInventory() && e.getWhoClicked() instanceof Player p)
            pl.gui.click(p, m, e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
