# ChaosDruidEpic

EpicBot script for training combat while farming Chaos Druid loot.

The script defaults to `AUTO`: it uses ranged before 20 Attack, then migrates to melee gear. Melee gear upgrades by Attack/Defence through mithril, adamant, and rune tiers.

When melee is active, the script rotates combat styles automatically. It prioritizes the lowest Attack/Strength/Defence band, keeps the selected style for a random 30-50 minute block, then re-rolls instead of waiting for the skill to equalize.

You can force a mode from scheduler arguments with `AUTO`, `MELEE`, or `RANGED`.

Runtime decisions are written to the EpicBot logger with a `[ChaosDruid]` prefix whenever state/status changes, plus periodic heartbeat logs.

Startup and combat checks ensure Auto Retaliate is enabled before normal combat flow continues.

Combat locks onto one Chaos Druid after an attack click and will not pick another target until the locked mob dies, disappears, is stolen by another player, or fails to engage after a short grace window.

The paint overlay is compact and pinned to the top-left of the game view, including total Chaos Druid kills.

Accessory setup equips a charged Ring of wealth, a charged Combat bracelet, and any cape/cloak/Ava item already available in the bank.

Bank setup withdraws the full combat loadout in one batch, closes the bank, then equips inventory gear/accessories before travelling.

Looting bag handling opens the bag only when storing newly looted items, avoiding combat-time open/close loops.

If the bank chat reports `Your containers are already empty.`, the script marks the looting bag as empty and stops clicking `Empty containers`.

If inventory fills with loot while a looting bag is available, the script tries to open/use the bag before returning to bank.

Grand Exchange handling places all queued offers into available slots before waiting and collecting the batch together.

Trapdoor travel first moves locally to the stand tile `3095,3469,0` without Dax/web walking, accepts a 1-tile local positioning tolerance, then looks for the Edgeville trapdoor object: id `1579`/`1581` at tile `3097,3468,0`.

World hop no longer waits forever for aggressive druids to stop combat; it uses a short grace window at the hop tile, then attempts the hop.

## Build

```powershell
.\gradlew.bat :chaos-druid-killer:build
```

Open EpicBot, refresh local scripts, and select **Chaos Druid Killer**.
