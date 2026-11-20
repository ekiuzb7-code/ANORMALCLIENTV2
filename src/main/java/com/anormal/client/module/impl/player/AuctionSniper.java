package com.anormal.client.module.impl.player;

import com.anormal.client.lucid.StringSetting;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ChatFeed;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auction house sniper that reads the auction GUI itself.
 *
 * <p>Most servers (DonutSMP, Hypixel-style, generic Essentials auction plugins) show listings in
 * a chest GUI with a price in the item lore. That is the interface this module watches: it reads
 * every listing in the open container, parses the price out of the name/lore, compares it to the
 * rolling average it has seen for the same item and reports - or buys - the ones that are far
 * below that average. Because it works off the visible container instead of hardcoded chat
 * formats, it behaves the same on any server that opens a GUI.</p>
 *
 * <p>Buying uses the normal {@code clickSlot} path: one shift-click (quick move) plus optional
 * confirm clicks, throttled so the server never sees a burst. Chat scanning is a second, optional
 * source for servers that list auctions in chat instead of a GUI.</p>
 */
public class AuctionSniper extends Module {

    private static final Pattern AMOUNT = Pattern.compile(
            "(\\d[\\d\\s,.'\\u2019]{0,15})(?:\\s*([kKmMbBtT\\u043A\\u041A]{1,3}))?"
                    + "(?![a-zA-Z\\u0430-\\u044f\\u0410-\\u042f])");
    private static final Pattern TRAILING_WORD = Pattern.compile(
            "^\\s*(thousand|million|mil|mio|billion|bil|trillion)");
    private static final Pattern STRONG_PRICE = Pattern.compile(
            "(price|cost|buy|bin|narx|sotib|coins|money|\\$|\\u20ac|\\u00a3|\\u20bd|\\u00a5|\\u26c3)");
    private static final Pattern NOISE_LINE = Pattern.compile(
            "(ends in|time left|expires?|duration|remaining|bids?\\s*:|bid\\s*:|seller|rarity|durability"
                    + "|chance|level\\s*\\d|uses\\s*\\d|left\\s*:|(?<![0-9,.'])\\d+\\s*(d|h|m|s)\\b)");
    private static final Pattern BID_LINE = Pattern.compile("(^|\\s)(bid|bids|highest bid|current bid)\\b");
    private static final Pattern PRICE_WORD = Pattern.compile("(price|cost|narx|sotib olish|sotib)");
    private static final Pattern BIN_WORD = Pattern.compile("(buy it now|buy now|instant buy|\\bbin\\b)");
    private static final Pattern CHAT_LISTING = Pattern.compile(
            "^\\s*(?:\\[[^\\]]{1,12}]\\s*)?([A-Za-z][A-Za-z0-9' _-]{2,32}?)\\s*(?:x\\s*\\d+)?\\s*"
                    + "(?:-|:|\\u2192|for|costs?|price:?)\\s*(?:\\$|\\u20ac|\\u00a3|coins?|money)?\\s*"
                    + "([0-9][0-9,\\.]*)\\s*([kKmMbB])?", Pattern.CASE_INSENSITIVE);

    private static final class Seen {
        double total;
        int count;

        double average() {
            return count <= 0 ? -1.0 : total / count;
        }

        void add(double value) {
            total += value;
            count++;
        }
    }

    private static final class Deal {
        final String name;
        final double price;
        final double discount;
        final int slotId;

        Deal(String name, double price, double discount, int slotId) {
            this.name = name;
            this.price = price;
            this.discount = discount;
            this.slotId = slotId;
        }
    }

    // ---- Scanner -------------------------------------------------------
    public final ModeSetting preset = new ModeSetting("Preset",
            "Command/format preset; Custom uses your own command below",
            "Generic", "Generic", "DonutSMP", "Hypixel SkyBlock", "Custom");
    public final StringSetting scanCommand = new StringSetting("Scan Command",
            "Command used to open page {page} of the auction house", "ah {page}", 64);
    public final BooleanSetting autoScan = new BooleanSetting("Auto Scan", "Send the scan command by itself", false);
    public final BooleanSetting scanOnEnable = new BooleanSetting("Scan On Enable", "Open the auction house when the module turns on", false);
    public final NumberSetting pages = new NumberSetting("Pages", "How many pages a scan cycle covers", 3.0, 1.0, 20.0, 1.0);
    public final NumberSetting pageDelay = new NumberSetting("Page Delay", "Ticks between page commands", 12.0, 4.0, 100.0, 1.0);

    // ---- Filters -------------------------------------------------------
    public final NumberSetting minPrice = new NumberSetting("Min Price", "Ignore listings below this price", 0.0, 0.0, 100000000.0, 1.0);
    public final NumberSetting maxPrice = new NumberSetting("Max Price", "Ignore listings above this price", 1000000.0, 1.0, 100000000.0, 100.0);
    public final NumberSetting dealThreshold = new NumberSetting("Deal Threshold %",
            "Minimum discount against the seen average to count as a deal", 25.0, 0.0, 90.0, 1.0);
    public final BooleanSetting onlyWatched = new BooleanSetting("Only Watched",
            "Only report items that match the watch list", false);
    public final StringSetting watchList = new StringSetting("Watch List",
            "Comma separated keywords (empty = everything)", "", 160);
    public final StringSetting blackList = new StringSetting("Black List",
            "Comma separated keywords to never report or buy", "", 160);
    public final BooleanSetting bidsFallback = new BooleanSetting("Bids Fallback",
            "Read a bid price when a listing has no buy-now price", true);
    public final BooleanSetting debugPrices = new BooleanSetting("Debug Prices",
            "Print every parsed listing price to local chat (calibration on a new server)", false);

    // ---- Buying --------------------------------------------------------
    public final BooleanSetting autoBuy = new BooleanSetting("Auto Buy", "Quick-move the best deal out of the GUI", false);
    public final NumberSetting autoBuyMax = new NumberSetting("Auto Buy Max", "Never pay more than this automatically", 50000.0, 1.0, 100000000.0, 100.0);
    public final NumberSetting confirmClicks = new NumberSetting("Confirm Clicks", "Extra clicks for servers with a confirm screen", 1.0, 0.0, 3.0, 1.0);
    public final NumberSetting clickDelay = new NumberSetting("Click Delay", "Ticks between buy and confirm clicks", 4.0, 1.0, 40.0, 1.0);
    public final BooleanSetting closeAfterBuy = new BooleanSetting("Close After Buy", "Close the GUI once the buy clicks are done", true);

    // ---- Output --------------------------------------------------------
    public final ModeSetting notifyMode = new ModeSetting("Notify", "Where deals are reported", "Notification", "Notification", "Chat", "Both");
    public final BooleanSetting sound = new BooleanSetting("Sound", "Click on a deal", true);
    public final BooleanSetting chatScan = new BooleanSetting("Chat Scan",
            "Also parse chat listings (servers without a GUI auction)", false);
    public final BooleanSetting showHud = new BooleanSetting("Show HUD", "Draw the scanner card", true);
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 6.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 520.0, 0.0, 1080.0, 1.0);

    private final Map<String, Seen> history = new HashMap<>();
    private final ChatFeed.Listener listener = this::onChatLine;
    private boolean listening;
    private int page = 1;
    private int delay;
    private int confirmLeft;
    private String lastDeal = "none";
    private double lastDealPrice = -1.0;
    private int listingsSeen;
    private int dealsFound;
    private int lastBoughtSlot = -1;
    private String lastDebugName = "";
    private long lastDebugMs;

    public AuctionSniper() {
        super("AuctionSniper", "Finds and optionally buys underpriced auction listings by reading the auction GUI", Category.PLAYER);
        addSetting(preset);
        addSetting(scanCommand);
        addSetting(autoScan);
        addSetting(scanOnEnable);
        addSetting(pages);
        addSetting(pageDelay);
        addSetting(minPrice);
        addSetting(maxPrice);
        addSetting(dealThreshold);
        addSetting(onlyWatched);
        addSetting(watchList);
        addSetting(blackList);
        addSetting(bidsFallback);
        addSetting(debugPrices);
        addSetting(autoBuy);
        addSetting(autoBuyMax);
        addSetting(confirmClicks);
        addSetting(clickDelay);
        addSetting(closeAfterBuy);
        addSetting(notifyMode);
        addSetting(sound);
        addSetting(chatScan);
        addSetting(showHud);
        addSetting(posX);
        addSetting(posY);
    }

    @Override
    public void onEnable() {
        if (!listening) {
            ChatFeed.add(listener);
            listening = true;
        }
        applyPreset();
        page = 1;
        delay = 0;
        confirmLeft = 0;
        lastBoughtSlot = -1;
        if (scanOnEnable.isEnabled()) scanNextPage();
    }

    @Override
    public void onDisable() {
        if (listening) {
            ChatFeed.remove(listener);
            listening = false;
        }
    }

    private void applyPreset() {
        try {
            String mode = preset.getValue();
            if (mode == null) return;
            if (mode.equals("DonutSMP")) scanCommand.set("ah {page}");
            else if (mode.equals("Hypixel SkyBlock")) scanCommand.set("ah");
            else if (mode.equals("Generic")) scanCommand.set("ah {page}");
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  Scanning
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (delay > 0) delay--;

        if (confirmLeft > 0 && delay <= 0) {
            clickConfirm();
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        boolean guiOpen = mc.currentScreen != null && handler instanceof GenericContainerScreenHandler;
        if (guiOpen) {
            scanContainer((GenericContainerScreenHandler) handler);
            return;
        }

        if (autoScan.isEnabled() && delay <= 0) {
            scanNextPage();
        }
    }

    private void scanNextPage() {
        try {
            String command = scanCommand.get();
            if (command == null || command.isBlank()) return;
            String pageText = String.valueOf(page);
            String full = command.contains("{page}") ? command.replace("{page}", pageText) : command;
            if (mc.player != null && mc.player.networkHandler != null) {
                mc.player.networkHandler.sendChatCommand(stripSlash(full));
            }
            page++;
            if (page > (int) pages.getValue().intValue()) page = 1;
            delay = (int) pageDelay.getValue().intValue();
        } catch (Throwable ignored) {
        }
    }

    private String stripSlash(String command) {
        String trimmed = command.trim();
        while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        return trimmed;
    }

    private void scanContainer(GenericContainerScreenHandler handler) {
        List<Deal> deals = new ArrayList<>();
        listingsSeen = 0;
        try {
            for (Slot slot : handler.slots) {
                if (slot == null) continue;
                if (slot.inventory instanceof PlayerInventory) continue;
                ItemStack stack = slot.getStack();
                if (stack == null || stack.isEmpty()) continue;
                String name = stack.getName().getString();
                if (name == null || name.isEmpty()) continue;
                double price = priceOf(stack, name);
                if (price <= 0.0) continue;
                listingsSeen++;
                String key = itemKey(stack, name);
                Seen seen = history.computeIfAbsent(key, ignored -> new Seen());
                double average = seen.average();
                double discount = 0.0;
                if (average > 0.0) discount = 1.0 - price / average;
                if (!acceptable(name, price)) {
                    if (average < 0.0) seen.add(price);
                    continue;
                }
                if (discount * 100.0 >= dealThreshold.getValue() && average > 0.0) {
                    deals.add(new Deal(name, price, discount, slot.id));
                }
                seen.add(price);
            }
        } catch (Throwable ignored) {
        }

        if (deals.isEmpty()) return;
        deals.sort((a, b) -> Double.compare(b.discount, a.discount));
        Deal best = deals.get(0);
        dealsFound += deals.size();
        lastDeal = trim(best.name, 18);
        lastDealPrice = best.price;
        report(best);

        if (autoBuy.isEnabled() && best.price <= autoBuyMax.getValue()) {
            buy(best);
        }
    }

    private void buy(Deal deal) {
        if (mc.player == null || mc.interactionManager == null) return;
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!(handler instanceof GenericContainerScreenHandler)) return;
        try {
            mc.interactionManager.clickSlot(handler.syncId, deal.slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            lastBoughtSlot = deal.slotId;
            confirmLeft = (int) confirmClicks.getValue().intValue();
            delay = (int) clickDelay.getValue().intValue();
            if (confirmLeft <= 0 && closeAfterBuy.isEnabled()) closeScreen();
        } catch (Throwable ignored) {
        }
    }

    private void clickConfirm() {
        if (mc.player == null || mc.interactionManager == null) {
            confirmLeft = 0;
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!(handler instanceof GenericContainerScreenHandler)) {
            confirmLeft = 0;
            return;
        }
        try {
            int target = findConfirmSlot((GenericContainerScreenHandler) handler);
            if (target < 0) {
                confirmLeft = 0;
                if (closeAfterBuy.isEnabled()) closeScreen();
                return;
            }
            mc.interactionManager.clickSlot(handler.syncId, target, 0, SlotActionType.QUICK_MOVE, mc.player);
            confirmLeft--;
            delay = (int) clickDelay.getValue().intValue();
            if (confirmLeft <= 0 && closeAfterBuy.isEnabled()) closeScreen();
        } catch (Throwable ignored) {
            confirmLeft = 0;
        }
    }

    /** Confirm GUIs put the "confirm" item near the middle-top; fall back to any emerald/lime pane. */
    private int findConfirmSlot(GenericContainerScreenHandler handler) {
        try {
            for (Slot slot : handler.slots) {
                if (slot == null || slot.inventory instanceof PlayerInventory) continue;
                ItemStack stack = slot.getStack();
                if (stack == null || stack.isEmpty()) continue;
                String name = stack.getName().getString().toLowerCase(Locale.ROOT);
                if (name.contains("confirm") || name.contains("accept") || name.contains("buy now")
                        || name.contains("yes")) {
                    return slot.id;
                }
            }
            for (Slot slot : handler.slots) {
                if (slot == null || slot.inventory instanceof PlayerInventory) continue;
                ItemStack stack = slot.getStack();
                if (stack == null || stack.isEmpty()) continue;
                String id = Registries.ITEM.getId(stack.getItem()).getPath();
                if (id.contains("emerald") || id.contains("lime")) return slot.id;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private void closeScreen() {
        try {
            if (mc.player != null) mc.player.closeHandledScreen();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  Price / filter helpers
    // ------------------------------------------------------------------

    private double priceOf(ItemStack stack, String name) {
        double best = -1.0;
        int bestScore = Integer.MAX_VALUE;
        try {
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore != null && lore.lines() != null) {
                for (Text line : lore.lines()) {
                    String text = clean(line.getString());
                    if (text.isEmpty()) continue;
                    double value = priceIn(text);
                    if (value <= 0.0) continue;
                    int score = scoreOf(text);
                    if (score < bestScore) {
                        bestScore = score;
                        best = value;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Some servers put the price in the display name ("Diamond Sword $1,200"); a bare
        // "x64" must never count, so the name is only read when it carries a currency marker.
        String plainName = clean(name);
        String lowerName = plainName.toLowerCase(Locale.ROOT);
        if (best <= 0.0 && (STRONG_PRICE.matcher(lowerName).find()
                || PRICE_WORD.matcher(lowerName).find())) {
            double namePrice = priceIn(plainName);
            if (namePrice > 0.0) {
                best = namePrice;
                bestScore = 30;
            }
        }

        if (best > 0.0) debug(name, best, bestScore);
        return best;
    }

    /** Strips colour codes, non-breaking spaces and repeated whitespace from plain text. */
    private String clean(String raw) {
        if (raw == null) return "";
        String text = raw.replaceAll("\u00a7.", " ").replaceAll("&[0-9a-fk-orA-FK-OR]", " ");
        text = text.replace('\u00a0', ' ').replaceAll("\\s{2,}", " ").trim();
        return text;
    }

    private boolean isNoise(String lower) {
        if (STRONG_PRICE.matcher(lower).find() || PRICE_WORD.matcher(lower).find()) return false;
        return NOISE_LINE.matcher(lower).find();
    }

    /** Score of a lore line: lower wins, so an explicit buy-now price beats a bid line. */
    private int scoreOf(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int score;
        if (BIN_WORD.matcher(lower).find()) score = 0;
        else if (PRICE_WORD.matcher(lower).find()) score = 5;
        else if (lower.contains("buy")) score = 10;
        else if (STRONG_PRICE.matcher(lower).find()) score = 20;
        else if (BID_LINE.matcher(lower).find()) score = 40;
        else score = 60;
        if (lower.contains("each") || lower.contains("per item") || lower.contains("per unit")
                || lower.contains("unit price")) {
            score += 30;
        }
        return score;
    }

    /**
     * Price on one line of lore (or the display name).
     *
     * <p>Covers the real formats seen in auction GUIs: Hypixel "Price: 1,234,567 coins" and
     * "Buy it now", DonutSMP "$1,250,000" / "$1.2k", zAuctionHouse "Price: $1,000", European
     * "1.234.567,89", space-grouped "1 200 000", CIS "1\u043a" / "1\u043a\u043a" and word
     * suffixes ("1.2 million"). Numbers followed by bids, time units, "x64" or a percent are
     * skipped, so "Bids: 3" next to "1,000 coins" cannot win.</p>
     */
    private double priceIn(String text) {
        if (text == null || text.isEmpty()) return -1.0;
        String lower = text.toLowerCase(Locale.ROOT);
        if (isNoise(lower)) return -1.0;

        boolean bidLine = BID_LINE.matcher(lower).find();
        boolean buyPrice = BIN_WORD.matcher(lower).find() || PRICE_WORD.matcher(lower).find()
                || lower.contains("buy");
        if (bidLine && !buyPrice && !bidsFallback.isEnabled()) return -1.0;
        boolean context = buyPrice || bidLine || STRONG_PRICE.matcher(lower).find()
                || PRICE_WORD.matcher(lower).find();

        Matcher matcher = AMOUNT.matcher(lower);
        while (matcher.find()) {
            String digits = matcher.group(1);
            if (digits == null) continue;
            if (matcher.start() > 0) {
                char before = lower.charAt(matcher.start() - 1);
                if (before == 'x' || before == '\u00d7') continue; // "x64" stack count
            }
            String tail = lower.substring(matcher.end());
            if (isCountOrTime(tail)) continue;
            double value = amount(digits, matcher.group(2), tail);
            if (value <= 0.0) continue;
            String suffix = matcher.group(2);
            if (!context && value < 1000.0 && (suffix == null || suffix.isEmpty())) {
                continue; // a bare small number (level, count) is not a price
            }
            return value;
        }
        return -1.0;
    }

    /** True when the number is followed by something that is not a price: bids, x64, 2d, 15%. */
    private boolean isCountOrTime(String tail) {
        String t = tail.trim();
        if (t.isEmpty()) return false;
        if (t.startsWith("bids") || t.startsWith("bid")) return true;
        if (t.startsWith("x") && t.length() > 1 && Character.isDigit(t.charAt(1))) return true;
        if (t.startsWith("%") || t.startsWith("percent")) return true;
        return t.matches("^(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours"
                + "|d|day|days|w|week|weeks|level|lvl|uses|left)\\b.*");
    }

    /** Normalises digit grouping and applies k/m/b (plus Cyrillic \u043a) multipliers. */
    private double amount(String digits, String suffix, String tail) {
        if (digits == null) return -1.0;
        String raw = digits.replace("'", "").replace("\u2019", "").trim();
        if (raw.isEmpty()) return -1.0;
        double value;
        try {
            value = Double.parseDouble(normalizeSeparators(raw));
        } catch (Throwable t) {
            return -1.0;
        }
        double multiplier = 1.0;
        if (suffix != null && !suffix.isEmpty()) {
            multiplier = suffixMultiplier(suffix);
        } else if (tail != null && !tail.isEmpty()) {
            Matcher word = TRAILING_WORD.matcher(tail.toLowerCase(Locale.ROOT));
            if (word.find()) multiplier = wordMultiplier(word.group(1));
        }
        value *= multiplier;
        if (value <= 0.0 || value > 1.0e15) return -1.0;
        return value;
    }

    /**
     * "1,234,567" and "1.234.567" are thousands groups, "1.5" and "1,25" are decimals, and
     * "1.234.567,89" mixes both. Getting this wrong turns a 1,500 coin listing into 15,000.
     */
    private String normalizeSeparators(String raw) {
        String s = raw.replace(" ", "").replace("'", "").replace("\u2019", "");
        if (s.indexOf(',') >= 0 && s.indexOf('.') >= 0) {
            int lastSep = Math.max(s.lastIndexOf('.'), s.lastIndexOf(','));
            String intPart = s.substring(0, lastSep).replace(".", "").replace(",", "");
            String decPart = s.substring(lastSep + 1);
            if (decPart.length() <= 2) return intPart + "." + decPart;
            return (intPart + decPart).replace(".", "");
        }
        if (s.matches("\\d{1,3}([.,]\\d{3})+")) return s.replace(".", "").replace(",", "");
        if (s.matches("\\d+[.,]\\d{1,2}")) return s.replace(",", ".");
        return s.replace(".", "").replace(",", "");
    }

    private double suffixMultiplier(String suffix) {
        String s = suffix.toLowerCase(Locale.ROOT);
        if (s.equals("\u043a\u043a") || s.equals("kk")) return 1_000_000.0;
        if (s.equals("\u043a\u043a\u043a") || s.equals("kkk")) return 1_000_000_000.0;
        char first = s.charAt(0);
        if (first == 'k' || first == '\u043a') return 1_000.0;
        if (first == 'm' || first == '\u043c') return 1_000_000.0;
        if (first == 'b') return 1_000_000_000.0;
        if (first == 't') return 1_000_000_000_000.0;
        return 1.0;
    }

    private double wordMultiplier(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.startsWith("thousand")) return 1_000.0;
        if (w.startsWith("mil") || w.startsWith("mio")) return 1_000_000.0;
        if (w.startsWith("bil")) return 1_000_000_000.0;
        if (w.startsWith("tril")) return 1_000_000_000_000.0;
        return 1.0;
    }

    private void debug(String name, double price, int score) {
        if (!debugPrices.isEnabled() || mc.inGameHud == null) return;
        long now = System.currentTimeMillis();
        if (name.equals(lastDebugName) && now - lastDebugMs < 3000L) return;
        lastDebugName = name;
        lastDebugMs = now;
        try {
            mc.inGameHud.getChatHud().addMessage(Text.literal(
                    "[AH] " + trim(name, 28) + " -> " + formatPrice(price) + " (score " + score + ")"));
        } catch (Throwable ignored) {
        }
    }

    private boolean acceptable(String name, double price) {
        if (price < minPrice.getValue() || price > maxPrice.getValue()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        String black = blackList.get();
        if (black != null && !black.isBlank()) {
            for (String word : black.split(",")) {
                String keyword = word.trim().toLowerCase(Locale.ROOT);
                if (!keyword.isEmpty() && lower.contains(keyword)) return false;
            }
        }
        if (onlyWatched.isEnabled()) {
            String watch = watchList.get();
            if (watch == null || watch.isBlank()) return false;
            for (String word : watch.split(",")) {
                String keyword = word.trim().toLowerCase(Locale.ROOT);
                if (!keyword.isEmpty() && lower.contains(keyword)) return true;
            }
            return false;
        }
        return true;
    }

    private String itemKey(ItemStack stack, String name) {
        try {
            return Registries.ITEM.getId(stack.getItem()).toString() + "|" + name.toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return name.toLowerCase(Locale.ROOT);
        }
    }

    // ------------------------------------------------------------------
    //  Chat source
    // ------------------------------------------------------------------

    private void onChatLine(String plain) {
        if (!chatScan.isEnabled() || plain == null || plain.isEmpty()) return;
        try {
            Matcher matcher = CHAT_LISTING.matcher(plain);
            if (!matcher.find()) return;
            String name = matcher.group(1);
            String number = matcher.group(2);
            if (name == null || number == null) return;
            double price = amount(number, matcher.group(3), "");
            if (price <= 0.0) return;
            if (price <= 0.0 || !acceptable(name, price)) return;
            Seen seen = history.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new Seen());
            double average = seen.average();
            seen.add(price);
            if (average <= 0.0) return;
            double discount = 1.0 - price / average;
            if (discount * 100.0 < dealThreshold.getValue()) return;
            lastDeal = trim(name, 18);
            lastDealPrice = price;
            dealsFound++;
            report(new Deal(name, price, discount, -1));
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  Output
    // ------------------------------------------------------------------

    private void report(Deal deal) {
        String price = formatPrice(deal.price);
        String text = trim(deal.name, 22) + " " + price + "  -" + Math.round(deal.discount * 100.0) + "%";
        String mode = notifyMode.getValue();
        try {
            if (mode == null || mode.equals("Notification") || mode.equals("Both")) {
                NotificationManager.info("Auction deal", text);
            }
            if ((mode != null && (mode.equals("Chat") || mode.equals("Both"))) && mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[AH] " + text));
            }
        } catch (Throwable ignored) {
        }
        if (sound.isEnabled() && mc.player != null) {
            try {
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.5f, 1.9f);
            } catch (Throwable ignored) {
            }
        }
    }

    private String formatPrice(double value) {
        if (value >= 1_000_000.0) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 1_000.0) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private String trim(String text, int max) {
        if (text == null) return "";
        String clean = text.trim();
        return clean.length() > max ? clean.substring(0, max) : clean;
    }

    // ------------------------------------------------------------------
    //  HUD
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showHud.isEnabled() || mc.player == null || mc.textRenderer == null) return;
        int w = 196, h = 58;
        int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
        int x = at[0], y = at[1];
        int accent = ThemeManager.getAccentColor();

        HudStyle.panel(context, x, y, w, h, accent);
        HudStyle.title(context, mc.textRenderer, "Auction Sniper", x + 10, y + 7);
        String state = autoScan.isEnabled() ? ("page " + page) : "manual";
        int sw = mc.textRenderer.getWidth(state);
        RenderUtils.drawText(context, mc.textRenderer, state, x + w - 10 - sw, y + 7, 0xFF6E7482, false);

        String best = lastDealPrice > 0.0 ? (lastDeal + " " + formatPrice(lastDealPrice)) : "no deal yet";
        RenderUtils.drawText(context, mc.textRenderer, trim(best, 26), x + 10, y + 20,
                lastDealPrice > 0.0 ? 0xFF4ADE80 : 0xFF8A8A96, false);

        String stats = "seen " + listingsSeen + ", deals " + dealsFound;
        RenderUtils.drawText(context, mc.textRenderer, stats, x + 10, y + 31, 0xFFC7CBD6, false);

        String filter = "max " + formatPrice(maxPrice.getValue()) + ", -" + Math.round(dealThreshold.getValue()) + "%";
        RenderUtils.drawText(context, mc.textRenderer, filter, x + 10, y + 42, 0xFF5E6470, false);

        float frac = (float) Math.min(1.0, Math.max(0.0, dealThreshold.getValue() / 90.0));
        HudStyle.bar(context, x + 10, y + 52, w - 20, 2, frac, accent);
    }
}
