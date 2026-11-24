package com.chaossmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class Gui {
    record Entry(Material mat, int amt, double price, int shards, EntityType spawner, boolean heart) {}

    private final ChaosSMP pl;
    private final List<Entry> shop = List.of(
            new Entry(Material.GOLDEN_APPLE, 8, 1500, 0, null, false),
            new Entry(Material.ENDER_PEARL, 16, 800, 0, null, false),
            new Entry(Material.OBSIDIAN, 32, 1200, 0, null, false),
            new Entry(Material.TNT, 16, 2000, 0, null, false),
            new Entry(Material.DIAMOND, 8, 3000, 0, null, false),
            new Entry(Material.EXPERIENCE_BOTTLE, 32, 1500, 0, null, false),
            new Entry(Material.FIREWORK_ROCKET, 64, 2500, 0, null, false),
            new Entry(Material.TOTEM_OF_UNDYING, 1, 5000, 0, null, false),
            new Entry(Material.NETHERITE_INGOT, 1, 20000, 0, null, false),
            new Entry(Material.ENCHANTED_GOLDEN_APPLE, 1, 0, 10, null, false),
            new Entry(Material.BEACON, 1, 0, 15, null, false),
            new Entry(Material.NETHER_STAR, 1, 0, 20, null, true),
            new Entry(Material.SPAWNER, 1, 0, 25, EntityType.ZOMBIE, false),
            new Entry(Material.SPAWNER, 1, 0, 30, EntityType.SKELETON, false),
            new Entry(Material.SPAWNER, 1, 0, 50, EntityType.BLAZE, false),
            new Entry(Material.SPAWNER, 1, 0, 60, EntityType.IRON_GOLEM, false)
    );

    public Gui(ChaosSMP pl) { this.pl = pl; }

    // ---------- helpers ----------
    static Component nl(String s) { return ChaosSMP.mm(s).decoration(TextDecoration.ITALIC, false); }

    static void addLore(ItemStack it, String... lines) {
        List<Component> l = it.lore() == null ? new ArrayList<>() : new ArrayList<>(it.lore());
        for (String s : lines) l.add(nl(s));
        it.lore(l);
    }

    static ItemStack icon(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(nl(name));
        it.setItemMeta(meta);
        if (lore.length > 0) addLore(it, lore);
        return it;
    }

    static String pretty(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase().split("_")) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }

    ItemStack make(Entry e) {
        if (e.heart) {
            ItemStack it = new ItemStack(Material.NETHER_STAR);
            ItemMeta m = it.getItemMeta();
            m.displayName(nl("<red><bold>\u2764 Heart Crystal"));
            m.lore(List.of(nl("<gray>Right-click to gain a heart")));
            m.getPersistentDataContainer().set(pl.heartKey, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(m);
            return it;
        }
        ItemStack it = new ItemStack(e.mat, e.amt);
        if (e.spawner != null) {
            BlockStateMeta m = (BlockStateMeta) it.getItemMeta();
            CreatureSpawner cs = (CreatureSpawner) m.getBlockState();
            cs.setSpawnedType(e.spawner);
            m.setBlockState(cs);
            m.displayName(nl("<gold>" + pretty(e.spawner.name()) + " Spawner"));
            it.setItemMeta(m);
        }
        return it;
    }

    // ---------- MAPS (warps) ----------
    public void openMaps(Player p) {
        Menu m = new Menu("maps", 45, ChaosSMP.mm("<gradient:#ff4ecd:#7a5cff><bold>Maps</bold></gradient>"));
        int slot = 0;
        for (String name : pl.data.warps.keySet()) {
            if (slot >= 27) break;
            m.inv.setItem(slot, icon(Material.FILLED_MAP, "<aqua>" + name, "<gray>Click to travel"));
            m.extra.put(slot, name);
            slot++;
        }
        m.inv.setItem(36, icon(Material.RED_BED, "<green>Spawn", "<gray>Go to spawn"));
        m.inv.setItem(38, icon(Material.ENDER_PEARL, "<light_purple>Random TP", "<gray>Drop into the wild"));
        m.inv.setItem(40, icon(Material.MAP, "<yellow>Minimap", "<gray>Get a map of where you stand", "<gold>Cost: $250"));
        p.openInventory(m.inv);
    }

    public void giveMinimap(Player p) {
        if (!pl.data.take(p.getUniqueId(), 250)) { pl.msg(p, "<red>You need $250 for a minimap."); return; }
        Location loc = p.getLocation();
        MapView v = Bukkit.createMap(p.getWorld());
        v.setCenterX(loc.getBlockX());
        v.setCenterZ(loc.getBlockZ());
        v.setScale(MapView.Scale.NORMAL);
        v.setTrackingPosition(true);
        v.setUnlimitedTracking(true);
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        MapMeta mm = (MapMeta) it.getItemMeta();
        mm.setMapView(v);
        mm.displayName(nl("<yellow>Minimap"));
        it.setItemMeta(mm);
        give(p, it);
        pl.msg(p, "<green>Minimap created. Walk around to reveal it.");
    }

    // ---------- SHOP ----------
    public void openShop(Player p) {
        Menu m = new Menu("shop", 54, ChaosSMP.mm("<gradient:#ff4ecd:#7a5cff><bold>Shop</bold></gradient>"));
        for (int i = 0; i < shop.size(); i++) {
            Entry e = shop.get(i);
            ItemStack it = make(e);
            addLore(it, "", e.shards > 0 ? "<aqua>Price: " + e.shards + " shards" : "<green>Price: $" + Data.fmt(e.price), "<gray>Click to buy");
            m.inv.setItem(i, it);
        }
        p.openInventory(m.inv);
    }

    // ---------- AUCTION HOUSE ----------
    public void openAh(Player p, int page) {
        Menu m = new Menu("ah", 54, ChaosSMP.mm("<gradient:#ff4ecd:#7a5cff><bold>Auction House</bold></gradient>"));
        List<Data.Listing> list = new ArrayList<>(pl.data.listings);
        list.sort((a, b) -> Long.compare(b.time(), a.time()));
        int pages = Math.max(1, (list.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        m.page = page;
        for (int i = page * 45; i < Math.min(list.size(), page * 45 + 45); i++) {
            Data.Listing l = list.get(i);
            ItemStack it = l.item().clone();
            boolean own = l.seller().equals(p.getUniqueId());
            addLore(it, "", "<green>Price: $" + Data.fmt(l.price()), "<gray>Seller: <white>" + l.sellerName(),
                    own ? "<red>Right-click to cancel listing" : "<yellow>Click to buy");
            int slot = i - page * 45;
            m.inv.setItem(slot, it);
            m.extra.put(slot, l.id());
        }
        m.inv.setItem(45, icon(Material.ARROW, "<yellow>Previous page"));
        m.inv.setItem(49, icon(Material.PAPER, "<aqua>Page " + (page + 1) + "/" + pages, "<gray>Click to refresh", "<gray>Sell with <white>/ah sell <price>"));
        m.inv.setItem(53, icon(Material.ARROW, "<yellow>Next page"));
        p.openInventory(m.inv);
    }

    // ---------- OBJECTIVES ----------
    public void openObjectives(Player p) {
        pl.data.checkDay();
        Menu m = new Menu("obj", 27, ChaosSMP.mm("<gradient:#ff4ecd:#7a5cff><bold>Daily Objectives</bold></gradient>"));
        int slot = 10;
        for (Obj o : Obj.values()) {
            int v = Math.min(o.target, pl.data.progress(p.getUniqueId(), o));
            boolean done = pl.data.isDone(p.getUniqueId(), o);
            int filled = (int) (20.0 * v / o.target);
            String bar = "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(20 - filled);
            ItemStack it = icon(o.icon, (done ? "<green>\u2714 " : "<yellow>") + o.title,
                    "<gray>" + o.desc, bar + " <white>" + v + "/" + o.target, "",
                    "<gray>Reward: <green>$" + Data.fmt(o.money) + (o.shards > 0 ? " <gray>+ <aqua>" + o.shards + " shards" : ""),
                    done ? "<green>Completed!" : "<gray>In progress...");
            if (done) {
                ItemMeta meta = it.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                it.setItemMeta(meta);
            }
            m.inv.setItem(slot++, it);
        }
        p.openInventory(m.inv);
    }

    // ---------- click handling ----------
    public void click(Player p, Menu m, InventoryClickEvent e) {
        int slot = e.getSlot();
        switch (m.type) {
            case "maps" -> {
                if (slot == 36) pl.cmds.tp(p, pl.data.spawn, "Spawn");
                else if (slot == 38) { p.closeInventory(); pl.cmds.rtp(p); }
                else if (slot == 40) giveMinimap(p);
                else if (m.extra.get(slot) instanceof String name) pl.cmds.tp(p, pl.data.warps.get(name), name);
            }
            case "shop" -> {
                if (slot < 0 || slot >= shop.size()) return;
                Entry en = shop.get(slot);
                boolean ok = en.shards > 0 ? pl.data.takeShards(p.getUniqueId(), en.shards) : pl.data.take(p.getUniqueId(), en.price);
                if (!ok) {
                    pl.msg(p, "<red>You can't afford that.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                give(p, make(en));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                pl.msg(p, "<green>Purchased!");
            }
            case "ah" -> {
                if (slot == 45) { openAh(p, m.page - 1); return; }
                if (slot == 53) { openAh(p, m.page + 1); return; }
                if (slot == 49) { openAh(p, m.page); return; }
                if (!(m.extra.get(slot) instanceof UUID id)) return;
                Data.Listing l = null;
                for (Data.Listing x : pl.data.listings) if (x.id().equals(id)) { l = x; break; }
                if (l == null) { pl.msg(p, "<red>That listing is gone."); openAh(p, m.page); return; }
                if (l.seller().equals(p.getUniqueId())) {
                    if (e.isRightClick()) {
                        pl.data.listings.remove(l);
                        give(p, l.item());
                        pl.msg(p, "<yellow>Listing cancelled.");
                        openAh(p, m.page);
                    } else pl.msg(p, "<gray>That's your listing. Right-click to cancel it.");
                    return;
                }
                if (!pl.data.take(p.getUniqueId(), l.price())) {
                    pl.msg(p, "<red>You can't afford that.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                pl.data.listings.remove(l);
                pl.data.add(l.seller(), l.price());
                give(p, l.item());
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                pl.msg(p, "<green>Bought for $" + Data.fmt(l.price()) + "!");
                Player seller = Bukkit.getPlayer(l.seller());
                if (seller != null) pl.msg(seller, "<green>" + p.getName() + " bought your item for $" + Data.fmt(l.price()) + "!");
                openAh(p, m.page);
            }
            default -> { }
        }
    }

    public void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values())
            p.getWorld().dropItem(p.getLocation(), left);
    }
}
