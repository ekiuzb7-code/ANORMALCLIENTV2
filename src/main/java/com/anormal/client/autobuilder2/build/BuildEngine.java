package com.anormal.client.autobuilder2.build;

import com.anormal.client.autobuilder2.litematic.LitematicaFile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.Property;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks the player around a schematic and places it, one block per tick, without any further input.
 *
 * <p>The plan is ordered bottom-up, but the engine does not follow it blindly. Real schematics
 * (especially machines and traps) often have blocks that float with nothing beside them until their
 * neighbours exist, and vanilla cannot place a block with no face to click. So each tick the engine
 * looks for the earliest block in the plan it can place <em>right now</em> — one whose cell it can
 * break, or which has a solid neighbour to place against — and leaves the rest queued. That lets a
 * floating cluster build itself out from whichever block first gets support. When nothing remaining
 * can be placed, the build ends cleanly and the leftovers are reported as floating.
 *
 * <p>Everything happens on the client thread from the client tick. Movement is driven with the real
 * {@link GameOptions} key bindings rather than by writing {@code player.input}, because the game
 * recomputes that input from the keyboard every tick — and it only takes effect while no screen is
 * open, so the engine walks only when the menu is closed and places whenever it is in range.
 */
public final class BuildEngine {
    public enum State { IDLE, RUNNING, PAUSED, DONE, STOPPED }

    /**
     * Where every change of state is reported. The status note and the crouch key are otherwise only
     * ever visible in the HUD, which cannot be read back after the fact — so a build that misbehaves
     * in somebody's own world leaves no evidence at all. Both go to the log here, one line per change
     * and no more, so the timeline of a real session can be read afterwards.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("auto-cc");

    public enum Phase { NONE, TRAVELLING, BREAKING, PLACING }

    /** Give up mining a block after this many ticks (bedrock, protected blocks, lag). */
    private static final int MAX_BREAK_TICKS = 240;

    /** Retry a rejected placement this many times before moving on. */
    private static final int MAX_PLACE_ATTEMPTS = 4;

    /** Ticks without moving before the current route is considered stuck. */
    private static final int STUCK_TICKS = 80;

    /** Ticks spent walking toward one block before giving up on it. */
    private static final int MAX_TRAVEL_TICKS = 900;

    /** How far ahead the engine looks for a block it can place right now. */
    private static final int MAX_SCAN = 4096;

    /**
     * Ticks to hold crouch before placing.
     *
     * <p>Vanilla runs a clicked block's own use action before it tries to place the held block, so
     * right-clicking a chest, crafting table, furnace or anything else with a screen opens that
     * screen instead of placing. Crouching is what makes the interaction place first. The server
     * only learns about the crouch on the tick after the key goes down, so placement waits a couple
     * of ticks after arriving in range, otherwise the very first block would still open a GUI.
     */
    private static final int SNEAK_WARMUP_TICKS = 2;

    /** How many different vantage points to try before giving up on a block nothing can see. */
    private static final int MAX_VANTAGE_TRIES = 4;

    /**
     * Ticks spent walking between vantage points for one block before settling for a reach
     * placement. Without this the builder can pace back and forth between two viewpoints forever,
     * because a fresh look never becomes available no matter how far it walks.
     */
    private static final int MAX_VANTAGE_TICKS = 60;

    /** Most temporary blocks a single climb may use. */
    private static final int MAX_SCAFFOLD = 24;

    /** Ticks a single climb may take before it is abandoned. A hop costs ~20 ticks. */
    private static final int MAX_SCAFFOLD_TICKS = 600;

    /** Ticks a climb may spend making no progress at all before it is abandoned. */
    private static final int MAX_SCAFFOLD_IDLE_TICKS = 120;

    /**
     * How far the player's feet must be above the top of the cell being filled before the game
     * accepts a block there. Any overlap with the player's own hitbox and the placement is refused
     * outright, so the click has to wait for the hop to actually clear the cell.
     */
    private static final double PLACEMENT_CLEARANCE = 0.05;

    /** Sentinel for "the tower column has not been pinned to a cell yet". */
    private static final int UNPINNED = Integer.MIN_VALUE;

    /** Ticks a single bridging run may take before it is abandoned. Walking to the gap eats these. */
    private static final int MAX_BRIDGE_TICKS = 600;

    /** Ticks spent walking toward a gap before the bridge is abandoned as going nowhere. */
    private static final int MAX_BRIDGE_WALK_TICKS = 120;

    /**
     * How many times a build may stop and hold blocks back while it waits for a neighbour that
     * gives them the facing the schematic asks for, before it gives up and fills what is left as
     * close to the schematic as a click can get.
     */
    private static final int MAX_AIM_GIVE_UPS = 4;

    /**
     * How many blocks may be held back at once waiting for a better angle. Small on purpose: every
     * one of them costs the build a pause while it decides they are not ready, and a schematic with
     * hundreds of machine blocks would spend all its time deciding instead of building.
     */
    private static final int MAX_AIM_WAITING = 8;

    /**
     * Ticks the player holds a look before clicking. The server places the block using its own copy
     * of where the player is looking, and that only catches up on the next movement packet, so a
     * click in the same tick as the turn places the block facing the <em>old</em> way.
     *
     * <p>Two ticks, and measured to be two: with one, the server still places a share of the blocks
     * facing the way the builder was looking <em>before</em> the turn, which a build of machines
     * cannot afford — a dispenser pointing the wrong way is a wrong build, not a slow one. The tick
     * the turn is chosen in and the one after are what the movement packets need before a click can
     * be trusted with the new look.
     */
    private static final int AIM_WARMUP_TICKS = 2;

    /** The four cardinal yaws, in the order south, west, east, north. */
    private static final float[] LOOK_YAWS = {0.0F, 90.0F, -90.0F, 180.0F};

    /** Level, straight up, straight down. Vertical looks are how pistons and observers face up. */
    private static final float[] LOOK_PITCHES = {0.0F, -90.0F, 90.0F};

    /** How tall a temporary column may be, when one is needed to support a floating block. */
    private static final int MAX_SUPPORT_COLUMN = 6;

    /**
     * Ticks spent walking toward one block before the approach is treated as wrong: thirty seconds.
     *
     * <p>Far longer than the ten seconds a block gets once the builder has arrived, because walking
     * to a block is not being stuck at it: on a large schematic the far corner is a long walk, and
     * throwing the route away every ten seconds sent the builder round again and again without ever
     * arriving. {@link #STUCK_TICKS} in the walk itself is what notices a walk that is going
     * nowhere.
     */
    private static final int TRAVEL_STUCK_TICKS_PER_ATTEMPT = 600;

    /**
     * Ticks spent on one block before the builder comes at it a different way: ten seconds.
     *
     * <p>Ten seconds is far longer than any block should take — a placement, a walk to it and the
     * climb to it are all measured in ticks — so hitting this means something about the approach is
     * wrong rather than slow. Rather than keep trying the same thing, the builder throws away what
     * it had planned for that block, which sends it looking for another vantage point and another
     * angle; a second window of the same length means the block is left for later, and a third means
     * it is written off, so the build always finishes.
     */
    private static final int STUCK_TICKS_PER_ATTEMPT = 200;

    /** Fresh approaches to one block before it is left for later, then written off. */
    private static final int MAX_RESTARTS = 2;

    /**
     * How far off a block's centre counts as still standing on it.
     *
     * <p>Pillaring has to start from the middle: a hop from the edge drifts sideways, and each hop
     * carries the column further off its own footprint until the player misses it and drops.
     */
    private static final double CENTRE_TOLERANCE = 0.25;

    /**
     * Blocks that must never be stood on, walked over, or used as scaffolding.
     *
     * <p>Slime bounces the player and honey glues them in place, so a route that relies on either is
     * unreliable — climbing off a honey block in particular is impossible without help.
     */
    private static final Set<Block> UNRELIABLE_FOOTING = Set.of(Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK);

    /**
     * Full cubes that open something when right-clicked but are not {@link BlockEntityProvider}s, so
     * they have to be named by hand. Building a tower with one is asking for trouble: a stray
     * right-click opens its menu mid-climb, and it costs a crouch to place at all.
     */
    private static final Set<Block> INTERACTIVE_ON_USE = Set.of(
            Blocks.CRAFTING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.SMITHING_TABLE, Blocks.LOOM,
            Blocks.STONECUTTER, Blocks.GRINDSTONE, Blocks.ANVIL, Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL, Blocks.NOTE_BLOCK, Blocks.JUKEBOX, Blocks.RESPAWN_ANCHOR,
            Blocks.CRAFTER, Blocks.COMPOSTER,
            // The enchanting table is the one screen with no block entity behind it, so it cannot be
            // caught by the {@link BlockEntityProvider} test above.
            Blocks.ENCHANTING_TABLE,
            // No menu, but a use action of their own: a click on one of these cycles it instead of
            // placing anything, which without a crouch leaves the builder clicking the same lever
            // or repeater for ever. Anything else like this is learned at runtime.
            Blocks.LEVER, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.DAYLIGHT_DETECTOR);

    /** The four directions the player can step, used to check they still have a way out. */
    private static final Direction[] STEP_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /** Placement faces tried in order; below first, which is how bottom-up building usually works. */
    private static final Direction[] PLACE_ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
    };

    /**
     * The properties a click can decide, by name. These are the ones vanilla derives from the face
     * that was clicked and the direction the player is looking, so a mismatch on one of them is
     * worth holding a block back for until a neighbour exists that gives the right angle.
     */
    private static final Set<String> PLACEMENT_PROPERTIES = Set.of(
            "facing", "axis", "rotation", "half", "hinge", "type", "face", "orientation",
            "vertical_direction", "attached", "tilt");

    /**
     * Most clicks one block's state may take before it is left as it is.
     *
     * <p>A click steps a property one value at a time rather than setting it: a repeater's delay runs
     * 1, 2, 3, 4 and back to 1, a comparator flips between its two modes, a note block climbs a note
     * at a time. A full cycle of the property is the most clicks that could ever help — one more lands
     * back on the value the block arrived with — and this is the ceiling on top of that.
     */
    private static final int MAX_STATE_CLICKS = 24;

    /** Ticks one state click is given to reach the world before it is written off. */
    private static final int MAX_STATE_WAIT_TICKS = 20;

    /** Ticks the whole of one block's state fix may take, waiting included. */
    private static final int MAX_STATE_TICKS = 120;

    /**
     * Times a state click may try to stand up before it says what is keeping it down.
     *
     * <p>The engine lets go of its own crouch — for a key set to toggle, by tapping it, which is the
     * only thing that binding answers — and it can see the key come straight back down when something
     * else is holding it: the player's own finger on the sneak key above all, because a held key sends
     * a fresh press every few milliseconds and each one puts the crouch back within a tick of it being
     * let go. A click made crouched is a block's use action being skipped, which is the one thing a
     * state click must not be, so rather than standing still for six seconds the build says out loud
     * that the crouch key is the thing in the way.
     */
    private static final int MAX_STAND_TRIES = 6;

    /** Ticks spent walking out of a cell the player is standing in before the spot is given up on. */
    private static final int MAX_ASIDE_TICKS = 200;

    /**
     * Times a placement that came out the wrong way round is broken and made again.
     *
     * <p>The click that places a block is chosen from what the client can see, and the game decides the
     * result from what it can see, a tick or two later. Where the two disagree — a neighbour that moved,
     * a look that had not arrived yet — the block lands turned, and nothing in the click itself says so.
     * One more go with a different click is what fixes it; three times through and it is left as it is,
     * with a line in the log saying which block and what it holds.
     */
    private static final int MAX_ROTATION_RETRIES = 2;

    /**
     * How many cells the last look over a finished build may hand back for another go, and how many
     * times it may hand back the same one.
     *
     * <p>Most of a build is finished by the time the plan runs out. What is left over is the handful
     * of cells the build could not do the first time: an item that was not in the inventory yet, a
     * block the game turned, a cell that had nothing to be placed against. By then the rest of the
     * structure is standing, and that is exactly the neighbour that was missing, so going back for
     * them is the difference between a build with holes in it and the schematic.
     *
     * <p>Bounded, because a cell that genuinely cannot be built must not keep the build running for
     * ever: sixteen cells in all, and twice for any one cell.
     */
    private static final int MAX_SWEEP_REPAIRS = 16;
    private static final int MAX_SWEEP_TRIES = 2;

    /** Ticks the last look over a finished build may keep the builder working before it stops. */
    private static final int MAX_SWEEP_TICKS = 1800;

    /**
     * How much of what a build left out is named when it stops.
     *
     * <p>A build that leaves blocks behind says how many and why, and the two numbers answer
     * different questions — "what is not there" and "what went wrong". Neither says <em>which</em>
     * blocks, which is the first thing the player wants to know, so the cells are named too: a few
     * of them by coordinate, and the kinds by count. Capped, because a chat line is one line.
     */
    private static final int MAX_LISTED_SPOTS = 3;
    private static final int MAX_LISTED_KINDS = 4;

    /**
     * Ticks after one of the build's own clicks in which a menu coming up is taken to be its doing.
     *
     * <p>A screen opened by a click is opened by the server answering it, and that answer takes a
     * round trip, so the menu turns up a tick or two after the click rather than in the same tick.
     */
    private static final int MENU_WATCH_TICKS = 3;

    private static final BuildEngine INSTANCE = new BuildEngine();

    private State state = State.IDLE;
    private Phase phase = Phase.NONE;
    private BuildPlan plan;
    private boolean[] done;
    private int cursor;
    private int workIndex = -1;
    private int placed;
    private int broken;
    private int satisfied;
    private int unsupported;
    private int floating;
    private int missingItems;
    private int unreachable;
    private int rejected;
    /** Cells the plan wants a block in that this game version can build at all. */
    private int buildable;
    /** Of those, the ones not standing exactly as the schematic has them when the build finishes. */
    private int unfinished;
    /** Cells whose block this game version has no answer for, counted apart from fluids. */
    private int unknownCells;
    /** Cells the schematic fills with water or lava, which no item can put down. */
    private int fluidCells;
    /**
     * Cells left in the wrong state because the player was holding the crouch key.
     *
     * <p>A state click — a repeater's delay, a lever's throw, a comparator's mode — has to be made
     * standing, because a crouched click is taken by the block's own use action and never reaches the
     * state at all. The builder presses the key up itself, but a key the player is physically holding
     * is put back down by the key repeat within a tick of being released, and nothing the build does
     * can win that race. Counted apart from the states no click can set, because this one is fixable by
     * the player in a way that one is not: let go of crouch and run the build again.
     */
    private int crouchBlocked;
    /**
     * Cells holding the right block in the wrong state at the end of a build.
     *
     * <p>Counted from the world when the build finishes, not from the placements: a repeater that
     * arrived at delay 1 and was clicked round to delay 4 is not a cell left over, and neither is one
     * that was placed twice, so the only honest answer is what is standing at the end. It is reported
     * apart from the cells that are missing altogether because the two need different words: one is a
     * block the player may want to set by hand, the other is a hole.
     */
    private int wrongState;
    private int breakTicks;
    private int attempts;
    private String note = "";

    private List<GridPos> route;
    private int routeIndex;
    private GridPos routeGoal;
    private GridPos lastPos;
    private GridPos travelGoal;
    private int stuckTicks;
    private int travelTicks;
    private int vantageTicks;
    private int reachOnly;
    private boolean fellBack;
    private boolean scrambling;
    private boolean climbBlocked;
    private boolean bridgeBlocked;
    private int bridgeTicks;
    private int bridgeStuckTicks;
    private GridPos bridgeLastPos;
    private boolean bridging;
    private Direction bridgeDir;
    private GridPos bridgeTarget;
    private int bridgeSteps;
    private int bridgeWalkTicks;
    private boolean tearingDown;
    private int scaffoldAnchorY = UNPINNED;
    private int scaffoldAnchorX;
    private int scaffoldAnchorZ;
    private int scaffoldTargetY;
    private int scaffoldTicks;
    private int scaffoldIdleTicks;
    private Aim aim;
    private int aimTicks;
    private int sneakHoldTicks;
    private int crouchTicks;
    /**
     * True while the engine is holding crouch for a run of clicks.
     *
     * <p>Held rather than pulsed around every click. A crouch pressed and released for each of them
     * leaves the builder bobbing up and down for the whole build, which is all a container-heavy
     * schematic ever looks like — nearly every block in one is placed against something with a menu,
     * so nearly every click needs the crouch — and each press also costs the two ticks it takes to
     * reach the server. Held down, a run of such blocks crouches once and every later click of the
     * run goes out on the tick it is ready.
     */
    private boolean crouchHeld;
    /** The block the previous swallowed click was made against, and the plan index it was for. */
    private Block lastSwallowed;
    private int lastSwallowedIndex = -1;
    private String lastLoggedNote = "";
    private boolean lastLoggedSneak;
    /** The placed block whose state is being clicked into shape, and the state it is meant to hold. */
    private BlockPos statePos;
    private BlockState stateWant;
    /** The state the last click was made against, so a second click waits for it to land first. */
    private BlockState stateSeen;
    private int stateClicks;
    private int stateWait;
    private int stateTicks;
    /** Consecutive ticks the state click has spent waiting for the crouch to come back up. */
    private int stateStands;
    /** Ticks spent walking out of a cell the player is standing in. */
    private int asideTicks;
    /** True while the engine is walking the player out of a cell it wants to place in. */
    private boolean asiding;
    /** Ticks left to watch for a menu opened by the build's own click. */
    private int menuWatchTicks;
    /** The block that click was made on, which is what to crouch for if a menu did come up. */
    private Block menuCulprit;
    private int menusOpened;
    /** Placements waiting to be looked at again once the game has had its say on them. */
    private final ArrayDeque<Placed> verifyQueue = new ArrayDeque<>();
    /** The click each plan cell was last placed with, so a retry can make a different one. */
    private final Map<Integer, Aim> lastAims = new HashMap<>();
    private final Map<Integer, Integer> rotationRetries = new HashMap<>();
    private int rotationRepairs;
    /** The last look over the whole plan, taken before the build is called finished. */
    private boolean sweeping;
    /** True once that look has run out of its time, so it has nothing more to say. */
    private boolean sweepOver;
    private int sweepIndex;
    private int sweepTicks;
    private int sweepRepairs;
    /** How often the last look has handed the same cell back for another go. */
    private final Map<Integer, Integer> sweepTries = new HashMap<>();
    /** Cells already counted as left out, so the same hole is never counted twice. */
    private final Set<Integer> floatingCounted = new HashSet<>();
    private int aimGiveUps;
    private int improvised;
    private int verified = -1;
    private int workTicks;
    private final Map<Integer, Integer> restarts = new HashMap<>();
    private final Set<Integer> aimWaiting = new HashSet<>();

    /**
     * Block types a click has already been swallowed by. Learned rather than listed: the set of
     * blocks with a use action of their own is long, differs between versions, and any mod can add
     * to it, so the builder notices for itself when a click placed nothing and crouches for that
     * block from then on.
     */
    private final Set<Block> clickSwallowers = new HashSet<>();

    /** A block the build placed, to be looked at again once the game has answered the click. */
    private record Placed(BlockPos pos, int index, BlockState want, int look) {
    }

    /**
     * A temporary block the build put down and has to take back up again.
     *
     * <p>The block itself is remembered, not just the cell, because the cell may not hold it any
     * more. A placeholder or a tower block often stands in a cell the plan wants, and once the real
     * block of the build has replaced it that cell is finished work, not scaffolding.
     */
    private record Scaffold(GridPos pos, Block block) {
    }
    private int scaffoldPlaced;
    private String scaffoldMaterial = "";
    private long startedAtMillis;
    private long finishedAtMillis;
    private final Set<GridPos> triedGoals = new HashSet<>();
    private final ArrayDeque<Scaffold> scaffoldBlocks = new ArrayDeque<>();

    private BuildEngine() {
    }

    public static BuildEngine get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Control
    // ------------------------------------------------------------------

    /** Starts a build anchored at {@code anchor}, which becomes grid cell {@code (0,0,0)}. */
    public void start(LitematicaFile file, GridPos anchor, GridPos builderPos) {
        this.plan = new BuildPlan(file, anchor, new GridPos(
                builderPos.x() - anchor.x(), builderPos.y() - anchor.y(), builderPos.z() - anchor.z()));
        this.done = new boolean[this.plan.size()];
        this.buildable = countBuildable();
        this.cursor = 0;
        this.workIndex = -1;
        this.placed = 0;
        this.broken = 0;
        this.satisfied = 0;
        this.unsupported = 0;
        this.unknownCells = 0;
        this.fluidCells = 0;
        this.floating = 0;
        this.missingItems = 0;
        this.unreachable = 0;
        this.crouchBlocked = 0;
        this.rejected = 0;
        this.reachOnly = 0;
        this.breakTicks = 0;
        this.attempts = 0;
        this.route = null;
        this.routeIndex = 0;
        this.routeGoal = null;
        this.lastPos = null;
        this.travelGoal = null;
        this.stuckTicks = 0;
        this.travelTicks = 0;
        this.vantageTicks = 0;
        this.fellBack = false;
        this.scrambling = false;
        this.climbBlocked = false;
        this.bridgeBlocked = false;
        this.bridgeTicks = 0;
        this.bridgeStuckTicks = 0;
        this.bridgeLastPos = null;
        this.bridging = false;
        this.bridgeTarget = null;
        this.bridgeSteps = 0;
        this.tearingDown = false;
        this.scaffoldTicks = 0;
        this.scaffoldPlaced = 0;
        this.scaffoldIdleTicks = 0;
        this.scaffoldAnchorY = UNPINNED;
        this.aim = null;
        this.aimTicks = 0;
        this.sneakHoldTicks = 0;
        this.crouchTicks = 0;
        this.crouchHeld = false;
        this.asiding = false;
        this.asideTicks = 0;
        this.lastSwallowed = null;
        this.lastSwallowedIndex = -1;
        clearState();
        this.menuWatchTicks = 0;
        this.menuCulprit = null;
        this.menusOpened = 0;
        this.verifyQueue.clear();
        this.lastAims.clear();
        this.rotationRetries.clear();
        this.rotationRepairs = 0;
        this.sweeping = false;
        this.sweepOver = false;
        this.sweepIndex = 0;
        this.sweepTicks = 0;
        this.sweepRepairs = 0;
        this.sweepTries.clear();        this.floatingCounted.clear();
        this.unfinished = 0;
        this.wrongState = 0;
        this.aimGiveUps = 0;
        this.improvised = 0;
        this.verified = -1;
        this.aimWaiting.clear();
        this.scaffoldBlocks.clear();

        this.restarts.clear();
        this.workTicks = 0;
        this.lastLoggedNote = "";
        this.phase = Phase.NONE;
        this.state = this.plan.size() == 0 ? State.DONE : State.RUNNING;
        this.startedAtMillis = System.currentTimeMillis();
        this.finishedAtMillis = 0L;
        this.note = this.plan.size() == 0
                ? "Schematic has no blocks to place"
                : "Placing " + this.plan.size() + " blocks from " + anchor;
        reportShortages(MinecraftClient.getInstance().player);
    }

    /**
     * Says up front what the build needs and the player has not got.
     *
     * <p>A build is only a walk, a click and an item, and of those three the item is the one the mod
     * has no way to make: an inventory with no barrel in it is a build with every barrel missing, one
     * at a time, twenty ticks apart, each cell worked on and walked to before finding out. Told at the
     * start, the same information is a shopping list — twenty seconds at a chest and the build is
     * whole — so the count of every block the schematic wants is compared with the count really in the
     * inventory before the first cell is touched.
     *
     * <p>Counted per item and not per cell, because that is what the player has to go and get: three
     * hundred cobblestone is one stack and a bit, and reading it as three hundred cells helps nobody.
     * Shortages are named biggest first, and the list is cut off rather than allowed to run away — a
     * schematic of forty new blocks is a wall of text, and the first four are what the first four trips
     * are anyway.
     */
    private void reportShortages(ClientPlayerEntity player) {
        if (player == null || this.plan == null) {
            return;
        }
        Map<Item, Integer> needed = new HashMap<>();
        for (int index = 0; index < this.plan.size(); index++) {
            BuildPlan.Target target = this.plan.target(index);
            if (!target.supported() || target.state() == null) {
                continue;
            }
            Item item = placeableItem(target.state());
            if (item != Items.AIR) {
                needed.merge(item, 1, Integer::sum);
            }
        }
        Map<String, Integer> missing = new HashMap<>();
        int blocks = 0;
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int shortBy = entry.getValue() - carried(player, entry.getKey());
            if (shortBy > 0) {
                missing.put(Registries.ITEM.getId(entry.getKey()).getPath(), shortBy);
                blocks += shortBy;
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        player.sendMessage(Text.literal("[Auto cc] Short of " + blocks + " block(s): "
                + nameCounts(missing) + ". Put them in the inventory and the build places them."),
                false);
    }

    /** How many of one item the player is carrying, hotbar, inventory and off hand together. */
    private static int carried(ClientPlayerEntity player, Item item) {
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        if (player.getOffHandStack().isOf(item)) {
            count += player.getOffHandStack().getCount();
        }
        return count;
    }

    public void pause() {
        if (this.state == State.RUNNING) {
            this.state = State.PAUSED;
            this.phase = Phase.NONE;
            this.note = "Paused at " + this.cursor + " / " + size();
            releaseMovementKeys();
        }
    }

    public void resume() {
        if (this.state == State.PAUSED) {
            this.state = State.RUNNING;
            this.attempts = 0;
            this.breakTicks = 0;
            this.workIndex = -1;
            this.note = "Resumed";
        }
    }

    /** Cancels the build, leaving whatever was already placed in the world. */
    public void stop(String reason) {
        if (this.state == State.RUNNING || this.state == State.PAUSED) {
            this.state = State.STOPPED;
            this.phase = Phase.NONE;
            this.note = reason;
        }
        releaseMovementKeys();
        this.route = null;
        this.plan = null;
        this.workIndex = -1;
        this.scrambling = false;
        this.bridging = false;
        this.bridgeTarget = null;
        this.tearingDown = false;
        this.aim = null;
        this.aimTicks = 0;
        this.sneakHoldTicks = 0;
        this.crouchTicks = 0;
        this.crouchHeld = false;
        this.asiding = false;
        this.asideTicks = 0;
        this.lastSwallowed = null;
        this.lastSwallowedIndex = -1;
        clearState();
        this.menuWatchTicks = 0;
        this.menuCulprit = null;
        this.verifyQueue.clear();
        this.lastAims.clear();
        this.rotationRetries.clear();
        this.sweeping = false;
        this.sweepOver = false;
        this.sweepIndex = 0;
        this.sweepTicks = 0;
        this.sweepRepairs = 0;
        this.sweepTries.clear();
        this.floatingCounted.clear();
        this.buildable = 0;
        this.unfinished = 0;
        this.wrongState = 0;
        this.unknownCells = 0;
        this.fluidCells = 0;
        this.crouchBlocked = 0;
        this.aimGiveUps = 0;
        this.improvised = 0;
        this.verified = -1;
        this.aimWaiting.clear();
        this.scaffoldBlocks.clear();
        this.restarts.clear();
        this.workTicks = 0;
    }

    private void complete(ClientWorld world) {
        if (!this.scaffoldBlocks.isEmpty() && !this.tearingDown) {
            // Take the temporary tower back down before calling the build finished.
            this.tearingDown = true;
            this.scrambling = false;
            this.phase = Phase.NONE;
            this.route = null;
            this.breakTicks = 0;
            releaseMovementKeys();
            this.note = "Removing " + this.scaffoldBlocks.size() + " temporary block(s)";
            return;
        }
        // The plan is finished, so the build is looked over once more before it is called done: every
        // cell is compared with the world, and one that is missing — or standing there turned the wrong
        // way — is handed back to the build for another go, now that every other block is in place. A
        // wall built last is the neighbour a wall built first was missing, so the second approach fixes
        // cells the first had no way to build. Only when there is nothing left to go back for does it
        // really finish.
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && sweep(world, player)) {
            return;
        }
        // Look once more at what is really standing before saying anything about it. The running
        // counters cannot tell the difference between a block placed for the build and a temporary
        // one that happens to match it while the builder climbs, so a build that ends with
        // scaffolding in its own footprint would claim work that is about to be taken down again.
        this.verified = countStanding(world);
        this.unfinished = Math.max(0, this.buildable - this.verified);
        int present = countBlocks(world);
        this.wrongState = Math.max(0, Math.min(this.unfinished, present - this.verified));
        this.state = State.DONE;
        this.phase = Phase.NONE;
        this.finishedAtMillis = System.currentTimeMillis();
        this.note = "Finished — " + this.placed + " placed, " + this.broken + " broken, "
                + this.satisfied + " already correct"
                + (this.wrongState > 0 ? ", " + this.wrongState + " in the wrong state" : "")
                + (this.unfinished - this.wrongState > 0
                        ? ", " + (this.unfinished - this.wrongState) + " missing" : "")
                + (this.reachOnly > 0 ? ", " + this.reachOnly + " out of view" : "")
                + (this.floating > 0 ? ", " + this.floating + " could not be placed" : "");
        announceCompletion();
        releaseMovementKeys();
        this.route = null;
        this.workIndex = -1;
    }

    // ------------------------------------------------------------------
    // Progress
    // ------------------------------------------------------------------

    public State state() {
        return this.state;
    }

    public Phase phase() {
        return this.phase;
    }

    public boolean isActive() {
        return this.state == State.RUNNING || this.state == State.PAUSED;
    }

    public int cursor() {
        return this.cursor;
    }

    public int size() {
        return this.plan == null ? 0 : this.plan.size();
    }

    public int placed() {
        return this.placed;
    }

    public int broken() {
        return this.broken;
    }

    public int satisfied() {
        return this.satisfied;
    }

    public int floating() {
        return this.floating;
    }

    public int missingItems() {
        return this.missingItems;
    }

    /** Placements that had to be made without a clear view, because nothing could see the block. */
    public int reachOnly() {
        return this.reachOnly;
    }

    /** Temporary blocks still standing, used to climb to a high block. */
    public int scaffoldRemaining() {
        return this.scaffoldBlocks.size();
    }

    /** Total temporary blocks placed so far, including ones already removed again. */
    public int scaffoldPlacedTotal() {
        return this.scaffoldPlaced;
    }

    /** Name of the block the current tower is built from, for status and for tests. */
    public String scaffoldMaterial() {
        return this.scaffoldMaterial;
    }

    /** Blocks the plan intends to place, whether or not the engine reached them. */
    public int planned() {
        return size();
    }

    /** Blocks confirmed in place: placed by the engine plus ones already correct when it looked. */
    public int confirmed() {
        return this.verified >= 0 ? this.verified : this.placed + this.satisfied;
    }

    /** Fraction of the buildable plan confirmed in place, 0..1. */
    public float completion() {
        int total = this.buildable;
        return total == 0 ? 1.0F : Math.min(1.0F, (float) this.confirmed() / total);
    }

    /** Cells of the plan that are not standing exactly as the schematic has them. */
    public int unfinished() {
        return this.unfinished;
    }

    /** Placements the build went back to because the game had put them down the wrong way round. */
    public int rotationRepairs() {
        return this.rotationRepairs;
    }

    /** Milliseconds from the start of the build to the moment it finished, or to now while running. */
    public long elapsedMillis() {
        if (this.startedAtMillis == 0L) {
            return 0L;
        }
        long end = this.finishedAtMillis == 0L ? System.currentTimeMillis() : this.finishedAtMillis;
        return Math.max(0L, end - this.startedAtMillis);
    }

    /**
     * Tells the player how the build ended, so the result is visible without watching the tracker.
     *
     * <p>A build that gave up after a block or two has not succeeded, and saying "finished" would
     * read as one. The two cases are reported differently, and a failure names the numbers so the
     * cause is guessable from the chat line alone.
     */
    private void announceCompletion() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        int planned = this.buildable;
        int confirmed = this.confirmed();
        long seconds = this.elapsedMillis() / 1000L;
        String took = (seconds / 60) + "m " + (seconds % 60) + "s";
        if (confirmed >= planned) {
            client.player.sendMessage(Text.literal("[Auto cc] Auto build complete — " + planned
                    + "/" + planned + " blocks in " + took), false);
            return;
        }
        // The count first, in two numbers that add up, and the reasons after it told apart from the
        // count by a full stop. The reason lines overlap each other — a cell with nothing to place it
        // against is also a cell that is missing — so one bracketed list of them never quite summed to
        // the whole and read as if blocks had gone missing twice.
        int absent = Math.max(0, this.unfinished - this.wrongState);
        String tally = (absent > 0 || this.wrongState > 0)
                ? ". " + absent + " missing, " + this.wrongState + " in the wrong state"
                : "";
        List<String> why = new ArrayList<>();
        if (this.missingItems > 0) {
            why.add(this.missingItems + " had no item in the inventory");
        }
        int unknown = this.unsupported - this.fluidCells;
        if (unknown > 0) {
            why.add(unknown + " unknown in this version");
        }
        if (this.fluidCells > 0) {
            why.add(this.fluidCells + " are water or lava the schematic wants");
        }
        if (this.floating > 0) {
            why.add(this.floating + " had nothing to build against");
        }
        if (this.unreachable > 0) {
            why.add(this.unreachable + " were given up on after three approaches");
        }
        if (this.crouchBlocked > 0) {
            why.add(this.crouchBlocked + " needed a click that the crouch key blocked — let go of "
                    + "crouch and run the build again");
        }
        client.player.sendMessage(Text.literal("[Auto cc] Auto build finished — " + confirmed
                + "/" + planned + " blocks standing in " + took + tally
                + (why.isEmpty() ? "" : ". " + String.join(", ", why) + ".")), false);
        ClientWorld world = client.world;
        if (world != null) {
            reportLeftovers(client.player, world);
        }
    }

    /**
     * Names the blocks a build could not finish, so "some blocks were left out" comes with the blocks
     * themselves and not just a count: which ones, where the first few of them are, and — for the ones
     * the builder had none of the item for — what to bring back for another run.
     *
     * <p>Another run is worth asking for on the item case and nowhere else. A cell left because there
     * was no lever anywhere in the inventory is a cell a second run places; a cell left because this
     * version of the game has no such block is not, and a cell left because the builder was pushed off
     * the approach three times is a coin toss. Only the first is put to the player as an errand.
     */
    private void reportLeftovers(ClientPlayerEntity player, ClientWorld world) {
        Map<String, Integer> left = new HashMap<>();
        Map<String, Integer> bring = new HashMap<>();
        List<String> spots = new ArrayList<>();
        for (int index = 0; index < this.plan.size(); index++) {
            BuildPlan.Target target = this.plan.target(index);
            if (!target.supported() || target.state() == null) {
                continue;
            }
            BlockPos pos = toBlockPos(target.world());
            if (built(world.getBlockState(pos), target.state())) {
                continue;
            }
            left.merge(target.blockId(), 1, Integer::sum);
            if (!hasItem(player, target.state())) {
                bring.merge(target.blockId(), 1, Integer::sum);
            }
            if (spots.size() < MAX_LISTED_SPOTS) {
                spots.add(shortName(target.blockId()) + " at " + pos.getX() + "," + pos.getY() + ","
                        + pos.getZ());
            }
        }
        if (left.isEmpty()) {
            return;
        }
        player.sendMessage(Text.literal("[Auto cc] Left out " + count(left) + ": " + nameCounts(left)
                + ". First " + String.join(", ", spots)
                + (count(left) > spots.size() ? " and more" : "") + "."), false);
        if (!bring.isEmpty()) {
            player.sendMessage(Text.literal("[Auto cc] Not in the inventory: " + nameCounts(bring)
                    + " — bring " + (count(bring) == 1 ? "it" : "them")
                    + " and run the build again."), false);
        }
    }

    /** Total number of blocks behind a block-id tally. */
    private static int count(Map<String, Integer> counts) {
        int total = 0;
        for (int value : counts.values()) {
            total += value;
        }
        return total;
    }

    /** "3x Lever, 1x Comparator" — biggest first, and cut short so one chat line stays one line. */
    private static String nameCounts(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < MAX_LISTED_KINDS; i++) {
            parts.add(entries.get(i).getValue() + "x " + shortName(entries.get(i).getKey()));
        }
        if (entries.size() > MAX_LISTED_KINDS) {
            parts.add("and " + (entries.size() - MAX_LISTED_KINDS) + " other kind(s)");
        }
        return String.join(", ", parts);
    }

    public int skipped() {
        return this.unsupported + this.floating + this.unreachable + this.rejected;
    }

    public String note() {
        return this.note;
    }

    /**
     * World-space bounding box of the current plan, or {@code null} when nothing is planned.
     *
     * @return {@code {minX, minY, minZ, maxX, maxY, maxZ}}
     */
    public int[] bounds() {
        return this.plan == null ? null : this.plan.worldBounds();
    }

    public GridPos anchor() {
        return this.plan == null ? null : this.plan.anchor();
    }

    /** One-line progress summary for the status bar. */
    public String progress() {
        String tail = (this.broken > 0 ? " · " + this.broken + " broken" : "")
                + (this.skipped() > 0 ? " · " + this.skipped() + " skipped" : "")
                + (this.improvised > 0 ? " · " + this.improvised + " inexact" : "");
        return switch (this.state) {
            case IDLE -> "Auto build idle";
            case RUNNING -> "Building " + this.cursor + "/" + size() + " · " + this.placed + " placed" + tail;
            case PAUSED -> "Paused " + this.cursor + "/" + size() + " · " + this.placed + " placed";
            case DONE -> "Done " + this.placed + "/" + size() + " placed" + tail;
            case STOPPED -> "Stopped " + this.cursor + "/" + size() + " · " + this.placed + " placed" + tail;
        };
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    /** Driven from the client tick. Does nothing unless a build is running. */
    public void tick(MinecraftClient client) {
        if (this.state != State.RUNNING) {
            return;
        }
        step(client);
        // Nothing may walk and crouch at once. A crouching player moves at a third of the speed and
        // cannot step off a ledge, so a crouch left down while a walk key is held is a builder that
        // crawls across the world for no reason at all — the whole of what a build looks like when it
        // has gone wrong. Placement takes its own crouch on the tick before each click, so a walk key
        // being down means that crouch has done its job, and the key goes up here whatever branch left
        // it down rather than in each of the places that might have.
        if (this.crouchHeld && walking(client)) {
            standUp(client);
        } else if (this.crouchHeld && client.options != null) {
            // Something outside the engine let the key up — a screen opening, the window losing focus,
            // another mod's key handling. The engine is about to click as though it were crouched, so
            // the key is put back rather than the click made standing: a click that is meant to skip a
            // block's own use action opens the screen it was crouching to avoid when it is not.
            setKey(client.options.sneakKey, true, sneakToggled(client));
        }
        watchMenus(client);
        logState(client);
    }

    /** One tick of the build itself. */
    private void step(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null || this.plan == null) {
            this.note = "Waiting for a world";
            return;
        }

        // The last look over the build is given a fixed slice of time and no more. A cell that cannot
        // be built must not keep the builder pottering about on a finished site: when the slice is up
        // the build is called finished, with whatever it could not do counted and said out loud.
        if (this.sweeping && !this.sweepOver && ++this.sweepTicks > MAX_SWEEP_TICKS) {
            this.sweepOver = true;
            this.note = "The last check has looked long enough — calling the build finished";
        }

        if (this.tearingDown) {
            tickTearDown(client, player, world);
            return;
        }
        if (this.scrambling) {
            tickClimb(client, player, world);
            return;
        }
        if (this.bridging) {
            tickBridge(client, player, world);
            return;
        }
        if (this.statePos != null) {
            stepState(client, player, world);
            return;
        }
        // One placement is looked at again per tick, so the build is never held up waiting for the game
        // to answer a click, while nothing it placed stays wrong for longer than it takes to come round.
        if (verifyPlacements(world)) {
            // Something was put right — a block handed back to be placed again, or a state click
            // started. The rest of the tick's work is left to the next tick: the note that says what
            // happened stays on the tracker long enough to be read, and the cell gets the tick to
            // itself, which is what its clock is counting.
            return;
        }

        settle(world);
        if (this.cursor >= this.plan.size()) {
            complete(world);
            return;
        }

        int index = this.workIndex;
        if (index < 0 || this.done[index]) {
            index = findPlaceable(world, player);
            if (index != this.workIndex) {
                this.triedGoals.clear();
                this.travelGoal = null;
                this.workTicks = 0;
                this.vantageTicks = 0;
                this.fellBack = false;
                this.climbBlocked = false;
                this.bridgeBlocked = false;
                this.bridgeTicks = 0;
                this.bridgeStuckTicks = 0;
                this.bridgeLastPos = null;
                this.bridging = false;
                this.bridgeSteps = 0;
                this.lastSwallowed = null;
                this.lastSwallowedIndex = -1;
                this.asideTicks = 0;
                this.asiding = false;
            }
            this.workIndex = index;
        }
        if (index < 0) {
            finishFloating(world);
            return;
        }

        BuildPlan.Target target = this.plan.target(index);
        BlockPos pos = toBlockPos(target.world());
        BlockState current = world.getBlockState(pos);

        // Nothing is allowed to hold the builder on one block for ever. Ten seconds of ticks is many
        // times what any single block needs, so from here the approach is treated as wrong: what was
        // planned for it is thrown away, which sends the builder around to another side and another
        // angle, and the block is only given up on if that does not work either.
        //
        // The window only starts once the block is in reach. Walking to it is not being stuck at it,
        // and the far side of a big schematic is a long walk that must not be thrown away.
        boolean reachable = inReach(player, pos);
        if (++this.workTicks > (reachable ? STUCK_TICKS_PER_ATTEMPT : TRAVEL_STUCK_TICKS_PER_ATTEMPT)) {
            if (restart(index, target)) {
                return;
            }
        }

        if (!reachable) {
            boolean reachingFromHere = false;
            this.phase = Phase.TRAVELLING;
            // Walking keys only: a crouch already held belongs to the click that is coming next, and
            // letting it go here and pressing it again two ticks later is what a crouch that never
            // stops bobbing looks like. The crouch still comes up when the walk really starts — the
            // tick wrapper drops it the moment a movement key is held.
            stopWalking();
            if (client.currentScreen != null) {
                this.note = "Close the menu so the builder can walk to the next block";
                return;
            }
            if (!steer(client, player, world, target.world())) {
                // Bridge before climbing. Walking sideways to the block and building up to it from
                // the ground is far more reliable than pillaring to the right height: nothing has
                // to be placed while airborne, so there is no jump to time.
                if (beginBridge(client, player, target.world())) {
                    return;
                }
                if (beginClimb(player, target.world())) {
                    return;
                }
                if (!inReach(player, pos)) {
                    this.note = "No walkable route to " + target.world();
                    this.unreachable++;
                    resolve(index);
                    return;
                }
                // Nothing to walk to, but the block is inside arm's length from where the player
                // stands. This is the indoor case, and it used to be the cell that got left out: a
                // machine's own shell has no room above the player to build a tower in, so the climb
                // that was supposed to reach the block cannot start, and the cell was written off as
                // out of reach while the block sat two blocks up and three along — well within reach
                // of the ground the player was already standing on. Placing it from here is the same
                // click, from below instead of beside, so fall through and try.
                this.note = "Nothing to walk to — reaching " + shortName(target.blockId())
                        + " at " + target.world() + " from here";
                reachingFromHere = true;
            }
            if (!reachingFromHere) {
                return;
            }
        }

        // Prefer a face the player can actually see, so the build looks like a player did it rather
        // than reaching through walls.
        boolean blocked = !current.isAir() && !current.isReplaceable();
        BlockHitResult face = blocked ? null : findVisibleFace(world, player, pos);
        boolean canSee = blocked ? canSeeBlock(world, player, pos) : face != null;

        // A block with air on every side has no face to find, so there is nothing to look for:
        // walking around hunting for one just burns the tick budget, which on a hundred block build
        // is the difference between finishing and being stopped part way.
        boolean nothingBeside = !blocked && !hasClickableNeighbour(world, pos);
        if (!canSee && !nothingBeside && this.triedGoals.size() <= MAX_VANTAGE_TRIES
                && this.vantageTicks++ < MAX_VANTAGE_TICKS) {
            // Walk around a little looking for an angle before settling for a reach placement.
            this.phase = Phase.TRAVELLING;
            // Walking keys only. This branch can fall through to the placement below when no route
            // and no climb are available, and the crouch that placement needs may already be held:
            // releasing it here would press it again on the same block for tick after tick.
            stopWalking();
            if (client.currentScreen != null) {
                this.note = "Close the menu so the builder can move into view";
                return;
            }
            boolean needsPlan = this.route == null || this.routeGoal == null
                    || !target.world().equals(this.routeGoal)
                    || this.routeIndex >= this.route.size();
            if (needsPlan) {
                this.triedGoals.add(new GridPos(player.getBlockX(), player.getBlockY(), player.getBlockZ()));
                this.route = null;
                this.routeGoal = null;
                this.travelGoal = null;
            }
            if (steer(client, player, world, target.world())) {
                this.note = "Repositioning to see " + target.world();
                return;
            }
            if (beginClimb(player, target.world())) {
                return;
            }
            // No route and no climb available: fall through and place from here below.
        }

        if (!canSee) {
            // Nothing can see this block — typically one enclosed inside the structure. Rather than
            // leave a hole in the build, place it from within reach; this is the only case where the
            // builder touches a block it cannot view.
            if (!this.fellBack) {
                this.fellBack = true;
                this.reachOnly++;
            }
            face = blocked ? null : findAnyFace(world, player, pos);
            if (!blocked && face == null) {
                // Nothing beside it to click. Rather than skip the block, build a temporary column
                // up to it from whatever solid ground is below, which is what a player does when a
                // block is left floating: the column gives the next placement a face, and it comes
                // back down with the rest of the scaffolding.
                if (canBeHelped(target, world, pos)
                        && (buildSupportColumn(client, player, world, target)
                                || buildPlaceholder(client, player, world, target))) {
                    return;
                }
                this.floating++;
                this.note = "Nothing in reach to place " + target.blockId() + " against";
                resolve(index);
                return;
            }
        }

        stopWalking();
        this.route = null;
        this.routeGoal = null;

        // The click lands on the block *beside* the target, so that is the one whose menu could
        // open — placing a block on a chest opens the chest just as surely as clicking it does.
        // That crouch is the business of {@link #place}, which knows exactly which block ends up
        // being clicked once the rotation has been worked out, and of nothing else in here.
        //
        // Mining in particular is done standing. It is the one thing in a build the crouch cannot
        // help with — a break sends no use click for a crouch to swallow — while a crouch held through
        // it is a crouch held across the walk to the next block, which is what makes a builder in a
        // world that still has terrain in it crawl from one obstruction to the next. Players do
        // crouch at a block they cannot reach, to keep from stepping off the edge they are standing
        // on, but that is not what this is: the builder stands still to mine and walks after.
        if (blocked) {
            // A block of the kind that is wanted, standing in the right cell, with only its state
            // wrong, is not something to break. Its state is what a click decides, and taking it out
            // and putting it back would land on the same first value it has now — so it is clicked
            // into shape instead, which is the whole of what the build had left to do there.
            if (clickCycles(current, target.state()) != null) {
                resolve(index);
                beginStateClick(pos, target.state());
                return;
            }
            standUp(client);
            this.phase = Phase.BREAKING;
            breakObstruction(client, player, pos, index);
            return;
        }

        this.phase = Phase.PLACING;
        place(client, player, world, target, pos, index, face);
    }

    /**
     * Walks the cursor forward over every block that is already settled: resolved, unknown to this
     * game version, or already holding the wanted state in the world.
     */
    private void settle(ClientWorld world) {
        while (this.cursor < this.plan.size()) {
            int index = this.cursor;
            if (this.done[index]) {
                this.cursor++;
                continue;
            }
            BuildPlan.Target target = this.plan.target(index);
            if (!target.supported()) {
                // Counted apart, because the two read very differently in the summary: a cell the
                // schematic fills with water or lava is not a block this game version has lost, and
                // "unknown in this version" about a pool of lava sends the player looking for a mod
                // update that would not put it there either.
                this.unsupported++;
                if (target.state() == null) {
                    this.unknownCells++;
                } else {
                    this.fluidCells++;
                }
                this.done[index] = true;
                this.cursor++;
                continue;
            }
            if (built(world.getBlockState(toBlockPos(target.world())), target.state())) {
                this.satisfied++;
                this.done[index] = true;
                this.cursor++;
                continue;
            }
            return;
        }
    }

    /**
     * Finds the earliest block that can be worked on now: one whose cell can be broken, or which has
     * a solid neighbour to place against. Blocks that are not there yet are left queued so they can
     * be built once a neighbour exists.
     *
     * @return the plan index to work on, or -1 when nothing remaining can be placed
     */
    private int findPlaceable(ClientWorld world, ClientPlayerEntity player) {
        int limit = Math.min(this.plan.size(), this.cursor + MAX_SCAN);
        int lastResort = -1;
        int waiting = -1;
        int needsSupport = -1;
        for (int index = this.cursor; index < limit; index++) {
            if (this.done[index]) {
                continue;
            }
            BuildPlan.Target target = this.plan.target(index);
            if (!target.supported()) {
                continue;
            }
            BlockPos pos = toBlockPos(target.world());
            BlockState current = world.getBlockState(pos);
            if (current.equals(target.state())) {
                continue;
            }
            if (!current.isAir() && !current.isReplaceable()) {
                // Breaking an obstruction never seals the player in.
                return index;
            }
            if (!hasClickableNeighbour(world, pos)) {
                // Air on every side, so there is no face to click and no amount of walking finds
                // one. A spare block beside it would give it one, which is what the build does with
                // it — but only after the rest of the plan has had its turn, since a neighbour
                // being built later is the usual reason a block has nothing to lean on yet.
                if (needsSupport < 0 && canBeHelped(target, world, pos)
                        && supportCell(world, player, target) != null) {
                    needsSupport = index;
                }
                continue;
            }
            if (wouldTrapPlayer(world, player, pos)) {
                // Filling this would wall the builder in. Hold it back and work elsewhere; the
                // blocks that keep the way out then get placed last, which is the difference
                // between finishing the build and standing inside it with nowhere to go.
                if (lastResort < 0) {
                    lastResort = index;
                }
                continue;
            }
            if (inTheWay(world, player, pos, target.state())) {
                // The builder is standing in the cell it wants to fill, and the game will not put a
                // block where the player is. Other work first — walking to it usually means walking
                // out of it, and by the time the plan is exhausted the builder has moved on — and this
                // cell only when nothing else is left, at which point it steps aside and comes back.
                if (lastResort < 0) {
                    lastResort = index;
                }
                continue;
            }
            if (this.aimWaiting.contains(index)) {
                // Held back for a neighbour that would give it the right facing. There is usually
                // plenty of other work, and by the time that runs out the neighbour has been built.
                if (waiting < 0) {
                    waiting = index;
                }
                continue;
            }
            return index;
        }
        // Nothing left anywhere, so blocks with nothing to lean on get their support built now: a
        // spare block beside each one gives it the face the game insists on.
        if (needsSupport >= 0) {
            return needsSupport;
        }
        // Everything left would seal the builder in. Rather than stall for ever, take one: the
        // plan wants the space filled, and the way out can be built again from above afterwards.
        if (lastResort >= 0) {
            return lastResort;
        }
        // Nothing else is placeable, so the wait is over: fill what is left as well as a click can,
        // and stop holding anything back for the rest of the build.
        if (waiting >= 0) {
            this.aimWaiting.clear();
            this.aimGiveUps++;
            return waiting;
        }
        return -1;
    }

    /**
     * True when filling {@code pos} would leave the player no way out of the cell they stand in.
     *
     * <p>This is the "boxes itself in" trap: a wall, a floor and a roof go up around the builder and
     * it ends up sealed inside the finished machine, unable to reach the rest of the plan. The way
     * out is checked the way the player would look for it — a neighbouring cell with room to stand
     * at foot and head height, over something solid to stand on.
     */
    private static boolean wouldTrapPlayer(ClientWorld world, ClientPlayerEntity player, BlockPos pos) {
        BlockPos feet = new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        for (Direction direction : STEP_DIRECTIONS) {
            BlockPos next = feet.offset(direction);
            if (openAfter(world, next, pos)
                    && openAfter(world, next.up(), pos)
                    && solidAfter(world, next.down(), pos)) {
                return false;
            }
        }
        return true;
    }

    /** True when this cell can be walked through, counting the block about to be placed as solid. */
    private static boolean openAfter(ClientWorld world, BlockPos cell, BlockPos filling) {
        if (cell.equals(filling)) {
            return false;
        }
        BlockState state = world.getBlockState(cell);
        return state.isAir() || state.isReplaceable();
    }

    /** True when this cell has something to stand on, counting the block about to be placed. */
    private static boolean solidAfter(ClientWorld world, BlockPos cell, BlockPos filling) {
        if (cell.equals(filling)) {
            return true;
        }
        BlockState state = world.getBlockState(cell);
        return !state.isAir() && !state.isReplaceable()
                && !state.getCollisionShape(world, cell).isEmpty();
    }

    /**
     * True when the block the plan wants in this cell would land inside the player.
     *
     * <p>The game will not put a block anywhere an entity is standing, and the builder's own body is an
     * entity: the click comes back refused whatever face it is aimed at and whatever angle it is made
     * from, so no number of retries gets the block in. It is not the aim and not the item — the builder
     * is standing where the block has to go. Only blocks that really collide count: a torch, a rail or a
     * line of redstone has no collision box at all, and the game is happy to put one inside the player.
     */
    private static boolean inTheWay(ClientWorld world, ClientPlayerEntity player, BlockPos pos,
                                    BlockState want) {
        if (want == null) {
            return false;
        }
        VoxelShape shape = want.getCollisionShape(world, pos);
        if (shape.isEmpty()) {
            return false;
        }
        return shape.offset(pos).getBoundingBox().intersects(player.getBoundingBox());
    }

    /**
     * Walks the builder out of the cell a block has to go in.
     *
     * <p>This is the one placement problem no better click answers. Four refused clicks, a restart, an
     * angle thrown away and a block written off all come from the player's own body standing where the
     * block belongs, and every one of those retries is the same refusal. So the cell the player is in
     * is taken off the list of spots worth standing on and the walk goes to a neighbour instead — which
     * is still in reach of the block, so the click is made from there, standing beside the same hole
     * rather than in it.
     *
     * @return true when the builder is out of the cell, or on its way out and the click should wait
     */
    private boolean stepAside(MinecraftClient client, ClientPlayerEntity player, ClientWorld world,
                              BuildPlan.Target target) {
        if (client.currentScreen != null) {
            this.note = "Close the menu so the builder can step out of the way";
            return true;
        }
        if (!this.asiding) {
            // A route planned before this one leads back to where the player already stands — the very
            // cell being walked out of — so the walk starts afresh from here. Once only: replanning on
            // every tick of the walk would restart it from the beginning each time.
            this.asiding = true;
            this.asideTicks = 0;
            this.route = null;
            this.routeGoal = null;
            this.travelGoal = null;
        }
        if (++this.asideTicks > MAX_ASIDE_TICKS) {
            this.asiding = false;
            this.note = "Nowhere to step out of the way of " + shortName(target.blockId()) + " at "
                    + target.world();
            return false;
        }
        GridPos feet = new GridPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        this.triedGoals.add(feet);
        this.note = "Stepping out of the way of " + shortName(target.blockId()) + " at " + target.world();
        this.phase = Phase.TRAVELLING;
        stopWalking();
        boolean walking = steer(client, player, world, target.world());
        if (!inTheWay(world, player, toBlockPos(target.world()), target.state())) {
            // Really clear of the cell: the click can go ahead from here.
            return walking;
        }
        // Still inside it, and this is the part that made stepping aside look like a build that had
        // stopped working. A walk ends when it is near the middle of the last cell of its route, and a
        // cell beside the target is only one block away: standing 0.7 of a block short of its middle
        // leaves the player's own width reaching back into the target cell, the game refuses the
        // placement exactly as before, and the same walk is asked for again on the next tick — for two
        // hundred ticks, then a restart, then two hundred more. So the last arm's length is walked out
        // deliberately, until the cell is really empty of the player.
        nudgeOutOfTheWay(client, player, world, target);
        return true;
    }

    /**
     * Presses on for the last few ticks of a step aside, until the target cell is clear of the player.
     *
     * <p>Aims at the middle of the nearest cell the player could stand on beside the block, which is a
     * whole block away from the block's own cell — far enough that a player standing on its middle does
     * not reach back into it. Walking is all this does: the walk is driven by the movement keys, so the
     * engine keeps noticing the cell is not clear yet and keeps the keys down for as long as it takes.
     */
    private void nudgeOutOfTheWay(MinecraftClient client, ClientPlayerEntity player, ClientWorld world,
                                  BuildPlan.Target target) {
        GridPos playerPos = new GridPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        GridPos goal = chooseStandGoal(world, playerPos, target.world(), this.triedGoals);
        if (goal == null) {
            return;
        }
        lookAt(player, new Vec3d(goal.x() + 0.5, player.getY(), goal.z() + 0.5));
        client.options.forwardKey.setPressed(true);
        client.options.jumpKey.setPressed(goal.y() > playerPos.y());
        setKey(client.options.sprintKey, goal.y() >= playerPos.y(), sprintToggled(client.options));
    }

    /** How many of the plan's blocks are standing in the world right now, exactly as the plan has them. */
    private int countStanding(ClientWorld world) {
        int standing = 0;
        for (int index = 0; index < this.plan.size(); index++) {
            BuildPlan.Target target = this.plan.target(index);
            if (!target.supported()) {
                continue;
            }
            BlockState want = target.state();
            if (want != null && built(got(world, target), want)) {
                standing++;
            }
        }
        return standing;
    }

    private static BlockState got(ClientWorld world, BuildPlan.Target target) {
        return world.getBlockState(toBlockPos(target.world()));
    }

    /**
     * True when a cell holds what the schematic asks for as far as any builder could make it so.
     *
     * <p>Not the same thing as an equal state, and the difference is the whole point. Half of a
     * redstone build's blocks carry a property the world sets and no one can: an observer is
     * {@code powered} for two ticks after it sees something move, a dispenser is {@code triggered} for
     * two ticks after it fires, a repeater next to a live wire is {@code powered}, a note block under a
     * signal is. A build read the moment it finishes catches a dozen of them mid-pulse, and a report
     * that counted those as unfinished would call a finished build broken — every time, and worst on
     * exactly the machines a player builds this mod for.
     *
     * <p>So a cell counts as built when the block is the right one and nothing else is wrong except
     * properties the world owns: not ones a click decides, and not ones the face of the click decides.
     * Those two are the builder's business, and they are what "left out" and "in the wrong state" are
     * kept for.
     */
    private static boolean built(BlockState got, BlockState want) {
        if (got.getBlock() != want.getBlock()) {
            return false;
        }
        if (got.equals(want)) {
            return true;
        }
        for (Property<?> property : want.getProperties()) {
            if (!got.get(property).equals(want.get(property))
                    && (PLACEMENT_PROPERTIES.contains(property.getName())
                            || clickSteps(got.getBlock(), property.getName()))) {
                return false;
            }
        }
        return true;
    }

    /** How many of the plan's cells hold the right block, whatever state it came out in. */
    private int countBlocks(ClientWorld world) {
        int present = 0;
        for (int index = 0; index < this.plan.size(); index++) {
            BuildPlan.Target target = this.plan.target(index);
            BlockState want = target.state();
            if (want != null && world.getBlockState(toBlockPos(target.world())).getBlock() == want.getBlock()) {
                present++;
            }
        }
        return present;
    }

    /** How many of the plan's cells want a block this game version can actually put down. */
    private int countBuildable() {
        int total = 0;
        for (int index = 0; index < this.plan.size(); index++) {
            if (this.plan.target(index).supported()) {
                total++;
            }
        }
        return total;
    }

    /**
     * The last look over the whole plan, taken once every cell has had its turn.
     *
     * <p>Before the build is called finished it walks the plan once more and compares it with what is
     * really standing, because the two are not the same thing: a click can be refused, a placement can
     * be turned by the game, an item can have run out, and a cell can be written off by the watchdog
     * while the rest of the structure is still going up. Every cell that is not in the build is handed
     * back to the plan — with a fresh set of tries and a different click — so what is left at the end is
     * the schematic rather than the schematic with holes in it.
     *
     * <p>Bounded three ways, so a build that genuinely cannot be finished still ends: each cell may be
     * handed back {@link #MAX_SWEEP_TRIES} times, the sweep may hand back {@link #MAX_SWEEP_REPAIRS}
     * cells in all, and it may keep the build going for {@link #MAX_SWEEP_TICKS} ticks.
     *
     * @return true when a cell was handed back and there is work to do again
     */
    private boolean sweep(ClientWorld world, ClientPlayerEntity player) {
        if (this.sweepOver) {
            return false;
        }
        if (!this.sweeping) {
            this.sweeping = true;
            this.sweepIndex = 0;
        }
        while (this.sweepIndex < this.plan.size()) {
            int index = this.sweepIndex++;
            BuildPlan.Target target = this.plan.target(index);
            BlockState want = target.state();
            if (!target.supported() || want == null) {
                continue;
            }
            BlockPos pos = toBlockPos(target.world());
            BlockState got = world.getBlockState(pos);
            if (got.equals(want)) {
                continue;
            }
            boolean missing = got.getBlock() != want.getBlock();
            if (!missing && !placementDiffers(got, want) && clickCycles(got, want) == null) {
                // The right block, holding something neither the world nor a click will change: a
                // lever's power, a note block's instrument, a crafter's flag. Breaking it and putting
                // it back would land on exactly the same state, so it is left standing where it is.
                // A repeater left at delay 1 is not this — that is a click away from right, and the
                // whole point of the last look over the build is to come back for those too.
                continue;
            }
            if (this.sweepRepairs >= MAX_SWEEP_REPAIRS
                    || this.sweepTries.getOrDefault(index, 0) >= MAX_SWEEP_TRIES) {
                continue;
            }
            if (missing && !hasItem(player, want)) {
                // Nothing to put in the cell, so asking again would only ask the same question. A block
                // that is already there and merely the wrong state is the other case: it is clicked
                // where it stands and needs no item at all.
                continue;
            }
            this.sweepTries.merge(index, 1, Integer::sum);
            this.sweepRepairs++;
            // The walk stays on this cell until it can see for itself that the build got there. Moving
            // on past it would mean one attempt and no more: the cell is out of the plan, the rest of
            // the walk finishes on the next tick, and the build declares itself done with that hole
            // still in it.
            this.sweepIndex = index;
            // A fresh set of tries, so the cell is not written off again on sight for what went wrong
            // the first time round. The click it failed with is kept: the next attempt then makes a
            // different one, which is the whole point of going back to it.
            this.restarts.remove(index);
            this.aimWaiting.remove(index);
            this.rotationRetries.remove(index);
            this.done[index] = false;
            this.cursor = Math.min(this.cursor, index);
            this.workIndex = -1;
            this.aim = null;
            this.aimTicks = 0;
            this.workTicks = 0;
            this.note = got.getBlock() == want.getBlock()
                    ? "Going back to " + shortName(target.blockId()) + " at " + target.world()
                            + " — it is not the way the schematic has it"
                    : "Going back for " + shortName(target.blockId()) + " at " + target.world()
                            + " — it is not in the build";
            return true;
        }
        return false;
    }

    /** Ends the build when nothing left in the plan can be supported by the world. */
    private void finishFloating(ClientWorld world) {
        int remaining = 0;
        for (int index = this.cursor; index < this.plan.size(); index++) {
            // Counted once each, however often the build comes back round: the same cell left out
            // twice is still one block missing from the build, and saying otherwise understates how
            // much of the schematic is standing.
            if (!this.done[index] && this.plan.target(index).supported()
                    && this.floatingCounted.add(index)) {
                remaining++;
            }
        }
        this.floating += remaining;
        this.note = remaining == 0
                ? "Nothing left to place"
                : remaining + " block(s) had no support and were left out";
        complete(world);
    }

    /** Marks a plan index finished. The cursor catches up on the next tick's settle pass. */
    private void resolve(int index) {
        if (index >= 0 && index < this.done.length) {
            this.done[index] = true;
        }
        this.workIndex = -1;
        this.attempts = 0;
        this.breakTicks = 0;
        this.route = null;
        this.routeGoal = null;
        this.travelGoal = null;
        this.travelTicks = 0;
        this.stuckTicks = 0;
        this.vantageTicks = 0;
        this.fellBack = false;
        this.climbBlocked = false;
        this.bridgeBlocked = false;
        this.bridgeTicks = 0;
        this.bridgeStuckTicks = 0;
        this.bridgeLastPos = null;
        this.bridging = false;
        this.bridgeTarget = null;
        this.bridgeSteps = 0;
        this.asideTicks = 0;
        this.asiding = false;
        this.triedGoals.clear();
        this.phase = Phase.NONE;
    }

    // ------------------------------------------------------------------
    // Placing
    // ------------------------------------------------------------------

    private void place(MinecraftClient client, ClientPlayerEntity player, ClientWorld world,
                       BuildPlan.Target target, BlockPos pos, int index, BlockHitResult hit) {
        ClientPlayerInteractionManager interactions = client.interactionManager;
        if (interactions == null) {
            return;
        }
        if (client.currentScreen != null) {
            // Nothing may be clicked through a menu, and the item a placement needs cannot be brought
            // out while one is up. The menu the build's own click opened is closed by {@link
            // #watchMenus}; anything else is the player's own and is left for them to close.
            this.note = "Close the menu so the builder can place " + shortName(target.blockId());
            return;
        }
        if (hit == null) {
            this.floating++;
            this.note = "Nothing visible to place " + target.blockId() + " against";
            resolve(index);
            return;
        }
        if (!selectItem(player, target.state())) {
            this.missingItems++;
            this.note = "Missing " + target.blockId() + " in the inventory";
            resolve(index);
            return;
        }

        // The game refuses to put a block where an entity stands, and the builder is one — so a click
        // for a cell the player's own body is inside comes back refused no matter how well it is
        // aimed. Step out of the cell first and come back to the click from beside it.
        if (inTheWay(world, player, pos, target.state()) && stepAside(client, player, world, target)) {
            return;
        }

        Aim chosen = chooseAim(player, world, pos, target.state(), hit);
        if (chosen == null) {
            this.floating++;
            this.note = "Nothing in reach to place " + target.blockId() + " against";
            resolve(index);
            return;
        }

        // A click that would place the block pointing the wrong way is worth waiting for: the facing
        // is the whole point of a machine block, and the face that gives it may come from a
        // neighbour that has not been built yet. Only a mismatch a click could really fix is ever
        // waited for, and only a few blocks at a time, so the build keeps moving: a repeater's delay
        // or a note block's pitch is never worth a wait, because no click can change it.
        //
        // This is settled before the crouch on purpose. Holding a block back can take a long time,
        // and a crouch held for all of it would leave the player frozen to the spot in a crouch for
        // seconds at a time, moving at a crawl whenever the build does get going again.
        if (isFixableByAngle(chosen, target) && !this.aimWaiting.contains(index)
                && this.aimWaiting.size() < MAX_AIM_WAITING && this.aimGiveUps < MAX_AIM_GIVE_UPS) {
            this.aimWaiting.add(index);
            this.workIndex = -1;
            this.note = "Waiting for a neighbour that faces " + shortName(target.blockId())
                    + " the way the schematic wants";
            return;
        }

        // Crouch for the block that is really going to be clicked. Vanilla runs a clicked block's own
        // use action first, which is what opens a chest or a barrel, so a click on anything with a
        // menu behind it has to be made crouched; everything else is clicked standing, so the builder
        // walks at full speed and steps off ledges like a player does.
        //
        // The key is held for a run of such clicks rather than pressed and released around each one.
        // A schematic worth building is usually full of containers — a regear, a sorter, a farm — so
        // nearly every click in it needs the crouch, and a key that goes down and up for each of them
        // is a builder that does nothing but bob up and down. The wait for the press to reach the
        // server is paid once, on the tick the run starts; every click after it goes out on the tick
        // it is ready. Whatever ends a run — walking, mining, bridging, climbing, stopping — lets the
        // key up again, and so does the tick wrapper if any of them ever forgets.
        //
        // A run is not ended just because one click in it turns out not to need the crouch. Crouching
        // through a click that would have worked standing costs nothing, and standing up mid-run costs
        // more than the tick it takes: crouching lowers the player's eyes, the eyes decide which face
        // of the block looks nearest, and the face decides whether a crouch is needed — so a builder
        // that stands up the moment the click no longer wants one, and sits down again when the next
        // one does, turns its own crouch into a stutter that can keep it off the block for ever.
        boolean clickCrouched = needsCrouch(world.getBlockState(chosen.hit().getBlockPos()));
        if (clickCrouched && !this.crouchHeld) {
            pressCrouch(client);
            this.crouchTicks = 0;
            // Choose the click again under the crouch it will be made with: a few blocks place
            // differently while sneaking, and the server only learns about a crouch on the next
            // movement packet, so pressing the key and clicking in one tick does nothing at all.
            this.aim = null;
            this.note = "Crouching to place " + shortName(target.blockId());
            return;
        }

        if (!chosen.sameAs(this.aim)) {
            // A new click, and a new direction to be facing for it. Turn first and click later: the
            // server decides the block's facing from its own copy of the player's look.
            this.aim = chosen;
            this.aimTicks = 0;
            applyLook(player, chosen.yaw(), chosen.pitch());
            this.note = "Lining up " + shortName(target.blockId()) + " at " + target.world();
            return;
        }
        applyLook(player, chosen.yaw(), chosen.pitch());
        if (++this.aimTicks < AIM_WARMUP_TICKS) {
            return;
        }

        if (isImprovised(chosen, target) && leftForThePlayer(chosen.produced(), target.state())) {
            this.improvised++;
        }
        this.aim = null;
        if (this.crouchHeld) {
            this.crouchTicks++;
        }
        BlockPos clicked = chosen.hit().getBlockPos();
        // Watch what this click does: a menu coming up is the block taking the click for itself instead
        // of letting a block be placed, which is exactly the case the crouch exists for.
        this.menuCulprit = world.getBlockState(clicked).getBlock();
        this.menuWatchTicks = MENU_WATCH_TICKS;
        this.lastAims.put(index, chosen);
        ActionResult result = interactions.interactBlock(player, Hand.MAIN_HAND, chosen.hit());
        if (result.isAccepted()) {
            if (swallowed(world, pos, chosen, target)) {
                // The click was taken but nothing arrived. That is what a block with a use action of
                // its own does — a lever thrown, a note block played, a chest opened — and the
                // accepted result hid it, so the builder would click the same spot for ever without
                // this check. Crouching is what makes the game skip that use action, so the block is
                // crouched for from now on and this same click tried again.
                //
                // It has to happen twice running before the block is written off as one of those.
                // Nothing arriving for a tick means very little on its own — a block the server
                // placed and the client has not been told about yet looks exactly like a click that
                // was swallowed — and being wrong is permanent: a common building block learned this
                // way is crouched for on every remaining click of the build, which is a builder that
                // does nothing but squat. A second swallowed click on the same block is the cheaper
                // thing to wait for.
                Block culpritBlock = world.getBlockState(clicked).getBlock();
                boolean repeat = this.lastSwallowedIndex == index && this.lastSwallowed == culpritBlock;
                boolean known = this.clickSwallowers.contains(culpritBlock);
                this.lastSwallowed = culpritBlock;
                this.lastSwallowedIndex = index;
                if (!known) {
                    if (repeat) {
                        this.clickSwallowers.add(culpritBlock);
                        this.note = "Learned to crouch when clicking "
                                + culpritBlock.getName().getString() + " around " + target.world();
                    } else {
                        this.note = "Click on " + culpritBlock.getName().getString()
                                + " placed nothing — trying once more";
                    }
                    return;
                }
            }
            // Every placement is worth coming back to: the client chooses the click from its own copy
            // of the world and the game places the block from its own, a tick or two later. Where the
            // two disagree the block lands turned — or nothing lands at all — and nothing in the click
            // itself says so; only looking at what was really placed does. Whether the click was the
            // exact one is not what decides it: a compromise click is the one to look at hardest.
            if (target.state() != null) {
                this.verifyQueue.add(new Placed(pos, index, target.state(), 0));
            }
            this.placed++;
            this.note = "Placed " + shortName(target.blockId()) + " at " + target.world()
                    + (chosen.produced() != null && target.state() != null
                            && !chosen.produced().equals(target.state())
                            ? " (best possible rotation)" : "");
            resolve(index);
            // The state step that follows a click that put a block holding the wrong value in place —
            // delay 1 where the schematic wants delay 3 — is deliberately not started here. The click
            // has only just been sent, so what the client's world shows for that cell this tick is a
            // prediction, and clicking a block the game has not placed yet is the surest way to get the
            // two copies of the world out of step. The look over the placement does it instead, one or
            // two ticks later, when the world really holds the block: see {@link #verifyPlacements}.
            return;
        }

        this.rejected++;
        if (inTheWay(world, player, pos, target.state()) && stepAside(client, player, world, target)) {
            // A refused click at a cell the player is standing in is not a bad aim and not a bad
            // angle: no click from here can ever work. Give the walk its own ticks rather than burning
            // the attempts on refusals that were never going to be anything else.
            this.attempts = 0;
            return;
        }
        if (++this.attempts < MAX_PLACE_ATTEMPTS) {
            this.note = "Retrying " + target.blockId();
            return;
        }
        // A few clicks in a row refused by the game means the approach is wrong, not that the block
        // cannot be built: the face is behind something, the builder is standing in the cell, or the
        // aim has drifted. So this counts as being stuck — the block is not skipped here, it is
        // thrown away and come at again from somewhere else, which is what a player does. Only after
        // the whole stuck window has passed with no better luck is the block written off.
        this.restart(index, target);
    }

    /**
     * True once the block really is in the world at {@code pos}, whatever exact state it came out
     * with. Checked rather than trusting the click's return value: an accepted click can still have
     * placed nothing at all, which is how a lever or a note block swallows it.
     */
    private static boolean arrived(ClientWorld world, BlockPos pos, BuildPlan.Target target) {
        BlockState want = target.state();
        return want != null && world.getBlockState(pos).getBlock() == want.getBlock();
    }

    /**
     * True when an accepted click still put nothing in the world, which means the block that was
     * clicked ran a use action of its own and ate the click.
     *
     * <p>Deliberately cautious, because wrongly believing a plain block swallows clicks would send
     * the builder back to crouching at everything: the wanted state is known, the click was picked
     * to produce exactly that block, and the cell is checked to see whether it really got one.
     * Unless all of that holds the old answer is kept, so a click that places something unexpected
     * is still counted as a placement rather than retried.
     */
    private static boolean swallowed(ClientWorld world, BlockPos pos, Aim chosen,
                                     BuildPlan.Target target) {
        BlockState want = target.state();
        if (want == null || chosen.produced() == null
                || chosen.produced().getBlock() != want.getBlock()) {
            return false;
        }
        return world.getBlockState(pos).getBlock() != want.getBlock();
    }

    /**
     * True when the click available right now cannot produce the state the schematic stores — a
     * hopper that would point down instead of east, a stair that would come out mirrored.
     */
    private static boolean isImprovised(Aim chosen, BuildPlan.Target target) {
        BlockState want = target.state();
        return chosen.produced() != null && want != null && !chosen.produced().equals(want);
    }

    /**
     * True when a click that could not match the schematic left something no later click can put right.
     *
     * <p>A repeater always lands holding delay 1 and a note block note 0 whatever the schematic stores,
     * so a click that is "not exact" is not the same thing as a block the build cannot finish: the
     * stepping clicks do those afterwards, standing at the same block. Only a difference with no click
     * behind it — a facing no angle could give, a piece of the world's own redstone — is really left for
     * the player, and only that is counted as such. Reporting the other kind would have every machine
     * the build gets entirely right announced as blocks the player has to finish by hand.
     */
    private static boolean leftForThePlayer(BlockState produced, BlockState want) {
        if (want == null || produced == null || produced.getBlock() != want.getBlock()) {
            return true;
        }
        for (Property<?> property : want.getProperties()) {
            if (!produced.get(property).equals(want.get(property))
                    && !clickSteps(produced.getBlock(), property.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when waiting for a different click could still give the wanted state, because what
     * differs is a property vanilla derives from the face that was clicked and the way the player
     * is looking. A mismatch on anything else — a repeater's delay, a note block's pitch, whether a
     * redstone component is powered — cannot be fixed by a different click, so it is not waited for.
     */
    private static boolean isFixableByAngle(Aim chosen, BuildPlan.Target target) {
        BlockState want = target.state();
        BlockState produced = chosen.produced();
        if (produced == null || want == null || produced.getBlock() != want.getBlock()) {
            return false;
        }
        for (Property<?> property : want.getProperties()) {
            if (!produced.get(property).equals(want.get(property))
                    && PLACEMENT_PROPERTIES.contains(property.getName())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Aiming
    // ------------------------------------------------------------------

    /** Where the player is looking. */
    private record Look(float yaw, float pitch) {
    }

    /**
     * A click, the direction to be looking when it lands, and the state it will produce.
     *
     * <p>Facing is not a property that can be set directly. Vanilla works it out from the face that
     * was clicked and the direction the player faces, and the answer differs per block: stairs and
     * observers turn to face the player, a log lies along the axis of the face that was clicked, a
     * piston points away from the player's look. A build placed the obvious way round therefore
     * comes out mirrored, which quietly breaks every machine in it.
     */
    private record Aim(BlockHitResult hit, float yaw, float pitch, BlockState produced) {
        /**
         * Whether this is the same click as {@code other}.
         *
         * <p>Not record equality: {@link BlockHitResult} has no {@code equals} of its own, so two
         * identical hits compare as different objects and the aim would look new on every tick —
         * the builder would turn toward the block for ever without ever clicking it.
         */
        boolean sameAs(Aim other) {
            return other != null
                    && this.hit.getBlockPos().equals(other.hit.getBlockPos())
                    && this.hit.getSide() == other.hit.getSide()
                    && Float.compare(this.yaw, other.yaw) == 0
                    && Float.compare(this.pitch, other.pitch) == 0
                    && this.produced == other.produced;
        }
    }

    /**
     * Picks the click that makes the game place what the schematic asks for.
     *
     * <p>Rather than re-implementing those rules per block family — they disagree about almost
     * everything — this asks the game itself. {@link Block#getPlacementState} is the very call the
     * item makes when it is used, so each candidate click can be scored on the state it would
     * produce, and the click that reproduces the schematic exactly is the one to make.
     *
     * @return the click to make, or null when there is nothing in reach to click at all
     */
    private Aim chooseAim(ClientPlayerEntity player, ClientWorld world, BlockPos pos,
                          BlockState want, BlockHitResult fallback) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof BlockItem blockItem) || want == null) {
            // Nothing directional to aim for: look at the face and get on with it.
            Look look = lookTo(player, fallback.getPos());
            return new Aim(fallback, look.yaw(), look.pitch(), null);
        }
        List<BlockHitResult> faces = placementFaces(world, player, pos);
        // The click that failed to put the block the right way round is not worth making again, so it is
        // passed over once: a block that came out turned is almost always one whose click the game read
        // differently, and the same click would be read the same way a second time.
        Aim avoid = this.lastAims.get(this.workIndex);
        Aim held = heldAim(player, faces, pos, stack, blockItem);
        if (held != null && (avoid == null || !held.sameAs(avoid))) {
            return held;
        }
        Aim best = null;
        int bestScore = -1;
        Aim sameAsLast = null;
        for (BlockHitResult face : faces) {
            for (Look look : candidateLooks(player, face.getPos())) {
                BlockState produced = simulatePlacement(player, stack, blockItem, face, look, pos);
                if (produced == null) {
                    continue;
                }
                if (produced.equals(want)) {
                    // Exact match, and faces are ordered nearest first: nothing later can beat it.
                    Aim candidate = new Aim(face, look.yaw(), look.pitch(), produced);
                    if (avoid == null || !candidate.sameAs(avoid)) {
                        return candidate;
                    }
                    // The only exact click on offer is the one that came out wrong. Keep it as the second
                    // choice, and see whether another face that is not quite exact beats making it again.
                    sameAsLast = candidate;
                    continue;
                }
                int score = propertyScore(produced, want);
                if (score > bestScore) {
                    bestScore = score;
                    best = new Aim(face, look.yaw(), look.pitch(), produced);
                }
            }
        }
        // Nothing else to try: better the click that came out wrong than no block at all.
        return sameAsLast != null ? sameAsLast : best;
    }

    /**
     * The click already chosen for this block, if it is still one of the faces on offer and still
     * produces the state it produced when it was chosen.
     *
     * <p>Re-chosen from nothing every tick, the click wanders. The faces are ordered by how far they
     * are from the player's eyes, and the look the builder is holding moves those eyes, so two
     * equally good faces swap places back and forth for ever: the builder turns, the turn changes
     * which face looks nearest, it turns back — one block that is never clicked, and the crouch that
     * belongs to the click held through every tick of it. Holding on to a click that is still good
     * settles it in the two ticks the click itself needs.
     */
    private Aim heldAim(ClientPlayerEntity player, List<BlockHitResult> faces, BlockPos pos,
                        ItemStack stack, BlockItem item) {
        Aim held = this.aim;
        if (held == null || held.produced() == null) {
            return null;
        }
        for (BlockHitResult face : faces) {
            if (!face.getBlockPos().equals(held.hit().getBlockPos())
                    || face.getSide() != held.hit().getSide()) {
                continue;
            }
            BlockState again = simulatePlacement(player, stack, item, face,
                    new Look(held.yaw(), held.pitch()), pos);
            return held.produced().equals(again) ? new Aim(face, held.yaw(), held.pitch(), again) : null;
        }
        return null;
    }

    /**
     * What the game would actually place for a given face and look, or null when that click would
     * put the block somewhere else entirely.
     */
    private static BlockState simulatePlacement(ClientPlayerEntity player, ItemStack stack,
                                                BlockItem item, BlockHitResult hit, Look look,
                                                BlockPos wanted) {
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        player.setYaw(look.yaw());
        player.setPitch(look.pitch());
        try {
            ItemPlacementContext context = new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit);
            // The clicked block has to be the one the block goes next to, or the click would fill a
            // different cell than the plan is asking for.
            if (!context.getBlockPos().equals(wanted)) {
                return null;
            }
            return item.getBlock().getPlacementState(context);
        } catch (RuntimeException ignored) {
            // A block whose placement state cannot be worked out here is simply not a candidate.
            return null;
        } finally {
            player.setYaw(yaw);
            player.setPitch(pitch);
        }
    }

    /** How many of the wanted state's properties a candidate click would get right. */
    private static int propertyScore(BlockState produced, BlockState want) {
        if (produced.getBlock() != want.getBlock()) {
            return -1;
        }
        int score = 0;
        for (Property<?> property : want.getProperties()) {
            if (produced.get(property).equals(want.get(property))) {
                score++;
            }
        }
        return score;
    }

    /**
     * The directions to look while testing a click: first the one a player would use, which is
     * straight at the face, then the four cardinal turns level, up and down. Those cover every
     * facing and axis a placement can be told to take.
     */
    private static List<Look> candidateLooks(ClientPlayerEntity player, Vec3d point) {
        List<Look> looks = new ArrayList<>(1 + LOOK_YAWS.length * LOOK_PITCHES.length);
        looks.add(lookTo(player, point));
        for (float pitch : LOOK_PITCHES) {
            for (float yaw : LOOK_YAWS) {
                looks.add(new Look(yaw, pitch));
            }
        }
        return looks;
    }

    /**
     * Every face the player could click to fill {@code pos}: the visible ones nearest first, or
     * every face in range when none of them can be seen.
     */
    private List<BlockHitResult> placementFaces(ClientWorld world, ClientPlayerEntity player,
                                                BlockPos pos) {
        Vec3d eyes = player.getEyePos();
        double range = Math.max(3.0, player.getBlockInteractionRange());
        List<BlockHitResult> visible = new ArrayList<>(PLACE_ORDER.length);
        List<BlockHitResult> all = new ArrayList<>(PLACE_ORDER.length);
        for (Direction direction : PLACE_ORDER) {
            BlockPos support = pos.offset(direction);
            BlockState state = world.getBlockState(support);
            if (state.isAir() || state.isReplaceable()) {
                continue;
            }
            Direction face = direction.getOpposite();
            Vec3d point = new Vec3d(
                    support.getX() + 0.5 + face.getOffsetX() * 0.5,
                    support.getY() + 0.5 + face.getOffsetY() * 0.5,
                    support.getZ() + 0.5 + face.getOffsetZ() * 0.5);
            if (eyes.squaredDistanceTo(point) > range * range) {
                continue;
            }
            BlockHitResult hit = new BlockHitResult(point, face, support, false);
            all.add(hit);
            if (visible(world, player, eyes, point, support, face)) {
                visible.add(hit);
            }
        }
        // Plain blocks first, so a click that needs no crouch is preferred whenever it gives the
        // same block state. The sort is stable, so within each group the nearest face still wins.
        all.sort((first, second) -> Boolean.compare(
                needsCrouch(world.getBlockState(first.getBlockPos())),
                needsCrouch(world.getBlockState(second.getBlockPos()))));
        visible.sort((first, second) -> Boolean.compare(
                needsCrouch(world.getBlockState(first.getBlockPos())),
                needsCrouch(world.getBlockState(second.getBlockPos()))));
        return visible.isEmpty() ? all : visible;
    }

    /**
     * Holds a crouch if the block about to be clicked could open a screen or do something of its
     * own. Vanilla runs the clicked block's use action first, and that is what opens a chest while
     * the builder is holding a block; crouching skips it, but the server only knows about the
     * crouch once the key press has been sent, so the click waits a tick for that to happen.
     *
     * @return true when the caller should wait a tick instead of clicking
     */
    private boolean holdCrouchFor(MinecraftClient client, ClientWorld world, BlockPos clicked) {
        if (client.currentScreen != null) {
            // A screen stops the scaffolding click as well: the click would belong to the menu, and the
            // spare block cannot be moved into hand while the inventory is on screen.
            this.note = "Close the menu so the builder can carry on";
            return true;
        }
        if (!needsCrouch(world.getBlockState(clicked))) {
            standUp(client);
            return false;
        }
        pressCrouch(client);
        return ++this.sneakHoldTicks < SNEAK_WARMUP_TICKS;
    }


    /**
     * Cheap check used while scanning the plan: is there anything next to {@code pos} that could be
     * clicked at all? Anything non-air and not replaceable counts, which includes torches and rails
     * that have no collision box, while fluids and air do not.
     */
    private static boolean hasClickableNeighbour(ClientWorld world, BlockPos pos) {
        for (Direction direction : PLACE_ORDER) {
            BlockState state = world.getBlockState(pos.offset(direction));
            if (!state.isAir() && !state.isReplaceable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a face the player can actually see to place against, which is what keeps the build
     * looking like a player did it instead of reaching through walls.
     *
     * <p>The block must be within interaction range, and the ray from the player's eyes to the face
     * centre has to arrive at that block before anything else. The nearest visible face wins, since
     * that is the one a player would naturally click.
     */
    private static BlockHitResult findVisibleFace(ClientWorld world, ClientPlayerEntity player, BlockPos pos) {
        Vec3d eyes = player.getEyePos();
        double range = Math.max(3.0, player.getBlockInteractionRange());
        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : PLACE_ORDER) {
            BlockPos support = pos.offset(direction);
            BlockState state = world.getBlockState(support);
            if (state.isAir() || state.isReplaceable()) {
                continue;
            }
            Direction face = direction.getOpposite();
            Vec3d point = new Vec3d(
                    support.getX() + 0.5 + face.getOffsetX() * 0.5,
                    support.getY() + 0.5 + face.getOffsetY() * 0.5,
                    support.getZ() + 0.5 + face.getOffsetZ() * 0.5);
            double distance = eyes.squaredDistanceTo(point);
            if (distance > range * range) {
                continue;
            }
            if (!visible(world, player, eyes, point, support, face)) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new BlockHitResult(point, face, support, false);
            }
        }
        return best;
    }

    /**
     * The nearest face within interaction range, whether or not the player can see it. Used only
     * when no visible face exists at all, so a block enclosed by the structure can still be built.
     */
    private static BlockHitResult findAnyFace(ClientWorld world, ClientPlayerEntity player, BlockPos pos) {
        Vec3d eyes = player.getEyePos();
        double range = Math.max(3.0, player.getBlockInteractionRange());
        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : PLACE_ORDER) {
            BlockPos support = pos.offset(direction);
            BlockState state = world.getBlockState(support);
            if (state.isAir() || state.isReplaceable()) {
                continue;
            }
            Direction face = direction.getOpposite();
            Vec3d point = new Vec3d(
                    support.getX() + 0.5 + face.getOffsetX() * 0.5,
                    support.getY() + 0.5 + face.getOffsetY() * 0.5,
                    support.getZ() + 0.5 + face.getOffsetZ() * 0.5);
            double distance = eyes.squaredDistanceTo(point);
            if (distance > range * range) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new BlockHitResult(point, face, support, false);
            }
        }
        return best;
    }

    /** True when the player can see {@code pos} itself, used before breaking a block. */
    private static boolean canSeeBlock(ClientWorld world, ClientPlayerEntity player, BlockPos pos) {
        Vec3d eyes = player.getEyePos();
        return visible(world, player, eyes, Vec3d.ofCenter(pos), pos, null);
    }

    /**
     * Casts a ray from the eyes to a target point and checks that it lands on the expected block.
     * The ray is aimed a hair inside the block so a face centre never grazes past it.
     */
    private static boolean visible(ClientWorld world, ClientPlayerEntity player, Vec3d eyes, Vec3d point,
                                   BlockPos expected, Direction face) {
        Vec3d end = face == null ? point : point.subtract(
                face.getOffsetX() * 0.05, face.getOffsetY() * 0.05, face.getOffsetZ() * 0.05);
        BlockHitResult result = world.raycast(new RaycastContext(eyes, end,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        return result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(expected);
    }

    /**
     * Puts the item for {@code state} in the player's hand.
     *
     * <p>An item already in the hotbar is simply selected. Anything else is swapped into the hotbar
     * through the player's own inventory screen handler, which is what makes the swap visible to the
     * server.
     *
     * @return false when the player has no such block
     */
    private static boolean selectItem(ClientPlayerEntity player, BlockState state) {
        if (state == null) {
            return false;
        }
        Item wanted = placeableItem(state);
        if (wanted == Items.AIR) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < PlayerInventory.HOTBAR_SIZE; slot++) {
            if (isStack(inventory.getStack(slot), wanted, state)) {
                selectHotbarSlot(player, slot);
                return true;
            }
        }
        for (int slot = PlayerInventory.HOTBAR_SIZE; slot < PlayerInventory.MAIN_SIZE; slot++) {
            if (!isStack(inventory.getStack(slot), wanted, state)) {
                continue;
            }
            moveToHotbar(player, slot);
            return true;
        }
        // The off hand is where the last of something usually is. A stack there cannot be clicked
        // from, so it is swapped into the hand instead — and only when the pockets are really empty of
        // it, so a build never disturbs what the player has deliberately put aside.
        if (isStack(player.getOffHandStack(), wanted, state)) {
            moveFromOffHand(player);
            return true;
        }
        return false;
    }

    /** Moves an inventory slot into the selected hotbar slot, through the player's own screen handler. */
    private static void moveToHotbar(ClientPlayerEntity player, int slot) {
        PlayerInventory inventory = player.getInventory();
        if (slot < PlayerInventory.HOTBAR_SIZE) {
            selectHotbarSlot(player, slot);
            return;
        }
        int hotbar = inventory.getSelectedSlot();
        int screenSlot = slot >= PlayerInventory.HOTBAR_SIZE ? slot : slot + 36;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager != null) {
            client.interactionManager.clickSlot(player.playerScreenHandler.syncId, screenSlot, hotbar,
                    SlotActionType.SWAP, player);
        }
        selectHotbarSlot(player, hotbar);
    }

    /**
     * Moves the off hand's stack into the selected hotbar slot, through the player's own screen handler.
     *
     * <p>The off hand is where the last stack of something ends up more often than not: a player who
     * built the first half of a schematic holds the block they are placing in the off hand so that the
     * main hand keeps whatever they were doing. The build has no other way to reach it — a stack in the
     * off hand cannot be placed from, only swapped from — and slot 45 of the player's own screen is the
     * game's own name for it.
     */
    private static void moveFromOffHand(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager == null) {
            return;
        }
        int hotbar = player.getInventory().getSelectedSlot();
        client.interactionManager.clickSlot(player.playerScreenHandler.syncId,
                PlayerScreenHandler.OFFHAND_ID, hotbar, SlotActionType.SWAP, player);
        selectHotbarSlot(player, hotbar);
    }

    private static void selectHotbarSlot(ClientPlayerEntity player, int slot) {
        PlayerInventory inventory = player.getInventory();
        if (inventory.getSelectedSlot() == slot) {
            return;
        }
        inventory.setSelectedSlot(slot);
        player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private static boolean isStack(ItemStack stack, Item wanted, BlockState state) {
        if (stack.isEmpty() || !stack.isOf(wanted)) {
            return false;
        }
        // Guard against an item that shares the id but is not the block we want, with a second chance
        // for the blocks that have no item of their own: the sign item puts a standing sign on the
        // ground and a wall sign against a wall, so the block behind the right item is not the block
        // the schematic asks for, and refusing on that would refuse the very item the build needs.
        if (!(wanted instanceof BlockItem blockItem)) {
            return true;
        }
        return blockItem.getBlock() == state.getBlock()
                || Item.BLOCK_ITEMS.get(state.getBlock()) == wanted;
    }

    /**
     * The item a click with it puts this block down with, or air when the game has none for it.
     *
     * <p>Most blocks are placed by the item of the same name, so the block's own item is the item. A
     * few are not: a wall torch, a wall sign, a wall banner and a hanging wall sign are shapes the
     * matching item takes when it is clicked against a wall rather than things with items of their
     * own, and a potted flower is a flower clicked into a pot. For all of those the block's own item is
     * air, and a build that only ever asked the block for its item called every such cell a block it
     * had none of — with the player standing there holding a stack of exactly the right thing, and the
     * torch it wanted already built into a wall somewhere else in the same schematic.
     *
     * <p>So the mounted name is unwound a step at a time and the first name the item registry knows is
     * the item. Nothing here is guessed by position: the names are what the game itself calls these
     * pairs, and the lookup is a real registry lookup that answers air if the pair does not exist.
     */
    private static Item placeableItem(BlockState state) {
        // The game's own answer to "which item puts this block down", which is the one map that has the
        // mounted shapes in it: a wall sign is filed under the sign item, a wall torch under the torch.
        Item known = Item.BLOCK_ITEMS.get(state.getBlock());
        if (known != null) {
            return known;
        }
        Item direct = state.getBlock().asItem();
        if (direct != Items.AIR) {
            return direct;
        }
        Block unmounted = unmounted(state.getBlock());
        return unmounted == Blocks.AIR ? Items.AIR : unmounted.asItem();
    }

    /**
     * The plain block a mounted shape is mounted from: a wall sign's sign, a wall torch's torch.
     *
     * <p>Only ever asked of a block with no item of its own, so a name that leads nowhere is just a
     * name that leads nowhere — the caller falls back to the item the block would have had.
     */
    private static Block unmounted(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        for (String name : unmountedNames(id.getPath())) {
            // A name that leads back to the mounted block is not an answer at all — the block every
            // mounted shape is filed under its own name first, and that one has no item either.
            if (name.equals(id.getPath())) {
                continue;
            }
            Block found = Registries.BLOCK.get(Identifier.of(id.getNamespace(), name));
            if (found != Blocks.AIR) {
                return found;
            }
        }
        return Blocks.AIR;
    }

    /** The names a mounted block's plain form goes by, most likely first. */
    private static List<String> unmountedNames(String path) {
        List<String> names = new ArrayList<>(3);
        names.add(path.replace("_wall_", "_"));
        if (path.startsWith("wall_")) {
            names.add(path.substring("wall_".length()));
        }
        if (path.startsWith("potted_")) {
            names.add(path.substring("potted_".length()));
        }
        return names;
    }

    /**
     * True when the player is carrying the item for {@code state}, anywhere in the inventory.
     *
     * <p>Asked before a cell is handed back for another go, so a build is not sent back across the
     * site for a block it has none of. An item that was never in the inventory, or a stack that has
     * run out, is not something a second approach can fix.
     */
    private static boolean hasItem(ClientPlayerEntity player, BlockState state) {
        Item wanted = placeableItem(state);
        if (wanted == Items.AIR) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            if (isStack(inventory.getStack(slot), wanted, state)) {
                return true;
            }
        }
        return isStack(player.getOffHandStack(), wanted, state);
    }

    // ------------------------------------------------------------------
    // Breaking
    // ------------------------------------------------------------------

    private void breakObstruction(MinecraftClient client, ClientPlayerEntity player, BlockPos pos, int index) {
        ClientPlayerInteractionManager interactions = client.interactionManager;
        ClientWorld world = client.world;
        if (interactions == null || world == null) {
            return;
        }
        lookAt(player, Vec3d.ofCenter(pos));
        Direction side = preferredBreakSide(player, pos);
        if (this.breakTicks == 0) {
            interactions.attackBlock(pos, side);
        } else {
            interactions.updateBlockBreakingProgress(pos, side);
        }
        this.breakTicks++;

        // Cleared means the cell is free for the build. Water is the usual surprise: breaking a
        // waterlogged plant leaves its fluid behind, and only taking air would call that a failed
        // break and skip the block for good.
        BlockState after = world.getBlockState(pos);
        if (after.isAir() || after.isReplaceable()) {
            this.broken++;
            this.note = "Broke the obstruction at " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            this.breakTicks = 0;
            return;
        }
        if (this.breakTicks >= MAX_BREAK_TICKS) {
            this.note = "Gave up breaking " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            interactions.cancelBlockBreaking();
            this.breakTicks = 0;
            this.rejected++;
            resolve(index);
        }
    }

    private static Direction preferredBreakSide(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eyes = player.getEyePos();
        double dx = eyes.x - (pos.getX() + 0.5);
        double dy = eyes.y - (pos.getY() + 0.5);
        double dz = eyes.z - (pos.getZ() + 0.5);
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }
        if (ax >= az) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ------------------------------------------------------------------
    // Stepping a block's state
    // ------------------------------------------------------------------

    /**
     * Sets about clicking a block of the build until its state matches the schematic.
     *
     * <p>Some of what a block holds is decided by clicking the block itself and by nothing else: a
     * repeater's delay steps up one per click, a comparator flips between compare and subtract, a
     * lever or a door toggles, a note block climbs a note at a time. The click that placed the block
     * chooses the first of those values whatever the schematic stores, so the block arrives with the
     * wrong state unless the click is repeated — which is exactly what a player does, standing in
     * front of a repeater and clicking it round to the delay they need.
     */
    private void beginStateClick(BlockPos pos, BlockState want) {
        this.statePos = pos;
        this.stateWant = want;
        this.stateSeen = null;
        this.stateClicks = 0;
        this.stateWait = 0;
        this.stateTicks = 0;
        this.stateStands = 0;
        this.note = "Stepping " + blockName(want) + " at " + at(pos) + " to the state the schematic wants";
    }

    /** Forgets any block whose state was being stepped. */
    private void clearState() {
        this.statePos = null;
        this.stateWant = null;
        this.stateSeen = null;
        this.stateClicks = 0;
        this.stateWait = 0;
        this.stateTicks = 0;
        this.stateStands = 0;
    }

    /**
     * Looks at one placement the build made, and repairs it when the game turned it.
     *
     * <p>A click reports that something was placed, not what was placed: the game works the state out
     * from a copy of the world and of the player's look that is a tick or two behind the client's, and
     * where the two disagree a stair or an observer or a hopper comes out facing another way entirely,
     * with no click refused and nothing to notice at the time. So every placement the build expected to
     * be exact is put back on the queue and looked at once the game has had its say. One is checked per
     * tick, so the build is not held up by the checking, and a cell that came out wrong is handed back
     * to the plan: the build mines it out and places it again from a different click.
     */
    private boolean verifyPlacements(ClientWorld world) {
        Placed placed = this.verifyQueue.poll();
        if (placed == null || placed.want() == null || !this.done[placed.index()]) {
            return false;
        }
        BlockState got = world.getBlockState(placed.pos());
        if (got.equals(placed.want())) {
            return false;
        }
        if (placed.look() == 0) {
            // A first look that disagrees is not acted on, whatever it says. The client's copy of the
            // world is a prediction of the server's for a tick or two after a click — a block placed,
            // clicked into shape and looked at again in quick succession can read as air in between and
            // come back a moment later — and acting on that first look is what sent the builder back to
            // blocks that were already standing, which is the thrash of "it is not in the build" lines
            // over a build that was in fact complete. A second look a tick later tells the two cases
            // apart: a real hole is still a hole, and a prediction that has landed is not.
            this.verifyQueue.add(new Placed(placed.pos(), placed.index(), placed.want(), 1));
            return false;
        }
        boolean missing = got.getBlock() != placed.want().getBlock();
        if (!missing && !placementDiffers(got, placed.want())) {
            if (clickCycles(got, placed.want()) != null) {
                // The right block in the right cell, with only a clickable property wrong: a repeater
                // that arrived at delay 1, a note block at note 0, a lever left thrown. Nothing has to
                // come out and nothing has to be carried — the block is clicked into shape where it
                // stands. This is where that step is started from, because by now the world really
                // holds the block the click placed; the check made at the placement itself reads the
                // world on the click's own tick, which is before the game has answered.
                beginStateClick(placed.pos(), placed.want());
                return true;
            }
            // Otherwise the right block, holding something no click has a say in — a lever's power, a
            // note block's instrument, a crafter's crafting flag. Nothing here to put right.
            return false;
        }
        int retries = this.rotationRetries.merge(placed.index(), 1, Integer::sum);
        if (retries > MAX_ROTATION_RETRIES) {
            this.note = missing
                    ? blockName(placed.want()) + " at " + at(placed.pos()) + " is not there after "
                            + (MAX_ROTATION_RETRIES + 1) + " tries"
                    : blockName(placed.want()) + " at " + at(placed.pos()) + " stays " + got
                            + " after " + (MAX_ROTATION_RETRIES + 1) + " tries";
            return false;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && missing && !hasItem(player, placed.want())) {
            this.note = "Nothing left to put " + blockName(placed.want()) + " at " + at(placed.pos())
                    + " back with";
            return false;
        }
        this.rotationRepairs++;
        this.done[placed.index()] = false;
        this.cursor = Math.min(this.cursor, placed.index());
        this.aim = null;
        this.aimTicks = 0;
        this.workIndex = -1;
        this.note = missing
                ? "Went back for " + blockName(placed.want()) + " at " + at(placed.pos())
                        + " — it is not in the build"
                : "Placed " + blockName(placed.want()) + " the wrong way round at " + at(placed.pos())
                        + " — taking it out and going again";
        return true;
    }

    /**
     * True when the block that was placed holds a different state from the schematic on something the
     * click decided — a facing, an axis, a half, a shape. Properties the world decides, and ones a
     * second click can step, are not this: those are left to the world and to {@link #stepState}.
     */
    private static boolean placementDiffers(BlockState got, BlockState want) {
        for (Property<?> property : want.getProperties()) {
            if (!got.get(property).equals(want.get(property))
                    && PLACEMENT_PROPERTIES.contains(property.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * One tick of a state fix: click the block again when a click can bring it closer to the
     * schematic's state, wait when the last click has not arrived yet, and stop as soon as there is
     * nothing left to do or nothing a click can do.
     */
    private void stepState(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        if (client.interactionManager == null) {
            clearState();
            return;
        }
        BlockPos pos = this.statePos;
        BlockState want = this.stateWant;
        BlockState got = world.getBlockState(pos);

        if (got.equals(want)) {
            this.note = "Stepped " + blockName(want) + " at " + at(pos)
                    + " to the state the schematic wants";
            clearState();
            return;
        }
        if (++this.stateTicks > MAX_STATE_TICKS) {
            if (this.stateStands > 0) {
                // The crouch key was held, and a crouched click is a click the block's own use action
                // takes instead: the block cannot be stepped while it is down. That is the player's
                // finger, not the build, so it is counted apart from the blocks no click can set — the
                // player can fix this one by letting go, and a report that lumped it in with the rest
                // would read as a build that failed at something it never had a chance to do.
                this.crouchBlocked++;
                this.note = "Left " + blockName(want) + " at " + at(pos)
                        + " — the crouch key stayed down, so it could not be clicked";
            } else {
                this.note = "Left " + blockName(want) + " at " + at(pos) + " as " + got + " after "
                        + this.stateClicks + " click(s)";
            }
            clearState();
            return;
        }
        if (got.getBlock() != want.getBlock()) {
            // Not even the right block: the placement is still on its way, so give it a moment before
            // deciding there is nothing here to click at all.
            if (++this.stateWait > MAX_STATE_WAIT_TICKS) {
                this.note = "Nothing to step at " + at(pos);
                clearState();
            }
            return;
        }
        Property<?> property = clickCycles(got, want);
        if (property == null) {
            this.note = "No click can set " + blockName(want) + " at " + at(pos) + " from " + got;
            clearState();
            return;
        }
        if (this.stateSeen != null && got.equals(this.stateSeen)) {
            // The last click has not reached the world yet, and clicking again before it arrives would
            // step the block straight past the value that was wanted.
            if (++this.stateWait > MAX_STATE_WAIT_TICKS) {
                this.note = "Gave up stepping " + blockName(want) + " at " + at(pos);
                clearState();
            }
            return;
        }
        int limit = Math.min(MAX_STATE_CLICKS, property.getValues().size());
        if (this.stateClicks >= limit) {
            this.note = "Ran out of clicks on " + blockName(want) + " at " + at(pos) + " — wants "
                    + property.getName() + "=" + want.get(property) + ", has " + got.get(property);
            clearState();
            return;
        }
        if (!inReach(player, pos)) {
            this.note = "Cannot reach " + blockName(want) + " at " + at(pos) + " to step it";
            clearState();
            return;
        }
        if (client.currentScreen != null) {
            this.note = "Close the menu so the builder can step " + blockName(want);
            return;
        }
        // Standing, always, and a tick before the click. A crouch tells the game to skip a block's own
        // use action, and here that action is the very click the state is waiting for — and the
        // release only reaches the server on the next movement packet, so a click sent in the same
        // tick as the stand-up is still a click made crouched, and does nothing at all.
        if (this.crouchHeld || client.options.sneakKey.isPressed()) {
            standUp(client);
            this.stateWait = 0;
            if (++this.stateStands > MAX_STAND_TRIES) {
                // The key came back down again, tick after tick, and the engine did not put it there:
                // its own crouch is let go above — for a toggled key, tapped up — and stays let go. A
                // sneak key held at the keyboard is re-pressed by the game on every repeat, so the
                // crouch comes back within a tick of being released and a click sent now would be a
                // crouched one — the block's use action skipped, which is the very click being waited
                // for. Nothing the engine can do will make that click standing, so the note says what
                // is in the way instead of the build standing still for six seconds with nothing on
                // the screen to say why.
                this.note = "Let go of crouch so the builder can click " + blockName(want)
                        + " at " + at(pos);
                return;
            }
            this.note = "Standing up to click " + blockName(want) + " at " + at(pos);
            return;
        }
        this.stateStands = 0;
        lookAt(player, Vec3d.ofCenter(pos));
        this.stateSeen = got;
        this.stateWait = 0;
        this.stateClicks++;
        this.menuCulprit = want.getBlock();
        this.menuWatchTicks = MENU_WATCH_TICKS;
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, faceHit(player, pos));
        this.phase = Phase.PLACING;
        this.note = "Clicking " + blockName(want) + " at " + at(pos) + " for " + property.getName()
                + " (click " + this.stateClicks + " of " + limit + ")";
    }

    /**
     * The property a click can step towards the schematic's state, or null when no click can help.
     *
     * <p>Only a mismatch on something a click really decides counts. A repeater that is powered, a
     * repeater that is locked, a note block's instrument: all of those are set by the world around the
     * block, so clicking for them would be clicking for nothing at all.
     */
    private static Property<?> clickCycles(BlockState got, BlockState want) {
        if (want == null || got.getBlock() != want.getBlock()) {
            return null;
        }
        for (Property<?> property : want.getProperties()) {
            if (!got.get(property).equals(want.get(property))
                    && clickSteps(got.getBlock(), property.getName())) {
                return property;
            }
        }
        return null;
    }

    /**
     * Whether right-clicking this block steps this property.
     *
     * <p>Kept to blocks whose click never opens anything: the click is aimed at a block the build has
     * already put there, with a building block still in hand and no crouch on it to keep a menu from
     * opening, so a block with a screen behind it would open the screen and step nothing. Every block
     * on this list was checked against the game's own bytecode for it, not from memory: clicking a
     * crafter looks like a toggle and is in fact its menu.
     */
    private static boolean clickSteps(Block block, String property) {
        return switch (property) {
            case "delay" -> block instanceof RepeaterBlock;
            case "mode" -> block instanceof ComparatorBlock;
            case "note" -> block instanceof NoteBlock;
            case "inverted" -> block instanceof DaylightDetectorBlock;
            // The crafter is deliberately absent, though a click is what toggles it. Clicking one
            // opens its screen — the toggle is a slot inside that screen, not the block — so stepping a
            // crafter means opening a menu the build has no business opening. Its crafting state is
            // reported as one no click can set instead.
            case "powered" -> block instanceof LeverBlock;
            case "open" -> opensByClick(block);
            default -> false;
        };
    }

    /**
     * Whether a bare click opens or closes this block.
     *
     * <p>Iron counts as not openable, and that is the whole reason this is a method rather than a list
     * of classes: an iron door or trapdoor takes a redstone signal, a hand does nothing to it, and
     * stepping one would be a run of clicks spent on a door that never moves. Every other door in the
     * game — wood, copper, the lot — opens by hand, and the game's own block set type says which is
     * which instead of a list here that the next update would leave behind.
     */
    private static boolean opensByClick(Block block) {
        if (block instanceof FenceGateBlock) {
            return true;
        }
        if (block instanceof DoorBlock door) {
            return door.getBlockSetType().canOpenByHand();
        }
        // A trapdoor keeps its block set type to itself, so iron is named outright. It is the only
        // trapdoor in the game that takes a signal instead of a hand.
        return block instanceof TrapdoorBlock && block != Blocks.IRON_TRAPDOOR;
    }

    /** The click to make on a block that is already in place: the face nearest the player's eyes. */
    private static BlockHitResult faceHit(ClientPlayerEntity player, BlockPos pos) {
        Direction face = preferredBreakSide(player, pos);
        Vec3d point = new Vec3d(
                pos.getX() + 0.5 + face.getOffsetX() * 0.5,
                pos.getY() + 0.5 + face.getOffsetY() * 0.5,
                pos.getZ() + 0.5 + face.getOffsetZ() * 0.5);
        return new BlockHitResult(point, face, pos, false);
    }

    /** A block's display name, for the status line. */
    private static String blockName(BlockState state) {
        if (state == null) {
            return "a block";
        }
        String name = state.getBlock().getName().getString();
        return name.isEmpty() ? "a block" : name;
    }

    /** A position written the way the other notes write one. */
    private static String at(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ------------------------------------------------------------------
    // Temporary scaffolding
    // ------------------------------------------------------------------

    /**
     * Starts a temporary climb toward a block that cannot be walked to.
     *
     * <p>Only worth doing when the goal is above the player: climbing down is what walking is for.
     * It also needs a spare block in the inventory that the schematic itself does not need, so the
     * build's own materials are never spent on a tower.
     *
     * @return true when a climb was started
     */
    private boolean beginClimb(ClientPlayerEntity player, GridPos target) {
        if (this.climbBlocked || this.scaffoldBlocks.size() >= MAX_SCAFFOLD) {
            return false;
        }
        if (target.y() <= player.getBlockY() + 1) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        // A tower needs room to hop: with a ceiling right above, the player cannot leave the ground.
        if (client.world == null
                || !roomToHop(client.world, new BlockPos(player.getBlockX(), player.getBlockY(),
                        player.getBlockZ()))) {
            this.climbBlocked = true;
            this.note = "No room above to build a tower here";
            return false;
        }
        if (!selectScaffoldItem(player)) {
            this.climbBlocked = true;
            this.note = "No spare block to build a tower with";
            return false;
        }
        this.scrambling = true;
        // The column is pinned later, on the first tick the player is really standing on the
        // ground. Reading it here instead pinned the cell they happen to be falling through.
        this.scaffoldAnchorY = UNPINNED;
        this.scaffoldTargetY = target.y() - 1;
        this.scaffoldTicks = 0;
        this.scaffoldIdleTicks = 0;
        this.breakTicks = 0;
        this.note = "Tower from " + this.scaffoldMaterial + " to reach " + target;
        return true;
    }

    /**
     * True when the player could really hop from this cell: is there sky enough above it?
     *
     * <p>A jump lifts the feet about a block and a quarter and the player stands nearly two blocks
     * tall, so a hop needs the three cells above the feet — the one jumped out of, the one the head
     * passes through and the one the head reaches. With a ceiling a cell lower than that the jump is
     * capped at half its height, and half a hop is a hop that rises, falls back and places nothing:
     * the tower's whole trick is to put the block down at the top of the hop, and at half height the
     * player has not cleared the cell they jumped out of.
     *
     * <p>Blocks with no collision box count as room, because a jump really does pass through them: a
     * torch or a rail over the player's head stops nothing.
     */
    private static boolean roomToHop(ClientWorld world, BlockPos feet) {
        for (int up = 1; up <= 3; up++) {
            BlockPos cell = feet.up(up);
            BlockState state = world.getBlockState(cell);
            if (!state.isAir() && !state.isReplaceable()
                    && !state.getCollisionShape(world, cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Comes at a block differently after a full window of ticks spent on it without finishing it.
     *
     * <p>Everything the builder decided for this block is dropped: the click it was lining up, the
     * crouch it had settled, the route it was walking, the vantage points it had already tried and
     * the tower or bridge it had started. The next tick then picks a fresh route and a fresh angle at
     * the block, which is what gets it moving again when the first approach was the problem.
     *
     * <p>A block that survives two of those windows is left for later — held back the way a block
     * waiting for the right neighbour is — and one that survives a third is written off, so no block
     * can keep the build from finishing.
     *
     * @return true when the caller should stop working on this block for now
     */
    private boolean restart(int index, BuildPlan.Target target) {
        this.workTicks = 0;
        this.aim = null;
        this.aimTicks = 0;
        // The approach is being thrown away, so the crouch it was holding goes with it: the builder is
        // about to walk to another side of the block, and a crouch held through that is a crawl.
        standUp(MinecraftClient.getInstance());
        this.route = null;
        this.routeIndex = 0;
        this.routeGoal = null;
        this.travelGoal = null;
        this.triedGoals.clear();
        this.vantageTicks = 0;
        this.attempts = 0;
        this.scrambling = false;
        this.climbBlocked = false;
        this.bridging = false;
        this.bridgeBlocked = false;
        this.bridgeTarget = null;
        this.scaffoldAnchorY = UNPINNED;

        // Counted per block for the whole build, not per attempt: a block that has already been
        // restarted twice and comes back around does not get two more windows.
        int attempts = this.restarts.merge(index, 1, Integer::sum);
        if (attempts < MAX_RESTARTS) {
            this.note = "Trying another angle on " + shortName(target.blockId()) + " at "
                    + target.world();
            return true;
        }
        if (attempts == MAX_RESTARTS) {
            // Left for later rather than skipped: a block with nothing to click often only needs a
            // neighbour that is built a little later in the plan.
            this.aimWaiting.add(index);
            this.workIndex = -1;
            this.note = "No luck with " + shortName(target.blockId()) + " at " + target.world()
                    + ", leaving it for later";
            return true;
        }
        this.unreachable++;
        this.note = "Gave up on " + shortName(target.blockId()) + " at " + target.world()
                + " after " + (MAX_RESTARTS + 1) + " tries";
        resolve(index);
        return true;
    }

    /**
     * Climbs one level at a time: hop, then fill the cell just left behind so the player lands a
     * block higher. Each filled cell is banked so {@link #tickTearDown} can take it back down.
     */
    private void tickClimb(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        if (++this.scaffoldTicks > MAX_SCAFFOLD_TICKS || client.interactionManager == null) {
            this.scrambling = false;
            // Do not try this block again: something about the spot stops the climb working.
            this.climbBlocked = true;
            this.note = "Gave up climbing after " + this.scaffoldTicks + " ticks";
            return;
        }
        // Walk keys off. The crouch goes on only for the hop itself: it keeps the click at the top
        // of it from opening the menu of whatever the column is standing on, and by then the key
        // press is on the server's side instead of being sent in the same tick as the click.
        // Sneaking does not stop a jump — only the auto-jump — so the hop still works.
        stopWalking();

        // Pin the column on the first tick the player is really standing. Starting the climb while
        // they are falling pins it to a cell they are only passing through, which puts the block in
        // the wrong place — or inside their own body, where the game refuses to place it at all.
        if (this.scaffoldAnchorY == UNPINNED) {
            if (!player.isOnGround()) {
                this.note = "Tower: waiting to land";
                return;
            }
            BlockPos spot = new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
            BlockState spotState = world.getBlockState(spot);
            if (!spotState.isAir() && !spotState.isReplaceable()) {
                this.scrambling = false;
                this.climbBlocked = true;
                this.note = "Tower: standing in something solid, cannot build up";
                return;
            }
            this.scaffoldAnchorX = spot.getX();
            this.scaffoldAnchorY = spot.getY();
            this.scaffoldAnchorZ = spot.getZ();
            this.scaffoldIdleTicks = 0;
            this.note = "Tower: building from " + spot.getY() + " toward " + this.scaffoldTargetY;
            return;
        }

        BlockPos cell = new BlockPos(this.scaffoldAnchorX, this.scaffoldAnchorY, this.scaffoldAnchorZ);
        boolean filled = !world.getBlockState(cell).isAir();
        boolean standing = player.isOnGround();

        // Landed on the block placed underfoot: the working level moves up with the player. The
        // column is re-pinned to the cell they actually came down in, so a hop that slid sideways
        // corrects itself instead of stacking the next block into empty air beside the tower.
        if (standing && filled && player.getBlockY() > this.scaffoldAnchorY) {
            this.scaffoldAnchorX = player.getBlockX();
            this.scaffoldAnchorY = player.getBlockY();
            this.scaffoldAnchorZ = player.getBlockZ();
            this.scaffoldIdleTicks = 0;
            client.options.jumpKey.setPressed(false);
            if (this.scaffoldBlocks.size() >= MAX_SCAFFOLD) {
                this.scrambling = false;
                this.climbBlocked = true;
                this.note = "Reached the tower height limit";
                return;
            }
        }

        // Finish only on solid ground. Ending the climb while the player is still in the air let a
        // plain jump report "reached height" without a single block placed underfoot, so the target
        // stayed unreachable and the same tower was started over and over, forever.
        if (standing && player.getBlockY() >= this.scaffoldTargetY) {
            this.scrambling = false;
            this.note = "Tower reached height " + player.getBlockY();
            return;
        }

        // A tower that neither places a block nor changes level is going nowhere: stop rather than
        // spend the whole tick budget standing still.
        if (++this.scaffoldIdleTicks > MAX_SCAFFOLD_IDLE_TICKS) {
            this.scrambling = false;
            this.climbBlocked = true;
            this.note = "Tower: no progress for " + this.scaffoldIdleTicks + " ticks, giving up";
            return;
        }

        if (standing) {
            if (player.getBlockY() != this.scaffoldAnchorY) {
                // Standing somewhere else entirely: re-pin rather than fill the wrong cell.
                this.scaffoldAnchorY = UNPINNED;
                return;
            }
            // Room for a hop is checked here as well as when the tower starts, because a tower is
            // climbed one block at a time and the ceiling arrives part way up. A tower that rises into
            // a block above hops for ever without gaining a block: the jump is capped at half its
            // height, the placement clearance is never reached, and every hop looks exactly like the
            // last one — a hundred identical "hopping" lines and six seconds later a note that says
            // only that no progress was made. Ending the climb the moment the ceiling is reached
            // leaves the caller free to come at the block some other way.
            if (!roomToHop(world, cell)) {
                this.scrambling = false;
                this.climbBlocked = true;
                this.note = "Tower: blocked from above at " + column(cell) + ", coming at it another way";
                return;
            }
            // Line up on the middle of the block first, so the hop goes straight up.
            double centreX = cell.getX() + 0.5;
            double centreZ = cell.getZ() + 0.5;
            if (Math.abs(player.getX() - centreX) > CENTRE_TOLERANCE
                    || Math.abs(player.getZ() - centreZ) > CENTRE_TOLERANCE) {
                lookAt(player, new Vec3d(centreX, player.getY(), centreZ));
                standUp(client);
                client.options.forwardKey.setPressed(true);
                this.note = "Tower: stepping onto the middle of " + column(cell);
                return;
            }
            // Standing over the middle: look straight down, crouch, and hop. The block goes into the
            // cell left behind on the way up, and the crouch is held from here so the click at the
            // top of the hop is already covered.
            player.setPitch(90.0F);
            pressCrouch(client);
            client.options.jumpKey.setPressed(true);
            this.note = "Tower: hopping from " + player.getBlockY();
            return;
        }

        // Airborne, so the hop is under way. The block goes into the cell the player jumped out of,
        // but the game only accepts that once their body has cleared it: while any part of the
        // player still overlaps the cell, useOnBlock returns FAIL and nothing is placed at all.
        // That rejection, one or two ticks into every hop, is why the tower looked like it never
        // clicked anything.
        client.options.jumpKey.setPressed(false);
        if (filled) {
            return;
        }
        double clearHeight = cell.getY() + 1.0 + PLACEMENT_CLEARANCE;
        if (player.getY() < clearHeight) {
            this.note = "Tower: rising " + round(player.getY()) + " to " + round(clearHeight);
            return;
        }
        // Click every tick from here to the top of the hop, not just at the apex. A player holds
        // right mouse down while jumping and the placement lands on whichever tick it is legal,
        // which is the whole trick.
        BlockPos support = cell.down();
        BlockState supportState = world.getBlockState(support);
        if (supportState.isAir() || supportState.isReplaceable()
                || supportState.getCollisionShape(world, support).isEmpty()) {
            this.note = "Tower: nothing solid below to build against";
            return;
        }
        if (!ensureScaffoldItem(player)) {
            this.scrambling = false;
            this.climbBlocked = true;
            this.note = "Ran out of spare blocks for the tower";
            return;
        }
        BlockHitResult hit = new BlockHitResult(
                new Vec3d(cell.getX() + 0.5, support.getY() + 1.0, cell.getZ() + 0.5),
                Direction.UP, support, false);
        // Straight down, so the crosshair really is on the block being clicked underneath, and
        // crouched, so the click cannot open anything above the column either.
        player.setPitch(90.0F);
        pressCrouch(client);
        ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            // Bank it straight away, so a tower abandoned mid-hop is still taken down again.
            this.scaffoldPlaced++;
            this.scaffoldBlocks.push(new Scaffold(
                    new GridPos(cell.getX(), cell.getY(), cell.getZ()), placedBlock(player, world, cell)));
            this.scaffoldIdleTicks = 0;
            this.note = "Tower: placed " + this.scaffoldMaterial + " at " + column(cell);
        } else {
            this.note = "Tower: click refused from " + round(player.getY()) + " onto " + column(cell);
        }
    }

    /**
     * The block a temporary placement put into a cell, read back from the world.
     *
     * <p>Taken from the world rather than from the held item, because the click may have been made
     * crouched against something odd, or the block may have been swapped into place by a neighbour
     * (a torch on a wall becomes a wall torch) — and whatever is really there is what has to be taken
     * back out later.
     */
    private static Block placedBlock(ClientPlayerEntity player, ClientWorld world, BlockPos cell) {
        BlockState state = world.getBlockState(cell);
        if (!state.isAir() && !state.isReplaceable()) {
            return state.getBlock();
        }
        ItemStack held = player.getMainHandStack();
        return held.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : Blocks.AIR;
    }

    /** Formats a tower cell for a status note. */
    private static String column(BlockPos cell) {
        return cell.getX() + "," + cell.getY() + "," + cell.getZ();
    }

    /**
     * Builds one block of a temporary column underneath a target that has nothing beside it to
     * click, stacking up from the solid ground below until the target has a neighbour to sit on.
     *
     * <p>Used when the gap under the target is too tall to bridge in one go; a single missing block
     * underneath is handled by {@link #buildPlaceholder}, which puts a spare block straight into the
     * cell below.
     *
     * <p>This is the case that used to be written off as "floating": a block in mid-air with air on
     * every side. Vanilla cannot place a block with no face to click, so the answer is not a better
     * click but more world — a column, placed one block per tick onto the top face of the block
     * below it. Each block is banked with the tower scaffolding, so the whole column is taken down
     * again when the build ends.
     *
     * <p>Every block of the column is within reach: the target itself is only considered here when
     * the player can already reach it, and the cells below are nearer still.
     *
     * @return true when a column block was placed or the target is now ready to place
     */
    private boolean buildSupportColumn(MinecraftClient client, ClientPlayerEntity player, ClientWorld world,
            BuildPlan.Target target) {
        if (client.interactionManager == null || this.scaffoldBlocks.size() >= MAX_SCAFFOLD) {
            return false;
        }
        BlockPos pos = toBlockPos(target.world());
        int columnLimit = Math.min(MAX_SUPPORT_COLUMN, MAX_SCAFFOLD - this.scaffoldBlocks.size());
        // Find the top of the solid stack under the target, within the column's height budget.
        int baseY = Integer.MIN_VALUE;
        for (int depth = 1; depth <= columnLimit; depth++) {
            BlockPos below = pos.down(depth);
            BlockState belowState = world.getBlockState(below);
            if (belowState.isAir() || belowState.isReplaceable()
                    || belowState.getCollisionShape(world, below).isEmpty()) {
                continue;
            }
            baseY = below.getY();
            break;
        }
        if (baseY == Integer.MIN_VALUE) {
            return false;
        }
        if (baseY + 1 == pos.getY()) {
            // The column already reaches the target, so it now has a face to sit on: let the
            // normal path place it on the next tick.
            return true;
        }
        BlockPos fill = new BlockPos(pos.getX(), baseY + 1, pos.getZ());
        BlockState fillState = world.getBlockState(fill);
        if (!fillState.isAir() && !fillState.isReplaceable()) {
            return false;
        }
        if (this.plan != null && this.plan.wanted(new GridPos(fill.getX(), fill.getY(), fill.getZ()))) {
            // The plan has its own block for this cell, and a column standing there would keep it out
            // of the build. The next block of the column belongs to the build instead, so leave the
            // column to wait until the plan has put it there.
            return false;
        }
        if (!ensureScaffoldItem(player)) {
            this.note = "No spare block to build a support column with";
            return false;
        }
        // Click the top face of the block below, which puts the new block exactly one level up.
        Vec3d top = new Vec3d(fill.getX() + 0.5, baseY + 1.0, fill.getZ() + 0.5);
        lookAt(player, top);
        if (holdCrouchFor(client, world, fill.down())) {
            // Crouch still on its way to the server: click on the next tick instead.
            return true;
        }
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                new BlockHitResult(top, Direction.UP, fill.down(), false));
        this.scaffoldBlocks.push(new Scaffold(
                new GridPos(fill.getX(), fill.getY(), fill.getZ()), placedBlock(player, world, fill)));
        this.scaffoldPlaced++;
        this.note = "Support column from " + this.scaffoldMaterial + " at "
                + fill.getX() + "," + fill.getY() + "," + fill.getZ();
        return true;
    }

    /**
     * Picks the cell to put a spare block in so that a block with nothing to click has something to
     * be placed against.
     *
     * <p>Ordered by how little the placeholder gets in the way. Underneath first, which is the
     * natural way up and leaves the target with a floor; then the four sides, the one facing the
     * player first, since that is the click they can actually see; then above, for a block hanging
     * under something already built.
     *
     * <p>A cell only qualifies if it is free and already has a face of its own to click, and never if
     * the builder is standing in it — a placeholder that lands inside the player is refused by the
     * game, and one that lands on their head is worse.
     *
     * @return the cell to fill, or null when no spare block can be got beside this one
     */
    private BlockPos supportCell(ClientWorld world, ClientPlayerEntity player, BuildPlan.Target target) {
        BlockPos pos = toBlockPos(target.world());
        BlockPos feet = new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        List<BlockPos> sides = new ArrayList<>(STEP_DIRECTIONS.length);
        for (Direction direction : STEP_DIRECTIONS) {
            sides.add(pos.offset(direction));
        }
        sides.sort((first, second) -> Double.compare(
                nearness(player, first), nearness(player, second)));
        List<BlockPos> candidates = new ArrayList<>(6);
        candidates.add(pos.down());
        candidates.addAll(sides);
        candidates.add(pos.up());
        for (BlockPos cell : candidates) {
            if (cell.equals(feet) || cell.equals(feet.up())) {
                continue;
            }
            BlockState state = world.getBlockState(cell);
            if (!state.isAir() && !state.isReplaceable()) {
                continue;
            }
            if (!hasClickableNeighbour(world, cell)) {
                // Nothing to click to put the placeholder there either, so move on to one that can
                // at least be built.
                continue;
            }
            if (this.plan != null
                    && this.plan.wanted(new GridPos(cell.getX(), cell.getY(), cell.getZ()))) {
                // The plan has a block of its own for this cell. Filling it with a placeholder would
                // keep that block out for good, so a build with a hole in it is the price of a
                // support put where the build wanted something.
                continue;
            }
            return cell;
        }
        return null;
    }

    /**
     * True when putting a spare block next to this target would actually help it.
     *
     * <p>Only blocks that stay where they are put can be helped this way. A rail, a line of redstone
     * or a torch needs the block underneath it to hold it up, and a temporary block is taken away
     * again at the end of the build — so supporting one of those only moves the trouble to the end,
     * when it pops off the moment the scaffolding comes down. Those are left as they were: a block
     * the schematic put in mid-air with nothing under it is the schematic's own business.
     */
    private static boolean canBeHelped(BuildPlan.Target target, ClientWorld world, BlockPos pos) {
        BlockState want = target.state();
        return want != null && want.canPlaceAt(world, pos);
    }

    /** How far a cell is from the player on the level, for ordering the cells a support can go in. */
    private static double nearness(ClientPlayerEntity player, BlockPos cell) {
        double dx = cell.getX() + 0.5 - player.getX();
        double dz = cell.getZ() + 0.5 - player.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Builds one block of support for a block that has nothing to click.
     *
     * <p>The answer to a block that hangs in mid-air with air on every side, which used to be
     * written off as floating and left out of the build. Vanilla refuses to place a block with no face
     * to click, and no amount of walking or looking finds one, so the builder makes one: a spare block
     * goes into the cell {@link #supportCell} picks, clicked exactly the way build materials are, and
     * the block the schematic wants is placed against it on the next tick.
     *
     * <p>Every placeholder is banked with the tower and the bridge, so it comes back out when the
     * build ends, and one that lands in a cell the plan wants later is simply replaced by the block
     * that belongs there.
     *
     * @return true when the next tick should try the target again
     */
    private boolean buildPlaceholder(MinecraftClient client, ClientPlayerEntity player, ClientWorld world,
            BuildPlan.Target target) {
        if (client.interactionManager == null || this.scaffoldBlocks.size() >= MAX_SCAFFOLD) {
            return false;
        }
        BlockPos cell = supportCell(world, player, target);
        if (cell == null) {
            return false;
        }
        BlockHitResult hit = findVisibleFace(world, player, cell);
        if (hit == null) {
            hit = findAnyFace(world, player, cell);
        }
        if (hit == null) {
            return false;
        }
        if (!ensureScaffoldItem(player)) {
            this.note = "No spare block to support " + shortName(target.blockId()) + " with";
            return false;
        }
        lookAt(player, hit.getPos());
        if (holdCrouchFor(client, world, hit.getBlockPos())) {
            // Crouch still on its way to the server, or aimed: click on the next tick instead.
            return true;
        }
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        BlockState filled = world.getBlockState(cell);
        if (filled.isAir() || filled.isReplaceable()) {
            this.note = "Support click refused at " + cell.getX() + "," + cell.getY()
                    + "," + cell.getZ();
            return true;
        }
        this.scaffoldBlocks.push(new Scaffold(
                new GridPos(cell.getX(), cell.getY(), cell.getZ()), placedBlock(player, world, cell)));
        this.scaffoldPlaced++;
        this.note = "Supported " + shortName(target.blockId()) + " with " + this.scaffoldMaterial
                + " at " + cell.getX() + "," + cell.getY() + "," + cell.getZ();
        return true;
    }

    /**
     * Lays one block of a horizontal bridge, so the player can walk out across a gap to a target
     * that is otherwise unreachable. Like the tower, the blocks are banked and taken down at the
     * end, and the build's own materials are avoided while a spare block exists.
     *
     * <p>The block is placed against the side of the one the player is standing on, which puts it
     * exactly one step ahead — the same click a player makes when bridging by hand. Only a genuine
     * gap qualifies: if the floor ahead already exists, walking is the problem, not bridging.
     *
     * @return true when a block was placed and the build should wait for the next tick
     */
    private boolean beginBridge(MinecraftClient client, ClientPlayerEntity player, GridPos target) {
        if (this.bridgeBlocked || this.bridging || client.world == null
                || client.interactionManager == null
                || this.scaffoldBlocks.size() >= MAX_SCAFFOLD) {
            return false;
        }
        GridPos foot = new GridPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        int dx = target.x() - foot.x();
        int dz = target.z() - foot.z();
        // A target clearly above or below is a climb, not a bridge.
        if (Math.abs(target.y() - foot.y()) > 1) {
            return false;
        }
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        if (alongX ? dx == 0 : dz == 0) {
            return false;
        }
        // Choose the direction once and hold it. Working it out again every tick from wherever the
        // player has got to is what made this wander: as they moved, the dominant axis flipped and
        // the walk reversed on itself instead of leaving the deck.
        this.bridgeDir = alongX
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        this.bridging = true;
        this.bridgeTarget = target;
        this.bridgeSteps = 0;
        this.bridgeTicks = 0;
        this.bridgeWalkTicks = 0;
        this.note = "Bridging " + this.bridgeDir + " toward " + target;
        return true;
    }

    /**
     * One tick of bridge building: line up on the middle of the block, then either step onto the
     * deck ahead or lay the next block of it.
     *
     * <p>Bridging this way is reliable where climbing is not, because nothing has to be jumped.
     * The block goes into the floor of the cell ahead — one level below the player's feet — clicked
     * against the side of the block they are standing on. The player never leaves the ground, never
     * has to time a jump, and can always see what they are clicking.
     *
     * <p>Every step is decided from scratch, which is what makes the walk safe: the moment the
     * floor ahead is missing, walking stops and the next block is laid instead, so the player can
     * never run out past the end of their own deck.
     */
    private void tickBridge(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        if (++this.bridgeTicks > MAX_BRIDGE_TICKS
                || this.bridgeSteps >= MAX_SCAFFOLD
                || this.scaffoldBlocks.size() >= MAX_SCAFFOLD) {
            this.bridging = false;
            this.bridgeBlocked = true;
            this.note = "Bridging: spent the budget after " + this.bridgeSteps + " step(s)";
            return;
        }
        // Walk keys off, but the crouch is left to {@link #holdCrouchFor}: it is the branch below
        // that presses it, for the block about to be clicked, and releasing it here every tick would
        // press it again every tick — a crouch that bobs down and up for the whole crossing.
        stopWalking();

        if (this.bridgeTarget != null && inReach(player, toBlockPos(this.bridgeTarget))) {
            this.bridging = false;
            this.note = "Bridged into reach of " + this.bridgeTarget;
            return;
        }

        BlockPos footPos = new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        BlockPos ahead = footPos.offset(this.bridgeDir);
        BlockState aheadState = world.getBlockState(ahead);
        if (!aheadState.isAir() && !aheadState.isReplaceable()) {
            this.bridging = false;
            this.bridgeBlocked = true;
            this.note = "Bridging: blocked by something solid at " + ahead;
            return;
        }
        BlockState deckState = world.getBlockState(ahead.down());
        boolean deckAhead = !deckState.isAir() && !deckState.isReplaceable();

        // Every block of the deck goes down from the middle of the block underfoot, so the player is
        // never standing on an edge — where a step can slide off the side of the deck, or the click
        // at the far side of the block lands out of reach. A step that starts in the middle of one
        // block ends in the middle of the next one, so lining up once keeps the whole crossing
        // straight instead of drifting further off with each block.
        //
        // The middle is waited for only while a block is about to be laid. While walking along deck
        // that is already down, only the drifting across the bridge is corrected: dragging the player
        // back to the middle of a block they are walking off undid the step they had just made, so
        // the two corrections cancelled each other and the crossing went nowhere.
        double centreX = footPos.getX() + 0.5;
        double centreZ = footPos.getZ() + 0.5;
        boolean acrossOff = this.bridgeDir.getOffsetX() != 0
                ? Math.abs(player.getZ() - centreZ) > CENTRE_TOLERANCE
                : Math.abs(player.getX() - centreX) > CENTRE_TOLERANCE;
        boolean alongOff = !deckAhead && (this.bridgeDir.getOffsetX() != 0
                ? Math.abs(player.getX() - centreX) > CENTRE_TOLERANCE
                : Math.abs(player.getZ() - centreZ) > CENTRE_TOLERANCE);
        if (acrossOff || alongOff) {
            // Aim at our own spot shifted sideways where only the drift needs correcting, so pressing
            // forward does not push the player back the way they came; at the middle of the block
            // itself when a block is about to be laid.
            Vec3d spot = alongOff
                    ? new Vec3d(centreX, player.getY(), centreZ)
                    : (this.bridgeDir.getOffsetX() != 0
                            ? new Vec3d(player.getX(), player.getY(), centreZ)
                            : new Vec3d(centreX, player.getY(), player.getZ()));
            lookAt(player, spot);
            client.options.forwardKey.setPressed(true);
            this.note = (alongOff ? "Bridging: centring on the middle of " : "Bridging: lining up across on ")
                    + player.getBlockX() + "," + player.getBlockZ()
                    + " at=" + exact(player);
            return;
        }

        if (deckAhead) {
            // The deck already reaches this far, so take one step onto it. Walking is counted
            // separately from blocks laid: charging a whole block of the budget per tick of walking
            // burned the block budget before the gap was even reached.
            if (++this.bridgeWalkTicks > MAX_BRIDGE_WALK_TICKS) {
                this.bridging = false;
                this.bridgeBlocked = true;
                this.note = "Bridging: walked " + this.bridgeWalkTicks + " ticks without finding a gap";
                return;
            }
            lookAt(player, new Vec3d(ahead.getX() + 0.5, player.getY(), ahead.getZ() + 0.5));
            client.options.forwardKey.setPressed(true);
            this.note = "Bridging: stepping " + this.bridgeDir + " walk=" + this.bridgeWalkTicks
                    + " at=" + exact(player) + " onGround=" + player.isOnGround()
                    + " pitch=" + Math.round(player.getPitch()) + " flying=" + player.getAbilities().flying
                    + " screen=" + (client.currentScreen != null);
            return;
        }

        BlockPos under = footPos.down();
        BlockState underState = world.getBlockState(under);
        if (underState.isAir() || underState.getCollisionShape(world, under).isEmpty()) {
            this.bridging = false;
            this.bridgeBlocked = true;
            this.note = "Bridging: nothing solid to build against";
            return;
        }
        if (!ensureScaffoldItem(player)) {
            this.bridging = false;
            this.bridgeBlocked = true;
            this.note = "No spare block to bridge with";
            return;
        }

        // Click the middle of the side face of the block underfoot, which puts the new block one
        // step ahead at the same level as the deck so far.
        BlockPos deck = ahead.down();
        Vec3d hitPos = new Vec3d(
                under.getX() + 0.5 + this.bridgeDir.getOffsetX() * 0.5,
                under.getY() + 0.5,
                under.getZ() + 0.5 + this.bridgeDir.getOffsetZ() * 0.5);
        lookAt(player, hitPos);
        if (holdCrouchFor(client, world, under)) {
            return;
        }
        ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                new BlockHitResult(hitPos, this.bridgeDir, under, false));
        if (!result.isAccepted()) {
            // Nothing was placed, so nothing is banked either: banking on a refused click left the
            // deck shorter than the engine believed and the walk carried straight on into the gap.
            this.note = "Bridging: click refused at " + top(deck);
            return;
        }
        this.scaffoldBlocks.push(new Scaffold(
                new GridPos(deck.getX(), deck.getY(), deck.getZ()), placedBlock(player, world, deck)));
        this.scaffoldPlaced++;
        this.bridgeSteps++;
        this.bridgeWalkTicks = 0;
        this.note = "Bridging: laid " + this.scaffoldMaterial + " at " + top(deck);
    }

    /** True when a banked cell still holds the temporary block that was put in it. */
    private static boolean holdsScaffold(ClientWorld world, Scaffold scaffold) {
        return world.getBlockState(toBlockPos(scaffold.pos())).getBlock() == scaffold.block();
    }

    /** Formats a bridge cell for a status note. */
    private static String top(BlockPos cell) {
        return cell.getX() + "," + cell.getY() + "," + cell.getZ();
    }

    /** Takes the temporary tower back down, top block first, leaving the world as it was found. */
    private void tickTearDown(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        if (client.interactionManager == null) {
            this.tearingDown = false;
            complete(world);
            return;
        }
        // A banked cell whose block is gone needs no tearing down, and one holding anything other
        // than the block that was put there is not a temporary block any more: it is the build's own
        // block, standing exactly where the plan wanted it, so it is dropped from the list unbroken.
        while (!this.scaffoldBlocks.isEmpty() && !holdsScaffold(world, this.scaffoldBlocks.peek())) {
            this.scaffoldBlocks.pop();
        }
        if (this.scaffoldBlocks.isEmpty()) {
            this.tearingDown = false;
            this.note = "Scaffolding removed";
            complete(world);
            return;
        }

        GridPos top = this.scaffoldBlocks.peek().pos();
        BlockPos pos = toBlockPos(top);
        releaseMovementKeys();

        double range = Math.max(3.0, player.getBlockInteractionRange());
        if (player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > range * range) {
            if (client.currentScreen != null) {
                this.note = "Close the menu so the tower can be taken down";
                return;
            }
            if (!steer(client, player, world, top)) {
                this.scaffoldBlocks.pop();
            }
            return;
        }

        lookAt(player, Vec3d.ofCenter(pos));
        Direction side = preferredBreakSide(player, pos);
        if (this.breakTicks == 0) {
            client.interactionManager.attackBlock(pos, side);
        } else {
            client.interactionManager.updateBlockBreakingProgress(pos, side);
        }
        this.breakTicks++;
        this.note = "Removing scaffolding at " + top;

        if (world.getBlockState(pos).isAir()) {
            this.broken++;
            this.breakTicks = 0;
            this.scaffoldBlocks.pop();
        } else if (this.breakTicks >= MAX_BREAK_TICKS) {
            client.interactionManager.cancelBlockBreaking();
            this.breakTicks = 0;
            this.scaffoldBlocks.pop();
        }
    }

    /**
     * True when a block is plain enough to stack into a tower: a full opaque cube the player can
     * place with one click and stand on, with no menu behind it.
     *
     * <p>Containers and interactive blocks are refused outright rather than merely deprioritised.
     * Placing a chest or a crafting table takes a crouch, and clicking one by accident opens a
     * screen that stops the walk; a tower is throwaway, so it should be built from throwaway
     * material.
     */
    /**
     * True when placing this block must be done crouched.
     *
     * <p>Crouching stops a right-click from opening a menu, which matters for the blocks that have
     * one: chests, furnaces, crafting tables and the like. For everything else it only hurts — a
     * crouching player cannot jump and moves at a crawl, which is what made the builder look stuck
     * whenever it was climbing or bridging. So the crouch is kept for the blocks that need it and
     * dropped for the rest.
     */
    /**
     * True when a click on this block has to be made crouched.
     *
     * <p>Vanilla runs the clicked block's own use action before the held item is used, so a click on
     * a chest, a lever or anything else with something behind it opens a screen or cycles the block
     * instead of placing anything — and then the builder clicks the same spot for ever. Crouching
     * makes the game skip that use action, so the block is placed.
     *
     * <p>Only blocks that really have an action are listed. Crouching everything would be easier to
     * write and worse to use: a crouching player moves at a third of the speed, and most of a build
     * is placed against things like slabs and stairs that do nothing at all when clicked.
     */
    private boolean needsCrouch(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BlockEntityProvider
                || INTERACTIVE_ON_USE.contains(block)
                || this.clickSwallowers.contains(block);
    }

    /**
     * Crouches for the click only when the block being clicked would otherwise open its menu.
     *
     * <p>Keep in step with {@link #needsCrouch}: every placement site uses this, so a container is
     * never left clicked standing, and a plain block never leaves the player stuck crouching.
     */
    private static boolean isScaffoldCandidate(Block block) {
        if (UNRELIABLE_FOOTING.contains(block) || INTERACTIVE_ON_USE.contains(block)) {
            return false;
        }
        if (block instanceof BlockEntityProvider) {
            return false;
        }
        return block.getDefaultState().isOpaqueFullCube();
    }

    /**
     * Keeps a scaffold block in hand, swapping only when the one held cannot be used.
     *
     * <p>Worth the check: the placement paths call this every tick, and picking a fresh slot each
     * time would swap the hotbar every tick for no reason.
     */
    private boolean ensureScaffoldItem(ClientPlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        if (held.getItem() instanceof BlockItem blockItem && isScaffoldCandidate(blockItem.getBlock())) {
            this.scaffoldMaterial = held.getName().getString();
            return true;
        }
        return selectScaffoldItem(player);
    }

    /**
     * Picks a disposable block for scaffolding: a plain full cube the player can click without a
     * menu opening, and preferably one the schematic does not need, so build materials are never
     * spent on a tower. Ties go to the largest stack, since a climb may need a dozen blocks.
     */
    private boolean selectScaffoldItem(ClientPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        int bestSlot = -1;
        int bestCount = -1;
        int fallbackSlot = -1;
        int fallbackCount = -1;
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            if (!isScaffoldCandidate(blockItem.getBlock())) {
                continue;
            }
            if (this.plan != null && this.plan.needs(blockItem.getBlock())) {
                // Only raided if nothing spare exists at all: better a finished build than none.
                if (stack.getCount() > fallbackCount) {
                    fallbackCount = stack.getCount();
                    fallbackSlot = slot;
                }
                continue;
            }
            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = slot;
            }
        }
        int chosen = bestSlot >= 0 ? bestSlot : fallbackSlot;
        if (chosen < 0) {
            return false;
        }
        // Read the name before moving the stack: moving swaps inventory slots, so the chosen slot
        // holds something else by the time the swap is done.
        this.scaffoldMaterial = inventory.getStack(chosen).getName().getString();
        moveToHotbar(player, chosen);
        return true;
    }

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    /** Walks toward {@code target} using the planned route. Returns false when it cannot be reached. */
    private boolean steer(MinecraftClient client, ClientPlayerEntity player, ClientWorld world, GridPos target) {
        GridPos playerPos = new GridPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());

        if (!target.equals(this.travelGoal)) {
            this.travelGoal = target;
            this.travelTicks = 0;
        }
        if (++this.travelTicks > MAX_TRAVEL_TICKS) {
            return false;
        }

        if (this.route == null || !target.equals(this.routeGoal)) {
            GridPos goal = chooseStandGoal(world, playerPos, target, this.triedGoals);
            if (goal == null) {
                return false;
            }
            List<GridPos> planned = PathPlanner.plan(new WorldView(world), playerPos, goal,
                    PathPlanner.DEFAULT_MAX_NODES);
            if (planned == null) {
                return false;
            }
            this.route = planned;
            this.routeGoal = target;
            this.routeIndex = 0;
            this.stuckTicks = 0;
            this.lastPos = playerPos;
            if (planned.isEmpty()) {
                // Already standing on the chosen spot; the reach check decides next tick.
                return true;
            }
        }
        if (this.routeIndex >= this.route.size()) {
            // Walked the whole route and still not in range — the spot was not good enough.
            return false;
        }

        GridPos node = this.route.get(this.routeIndex);
        Vec3d nodeCenter = new Vec3d(node.x() + 0.5, node.y(), node.z() + 0.5);
        double dx = player.getX() - nodeCenter.x;
        double dy = player.getY() - nodeCenter.y;
        double dz = player.getZ() - nodeCenter.z;
        if (dx * dx + dy * dy + dz * dz < 0.49) {
            this.routeIndex++;
            return true;
        }

        if (playerPos.equals(this.lastPos)) {
            if (++this.stuckTicks > STUCK_TICKS) {
                this.route = null;
                this.routeGoal = null;
                return false;
            }
        } else {
            this.stuckTicks = 0;
            this.lastPos = playerPos;
        }

        lookAt(player, nodeCenter);
        GameOptions options = client.options;
        options.forwardKey.setPressed(true);
        options.jumpKey.setPressed(node.y() > playerPos.y());
        // Only sprint on flat ground or uphill: sprinting downhill overshoots and can walk the
        // player straight off a ledge while building high up.
        setKey(options.sprintKey, node.y() >= playerPos.y(), sprintToggled(options));
        return true;
    }

    /**
     * Picks a spot to stand near a block: the closest standable cell on a small ring around it,
     * preferring one close to the player so the walk is short.
     */
    private static GridPos chooseStandGoal(ClientWorld world, GridPos playerPos, GridPos target,
                                           Set<GridPos> avoid) {
        PathPlanner.Standable view = new WorldView(world);
        GridPos best = null;
        long bestScore = Long.MAX_VALUE;
        // Search a couple of blocks up and down as well, so high blocks can be worked on from a
        // ledge or from the layer below once it exists.
        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int dy = 0; dy >= -2; dy--) {
                        GridPos candidate = new GridPos(target.x() + dx, target.y() + dy, target.z() + dz);
                        if (avoid.contains(candidate) || !PathPlanner.canStand(view, candidate)) {
                            continue;
                        }
                        long score = candidate.squaredDistanceTo(playerPos) + candidate.squaredDistanceTo(target);
                        if (score < bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static boolean inReach(ClientPlayerEntity player, BlockPos pos) {
        double range = Math.max(3.0, player.getBlockInteractionRange());
        return player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= range * range;
    }

    /** Formats a coordinate for a status note, so a stuck climb shows the height it reached. */
    private static String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** Formats the player's exact position, for diagnosing a walk that will not move. */
    private static String exact(ClientPlayerEntity player) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", player.getX(), player.getY(), player.getZ());
    }

    /** The yaw and pitch that point the player's eyes at a spot. */
    private static Look lookTo(ClientPlayerEntity player, Vec3d point) {
        Vec3d eyes = player.getEyePos();
        double dx = point.x - eyes.x;
        double dy = point.y - eyes.y;
        double dz = point.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return new Look((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0),
                (float) (-Math.toDegrees(Math.atan2(dy, horizontal))));
    }

    private static void applyLook(ClientPlayerEntity player, float yaw, float pitch) {
        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private static void lookAt(ClientPlayerEntity player, Vec3d point) {
        Look look = lookTo(player, point);
        applyLook(player, look.yaw(), look.pitch());
    }

    /**
     * Closes a menu the build's own click opened, and remembers the block that opened it.
     *
     * <p>A click is meant to place a block or step a state. A menu coming up instead means the clicked
     * block had one behind it, and the build's crouch rule did not know: the click was taken by the
     * block's own use action — the very thing a crouch is for — so nothing was placed and the screen
     * that answered it sits in the way of everything that follows, because nothing walks, places or
     * picks an item out of the inventory while a screen is up. The menu is closed here rather than left
     * for the player to find, and the block is crouched for from then on, which is what would have kept
     * it closed in the first place.
     */
    private void watchMenus(MinecraftClient client) {
        if (this.menuWatchTicks <= 0) {
            return;
        }
        this.menuWatchTicks--;
        if (!(client.currentScreen instanceof HandledScreen<?> screen) || client.player == null
                || screen.getScreenHandler() == client.player.playerScreenHandler) {
            // The player's own inventory is not the build's doing, and neither is anything that is not a
            // container screen at all — the mod's own menu among them.
            return;
        }
        // Closing the screen tells the server to close the container as well, so the click that opened
        // it is not left half done.
        screen.close();
        this.menusOpened++;
        this.menuWatchTicks = 0;
        if (this.menuCulprit != null && this.clickSwallowers.add(this.menuCulprit)) {
            this.note = "A menu opened when clicking " + this.menuCulprit.getName().getString()
                    + " — crouching for it from now on";
        } else {
            this.note = "Closed a menu the build opened by mistake (" + this.menusOpened + " so far)";
        }
    }

    /**
     * Writes the status note and the crouch key to the log, and only when one of the two changes.
     *
     * <p>The HUD they are shown on cannot be read back afterwards, so a build that misbehaves in
     * somebody else's world leaves nothing behind but the memory of it. A line per change costs
     * nothing and turns a real session into a timeline: what the builder was doing, and whether it
     * was crouched while it did it.
     */
    private void logState(MinecraftClient client) {
        if (!this.note.equals(this.lastLoggedNote)) {
            this.lastLoggedNote = this.note;
            LOGGER.info("[auto-cc] {}", this.note);
        }
        boolean sneaking = client.options != null && client.options.sneakKey.isPressed();
        if (sneaking != this.lastLoggedSneak) {
            this.lastLoggedSneak = sneaking;
            LOGGER.info("[auto-cc] crouch {} — {}", sneaking ? "down" : "up",
                    sneaking && walking(client) ? "while walking" : this.note);
        }
    }

    /**
     * Presses or lets up one of the game's keys, whichever way the game's key toggles are set.
     *
     * <p>The sneak and sprint keys are {@link net.minecraft.client.option.StickyKeyBinding}s: with the
     * game's toggle crouch or toggle sprint setting on, a press does not hold the key down, it
     * <em>flips</em> it — and a release, which is what {@code setPressed(false)} means for an ordinary
     * key, does nothing to a toggled one at all. A builder that pressed and released them like
     * ordinary keys therefore crouched when it meant to stand (the key had been pressed for the last
     * placement and the next press let it up), stood when it meant to crouch, and could never let the
     * crouch up when a state click needed it standing — which is exactly the shape of a build that
     * only misbehaves in a game with the toggle on, and leaves every repeater at the delay the click
     * that placed it gave it.
     *
     * <p>Asking for the state a toggled key is already in is the one thing that must never be done, so
     * every change of the crouch passes through here: a key that is up is pressed, and a key that is
     * down is let up the only way a toggled binding answers — with another tap.
     */
    private static void setKey(KeyBinding key, boolean down, boolean toggled) {
        if (key == null || key.isPressed() == down) {
            return;
        }
        key.setPressed(down || toggled);
    }

    /** True when the game's sneak key is set to toggle rather than to hold. */
    private static boolean sneakToggled(MinecraftClient client) {
        return client != null && client.options != null
                && Boolean.TRUE.equals(client.options.getSneakToggled().getValue());
    }

    /** True when the game's sprint key is set to toggle rather than to hold. */
    private static boolean sprintToggled(GameOptions options) {
        return options != null && Boolean.TRUE.equals(options.getSprintToggled().getValue());
    }

    /**
     * Holds crouch down and remembers that the engine is the one holding it.
     *
     * <p>Remembered rather than read back off the key binding, because the key can also be held by the
     * player: a build that took a crouch it did not press for its own would wait for runs that never
     * started, and would let go of one in a run it thought it was still making.
     */
    private void pressCrouch(MinecraftClient client) {
        if (client != null && client.options != null) {
            setKey(client.options.sneakKey, true, sneakToggled(client));
        }
        this.crouchHeld = true;
    }

    /** Lets the crouch up and forgets the run of clicks that needed it. */
    private void standUp(MinecraftClient client) {
        if (client != null && client.options != null) {
            setKey(client.options.sneakKey, false, sneakToggled(client));
        }
        this.crouchHeld = false;
        this.crouchTicks = 0;
        this.sneakHoldTicks = 0;
    }

    /** True while a walk key is down. Crouching through that is a crawl, not a build. */
    private static boolean walking(MinecraftClient client) {
        if (client == null || client.options == null) {
            return false;
        }
        GameOptions options = client.options;
        return options.forwardKey.isPressed() || options.backKey.isPressed()
                || options.leftKey.isPressed() || options.rightKey.isPressed();
    }

    /** Releases the walking keys but leaves crouch where it is, which placement looks after itself. */
    private static void stopWalking() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return;
        }
        GameOptions options = client.options;
        setKey(options.forwardKey, false, false);
        setKey(options.backKey, false, false);
        setKey(options.leftKey, false, false);
        setKey(options.rightKey, false, false);
        setKey(options.jumpKey, false, false);
        setKey(options.sprintKey, false, sprintToggled(options));
    }

    /** Releases every key the engine may be holding, crouch included. */
    private void releaseMovementKeys() {
        stopWalking();
        standUp(MinecraftClient.getInstance());
    }

    private static BlockPos toBlockPos(GridPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    private static String shortName(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    /** Collision view of the client world, cached per planning run because A* touches each cell often. */
    private static final class WorldView implements PathPlanner.Standable {
        private record Cell(boolean solid, boolean passable) {
        }

        private final ClientWorld world;
        private final Map<GridPos, Cell> cells = new HashMap<>();

        WorldView(ClientWorld world) {
            this.world = world;
        }

        private Cell cell(GridPos pos) {
            return this.cells.computeIfAbsent(pos, key -> {
                BlockPos blockPos = new BlockPos(key.x(), key.y(), key.z());
                BlockState state = this.world.getBlockState(blockPos);
                VoxelShape shape = state.getCollisionShape(this.world, blockPos);
                boolean passable = shape.isEmpty();
                // Slime and honey are excluded as floors: a route that stands on them is unreliable,
                // because slime bounces the player and honey glues them down.
                boolean solid = !passable && !UNRELIABLE_FOOTING.contains(state.getBlock());
                return new Cell(solid, passable);
            });
        }

        @Override
        public boolean solid(GridPos pos) {
            return cell(pos).solid();
        }

        @Override
        public boolean passable(GridPos pos) {
            return cell(pos).passable();
        }
    }
}
