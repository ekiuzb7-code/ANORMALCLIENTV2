package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ActionSetting;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.HeaderSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.util.staff.StaffAlertManager;
import com.anormal.client.util.staff.StaffConfidence;
import com.anormal.client.util.staff.StaffDetectionType;
import com.anormal.client.util.staff.StaffHistory;
import com.anormal.client.util.staff.StaffListManager;
import com.anormal.client.util.staff.StaffObservation;
import com.anormal.client.util.staff.MsgCompletionObserver;
import com.anormal.client.util.staff.StaffTracker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Staff Detector.
 *
 * <p>Observes known staff accounts and possible hidden presence using only data the client already
 * received: tab-list entries (with their game mode when the server sends it) and player entities in
 * the client world (with their real invisibility state). It never claims a player is nearby, hidden
 * or staff without evidence, never invents a UUID or a position, and its completion check only reads
 * suggestions the server returned for the user's own tab completion.</p>
 */
public class StaffDetector extends Module {

    // ---- Detection -----------------------------------------------------
    public final HeaderSetting detectionHeader = new HeaderSetting("Observations");
    public final BooleanSetting autoDetect = new BooleanSetting("Auto-detect staff",
            "Detect staff from tab-list ranks, spectator mode and invisible players without a list", true);
    public final BooleanSetting staffAlerts = new BooleanSetting("Staff alerts",
            "Alert when a staff-list account is observed", true);
    public final BooleanSetting spectatorAlerts = new BooleanSetting("Spectator alerts",
            "Alert when a staff-list account is reliably observed in spectator mode", true);
    public final BooleanSetting invisibleAlerts = new BooleanSetting("Invisible nearby alerts",
            "Alert when an invisible player entity is present nearby", true);
    public final NumberSetting nearbyRange = new NumberSetting("Nearby range",
            "Proximity radius in blocks for positional alerts", 31.0, 1.0, 128.0, 1.0);
    public final BooleanSetting checkMsgCompletions = new BooleanSetting("Check /msg completions",
            "Inspect /msg tab-completion suggestions you request yourself (never sends requests)", false);
    public final BooleanSetting msgCompletionAlerts = new BooleanSetting("/msg completion alerts",
            "Show suggestions that match a staff-list account", false);

    // ---- Staff matching ------------------------------------------------
    public final HeaderSetting matchingHeader = new HeaderSetting("Staff matching");
    public final BooleanSetting matchByUuid = new BooleanSetting("Match by UUID",
            "Match observed accounts by UUID when the server exposes it", true);
    public final BooleanSetting matchByUsername = new BooleanSetting("Match by exact username",
            "Fall back to an exact username comparison", true);
    public final BooleanSetting allowUsernameOnly = new BooleanSetting("Allow username-only matches",
            "Show name matches even when the UUID is not verified", true);
    public final BooleanSetting showUnknownInvisible = new BooleanSetting("Show unknown invisible entities",
            "Report an invisible entity whose identity the client does not know", true);

    // ---- Notifications -------------------------------------------------
    public final HeaderSetting notificationsHeader = new HeaderSetting("Notifications");
    public final BooleanSetting warningCards = new BooleanSetting("Warning cards",
            "Draw the observed-presence panel", true);
    public final BooleanSetting localSound = new BooleanSetting("Local notification sound",
            "Play a short local chime for staff matches", false);
    public final ModeSetting minConfidence = new ModeSetting("Minimum confidence",
            "Lowest confidence that may reach the HUD", "Medium", "Low", "Medium", "High");
    public final NumberSetting alertCooldown = new NumberSetting("Alert cooldown",
            "Seconds before the same player can alert again", 10.0, 1.0, 120.0, 1.0);
    public final BooleanSetting repeatOnStateChange = new BooleanSetting("Repeat alert on state change",
            "Allow a new alert when the evidence changes (for example joins spectator mode)", true);
    public final BooleanSetting showUnconfirmed = new BooleanSetting("Show unconfirmed evidence",
            "Include low-confidence and unmatched observations", true);

    // ---- Display -------------------------------------------------------
    public final HeaderSetting displayHeader = new HeaderSetting("Display");
    public final NumberSetting posX = new NumberSetting("Pos X",
            "HUD X position, also used by the HUD editor", 6.0, 0.0, 3840.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y",
            "HUD Y position, also used by the HUD editor", 6.0, 0.0, 2160.0, 1.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show distance",
            "Append the measured distance to a row", false);
    public final BooleanSetting showCoordinates = new BooleanSetting("Show coordinates",
            "Keep coordinates in the history view", true);
    public final BooleanSetting showEvidence = new BooleanSetting("Show evidence source",
            "Draw the evidence source line under the rows", false);
    public final BooleanSetting showConfidence = new BooleanSetting("Show confidence",
            "Append the confidence level to a row", false);
    public final BooleanSetting showTimestamp = new BooleanSetting("Show timestamp",
            "Append the observation time to a row", false);
    public final BooleanSetting compact = new BooleanSetting("Compact notifications",
            "Smaller rows with no separator styling", false);
    public final NumberSetting visibleRows = new NumberSetting("Visible rows",
            "Rows shown before the '+N more' summary", 4.0, 1.0, 10.0, 1.0);

    // ---- Advanced ------------------------------------------------------
    public final HeaderSetting advancedHeader = new HeaderSetting("Advanced");
    public final BooleanSetting deduplicate = new BooleanSetting("Deduplicate alerts",
            "One alert per player and detection type", true);
    public final NumberSetting historySize = new NumberSetting("History size",
            "Maximum stored evidence records", 100.0, 10.0, 500.0, 10.0);
    public final BooleanSetting historyEnabled = new BooleanSetting("Evidence history",
            "Record observations in the history view", true);
    public final BooleanSetting persistEvidence = new BooleanSetting("Persist evidence",
            "Save the staff list and evidence to disk", true);
    public final BooleanSetting useServerLists = new BooleanSetting("Use server-specific staff lists",
            "Only apply entries labelled with the current server (global entries always apply)", true);
    public final BooleanSetting debug = new BooleanSetting("Debug logging",
            "Print observations and rejection reasons to chat", false);

    // ---- Staff identity -------------------------------------------------
    public final HeaderSetting identityHeader = new HeaderSetting("Staff identity");
    public final ActionSetting staffListAction = new ActionSetting("Staff list / evidence",
            "Add usernames or UUIDs for the current server",
            () -> mc.setScreen(new com.anormal.client.gui.StaffListScreen(this)));

    private final StaffTracker tracker = new StaffTracker();
    private final StaffAlertManager alerts = new StaffAlertManager();
    private final StaffHistory history = new StaffHistory();
    private long lastChime;
    private long lastDebug;

    public StaffDetector() {
        super("Staff Detector", "Observe known staff and possible hidden presence", Category.RENDER);

        addSetting(identityHeader);
        addSetting(staffListAction);
        addSetting(detectionHeader);
        addSetting(autoDetect);
        addSetting(staffAlerts);
        addSetting(spectatorAlerts);
        addSetting(invisibleAlerts);
        addSetting(nearbyRange);
        addSetting(checkMsgCompletions);
        addSetting(msgCompletionAlerts);

        addSetting(matchingHeader);
        addSetting(matchByUuid);
        addSetting(matchByUsername);
        addSetting(allowUsernameOnly);
        addSetting(showUnknownInvisible);

        addSetting(notificationsHeader);
        addSetting(warningCards);
        addSetting(localSound);
        addSetting(minConfidence);
        addSetting(alertCooldown);
        addSetting(repeatOnStateChange);
        addSetting(showUnconfirmed);

        addSetting(displayHeader);
        addSetting(posX);
        addSetting(posY);
        addSetting(showDistance);
        addSetting(showCoordinates);
        addSetting(showEvidence);
        addSetting(showConfidence);
        addSetting(showTimestamp);
        addSetting(compact);
        addSetting(visibleRows);

        addSetting(advancedHeader);
        addSetting(deduplicate);
        addSetting(historySize);
        addSetting(historyEnabled);
        addSetting(persistEvidence);
        addSetting(useServerLists);
        addSetting(debug);
    }

    public StaffHistory history() {
        return history;
    }

    @Override
    public void onEnable() {
        alerts.clear();
        StaffListManager.ensureLoaded();
    }

    @Override
    public void onDisable() {
        alerts.clear();
        StaffListManager.flush();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        MsgCompletionObserver.drain(this);
        tracker.tick(this);
        alerts.tick(15000L);
    }

    public StaffConfidence minConfidence() {
        return StaffConfidence.byName(minConfidence.getValue());
    }

    /** Called by the tracker and the completion observer for every accepted piece of evidence. */
    public void handle(StaffObservation observation) {
        if (observation == null) return;
        if (!observation.staffListMatched && !showUnconfirmed.isEnabled()) return;

        history.add(observation, historySize.getValue().intValue(), historyEnabled.isEnabled());

        if (!deduplicate.isEnabled()) {
            alerts.submit(observation, 0L, true);
        } else if (!alerts.submit(observation,
                (long) (alertCooldown.getValue() * 1000.0), repeatOnStateChange.isEnabled())) {
            debug(observation, "suppressed by dedupe/cooldown");
            return;
        }

        if (localSound.isEnabled() && observation.staffListMatched) playChime();
        debug(observation, "alert");
    }

    private void playChime() {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastChime < 250L) return;
        lastChime = now;
        try {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.55f,
                    observationPitch());
        } catch (Throwable ignored) {
        }
    }

    private float observationPitch() {
        return 1.5f;
    }

    private void debug(StaffObservation observation, String reason) {
        if (!debug.isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastDebug < 300L) return;
        lastDebug = now;
        chat(String.format(Locale.ROOT,
                "%s | %s | type=%s | conf=%s | verified=%s | staff=%s | dist=%s | %s",
                observation.displayName(), observation.type.evidenceSource(),
                observation.type.name(), observation.confidence.display(),
                observation.identityVerified, observation.staffListMatched,
                observation.distanceText(), reason));
    }

    private void chat(String message) {
        if (mc.inGameHud == null) return;
        try {
            mc.inGameHud.getChatHud().addMessage(
                    net.minecraft.text.Text.literal("[StaffDetector] " + message));
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  HUD - reference layout
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!warningCards.isEnabled()) return;
        if (mc.player == null || mc.textRenderer == null) return;
        if (mc.options.hudHidden) return;

        List<StaffAlertManager.Alert> active = alerts.active();

        int limit = Math.max(1, visibleRows.getValue().intValue());
        List<StaffAlertManager.Alert> rows = new ArrayList<>(active);
        if (rows.size() > limit) rows = new ArrayList<>(rows.subList(0, limit));

        boolean spectatorEvidence = false;
        for (StaffAlertManager.Alert alert : active) {
            if (alert.observation.type == StaffDetectionType.SPECTATOR) {
                spectatorEvidence = true;
                break;
            }
        }
        int hidden = Math.max(0, active.size() - rows.size());

        String headerName = "Staff Detector";
        String headerTail = " | Observed presence";
        String moreText = "+" + hidden + " more in staff list";
        String verdict = spectatorEvidence ? "Watching: yes (spectator evidence)"
                : (!rows.isEmpty() ? "Watching: staff observed" : "Watching: no staff observed");
        int verdictColor = spectatorEvidence ? 0xFFF87171
                : (!rows.isEmpty() ? 0xFFFBBF24 : 0xFF868A99);
        String emptyLine = rows.isEmpty() ? "No staff presence observed" : null;
        String footer = spectatorEvidence ? "Spectator evidence observed" : "Hidden presence is unknown";
        String evidenceLine = showEvidence.isEnabled() && !rows.isEmpty()
                ? rows.get(0).observation.evidenceSource : null;

        int width = mc.textRenderer.getWidth(headerName) + mc.textRenderer.getWidth(headerTail) + 20;
        for (StaffAlertManager.Alert alert : rows) {
            width = Math.max(width, mc.textRenderer.getWidth(rowPrefix(alert))
                    + mc.textRenderer.getWidth(" | ") + mc.textRenderer.getWidth(rowText(alert)) + 20);
        }
        width = Math.max(width, mc.textRenderer.getWidth(moreText) + 20);
        width = Math.max(width, mc.textRenderer.getWidth(verdict) + 20);
        if (emptyLine != null) width = Math.max(width, mc.textRenderer.getWidth(emptyLine) + 20);
        width = Math.max(width, mc.textRenderer.getWidth(footer) + 20);
        if (evidenceLine != null) width = Math.max(width, mc.textRenderer.getWidth(evidenceLine) + 20);
        width = Math.min(380, Math.max(210, width));

        int height = 20 + rows.size() * 11 + (hidden > 0 ? 11 : 0)
                + (emptyLine != null ? 11 : 0) + 11
                + (evidenceLine != null ? 11 : 0) + 12;
        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();
        int accent = spectatorEvidence ? 0xFFF87171 : 0xFFFBBF24;

        HudStyle.panel(context, x, y, width, height, accent);
        RenderUtils.drawBorder(context, x, y, x + width, y + height, 1,
                (accent & 0x00FFFFFF) | 0x38000000);

        int textY = y + 6;
        RenderUtils.drawText(context, mc.textRenderer, headerName, x + 10, textY, 0xFFEDEFF5, true);
        RenderUtils.drawText(context, mc.textRenderer, headerTail,
                x + 10 + mc.textRenderer.getWidth(headerName), textY, 0xFF868A99, false);
        textY += 13;

        for (StaffAlertManager.Alert alert : rows) {
            String prefix = rowPrefix(alert);
            RenderUtils.drawText(context, mc.textRenderer, prefix, x + 10, textY,
                    alert.accent(), compact.isEnabled());
            int px = x + 10 + mc.textRenderer.getWidth(prefix);
            RenderUtils.drawText(context, mc.textRenderer, " | ", px, textY, 0xFF6E7280, false);
            int dx = px + mc.textRenderer.getWidth(" | ");
            RenderUtils.drawText(context, mc.textRenderer, rowText(alert), dx, textY, 0xFFB9BDC8, false);
            textY += 11;
        }

        if (hidden > 0) {
            RenderUtils.drawText(context, mc.textRenderer, moreText, x + 10, textY, 0xFF868A99, false);
            textY += 11;
        }
        if (emptyLine != null) {
            RenderUtils.drawText(context, mc.textRenderer, emptyLine, x + 10, textY, 0xFFB9BDC8, false);
            textY += 11;
        }
        if (evidenceLine != null) {
            RenderUtils.drawText(context, mc.textRenderer, evidenceLine, x + 10, textY, 0xFF6E7280, false);
            textY += 11;
        }
        RenderUtils.drawText(context, mc.textRenderer, verdict, x + 10, textY, verdictColor,
                spectatorEvidence);
        textY += 11;
        RenderUtils.drawText(context, mc.textRenderer, footer, x + 10, textY, 0xFF868A99, false);
    }

    private String rowPrefix(StaffAlertManager.Alert alert) {
        if (!alert.observation.staffListMatched) {
            return alert.observation.username != null ? "Hidden player" : "Unknown identity";
        }
        return alert.observation.type == StaffDetectionType.SPECTATOR
                ? "Staff spectator" : "Staff member";
    }

    private String rowText(StaffAlertManager.Alert alert) {
        StringBuilder sb = new StringBuilder(alert.detail());
        StaffObservation observation = alert.observation;
        if (showDistance.isEnabled() && observation.distance >= 0.0) {
            sb.append("  ").append(observation.distanceText());
        }
        if (showConfidence.isEnabled()) {
            sb.append("  ").append(observation.confidence.display());
        }
        if (showTimestamp.isEnabled()) {
            sb.append("  ").append(String.format(Locale.ROOT, "%tT",
                    new java.util.Date(observation.timestamp)));
        }
        return sb.toString();
    }
}
