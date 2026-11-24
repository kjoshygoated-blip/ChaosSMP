package com.chaossmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ChaosSMP extends JavaPlugin {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    static final String[] COMMANDS = {"bal", "pay", "baltop", "shards", "shop", "sell", "ah", "maps", "warp", "setwarp",
            "delwarp", "minimap", "spawn", "setspawn", "rtp", "tpa", "tpaccept", "tpdeny", "objectives", "chaos",
            "givemoney", "giveshards", "loading"};

    public Data data;
    public Loading loading;
    public Board board;
    public Chaos chaos;
    public Gui gui;
    public Cmds cmds;
    public NamespacedKey heartKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        heartKey = new NamespacedKey(this, "heart");
        data = new Data(this);
        data.load();
        loading = new Loading(this);
        board = new Board(this);
        chaos = new Chaos(this);
        gui = new Gui(this);
        cmds = new Cmds(this);

        getServer().getPluginManager().registerEvents(new Events(this), this);
        for (String n : COMMANDS) {
            PluginCommand c = getCommand(n);
            if (c != null) {
                c.setExecutor(cmds);
                c.setTabCompleter(cmds);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) board.add(p);

        // every second: playtime + scoreboard
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            data.checkDay();
            for (Player p : Bukkit.getOnlinePlayers()) {
                data.playtime.merge(p.getUniqueId(), 1L, Long::sum);
                board.update(p);
            }
        }, 20L, 20L);
        // every minute: "play 30 minutes" objective
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) data.addProgress(p, Obj.TIME, 1);
        }, 1200L, 1200L);
        // autosave every 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, () -> data.save(), 6000L, 6000L);

        chaos.start();
        getLogger().info("ChaosSMP enabled. Let the chaos begin.");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) loading.cancel(p);
        if (data != null) data.save();
    }

    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    public void msg(CommandSender s, String m) {
        s.sendMessage(mm("<gradient:#ff4ecd:#7a5cff><bold>CHAOS</bold></gradient> <dark_gray>» <gray>" + m));
    }
}
