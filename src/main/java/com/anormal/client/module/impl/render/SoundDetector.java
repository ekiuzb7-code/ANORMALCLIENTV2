package com.anormal.client.module.impl.render;

import com.anormal.client.lucid.StringSetting;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.HeaderSetting;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.util.sound.SoundClass;
import com.anormal.client.util.sound.SoundEventClassifier;
import com.anormal.client.util.sound.SoundEventData;
import com.anormal.client.util.sound.SoundEventHistory;
import com.anormal.client.util.sound.SoundEventRegistry;
import com.anormal.client.util.sound.SoundDeduplicator;
import com.anormal.client.util.sound.SoundWarningManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sound Detector.
 *
 * <p>Watches the sound instances the client actually plays and reports the useful ones: chests,
 * doors, machinery, combat, minecarts, explosions, sculk and player movement. It never listens to
 * a microphone, never invents a position and never invents a player name - everything comes from
 * the {@code SoundInstance} the client received, and unknown sources are shown as
 * {@code Unknown}.</p>
 *
 * <p>The event flow is: capture ({@code SoundManagerMixin}) -> classify
 * ({@link SoundEventClassifier}) -> category/range/source filters -> duplicate and cooldown
 * suppression -> scoring and thresholds -> warning card, chime, chat line, world marker and
 * history entry.</p>
 */
public class SoundDetector extends Module {

    // ---- Detection -----------------------------------------------------
    public final ModeSetting preset = new ModeSetting("Detection Preset",
            "Which event families are watched; Custom keeps your own category toggles",
            "Base Activity", "Base Activity", "Player Activity", "Containers", "Combat",
            "Transport", "Environment", "Everything", "Custom");
    public final NumberSetting range = new NumberSetting("Received-event range",
            "Only events the client received inside this radius are processed", 512.0, 8.0, 512.0, 8.0);
    public final NumberSetting warningThreshold = new NumberSetting("Warning threshold",
            "Minimum score before a warning card is shown", 1.0, 0.0, 20.0, 1.0);
    public final NumberSetting criticalThreshold = new NumberSetting("Critical threshold",
            "Score an event needs to be styled as critical", 1.0, 0.0, 20.0, 1.0);
    public final NumberSetting criticalScore = new NumberSetting("Critical Score",
            "Base score from which an event is critical (explosions default)", 5.0, 1.0, 20.0, 1.0);
    public final BooleanSetting requirePlayer = new BooleanSetting("Require identified player",
            "Drop events whose source player cannot be identified", false);
    public final BooleanSetting filterOwn = new BooleanSetting("Filter own actions",
            "Ignore your own footsteps and ambience; combat on you is still reported", true);
    public final BooleanSetting showUnknownSource = new BooleanSetting("Show unknown-source events",
            "Show events where the client has no entity source", true);
    public final StringSetting disabledEvents = new StringSetting("Disabled events",
            "Comma separated sound ids to ignore (e.g. minecraft:block.note_block.harp)", "", 256);

    // ---- Timing --------------------------------------------------------
    public final NumberSetting cooldown = new NumberSetting("Cooldown",
            "Per-sound cooldown in milliseconds", 500.0, 0.0, 5000.0, 50.0);
    public final BooleanSetting duplicate = new BooleanSetting("Duplicate suppression",
            "Merge the same sound at the same block position inside the window", true);
    public final NumberSetting duplicateWindow = new NumberSetting("Duplicate window",
            "Milliseconds for duplicate suppression", 250.0, 50.0, 2000.0, 50.0);
    public final NumberSetting maxPerSecond = new NumberSetting("Maximum warnings per second",
            "Upper limit of new warning cards per second", 10.0, 1.0, 50.0, 1.0);
    public final BooleanSetting grouping = new BooleanSetting("Grouping",
            "Group same-category events into one card with a counter", true);
    public final NumberSetting groupWindow = new NumberSetting("Group window",
            "Milliseconds in which same-category events are grouped", 2000.0, 200.0, 10000.0, 100.0);
    public final NumberSetting sourceCooldown = new NumberSetting("Source cooldown",
            "Seconds before the same source can warn again", 2.0, 0.0, 60.0, 0.5);
    public final NumberSetting scoreWindow = new NumberSetting("Score window",
            "Seconds the running score accumulates before it decays", 3.0, 1.0, 30.0, 1.0);

    // ---- Categories ----------------------------------------------------
    public final BooleanSetting music = new BooleanSetting("Music", "Music, jukebox and note block sounds", true);
    public final BooleanSetting movement = new BooleanSetting("Movement", "Footsteps, splashes, swimming", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs",
            "Mob movement, idle, hurt and death sounds; off hides mobs completely", true);
    public final BooleanSetting combat = new BooleanSetting("Combat", "Hits, hurt, death, shields, projectiles", true);
    public final BooleanSetting transport = new BooleanSetting("Transport", "Minecarts and boats", true);
    public final BooleanSetting explosions = new BooleanSetting("Explosions", "TNT, creepers, firework blasts", true);
    public final BooleanSetting farming = new BooleanSetting("Farming", "Crops, composters, beehives", true);
    public final BooleanSetting sculk = new BooleanSetting("Sculk", "Sculk sensors, shriekers and spread", true);
    public final BooleanSetting natural = new BooleanSetting("Natural", "Water, lava, weather, ambience", true);
    public final BooleanSetting customCategory = new BooleanSetting("Custom",
            "Container interactions and unrecognised sounds", true);

    // ---- Delivery ------------------------------------------------------
    public final BooleanSetting warningCards = new BooleanSetting("Warning cards", "Draw warning cards on the HUD", true);
    public final BooleanSetting chime = new BooleanSetting("Local warning chime", "Play a short client-side chime", true);
    public final BooleanSetting criticalChime = new BooleanSetting("Critical chime", "Use a distinct chime for critical events", true);
    public final NumberSetting volume = new NumberSetting("Volume", "Chime volume in percent", 60.0, 0.0, 100.0, 5.0);
    public final BooleanSetting chatNotices = new BooleanSetting("Chat notices", "Print a local chat line for every warning", false);
    public final BooleanSetting worldMarkers = new BooleanSetting("World markers", "Draw a marker where a warning came from", true);
    public final ModeSetting markerStyle = new ModeSetting("Marker style",
            "World marker shape: the small cube from the reference, a block outline, or both",
            "Cube", "Cube", "Block Outline", "Both");
    public final NumberSetting markerSize = new NumberSetting("Marker size",
            "Marker cube size in blocks", 1.0, 0.15, 2.0, 0.05);
    public final BooleanSetting footstepLines = new BooleanSetting("Footstep lines",
            "Where a player walks, draw a line on the ground that fades out instead of a cube", true);
    public final NumberSetting footstepFade = new NumberSetting("Footstep fade",
            "Seconds a footstep line takes to fade away", 2.5, 0.5, 10.0, 0.5);

    // ---- HUD -----------------------------------------------------------
    public final BooleanSetting hud = new BooleanSetting("HUD", "Show the detector HUD", true);
    public final ModeSetting hudPosition = new ModeSetting("Position", "HUD anchor",
            "Top Left", "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Custom");
    public final NumberSetting posX = new NumberSetting("Pos X", "Custom X position", 6.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Custom Y position", 120.0, 0.0, 1080.0, 1.0);
    public final NumberSetting visibleCards = new NumberSetting("Visible cards", "How many cards are shown", 3.0, 1.0, 10.0, 1.0);
    public final NumberSetting cardDuration = new NumberSetting("Card duration", "Seconds a card stays", 17.0, 1.0, 30.0, 1.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show distance",
            "Show the distance, direction and height line on a card", true);
    public final BooleanSetting showCoordinates = new BooleanSetting("Show coordinates", "Show the block position on a card", false);
    public final BooleanSetting showCategory = new BooleanSetting("Show category", "Show the category on a card", false);
    public final BooleanSetting showScore = new BooleanSetting("Show score", "Show the event score on a card", false);
    public final BooleanSetting showSource = new BooleanSetting("Show source", "Show the source entity or player", true);
    public final BooleanSetting showEventName = new BooleanSetting("Show event name", "Show the event name", true);
    public final BooleanSetting compact = new BooleanSetting("Compact mode", "One-line cards instead of full cards", false);
    public final BooleanSetting showInMenus = new BooleanSetting("Show while in menus", "Keep drawing the HUD with a screen open", true);
    public final BooleanSetting respectF1 = new BooleanSetting("Respect F1 HUD toggle",
            "Hide the detector while the vanilla HUD is hidden", true);

    // ---- History -------------------------------------------------------
    public final BooleanSetting historyEnabled = new BooleanSetting("History", "Record detected events", true);
    public final NumberSetting historySize = new NumberSetting("Maximum history", "How many events are kept", 100.0, 10.0, 500.0, 10.0);
    public final BooleanSetting showHistory = new BooleanSetting("Show history", "Draw the history panel", false);
    public final BooleanSetting pauseHistory = new BooleanSetting("Pause history", "Stop recording without clearing", false);
    public final ModeSetting historyCategory = new ModeSetting("History category",
            "Only show history entries of this category", "All", "All", "Music", "Movement",
            "Combat", "Transport", "Explosions", "Farming", "Sculk", "Natural", "Container",
            "Custom");
    public final StringSetting historyFilter = new StringSetting("History filter", "Only show history entries containing this text", "", 48);
    public final KeybindSetting clearHistoryKey = new KeybindSetting("Clear History Key", "Clears the history", GLFW.GLFW_KEY_UNKNOWN);

    // ---- Debug ---------------------------------------------------------
    public final BooleanSetting debug = new BooleanSetting("Debug mode", "Print every received sound with its raw id", false);
    public final BooleanSetting debugUnknownOnly = new BooleanSetting("Debug unknown only", "Only print sounds the classifier does not know", false);

    // ---- Scoring -------------------------------------------------------
    public final NumberSetting ambientScore = new NumberSetting("Ambient score", "Score for ambient events", 1.0, 0.0, 20.0, 1.0);
    public final NumberSetting interactionScore = new NumberSetting("Interaction score", "Score for interactions", 2.0, 0.0, 20.0, 1.0);
    public final NumberSetting containerScore = new NumberSetting("Container score", "Score for container events", 3.0, 0.0, 20.0, 1.0);
    public final NumberSetting playerScore = new NumberSetting("Player score", "Score for player-specific events", 3.0, 0.0, 20.0, 1.0);
    public final NumberSetting combatScore = new NumberSetting("Combat score", "Score for combat events", 3.0, 0.0, 20.0, 1.0);
    public final NumberSetting explosionScore = new NumberSetting("Explosion score", "Score for explosions", 5.0, 0.0, 20.0, 1.0);

    // ---- Runtime -------------------------------------------------------
    private final SoundDeduplicator dedupe = new SoundDeduplicator();
    private final SoundEventHistory history = new SoundEventHistory();
    private final SoundWarningManager warnings = new SoundWarningManager();
    private final ArrayDeque<long[]> runningScore = new ArrayDeque<>();
    private final Map<String, Long> sourceCooldowns = new HashMap<>();
    private final ArrayDeque<Footstep> footsteps = new ArrayDeque<>();
    private String lastPreset;
    private boolean clearKeyDown;
    private long lastChimeMs;
    private long lastDebugMs;
    // Diagnostics: proves at a glance where the pipeline stops (capture -> parse -> card).
    private int diagCaptured;
    private int diagParsed;
    private int diagShown;
    private String lastRawId = "-";
    private boolean lastRawRelative;
    private boolean lastRawHasSource;
    private int diagTicks;

    public SoundDetector() {
        super("Sound Detector",
                "Warn about nearby interactions, players and machinery",
                Category.RENDER);
        // The nine category toggles sit directly under the description, like the reference panel.
        addSetting(music);
        addSetting(movement);
        addSetting(mobs);
        addSetting(combat);
        addSetting(transport);
        addSetting(explosions);
        addSetting(farming);
        addSetting(sculk);
        addSetting(natural);
        addSetting(customCategory);

        addSetting(new HeaderSetting("Warning delivery"));
        addSetting(warningCards);
        addSetting(chime);
        addSetting(criticalChime);
        addSetting(volume);
        addSetting(chatNotices);
        addSetting(worldMarkers);
        addSetting(markerStyle);
        addSetting(markerSize);
        addSetting(footstepLines);
        addSetting(footstepFade);

        addSetting(new HeaderSetting("Detection"));
        addSetting(preset);
        addSetting(range);
        addSetting(warningThreshold);
        addSetting(criticalThreshold);
        addSetting(criticalScore);
        addSetting(requirePlayer);
        addSetting(filterOwn);
        addSetting(showUnknownSource);
        addSetting(disabledEvents);

        addSetting(new HeaderSetting("Timing"));
        addSetting(cooldown);
        addSetting(duplicate);
        addSetting(duplicateWindow);
        addSetting(maxPerSecond);
        addSetting(grouping);
        addSetting(groupWindow);
        addSetting(sourceCooldown);
        addSetting(scoreWindow);

        addSetting(new HeaderSetting("HUD"));
        addSetting(hud);
        addSetting(hudPosition);
        addSetting(posX);
        addSetting(posY);
        addSetting(visibleCards);
        addSetting(cardDuration);
        addSetting(showDistance);
        addSetting(showCoordinates);
        addSetting(showCategory);
        addSetting(showScore);
        addSetting(showSource);
        addSetting(showEventName);
        addSetting(compact);
        addSetting(showInMenus);
        addSetting(respectF1);

        addSetting(new HeaderSetting("History"));
        addSetting(historyEnabled);
        addSetting(historySize);
        addSetting(showHistory);
        addSetting(pauseHistory);
        addSetting(historyCategory);
        addSetting(historyFilter);
        addSetting(clearHistoryKey);

        addSetting(new HeaderSetting("Debug"));
        addSetting(debug);
        addSetting(debugUnknownOnly);

        addSetting(new HeaderSetting("Scoring"));
        addSetting(ambientScore);
        addSetting(interactionScore);
        addSetting(containerScore);
        addSetting(playerScore);
        addSetting(combatScore);
        addSetting(explosionScore);
    }

    @Override
    public void onEnable() {
        dedupe.clear();
        warnings.clear();
        runningScore.clear();
        sourceCooldowns.clear();
        history.clear();
        lastPreset = null;
        applyPreset();
    }

    @Override
    public void onDisable() {
        warnings.clear();
        runningScore.clear();
        sourceCooldowns.clear();
        footsteps.clear();
        SoundEventRegistry.clear();
    }

    // ------------------------------------------------------------------
    //  Event processing
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        applyPresetIfChanged();
        warnings.tick((long) (cardDuration.getValue() * 1000.0));
        handleClearKey();
        scanMovingMinecarts();
        processSounds();
        followMovingSources();
        diagTicks++;
    }

    /**
     * A minecart rolling sound is started once and then follows its cart, so the captured position
     * would freeze where the loop began. While such a warning is on screen, snap it to the nearest
     * cart instead, which keeps the cube marker on the minecart the user can actually hear.
     */
    private void followMovingSources() {
        List<SoundWarningManager.Warning> list = warnings.active();
        if (list.isEmpty() || mc.player == null || mc.world == null) return;
        boolean minecart = false;
        for (SoundWarningManager.Warning warning : list) {
            if (warning.data.rawSoundId != null && warning.data.rawSoundId.contains("minecart")) {
                minecart = true;
                break;
            }
        }
        if (!minecart) return;

        List<AbstractMinecartEntity> carts;
        try {
            carts = mc.world.getEntitiesByClass(AbstractMinecartEntity.class,
                    mc.player.getBoundingBox().expand(96.0), cart -> true);
        } catch (Throwable ignored) {
            return;
        }
        if (carts.isEmpty()) return;

        for (SoundWarningManager.Warning warning : list) {
            if (warning.data.rawSoundId == null || !warning.data.rawSoundId.contains("minecart")) continue;
            // Stay on the cart the warning was raised for (the one closest to its last position),
            // and only jump to the cart nearest to the player when that one is far away.
            AbstractMinecartEntity chosen = nearestCart(carts,
                    warning.data.position != null ? warning.data.position : mc.player.getEntityPos());
            if (chosen == null || chosen.squaredDistanceTo(mc.player) > 32.0 * 32.0) {
                chosen = nearestCart(carts, mc.player.getEntityPos());
            }
            if (chosen == null) continue;
            warning.data.position = chosen.getEntityPos();
            warning.data.distance = Math.sqrt(chosen.squaredDistanceTo(mc.player));
            // Vanilla keeps the rolling loop audible only while the cart is actually moving, so a
            // rolling cart is a sound that is still being heard: keep its card alive until it stops.
            if (chosen.getVelocity().horizontalLengthSquared() > 1.0E-4) {
                warning.lastSeen = System.currentTimeMillis();
            }
        }
    }

    private AbstractMinecartEntity nearestCart(List<AbstractMinecartEntity> carts, Vec3d position) {
        AbstractMinecartEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (AbstractMinecartEntity cart : carts) {
            double distance = cart.squaredDistanceTo(position);
            if (distance < best) {
                best = distance;
                nearest = cart;
            }
        }
        return nearest;
    }

    // ---- Moving minecarts ---------------------------------------------------
    private final Map<Integer, Vec3d> cartLastPos = new HashMap<>();
    private final Map<Integer, Long> cartLastFired = new HashMap<>();
    private int cartScanTick;

    /**
     * The rolling minecart loop is created <b>once</b> when the cart spawns on the client (with
     * volume 0) and afterwards only changes its own volume while the cart moves. No new sound play
     * ever reaches the capture hooks, so a cart that starts moving later was never reported. The
     * cart's real movement is the same information the loop's volume carries, so it is watched
     * directly and fed into the normal pipeline as an {@code entity.minecart.riding} event.
     */
    private void scanMovingMinecarts() {
        if (mc.player == null || mc.world == null) return;
        if (++cartScanTick % 5 != 0) return;
        if (!transport.isEnabled()) {
            cartLastPos.clear();
            return;
        }
        long now = System.currentTimeMillis();
        double reach = Math.min(range.getValue(), 256.0);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        try {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof AbstractMinecartEntity cart) || cart.isRemoved()) continue;
                int id = cart.getId();
                seen.add(id);
                Vec3d pos = cart.getEntityPos();
                Vec3d prev = cartLastPos.put(id, pos);
                if (prev == null) continue;
                if (pos.distanceTo(prev) < 0.35) continue; // < ~1.5 blocks/s: standing or nudged
                if (cart.squaredDistanceTo(mc.player) > reach * reach) continue;
                if (filterOwn.isEnabled() && mc.player.getVehicle() == cart) continue;
                Long fired = cartLastFired.get(id);
                if (fired != null && now - fired < 1000L) continue;
                cartLastFired.put(id, now);
                SoundEventRegistry.capturePacket(net.minecraft.util.Identifier.of("minecraft", "entity.minecart.riding"),
                        net.minecraft.sound.SoundCategory.NEUTRAL, pos.x, pos.y, pos.z, 1.0f, 1.0f, cart);
            }
        } catch (Throwable ignored) {
        }
        cartLastPos.keySet().retainAll(seen);
        cartLastFired.keySet().retainAll(seen);
    }

    /** Looping movement sounds: minecart rolling and the in-minecart loop. */
    private boolean isMovingEntitySound(net.minecraft.util.Identifier id) {
        if (id == null) return false;
        String path = id.getPath();
        return path.startsWith("entity.minecart");
    }

    private void handleClearKey() {
        int key = clearHistoryKey.getKey();
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == 0) return;
        if (mc.getWindow() == null) return;
        boolean down;
        try {
            long handle = mc.getWindow().getHandle();
            down = KeybindSetting.isMouseCode(key)
                    ? GLFW.glfwGetMouseButton(handle, KeybindSetting.mouseButton(key)) == GLFW.GLFW_PRESS
                    : GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return;
        }
        if (down && !clearKeyDown) {
            history.clear();
            chat("History cleared");
        }
        clearKeyDown = down;
    }

    private void processSounds() {
        long now = System.currentTimeMillis();
        List<SoundEventRegistry.RawSound> received = SoundEventRegistry.drain(96);
        diagCaptured += received.size();
        if (!received.isEmpty()) {
            SoundEventRegistry.RawSound last = received.get(received.size() - 1);
            lastRawId = last.id.toString();
            lastRawRelative = last.relative;
            lastRawHasSource = last.entity != null;
            if (debug.isEnabled() && now - lastDebugMs >= 250L) {
                lastDebugMs = now;
                chat("raw " + lastRawId + " rel=" + lastRawRelative + " src=" + lastRawHasSource);
            }
        }
        for (SoundEventRegistry.RawSound raw : received) {
            try {
                diagParsed++;
                if (isDisabled(raw.id)) continue;
                SoundEventClassifier.Match match = SoundEventClassifier.classify(raw.id);
                if (!categoryEnabled(match.soundClass)) continue;
                Entity source = raw.entity;
                boolean movingLoop = isMovingEntitySound(raw.id);
                // A looping movement sound (minecart rolling) is a MovingSoundInstance: no
                // EntityTracking source and flagged relative, exactly like a UI click. It is a
                // real world sound though, so resolve it to its vehicle instead of dropping it.
                if (movingLoop && source == null) {
                    source = SoundEventRegistry.findMovingOwner(raw.id, new Vec3d(raw.x, raw.y, raw.z));
                }
                // Relative sounds (UI clicks, own UI feedback) are attached to the camera, not to a
                // world block, so only those without any source are dropped here.
                if (raw.relative && source == null) continue;
                // Mobs are a source filter, not a sound family: with the toggle off a zombie's
                // steps, idle sounds and hurt cries produce no card, marker, trail or history
                // entry. Players and non-living entities (minecarts, TNT) are unaffected.
                if (!mobs.isEnabled() && isMob(source)) continue;
                String sourceName = null;
                String playerName = null;
                if (source != null) {
                    sourceName = source.getName().getString();
                    if (source instanceof PlayerEntity player) playerName = player.getName().getString();
                }
                if (requirePlayer.isEnabled() && playerName == null) continue;
                boolean local = source != null && source == mc.player;
                // Your own swing is not evidence about anyone else, and it would group with the
                // victim's hurt sound and turn a single hit into a CRITICAL card.
                if (local && raw.id.getPath().startsWith("entity.player.attack")) continue;
                // Combat is never filtered: damage taken by the local player is the single most
                // useful event, and the reference shows it too.
                if (filterOwn.isEnabled() && local && match.soundClass != SoundClass.COMBAT) continue;
                if (!showUnknownSource.isEnabled() && source == null) continue;

                // The captured coordinates of a moving loop are the ones it had when the loop
                // started, so the vehicle's live position is used instead.
                Vec3d position = source != null && movingLoop
                        ? source.getEntityPos() : new Vec3d(raw.x, raw.y, raw.z);
                double distance = Math.sqrt(mc.player.squaredDistanceTo(position));
                if (distance > range.getValue()) continue;
                // A footstep leaves a fading line on the ground, recorded before the warning
                // gates so the trail follows a walking player even when no card is due. Your own
                // step is played locally without an entity source, so it is only recognisable by
                // its position right under you - "Filter own actions" drops exactly that step.
                boolean footstep = isFootstep(raw.id.toString());
                if (footstep && filterOwn.isEnabled() && source == null && distance <= 1.75) {
                    continue;
                }
                if (footstep && footstepLines.isEnabled()) recordFootstep(source, position, now);

                String key = SoundDeduplicator.key(raw.id.toString(), position);
                if (duplicate.isEnabled() && dedupe.duplicate(key, now,
                        (long) duplicateWindow.getValue().intValue())) {
                    continue;
                }
                if (dedupe.cooling(key.substring(0, key.indexOf('|')) + "@" + Math.floorDiv((int) Math.floor(position.x), 8) + "," + Math.floorDiv((int) Math.floor(position.z), 8), now, (long) cooldown.getValue().intValue())) {
                    continue;
                }

                int score = scoreFor(match.kind);
                SoundEventData.Severity severity = severity(score);
                SoundEventData data = new SoundEventData(raw.id.toString(), raw.id.toString(),
                        match.soundClass, match.name, position, distance, sourceName, playerName,
                        score, now, severity, local);

                debug(data);
                if (historyEnabled.isEnabled()) {
                    history.add(data, historySize.getValue().intValue(), pauseHistory.isEnabled());
                }

                int accumulated = accumulate(score, now);
                int gate = Math.max(score, accumulated);
                if (gate < warningThreshold.getValue()) continue;
                warn(data, now);
            } catch (Throwable ignored) {
            }
        }
    }

    private void warn(SoundEventData data, long now) {
        if (!warningCards.isEnabled() && !worldMarkers.isEnabled() && !chatNotices.isEnabled()) return;
        long groupMs = (long) groupWindow.getValue().intValue();
        // A refresh of an existing card is cheap and always allowed; only a brand new card is
        // subject to the per-second budget, so ambient spam cannot starve block breaks anymore.
        boolean merging = warnings.mergeTarget(data, grouping.isEnabled(), groupMs) != null;
        if (!merging && !warnings.allow(now, maxPerSecond.getValue().intValue())) return;

        String sourceKey = data.player != null ? "player:" + data.player
                : data.position == null ? "unknown"
                : "pos:" + Math.round(data.position.x / 8.0) + "," + Math.round(data.position.y / 8.0)
                + "," + Math.round(data.position.z / 8.0);
        long sourceMs = (long) (sourceCooldown.getValue() * 1000.0);
        Long previous = sourceCooldowns.get(sourceKey);
        if (previous != null && now - previous < sourceMs) return;
        sourceCooldowns.put(sourceKey, now);
        if (sourceCooldowns.size() > 256) sourceCooldowns.clear();

        if (warningCards.isEnabled() || worldMarkers.isEnabled()) {
            warnings.submit(data, visibleCards.getValue().intValue(), grouping.isEnabled(), groupMs);
            diagShown++;
        }
        if (chatNotices.isEnabled()) {
            chat(data.name + " " + data.distanceText()
                    + (data.severity == SoundEventData.Severity.CRITICAL ? " [critical]" : ""));
        }
        playChime(data.severity, now);
    }

    private int accumulate(int score, long now) {
        long windowMs = (long) (scoreWindow.getValue() * 1000.0);
        while (!runningScore.isEmpty() && now - runningScore.peekFirst()[0] > windowMs) {
            runningScore.pollFirst();
        }
        runningScore.addLast(new long[]{now, score});
        int total = 0;
        for (long[] entry : runningScore) total += (int) entry[1];
        return total;
    }

    private SoundEventData.Severity severity(int score) {
        boolean critical = score >= criticalScore.getValue().intValue()
                && score >= criticalThreshold.getValue().intValue();
        if (critical) return SoundEventData.Severity.CRITICAL;
        if (score >= warningThreshold.getValue().intValue()) return SoundEventData.Severity.WARNING;
        return SoundEventData.Severity.INFO;
    }

    /** Any living entity that is not a player. Unknown sources are never treated as mobs. */
    private boolean isMob(Entity source) {
        return source instanceof LivingEntity && !(source instanceof PlayerEntity);
    }

    private boolean isDisabled(net.minecraft.util.Identifier id) {
        String list = disabledEvents.getValue();
        if (list == null || list.isBlank()) return false;
        String full = id.toString();
        String path = id.getPath();
        for (String part : list.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) continue;
            if (entry.equalsIgnoreCase(full) || entry.equalsIgnoreCase(path)) return true;
        }
        return false;
    }

    private SoundClass historyFilterClass() {
        String value = historyCategory.getValue();
        if (value == null || value.equals("All")) return null;
        if (value.equals("Container")) return SoundClass.CONTAINER;
        for (SoundClass soundClass : SoundClass.values()) {
            if (soundClass.display().equalsIgnoreCase(value) || soundClass.name().equalsIgnoreCase(value)) {
                return soundClass;
            }
        }
        return null;
    }

    private int scoreFor(SoundEventClassifier.Kind kind) {
        switch (kind) {
            case CONTAINER: return containerScore.getValue().intValue();
            case PLAYER: return playerScore.getValue().intValue();
            case COMBAT: return combatScore.getValue().intValue();
            case EXPLOSION: return explosionScore.getValue().intValue();
            case INTERACTION: return interactionScore.getValue().intValue();
            case AMBIENT:
            default: return ambientScore.getValue().intValue();
        }
    }

    private boolean categoryEnabled(SoundClass soundClass) {
        switch (soundClass) {
            case MUSIC: return music.isEnabled();
            case MOVEMENT: return movement.isEnabled();
            case COMBAT: return combat.isEnabled();
            case TRANSPORT: return transport.isEnabled();
            case EXPLOSIONS: return explosions.isEnabled();
            case FARMING: return farming.isEnabled();
            case SCULK: return sculk.isEnabled();
            case NATURAL: return natural.isEnabled();
            case CONTAINER:
            case CUSTOM:
            default: return customCategory.isEnabled();
        }
    }

    // ------------------------------------------------------------------
    //  Preset
    // ------------------------------------------------------------------

    private void applyPresetIfChanged() {
        String current = preset.getValue();
        if (current == null) return;
        if (current.equals(lastPreset)) return;
        applyPreset();
    }

    private void applyPreset() {
        String current = preset.getValue();
        lastPreset = current;
        if (current == null || current.equals("Custom")) return;
        boolean all = current.equals("Everything");

        music.setEnabled(all || current.equals("Environment"));
        movement.setEnabled(all || current.equals("Base Activity") || current.equals("Player Activity")
                || current.equals("Combat"));
        combat.setEnabled(all || current.equals("Base Activity") || current.equals("Player Activity") || current.equals("Combat"));
        transport.setEnabled(all || current.equals("Base Activity") || current.equals("Transport"));
        explosions.setEnabled(all || current.equals("Base Activity"));
        farming.setEnabled(all || current.equals("Base Activity"));
        sculk.setEnabled(all || current.equals("Base Activity") || current.equals("Environment"));
        natural.setEnabled(all || current.equals("Base Activity") || current.equals("Environment"));
        customCategory.setEnabled(all || current.equals("Base Activity") || current.equals("Containers"));
    }

    // ------------------------------------------------------------------
    //  Output helpers
    // ------------------------------------------------------------------

    private void playChime(SoundEventData.Severity severity, long now) {
        if (!chime.isEnabled() || mc.player == null) return;
        if (severity == SoundEventData.Severity.CRITICAL && !criticalChime.isEnabled()) return;
        if (now - lastChimeMs < 120L) return;
        lastChimeMs = now;
        float level = (float) (volume.getValue() / 100.0);
        if (level <= 0.0f) return;
        try {
            if (severity == SoundEventData.Severity.CRITICAL) {
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), level, 0.7f);
            } else {
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), level, 1.7f);
            }
        } catch (Throwable ignored) {
        }
    }

    private void chat(String message) {
        if (mc.inGameHud == null) return;
        try {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[Sound] " + message));
        } catch (Throwable ignored) {
        }
    }

    private void debug(SoundEventData data) {
        if (!debug.isEnabled()) return;
        if (debugUnknownOnly.isEnabled() && data.soundClass != SoundClass.CUSTOM) return;
        long now = System.currentTimeMillis();
        if (now - lastDebugMs < 250L) return;
        lastDebugMs = now;
        String line = String.format(Locale.ROOT,
                "raw=%s | cat=%s | pos=%s | dist=%s | src=%s | score=%d | %s",
                data.rawSoundId, data.soundClass.name(), data.positionText(),
                data.distanceText(), data.sourceText(), data.score, data.severity.name());
        chat(line);
    }

    // ------------------------------------------------------------------
    //  HUD
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        if (respectF1.isEnabled() && mc.options.hudHidden) return;
        if (!showInMenus.isEnabled() && mc.currentScreen != null) return;

        List<SoundWarningManager.Warning> list = new ArrayList<>(warnings.active());
        if (worldMarkers.isEnabled()) drawMarkers(context, tickDelta, list);
        if (footstepLines.isEnabled()) drawFootstepTrail(context, tickDelta);
        if (!hud.isEnabled()) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int limit = Math.max(1, visibleCards.getValue().intValue());
        if (list.size() > limit) list = new ArrayList<>(list.subList(0, limit));

        boolean drawCards = warningCards.isEnabled() && !list.isEmpty();
        int totalHeight = 0;
        List<Integer> heights = new ArrayList<>();
        List<Integer> widths = new ArrayList<>();
        if (drawCards) {
            for (SoundWarningManager.Warning warning : list) {
                int h;
                int w;
                if (compact.isEnabled()) {
                    h = 17;
                    w = Math.max(120, mc.textRenderer.getWidth(compactLine(warning)) + 22);
                } else {
                    List<String> lines = linesFor(warning);
                    int textWidth = mc.textRenderer.getWidth(cardTitle(warning));
                    if (warning.count > 1) {
                        textWidth += mc.textRenderer.getWidth("x" + warning.count) + 18;
                    }
                    for (String line : lines) {
                        textWidth = Math.max(textWidth, mc.textRenderer.getWidth(line));
                    }
                    h = 20 + lines.size() * 11 + 6;
                    w = Math.max(196, textWidth + 20);
                }
                heights.add(h);
                widths.add(Math.min(340, w));
                totalHeight += h + 4;
            }
        }

        int historyRows = showHistory.isEnabled()
                ? history.view(6, historyFilter.getValue(), historyFilterClass()).size() : 0;
        int historyHeight = historyRows > 0 ? 20 + historyRows * 11 : 0;
        int blockHeight = Math.max(0, totalHeight - 4) + (historyHeight > 0 ? historyHeight + 6 : 0);

        String mode = hudPosition.getValue() == null ? "Top Left" : hudPosition.getValue();
        boolean right = mode.contains("Right");
        boolean bottom = mode.contains("Bottom");
        int y = mode.equals("Custom") ? posY.getValue().intValue()
                : bottom ? Math.max(8, sh - blockHeight - 8) : 8;

        if (drawCards) {
            for (int i = 0; i < list.size(); i++) {
                SoundWarningManager.Warning warning = list.get(i);
                int w = widths.get(i);
                int h = heights.get(i);
                int x = mode.equals("Custom") ? posX.getValue().intValue()
                        : right ? Math.max(6, sw - w - 6) : 6;
                drawCard(context, warning, x, y, w, h);
                y += h + 4;
            }
        }

        if (historyRows > 0) drawHistory(context, y + 2, historyRows);
        if (!drawCards && historyRows == 0) drawListeningPanel(context);
        if (debug.isEnabled()) drawDiagnostics(context, sh);
    }

    /**
     * Always-visible status while there is nothing to show, so "silence" is explainable: either
     * the module is receiving sounds (captured grows) or the capture hook never fired.
     */
    private void drawListeningPanel(DrawContext context) {
        String line1 = "Sound Detector | listening";
        String line2;
        int color;
        if (diagCaptured == 0 && diagTicks > 100) {
            line2 = "captured 0 | capture hook inactive";
            color = 0xFFF87171;
        } else {
            line2 = "captured " + diagCaptured + " | shown " + diagShown;
            color = 0xFF60A5FA;
        }
        int w = Math.max(mc.textRenderer.getWidth(line1), mc.textRenderer.getWidth(line2)) + 16;
        String mode = hudPosition.getValue() == null ? "Top Left" : hudPosition.getValue();
        int x = mode.contains("Right") ? Math.max(6, mc.getWindow().getScaledWidth() - w - 6) : 6;
        int y = 8;
        HudStyle.panel(context, x, y, w, 30, color);
        RenderUtils.drawText(context, mc.textRenderer, line1, x + 8, y + 5, 0xFFEDEFF5, true);
        RenderUtils.drawText(context, mc.textRenderer, line2, x + 8, y + 16, color, false);
    }

    /** Debug readout: shows whether sounds are captured, parsed and turned into cards at all. */
    private void drawDiagnostics(DrawContext context, int sh) {
        String line1 = "captured " + diagCaptured + " | parsed " + diagParsed + " | shown " + diagShown;
        String line2 = "last " + trim(lastRawId, 190) + " rel=" + lastRawRelative + " src=" + lastRawHasSource;
        int w = Math.max(mc.textRenderer.getWidth(line1), mc.textRenderer.getWidth(line2)) + 16;
        int y = Math.max(8, sh - 42);
        HudStyle.panel(context, 6, y, w, 30, 0xFF60A5FA);
        RenderUtils.drawText(context, mc.textRenderer, line1, 14, y + 5, 0xFFEDEFF5, true);
        RenderUtils.drawText(context, mc.textRenderer, line2, 14, y + 16, 0xFF9AA0AE, false);
    }

    private void drawCard(DrawContext context, SoundWarningManager.Warning warning,
                          int x, int y, int w, int h) {
        int color = severityColor(cardSeverity(warning));
        HudStyle.panel(context, x, y, w, h, color);
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, (color & 0x00FFFFFF) | 0x38000000);

        if (compact.isEnabled()) {
            RenderUtils.drawText(context, mc.textRenderer, compactLine(warning), x + 10, y + 4,
                    color, true);
            return;
        }

        RenderUtils.drawText(context, mc.textRenderer, cardTitle(warning), x + 10, y + 6, color, true);
        if (warning.count > 1) {
            String badge = "x" + warning.count;
            RenderUtils.drawText(context, mc.textRenderer, badge,
                    x + w - 10 - mc.textRenderer.getWidth(badge), y + 6, 0xFF868A99, false);
        }
        int lineY = y + 20;
        List<String> lines = linesFor(warning);
        for (int i = 0; i < lines.size(); i++) {
            int lineColor = i == 0 ? 0xFFEDEFF5 : 0xFFB9BDC8;
            RenderUtils.drawText(context, mc.textRenderer, lines.get(i), x + 10, lineY, lineColor, i == 0);
            lineY += 11;
        }
    }

    private String cardTitle(SoundWarningManager.Warning warning) {
        return cardSeverity(warning) == SoundEventData.Severity.CRITICAL ? "CRITICAL" : "WARNING";
    }

    /**
     * Grouped events add their scores up, exactly like the reference: two container events of
     * score 3 become one CRITICAL card at 6, while a single damage event stays WARNING.
     */
    private SoundEventData.Severity cardSeverity(SoundWarningManager.Warning warning) {
        return severity(warning.totalScore);
    }

    private String compactLine(SoundWarningManager.Warning warning) {
        String name = warning.data.name;
        if (warning.count > 1) name += " x" + warning.count;
        String symbol = cardSeverity(warning) == SoundEventData.Severity.CRITICAL ? "!!!" : "!";
        return symbol + " " + name + " - " + metricLine(warning.data);
    }

    /**
     * Card body in the reference layout:
     * <pre>
     * Container closed
     * Actor unknown
     * 8 m - NW - 6 m below
     * Observed interaction, actor unknown
     * Last seen 0.7 s ago
     * </pre>
     */
    private List<String> linesFor(SoundWarningManager.Warning warning) {
        List<String> lines = new ArrayList<>();
        SoundEventData data = warning.data;
        if (showEventName.isEnabled()) lines.add(data.name);
        lines.add(actorLine(data));
        if (showDistance.isEnabled()) lines.add(metricLine(data));
        lines.add(observedLine(data));
        lines.add(lastSeenLine(warning));
        if (showCategory.isEnabled()) lines.add(data.soundClass.display());
        if (showCoordinates.isEnabled()) lines.add("Position: " + data.positionText());
        if (showScore.isEnabled()) lines.add("Score: +" + warning.totalScore);
        if (lines.isEmpty()) lines.add(data.name);
        return lines;
    }

    /** {@code 8 m - NW - 6 m below} */
    private String metricLine(SoundEventData data) {
        double distance = data.distance < 0.0 ? 0.0 : data.distance;
        return String.format(Locale.ROOT, "%.0f m - %s - %s", distance,
                directionText(data.position), heightText(data.position));
    }

    /** Compass sector from the player to the event, {@code here} when it is on top of us. */
    private String directionText(Vec3d position) {
        if (position == null || mc.player == null) return "unknown";
        double dx = position.x - mc.player.getX();
        double dz = position.z - mc.player.getZ();
        double ax = Math.abs(dx);
        double az = Math.abs(dz);
        if (ax < 0.75 && az < 0.75) return "here";
        if (ax > az * 2.5) return dx > 0.0 ? "E" : "W";
        if (az > ax * 2.5) return dz < 0.0 ? "N" : "S";
        return (dz < 0.0 ? "N" : "S") + (dx > 0.0 ? "E" : "W");
    }

    /** Vertical relation to the player: {@code same height}, {@code 6 m below}, {@code 20 m above}. */
    private String heightText(Vec3d position) {
        if (position == null || mc.player == null) return "unknown";
        double dy = position.y - mc.player.getEyeY();
        if (Math.abs(dy) < 1.0) return "same height";
        return String.format(Locale.ROOT, "%.0f m %s", Math.abs(dy), dy > 0.0 ? "above" : "below");
    }

    /** {@code Observed interaction, actor unknown} - the actor is only named when the client knows it. */
    private String observedLine(SoundEventData data) {
        boolean ambience = data.soundClass == SoundClass.NATURAL || data.soundClass == SoundClass.MUSIC;
        return (ambience ? "Observed ambience" : "Observed interaction")
                + ", actor " + actorText(data);
    }

    /** {@code Actor unknown} - a separate line in the reference card, never a guessed name. */
    private String actorLine(SoundEventData data) {
        return "Actor " + actorText(data);
    }

    private String actorText(SoundEventData data) {
        if (!showSource.isEnabled()) return "unknown";
        if (data.player != null && !data.player.isEmpty()) return data.player;
        if (data.source != null && !data.source.isEmpty()) return data.source;
        return "unknown";
    }

    private String lastSeenLine(SoundWarningManager.Warning warning) {
        long age = Math.max(0L, System.currentTimeMillis() - warning.lastSeen);
        return String.format(Locale.ROOT, "Last seen %.1f s ago", age / 1000.0);
    }

    /**
     * Small severity-coloured wireframe cube on the sound origin, like the reference marker.
     * {@code Marker style} switches back to the block outline or draws both.
     */
    private void drawMarkers(DrawContext context, float tickDelta,
                             List<SoundWarningManager.Warning> list) {
        String style = markerStyle.getValue() == null ? "Cube" : markerStyle.getValue();
        boolean cube = !style.equals("Block Outline");
        boolean outline = !style.equals("Cube");
        double half = markerSize.getValue() / 2.0;
        for (SoundWarningManager.Warning warning : list) {
            Vec3d position = warning.data.position;
            if (position == null) continue;
            // Footsteps are drawn as fading ground lines, not as cubes.
            if (footstepLines.isEnabled() && isFootstep(warning.data.rawSoundId)) continue;
            try {
                int color = severityColor(cardSeverity(warning));
                int line = (color & 0x00FFFFFF) | 0xFF000000;
                if (cube) {
                    Box box = new Box(position.x - half, position.y - half, position.z - half,
                            position.x + half, position.y + half, position.z + half);
                    RenderUtils.drawBox3D(context, box, line, 0);
                }
                if (outline) {
                    RenderUtils.drawBlockOutline(context, BlockPos.ofFloored(position),
                            (color & 0x00FFFFFF) | 0xB0000000, tickDelta, 1);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** {@code block.stone.step}, {@code entity.player.step} and any other footstep id. */
    private boolean isFootstep(String fullId) {
        if (fullId == null) return false;
        String path = fullId.startsWith("minecraft:") ? fullId.substring("minecraft:".length()) : fullId;
        return path.endsWith(".step") || path.contains("footstep");
    }

    private void recordFootstep(Entity source, Vec3d soundPosition, long now) {
        Vec3d position = source != null ? source.getEntityPos() : soundPosition;
        if (position == null) return;
        footsteps.addLast(new Footstep(position, now));
        while (footsteps.size() > 64) footsteps.pollFirst();
    }

    /**
     * Draws one short line on the ground per footstep and fades it out over
     * {@code Footstep fade} seconds, so a walking player leaves a fading track instead of a
     * cube hovering above their feet.
     */
    private void drawFootstepTrail(DrawContext context, float tickDelta) {
        long now = System.currentTimeMillis();
        long fadeMs = (long) (footstepFade.getValue() * 1000.0);
        if (fadeMs <= 0L) {
            footsteps.clear();
            return;
        }
        while (!footsteps.isEmpty() && now - footsteps.peekFirst().time > fadeMs) {
            footsteps.pollFirst();
        }
        if (footsteps.size() < 2) return;

        // Project every step once, then join them in screen space: the line runs through the
        // steps, so when the player turns the track turns with them instead of being an
        // axis-aligned dash per step.
        int size = footsteps.size();
        double[] wx = new double[size];
        double[] wy = new double[size];
        double[] wz = new double[size];
        long[] when = new long[size];
        double[] sx = new double[size];
        double[] sy = new double[size];
        int[] alpha = new int[size];
        int count = 0;
        for (Footstep step : footsteps) {
            int[] point = ProjectionUtil.project(
                    new Vec3d(step.position.x, step.position.y + 0.03, step.position.z), tickDelta);
            if (point == null) continue;
            wx[count] = step.position.x;
            wy[count] = step.position.y;
            wz[count] = step.position.z;
            when[count] = step.time;
            sx[count] = point[0];
            sy[count] = point[1];
            float life = 1.0f - (float) (now - step.time) / (float) fadeMs;
            alpha[count] = Math.max(8, Math.min(200, (int) (Math.max(0.0f, life) * 200.0f)));
            count++;
        }
        if (count < 2) return;

        // Catmull-Rom between consecutive steps: four small segments per span give a rounded,
        // continuous line, and the fade follows the age of the newer step.
        int subdivisions = 4;
        for (int i = 0; i < count - 1; i++) {
            double spanX = wx[i + 1] - wx[i];
            double spanY = wy[i + 1] - wy[i];
            double spanZ = wz[i + 1] - wz[i];
            double gap = Math.sqrt(spanX * spanX + spanY * spanY + spanZ * spanZ);
            // A teleport, a long pause or a jump must not draw a line across the world.
            if (gap > 6.0 || when[i + 1] - when[i] > 1500L) continue;

            double x0 = sx[Math.max(0, i - 1)], y0 = sy[Math.max(0, i - 1)];
            double x1 = sx[i], y1 = sy[i];
            double x2 = sx[i + 1], y2 = sy[i + 1];
            double x3 = sx[Math.min(count - 1, i + 2)], y3 = sy[Math.min(count - 1, i + 2)];
            int color = (alpha[i + 1] << 24) | 0x60A5FA;
            double px = x1;
            double py = y1;
            for (int s = 1; s <= subdivisions; s++) {
                double t = s / (double) subdivisions;
                double t2 = t * t;
                double t3 = t2 * t;
                double qx = 0.5 * (2.0 * x1 + (-x0 + x2) * t
                        + (2.0 * x0 - 5.0 * x1 + 4.0 * x2 - x3) * t2
                        + (-x0 + 3.0 * x1 - 3.0 * x2 + x3) * t3);
                double qy = 0.5 * (2.0 * y1 + (-y0 + y2) * t
                        + (2.0 * y0 - 5.0 * y1 + 4.0 * y2 - y3) * t2
                        + (-y0 + 3.0 * y1 - 3.0 * y2 + y3) * t3);
                RenderUtils.drawLine(context, (int) Math.round(px), (int) Math.round(py),
                        (int) Math.round(qx), (int) Math.round(qy), color, 1.6f);
                px = qx;
                py = qy;
            }
        }
    }

    /** One recorded footstep: the ground position and when it was heard. */
    private static final class Footstep {
        final Vec3d position;
        final long time;

        Footstep(Vec3d position, long time) {
            this.position = position;
            this.time = time;
        }
    }

    private void drawHistory(DrawContext context, int y, int rows) {
        List<SoundEventHistory.Entry> entries = history.view(rows, historyFilter.getValue(), historyFilterClass());
        if (entries.isEmpty()) return;
        int w = 240;
        int h = 20 + entries.size() * 11;
        int sw = mc.getWindow().getScaledWidth();
        String mode = hudPosition.getValue() == null ? "Top Left" : hudPosition.getValue();
        boolean right = mode.contains("Right");
        int x = mode.equals("Custom") ? posX.getValue().intValue()
                : right ? Math.max(6, sw - w - 6) : 6;
        int accent = ThemeManager.getAccentColor();
        HudStyle.panel(context, x, y, w, h, accent);
        HudStyle.title(context, mc.textRenderer, "Sound History", x + 10, y + 7);
        int lineY = y + 20;
        for (SoundEventHistory.Entry entry : entries) {
            String text = entry.time + "  " + entry.name + "  " + entry.distance + "  +" + entry.score;
            RenderUtils.drawText(context, mc.textRenderer, trim(text, 225), x + 10, lineY,
                    0xFFB9BDC8, false);
            lineY += 11;
        }
    }

    private String trim(String text, int maxWidth) {
        String value = text;
        while (mc.textRenderer.getWidth(value) > maxWidth && value.length() > 4) {
            value = value.substring(0, value.length() - 2);
        }
        return value;
    }

    private int severityColor(SoundEventData.Severity severity) {
        switch (severity) {
            case CRITICAL: return 0xFFF87171;
            case WARNING: return 0xFFFBBF24;
            case INFO:
            default: return 0xFF60A5FA;
        }
    }
}
