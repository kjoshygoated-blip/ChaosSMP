package com.chaossmp;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Loading {
    private final ChaosSMP pl;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private static final String[] STAGES = {
            "Loading world...", "Syncing economy...", "Waking up the chaos...",
            "Spawning objectives...", "Counting your hearts...", "Almost there..."
    };

    public Loading(ChaosSMP pl) { this.pl = pl; }

    public boolean isLoading(Player p) { return tasks.containsKey(p.getUniqueId()); }

    public void start(Player p) {
        cancel(p);
        int secs = Math.max(2, pl.getConfig().getInt("loading.seconds", 8));
        int steps = secs * 10;
        List<String> tips = pl.getConfig().getStringList("loading.tips");
        String name = pl.getConfig().getString("server-name", "CHAOS SMP");

        if (pl.getConfig().getBoolean("loading.teleport-to-spawn", true)) {
            Location s = pl.data.spawn;
            if (s != null && s.getWorld() != null) p.teleportAsync(s);
        }

        BossBar bar = BossBar.bossBar(ChaosSMP.mm("<light_purple>Loading..."), 0f,
                BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_20);
        bars.put(p.getUniqueId(), bar);
        p.showBossBar(bar);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, secs * 20 + 40, 0, false, false, false));
        p.setInvulnerable(true);

        int[] step = {0};
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (!p.isOnline()) { cancel(p); return; }
            step[0]++;
            float prog = Math.min(1f, step[0] / (float) steps);
            int pct = (int) (prog * 100);
            String stage = STAGES[Math.min(STAGES.length - 1, (int) (prog * STAGES.length))];
            int filled = (int) (prog * 20);
            String barStr = "<green>" + "\u2588".repeat(filled) + "<dark_gray>" + "\u2588".repeat(20 - filled);
            String tip = tips.isEmpty() ? "" : tips.get((step[0] / 30) % tips.size());

            p.showTitle(Title.title(
                    ChaosSMP.mm("<gradient:#ff4ecd:#7a5cff><bold>" + name + "</bold></gradient>"),
                    ChaosSMP.mm(barStr + " <white>" + pct + "%"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(400), Duration.ZERO)));
            p.sendActionBar(ChaosSMP.mm("<yellow>" + stage + (tip.isEmpty() ? "" : " <gray>\u2022 <aqua>" + tip)));
            bar.progress(prog);
            bar.name(ChaosSMP.mm("<light_purple>" + stage + " <white>" + pct + "%"));
            if (step[0] % 5 == 0)
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 0.5f + prog * 1.5f);

            if (step[0] >= steps) finish(p, name);
        }, 0L, 2L);
        tasks.put(p.getUniqueId(), t);
    }

    private void finish(Player p, String name) {
        cancel(p);
        p.showTitle(Title.title(
                ChaosSMP.mm("<gradient:#ff4ecd:#7a5cff><bold>WELCOME</bold></gradient>"),
                ChaosSMP.mm("<yellow>to " + name + " <gray>\u2022 <white>good luck, you'll need it"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
    }

    public void cancel(Player p) {
        UUID u = p.getUniqueId();
        BukkitTask t = tasks.remove(u);
        if (t != null) t.cancel();
        BossBar b = bars.remove(u);
        if (b != null) p.hideBossBar(b);
        if (t != null) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.setInvulnerable(false);
            p.clearTitle();
        }
    }
}
