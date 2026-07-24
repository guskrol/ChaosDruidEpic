package org.gusta.chaosdruid;

import com.epicbot.api.shared.APIContext;
import com.epicbot.api.shared.GameType;
import com.epicbot.api.shared.entity.GroundItem;
import com.epicbot.api.shared.entity.Item;
import com.epicbot.api.shared.entity.ItemWidget;
import com.epicbot.api.shared.entity.NPC;
import com.epicbot.api.shared.entity.SceneObject;
import com.epicbot.api.shared.entity.WidgetChild;
import com.epicbot.api.shared.entity.details.Locatable;
import com.epicbot.api.shared.event.ChatMessageEvent;
import com.epicbot.api.shared.methods.IEquipmentAPI;
import com.epicbot.api.shared.methods.IBankAPI;
import com.epicbot.api.shared.methods.ICombatAPI;
import com.epicbot.api.shared.methods.ITabsAPI;
import com.epicbot.api.shared.model.Area;
import com.epicbot.api.shared.model.ItemDetail;
import com.epicbot.api.shared.model.Skill;
import com.epicbot.api.shared.model.Tile;
import com.epicbot.api.shared.model.World;
import com.epicbot.api.shared.model.WorldType;
import com.epicbot.api.shared.model.ge.GrandExchangeOffer;
import com.epicbot.api.shared.model.ge.GrandExchangeSlot;
import com.epicbot.api.shared.script.Script;
import com.epicbot.api.shared.script.ScriptManifest;
import com.epicbot.api.shared.script.task.ScriptTask;
import com.epicbot.api.shared.util.paint.PaintContext;
import com.epicbot.api.shared.util.time.Time;
import com.epicbot.api.shared.webwalking.model.WalkState;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@ScriptManifest(name = "Chaos Druid Killer", gameType = GameType.OS)
public class ChaosDruidKillerScript extends Script {
    private static final String VERSION = "v0.2.5-robust-trapdoor";

    private static final int CHAOS_DRUID_ID = 520;
    private static final int COINS_ID = 995;
    private static final String FOOD = "Tuna";
    private static final String LOOTING_BAG_CLOSED = "Looting bag";
    private static final String LOOTING_BAG_OPEN = "Looting bag (open)";
    private static final String MSB_SCROLL = "Magic shortbow scroll";
    private static final String MSB_BASE = "Magic shortbow";
    private static final String MSB_IMBUED = "Magic shortbow (i)";

    private static final int COMBAT_FOOD = 10;
    private static final int FOOD_MIN_STOCK = 15;
    private static final int FOOD_BUY_STOCK = 100;
    private static final int AMMO_MIN_STOCK = 200;
    private static final int AMMO_BUY_STOCK = 1000;
    private static final int COMBAT_AMMO = 1000;
    private static final int GLORY_MIN_STOCK = 2;
    private static final int GLORY_BUY_STOCK = 8;
    private static final int ROW_MIN_STOCK = 1;
    private static final int ROW_BUY_STOCK = 3;
    private static final int EAT_HP_PERCENT = 55;
    private static final int RETURN_HP_PERCENT = 35;
    private static final long SELL_THRESHOLD_GP = 400_000L;
    private static final long GE_OFFER_WAIT_MS = 12_000L;
    private static final long GE_OFFER_ABORT_MS = 60_000L;
    private static final long LOOT_TIMEOUT_MS = 10_000L;
    private static final long MELEE_STYLE_MIN_MS = 30 * 60_000L;
    private static final long MELEE_STYLE_MAX_MS = 50 * 60_000L;
    private static final int MELEE_STYLE_ROTATION_BAND = 1;
    private static final int MAX_OTHER_PLAYERS = 2;

    private static final Area CHAOS_DRUIDS_AREA = new Area(
            new Tile(3102, 9944, 0),
            new Tile(3109, 9944, 0),
            new Tile(3112, 9940, 0),
            new Tile(3123, 9937, 0),
            new Tile(3123, 9925, 0),
            new Tile(3118, 9922, 0),
            new Tile(3111, 9923, 0),
            new Tile(3107, 9927, 0),
            new Tile(3107, 9932, 0),
            new Tile(3103, 9935, 0)
    );
    private static final Area GE_AREA = new Area(3141, 3468, 3186, 3513);
    private static final Area EDGEVILLE_BANK_AREA = new Area(3085, 3488, 3098, 3500);
    private static final Area HOP_AREA = new Area(3102, 9939, 3107, 9944);
    private static final Tile GE_CENTER = new Tile(3165, 3487, 0);
    private static final Tile EDGEVILLE_TRAPDOOR = new Tile(3097, 3468, 0);
    private static final Tile HOP_TILE = new Tile(3105, 9941, 0);

    private static final String[] CHARGED_GLORIES = {
            "Amulet of glory(1)",
            "Amulet of glory(2)",
            "Amulet of glory(3)",
            "Amulet of glory(4)",
            "Amulet of glory(5)",
            "Amulet of glory(6)"
    };
    private static final String[] CHARGED_ROWS = {
            "Ring of wealth (1)",
            "Ring of wealth (2)",
            "Ring of wealth (3)",
            "Ring of wealth (4)",
            "Ring of wealth (5)"
    };
    private static final Set<String> LOOT_NAMES = normalizedSet(
            "Grimy guam leaf",
            "Grimy marrentill",
            "Grimy tarromin",
            "Grimy harralander",
            "Grimy ranarr weed",
            "Grimy irit leaf",
            "Grimy avantoe",
            "Grimy kwuarm",
            "Law rune",
            "Nature rune",
            "Mithril bolts",
            "Ensouled chaos druid head",
            LOOTING_BAG_CLOSED
    );
    private static final Set<String> STACKABLE_LOOT = normalizedSet(
            "Coins",
            "Law rune",
            "Nature rune",
            "Mithril bolts"
    );
    private static final Set<WorldType> BLOCKED_WORLD_TYPES = Set.of(
            WorldType.PVP,
            WorldType.BOUNTY,
            WorldType.PVP_ARENA,
            WorldType.SKILL_TOTAL,
            WorldType.HIGH_RISK,
            WorldType.LAST_MAN_STANDING,
            WorldType.BETA_WORLD,
            WorldType.NOSAVE_MODE,
            WorldType.TOURNAMENT_WORLD,
            WorldType.FRESH_START_WORLD,
            WorldType.DEADMAN,
            WorldType.SEASONAL
    );

    private final Deque<GeAction> geQueue = new ArrayDeque<>();
    private final List<GeAction> activeGeActions = new ArrayList<>();

    private State state = State.STARTUP;
    private CombatMode configuredMode = CombatMode.AUTO;
    private CombatMode activeMode = CombatMode.RANGED;
    private GearPlan gearPlan;
    private String status = "Starting";
    private long startedAt;
    private int startRangedXp;
    private int startAttackXp;
    private int startStrengthXp;
    private int startDefenceXp;
    private long estimatedLootGp;
    private boolean lootBagFull;
    private long lootStartedAt;
    private int lootFails;
    private long nextGeCollectAt;
    private long activeGeStartedAt;
    private long hopReadyAt;
    private long nextAntibanAt;
    private long nextWorldHopCheckAt;
    private Skill.Skills meleeTrainingSkill;
    private long meleeStyleSwitchAt;
    private State lastLoggedState;
    private CombatMode lastLoggedMode;
    private String lastLoggedStatus = "";
    private String lastLoggedGear = "";
    private String lastLoggedStyle = "";
    private long nextHeartbeatLogAt;

    @Override
    public boolean onStart(String... args) {
        configuredMode = parseMode(args);
        startedAt = System.currentTimeMillis();
        APIContext ctx = getAPIContext();
        if (ctx != null) {
            startRangedXp = skillXp(ctx, Skill.Skills.RANGED);
            startAttackXp = skillXp(ctx, Skill.Skills.ATTACK);
            startStrengthXp = skillXp(ctx, Skill.Skills.STRENGTH);
            startDefenceXp = skillXp(ctx, Skill.Skills.DEFENCE);
            activeMode = chooseMode(ctx);
            gearPlan = GearPlan.forMode(ctx, activeMode);
        }
        scheduleAntiban();
        state = State.STARTUP;
        addTask(new ChaosTask());
        getLogger().info("Chaos Druid Killer " + VERSION + " started in " + configuredMode + " mode");
        return true;
    }

    @Override
    protected void onChatMessage(ChatMessageEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        String message = event.getMessage().toLowerCase(Locale.ENGLISH);
        if (message.contains("you don't have space in your looting bag")) {
            lootBagFull = true;
            log("Looting bag is full; switching to inventory loot");
        }
    }

    @Override
    protected void onPaint(PaintContext paint, APIContext ctx) {
        if (paint == null) {
            return;
        }

        int x = 8;
        int y = 344;
        int width = 500;
        int height = 141;
        paint.fill(new Rectangle(x, y, width, height), new Color(15, 18, 20, 210));
        paint.draw(new Rectangle(x, y, width, height), new Color(130, 185, 95, 220), 1);

        int left = x + 12;
        int right = x + 260;
        int line = y + 18;
        paint.drawText("Chaos Druid Killer " + VERSION, left, line, new Color(190, 235, 150), 14);
        line += 16;
        paint.drawText("Runtime: " + runtimeText(), left, line, Color.WHITE, 11);
        line += 15;
        paint.drawText("State: " + state, left, line, new Color(220, 235, 210), 11);
        line += 15;
        paint.drawText("Mode: " + activeMode + " (" + configuredMode + ")", left, line, new Color(220, 235, 210), 11);
        line += 15;
        paint.drawText("Style: " + combatStyleText(), left, line, new Color(220, 235, 210), 11);
        line += 15;
        paint.drawText("Gear: " + (gearPlan == null ? "-" : gearPlan.shortText()), left, line, new Color(240, 220, 140), 11);
        line += 15;
        paint.drawText("Status: " + shortText(status, 42), left, line, new Color(220, 235, 210), 11);

        int rightLine = y + 34;
        paint.drawText("Ranged XP: " + xpGained(ctx, Skill.Skills.RANGED, startRangedXp), right, rightLine, new Color(220, 235, 210), 11);
        rightLine += 15;
        paint.drawText("Melee XP: " + meleeXpGained(ctx), right, rightLine, new Color(220, 235, 210), 11);
        rightLine += 15;
        paint.drawText("Food: " + (ctx == null ? "-" : ctx.inventory().getCount(FOOD)), right, rightLine, new Color(220, 235, 210), 11);
        rightLine += 15;
        paint.drawText("Loot bag: " + lootBagText(ctx), right, rightLine, new Color(220, 235, 210), 11);
        rightLine += 15;
        paint.drawText("Loot est.: " + estimatedLootGp + " gp", right, rightLine, new Color(240, 220, 140), 11);
        rightLine += 15;
        paint.drawText("GE queue: " + geQueue.size() + "/" + activeGeActions.size(), right, rightLine, new Color(220, 235, 210), 11);
    }

    @Override
    protected void onStop() {
        getLogger().info("Chaos Druid Killer " + VERSION + " stopped");
    }

    @Override
    protected void onPause() {
        clearInteractionState(getAPIContext());
    }

    private class ChaosTask implements ScriptTask {
        @Override
        public boolean shouldExecute() {
            return true;
        }

        @Override
        public void run() {
            APIContext ctx = getAPIContext();
            if (ctx == null || !ctx.client().isLoggedIn()) {
                status = "Waiting for logged-in client";
                Time.sleep(800, 1200);
                return;
            }

            activeMode = chooseMode(ctx);
            gearPlan = GearPlan.forMode(ctx, activeMode);
            handleDialogue(ctx);
            runAntiban(ctx);

            switch (state) {
                case STARTUP:
                    startup(ctx);
                    break;
                case BANK_DEPOSIT:
                    bankDeposit(ctx);
                    break;
                case BUILD_RESTOCK:
                    buildRestock(ctx);
                    break;
                case GE_TRADING:
                    geTrading(ctx);
                    break;
                case BANK_SETUP:
                    bankSetup(ctx);
                    break;
                case TRAVEL_TO_DRUIDS:
                    travelToDruids(ctx);
                    break;
                case COMBAT:
                    combat(ctx);
                    break;
                case LOOT:
                    loot(ctx);
                    break;
                case EAT:
                    eat(ctx);
                    break;
                case RETURN_TO_BANK:
                    returnToBank(ctx);
                    break;
                case WORLD_HOP:
                    worldHop(ctx);
                    break;
                case DEATH_RECOVERY:
                    deathRecovery(ctx);
                    break;
                default:
                    state = State.STARTUP;
                    Time.sleep(600, 900);
            }
            logLoopSnapshot(ctx);
        }
    }

    private void startup(APIContext ctx) {
        clearInteractionState(ctx);
        status = "Startup check";
        if (isLikelyDeathsOffice(ctx)) {
            state = State.DEATH_RECOVERY;
            return;
        }
        if (isAtAnyBank(ctx)) {
            state = State.BANK_DEPOSIT;
            return;
        }
        if (CHAOS_DRUIDS_AREA.contains(ctx.localPlayer().getLocation())) {
            state = State.COMBAT;
            return;
        }
        if (GE_AREA.contains(ctx.localPlayer().getLocation())) {
            state = State.BANK_DEPOSIT;
            return;
        }
        if (EDGEVILLE_BANK_AREA.contains(ctx.localPlayer().getLocation())) {
            state = State.BANK_DEPOSIT;
            return;
        }

        status = "Walking to nearest bank for setup";
        walkToBank(ctx);
        Time.sleep(1200, 1800);
    }

    private void bankDeposit(APIContext ctx) {
        if (!openBank(ctx, "deposit/restock check")) {
            return;
        }

        if (emptyLootingBag(ctx)) {
            return;
        }

        status = "Depositing inventory";
        ctx.bank().depositInventory();
        Time.sleep(700, 1100);
        lootBagFull = false;

        long lootValue = bankLootValue(ctx);
        if (lootValue >= SELL_THRESHOLD_GP) {
            status = "Loot threshold reached: " + lootValue + " gp";
            buildSellQueue(ctx);
            closeBank(ctx);
            state = State.GE_TRADING;
            return;
        }

        state = State.BANK_SETUP;
    }

    private void buildRestock(APIContext ctx) {
        if (!openBank(ctx, "restock queue")) {
            return;
        }
        if (!waitForBankSnapshot(ctx)) {
            return;
        }

        geQueue.clear();
        activeGeActions.clear();
        addMissingGearBuys(ctx);
        addSupplyBuys(ctx);

        if (geQueue.isEmpty()) {
            status = "No restock needed";
            state = State.BANK_SETUP;
            return;
        }

        status = "Restock queue ready: " + geQueue.size();
        closeBank(ctx);
        state = State.GE_TRADING;
    }

    private void geTrading(APIContext ctx) {
        if (ctx.bank().isOpen()) {
            closeBank(ctx);
            return;
        }
        if (!GE_AREA.contains(ctx.localPlayer().getLocation())) {
            status = "Walking to GE for trades";
            walkTo(ctx, GE_CENTER, true);
            Time.sleep(1200, 1800);
            return;
        }
        if (!ctx.grandExchange().isOpen()) {
            status = "Opening Grand Exchange";
            ctx.grandExchange().open();
            Time.sleep(1200, 1800, () -> ctx.grandExchange().isOpen(), 100);
            return;
        }
        if (confirmGeWarning(ctx)) {
            return;
        }

        if (!activeGeActions.isEmpty()) {
            handleActiveGeActions(ctx);
            return;
        }

        GeAction action = geQueue.poll();
        if (action == null) {
            status = "Collecting GE leftovers";
            collectGeToBank(ctx);
            ctx.grandExchange().close();
            Time.sleep(600, 900, () -> !ctx.grandExchange().isOpen(), 100);
            state = State.BANK_DEPOSIT;
            return;
        }

        placeGeAction(ctx, action);
    }

    private void bankSetup(APIContext ctx) {
        if (equipInventorySetupItem(ctx)) {
            return;
        }

        if (!openBank(ctx, "combat setup")) {
            return;
        }

        GearItem missing = firstMissingGear(ctx);
        if (missing != null) {
            if (withdrawOrEquip(ctx, missing)) {
                return;
            }
            status = "Missing setup item; rebuilding restock: " + missing.name;
            state = State.BUILD_RESTOCK;
            return;
        }

        if (ensureChargedGloryEquipped(ctx)) {
            return;
        }

        if (gearPlan.mode == CombatMode.RANGED && ensureAmmo(ctx)) {
            return;
        }

        if (ensureFood(ctx)) {
            return;
        }

        if (ensureLootingBag(ctx)) {
            return;
        }

        closeBank(ctx);
        state = State.TRAVEL_TO_DRUIDS;
        travelDelay();
    }

    private void travelToDruids(APIContext ctx) {
        if (CHAOS_DRUIDS_AREA.contains(ctx.localPlayer().getLocation())) {
            state = State.COMBAT;
            return;
        }

        if (isInEdgevilleDungeon(ctx)) {
            status = "Walking inside Edgeville dungeon";
            walkTo(ctx, CHAOS_DRUIDS_AREA.getRandomTile(), false);
            Time.sleep(1200, 1800);
            return;
        }

        if (ctx.bank().isOpen()) {
            closeBank(ctx);
            return;
        }
        if (ctx.grandExchange().isOpen()) {
            ctx.grandExchange().close();
            Time.sleep(600, 900);
            return;
        }

        if (distanceTo(ctx, EDGEVILLE_TRAPDOOR) <= 15) {
            handleTrapdoor(ctx);
            return;
        }

        if (EDGEVILLE_BANK_AREA.contains(ctx.localPlayer().getLocation())
                || distanceTo(ctx, EDGEVILLE_TRAPDOOR) <= 55) {
            status = "Walking to Edgeville trapdoor";
            walkTo(ctx, EDGEVILLE_TRAPDOOR, false);
            Time.sleep(1200, 1800);
            return;
        }

        if (tryGloryTeleport(ctx)) {
            return;
        }

        status = "Web walking to Edgeville trapdoor";
        walkTo(ctx, EDGEVILLE_TRAPDOOR, true);
        Time.sleep(1200, 1800);
    }

    private void combat(APIContext ctx) {
        if (!CHAOS_DRUIDS_AREA.contains(ctx.localPlayer().getLocation())) {
            state = State.TRAVEL_TO_DRUIDS;
            return;
        }

        if (!gearLooksReady(ctx)) {
            status = "Gear no longer ready; banking";
            state = State.RETURN_TO_BANK;
            return;
        }

        if (lootBagFull && ctx.inventory().contains(LOOTING_BAG_OPEN)) {
            status = "Closing full looting bag";
            ctx.inventory().interactItem("Close", LOOTING_BAG_OPEN);
            Time.sleep(600, 1000);
            return;
        }
        if (!lootBagFull && ctx.inventory().contains(LOOTING_BAG_CLOSED)) {
            status = "Opening looting bag";
            ctx.inventory().interactItem("Open", LOOTING_BAG_CLOSED);
            Time.sleep(600, 1000);
            return;
        }

        if (shouldReturnForSupplies(ctx)) {
            state = State.RETURN_TO_BANK;
            return;
        }
        if (shouldEat(ctx)) {
            state = State.EAT;
            return;
        }
        if (!ctx.inventory().isFull() && findLootTarget(ctx) != null) {
            state = State.LOOT;
            return;
        }
        if (shouldWorldHop(ctx)) {
            state = State.WORLD_HOP;
            return;
        }

        if (ensureCombatStyle(ctx)) {
            return;
        }

        if (ctx.localPlayer().isInCombat() || ctx.localPlayer().isAttacking()) {
            maybeSpecialAttack(ctx);
            Time.sleep(600, 900);
            return;
        }

        NPC attacker = attackingPlayer(ctx);
        if (attacker != null) {
            status = "Re-engaging attacker";
            attacker.interact("Attack");
            Time.sleep(600, 900);
            return;
        }

        NPC target = findAttackTarget(ctx);
        if (target == null) {
            status = "No Chaos Druid target nearby";
            Time.sleep(800, 1200);
            return;
        }

        status = "Attacking Chaos Druid";
        ctx.mouse().move(target);
        Time.sleep(80, 220);
        target.interact("Attack");
        Time.sleep(900, 1400);
    }

    private void loot(APIContext ctx) {
        if (ctx.inventory().isFull()) {
            resetLootState();
            state = State.COMBAT;
            return;
        }
        if (lootStartedAt == 0L) {
            lootStartedAt = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - lootStartedAt > LOOT_TIMEOUT_MS || lootFails >= 5) {
            status = "Loot timeout; returning to combat";
            resetLootState();
            state = State.COMBAT;
            return;
        }

        GroundItem loot = findLootTarget(ctx);
        if (loot == null || !loot.isValid()) {
            resetLootState();
            state = State.COMBAT;
            return;
        }
        if (distanceTo(ctx, loot) > 4) {
            status = "Walking to loot";
            walkTo(ctx, loot.getLocation(), false);
            Time.sleep(900, 1400);
            return;
        }

        String name = loot.getName();
        int amount = Math.max(1, loot.getStackSize());
        int before = inventoryCountIncludingStacks(ctx, name);
        status = "Taking " + name;
        if (loot.interact("Take")) {
            Time.sleep(700, 1400, () -> inventoryCountIncludingStacks(ctx, name) > before || !loot.isValid(), 100);
            if (inventoryCountIncludingStacks(ctx, name) > before || !loot.isValid()) {
                lootFails = 0;
                lootStartedAt = System.currentTimeMillis();
                estimatedLootGp += estimatedValue(ctx, name, amount);
                if (!lootBagFull && hasOpenLootingBag(ctx) && !nameMatches(name, LOOTING_BAG_CLOSED)) {
                    storeLootInBag(ctx, name);
                }
            } else {
                lootFails++;
            }
        } else {
            lootFails++;
        }
        Time.sleep(400, 700);
    }

    private void eat(APIContext ctx) {
        status = "Eating";
        ctx.inventory().interactItem("Eat", FOOD);
        Time.sleep(600, 900);
        state = State.COMBAT;
    }

    private void returnToBank(APIContext ctx) {
        if (ctx.bank().isOpen()) {
            state = State.BANK_DEPOSIT;
            return;
        }
        if (EDGEVILLE_BANK_AREA.contains(ctx.localPlayer().getLocation())) {
            state = State.BANK_DEPOSIT;
            return;
        }
        if (tryGloryTeleport(ctx)) {
            return;
        }
        status = "Walking to bank";
        walkToBank(ctx);
        Time.sleep(1200, 1800);
    }

    private void worldHop(APIContext ctx) {
        if (!HOP_AREA.contains(ctx.localPlayer().getLocation())) {
            hopReadyAt = 0L;
            status = "Walking to hop tile";
            walkTo(ctx, HOP_TILE, false);
            Time.sleep(1200, 1800);
            return;
        }
        if (ctx.localPlayer().isInCombat() || ctx.localPlayer().isAttacking()) {
            hopReadyAt = 0L;
            status = "Waiting out combat before hop";
            Time.sleep(1200, 1600);
            return;
        }
        if (hopReadyAt == 0L) {
            hopReadyAt = System.currentTimeMillis() + 10_000L;
            status = "Out of combat; waiting before hop";
            Time.sleep(600, 900);
            return;
        }
        if (System.currentTimeMillis() < hopReadyAt) {
            status = "Hop cooldown";
            Time.sleep(600, 900);
            return;
        }

        int currentWorld = ctx.world().getCurrent();
        status = "Hopping world from " + currentWorld;
        boolean hopped = ctx.world().hop(world -> isSafeMembersWorld(world, currentWorld));
        Time.sleep(2500, 5000);
        if (!hopped) {
            status = "World hop failed";
        }
        hopReadyAt = 0L;
        state = State.COMBAT;
    }

    private void deathRecovery(APIContext ctx) {
        status = "Death recovery: returning to GE setup";
        if (ctx.widgets().isInterfaceOpen()) {
            WidgetChild takeAll = findWidgetByText(ctx, "Take-All");
            if (takeAll != null && takeAll.click()) {
                Time.sleep(1200, 1800);
                return;
            }
            ctx.widgets().closeInterface();
            Time.sleep(600, 900);
            return;
        }
        walkTo(ctx, GE_CENTER, true);
        Time.sleep(1200, 1800);
        state = State.BANK_DEPOSIT;
    }

    private CombatMode parseMode(String... args) {
        if (args == null) {
            return CombatMode.AUTO;
        }
        for (String arg : args) {
            if (arg == null) {
                continue;
            }
            String normalized = arg.trim().toUpperCase(Locale.ENGLISH);
            if (normalized.contains("MELEE")) {
                return CombatMode.MELEE;
            }
            if (normalized.contains("RANGED") || normalized.contains("RANGE")) {
                return CombatMode.RANGED;
            }
            if (normalized.contains("AUTO")) {
                return CombatMode.AUTO;
            }
        }
        return CombatMode.AUTO;
    }

    private CombatMode chooseMode(APIContext ctx) {
        if (configuredMode == CombatMode.MELEE) {
            return CombatMode.MELEE;
        }
        if (configuredMode == CombatMode.RANGED) {
            return CombatMode.RANGED;
        }
        return realLevel(ctx, Skill.Skills.ATTACK) >= 20 ? CombatMode.MELEE : CombatMode.RANGED;
    }

    private void addMissingGearBuys(APIContext ctx) {
        for (GearItem item : gearPlan.items) {
            if (item.optional) {
                continue;
            }
            int inventory = inventoryCount(ctx, item.name);
            int equipment = equipmentCount(ctx, item.name);
            int bank = bankCount(ctx, item.name);
            int owned = inventory + equipment + bank;
            if (owned <= 0) {
                getLogger().info("[ChaosDruid] queue gear buy: " + item.name
                        + " inv=" + inventory + " equip=" + equipment + " bank=" + bank
                        + " visibleBankItems=" + visibleBankItems(ctx));
                geQueue.add(GeAction.buy(item.name, 1, buyPrice(ctx, item.name)));
            } else {
                getLogger().info("[ChaosDruid] skip gear buy: " + item.name
                        + " owned=" + owned
                        + " inv=" + inventory + " equip=" + equipment + " bank=" + bank);
            }
        }
    }

    private void addSupplyBuys(APIContext ctx) {
        int foodTotal = countAnywhere(ctx, FOOD);
        if (realLevel(ctx, Skill.Skills.DEFENCE) < 50 && foodTotal < FOOD_MIN_STOCK) {
            geQueue.add(GeAction.buy(FOOD, FOOD_BUY_STOCK - foodTotal, buyPrice(ctx, FOOD)));
        }

        if (gearPlan.mode == CombatMode.RANGED) {
            int arrows = countAnywhere(ctx, gearPlan.ammoName);
            if (arrows < AMMO_MIN_STOCK) {
                geQueue.add(GeAction.buy(gearPlan.ammoName, AMMO_BUY_STOCK - arrows, buyPrice(ctx, gearPlan.ammoName)));
            }
        }

        int glories = totalChargedGlories(ctx);
        if (glories < GLORY_MIN_STOCK) {
            geQueue.add(GeAction.buy("Amulet of glory(6)", GLORY_BUY_STOCK - glories, buyPrice(ctx, "Amulet of glory(6)")));
        }

        int rows = totalChargedRows(ctx);
        if (rows < ROW_MIN_STOCK) {
            geQueue.add(GeAction.buy("Ring of wealth (5)", ROW_BUY_STOCK - rows, buyPrice(ctx, "Ring of wealth (5)")));
        }
    }

    private void buildSellQueue(APIContext ctx) {
        geQueue.clear();
        activeGeActions.clear();
        if (!ctx.bank().isWithdrawMode(IBankAPI.WithdrawMode.NOTE)) {
            ctx.bank().selectWithdrawMode(IBankAPI.WithdrawMode.NOTE);
            Time.sleep(600, 900);
        }
        for (String loot : LOOT_NAMES) {
            if (nameMatches(loot, LOOTING_BAG_CLOSED)) {
                continue;
            }
            int count = ctx.bank().getCount(loot);
            if (count <= 0) {
                continue;
            }
            status = "Withdrawing sell loot: " + loot;
            if (ctx.bank().withdrawAll(loot)) {
                Time.sleep(500, 900);
                geQueue.add(GeAction.sell(loot, count, sellPrice(ctx, loot)));
            }
        }
        if (ctx.bank().isWithdrawMode(IBankAPI.WithdrawMode.NOTE)) {
            ctx.bank().selectWithdrawMode(IBankAPI.WithdrawMode.ITEM);
            Time.sleep(500, 800);
        }
    }

    private void placeGeAction(APIContext ctx, GeAction action) {
        if (action.quantity <= 0) {
            return;
        }
        status = action.describe();
        boolean placed = action.type == GeActionType.BUY
                ? ctx.grandExchange().placeBuyOffer(action.itemName, action.quantity, action.price)
                : ctx.grandExchange().placeSellOffer(action.itemName, action.quantity, action.price);
        Time.sleep(1200, 1800);
        if (placed) {
            activeGeActions.add(action);
            activeGeStartedAt = System.currentTimeMillis();
            nextGeCollectAt = System.currentTimeMillis() + GE_OFFER_WAIT_MS;
            return;
        }
        if (!confirmGeWarning(ctx)) {
            status = "GE offer not placed; retrying " + action.itemName;
            geQueue.addFirst(action);
            Time.sleep(1200, 1800);
        }
    }

    private void handleActiveGeActions(APIContext ctx) {
        if (System.currentTimeMillis() < nextGeCollectAt) {
            status = "Waiting for GE offers";
            Time.sleep(800, 1200);
            return;
        }

        int pending = 0;
        for (GeAction action : activeGeActions) {
            GrandExchangeSlot slot = findSlot(ctx, action);
            if (slot != null && !slot.isCompleted() && !slot.canCollect()) {
                pending++;
            }
        }

        if (pending > 0 && System.currentTimeMillis() - activeGeStartedAt < GE_OFFER_ABORT_MS) {
            status = "GE pending: " + pending;
            nextGeCollectAt = System.currentTimeMillis() + 6_000L;
            Time.sleep(800, 1200);
            return;
        }

        if (pending > 0) {
            status = "Aborting stale GE offers";
            for (GeAction action : new ArrayList<>(activeGeActions)) {
                GrandExchangeSlot slot = findSlot(ctx, action);
                if (slot != null && !slot.isCompleted() && !slot.canCollect()) {
                    slot.abortOffer();
                    Time.sleep(600, 900);
                    action.increasePrice();
                    geQueue.add(action);
                }
            }
        }

        status = "Collecting GE offers";
        collectGeToBank(ctx);
        activeGeActions.clear();
        Time.sleep(900, 1400);
    }

    private GrandExchangeSlot findSlot(APIContext ctx, GeAction action) {
        for (GrandExchangeSlot slot : ctx.grandExchange().getSlots()) {
            if (slot == null || !slot.inUse() || slot.getOffer() == null) {
                continue;
            }
            GrandExchangeOffer offer = slot.getOffer();
            if (!nameMatches(offer.getItemName(), action.itemName)) {
                continue;
            }
            String stateName = slot.getState() == null ? "" : slot.getState().name();
            boolean buy = stateName.contains("BUY") || stateName.contains("BOUGHT");
            boolean sell = stateName.contains("SELL") || stateName.contains("SOLD");
            if ((action.type == GeActionType.BUY && buy) || (action.type == GeActionType.SELL && sell)) {
                return slot;
            }
        }
        return null;
    }

    private boolean confirmGeWarning(APIContext ctx) {
        if (!hasWidgetText(ctx, "much higher") && !hasWidgetText(ctx, "Are you sure")) {
            return false;
        }
        WidgetChild yes = findWidgetByText(ctx, "Yes");
        status = "Confirming GE warning";
        if (yes != null && (yes.click() || ctx.mouse().click(yes, false))) {
            Time.sleep(1000, 1500);
            return true;
        }
        Time.sleep(600, 900);
        return true;
    }

    private GearItem firstMissingGear(APIContext ctx) {
        for (GearItem item : gearPlan.items) {
            if (item.optional && !ownsAnywhere(ctx, item.name)) {
                continue;
            }
            if (!ctx.equipment().contains(item.slot, equippedItem -> itemNameMatches(equippedItem, item.name))) {
                return item;
            }
        }
        return null;
    }

    private boolean withdrawOrEquip(APIContext ctx, GearItem item) {
        if (ctx.equipment().contains(item.slot, equippedItem -> itemNameMatches(equippedItem, item.name))) {
            return true;
        }
        if (!inventoryContains(ctx, item.name)) {
            if (bankContains(ctx, item.name)) {
                status = "Withdrawing " + item.name;
                ctx.bank().withdraw(1, bankItem -> itemNameMatches(bankItem, item.name));
                Time.sleep(600, 900, () -> inventoryContains(ctx, item.name), 100);
                return true;
            }
            return false;
        }
        return equipInventoryItem(ctx, item.action, item.name,
                () -> ctx.equipment().contains(item.slot, equippedItem -> itemNameMatches(equippedItem, item.name)));
    }

    private boolean equipInventorySetupItem(APIContext ctx) {
        if (gearPlan != null) {
            for (GearItem item : gearPlan.items) {
                if (!ctx.equipment().contains(item.slot, bankItem -> itemNameMatches(bankItem, item.name))
                        && inventoryContains(ctx, item.name)) {
                    return equipInventoryItem(ctx, item.action, item.name,
                            () -> ctx.equipment().contains(item.slot, bankItem -> itemNameMatches(bankItem, item.name)));
                }
            }
        }

        if (!hasChargedGloryEquipped(ctx)) {
            for (String glory : reverse(CHARGED_GLORIES)) {
                if (inventoryContains(ctx, glory)) {
                    return equipInventoryItem(ctx, "Wear", glory, () -> hasChargedGloryEquipped(ctx));
                }
            }
        }

        if (gearPlan != null
                && gearPlan.mode == CombatMode.RANGED
                && gearPlan.ammoName != null
                && ctx.equipment().getCount(gearPlan.ammoName) < COMBAT_AMMO / 2
                && inventoryContains(ctx, gearPlan.ammoName)) {
            return equipInventoryItem(ctx, "Wield", gearPlan.ammoName,
                    () -> equipmentContains(ctx, gearPlan.ammoName));
        }
        return false;
    }

    private boolean equipInventoryItem(APIContext ctx, String action, String itemName, java.util.function.BooleanSupplier equipped) {
        status = "Equipping " + itemName;
        if (ctx.bank().isOpen()) {
            closeBank(ctx);
            return true;
        }

        ItemWidget item = inventoryItem(ctx, itemName);
        if (item == null) {
            getLogger().info("[ChaosDruid] equip failed: inventory item not found " + itemName);
            return false;
        }

        clearInteractionState(ctx);
        getLogger().info("[ChaosDruid] equipping " + itemName + " primary=" + action + " actions=" + item.getActions());
        for (String candidateAction : equipActions(action)) {
            if (equipped.getAsBoolean()) {
                return true;
            }
            if (!item.isValid()) {
                item = inventoryItem(ctx, itemName);
                if (item == null) {
                    return equipped.getAsBoolean();
                }
            }
            status = "Equipping " + itemName + " via " + candidateAction;
            boolean interacted = item.interact(candidateAction)
                    || ctx.inventory().interactItem(candidateAction, itemName);
            Time.sleep(700, 1100, equipped::getAsBoolean, 100);
            if (equipped.getAsBoolean()) {
                getLogger().info("[ChaosDruid] equipped " + itemName + " via " + candidateAction);
                return true;
            }
            if (ctx.menu().isOpen()) {
                getLogger().info("[ChaosDruid] closing menu after failed equip action " + candidateAction
                        + " for " + itemName + " interacted=" + interacted
                        + " menuActions=" + ctx.menu().getActions());
                ctx.menu().closeMenu();
                Time.sleep(200, 400);
            }
        }
        getLogger().info("[ChaosDruid] equip not confirmed: " + itemName);
        return true;
    }

    private boolean ensureChargedGloryEquipped(APIContext ctx) {
        if (hasChargedGloryEquipped(ctx)) {
            return false;
        }
        for (String glory : reverse(CHARGED_GLORIES)) {
            if (ctx.inventory().contains(glory)) {
                status = "Equipping " + glory;
                if (ctx.bank().isOpen()) {
                    closeBank(ctx);
                    return true;
                }
                return equipInventoryItem(ctx, "Wear", glory, () -> hasChargedGloryEquipped(ctx));
            }
            if (ctx.bank().contains(glory)) {
                status = "Withdrawing " + glory;
                ctx.bank().withdraw(1, glory);
                Time.sleep(600, 900, () -> ctx.inventory().contains(glory), 100);
                return true;
            }
        }
        state = State.BUILD_RESTOCK;
        return true;
    }

    private boolean ensureAmmo(APIContext ctx) {
        if (gearPlan.ammoName == null) {
            return false;
        }
        int equipped = ctx.equipment().getCount(gearPlan.ammoName);
        if (equipped >= COMBAT_AMMO / 2) {
            return false;
        }
        if (!ctx.inventory().contains(gearPlan.ammoName)) {
            int needed = Math.max(1, COMBAT_AMMO - equipped);
            if (ctx.bank().contains(gearPlan.ammoName)) {
                status = "Withdrawing arrows";
                ctx.bank().withdraw(Math.min(needed, ctx.bank().getCount(gearPlan.ammoName)), gearPlan.ammoName);
                Time.sleep(600, 900);
                return true;
            }
            state = State.BUILD_RESTOCK;
            return true;
        }
        return equipInventoryItem(ctx, "Wield", gearPlan.ammoName, () -> equipmentContains(ctx, gearPlan.ammoName));
    }

    private boolean ensureFood(APIContext ctx) {
        if (realLevel(ctx, Skill.Skills.DEFENCE) >= 50) {
            return false;
        }
        int food = ctx.inventory().getCount(FOOD);
        if (food >= COMBAT_FOOD) {
            return false;
        }
        if (ctx.bank().contains(FOOD)) {
            status = "Withdrawing food";
            ctx.bank().withdraw(Math.min(COMBAT_FOOD - food, ctx.bank().getCount(FOOD)), FOOD);
            Time.sleep(600, 900);
            return true;
        }
        state = State.BUILD_RESTOCK;
        return true;
    }

    private boolean ensureLootingBag(APIContext ctx) {
        if (ctx.inventory().contains(LOOTING_BAG_OPEN) || ctx.inventory().contains(LOOTING_BAG_CLOSED)) {
            return false;
        }
        if (ctx.bank().contains(LOOTING_BAG_OPEN)) {
            status = "Withdrawing looting bag";
            ctx.bank().withdraw(1, LOOTING_BAG_OPEN);
            Time.sleep(600, 900);
            return true;
        }
        if (ctx.bank().contains(LOOTING_BAG_CLOSED)) {
            status = "Withdrawing looting bag";
            ctx.bank().withdraw(1, LOOTING_BAG_CLOSED);
            Time.sleep(600, 900);
            return true;
        }
        return false;
    }

    private boolean emptyLootingBag(APIContext ctx) {
        if (!ctx.inventory().contains(LOOTING_BAG_OPEN) && !ctx.inventory().contains(LOOTING_BAG_CLOSED)) {
            return false;
        }
        WidgetChild emptyContainers = findWidgetByText(ctx, "Empty containers");
        if (emptyContainers != null) {
            status = "Emptying looting bag";
            if (emptyContainers.interact("Empty containers") || emptyContainers.click()) {
                Time.sleep(900, 1400);
                return true;
            }
        }
        return false;
    }

    private boolean gearLooksReady(APIContext ctx) {
        return firstMissingGear(ctx) == null
                && hasChargedGloryEquipped(ctx)
                && (gearPlan.mode != CombatMode.RANGED || ctx.equipment().contains(gearPlan.ammoName));
    }

    private boolean shouldReturnForSupplies(APIContext ctx) {
        if (ctx.inventory().isFull()) {
            status = "Inventory full";
            return true;
        }
        if (realLevel(ctx, Skill.Skills.DEFENCE) < 50
                && ctx.inventory().getCount(FOOD) <= 0
                && hpPercent(ctx) < RETURN_HP_PERCENT) {
            status = "Low HP with no food";
            return true;
        }
        if (gearPlan.mode == CombatMode.RANGED && ctx.equipment().getCount(gearPlan.ammoName) <= 0) {
            status = "Out of arrows";
            return true;
        }
        return false;
    }

    private boolean shouldEat(APIContext ctx) {
        return hpPercent(ctx) < EAT_HP_PERCENT && ctx.inventory().contains(FOOD);
    }

    private GroundItem findLootTarget(APIContext ctx) {
        List<GroundItem> items = ctx.groundItems().getAll(14, item ->
                item != null
                        && item.isValid()
                        && item.getName() != null
                        && LOOT_NAMES.contains(normalizedName(item.getName()))
                        && CHAOS_DRUIDS_AREA.contains(item.getLocation()));
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .max(Comparator
                        .comparingInt((GroundItem item) -> lootPriority(item.getName()))
                        .thenComparingLong(item -> estimatedValue(ctx, item.getName(), Math.max(1, item.getStackSize())))
                        .thenComparingInt(item -> -distanceTo(ctx, item)))
                .orElse(null);
    }

    private NPC findAttackTarget(APIContext ctx) {
        List<NPC> npcs = ctx.npcs().getAll(npc ->
                npc != null
                        && npc.isValid()
                        && npc.getId() == CHAOS_DRUID_ID
                        && CHAOS_DRUIDS_AREA.contains(npc.getLocation())
                        && distanceTo(ctx, npc) <= 12
                        && !npc.isInCombat());
        if (npcs == null || npcs.isEmpty()) {
            return null;
        }
        return npcs.stream().min(Comparator.comparingInt(npc -> distanceTo(ctx, npc))).orElse(null);
    }

    private NPC attackingPlayer(APIContext ctx) {
        List<NPC> npcs = ctx.npcs().getAll(npc ->
                npc != null
                        && npc.isValid()
                        && npc.getId() == CHAOS_DRUID_ID
                        && npc.getInteracting() != null
                        && npc.getInteracting().equals(ctx.localPlayer()));
        if (npcs == null || npcs.isEmpty()) {
            return null;
        }
        return npcs.stream().min(Comparator.comparingInt(npc -> distanceTo(ctx, npc))).orElse(null);
    }

    private boolean shouldWorldHop(APIContext ctx) {
        if (System.currentTimeMillis() < nextWorldHopCheckAt) {
            return false;
        }
        nextWorldHopCheckAt = System.currentTimeMillis() + 5_000L;
        long players = ctx.players().getAll(player ->
                player != null
                        && player.isValid()
                        && !player.equals(ctx.localPlayer())
                        && CHAOS_DRUIDS_AREA.contains(player.getLocation())).size();
        return players >= MAX_OTHER_PLAYERS;
    }

    private boolean isSafeMembersWorld(World world, int currentWorld) {
        if (world == null || world.getId() == currentWorld || !world.isMembers()) {
            return false;
        }
        if (world.getPopulation() <= 0 || world.getPopulation() >= 1800) {
            return false;
        }
        if (WorldType.isPvpWorld(world.getTypes())) {
            return false;
        }
        for (WorldType blocked : BLOCKED_WORLD_TYPES) {
            if (world.getTypes().contains(blocked)) {
                return false;
            }
        }
        return true;
    }

    private void maybeSpecialAttack(APIContext ctx) {
        if (gearPlan.mode != CombatMode.RANGED || !ctx.equipment().contains(MSB_IMBUED)) {
            return;
        }
        if (ctx.combat().isSpecialActive() || ctx.combat().getSpecialAttackEnergy() < 55) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) >= 20) {
            return;
        }
        status = "Toggling special attack";
        ctx.combat().toggleSpecialAttack(true);
    }

    private boolean ensureCombatStyle(APIContext ctx) {
        if (ctx.bank().isOpen()
                || ctx.grandExchange().isOpen()
                || ctx.localPlayer().isMoving()
                || ctx.localPlayer().isInCombat()
                || ctx.localPlayer().isAttacking()) {
            return false;
        }
        return activeMode == CombatMode.MELEE
                ? ensureMeleeCombatStyle(ctx)
                : ensureRangedCombatStyle(ctx);
    }

    private boolean ensureMeleeCombatStyle(APIContext ctx) {
        Skill.Skills targetSkill = plannedMeleeTrainingSkill(ctx);
        ICombatAPI.AttackStyle desiredStyle = attackStyleForSkill(targetSkill);
        if (desiredStyle == null) {
            return false;
        }
        if (!ctx.combat().hasOption(desiredStyle)) {
            if (ctx.combat().hasOption(ICombatAPI.AttackStyle.CONTROLLED)) {
                desiredStyle = ICombatAPI.AttackStyle.CONTROLLED;
            } else {
                status = "Combat style unavailable: " + friendlySkillName(targetSkill);
                return false;
            }
        }
        if (ctx.combat().getAttackStyle() == desiredStyle) {
            return false;
        }

        status = "Switching style to " + friendlySkillName(targetSkill);
        ctx.tabs().open(ITabsAPI.Tabs.COMBAT_OPTIONS);
        Time.sleep(250, 450);
        if (ctx.combat().toggleAttackStyle(desiredStyle)) {
            final ICombatAPI.AttackStyle selectedStyle = desiredStyle;
            Time.sleep(600, 900, () -> ctx.combat().getAttackStyle() == selectedStyle, 100);
            return true;
        }
        return false;
    }

    private boolean ensureRangedCombatStyle(APIContext ctx) {
        ICombatAPI.AttackStyle desiredStyle = ICombatAPI.AttackStyle.RANGING;
        if (!ctx.combat().hasOption(desiredStyle)) {
            if (ctx.combat().hasOption(ICombatAPI.AttackStyle.ACCURATERANGING)) {
                desiredStyle = ICombatAPI.AttackStyle.ACCURATERANGING;
            } else if (ctx.combat().hasOption(ICombatAPI.AttackStyle.LONGRANGE)) {
                desiredStyle = ICombatAPI.AttackStyle.LONGRANGE;
            } else {
                status = "Ranged style unavailable";
                return false;
            }
        }
        if (ctx.combat().getAttackStyle() == desiredStyle) {
            return false;
        }

        status = "Switching style to Ranged";
        ctx.tabs().open(ITabsAPI.Tabs.COMBAT_OPTIONS);
        Time.sleep(250, 450);
        if (ctx.combat().toggleAttackStyle(desiredStyle)) {
            final ICombatAPI.AttackStyle selectedStyle = desiredStyle;
            Time.sleep(600, 900, () -> ctx.combat().getAttackStyle() == selectedStyle, 100);
            return true;
        }
        return false;
    }

    private Skill.Skills plannedMeleeTrainingSkill(APIContext ctx) {
        if (meleeTrainingSkill == null || System.currentTimeMillis() >= meleeStyleSwitchAt) {
            chooseNextMeleeTrainingSkill(ctx);
        }
        return meleeTrainingSkill;
    }

    private void chooseNextMeleeTrainingSkill(APIContext ctx) {
        Skill.Skills previous = meleeTrainingSkill;
        List<Skill.Skills> candidates = meleeTrainingCandidates(ctx, previous);
        meleeTrainingSkill = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        long duration = randomLong(MELEE_STYLE_MIN_MS, MELEE_STYLE_MAX_MS);
        meleeStyleSwitchAt = System.currentTimeMillis() + duration;
        log("Melee style plan: " + friendlySkillName(meleeTrainingSkill)
                + " for " + Math.max(1, duration / 60_000L) + " min"
                + " (A/S/D " + realLevel(ctx, Skill.Skills.ATTACK)
                + "/" + realLevel(ctx, Skill.Skills.STRENGTH)
                + "/" + realLevel(ctx, Skill.Skills.DEFENCE) + ")");
    }

    private List<Skill.Skills> meleeTrainingCandidates(APIContext ctx, Skill.Skills previous) {
        Skill.Skills[] skills = {Skill.Skills.ATTACK, Skill.Skills.STRENGTH, Skill.Skills.DEFENCE};
        int lowest = Math.min(realLevel(ctx, Skill.Skills.ATTACK),
                Math.min(realLevel(ctx, Skill.Skills.STRENGTH), realLevel(ctx, Skill.Skills.DEFENCE)));

        List<Skill.Skills> candidates = new ArrayList<>();
        for (Skill.Skills skill : skills) {
            if (realLevel(ctx, skill) <= lowest + MELEE_STYLE_ROTATION_BAND) {
                candidates.add(skill);
            }
        }
        if (candidates.size() > 1 && previous != null) {
            candidates.remove(previous);
        }
        if (candidates.isEmpty()) {
            candidates.add(lowestMeleeSkill(ctx));
        }
        return candidates;
    }

    private Skill.Skills lowestMeleeSkill(APIContext ctx) {
        int attack = realLevel(ctx, Skill.Skills.ATTACK);
        int strength = realLevel(ctx, Skill.Skills.STRENGTH);
        int defence = realLevel(ctx, Skill.Skills.DEFENCE);
        if (attack <= strength && attack <= defence) {
            return Skill.Skills.ATTACK;
        }
        if (strength <= attack && strength <= defence) {
            return Skill.Skills.STRENGTH;
        }
        return Skill.Skills.DEFENCE;
    }

    private ICombatAPI.AttackStyle attackStyleForSkill(Skill.Skills skill) {
        if (skill == Skill.Skills.ATTACK) {
            return ICombatAPI.AttackStyle.ACCURATE;
        }
        if (skill == Skill.Skills.STRENGTH) {
            return ICombatAPI.AttackStyle.AGGRESSIVE;
        }
        if (skill == Skill.Skills.DEFENCE) {
            return ICombatAPI.AttackStyle.DEFENSIVE;
        }
        return null;
    }

    private String friendlySkillName(Skill.Skills skill) {
        if (skill == Skill.Skills.ATTACK) {
            return "Attack";
        }
        if (skill == Skill.Skills.STRENGTH) {
            return "Strength";
        }
        if (skill == Skill.Skills.DEFENCE) {
            return "Defence";
        }
        return skill == null ? "-" : skill.name();
    }

    private void storeLootInBag(APIContext ctx, String itemName) {
        if (!ctx.inventory().contains(itemName) || !ctx.inventory().contains(LOOTING_BAG_OPEN)) {
            return;
        }
        status = "Storing loot in bag";
        ctx.inventory().selectItem(itemName);
        Time.sleep(150, 300);
        ctx.inventory().interactItem("Use", LOOTING_BAG_OPEN);
        Time.sleep(400, 700);
    }

    private boolean tryGloryTeleport(APIContext ctx) {
        ItemWidget amulet = ctx.equipment().getItem(IEquipmentAPI.Slot.NECK);
        if (amulet != null && isChargedGlory(amulet.getName())) {
            status = "Glory teleport to Edgeville";
            if (amulet.interact("Edgeville")) {
                Time.sleep(3500, 5500, () -> EDGEVILLE_BANK_AREA.contains(ctx.localPlayer().getLocation()), 100);
                return true;
            }
        }
        for (String glory : CHARGED_GLORIES) {
            if (ctx.inventory().contains(glory)) {
                status = "Rubbing glory";
                if (ctx.inventory().interactItem("Rub", glory)) {
                    Time.sleep(700, 1000);
                    WidgetChild edgeville = findWidgetByText(ctx, "Edgeville");
                    if (edgeville != null && edgeville.click()) {
                        Time.sleep(3500, 5500, () -> EDGEVILLE_BANK_AREA.contains(ctx.localPlayer().getLocation()), 100);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void handleTrapdoor(APIContext ctx) {
        SceneObject trapdoor = nearestTrapdoor(ctx);
        if (trapdoor == null) {
            status = "Walking closer to trapdoor";
            walkTo(ctx, EDGEVILLE_TRAPDOOR, false);
            Time.sleep(900, 1400);
            return;
        }

        if (climbDownTrapdoor(ctx, trapdoor)) {
            return;
        }

        status = "Opening Edgeville trapdoor";
        getLogger().info("[ChaosDruid] opening trapdoor id=" + trapdoor.getId()
                + " tile=" + trapdoor.getLocation()
                + " actions=" + trapdoor.getActions());
        boolean opened = trapdoor.interact("Open", "Trapdoor")
                || trapdoor.interact("Open")
                || trapdoor.interactMatch("Open")
                || ctx.menu().interact("Open", "Trapdoor", trapdoor, true)
                || ctx.menu().interact("Open", trapdoor, true)
                || ctx.menu().interact("Open", trapdoor, false);
        if (!opened) {
            getLogger().info("[ChaosDruid] trapdoor open interaction failed; menuActions=" + ctx.menu().getActions());
            clearInteractionState(ctx);
            Time.sleep(500, 800);
            return;
        }

        Time.sleep(700, 1200, () -> isInEdgevilleDungeon(ctx) || hasClimbDownTrapdoor(ctx), 100);
        if (isInEdgevilleDungeon(ctx)) {
            return;
        }

        trapdoor = nearestTrapdoor(ctx);
        if (trapdoor != null) {
            climbDownTrapdoor(ctx, trapdoor);
        }
    }

    private SceneObject nearestTrapdoor(APIContext ctx) {
        return ctx.objects()
                .query()
                .nameContains("Trapdoor")
                .tileDistance(15)
                .results()
                .nearest();
    }

    private boolean hasClimbDownTrapdoor(APIContext ctx) {
        SceneObject trapdoor = nearestTrapdoor(ctx);
        return trapdoor != null && trapdoor.hasAction("Climb-down");
    }

    private boolean climbDownTrapdoor(APIContext ctx, SceneObject trapdoor) {
        if (trapdoor == null || !trapdoor.hasAction("Climb-down")) {
            return false;
        }
        clearInteractionState(ctx);
        status = "Climbing down Edgeville trapdoor";
        getLogger().info("[ChaosDruid] climbing trapdoor id=" + trapdoor.getId()
                + " tile=" + trapdoor.getLocation()
                + " actions=" + trapdoor.getActions());
        boolean clicked = trapdoor.interact("Climb-down", "Trapdoor")
                || trapdoor.interact("Climb-down")
                || trapdoor.interactMatch("Climb-down")
                || ctx.menu().interact("Climb-down", "Trapdoor", trapdoor, true)
                || ctx.menu().interact("Climb-down", trapdoor, true)
                || ctx.menu().interact("Climb-down", trapdoor, false);
        if (!clicked) {
            getLogger().info("[ChaosDruid] trapdoor climb interaction failed; menuActions=" + ctx.menu().getActions());
            clearInteractionState(ctx);
            Time.sleep(500, 800);
            return true;
        }
        Time.sleep(2500, 4500, () -> isInEdgevilleDungeon(ctx), 100);
        return true;
    }

    private boolean openBank(APIContext ctx, String reason) {
        if (ctx.bank().isOpen()) {
            return true;
        }
        if (ctx.grandExchange().isOpen()) {
            ctx.grandExchange().close();
            Time.sleep(600, 900);
            return false;
        }
        if (ctx.bank().isReachable() || ctx.bank().isVisible()) {
            status = "Opening bank: " + reason;
            ctx.bank().open();
            Time.sleep(1200, 1800, () -> ctx.bank().isOpen(), 100);
            return ctx.bank().isOpen();
        }
        SceneObject bankObject = ctx.objects()
                .query()
                .actions("Bank")
                .tileDistance(16)
                .results()
                .nearest();
        if (bankObject != null && bankObject.interact("Bank")) {
            status = "Opening bank object";
            Time.sleep(1200, 1800, () -> ctx.bank().isOpen(), 100);
            return ctx.bank().isOpen();
        }
        NPC banker = ctx.npcs()
                .query()
                .named("Banker")
                .actions("Bank")
                .tileDistance(16)
                .results()
                .nearest();
        if (banker != null && banker.interact("Bank")) {
            status = "Opening banker";
            Time.sleep(1200, 1800, () -> ctx.bank().isOpen(), 100);
            return ctx.bank().isOpen();
        }
        status = "Walking to bank: " + reason;
        walkToBank(ctx);
        Time.sleep(1200, 1800);
        return false;
    }

    private void closeBank(APIContext ctx) {
        if (!ctx.bank().isOpen()) {
            return;
        }
        ctx.bank().close();
        Time.sleep(500, 900, () -> !ctx.bank().isOpen(), 100);
    }

    private boolean isAtAnyBank(APIContext ctx) {
        return ctx.bank().isReachable()
                || ctx.bank().isVisible()
                || EDGEVILLE_BANK_AREA.contains(ctx.localPlayer().getLocation())
                || GE_AREA.contains(ctx.localPlayer().getLocation());
    }

    private void walkToBank(APIContext ctx) {
        ctx.webWalking().setUseTeleports(true);
        WalkState result = ctx.webWalking().walkToBank();
        status = "Walking to bank: " + result;
    }

    private void walkTo(APIContext ctx, Tile tile, boolean teleports) {
        if (tile == null) {
            return;
        }
        ctx.webWalking().setUseTeleports(teleports);
        if (distanceTo(ctx, tile) <= 12) {
            ctx.walking().walkTo(tile);
            return;
        }
        ctx.webWalking().walkTo(tile);
    }

    private void handleDialogue(APIContext ctx) {
        if (!ctx.dialogues().isDialogueOpen()) {
            return;
        }
        if (ctx.dialogues().canContinue()) {
            ctx.dialogues().selectContinue();
            Time.sleep(500, 800);
        }
    }

    private void runAntiban(APIContext ctx) {
        if (System.currentTimeMillis() < nextAntibanAt
                || ctx.bank().isOpen()
                || ctx.grandExchange().isOpen()
                || ctx.localPlayer().isInCombat()) {
            return;
        }
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 35) {
            ctx.camera().setYawDeg(ThreadLocalRandom.current().nextInt(360));
        } else if (roll < 70) {
            ctx.mouse().moveRandomly(80, 180);
        } else {
            ctx.tabs().open(ITabsAPI.Tabs.INVENTORY);
        }
        scheduleAntiban();
    }

    private void scheduleAntiban() {
        nextAntibanAt = System.currentTimeMillis() + randomLong(45_000L, 95_000L);
    }

    private boolean clearInteractionState(APIContext ctx) {
        if (ctx == null) {
            return false;
        }
        boolean cleared = false;
        try {
            if (ctx.menu().isOpen()) {
                ctx.menu().closeMenu();
                cleared = true;
            }
            if (ctx.inventory().isItemSelected()) {
                ctx.inventory().deselectItem();
                cleared = true;
            }
        } catch (RuntimeException ignored) {
            // Best-effort cleanup.
        }
        return cleared;
    }

    private boolean hasOpenLootingBag(APIContext ctx) {
        return ctx.inventory().contains(LOOTING_BAG_OPEN);
    }

    private boolean hasChargedGloryEquipped(APIContext ctx) {
        for (String glory : CHARGED_GLORIES) {
            if (ctx.equipment().contains(IEquipmentAPI.Slot.NECK, glory)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyChargedGloryAnywhere(APIContext ctx) {
        return totalChargedGlories(ctx) > 0;
    }

    private boolean ownsAnywhere(APIContext ctx, String itemName) {
        return countAnywhere(ctx, itemName) > 0;
    }

    private int countAnywhere(APIContext ctx, String itemName) {
        int count = inventoryCount(ctx, itemName) + equipmentCount(ctx, itemName);
        if (ctx.bank().isOpen()) {
            count += bankCount(ctx, itemName);
        }
        return count;
    }

    private int inventoryCount(APIContext ctx, String itemName) {
        int count = 0;
        for (ItemWidget item : ctx.inventory().getItems()) {
            if (itemNameMatches(item, itemName)) {
                count += Math.max(1, item.getStackSize());
            }
        }
        return count;
    }

    private int equipmentCount(APIContext ctx, String itemName) {
        int count = 0;
        for (ItemWidget item : ctx.equipment().getItems()) {
            if (itemNameMatches(item, itemName)) {
                count += Math.max(1, item.getStackSize());
            }
        }
        return count;
    }

    private int bankCount(APIContext ctx, String itemName) {
        if (!ctx.bank().isOpen()) {
            return 0;
        }
        int count = 0;
        for (ItemWidget item : ctx.bank().getItems()) {
            if (itemNameMatches(item, itemName)) {
                count += Math.max(1, item.getStackSize());
            }
        }
        return count;
    }

    private int visibleBankItems(APIContext ctx) {
        return ctx.bank().isOpen() ? ctx.bank().getItems().size() : 0;
    }

    private boolean waitForBankSnapshot(APIContext ctx) {
        if (!ctx.bank().isOpen()) {
            return false;
        }
        if (visibleBankItems(ctx) > 0) {
            return true;
        }
        status = "Waiting for bank snapshot";
        getLogger().info("[ChaosDruid] bank snapshot empty; delaying restock to avoid duplicate GE buys");
        Time.sleep(800, 1200);
        return visibleBankItems(ctx) > 0;
    }

    private ItemWidget inventoryItem(APIContext ctx, String itemName) {
        return ctx.inventory().getItem(item -> itemNameMatches(item, itemName));
    }

    private List<String> equipActions(String primaryAction) {
        List<String> actions = new ArrayList<>();
        addAction(actions, primaryAction);
        addAction(actions, "Wield");
        addAction(actions, "Wear");
        addAction(actions, "Equip");
        return actions;
    }

    private void addAction(List<String> actions, String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        for (String existing : actions) {
            if (existing.equalsIgnoreCase(action)) {
                return;
            }
        }
        actions.add(action);
    }

    private boolean inventoryContains(APIContext ctx, String itemName) {
        return inventoryCount(ctx, itemName) > 0;
    }

    private boolean equipmentContains(APIContext ctx, String itemName) {
        return equipmentCount(ctx, itemName) > 0;
    }

    private boolean bankContains(APIContext ctx, String itemName) {
        return bankCount(ctx, itemName) > 0;
    }

    private boolean itemNameMatches(Item item, String itemName) {
        return item != null && nameMatches(item.getName(), itemName);
    }

    private int totalChargedGlories(APIContext ctx) {
        int count = 0;
        for (String glory : CHARGED_GLORIES) {
            count += countAnywhere(ctx, glory);
        }
        return count;
    }

    private int totalChargedRows(APIContext ctx) {
        int count = 0;
        for (String row : CHARGED_ROWS) {
            count += countAnywhere(ctx, row);
        }
        return count;
    }

    private long bankLootValue(APIContext ctx) {
        long total = 0L;
        for (String loot : LOOT_NAMES) {
            if (nameMatches(loot, LOOTING_BAG_CLOSED)) {
                continue;
            }
            int count = ctx.bank().getCount(loot);
            if (count > 0) {
                total += estimatedValue(ctx, loot, count);
            }
        }
        return total;
    }

    private int buyPrice(APIContext ctx, String itemName) {
        return Math.max(1, (int) Math.ceil(guidePrice(ctx, itemName) * 1.25));
    }

    private int sellPrice(APIContext ctx, String itemName) {
        return Math.max(1, (int) Math.floor(guidePrice(ctx, itemName) * 0.65));
    }

    private int guidePrice(APIContext ctx, String itemName) {
        try {
            ItemDetail detail = ctx.pricing().get(itemName);
            if (detail != null) {
                int price = Math.max(detail.getHighestPrice(), detail.getLowestPrice());
                if (price > 0) {
                    return price;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to conservative fallback.
        }
        return fallbackPrice(itemName);
    }

    private long estimatedValue(APIContext ctx, String itemName, int quantity) {
        if (quantity <= 0) {
            return 0L;
        }
        if (nameMatches(itemName, "Coins")) {
            return quantity;
        }
        return (long) Math.max(1, sellPrice(ctx, itemName)) * quantity;
    }

    private int fallbackPrice(String itemName) {
        if (itemName == null) {
            return 1;
        }
        String n = normalizedName(itemName);
        if (n.contains("ranarr")) return 6_000;
        if (n.contains("kwuarm")) return 2_000;
        if (n.contains("avantoe")) return 1_500;
        if (n.contains("irit")) return 1_000;
        if (n.contains("law rune")) return 120;
        if (n.contains("nature rune")) return 100;
        if (n.contains("mithril bolts")) return 60;
        if (n.contains("tuna")) return 100;
        if (n.contains("glory")) return 14_000;
        if (n.contains("ring of wealth")) return 12_000;
        if (n.contains("rune")) return 25_000;
        if (n.contains("adamant")) return 8_000;
        if (n.contains("mithril")) return 2_000;
        return 500;
    }

    private int inventoryCountIncludingStacks(APIContext ctx, String itemName) {
        int count = 0;
        for (ItemWidget item : ctx.inventory().getItems()) {
            if (item != null && nameMatches(item.getName(), itemName)) {
                count += Math.max(1, item.getStackSize());
            }
        }
        return count;
    }

    private int lootPriority(String name) {
        String n = normalizedName(name);
        if (n.contains("looting bag")) return 5;
        if (n.contains("ensouled")) return 4;
        if (n.contains("ranarr") || n.contains("kwuarm") || n.contains("nature rune") || n.contains("law rune")) return 3;
        if (n.contains("irit") || n.contains("avantoe")) return 2;
        return 1;
    }

    private int hpPercent(APIContext ctx) {
        int max = realLevel(ctx, Skill.Skills.HITPOINTS);
        int current = boostedLevel(ctx, Skill.Skills.HITPOINTS);
        return max <= 0 ? 100 : current * 100 / max;
    }

    private int realLevel(APIContext ctx, Skill.Skills skill) {
        return ctx.skills().get(skill).getRealLevel();
    }

    private int boostedLevel(APIContext ctx, Skill.Skills skill) {
        return ctx.skills().get(skill).getCurrentLevel();
    }

    private int skillXp(APIContext ctx, Skill.Skills skill) {
        return ctx.skills().get(skill).getExperience();
    }

    private int xpGained(APIContext ctx, Skill.Skills skill, int startXp) {
        if (ctx == null) {
            return 0;
        }
        return Math.max(0, skillXp(ctx, skill) - startXp);
    }

    private int meleeXpGained(APIContext ctx) {
        if (ctx == null) {
            return 0;
        }
        return xpGained(ctx, Skill.Skills.ATTACK, startAttackXp)
                + xpGained(ctx, Skill.Skills.STRENGTH, startStrengthXp)
                + xpGained(ctx, Skill.Skills.DEFENCE, startDefenceXp);
    }

    private boolean isInEdgevilleDungeon(APIContext ctx) {
        Tile location = ctx.localPlayer().getLocation();
        return location != null && location.getY() > 9000;
    }

    private boolean isLikelyDeathsOffice(APIContext ctx) {
        Tile location = ctx.localPlayer().getLocation();
        return location != null && location.getX() >= 3225 && location.getX() <= 3255
                && location.getY() >= 3180 && location.getY() <= 3205;
    }

    private int distanceTo(APIContext ctx, Locatable target) {
        Tile location = ctx.localPlayer().getLocation();
        Tile tile = target == null ? null : target.getLocation();
        if (location == null || tile == null || location.getPlane() != tile.getPlane()) {
            return 999;
        }
        return Math.max(Math.abs(location.getX() - tile.getX()), Math.abs(location.getY() - tile.getY()));
    }

    private void collectGeToBank(APIContext ctx) {
        try {
            ctx.grandExchange().collectToBank();
        } catch (RuntimeException ignored) {
            // Safe to retry next loop.
        }
    }

    private WidgetChild findWidgetByText(APIContext ctx, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ENGLISH);
        for (WidgetChild widget : ctx.widgets().getAllChildren(candidate -> {
            if (candidate == null || !candidate.isValid() || candidate.getWidth() <= 0 || candidate.getHeight() <= 0) {
                return false;
            }
            return containsIgnoreCase(candidate.getText(), lower)
                    || containsIgnoreCase(candidate.getRawText(), lower)
                    || actionContains(candidate, lower);
        })) {
            return widget;
        }
        return null;
    }

    private boolean hasWidgetText(APIContext ctx, String text) {
        return findWidgetByText(ctx, text) != null;
    }

    private boolean actionContains(WidgetChild widget, String lowerNeedle) {
        List<String> actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (containsIgnoreCase(action, lowerNeedle)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(Locale.ENGLISH).contains(lowerNeedle);
    }

    private String lootBagText(APIContext ctx) {
        if (ctx == null) {
            return "-";
        }
        if (lootBagFull) {
            return "Full";
        }
        if (ctx.inventory().contains(LOOTING_BAG_OPEN)) {
            return "Open";
        }
        if (ctx.inventory().contains(LOOTING_BAG_CLOSED)) {
            return "Closed";
        }
        return "None";
    }

    private String combatStyleText() {
        if (activeMode == CombatMode.RANGED) {
            return "Ranged";
        }
        if (meleeTrainingSkill == null) {
            return "Melee planning";
        }
        long remainingMs = Math.max(0L, meleeStyleSwitchAt - System.currentTimeMillis());
        long remainingMinutes = Math.max(1L, (remainingMs + 59_999L) / 60_000L);
        return friendlySkillName(meleeTrainingSkill) + " ~" + remainingMinutes + "m";
    }

    private void logLoopSnapshot(APIContext ctx) {
        long now = System.currentTimeMillis();
        String gear = gearPlan == null ? "-" : gearPlan.shortText();
        String style = combatStyleText();
        boolean changed = state != lastLoggedState
                || activeMode != lastLoggedMode
                || !Objects.equals(status, lastLoggedStatus)
                || !Objects.equals(gear, lastLoggedGear)
                || !Objects.equals(style, lastLoggedStyle);
        if (!changed && now < nextHeartbeatLogAt) {
            return;
        }

        StringBuilder message = new StringBuilder("[ChaosDruid] state=")
                .append(state)
                .append(" mode=").append(activeMode).append("/").append(configuredMode)
                .append(" style=").append(style)
                .append(" gear=").append(gear)
                .append(" status=").append(status);
        if (ctx != null && ctx.client().isLoggedIn()) {
            message.append(" food=").append(ctx.inventory().getCount(FOOD))
                    .append(" bag=").append(lootBagText(ctx))
                    .append(" lootGp=").append(estimatedLootGp)
                    .append(" ge=").append(geQueue.size()).append("/").append(activeGeActions.size());
        }
        getLogger().info(message.toString());

        lastLoggedState = state;
        lastLoggedMode = activeMode;
        lastLoggedStatus = status;
        lastLoggedGear = gear;
        lastLoggedStyle = style;
        nextHeartbeatLogAt = now + (changed ? 30_000L : 120_000L);
    }

    private String runtimeText() {
        long seconds = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000L);
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private String shortText(String value, int max) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(1, max - 3)) + "...";
    }

    private void resetLootState() {
        lootStartedAt = 0L;
        lootFails = 0;
    }

    private void travelDelay() {
        Time.sleep(600, 900);
    }

    private long randomLong(long minInclusive, long maxInclusive) {
        return ThreadLocalRandom.current().nextLong(minInclusive, maxInclusive + 1L);
    }

    private boolean isChargedGlory(String name) {
        for (String glory : CHARGED_GLORIES) {
            if (nameMatches(name, glory)) {
                return true;
            }
        }
        return false;
    }

    private List<String> reverse(String[] values) {
        List<String> reversed = new ArrayList<>(Arrays.asList(values));
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private boolean nameMatches(String left, String right) {
        return normalizedName(left).equals(normalizedName(right));
    }

    private String normalizedName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private static Set<String> normalizedSet(String... values) {
        Set<String> set = new HashSet<>();
        for (String value : values) {
            set.add(value.trim().toLowerCase(Locale.ENGLISH));
        }
        return set;
    }

    private void log(String message) {
        status = message;
        getLogger().info(message);
    }

    private enum State {
        STARTUP,
        BANK_DEPOSIT,
        BUILD_RESTOCK,
        GE_TRADING,
        BANK_SETUP,
        TRAVEL_TO_DRUIDS,
        COMBAT,
        LOOT,
        EAT,
        RETURN_TO_BANK,
        WORLD_HOP,
        DEATH_RECOVERY
    }

    private enum CombatMode {
        AUTO,
        RANGED,
        MELEE
    }

    private enum GeActionType {
        BUY,
        SELL
    }

    private static final class GeAction {
        final GeActionType type;
        final String itemName;
        final int quantity;
        int price;

        private GeAction(GeActionType type, String itemName, int quantity, int price) {
            this.type = type;
            this.itemName = itemName;
            this.quantity = Math.max(0, quantity);
            this.price = Math.max(1, price);
        }

        static GeAction buy(String itemName, int quantity, int price) {
            return new GeAction(GeActionType.BUY, itemName, quantity, price);
        }

        static GeAction sell(String itemName, int quantity, int price) {
            return new GeAction(GeActionType.SELL, itemName, quantity, price);
        }

        void increasePrice() {
            if (type == GeActionType.BUY) {
                price = Math.max(1, (int) Math.ceil(price * 1.12));
            } else {
                price = Math.max(1, (int) Math.floor(price * 0.85));
            }
        }

        String describe() {
            return type + " " + quantity + "x " + itemName + " @ " + price;
        }
    }

    private static final class GearItem {
        final IEquipmentAPI.Slot slot;
        final String name;
        final String action;
        final boolean optional;

        private GearItem(IEquipmentAPI.Slot slot, String name, String action) {
            this(slot, name, action, false);
        }

        private GearItem(IEquipmentAPI.Slot slot, String name, String action, boolean optional) {
            this.slot = slot;
            this.name = name;
            this.action = action;
            this.optional = optional;
        }
    }

    private static final class GearPlan {
        final CombatMode mode;
        final List<GearItem> items;
        final String ammoName;

        private GearPlan(CombatMode mode, List<GearItem> items, String ammoName) {
            this.mode = mode;
            this.items = items;
            this.ammoName = ammoName;
        }

        static GearPlan forMode(APIContext ctx, CombatMode mode) {
            return mode == CombatMode.MELEE ? melee(ctx) : ranged(ctx);
        }

        static GearPlan melee(APIContext ctx) {
            int attack = ctx.skills().get(Skill.Skills.ATTACK).getRealLevel();
            int defence = ctx.skills().get(Skill.Skills.DEFENCE).getRealLevel();
            List<GearItem> items = new ArrayList<>();
            items.add(new GearItem(IEquipmentAPI.Slot.WEAPON, meleeWeapon(attack), "Wield"));
            if (defence >= 40) {
                items.add(new GearItem(IEquipmentAPI.Slot.HELMET, "Rune full helm", "Wear"));
                items.add(new GearItem(IEquipmentAPI.Slot.BODY, "Rune chainbody", "Wear"));
                items.add(new GearItem(IEquipmentAPI.Slot.LEGS, "Rune platelegs", "Wear"));
                items.add(new GearItem(IEquipmentAPI.Slot.SHIELD, "Rune kiteshield", "Wield"));
            } else if (defence >= 30) {
                items.add(new GearItem(IEquipmentAPI.Slot.HELMET, "Adamant full helm", "Wear"));
                items.add(new GearItem(IEquipmentAPI.Slot.BODY, "Adamant chainbody", "Wear"));
                items.add(new GearItem(IEquipmentAPI.Slot.LEGS, "Adamant platelegs", "Wear"));
                items.add(new GearItem(IEquipmentAPI.Slot.SHIELD, "Adamant kiteshield", "Wield"));
            } else if (defence >= 20) {
                items.add(new GearItem(IEquipmentAPI.Slot.HELMET, "Mithril full helm", "Wear"));
                items.add(new GearItem(IEquipmentAPI.Slot.BODY, "Mithril chainbody", "Wear"));
                items.add(new GearItem(IEquipmentAPI.Slot.LEGS, "Mithril platelegs", "Wear"));
                items.add(new GearItem(IEquipmentAPI.Slot.SHIELD, "Mithril kiteshield", "Wield"));
            } else {
                items.add(new GearItem(IEquipmentAPI.Slot.BODY, "Hardleather body", "Wear"));
                items.add(new GearItem(IEquipmentAPI.Slot.LEGS, "Leather chaps", "Wear"));
            }
            items.add(new GearItem(IEquipmentAPI.Slot.NECK, "Amulet of glory(6)", "Wear", true));
            items.add(new GearItem(IEquipmentAPI.Slot.RING, "Ring of wealth (5)", "Wear", true));
            return new GearPlan(CombatMode.MELEE, items, null);
        }

        static GearPlan ranged(APIContext ctx) {
            int ranged = ctx.skills().get(Skill.Skills.RANGED).getRealLevel();
            int defence = ctx.skills().get(Skill.Skills.DEFENCE).getRealLevel();
            List<GearItem> items = new ArrayList<>();
            items.add(new GearItem(IEquipmentAPI.Slot.WEAPON, rangedWeapon(ranged), "Wield"));
            items.add(new GearItem(IEquipmentAPI.Slot.HELMET, ranged >= 30 && defence >= 30 ? "Snakeskin bandana" : "Coif", "Wear"));
            items.add(new GearItem(IEquipmentAPI.Slot.FEET, ranged >= 30 && defence >= 30 ? "Snakeskin boots" : "Leather boots", "Wear"));
            items.add(new GearItem(IEquipmentAPI.Slot.BODY, rangedBody(ranged, defence), "Wear"));
            items.add(new GearItem(IEquipmentAPI.Slot.LEGS, rangedChaps(ranged, defence), "Wear"));
            items.add(new GearItem(IEquipmentAPI.Slot.HANDS, ranged >= 40 ? "Green d'hide vambraces" : "Leather vambraces", "Wear"));
            items.add(new GearItem(IEquipmentAPI.Slot.CAPE, "Ava's accumulator", "Wear", true));
            items.add(new GearItem(IEquipmentAPI.Slot.NECK, "Amulet of glory(6)", "Wear", true));
            items.add(new GearItem(IEquipmentAPI.Slot.RING, "Ring of wealth (5)", "Wear", true));
            return new GearPlan(CombatMode.RANGED, items, rangedArrow(ranged));
        }

        String shortText() {
            String weapon = items.isEmpty() ? "-" : items.get(0).name;
            return mode + " " + weapon + (ammoName == null ? "" : " + " + ammoName);
        }

        private static String meleeWeapon(int attack) {
            if (attack >= 40) return "Rune scimitar";
            if (attack >= 30) return "Adamant scimitar";
            if (attack >= 20) return "Mithril scimitar";
            if (attack >= 5) return "Steel scimitar";
            return "Iron scimitar";
        }

        private static String rangedWeapon(int ranged) {
            if (ranged >= 50) return MSB_IMBUED;
            if (ranged >= 40) return "Yew shortbow";
            if (ranged >= 30) return "Maple shortbow";
            if (ranged >= 20) return "Willow shortbow";
            return "Oak shortbow";
        }

        private static String rangedArrow(int ranged) {
            if (ranged >= 50) return "Rune arrow";
            if (ranged >= 40) return "Adamant arrow";
            if (ranged >= 30) return "Mithril arrow";
            if (ranged >= 20) return "Steel arrow";
            if (ranged >= 10) return "Iron arrow";
            return "Bronze arrow";
        }

        private static String rangedBody(int ranged, int defence) {
            if (ranged >= 70) return "Black d'hide body";
            if (ranged >= 60) return "Red d'hide body";
            if (ranged >= 50) return "Blue d'hide body";
            if (ranged >= 30 && defence >= 30) return "Snakeskin body";
            if (ranged >= 20) return "Studded body";
            if (ranged >= 10) return "Hardleather body";
            return "Leather body";
        }

        private static String rangedChaps(int ranged, int defence) {
            if (ranged >= 70) return "Black d'hide chaps";
            if (ranged >= 60) return "Red d'hide chaps";
            if (ranged >= 50) return "Blue d'hide chaps";
            if (ranged >= 40) return "Green d'hide chaps";
            if (ranged >= 30 && defence >= 30) return "Snakeskin chaps";
            if (ranged >= 20) return "Studded chaps";
            return "Leather chaps";
        }
    }
}
