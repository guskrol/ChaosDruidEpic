# ChaosDruidEpic

EpicBot script for training combat while farming Chaos Druid loot.

The script defaults to `AUTO`: it uses ranged before 20 Attack, then migrates to melee gear. Melee gear upgrades by Attack/Defence through mithril, adamant, and rune tiers.

You can force a mode from scheduler arguments with `AUTO`, `MELEE`, or `RANGED`.

## Build

```powershell
.\gradlew.bat :chaos-druid-killer:build
```

Open EpicBot, refresh local scripts, and select **Chaos Druid Killer**.
