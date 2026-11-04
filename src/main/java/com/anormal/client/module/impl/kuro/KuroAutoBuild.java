package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.util.kuro.KuroAssistAim;
import com.anormal.client.util.kuro.KuroBaritoneBridge;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroBuildProgress;
import com.anormal.client.util.kuro.KuroRotationEngine;
import com.anormal.client.util.kuro.KuroRotations;
import com.anormal.client.util.kuro.KuroSchematicInfo;
import com.anormal.client.util.kuro.KuroSchematicLayout;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * kuro {@code AutoBuild} port: Baritone schematikani quradi, siz kuzata-
 * borasiz; yuklama tugaganda modul sizni qayta to'ldirish sandig'iga olib
 * boradi, keraklarni oladi va qurishni davom ettiradi. Sandiq, boshlanish
 * nuqtasi, fayl — chat xabarlari orqali boshqariladi
 * ({@link #command(String[])}; Anormal'da chat buyrug'i qatlami yo'q, ammo
 * u qatlam qo'shilsa shu metod tayyor). kuro'dagi {@code Setting.Button /
 * Setting.Text} va GUI ekranlar (SchematicPickerScreen, MaterialsScreen)
 * Anormalda yo'q, shuning uchun ular o'rniga ochiq metodlar: file →
 * {@link #setFile(String)}, list → {@link #listFiles()}, mats →
 * {@link #showMaterials()}, status → {@link #status()}, chest = look-at →
 * {@link #pickLooked()}, origin = where I stand → {@link #setOriginHere()},
 * folder → {@link #openFolder()}.
 *
 * <p>Profilaktika: kuryog'dosh cem qismlari zig'da: Baritone'dagi almashtirish
 * opsiyalardi Baritone'ning standalone-fabric 1.17.0 versiyada tekshiring.</p>
 */
public class KuroAutoBuild extends Module {

    public static KuroAutoBuild get() { return instance; }
    private static KuroAutoBuild instance;

    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("kuro-autobuild.txt");
    private static final String[] EXTENSIONS = {".schem", ".litematic", ".schematic"};
    /** pace 1..4 → rightClickSpeed (bloklarni joylash oralig'i, tick). */
    private static final int[] PACE_TICKS = {12, 20, 30, 44};

    // ------------------------------------------------------------------ settings

    private final NumberSetting camera = new NumberSetting("Camera", "1 steady, 2 smooth, 3 Baritone default", 2.0, 1.0, 3.0, 1.0);
    private final NumberSetting smoothTicks = new NumberSetting("Smooth look (ticks)", "Ticks of the eased look", 14.0, 4.0, 40.0, 1.0);
    private final NumberSetting turnSpeed = new NumberSetting("Chest look speed (deg/s)", "Peak camera speed", 110.0, 30.0, 360.0, 10.0);
    private final BooleanSetting calm = new BooleanSetting("Calm movement", true);
    private final NumberSetting pace = new NumberSetting("Build pace", "1 fast .. 4 slow", 3.0, 1.0, 4.0, 1.0);
    private final BooleanSetting layers = new BooleanSetting("Build in layers", true);
    private final BooleanSetting lookAtBlocks = new BooleanSetting("Look at the blocks it places/breaks", true);
    private final BooleanSetting openDoors = new BooleanSetting("Open doors / gates in the way", true);
    private final BooleanSetting closeSigns = new BooleanSetting("Close the sign editor automatically", true);
    private final NumberSetting signDelay = new NumberSetting("Sign close delay (ticks)", "Ticks before closing a sign editor", 4.0, 1.0, 20.0, 1.0);
    private final NumberSetting aimStyle = new NumberSetting("Aim style", "1 eased curve, 2 assist", 2.0, 1.0, 2.0, 1.0);
    private final NumberSetting assistSpeed = new NumberSetting("Assist aim speed", "1..10 pull strength", 5.0, 1.0, 10.0, 1.0);
    private final NumberSetting aimTime = new NumberSetting("Aim smoothness (ms)", "Minimum turn time", 280.0, 100.0, 900.0, 20.0);
    private final NumberSetting lowBlocks = new NumberSetting("Refill below (blocks)", "0 = only when empty", 0.0, 0.0, 640.0, 16.0);
    private final NumberSetting scaffold = new NumberSetting("Scaffold blocks to take", "dirt / cobble, 0 = none", 64.0, 0.0, 256.0, 16.0);
    private final NumberSetting stacks = new NumberSetting("Max stacks per trip", "Inventory slots used", 27.0, 1.0, 36.0, 1.0);
    private final NumberSetting keepFree = new NumberSetting("Free slots to keep", "Never empty these", 2.0, 0.0, 8.0, 1.0);
    private final NumberSetting clickDelay = new NumberSetting("Chest click delay (ticks)", "Pause between chest clicks", 3.0, 1.0, 10.0, 1.0);
    private final BooleanSetting relaxed = new BooleanSetting("Relaxed watch mode", "Slow, smooth, calm", true);
    private final BooleanSetting chestFacing = new BooleanSetting("Accept any chest facing", true);
    private final BooleanSetting doubleChests = new BooleanSetting("Allow double chests", true);
    private final BooleanSetting showProgress = new BooleanSetting("Show build progress", "Bar + % on the HUD", true);
    private final BooleanSetting showNext = new BooleanSetting("Show next blocks", "Highlight the upcoming blocks", true);
    private final BooleanSetting showGhost = new BooleanSetting("Show schematic ghost", "Faint missing blocks", true);
    private final NumberSetting ghostOpacity = new NumberSetting("Ghost opacity (%)", "How solid ghost blocks look", 20.0, 5.0, 60.0, 5.0);
    private final NumberSetting ghostRange = new NumberSetting("Ghost range (blocks)", "Only show ghosts near you", 32.0, 8.0, 64.0, 4.0);
    private final BooleanSetting showOutline = new BooleanSetting("Show schematic outline", "Corner frame of the build", true);
    private final BooleanSetting esp = new BooleanSetting("Show chest / origin boxes", true);

    // ------------------------------------------------------------------ state

    private enum S { IDLE, BUILDING, TO_CHEST, OPENING, LOOTING, LOOK_BACK, DOOR }

    private S state = S.IDLE;
    private int stateTicks, clock, grace, signWait;
    private boolean pendingOff, started, wasActive, apiOk;
    private int walkRetries, retryWait, openAttempts, clickWait, clickCd, taken, freeAtOpen;
    private final Set<Integer> tried = new HashSet<>();
    private int stallTicks, restarts, lastDone, progressTick, ghostCount;
    private String file;                                    // schematic file name (relative)
    private BlockPos origin, chest;
    private volatile KuroSchematicInfo info;
    private volatile Path loadingPath;
    private volatile KuroSchematicLayout layoutLoaded;
    private volatile Path layoutPath;
    private KuroBuildProgress progress;
    private Map<Item, Integer> needNow;                     // still missing in the build, per item (null = unknown)
    private final Map<Item, Integer> takenItems = new LinkedHashMap<>();
    private List<BlockPos> nextBlocks = List.of();
    private static final int GHOST_CAP = 4096;
    private final int[] ghostIdx = new int[GHOST_CAP];
    private final List<String> cmdSettings = new ArrayList<>();
    // doors / gates
    private BlockPos doorPos;
    private int doorTries, doorCooldown, stuckTicks;
    private double lastX = Double.NaN, lastZ;
    private boolean doorPausedBuilder;
    private final Map<BlockPos, Integer> doorGiveUp = new HashMap<>();
    private final Map<BlockPos, Integer> doorFails = new HashMap<>();
    // smooth camera
    private boolean aiming, aimDone;
    private float aimDurationS, aimYaw, aimPitch, aimStartYaw, aimStartPitch, savedYaw, savedPitch;
    private long aimStartNs;
    private final KuroAssistAim assist = new KuroAssistAim();
    // chest fixes (per schematic layer)
    private boolean[] layerChest, layerTypeUsed, layerFacingUsed;
    private boolean chestFixLayered, ignoreTypeOn, ignoreFacingOn;
    private boolean registeredRender;

    private record ClickAim(float yaw, float pitch, BlockHitResult hit) {}

    public KuroAutoBuild() {
        super("Auto Build", "Builds a schematic with Baritone and restocks from a refill chest", Category.KURO);
        instance = this;
        for (SettingLike s : orderedSettings()) addSetting(s.setting());
        load();
    }

    /** Shunchaki konstruktorda addSetting tartibini aniq saqlash uchun yordamchi. */
    private interface SettingLike { com.anormal.client.setting.Setting<?> setting(); }

    private List<SettingLike> orderedSettings() {
        List<SettingLike> out = new ArrayList<>();
        out.add(() -> showOutline);
        out.add(() -> showNext);
        out.add(() -> showGhost);
        out.add(() -> ghostOpacity);
        out.add(() -> ghostRange);
        out.add(() -> camera);
        out.add(() -> smoothTicks);
        out.add(() -> calm);
        out.add(() -> pace);
        out.add(() -> layers);
        out.add(() -> lookAtBlocks);
        out.add(() -> openDoors);
        out.add(() -> closeSigns);
        out.add(() -> signDelay);
        out.add(() -> aimStyle);
        out.add(() -> assistSpeed);
        out.add(() -> aimTime);
        out.add(() -> lowBlocks);
        out.add(() -> scaffold);
        out.add(() -> stacks);
        out.add(() -> keepFree);
        out.add(() -> clickDelay);
        out.add(() -> turnSpeed);
        out.add(() -> relaxed);
        out.add(() -> doubleChests);
        out.add(() -> chestFacing);
        out.add(() -> showProgress);
        out.add(() -> esp);
        return out;
    }

    // ================================================================== enable / disable

    @Override
    public void onEnable() {
        state = S.IDLE; started = false; pendingOff = false; aiming = false; aimDone = false; wasActive = false;
        stateTicks = 0; walkRetries = 0; openAttempts = 0; retryWait = 0;
        doorPos = null; doorTries = 0; doorCooldown = 0; stuckTicks = 0; signWait = 0; clock = 0;
        doorPausedBuilder = false; doorGiveUp.clear(); doorFails.clear();
        String problem = checkReady();
        if (problem != null) { say(problem); pendingOff = true; return; }

        Path path = resolveFile(file);
        String name = schematicsDir().relativize(path).toString().replace('\\', '/');
        if (name.matches(".*\\s.*")) {
            say("The file name has a space in it. Rename it (e.g. my_house.schem): Baritone's #build cannot take spaces.");
            pendingOff = true;
            return;
        }
        loadInfoAsync(path);
        loadLayoutAsync(path);
        progress = null; ghostCount = 0; nextBlocks = List.of(); progressTick = 0; lastDone = 0; stallTicks = 0; restarts = 0;
        apiOk = KuroBaritoneBridge.apiWorks();
        if (!apiOk) say("Could not read Baritone's API (" + KuroBaritoneBridge.lastError() + "). Using chat commands instead; progress is judged from the world.");
        cmdSettings.clear();
        applyBaritone();
        startBuild();
        started = true;
        if (scaffold.getValue().intValue() > 0 && throwawayCount() == 0)
            say("You carry no scaffold blocks (dirt / cobblestone / netherrack). Baritone needs some to bridge and pillar: the refill will take " + scaffold.getValue().intValue() + ".");
        grace = 60;
        enter(S.BUILDING);
        say("Building " + name + " at " + fmt(origin) + ". Refill chest: " + fmt(chest) + ".");
    }

    @Override
    public void onDisable() {
        stopAim();
        KuroRotations.release(this);
        if (started) {
            if (!KuroBaritoneBridge.cancelEverything()) bcmd("cancel");
            started = false;
        }
        KuroBaritoneBridge.restoreAll();
        for (String n : cmdSettings) bcmd("set reset " + n);
        cmdSettings.clear();
        if (mc.player != null && (state == S.OPENING || state == S.LOOTING) && mc.currentScreen instanceof HandledScreen<?>) {
            mc.player.closeHandledScreen();
        }
        state = S.IDLE;
        doorPos = null; doorPausedBuilder = false;
        pendingOff = false;
    }

    /** (Re)starts the Baritone build of the chosen schematic. Baritone compares with the world, so this also resumes after a refill. */
    private void startBuild() {
        Path path = resolveFile(file);
        if (path == null || origin == null) return;
        String name = schematicsDir().relativize(path).toString().replace('\\', '/');
        bcmd("cancel");
        bcmd("build " + name + " " + origin.getX() + " " + origin.getY() + " " + origin.getZ());
    }

    private String checkReady() {
        if (mc.world == null || mc.player == null) return "Join a world first.";
        if (!KuroBaritoneBridge.installed()) return "Baritone is not installed. Put baritone-standalone-fabric (v1.17.0 for 1.21.11) in your mods folder.";
        if (file == null) return "Choose a schematic first: .build file <name>   (see .build list)";
        if (resolveFile(file) == null) return "Schematic \"" + file + "\" not found in " + schematicsDir() + "  (see .build list)";
        if (origin == null) return "Set the build origin first: stand where the schematic's corner goes and run .build origin";
        if (chest == null) return "Choose the refill chest first: look at it and run .build chest";
        if (!isContainer(chest)) return "The saved refill chest at " + fmt(chest) + " is not a chest / barrel / shulker box any more (or is not loaded).";
        return null;
    }

    // ================================================================== Baritone settings

    /** Relaxed watch mode forces the visible smooth camera; otherwise the Camera setting decides. */
    private int camMode() { return relaxed.isEnabled() ? 2 : camera.getValue().intValue(); }
    private int lookTicks() { return relaxed.isEnabled() ? Math.max(smoothTicks.getValue().intValue(), 26) : smoothTicks.getValue().intValue(); }
    private int chestTurnSpeed() { return relaxed.isEnabled() ? Math.min(turnSpeed.getValue().intValue(), 100) : turnSpeed.getValue().intValue(); }

    private void applyBaritone() {
        List<String> missing = new ArrayList<>();
        bset(missing, "allowInventory", true);                     // lets Baritone pull blocks from the inventory onto the hotbar
        bset(missing, "buildInLayers", layers.isEnabled());
        applyIgnoreProps(false, false);                            // "open" (doors / gates you opened) + the chest fixes
        switch (camMode()) {
            case 1 -> {                                            // Steady: walking never turns the camera
                bset(missing, "freeLook", true);
                if (lookAtBlocks.isEnabled()) {                    // ...but placing / breaking turns it to the block, eased
                    bset(missing, "blockFreeLook", false);
                    bset(missing, "smoothLook", true);
                    bset(missing, "smoothLookTicks", lookTicks());
                } else {
                    bset(missing, "blockFreeLook", true);
                    bset(missing, "smoothLook", false);
                }
            }
            case 2 -> {                                            // Smooth: the camera turns to the walk and to each block
                bset(missing, "freeLook", false);
                bset(missing, "blockFreeLook", false);
                bset(missing, "smoothLook", true);
                bset(missing, "smoothLookTicks", lookTicks());
            }
            default -> {}
        }
        if (relaxed.isEnabled()) {
            bset(missing, "randomLooking", 0.0);                   // no random head twitching
            bset(missing, "randomLooking113", 0.0);
            bset(missing, "blockPlacementPenalty", 40.0);          // prefers a steady walk over awkward hops
        }
        int paceIdx = Math.max(1, Math.min(4, pace.getValue().intValue()));
        bset(missing, "rightClickSpeed", PACE_TICKS[paceIdx - 1]); // ticks between block placements
        if (paceIdx >= 2) bset(missing, "walkWhileBreaking", false); // stand still while breaking: calmer
        if (calm.isEnabled() || relaxed.isEnabled()) {
            bset(missing, "allowSprint", false);
            bset(missing, "allowParkour", false);
            bset(missing, "allowParkourPlace", false);
            bset(missing, "allowParkourAscend", false);
            bset(missing, "jumpPenalty", 6.0);
        }
        if (!missing.isEmpty()) say("This Baritone version has no setting: " + String.join(", ", missing) + " (skipped).");
    }

    private boolean cmdSet(String name, String value) { bcmd("set " + name + " " + value); cmdSettings.add(name); return true; }

    private void bset(List<String> missing, String name, Object value) {
        if (KuroBaritoneBridge.set(name, value)) return;
        if (!apiOk) { bcmd("set " + name + " " + value); cmdSettings.add(name); return; }   // same thing typed as #set
        missing.add(name);
    }

    private static void bcmd(String command) {
        KuroBaritoneControl b = ModuleManager.getModule(KuroBaritoneControl.class);
        if (b != null) b.cmd(command);
    }

    // ================================================================== main state machine

    private void enter(S next) { state = next; stateTicks = 0; }

    @Override
    public void onPreTick() {
        if (pendingOff) { pendingOff = false; setEnabled(false); return; }
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { setEnabled(false); return; }
        stateTicks++;
        clock++;
        tickSign();                                                // first: an open sign editor blocks everything else
        tickProgress();                                            // always: the refill and the chest fix use it too
        switch (state) {
            case DOOR -> tickDoor();
            case BUILDING -> tickBuilding();
            case TO_CHEST -> tickToChest();
            case OPENING -> tickOpening();
            case LOOTING -> tickLooting();
            case LOOK_BACK -> tickLookBack();
            default -> {}
        }
    }

    private void tickBuilding() {
        if (grace > 0) { grace--; return; }
        if (checkDoor()) return;                                   // a closed door / gate ahead: open it first
        if (!apiOk) {                                              // no API: judge from the world and the inventory
            wasActive = true;
            if (progress != null && progress.total() > 0 && progress.missing() == 0 && progress.done() > 0) {
                say("Build finished.");
                setEnabled(false);
                return;
            }
            if (stateTicks > 100 && stateTicks % 20 == 0 && progress != null && mc.player != null) {
                List<BlockPos> nx = progress.next(1, mc.player.getX(), mc.player.getY(), mc.player.getZ(), layers.isEnabled());
                Block want = nx.isEmpty() ? null : progress.targetAt(nx.get(0));
                Item it = want == null ? null : want.asItem();
                if (it != null && it != Items.AIR && inInventory(it) == 0) {
                    startTrip("Out of " + new ItemStack(it).getName().getString());
                    return;
                }
            }
            if (progress != null) {                                // Baritone idle but we think it builds: restart it
                if (progress.done() != lastDone) { lastDone = progress.done(); stallTicks = 0; restarts = 0; }
                else if (stateTicks > 100 && ++stallTicks >= 400) {
                    stallTicks = 0;
                    if (scaffold.getValue().intValue() > 0 && throwawayCount() == 0) { startTrip("Out of scaffold blocks"); return; }
                    if (++restarts > 4) { fail("Baritone is not making progress. Read its messages in chat (out of scaffold blocks? a spot it cannot reach?)."); return; }
                    say("No progress for 20 seconds: restarting Baritone's build (" + restarts + " / 4).");
                    startBuild();
                    grace = 40;
                    return;
                }
            }
            int lowF = Math.max(1, lowBlocks.getValue().intValue());
            if (stateTicks > 100 && lowBlocks.getValue().intValue() > 0 && blocksInInventory() < lowF) startTrip("Running low on blocks");
            return;
        }
        if (!KuroBaritoneBridge.builderActive()) {
            say(!wasActive && stateTicks < 200 ? "Baritone did not start the build. Check its message in chat (file name? origin?)."
                                               : "Build finished.");
            started = false;                                       // nothing left to cancel
            setEnabled(false);
            return;
        }
        wasActive = true;
        if (KuroBaritoneBridge.builderPaused()) { startTrip("Baritone is out of materials"); return; }
        int low = lowBlocks.getValue().intValue();
        if (low > 0 && blocksInInventory() < low) {
            KuroBaritoneBridge.pauseBuilder();
            startTrip("Running low on blocks");
        }
    }

    private Map<Item, Integer> remainingNeeds() {
        KuroBuildProgress bp = progress;
        if (bp == null) return null;
        Map<Item, Integer> out = new HashMap<>();
        for (Map.Entry<Block, Integer> e : bp.missingBlocks().entrySet()) {
            Item it = e.getKey().asItem();
            if (it != Items.AIR) out.merge(it, e.getValue(), Integer::sum);
        }
        return out.isEmpty() ? null : out;
    }

    private int inInventory(Item it) {
        int n = 0;
        for (int i = 0; i < 36; i++) { ItemStack s = mc.player.getInventory().getStack(i); if (s.getItem() == it) n += s.getCount(); }
        return n;
    }

    private void startTrip(String reason) {
        needNow = remainingNeeds();
        takenItems.clear();
        say(reason + ": walking to the refill chest.");
        walkRetries = 0; retryWait = 0;
        if (!apiOk) {
            bcmd("cancel");
            bcmd("goto " + chest.getX() + " " + chest.getY() + " " + chest.getZ());
            enter(S.TO_CHEST);
            return;
        }
        if (!KuroBaritoneBridge.goToBlock(chest)) { fail("Could not start walking to the chest."); return; }
        enter(S.TO_CHEST);
    }

    private void tickToChest() {
        if (stateTicks < 10) return;                               // the goal needs a few ticks to start
        checkDoor();                                               // walking to the chest: doors are opened without stopping
        if (retryWait > 0) { retryWait--; return; }
        if (!apiOk) {
            double dd = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(chest));
            if (dd <= 4.5) { bcmd("cancel"); openAttempts = 0; clickWait = 0; enter(S.OPENING); }
            else if (stateTicks > 3600) fail("Took too long to reach the refill chest.");
            return;
        }
        if (!KuroBaritoneBridge.goalActive()) {
            double d = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(chest));
            if (d <= 5.2) { openAttempts = 0; clickWait = 0; enter(S.OPENING); return; }
            if (++walkRetries > 3) { fail("Could not reach the refill chest (no path?)."); return; }
            KuroBaritoneBridge.goToBlock(chest);
            retryWait = 10;
            return;
        }
        if (stateTicks > 3600) fail("Took too long to reach the refill chest.");
    }

    private void tickOpening() {
        if (chestMenuSize() > 0) { tried.clear(); taken = 0; clickCd = 0; freeAtOpen = freeSlots(); enter(S.LOOTING); return; }
        if (mc.currentScreen != null) { if (stateTicks > 80) fail("Another screen is in the way."); return; }
        if (stateTicks > 240 || openAttempts > 4) { fail("The refill chest would not open (is something on top of it?)."); return; }
        if (mc.player.isSneaking()) return;                        // sneaking with a block in hand skips the chest
        if (clickWait > 0) { clickWait--; return; }

        ClickAim aim = findAim(chest);
        if (aim == null) { if (stateTicks > 40) fail("No clear line to the refill chest from here."); return; }

        if (camMode() == 1 || stateTicks > 100) {                  // steady, or the window has no focus so the camera never turned
            if (silentClick(aim)) { openAttempts++; clickWait = 12; }
            return;
        }
        if (!aiming) { savedYaw = mc.player.getYaw(); savedPitch = mc.player.getPitch(); }
        setAim(aim.yaw(), aim.pitch());
        if (aimDone) {
            BlockHitResult hr = viewHit(chest);
            if (hr != null) { click(hr); openAttempts++; clickWait = 12; }
            else reaim();                                          // not on it exactly: turn again from here
        }
    }

    private void tickLooting() {
        int size = chestMenuSize();
        if (size <= 0) { if (stateTicks > 20) finishTrip(); return; }   // the chest was closed from outside
        if (clickCd > 0) { clickCd--; return; }
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (taken >= stacks.getValue().intValue() || freeSlots() <= keepFree.getValue().intValue()) { closeAndFinish(); return; }
        for (int i = 0; i < size; i++) {
            if (!tried.add(i)) continue;                           // each slot once: a full inventory cannot loop forever
            ItemStack st = menu.getSlot(i).getStack();
            if (st.isEmpty() || !wanted(st)) continue;
            takenItems.merge(st.getItem(), st.getCount(), Integer::sum);
            mc.interactionManager.clickSlot(menu.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            taken++;
            clickCd = Math.max(0, clickDelay.getValue().intValue() - 1);
            return;
        }
        closeAndFinish();                                          // every slot looked at
    }

    private void closeAndFinish() {
        mc.player.closeHandledScreen();
        finishTrip();
    }

    private void finishTrip() {
        if (taken == 0) {
            fail(freeAtOpen <= keepFree.getValue().intValue()
                    ? "Your inventory is full: free some slots or lower \"Free slots to keep\"."
                    : "The refill chest has nothing this schematic needs. Stopping.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<Item, Integer> e : takenItems.entrySet()) {
            if (shown++ >= 8) { sb.append(", ..."); break; }
            sb.append(shown > 1 ? ", " : "").append(new ItemStack(e.getKey()).getName().getString()).append(" x").append(e.getValue());
        }
        say("Took " + taken + " stack" + (taken == 1 ? "" : "s") + " (" + sb + "). Back to building.");
        Map<Item, Integer> need = needNow;
        if (need != null) {
            StringBuilder lack = new StringBuilder();
            int c = 0;
            for (Map.Entry<Item, Integer> e : need.entrySet()) {
                int missing = e.getValue() - inInventory(e.getKey());
                if (missing <= 0 || c >= 6) continue;
                lack.append(c++ > 0 ? ", " : "").append(new ItemStack(e.getKey()).getName().getString()).append(" x").append(missing);
            }
            if (c > 0) say("Still not in your inventory (not in the chest, or no room): " + lack);
        }
        if (camMode() != 1 && aiming) setAim(savedYaw, savedPitch);   // ease the camera back to where you were looking
        enter(S.LOOK_BACK);
    }

    private void tickLookBack() {
        if (stateTicks < 6) return;                                // let the server settle after the chest closes
        if (aiming && !aimDone && stateTicks < 140) return;
        stopAim();
        if (!apiOk) startBuild();                                  // Baritone rebuilds whatever is still missing
        else if (!KuroBaritoneBridge.resumeBuilder()) bcmd("resume");
        grace = 40;
        enter(S.BUILDING);
    }

    private void fail(String message) {
        say(message);
        say("Auto Build stopped.");
        pendingOff = true;
    }

    // ================================================================== signs

    /** The sign editor opens by itself after a sign is placed and blocks the build: close it after a short human pause. */
    private void tickSign() {
        if (!closeSigns.isEnabled() || !(mc.currentScreen instanceof AbstractSignEditScreen)) { signWait = 0; return; }
        if (++signWait >= signDelay.getValue().intValue()) {
            signWait = 0;
            mc.currentScreen.close();                                     // same as pressing Escape: the sign stays blank
        }
    }

    // ================================================================== doors / fence gates

    /** A wooden / copper door or a fence gate that is shut. Iron doors need redstone, so they are left alone. */
    private static boolean isClosedDoor(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState st = mc.world.getBlockState(pos);
        Block b = st.getBlock();
        if (b == Blocks.IRON_DOOR) return false;
        if (!(b instanceof DoorBlock) && !(b instanceof FenceGateBlock)) return false;
        return st.contains(Properties.OPEN) && !st.get(Properties.OPEN);
    }

    /** A closed door / gate on the path ahead (or right next to a player who has stopped moving), within reach. */
    private BlockPos findBlockingDoor() {
        List<BlockPos> cands = new ArrayList<>();
        for (BlockPos q : KuroBaritoneBridge.upcomingPath(5)) { cands.add(q); cands.add(q.up()); }
        if (stuckTicks >= 25) {                                    // path unknown or still blocked: whatever is touching us
            BlockPos me = mc.player.getBlockPos();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) { cands.add(me.add(dx, 0, dz)); cands.add(me.add(dx, 1, dz)); }
        }
        Vec3d eye = mc.player.getEyePos();
        for (BlockPos c : cands) {
            if (!isClosedDoor(c)) continue;
            Integer until = doorGiveUp.get(c);
            if (until != null && clock < until) continue;
            if (eye.squaredDistanceTo(Vec3d.ofCenter(c)) > 16.0) continue;         // not close enough yet
            return c.toImmutable();
        }
        return null;
    }

    private void doorFailed(BlockPos door) {
        doorGiveUp.put(door.toImmutable(), clock + 600);           // leave it alone for 30 seconds
        say("Could not open the door / gate at " + fmt(door) + " (locked, blocked or out of reach). Open it by hand if the build stops.");
    }

    /**
     * Called every tick while walking. Returns true when it took over (state DOOR), so the caller stops for this tick.
     * Camera modes Smooth / Default stop the builder and turn the real camera to the door like to the chest; in Steady mode
     * (and while walking to the chest) the door is clicked with a silent look packet and the walk goes on.
     */
    private boolean checkDoor() {
        if (!openDoors.isEnabled() || mc.player == null || mc.world == null) return false;
        var p = mc.player;
        double mx = Double.isNaN(lastX) ? 0 : p.getX() - lastX;
        double mz = Double.isNaN(lastX) ? 0 : p.getZ() - lastZ;
        lastX = p.getX(); lastZ = p.getZ();
        stuckTicks = Math.hypot(mx, mz) < 0.02 ? stuckTicks + 1 : 0;
        if (doorCooldown > 0) { doorCooldown--; return false; }
        if (mc.currentScreen != null || p.isSneaking() || p.isUsingItem() || p.isSpectator() || !p.getAbilities().allowModifyWorld) return false;
        if (stuckTicks < 25 && clock % 4 != 0) return false;       // look every 4th tick, every tick once we are stuck
        BlockPos door = findBlockingDoor();
        if (door == null) return false;

        if (state == S.BUILDING && apiOk && camMode() != 1 && KuroBaritoneBridge.builderActive() && !KuroBaritoneBridge.builderPaused()) {
            doorPos = door;
            doorTries = 0; clickWait = 0;
            doorPausedBuilder = KuroBaritoneBridge.pauseBuilder();
            enter(S.DOOR);
            return true;
        }
        ClickAim aim = findAim(door);                              // silent version
        int fails = doorFails.merge(door, 1, Integer::sum);
        if (aim == null || fails > 3) { doorFailed(door); doorFails.remove(door); return false; }
        if (silentClick(aim)) doorCooldown = 10;
        return false;
    }

    private void tickDoor() {
        var p = mc.player;
        if (doorPos == null) { endDoor(); return; }
        if (mc.currentScreen != null) { if (stateTicks > 60) endDoor(); return; }
        if (!isClosedDoor(doorPos)) {                              // open now (our click came back from the server), or gone
            endDoor();
            return;
        }
        if (stateTicks > 200 || doorTries >= 3) { doorFailed(doorPos); endDoor(); return; }
        if (p.isSneaking()) return;
        if (stateTicks < 3) return;                                // let the walk settle after the pause
        if (clickWait > 0) { clickWait--; return; }

        ClickAim aim = findAim(doorPos);
        if (aim == null) {
            if (stateTicks > 40) { doorFailed(doorPos); endDoor(); }
            return;
        }
        if (stateTicks > 70) {                                     // the camera never turned (window without focus): silent click
            if (silentClick(aim)) { doorTries++; clickWait = 10; }
            return;
        }
        setAim(aim.yaw(), aim.pitch());
        if (aimDone) {
            BlockHitResult hr = viewHit(doorPos);
            if (hr != null) { click(hr); doorTries++; clickWait = 10; }
            else reaim();
        }
    }

    private void endDoor() {
        stopAim();
        if (doorPausedBuilder) {
            if (!KuroBaritoneBridge.resumeBuilder()) bcmd("resume");
            doorPausedBuilder = false;
        }
        doorPos = null;
        grace = 30;                                                // give Baritone a moment to re-plan through the open door
        enter(S.BUILDING);
    }

    // ================================================================== chest interaction

    /**
     * Finds where to look to click a block: points spread over the block's real outline (a door is a thin slab on one edge,
     * so the middle of the 1x1x1 cube would miss it), each checked with the same ray the game casts. Of the points that hit,
     * the one that needs the smallest turn from where you look now wins (the middle is preferred when it is about as close).
     */
    private ClickAim findAim(BlockPos target) {
        var p = mc.player;
        Vec3d eye = p.getEyePos();
        VoxelShape shape = mc.world.getBlockState(target).getOutlineShape(mc.world, target);
        Box box = shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1) : shape.getBoundingBox();
        Vec3d c = new Vec3d(target.getX() + (box.minX + box.maxX) / 2, target.getY() + (box.minY + box.maxY) / 2, target.getZ() + (box.minZ + box.maxZ) / 2);
        double sx = box.maxX - box.minX, sy = box.maxY - box.minY, sz = box.maxZ - box.minZ;
        double[] f = {0.0, 0.2, -0.2, 0.35, -0.35};                // fractions of the outline size, centre first
        ClickAim best = null;
        float bestCost = Float.MAX_VALUE;
        for (double fx : f) for (double fy : f) for (double fz : f) {
            Vec3d dir = c.add(fx * sx, fy * sy, fz * sz).subtract(eye);
            double len = dir.length();
            if (len < 1.0E-4) continue;
            Vec3d end = eye.add(dir.multiply(KuroBlockPlacer.REACH / len));
            BlockHitResult hr = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
            if (hr.getType() != HitResult.Type.BLOCK || !hr.getBlockPos().equals(target)) continue;
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            float pitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))), -90f, 90f);
            float turn = (float) Math.hypot(MathHelper.wrapDegrees(yaw - p.getYaw()), pitch - p.getPitch());
            float cost = turn + (float) (Math.abs(fx) + Math.abs(fy) + Math.abs(fz)) * 6f;   // small pull towards the middle
            if (cost < bestCost) { bestCost = cost; best = new ClickAim(yaw, pitch, hr); }
        }
        return best;
    }

    /** What the crosshair hits right now, if that is the given block. */
    private BlockHitResult viewHit(BlockPos target) {
        var p = mc.player;
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(Vec3d.fromPolar(p.getPitch(), p.getYaw()).multiply(KuroBlockPlacer.REACH));
        BlockHitResult hr = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
        return hr.getType() == HitResult.Type.BLOCK && hr.getBlockPos().equals(target) ? hr : null;
    }

    private void click(BlockHitResult hr) {
        ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hr);
        if (r.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Steady mode: the server is told the aim with a look packet, your own camera never moves (same method as BlockPlacer.placeAt). */
    private boolean silentClick(ClickAim aim) {
        var p = mc.player;
        if (!KuroRotations.free() && !KuroRotations.ownedBy(this)) return false;
        if (!KuroRotations.set(this, aim.yaw(), aim.pitch()) || !KuroRotations.sendLookPacket(true)) { KuroRotations.release(this); return false; }
        float oldYaw = p.getYaw(), oldPitch = p.getPitch();
        p.setYaw(aim.yaw());
        p.setPitch(aim.pitch());
        try { click(aim.hit()); }
        finally { p.setYaw(oldYaw); p.setPitch(oldPitch); KuroRotations.release(this); }
        return true;
    }

    private int chestMenuSize() {
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return -1;
        ScreenHandler m = mc.player.currentScreenHandler;
        if (m instanceof GenericContainerScreenHandler g) return g.getRows() * 9;
        if (m instanceof ShulkerBoxScreenHandler) return 27;
        return -1;
    }

    /** Blocks Baritone uses for scaffolding / pillaring / bridging (its default acceptableThrowawayItems). */
    private static boolean isThrowaway(Item it) {
        return it == Items.DIRT || it == Items.COBBLESTONE
                || it == Items.NETHERRACK || it == Items.STONE
                || it == Items.COBBLED_DEEPSLATE;
    }

    private int throwawayCount() {
        int n = 0;
        for (int i = 0; i < 36; i++) { ItemStack s = mc.player.getInventory().getStack(i); if (isThrowaway(s.getItem())) n += s.getCount(); }
        return n;
    }

    private boolean wanted(ItemStack st) {
        if (state == S.LOOTING && scaffold.getValue().intValue() > 0 && isThrowaway(st.getItem()) && throwawayCount() < scaffold.getValue().intValue()) return true;
        Map<Item, Integer> need = needNow;
        if (need != null && state == S.LOOTING) {                  // only what the build still lacks, and not more than it lacks
            Integer n = need.get(st.getItem());
            return n != null && inInventory(st.getItem()) < n;
        }
        return wantedStatic(st);
    }

    private boolean wantedStatic(ItemStack st) {
        KuroSchematicInfo si = info;
        if (si != null && !si.items.isEmpty()) return si.items.containsKey(st.getItem());
        return st.getItem() instanceof BlockItem;
    }

    private int freeSlots() {
        int n = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) n++;
        return n;
    }

    private int blocksInInventory() {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && wantedStatic(s)) n += s.getCount();
        }
        return n;
    }

    private static boolean isContainer(BlockPos pos) {
        if (mc.world == null) return false;
        Block b = mc.world.getBlockState(pos).getBlock();
        return b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock || b instanceof EnderChestBlock;
    }

    // ================================================================== smooth camera (every frame, not every tick)

    /**
     * Starts (or keeps) a turn to yaw / pitch. The turn follows a minimum-jerk curve: speed rises from nothing, peaks in the
     * middle and falls to nothing exactly on the target, which is how a hand moves a mouse. How long it takes depends on the
     * distance (peak speed = "Chest look speed", never quicker than "Aim smoothness"), and each turn is a little different
     * in length. Small changes of the aim point (under 1 degree) do not restart the turn.
     */
    private void setAim(float yaw, float pitch) {
        pitch = MathHelper.clamp(pitch, -90f, 90f);
        var p = mc.player;
        boolean same = aiming && aimDurationS > 0f
                && Math.abs(MathHelper.wrapDegrees(yaw - aimYaw)) <= 1.0f && Math.abs(pitch - aimPitch) <= 1.0f;
        if (same) return;
        aimStartYaw = p.getYaw();
        aimStartPitch = p.getPitch();
        aimYaw = aimStartYaw + MathHelper.wrapDegrees(yaw - aimStartYaw);       // the short way round
        aimPitch = pitch;
        float dist = (float) Math.hypot(aimYaw - aimStartYaw, aimPitch - aimStartPitch);
        float peak = Math.max(10f, chestTurnSpeed());
        float variation = 0.92f + 0.2f * ThreadLocalRandom.current().nextFloat();
        aimDurationS = Math.max((float) (aimTime.getValue() / 1000.0), 1.875f * dist / peak) * variation;
        aimStartNs = System.nanoTime();
        aimDone = dist < 0.4f;
        aiming = true;
    }

    /** Throws the current turn away and starts a new one from where the camera is now (used when the click would miss). */
    private void reaim() { aimDurationS = -1f; aimDone = false; }

    private void stopAim() {
        aiming = false; aimDone = false; aimDurationS = 0f;
        assist.reset();
    }

    /** Runs once per rendered frame (from the 2D overlay hook) and places the camera on the curve for this exact moment. */
    private void applyFrameTurn() {
        if (mc.player == null || mc.currentScreen != null || aimDurationS <= 0f) return;
        var p = mc.player;
        float t = MathHelper.clamp((System.nanoTime() - aimStartNs) / 1.0E9f / aimDurationS, 0f, 1f);
        float s = t * t * t * (t * (t * 6f - 15f) + 10f);              // minimum-jerk position profile

        float yaw = p.getYaw(), pitch = p.getPitch();
        float newYaw = aimStartYaw + (aimYaw - aimStartYaw) * s;
        float newPitch = MathHelper.clamp(aimStartPitch + (aimPitch - aimStartPitch) * s, -90f, 90f);

        p.setYaw(newYaw);
        p.setPitch(newPitch);
        p.headYaw = newYaw;
        p.lastYaw += newYaw - yaw;                                // same bookkeeping as a real mouse move
        p.lastPitch += newPitch - pitch;
        if (t >= 1f) aimDone = true;
    }

    /** Assist style: one frame of Aim Assist's pull towards the current aim angles (a point far along that direction). */
    private void assistTurn() {
        if (mc.player == null || mc.currentScreen != null) return;
        var p = mc.player;
        float yawErr = MathHelper.wrapDegrees(aimYaw - p.getYaw()), pitchErr = aimPitch - p.getPitch();
        if (Math.hypot(yawErr, pitchErr) < 0.4) { aimDone = true; return; }
        aimDone = false;
        Vec3d point = p.getEyePos().add(Vec3d.fromPolar(aimPitch, aimYaw).multiply(16.0));
        float[] d = assist.stepToPoint(point, p.getYaw(), p.getPitch(), assistSpeed.getValue(), assistSpeed.getValue(), true, 1f, 1f);
        if (d != null) {
            // applyTrackedMouseDelta takes mouse units: yaw += x * 0.15, pitch -= y * 0.15
            KuroRotationEngine.PlayerMouseRotationApplier.applyTrackedMouseDelta(d[0] / 0.15f, -d[1] / 0.15f);
        }
    }

    // ================================================================== picking the refill chest

    /** kuro'da bu "right-click chest'ni tanlash" rejimi; Anormal'da ichki click-webhook qatlami yo'q, shuning uchun {@link #pickLooked()} ishlatiladi. */
    public void armPicker() { pickLooked(); }

    private void pickLooked() {
        if (mc.player == null || mc.world == null) return;
        var p = mc.player;
        Vec3d eye = p.getEyePos();
        BlockHitResult hr = mc.world.raycast(new RaycastContext(eye, eye.add(p.getRotationVec(1.0f).multiply(6.0)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
        if (hr.getType() != HitResult.Type.BLOCK || !isContainer(hr.getBlockPos())) {
            say("Look at a chest, barrel or shulker box first (within 6 blocks), then run this again.");
            return;
        }
        setChest(hr.getBlockPos());
    }

    private void setChest(BlockPos pos) {
        chest = pos.toImmutable();
        save();
        say("Refill chest set to " + fmt(chest) + ".");
    }

    private void setOriginHere() {
        if (mc.player == null) return;
        origin = mc.player.getBlockPos().toImmutable();
        save();
        say("Build origin set to " + fmt(origin) + " (the schematic's corner goes here).");
    }

    // ================================================================== files / info

    /** Folder the schematics live in (created on demand). */
    public static Path folder() { return schematicsDir(); }

    /** The schematic file the module will build, or null. */
    public String selectedFile() { return file; }

    /** Opens the schematics folder in the OS file explorer, creating it first if it does not exist. */
    public static void openFolder() {
        Path dir = schematicsDir();
        try { Files.createDirectories(dir); } catch (IOException e) { say("Could not create " + dir); return; }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd = os.contains("win") ? new String[]{"explorer.exe", dir.toString()}
                : os.contains("mac") ? new String[]{"open", dir.toString()}
                : new String[]{"xdg-open", dir.toString()};
        try {
            new ProcessBuilder(cmd).start();
            say("Opened " + dir);
        } catch (IOException | RuntimeException e) {
            say("Could not open the folder automatically. It is: " + dir);
        }
    }

    /** All schematic files under the folder (3 levels deep), sorted, as paths relative to it. */
    public static List<String> listSchematics() {
        Path dir = schematicsDir();
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) return names;
        try (Stream<Path> s = Files.walk(dir, 3)) {
            s.filter(Files::isRegularFile).forEach(fx -> {
                String n = dir.relativize(fx).toString().replace('\\', '/');
                String low = n.toLowerCase(Locale.ROOT);
                for (String ext : EXTENSIONS) if (low.endsWith(ext)) { names.add(n); break; }
            });
        } catch (IOException ignored) { }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static Path schematicsDir() { return FabricLoader.getInstance().getGameDir().resolve("schematics"); }

    private static Path resolveFile(String name) {
        if (name == null) return null;
        Path dir = schematicsDir();
        Path exact = dir.resolve(name);
        if (Files.isRegularFile(exact)) return exact;
        for (String ext : EXTENSIONS) {
            Path q = dir.resolve(name + ext);
            if (Files.isRegularFile(q)) return q;
        }
        return null;
    }

    private void setFile(String name) {
        file = name.trim();
        info = null;
        save();
        Path p = resolveFile(file);
        if (p == null) { say("Saved \"" + file + "\", but it is not in " + schematicsDir() + " yet (see .build list)."); return; }
        say("Schematic set to " + p.getFileName() + ".");
        if (schematicsDir().relativize(p).toString().matches(".*\\s.*"))
            say("Warning: that name has a space in it. Rename the file (e.g. my_house.schem) or Baritone's #build cannot load it.");
        loadInfoAsync(p);
    }

    private void loadInfoAsync(Path path) {
        info = null;
        loadingPath = path;
        Thread t = new Thread(() -> {
            try {
                KuroSchematicInfo si = KuroSchematicInfo.load(path);
                if (!path.equals(loadingPath)) return;             // another file was chosen meanwhile
                info = si;
                mc.execute(() -> say("Needs " + si.totalBlocks + " blocks of " + si.items.size() + " kinds. The refill takes only those."));
            } catch (IOException | RuntimeException e) {
                mc.execute(() -> say("Could not read the materials (" + e.getMessage() + "). The refill will take any placeable block."));
            }
        }, "kuro-schematic-info");
        t.setDaemon(true);
        t.start();
    }

    private void loadLayoutAsync(Path path) {
        layoutLoaded = null;
        layoutPath = path;
        Thread t = new Thread(() -> {
            try {
                KuroSchematicLayout l = KuroSchematicLayout.load(path);
                if (path.equals(layoutPath)) layoutLoaded = l;
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                mc.execute(() -> say("Progress display unavailable (" + e.getMessage() + "). Building still works."));
            }
        }, "kuro-schematic-layout");
        t.setDaemon(true);
        t.start();
    }

    // ================================================================== chest fix (double chests / facing per layer)

    /**
     * Baritone cannot place the first half of a double chest as "left"/"right" (it comes out single until the 2nd half joins
     * it), so it gets stuck. Ignoring the block property "type" fixes that, but slabs use "type" too (top / bottom / double),
     * so it is only switched on when the schematic has chests and no other "type" users in that layer.
     */
    private void applyChestFix(KuroSchematicLayout l) {
        chestFixLayered = false; ignoreTypeOn = false; ignoreFacingOn = false;
        if (!doubleChests.isEnabled() && !chestFacing.isEnabled()) return;
        layerChest = new boolean[l.sy];
        layerTypeUsed = new boolean[l.sy];
        layerFacingUsed = new boolean[l.sy];
        boolean chests = false;
        int per = l.sx * l.sz;
        for (int i = 0; i < l.ids.length; i++) {
            Block b = l.blocks[l.ids[i]];
            if (b == null) continue;
            int y = i / per;
            if (b instanceof ChestBlock) { layerChest[y] = true; chests = true; continue; }
            if (b instanceof SlabBlock || b instanceof PistonBlock) layerTypeUsed[y] = true;
            BlockState st = b.getDefaultState();
            if (st.contains(Properties.HORIZONTAL_FACING) || st.contains(Properties.FACING)) layerFacingUsed[y] = true;
        }
        if (!chests) return;
        chestFixLayered = true;
        say("Schematic has chests: Baritone will accept any chest side" + (chestFacing.isEnabled() ? " and facing" : "")
                + " on layers where that is safe (no slabs / stairs / doors etc. in the same layer).");
    }

    private void setChestIgnore(boolean type, boolean facing) { applyIgnoreProps(type, facing); }

    /**
     * Writes Baritone's buildIgnoreProperties: "open" while door help is on (a door / gate you opened is still the right
     * block, so Baritone must not break and re-place it), plus "type" / "facing" for the chest fixes.
     */
    private void applyIgnoreProps(boolean type, boolean facing) {
        ignoreTypeOn = type; ignoreFacingOn = facing;
        List<String> want = new ArrayList<>();
        if (openDoors.isEnabled()) want.add("open");
        if (type) want.add("type");
        if (facing) want.add("facing");
        boolean ok = true;
        if (apiOk) ok = KuroBaritoneBridge.setListEntries("buildIgnoreProperties", want);   // your own entries are kept
        else {
            bcmd("set reset buildIgnoreProperties");
            if (!want.isEmpty()) ok = cmdSet("buildIgnoreProperties", String.join(",", want));
        }
        if (!ok && !want.isEmpty()) say("Could not set buildIgnoreProperties in this Baritone: doors / chests may get stuck.");
    }

    /** Switches the chest fixes on/off to match the layer Baritone is working on. */
    private void updateChestFix() {
        if (!chestFixLayered || progress == null || layerChest == null) return;
        int L = progress.lowestWrongLayer();
        if (L < 0 || L >= layerChest.length) return;
        boolean chestLayer = layerChest[L];
        boolean type = chestLayer && doubleChests.isEnabled() && !layerTypeUsed[L];
        boolean facing = chestLayer && chestFacing.isEnabled() && !layerFacingUsed[L];
        if (chestLayer && progressTick % 200 == 0
                && ((doubleChests.isEnabled() && layerTypeUsed[L]) || (chestFacing.isEnabled() && layerFacingUsed[L])))
            say("Layer " + (L + 1) + " mixes chests with slabs / stairs / doors etc.: chests there may need a hand.");
        if (type != ignoreTypeOn || facing != ignoreFacingOn) setChestIgnore(type, facing);
    }

    /** Keeps the progress numbers and the "next blocks" list fresh. Cheap: a slice of the schematic per tick. */
    private void tickProgress() {
        if (progress == null) {
            KuroSchematicLayout l = layoutLoaded;
            if (l == null || origin == null) return;
            progress = new KuroBuildProgress(l, origin);
            applyChestFix(l);
        }
        progress.tick(mc);
        if (progressTick % 20 == 0) updateChestFix();
        if (progressTick % 10 == 5) {
            ghostCount = showGhost.isEnabled() && mc.player != null
                    ? progress.missingNear(ghostIdx, mc.player.getX(), mc.player.getY(), mc.player.getZ(), ghostRange.getValue().intValue()) : 0;
        }
        if (++progressTick % 10 == 0 && showNext.isEnabled() && mc.player != null) {
            nextBlocks = progress.next(10, mc.player.getX(), mc.player.getY(), mc.player.getZ(), layers.isEnabled());
        }
    }

    // ================================================================== chat info helpers

    private void listFiles() {
        Path dir = schematicsDir();
        if (!Files.isDirectory(dir)) { say("No schematics folder yet. Create " + dir + " and put your files there."); return; }
        List<String> names = listSchematics();
        if (names.isEmpty()) { say("No .schem / .litematic / .schematic files in " + dir); return; }
        say("Schematics in " + dir + ":");
        for (int i = 0; i < Math.min(names.size(), 25); i++) say("  " + names.get(i));
        if (names.size() > 25) say("  ... and " + (names.size() - 25) + " more");
    }

    private void showMaterials() {
        KuroSchematicInfo si = info;
        if (si == null) {
            Path p = resolveFile(file);
            if (p == null) { say("Choose a schematic first."); return; }
            say("Reading " + p.getFileName() + " ... run this again in a moment.");
            loadInfoAsync(p);
            return;
        }
        say(si.fileName + ": " + si.totalBlocks + " blocks.");
        int n = 0;
        for (Map.Entry<Item, Integer> e : si.items.entrySet()) {
            if (n++ >= 12) { say("  ... and " + (si.items.size() - 12) + " more kinds"); break; }
            say("  " + Registries.ITEM.getId(e.getKey()).getPath() + " x" + e.getValue());
        }
    }

    private void status() {
        say("Schematic: " + (file == null ? "(none)" : file));
        say("Origin: " + (origin == null ? "(not set)" : fmt(origin)) + "   Refill chest: " + (chest == null ? "(not set)" : fmt(chest)));
        say("State: " + (isEnabled() ? state.name().toLowerCase(Locale.ROOT).replace('_', ' ') : "off")
                + (KuroBaritoneBridge.installed() ? "" : "   (Baritone is NOT installed)"));
    }

    // ================================================================== chat command  (.build ...)

    /** kuro'dagi .build buyrug'i mantig'i; Anormal'da chat buyrug'i qatlami yo'q (KuroStopwatches'ning usuli), faqat o'rnatilgan chaqiruvchilar uchun. */
    public void command(String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "folder", "open" -> openFolder();
            case "file", "schematic" -> {
                if (a.length < 2) say("Usage: .build file <name>   (a file in the schematics folder, see .build list)");
                else setFile(String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            case "origin" -> {
                BlockPos p = a.length >= 4 ? parsePos(a[1], a[2], a[3]) : null;
                if (a.length >= 4 && p == null) say("Usage: .build origin [x y z]");
                else if (p != null) { origin = p; save(); say("Build origin set to " + fmt(origin) + "."); }
                else setOriginHere();
            }
            case "chest" -> {
                BlockPos p = a.length >= 4 ? parsePos(a[1], a[2], a[3]) : null;
                if (a.length >= 4 && p == null) say("Usage: .build chest [x y z]   (no numbers = the block you look at)");
                else if (p != null) setChest(p);
                else pickLooked();
            }
            case "pick" -> armPicker();
            case "list", "ls" -> listFiles();
            case "info", "materials" -> showMaterials();
            case "status" -> status();
            case "start", "on" -> { if (isEnabled()) say("Already running."); else setEnabled(true); }
            case "stop", "off" -> { if (isEnabled()) setEnabled(false); else say("Not running."); }
            default -> {
                say(".build file <name>    choose the schematic (in .minecraft/schematics)");
                say(".build origin [x y z] where the schematic's corner goes (default: where you stand)");
                say(".build chest [x y z]  refill chest (default: the block you look at)");
                say(".build list | info | status | start | stop");
            }
        }
    }

    private static BlockPos parsePos(String x, String y, String z) {
        try { return new BlockPos(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z)); }
        catch (NumberFormatException e) { return null; }
    }

    // ================================================================== persistence

    private void save() {
        try {
            Files.writeString(CONFIG, "file=" + (file == null ? "" : file) + "\norigin=" + raw(origin) + "\nchest=" + raw(chest) + "\n");
        } catch (IOException ignored) {}
    }

    private void load() {
        try {
            if (!Files.exists(CONFIG)) return;
            for (String line : Files.readAllLines(CONFIG)) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq), value = line.substring(eq + 1).trim();
                switch (key) {
                    case "file" -> file = value.isEmpty() ? null : value;
                    case "origin" -> origin = parseRaw(value);
                    case "chest" -> chest = parseRaw(value);
                    default -> {}
                }
            }
        } catch (IOException ignored) {}
    }

    private static String raw(BlockPos p) { return p == null ? "" : p.getX() + " " + p.getY() + " " + p.getZ(); }

    private static BlockPos parseRaw(String v) {
        String[] t = v.split("\\s+");
        return t.length == 3 ? parsePos(t[0], t[1], t[2]) : null;
    }

    private static String fmt(BlockPos p) { return p == null ? "(none)" : p.getX() + " " + p.getY() + " " + p.getZ(); }

    private static void say(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Auto Build] " + s), false);
    }

    // ================================================================== world overlay

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (aiming) {                                              // the smooth camera: once per rendered frame
            if (aimStyle.getValue().intValue() == 2) assistTurn();
            else applyFrameTurn();
        }
        if (!showProgress.isEnabled() || mc.player == null || state == S.IDLE) return;
        TextRenderer font = mc.textRenderer;
        if (font == null) return;

        String text = switch (state) {
            case BUILDING -> "Auto Build: building";
            case TO_CHEST -> "Auto Build: walking to the refill chest";
            case OPENING -> "Auto Build: opening the chest";
            case LOOTING -> "Auto Build: restocking";
            case LOOK_BACK -> "Auto Build: back to work";
            case DOOR -> "Auto Build: opening a door";
            default -> "";
        };
        KuroBuildProgress bp = progress;
        String line = null;
        double frac = 0;
        if (bp != null && bp.total() > 0) {
            int pct = (int) Math.floor(bp.fraction() * 100.0);
            int low = bp.lowestWrongLayer();
            line = pct + "%   " + bp.done() + " / " + bp.total() + " blocks"
                    + (low >= 0 ? "   layer " + (low + 1) + " / " + bp.layerCount() : "   done");
            frac = bp.fraction();
        }

        int bw = 140;
        int w = Math.max(Math.max(bw, font.getWidth(text) + 14), line == null ? 0 : font.getWidth(line) + 14);
        int x1 = (mc.getWindow().getScaledWidth() - w) / 2, y1 = 6;
        context.fill(x1, y1, x1 + w, y1 + (line != null ? 32 : 13), 0xB0101214);        // panel background
        context.fill(x1, y1, x1 + 3, y1 + (line != null ? 32 : 13), 0xFFFFD27F);        // accent edge
        RenderUtils.drawText(context, font, text, x1 + 9, y1 + 4, 0xFFE8E8F0, false);
        if (line != null) {
            RenderUtils.drawText(context, font, line, x1 + 9, y1 + 14, 0xFFBDBDC8, false);
            int bx = x1 + 9, by = y1 + 25;
            context.fill(bx - 1, by - 1, bx + bw + 1, by + 5, 0xB0000000);
            context.fill(bx, by, bx + (int) (bw * frac), by + 4, 0xFF7FFFA0);
        }
    }

    /** World overlay: outline frame, ghost blocks, next-block highlights, chest / origin boxes. */
    private void dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || mc.world == null) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        if (state != S.IDLE) {
            KuroBuildProgress bp = progress;
            if (bp != null && showOutline.isEnabled()) {
                BlockPos o = bp.origin();
                WorldRender3D.begin(matrices, camera);
                try {
                    WorldRender3D.drawBox(consumers, new Box(o.getX(), o.getY(), o.getZ(),
                            o.getX() + bp.layout.sx, o.getY() + bp.layout.sy, o.getZ() + bp.layout.sz), 0xE6FFD27F);
                    WorldRender3D.drawBox(consumers, new Box(o.getX(), o.getY() + 0.01, o.getZ(),
                            o.getX() + bp.layout.sx, o.getY() + 0.02, o.getZ() + bp.layout.sz), 0x70FFD27F);
                } finally {
                    WorldRender3D.end(matrices);
                }
            }
            if (bp != null && showGhost.isEnabled() && ghostCount > 0) {
                // every block still missing, as a faint tinted ghost (opacity setting; 20% = 80% see-through)
                int alpha = MathHelper.clamp(Math.round(ghostOpacity.getValue().intValue() * 2.55f), 0, 255);
                WorldRender3D.begin(matrices, camera);
                try {
                    for (int k = 0; k < ghostCount; k++) {
                        int i = ghostIdx[k];
                        Block blk = bp.blockAtIndex(i);
                        if (blk == null) continue;
                        BlockPos b = bp.blockPos(i);
                        int rgb = blk.getDefaultMapColor().color;
                        WorldRender3D.drawFilledBox(consumers, new Box(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1),
                                (alpha << 24) | rgb);
                    }
                } finally {
                    WorldRender3D.end(matrices);
                }
            }
            if (bp != null && showNext.isEnabled()) {
                // the next block stands out (green); the queue behind it fades out with distance in the order
                for (int i = nextBlocks.size() - 1; i >= 0; i--) {
                    BlockPos b = nextBlocks.get(i);
                    WorldRender3D.begin(matrices, camera);
                    try {
                        if (i == 0) {
                            WorldRender3D.drawBox(consumers, new Box(b.getX() - 0.004, b.getY() - 0.004, b.getZ() - 0.004,
                                    b.getX() + 1.004, b.getY() + 1.004, b.getZ() + 1.004), 0xFF7FFFA0);
                        } else {
                            WorldRender3D.drawBox(consumers, new Box(b.getX() + 0.03, b.getY() + 0.03, b.getZ() + 0.03,
                                    b.getX() + 0.97, b.getY() + 0.97, b.getZ() + 0.97), 0x38FFFFFF);
                        }
                    } finally {
                        WorldRender3D.end(matrices);
                    }
                }
            }
        }
        if (!esp.isEnabled()) return;
        WorldRender3D.begin(matrices, camera);
        try {
            if (chest != null) {
                WorldRender3D.drawBox(consumers, new Box(chest.getX(), chest.getY(), chest.getZ(),
                        chest.getX() + 1, chest.getY() + 1, chest.getZ() + 1), 0xFF7FD4FF);
            }
            if (origin != null) {
                WorldRender3D.drawBox(consumers, new Box(origin.getX(), origin.getY(), origin.getZ(),
                        origin.getX() + 1, origin.getY() + 1, origin.getZ() + 1), 0xFF9CFFB0);
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    /** Instance the world-render hook once, the first time the module actually runs. */
    private void ensureWorldRenderHook() {
        if (registeredRender) return;
        registeredRender = true;
        WorldRenderEvents.END_MAIN.register(this::dispatchWorld);
    }

    @Override
    public void onTick() {
        ensureWorldRenderHook();
    }
}
