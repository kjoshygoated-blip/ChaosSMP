package com.chaossmp;

import org.bukkit.Material;

public enum Obj {
    STONE("Stone Crusher", "Mine 200 stone", 200, 2500, 0, Material.IRON_PICKAXE),
    ORES("Ore Hunter", "Mine 15 ores", 15, 4000, 1, Material.DIAMOND_PICKAXE),
    MOBS("Monster Slayer", "Kill 25 mobs", 25, 3000, 1, Material.IRON_SWORD),
    PVP("Menace", "Kill 1 player", 1, 5000, 3, Material.NETHERITE_SWORD),
    TIME("Grinder", "Play for 30 minutes", 30, 3500, 2, Material.CLOCK),
    FISH("Gone Fishin'", "Catch 5 fish", 5, 2000, 1, Material.FISHING_ROD);

    public final String title, desc;
    public final int target, shards;
    public final double money;
    public final Material icon;

    Obj(String title, String desc, int target, double money, int shards, Material icon) {
        this.title = title;
        this.desc = desc;
        this.target = target;
        this.money = money;
        this.shards = shards;
        this.icon = icon;
    }
}
