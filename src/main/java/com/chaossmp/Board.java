package com.chaossmp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Board {
    private static final int LINES = 10;
    private final ChaosSMP pl;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public Board(ChaosSMP pl) { this.pl = pl; }

    public void add(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        String name = pl.getConfig().getString("server-name", "CHAOS SMP");
        Objective o = sb.registerNewObjective("chaos", Criteria.DUMMY,
                ChaosSMP.mm("<gradient:#ff4ecd:#7a5cff><bold>" + name + "</bold></gradient>"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < LINES; i++) {
            Team t = sb.registerNewTeam("l" + i);
            String entry = "\u00a7" + i;
            t.addEntry(entry);
            o.getScore(entry).setScore(LINES - i);
        }
        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
        update(p);
    }

    public void remove(Player p) { boards.remove(p.getUniqueId()); }

    public void update(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;
        Data d = pl.data;
        UUID u = p.getUniqueId();
        long pt = d.playtime.getOrDefault(u, 0L);
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        int hearts = hp == null ? 10 : (int) (hp.getBaseValue() / 2);
        String[] l = {
                "",
                "<gold>$ <white>Money <green>" + Data.fmt(d.bal(u)),
                "<aqua>\u25c6 <white>Shards <aqua>" + d.sh(u),
                "<red>\u2694 <white>Kills <red>" + d.kills.getOrDefault(u, 0),
                "<gray>\u2620 <white>Deaths <gray>" + d.deaths.getOrDefault(u, 0),
                "<yellow>\u231a <white>Playtime <yellow>" + (pt / 3600) + "h " + ((pt % 3600) / 60) + "m",
                "<light_purple>\u2764 <white>Hearts <light_purple>" + hearts,
                "<gold>\u2605 <white>Objectives <gold>" + d.doneCount(u) + "/" + Obj.values().length,
                "<dark_red>\u26a1 <white>" + pl.chaos.status(),
                "<gray>play.localhost"
        };
        for (int i = 0; i < LINES; i++) {
            Team t = sb.getTeam("l" + i);
            if (t != null) t.prefix(l[i].isEmpty() ? Component.empty() : ChaosSMP.mm(l[i]));
        }
    }
}
