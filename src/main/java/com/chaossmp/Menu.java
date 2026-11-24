package com.chaossmp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class Menu implements InventoryHolder {
    public final String type;
    public final Inventory inv;
    public int page = 0;
    public final Map<Integer, Object> extra = new HashMap<>();

    public Menu(String type, int size, Component title) {
        this.type = type;
        this.inv = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
