package com.chaossmp;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Chaos {
    public enum Ev {
        LOW_GRAVITY("Moon Gravity", 90), METEORS("Meteor Shower", 20), ANIMAL_RAIN("Animal Rain", 0),
        ZOOM("Zoomies", 60), MONEY_RAIN("2x Money", 120), LIGHTNING("Lightning Storm", 20),
        YEET("YEET", 0), SWAP("Position Swap", 0), DROP_PARTY("Drop Party", 0), DOUBLE_DROPS("Double Drops", 120);

        public final String label;
        public final int secs;
        Ev(String label, int secs) { this.label = label; this.secs = secs; }
    }

    private final ChaosSMP pl;
    private Ev active;
    private long end;
    public final Set<UUID> meteors = new HashSet<>();
    private final Random rnd = new Random();

    public Chaos(ChaosSMP pl) { this.pl = pl; }

    public boolean is(Ev e) { return active == e && System.currentTimeMillis() < end; }
    public double moneyMult() { return is(Ev.MONEY_RAIN) ? 2.0 : 1.0; }

    public String status() {
        long left = (end - System.currentTimeMillis()) / 1000;
        if (active != null && left > 0 && active.secs > 0) return active.label + " " + left + "s";
        return "Event: none";
    }

    public void start() {
        long ticks = 20L * 60 * Math.max(1, pl.getConfig().getInt("chaos.interval-minutes", 8));
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            Ev[] v = Ev.values();
            trigger(v[rnd.nextInt(v.length)]);
        }, ticks, ticks);
    }

    public Ev find(String name) {
        for (Ev e : Ev.values()) if (e.name().equalsIgnoreCase(name)) return e;
        return null;
    }

    public void trigger(Ev e) {
        active = e;
        end = System.currentTimeMillis() + e.secs * 1000L;
        Bukkit.getServer().showTitle(Title.title(ChaosSMP.mm("<red><bold>\u26a0 CHAOS EVENT \u26a0"),
                ChaosSMP.mm("<yellow>" + e.label),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        Bukkit.broadcast(ChaosSMP.mm("<gradient:#ff4ecd:#7a5cff><bold>CHAOS</bold></gradient> <dark_gray>» <red>" + e.label + " <gray>has started!"));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.5f);

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        switch (e) {
            case LOW_GRAVITY -> effect(online, PotionEffectType.JUMP_BOOST, 4, e.secs, PotionEffectType.SLOW_FALLING, 0);
            case ZOOM -> effect(online, PotionEffectType.SPEED, 3, e.secs, PotionEffectType.JUMP_BOOST, 1);
            case YEET -> {
                for (Player p : online) {
                    p.setVelocity(new Vector(0, 3, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 400, 0, false, false));
                }
            }
            case SWAP -> {
                if (online.size() >= 2) {
                    List<Location> locs = new ArrayList<>();
                    for (Player p : online) locs.add(p.getLocation());
                    for (int i = 0; i < online.size(); i++) online.get(i).teleportAsync(locs.get((i + 1) % locs.size()));
                }
            }
            case ANIMAL_RAIN -> {
                EntityType[] types = {EntityType.CHICKEN, EntityType.COW, EntityType.PIG, EntityType.SHEEP};
                for (Player p : online) for (int i = 0; i < 8; i++) {
                    Location l = p.getLocation().add(rnd.nextInt(11) - 5, 25, rnd.nextInt(11) - 5);
                    p.getWorld().spawnEntity(l, types[rnd.nextInt(types.length)]);
                }
            }
            case DROP_PARTY -> {
                Material[] loot = {Material.DIAMOND, Material.EMERALD, Material.GOLDEN_APPLE, Material.IRON_INGOT,
                        Material.GOLD_INGOT, Material.ENDER_PEARL, Material.COOKED_BEEF};
                for (Player p : online) for (int i = 0; i < 20; i++) {
                    Location l = p.getLocation().add(rnd.nextInt(9) - 4, 10, rnd.nextInt(9) - 4);
                    p.getWorld().dropItem(l, new ItemStack(loot[rnd.nextInt(loot.length)], 1 + rnd.nextInt(3)));
                }
            }
            case METEORS -> repeat(e.secs, p -> {
                if (p.getWorld().getEnvironment() != World.Environment.NORMAL) return;
                Location l = p.getLocation().add(rnd.nextInt(25) - 12, 40, rnd.nextInt(25) - 12);
                Fireball f = p.getWorld().spawn(l, Fireball.class, fb -> {
                    fb.setDirection(new Vector(0, -1, 0));
                    fb.setYield(1.5f);
                    fb.setIsIncendiary(false);
                });
                meteors.add(f.getUniqueId());
            });
            case LIGHTNING -> repeat(e.secs, p -> {
                Location l = p.getLocation().add(rnd.nextInt(13) - 6, 0, rnd.nextInt(13) - 6);
                p.getWorld().strikeLightning(l);
            });
            default -> { } // MONEY_RAIN / DOUBLE_DROPS are passive flags
        }
    }

    private void effect(List<Player> ps, PotionEffectType a, int ampA, int secs, PotionEffectType b, int ampB) {
        for (Player p : ps) {
            p.addPotionEffect(new PotionEffect(a, secs * 20, ampA, false, false));
            p.addPotionEffect(new PotionEffect(b, secs * 20, ampB, false, false));
        }
    }

    private void repeat(int seconds, java.util.function.Consumer<Player> action) {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ >= seconds) { cancel(); return; }
                for (Player p : Bukkit.getOnlinePlayers()) action.accept(p);
            }
        }.runTaskTimer(pl, 0L, 20L);
    }
}
