package com.chaossmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class Data {
    public record Listing(UUID id, UUID seller, String sellerName, double price, ItemStack item, long time) {}

    private final ChaosSMP pl;
    private final File file;

    public final Map<UUID, Double> money = new HashMap<>();
    public final Map<UUID, Integer> shards = new HashMap<>();
    public final Map<UUID, Integer> kills = new HashMap<>();
    public final Map<UUID, Integer> deaths = new HashMap<>();
    public final Map<UUID, Long> playtime = new HashMap<>();
    public final Map<UUID, String> names = new HashMap<>();
    public final Set<UUID> joined = new HashSet<>();
    public final Map<UUID, Map<String, Integer>> prog = new HashMap<>();
    public final Map<UUID, Set<String>> done = new HashMap<>();
    public final Map<String, Location> warps = new LinkedHashMap<>();
    public final List<Listing> listings = new ArrayList<>();
    public Location spawn;
    public long day = LocalDate.now().toEpochDay();

    public Data(ChaosSMP pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "data.yml");
    }

    // ---------- economy ----------
    public double bal(UUID u) { return money.getOrDefault(u, 0.0); }
    public void add(UUID u, double a) { money.put(u, bal(u) + a); }
    public boolean take(UUID u, double a) {
        if (bal(u) < a) return false;
        money.put(u, bal(u) - a);
        return true;
    }
    public int sh(UUID u) { return shards.getOrDefault(u, 0); }
    public void addShards(UUID u, int a) { shards.put(u, sh(u) + a); }
    public boolean takeShards(UUID u, int a) {
        if (sh(u) < a) return false;
        shards.put(u, sh(u) - a);
        return true;
    }

    public static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 10_000) return String.format("%.1fK", v / 1_000);
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format("%.1f", v);
    }

    // ---------- objectives ----------
    public void checkDay() {
        long today = LocalDate.now().toEpochDay();
        if (today != day) {
            day = today;
            prog.clear();
            done.clear();
            Bukkit.broadcast(ChaosSMP.mm("<gradient:#ff4ecd:#7a5cff><bold>CHAOS</bold></gradient> <dark_gray>» <yellow>Daily objectives have been reset! <gray>/objectives"));
        }
    }

    public int progress(UUID u, Obj o) { return prog.getOrDefault(u, Map.of()).getOrDefault(o.name(), 0); }
    public boolean isDone(UUID u, Obj o) { return done.getOrDefault(u, Set.of()).contains(o.name()); }
    public int doneCount(UUID u) { return done.getOrDefault(u, Set.of()).size(); }

    public void addProgress(Player p, Obj o, int amt) {
        checkDay();
        UUID u = p.getUniqueId();
        if (isDone(u, o)) return;
        int v = prog.computeIfAbsent(u, k -> new HashMap<>()).merge(o.name(), amt, Integer::sum);
        if (v >= o.target) {
            done.computeIfAbsent(u, k -> new HashSet<>()).add(o.name());
            add(u, o.money);
            addShards(u, o.shards);
            p.sendMessage(ChaosSMP.mm("<gradient:#ff4ecd:#7a5cff><bold>OBJECTIVE</bold></gradient> <dark_gray>» <green>" + o.title
                    + " complete! <gray>+<green>$" + fmt(o.money) + (o.shards > 0 ? " <gray>+<aqua>" + o.shards + " shards" : "")));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    // ---------- persistence ----------
    public void load() {
        pl.getDataFolder().mkdirs();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        day = y.getLong("day", LocalDate.now().toEpochDay());
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                UUID u;
                try { u = UUID.fromString(k); } catch (Exception e) { continue; }
                ConfigurationSection s = ps.getConfigurationSection(k);
                if (s == null) continue;
                money.put(u, s.getDouble("money"));
                shards.put(u, s.getInt("shards"));
                kills.put(u, s.getInt("kills"));
                deaths.put(u, s.getInt("deaths"));
                playtime.put(u, s.getLong("playtime"));
                names.put(u, s.getString("name", "?"));
                if (s.getBoolean("joined")) joined.add(u);
                ConfigurationSection pr = s.getConfigurationSection("prog");
                if (pr != null) {
                    Map<String, Integer> m = new HashMap<>();
                    for (String o : pr.getKeys(false)) m.put(o, pr.getInt(o));
                    prog.put(u, m);
                }
                List<String> d = s.getStringList("done");
                if (!d.isEmpty()) done.put(u, new HashSet<>(d));
            }
        }
        ConfigurationSection w = y.getConfigurationSection("warps");
        if (w != null) for (String k : w.getKeys(false)) {
            Location l = w.getLocation(k);
            if (l != null) warps.put(k, l);
        }
        spawn = y.getLocation("spawn");
        ConfigurationSection a = y.getConfigurationSection("ah");
        if (a != null) for (String k : a.getKeys(false)) {
            ConfigurationSection s = a.getConfigurationSection(k);
            if (s == null) continue;
            ItemStack it = s.getItemStack("item");
            if (it == null) continue;
            try {
                listings.add(new Listing(UUID.fromString(k), UUID.fromString(s.getString("seller", "")),
                        s.getString("name", "?"), s.getDouble("price"), it, s.getLong("time")));
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("day", day);
        Set<UUID> all = new HashSet<>(names.keySet());
        all.addAll(money.keySet());
        for (UUID u : all) {
            String b = "players." + u + ".";
            y.set(b + "name", names.getOrDefault(u, "?"));
            y.set(b + "money", bal(u));
            y.set(b + "shards", sh(u));
            y.set(b + "kills", kills.getOrDefault(u, 0));
            y.set(b + "deaths", deaths.getOrDefault(u, 0));
            y.set(b + "playtime", playtime.getOrDefault(u, 0L));
            y.set(b + "joined", joined.contains(u));
            Map<String, Integer> m = prog.get(u);
            if (m != null) for (var e : m.entrySet()) y.set(b + "prog." + e.getKey(), e.getValue());
            Set<String> d = done.get(u);
            if (d != null) y.set(b + "done", new ArrayList<>(d));
        }
        for (var e : warps.entrySet()) y.set("warps." + e.getKey(), e.getValue());
        if (spawn != null) y.set("spawn", spawn);
        for (Listing l : listings) {
            String b = "ah." + l.id() + ".";
            y.set(b + "seller", l.seller().toString());
            y.set(b + "name", l.sellerName());
            y.set(b + "price", l.price());
            y.set(b + "item", l.item());
            y.set(b + "time", l.time());
        }
        try { y.save(file); } catch (IOException e) { pl.getLogger().warning("Could not save data.yml: " + e.getMessage()); }
    }
}
