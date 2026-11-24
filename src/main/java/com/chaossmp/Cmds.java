package com.chaossmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Cmds implements CommandExecutor, TabCompleter {
    private final ChaosSMP pl;
    private final Map<UUID, UUID> tpa = new HashMap<>();      // target -> requester
    private final Map<UUID, Long> tpaTime = new HashMap<>();

    static final Map<Material, Double> SELL = new LinkedHashMap<>();
    static {
        s(Material.DIAMOND, 150); s(Material.DIAMOND_BLOCK, 1350); s(Material.EMERALD, 100);
        s(Material.GOLD_INGOT, 30); s(Material.IRON_INGOT, 15); s(Material.COPPER_INGOT, 3);
        s(Material.COAL, 5); s(Material.REDSTONE, 2); s(Material.LAPIS_LAZULI, 4);
        s(Material.NETHERITE_INGOT, 2000); s(Material.NETHERITE_SCRAP, 400); s(Material.QUARTZ, 4);
        s(Material.WHEAT, 2); s(Material.CARROT, 2); s(Material.POTATO, 2); s(Material.COBBLESTONE, 0.5);
        s(Material.ROTTEN_FLESH, 1); s(Material.BONE, 2); s(Material.GUNPOWDER, 6); s(Material.STRING, 2);
        s(Material.SPIDER_EYE, 2); s(Material.SLIME_BALL, 4); s(Material.ENDER_PEARL, 25);
        s(Material.BLAZE_ROD, 20); s(Material.GHAST_TEAR, 60); s(Material.COD, 3); s(Material.SALMON, 4);
    }
    private static void s(Material m, double v) { SELL.put(m, v); }

    public Cmds(ChaosSMP pl) { this.pl = pl; }

    private double parseAmt(String s) {
        try {
            s = s.toLowerCase().replace(",", "");
            double mult = 1;
            if (s.endsWith("k")) { mult = 1_000; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("m")) { mult = 1_000_000; s = s.substring(0, s.length() - 1); }
            return Double.parseDouble(s) * mult;
        } catch (Exception e) { return -1; }
    }

    private double sellValue(ItemStack it) {
        if (it == null) return 0;
        Double pr = SELL.get(it.getType());
        if (pr == null) return 0;
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) return 0;
        return pr * it.getAmount();
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase();
        boolean admin = s.hasPermission("chaossmp.admin");

        // ---- commands usable from console ----
        switch (n) {
            case "givemoney", "giveshards" -> {
                if (!admin) { pl.msg(s, "<red>No permission."); return true; }
                if (a.length < 2) { pl.msg(s, "<red>Usage: /" + n + " <player> <amount>"); return true; }
                Player t = Bukkit.getPlayerExact(a[0]);
                double amt = parseAmt(a[1]);
                if (t == null || amt < 0) { pl.msg(s, "<red>Invalid player or amount."); return true; }
                if (n.equals("givemoney")) pl.data.add(t.getUniqueId(), amt);
                else pl.data.addShards(t.getUniqueId(), (int) amt);
                pl.msg(s, "<green>Done.");
                return true;
            }
            case "chaos" -> {
                if (!admin) { pl.msg(s, "<red>No permission."); return true; }
                if (a.length == 0) {
                    StringBuilder b = new StringBuilder();
                    for (Chaos.Ev e : Chaos.Ev.values()) b.append(e.name().toLowerCase()).append(", ");
                    pl.msg(s, "Events: <white>" + b + "<gray>use <white>/chaos <event>");
                    return true;
                }
                Chaos.Ev e = pl.chaos.find(a[0]);
                if (e == null) { pl.msg(s, "<red>Unknown event."); return true; }
                pl.chaos.trigger(e);
                return true;
            }
            default -> { }
        }

        if (!(s instanceof Player p)) { pl.msg(s, "<red>Players only."); return true; }
        UUID u = p.getUniqueId();

        switch (n) {
            case "bal" -> {
                if (a.length > 0) {
                    Player t = Bukkit.getPlayerExact(a[0]);
                    if (t == null) { pl.msg(p, "<red>Player not found."); return true; }
                    pl.msg(p, "<white>" + t.getName() + " <gray>has <green>$" + Data.fmt(pl.data.bal(t.getUniqueId())));
                } else pl.msg(p, "Balance: <green>$" + Data.fmt(pl.data.bal(u)) + " <gray>| Shards: <aqua>" + pl.data.sh(u));
            }
            case "shards" -> pl.msg(p, "Shards: <aqua>" + pl.data.sh(u));
            case "pay" -> {
                if (a.length < 2) { pl.msg(p, "<red>Usage: /pay <player> <amount>"); return true; }
                Player t = Bukkit.getPlayerExact(a[0]);
                double amt = parseAmt(a[1]);
                if (t == null || t.equals(p)) { pl.msg(p, "<red>Invalid player."); return true; }
                if (amt <= 0) { pl.msg(p, "<red>Invalid amount."); return true; }
                if (!pl.data.take(u, amt)) { pl.msg(p, "<red>Not enough money."); return true; }
                pl.data.add(t.getUniqueId(), amt);
                pl.msg(p, "<green>Paid $" + Data.fmt(amt) + " to " + t.getName());
                pl.msg(t, "<green>" + p.getName() + " paid you $" + Data.fmt(amt));
            }
            case "baltop" -> {
                List<Map.Entry<UUID, Double>> l = new ArrayList<>(pl.data.money.entrySet());
                l.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
                pl.msg(p, "<gold>Richest players:");
                for (int i = 0; i < Math.min(10, l.size()); i++)
                    p.sendMessage(ChaosSMP.mm("<gray>" + (i + 1) + ". <white>" + pl.data.names.getOrDefault(l.get(i).getKey(), "?")
                            + " <green>$" + Data.fmt(l.get(i).getValue())));
            }
            case "shop" -> pl.gui.openShop(p);
            case "objectives" -> pl.gui.openObjectives(p);
            case "maps" -> pl.gui.openMaps(p);
            case "minimap" -> pl.gui.giveMinimap(p);
            case "ah" -> {
                if (a.length >= 1 && a[0].equalsIgnoreCase("sell")) {
                    if (a.length < 2) { pl.msg(p, "<red>Usage: /ah sell <price>"); return true; }
                    double price = parseAmt(a[1]);
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (price <= 0) { pl.msg(p, "<red>Invalid price."); return true; }
                    if (hand.getType().isAir()) { pl.msg(p, "<red>Hold the item you want to sell."); return true; }
                    long mine = pl.data.listings.stream().filter(x -> x.seller().equals(u)).count();
                    if (mine >= 15) { pl.msg(p, "<red>You can only have 15 listings."); return true; }
                    pl.data.listings.add(new Data.Listing(UUID.randomUUID(), u, p.getName(), price, hand.clone(), System.currentTimeMillis()));
                    p.getInventory().setItemInMainHand(null);
                    pl.msg(p, "<green>Listed for $" + Data.fmt(price) + ".");
                } else pl.gui.openAh(p, 0);
            }
            case "sell" -> {
                String mode = a.length > 0 ? a[0].toLowerCase() : "hand";
                PlayerInventory inv = p.getInventory();
                double total = 0;
                if (mode.equals("all")) {
                    ItemStack[] cont = inv.getStorageContents();
                    for (int i = 0; i < cont.length; i++) {
                        double v = sellValue(cont[i]);
                        if (v > 0) { total += v; inv.setItem(i, null); }
                    }
                } else {
                    ItemStack hand = inv.getItemInMainHand();
                    double v = sellValue(hand);
                    if (v > 0) { total = v; inv.setItemInMainHand(null); }
                }
                if (total <= 0) { pl.msg(p, "<red>Nothing sellable. Try <white>/sell all <red>or hold something valuable."); return true; }
                total *= pl.chaos.moneyMult();
                pl.data.add(u, total);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                pl.msg(p, "<green>Sold for $" + Data.fmt(total) + (pl.chaos.moneyMult() > 1 ? " <gold>(2x event!)" : ""));
            }
            case "warp" -> {
                if (a.length < 1) { pl.gui.openMaps(p); return true; }
                Location l = pl.data.warps.get(a[0]);
                if (l == null) { pl.msg(p, "<red>No such map."); return true; }
                tp(p, l, a[0]);
            }
            case "setwarp" -> {
                if (!admin) { pl.msg(p, "<red>No permission."); return true; }
                if (a.length < 1) { pl.msg(p, "<red>Usage: /setwarp <name>"); return true; }
                pl.data.warps.put(a[0], p.getLocation());
                pl.msg(p, "<green>Map '" + a[0] + "' created.");
            }
            case "delwarp" -> {
                if (!admin) { pl.msg(p, "<red>No permission."); return true; }
                if (a.length < 1 || pl.data.warps.remove(a[0]) == null) { pl.msg(p, "<red>No such map."); return true; }
                pl.msg(p, "<green>Deleted.");
            }
            case "setspawn" -> {
                if (!admin) { pl.msg(p, "<red>No permission."); return true; }
                pl.data.spawn = p.getLocation();
                p.getWorld().setSpawnLocation(p.getLocation());
                pl.msg(p, "<green>Spawn set.");
            }
            case "spawn" -> tp(p, pl.data.spawn != null ? pl.data.spawn : p.getWorld().getSpawnLocation(), "Spawn");
            case "rtp" -> rtp(p);
            case "tpa" -> {
                if (a.length < 1) { pl.msg(p, "<red>Usage: /tpa <player>"); return true; }
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null || t.equals(p)) { pl.msg(p, "<red>Invalid player."); return true; }
                tpa.put(t.getUniqueId(), u);
                tpaTime.put(t.getUniqueId(), System.currentTimeMillis());
                pl.msg(p, "<green>Request sent to " + t.getName() + ".");
                pl.msg(t, "<white>" + p.getName() + " <gray>wants to teleport to you. <green>/tpaccept <gray>or <red>/tpdeny");
            }
            case "tpaccept" -> {
                UUID r = tpa.remove(u);
                Long t0 = tpaTime.remove(u);
                Player rp = r == null ? null : Bukkit.getPlayer(r);
                if (rp == null || t0 == null || System.currentTimeMillis() - t0 > 60_000) { pl.msg(p, "<red>No pending request."); return true; }
                tp(rp, p.getLocation(), p.getName());
                pl.msg(p, "<green>Accepted.");
            }
            case "tpdeny" -> {
                UUID r = tpa.remove(u);
                tpaTime.remove(u);
                Player rp = r == null ? null : Bukkit.getPlayer(r);
                pl.msg(p, "<yellow>Request denied.");
                if (rp != null) pl.msg(rp, "<red>" + p.getName() + " denied your request.");
            }
            case "loading" -> {
                if (!admin) { pl.msg(p, "<red>No permission."); return true; }
                pl.loading.start(p);
            }
            default -> { }
        }
        return true;
    }

    // ---------- teleport helpers ----------
    public void tp(Player p, Location l, String label) {
        if (l == null || l.getWorld() == null) { pl.msg(p, "<red>That location isn't available."); return; }
        p.closeInventory();
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
        p.teleportAsync(l).thenAccept(ok -> Bukkit.getScheduler().runTask(pl, () -> {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            p.showTitle(Title.title(ChaosSMP.mm("<aqua>" + label), Component.empty(),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(300))));
        }));
    }

    public void rtp(Player p) {
        World w = p.getWorld().getEnvironment() == World.Environment.NORMAL ? p.getWorld() : Bukkit.getWorlds().get(0);
        pl.msg(p, "<yellow>Finding a spot...");
        tryRtp(p, w, pl.getConfig().getInt("rtp-radius", 5000), 0);
    }

    private void tryRtp(Player p, World w, int r, int n) {
        if (!p.isOnline()) return;
        if (n > 15) { pl.msg(p, "<red>Couldn't find a safe spot. Try again."); return; }
        int x = ThreadLocalRandom.current().nextInt(-r, r + 1);
        int z = ThreadLocalRandom.current().nextInt(-r, r + 1);
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(ch -> Bukkit.getScheduler().runTask(pl, () -> {
            int y = w.getHighestBlockYAt(x, z);
            Block b = w.getBlockAt(x, y, z);
            String t = b.getType().name();
            if (b.isLiquid() || t.contains("LEAVES") || t.contains("ICE") || t.contains("MAGMA") || t.contains("CACTUS")
                    || t.contains("FIRE") || y <= w.getMinHeight()) {
                tryRtp(p, w, r, n + 1);
                return;
            }
            tp(p, new Location(w, x + 0.5, y + 1, z + 0.5), "Random TP");
        }));
    }

    // ---------- tab complete ----------
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            switch (n) {
                case "pay", "tpa", "bal", "givemoney", "giveshards" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                case "sell" -> { out.add("hand"); out.add("all"); }
                case "ah" -> out.add("sell");
                case "warp", "delwarp" -> out.addAll(pl.data.warps.keySet());
                case "chaos" -> { for (Chaos.Ev e : Chaos.Ev.values()) out.add(e.name().toLowerCase()); }
                default -> { }
            }
        }
        String pre = a.length > 0 ? a[a.length - 1].toLowerCase() : "";
        out.removeIf(x -> !x.toLowerCase().startsWith(pre));
        return out;
    }
}
