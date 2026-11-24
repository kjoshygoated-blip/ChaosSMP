# ChaosSMP (Donut + Unstable style SMP plugin for Paper)

## Build
1. Install JDK 25 (Paper 26.x needs it).
2. Open this folder in IntelliJ (it imports Gradle automatically) or run `gradle build`.
3. If `paper-api:26.3.build.+` doesn't resolve, edit build.gradle.kts and use `26.2.build.+` or `26.1.2.build.+`.
4. Copy `build/libs/ChaosSMP-1.0.0.jar` into your server's `plugins/` folder and restart.

## First-time setup (as op)
- `/setspawn` where players should load in
- `/setwarp <name>` for each map/location (Nether hub, arena, shops...)

## Features
- Loading screen on join (blindness + frozen + title progress bar + boss bar + tips + welcome)
- Scoreboard: money, shards, kills, deaths, playtime, hearts, objectives, current chaos event
- Economy: /bal /pay /baltop /shards /shop /sell hand|all /ah (sell <price>)
- Maps menu: /maps /warp /minimap /spawn /rtp /tpa /tpaccept /tpdeny
- Daily objectives: /objectives (rewards money + shards)
- Chaos events every 8 min (config): moon gravity, meteors, animal rain, zoomies, 2x money, lightning, yeet, position swap, drop party, double drops
- Lifesteal hearts + Heart Crystal item, combat log kill, kill bounties
- Admin: /chaos [event], /givemoney, /giveshards, /loading

Edit `plugins/ChaosSMP/config.yml` to tune everything.
