# ChaosDruidEpic

EpicBot script for training combat while farming Chaos Druid loot.

The script defaults to `AUTO`: it uses ranged before 20 Attack, then migrates to melee gear. Melee gear upgrades by Attack/Defence through mithril, adamant, and rune tiers.

When melee is active, the script rotates combat styles automatically. It prioritizes the lowest Attack/Strength/Defence band, keeps the selected style for a random 30-50 minute block, then re-rolls instead of waiting for the skill to equalize.

You can force a mode from scheduler arguments with `AUTO`, `MELEE`, or `RANGED`.

Runtime decisions are written to the EpicBot logger with a `[ChaosDruid]` prefix whenever state/status changes, plus periodic heartbeat logs.

Accessory setup equips a charged Ring of wealth, a charged Combat bracelet, and any cape/cloak/Ava item already available in the bank.

Bank setup withdraws the full combat loadout in one batch, closes the bank, then equips inventory gear/accessories before travelling.

## Build

```powershell
.\gradlew.bat :chaos-druid-killer:build
```

Open EpicBot, refresh local scripts, and select **Chaos Druid Killer**.
