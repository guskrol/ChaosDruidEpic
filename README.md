# ChaosDruidEpic

EpicBot script for training combat while farming Chaos Druid loot.

The script defaults to `AUTO`: it uses ranged before 20 Attack, then migrates to melee gear. Melee gear upgrades by Attack/Defence through mithril, adamant, and rune tiers.

When melee is active, the script rotates combat styles automatically. It prioritizes the lowest Attack/Strength/Defence band, keeps the selected style for a random 30-50 minute block, then re-rolls instead of waiting for the skill to equalize.

You can force a mode from scheduler arguments with `AUTO`, `MELEE`, or `RANGED`.

Runtime decisions are written to the EpicBot logger with a `[ChaosDruid]` prefix whenever state/status changes, plus periodic heartbeat logs.

Accessory setup equips a charged Ring of wealth, a charged Combat bracelet, and any cape/cloak/Ava item already available in the bank.

Bank setup withdraws the full combat loadout in one batch, closes the bank, then equips inventory gear/accessories before travelling.

Looting bag handling opens the bag only when storing newly looted items, avoiding combat-time open/close loops.

If the bank chat reports `Your containers are already empty.`, the script marks the looting bag as empty and stops clicking `Empty containers`.

Grand Exchange handling places all queued offers into available slots before waiting and collecting the batch together.

Trapdoor travel walks to the fixed reference tile `3095,3469,0` before interacting with the Edgeville trapdoor.

## Build

```powershell
.\gradlew.bat :chaos-druid-killer:build
```

Open EpicBot, refresh local scripts, and select **Chaos Druid Killer**.
