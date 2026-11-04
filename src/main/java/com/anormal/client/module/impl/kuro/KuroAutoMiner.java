package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.InputForcer;
import com.anormal.client.util.kuro.KuroSwapLimit;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * kuro {@code AutoMiner} port: yoqing paytda qaragan yo'nalishingizda to'g'ri
 * tunnel qazadi — BOSIB TURILGAN break tugmasi bilan (default: o'ng tugma)
 * va juda yuradi.
 *
 * <p>G'or xavfsizligi: har qadamdan oldin tunnel aro kengaytirilgan quti
 * tekshiriladi. Minerli o'zi qazmagan har qanday ochiq maydon (havo/suv/lava)
 * g'or hisoblanadi: barcha tugmalar bo'shatiladiba to'xtaydi (yoki o'chadi).</p>
 *
 * <p>Pickaxe pase: o'rinli pickaxe qolmasa "/ah <search>" yuboradi, lore ichidan
 * narxni o'qib eng arzonini bosadi, tasdiqlash tugmasini bosadi va qazishga
 * qaytadi.</p>
 */
public class KuroAutoMiner extends Module {
    private static final Pattern PRICE = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbBtT])?");

    private final BooleanSetting rightClick = new BooleanSetting("Break with right click", true);
    private final BooleanSetting walk = new BooleanSetting("Walk forward", true);
    private final BooleanSetting aim = new BooleanSetting("Aim at block", true);
    // ---- cave safety ----
    private final BooleanSetting caveCheck = new BooleanSetting("Cave detection", true);
    private final NumberSetting lookAhead = new NumberSetting("Look ahead (blocks)", "How many blocks of tunnel to check", 3.0, 1.0, 6.0, 1.0);
    private final NumberSetting margin = new NumberSetting("Cave margin (blocks)", "How far beside/above to check", 1.0, 1.0, 3.0, 1.0);
    private final BooleanSetting disableOnCave = new BooleanSetting("Switch off on cave", false);
    // ---- pickaxe upkeep ----
    private final BooleanSetting autoBuy = new BooleanSetting("Auto buy pickaxe (/ah)", true);
    private final NumberSetting minDurability = new NumberSetting("Replace at durability", "Spares", 3.0, 0.0, 50.0, 1.0);
    private final NumberSetting clickDelay = new NumberSetting("GUI delay (ticks)", "Wait before clicking", 10.0, 3.0, 40.0, 1.0);
    private final NumberSetting maxTries = new NumberSetting("Buy attempts", "Retry limit", 3.0, 1.0, 10.0, 1.0);
    /** kuro "AH search (type + Enter)" text sozlamasi turi Anormal'da yo'q: tayyor tanlov berilibdi. */
    private final ModeSetting search = new ModeSetting("AH search", "What the auto-buyer searches in /ah",
            "diamond pickaxe", "diamond pickaxe", "iron pickaxe", "golden pickaxe", "netherite pickaxe");

    private enum State { MINE, SEND, WAIT_AH, WAIT_CONFIRM, VERIFY }
    private State state = State.MINE;
    private int timer, attempts, screenAge, lastContainer = -1, ahContainer = -1;
    private Direction dir;
    private final Set<Long> dug = new HashSet<>();
    private final Set<Long> seenSolid = new HashSet<>();
    private boolean cavePaused;

    public KuroAutoMiner() {
        super("Auto Miner", "Holds the break key and tunnels forward in the direction you faced", Category.KURO);
        addSetting(rightClick);
        addSetting(walk);
        addSetting(aim);
        addSetting(caveCheck);
        addSetting(lookAhead);
        addSetting(margin);
        addSetting(disableOnCave);
        addSetting(autoBuy);
        addSetting(minDurability);
        addSetting(clickDelay);
        addSetting(maxTries);
        addSetting(search);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) { setEnabled(false); return; }
        dir = mc.player.getHorizontalFacing();
        dug.clear(); seenSolid.clear();
        state = State.MINE; attempts = 0; timer = 0; cavePaused = false;
        say("Mining " + dir.name().toLowerCase(Locale.ROOT) + ". Right click = "
                + (rightClick.isEnabled() ? "break" : "off (left click)") + ".", Formatting.GRAY);
    }

    @Override public void onDisable() { releaseKeys(); }

    private void say(String m, Formatting c) {
        mc.execute(() -> { if (mc.player != null) mc.player.sendMessage(Text.literal("[Auto Miner] " + m).formatted(c), false); });
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        InputForcer.use = false;
        InputForcer.attack = false;
        InputForcer.forward = false;
        try { net.minecraft.client.option.KeyBinding.updatePressedStates(); } catch (Throwable ignored) {}
    }

    private void fail(String why) {
        say(why, Formatting.RED);
        if (mc.currentScreen instanceof HandledScreen<?> && mc.player != null) mc.player.closeHandledScreen();
        setEnabled(false);
    }

    // ===================================================================== tick

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || dir == null) { releaseKeys(); return; }
        if (mc.player.isSpectator() || KuroAutoEat.eating()) { releaseKeys(); return; }

        if (state != State.MINE) { buyTick(); return; }

        if (mc.currentScreen != null) { releaseKeys(); return; }          // some other menu is open: wait

        // ---- pickaxe ----
        int toolState = ensurePickaxe();
        if (toolState == 0) {                                       // none usable
            releaseKeys();
            if (!autoBuy.isEnabled()) { fail("No usable pickaxe left and Auto buy is off."); return; }
            state = State.SEND; timer = 0; attempts = 0;
            say("Pickaxe is gone, buying one from /ah...", Formatting.YELLOW);
            return;
        }
        if (toolState == 2) { releaseKeys(); return; }              // swap refused this tick, retry

        // ---- cave safety ----
        BlockPos origin = mc.player.getBlockPos();
        trackDug(origin);
        if (caveCheck.isEnabled()) {
            String hazard = findHazard(origin);
            if (hazard != null) {
                releaseKeys();
                if (!cavePaused) {
                    cavePaused = true;
                    say("Stopped: " + hazard, Formatting.RED);
                }
                if (disableOnCave.isEnabled()) setEnabled(false);
                return;
            }
            cavePaused = false;
        }

        // ---- aim + hold keys ----
        BlockPos feet1 = origin.offset(dir, 1);
        BlockPos head1 = feet1.up();
        boolean clear1 = !solid(feet1) && !solid(head1);
        if (aim.isEnabled()) aimAt(origin);

        if (rightClick.isEnabled()) {
            InputForcer.use = true;          // break via right click
            InputForcer.attack = false;
        } else {
            InputForcer.attack = true;       // break via left click
            InputForcer.use = false;
        }
        if (walk.isEnabled() && clear1) InputForcer.forward = true; else InputForcer.forward = false;
        if (dug.size() > 4000) { dug.clear(); seenSolid.clear(); }
    }

    // ===================================================================== mining helpers

    private boolean solid(BlockPos p) {
        BlockState st = mc.world.getBlockState(p);
        return st.getFluidState().isEmpty() && !st.getCollisionShape(mc.world, p).isEmpty();
    }

    /** Remembers which tunnel blocks we broke ourselves, so our own tunnel never looks like a cave. */
    private void trackDug(BlockPos origin) {
        for (int f = 1; f <= 2; f++) for (int v = 0; v <= 1; v++) {
            BlockPos p = origin.offset(dir, f).up(v);
            long k = p.asLong();
            if (solid(p)) seenSolid.add(k);
            else if (seenSolid.contains(k)) dug.add(k);
        }
    }

    /** Returns a description of the first open space found around the tunnel ahead, or null when it is all rock. */
    private String findHazard(BlockPos origin) {
        int L = lookAhead.getValue().intValue(), M = margin.getValue().intValue();
        Direction side = dir.rotateYClockwise();
        for (int f = 0; f <= L + 1; f++) {
            for (int lat = -M; lat <= M; lat++) {
                for (int v = -M; v <= 1 + M; v++) {
                    boolean tunnel = lat == 0 && (v == 0 || v == 1);
                    if (tunnel && f == 0) continue;                   // where we stand
                    BlockPos p = origin.offset(dir, f).offset(side, lat).up(v);
                    if (solid(p) || dug.contains(p.asLong())) continue;
                    BlockState st = mc.world.getBlockState(p);
                    String what = !st.getFluidState().isEmpty() ? "liquid" : "open air / cave";
                    return what + " " + (tunnel ? "ahead in the tunnel" : "next to the tunnel") + " at "
                            + p.getX() + ", " + p.getY() + ", " + p.getZ();
                }
            }
        }
        return null;
    }

    private void aimAt(BlockPos origin) {
        BlockPos[] order = {
                origin.offset(dir, 1), origin.offset(dir, 1).up(),
                origin.offset(dir, 2), origin.offset(dir, 2).up() };
        BlockPos t = order[0];
        for (BlockPos p : order) if (solid(p)) { t = p; break; }
        Vec3d eye = mc.player.getEyePos();
        Vec3d c = Vec3d.ofCenter(t);
        double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
        mc.player.setYaw((float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90f);
        mc.player.setPitch((float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI));
    }

    // ===================================================================== pickaxe handling

    private boolean usablePickaxe(ItemStack st) {
        if (st.isEmpty() || !st.isIn(ItemTags.PICKAXES)) return false;
        if (!st.isDamageable()) return true;
        return st.getMaxDamage() - st.getDamage() > minDurability.getValue().intValue();
    }

    /** 1 = a usable pickaxe is in hand, 0 = none anywhere, 2 = working on it (swap pending). */
    private int ensurePickaxe() {
        var inv = mc.player.getInventory();
        if (usablePickaxe(inv.getStack(inv.getSelectedSlot()))) return 1;
        for (int i = 0; i < 9; i++) {
            if (usablePickaxe(inv.getStack(i))) return KuroSwapLimit.select(i) ? 1 : 2;
        }
        for (int i = 9; i < 36; i++) {
            if (usablePickaxe(inv.getStack(i))) {
                if (!KuroSwapLimit.take()) return 2;
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, inv.getSelectedSlot(),
                        SlotActionType.SWAP, mc.player);
                return 2;
            }
        }
        return 0;
    }

    private boolean haveUsablePickaxe() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) if (usablePickaxe(inv.getStack(i))) return true;
        return false;
    }

    // ===================================================================== /ah buying

    private void buyTick() {
        timer++;
        net.minecraft.screen.ScreenHandler menu = mc.currentScreen instanceof HandledScreen<?> ? mc.player.currentScreenHandler : null;
        if (menu != null && menu.syncId != lastContainer) { lastContainer = menu.syncId; screenAge = 0; }
        if (menu != null) screenAge++;

        switch (state) {
            case SEND: {
                if (mc.currentScreen != null) { mc.player.closeHandledScreen(); return; }
                if (timer < 5) return;
                mc.player.networkHandler.sendChatCommand("ah " + search.getValue());
                state = State.WAIT_AH; timer = 0; ahContainer = -1;
                return;
            }
            case WAIT_AH: {
                if (menu == null || menu.slots.size() <= 36) {
                    if (timer > 100) retryOrFail("The /ah menu never opened.");
                    return;
                }
                if (ahContainer == -1) ahContainer = menu.syncId;
                if (screenAge < clickDelay.getValue().intValue()) return;       // let the server fill the menu
                int size = menu.slots.size() - 36, best = -1;
                double bestPrice = Double.MAX_VALUE;
                boolean sawPickaxe = false;
                for (int i = 0; i < size; i++) {
                    ItemStack st = menu.getSlot(i).getStack();
                    if (st.isEmpty() || !st.isIn(ItemTags.PICKAXES)) continue;
                    sawPickaxe = true;
                    double price = priceOf(st);
                    if (price >= 0 && price < bestPrice) { bestPrice = price; best = i; }
                }
                if (best < 0) {
                    if (timer > 100) retryOrFail(sawPickaxe ? "Found pickaxes but could not read a price from their lore."
                            : "No pickaxes listed for \"" + search.getValue() + "\".");
                    return;
                }
                mc.interactionManager.clickSlot(menu.syncId, best, 0, SlotActionType.PICKUP, mc.player);
                say("Clicked the cheapest pickaxe (" + fmt(bestPrice) + ").", Formatting.GRAY);
                state = State.WAIT_CONFIRM; timer = 0;
                return;
            }
            case WAIT_CONFIRM: {
                if (menu != null && menu.syncId != ahContainer && menu.slots.size() > 36) {
                    if (screenAge < Math.max(3, clickDelay.getValue().intValue() / 2)) return;
                    int size = menu.slots.size() - 36, pick = -1;
                    for (int i = 0; i < size && pick < 0; i++) if (isConfirm(menu.getSlot(i).getStack())) pick = i;
                    if (pick >= 0) {
                        mc.interactionManager.clickSlot(menu.syncId, pick, 0, SlotActionType.PICKUP, mc.player);
                        state = State.VERIFY; timer = 0;
                    } else if (timer > 60) retryOrFail("Could not find the confirm button in the purchase menu.");
                    return;
                }
                if (timer > 30) { state = State.VERIFY; timer = 0; }   // no confirm menu showed up: maybe it bought instantly
                return;
            }
            case VERIFY: {
                if (haveUsablePickaxe()) {
                    if (mc.currentScreen != null) mc.player.closeHandledScreen();
                    say("Bought a pickaxe. Back to mining.", Formatting.GREEN);
                    state = State.MINE; timer = 0; cavePaused = false;
                    return;
                }
                if (timer > 50) retryOrFail("Clicked buy but no pickaxe arrived (not enough money, or someone sniped it?).");
                return;
            }
            default: { }
        }
    }

    private void retryOrFail(String why) {
        attempts++;
        if (attempts >= maxTries.getValue().intValue()) { fail(why + " Giving up."); return; }
        say(why + " Retrying (" + attempts + "/" + maxTries.getValue().intValue() + ").", Formatting.YELLOW);
        if (mc.currentScreen != null) mc.player.closeHandledScreen();
        state = State.SEND; timer = 0;
    }

    private boolean isConfirm(ItemStack st) {
        if (st.isEmpty()) return false;
        String name = st.getName().getString().toLowerCase(Locale.ROOT);
        if (name.contains("confirm") || name.contains("accept") || name.equals("buy")) return true;
        String id = Registries.ITEM.getId(st.getItem()).getPath();
        return id.startsWith("lime_") || id.startsWith("green_") || id.equals("emerald_block");
    }

    /** Price parsed from the item's lore ("$1,500", "$2.5K", "$1M"...), -1 when none found. Lines that say "price" win. */
    private double priceOf(ItemStack st) {
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore == null) return -1;
        double any = -1;
        for (Text line : lore.lines()) {
            String s = line.getString();
            Matcher m = PRICE.matcher(s);
            if (!m.find()) continue;
            double v;
            try { v = Double.parseDouble(m.group(1).replace(",", "")); } catch (NumberFormatException e) { continue; }
            String suf = m.group(2);
            if (suf != null) switch (Character.toLowerCase(suf.charAt(0))) {
                case 'k' -> v *= 1_000d;
                case 'm' -> v *= 1_000_000d;
                case 'b' -> v *= 1_000_000_000d;
                case 't' -> v *= 1_000_000_000_000d;
                default -> { }
            }
            if (s.toLowerCase(Locale.ROOT).contains("price")) return v;
            if (any < 0) any = v;
        }
        return any;
    }

    private static String fmt(double v) { return v >= 1000 ? String.format("$%,.0f", v) : "$" + (long) v; }
}
