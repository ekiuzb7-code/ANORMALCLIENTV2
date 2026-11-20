package com.anormal.client.gui;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.*;
import com.anormal.client.theme.Theme;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.theme.FrozenFx;
import com.anormal.client.theme.GlassFx;
import com.anormal.client.theme.BrutalismFx;
import com.anormal.client.util.CategoryIcon;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ClickGuiScreen extends Screen {
    private Category currentCategory = Category.COMBAT;

    /** When true, the sidebar shows only the ported-source tabs (Spectre / Kuro). */
    private boolean sourceView = false;

    /** The tabs to render: everything normally, or just the two ported-source tabs. */
    private Category[] visibleCategories() {
        if (sourceView) {
            return new Category[] { Category.SPECTRE, Category.KURO };
        }
        return java.util.Arrays.stream(Category.values())
                .filter(c -> c != Category.SPECTRE && c != Category.KURO)
                .toArray(Category[]::new);
    }
    private String searchQuery = "";
    private boolean searchFocused = false;
    private KeybindSetting listeningSetting = null;
    private NumberSetting draggingSlider = null;
    private ColorSetting editingColor = null;
    private int editDrag = 0; // 0 none, 1 R, 2 G, 3 B

    private int scrollOffset = 0;
    private com.anormal.client.setting.ModeSetting expandedMode = null;

    /** The free-text setting currently accepting keystrokes, or null. */
    private com.anormal.client.lucid.StringSetting editingString = null;
    private MultiSelectSetting expandedMulti = null;
    private Object draggingRange = null;      // IntRangeSetting | DoubleRangeSetting
    private boolean draggingRangeMax = false;

    // Scrollbar drag state (mouse-drag scrolling, not just the wheel).
    private boolean draggingScrollbar = false;
    private int lastMaxScroll = 0;
    private int sbTrackY = 0, sbTrackH = 0, sbThumbH = 0, sbHitX = 0, sbHitW = 0;

    private String hoveredTooltip = null;
    private Float lockedScale = null;

    private static final int RANGE_TRACK_W = 60;
    private static final int RANGE_TRACK_OFFSET = 110;

    public ClickGuiScreen() {
        super(Text.literal("Anormal Client GUI"));
    }

    /**
     * Where the GUI was left. {@code ClientSettings > Remember Position} (on by default) makes
     * the next opening start there, so closing the GUI next to a module in some section and
     * pressing the key again lands you on that same module instead of the top of the list.
     */
    private static Category lastCategory = null;
    private static int lastScroll = 0;

    @Override
    protected void init() {
        super.init();
        // Frozen: entrance shockwave + icy chime. init() also runs on window resize, so the sound
        // only plays for a real open.
        boolean freshOpen = FrozenFx.markOpened() & GlassFx.markOpened() & BrutalismFx.markOpened();
        if (freshOpen) playThemeOpenSound();
        var cs = ModuleManager.getModule(com.anormal.client.module.impl.client.ClientSettings.class);
        boolean remember = cs == null || cs.rememberPosition.isEnabled();
        if (remember && lastCategory != null) {
            boolean wasSourceView = lastCategory == Category.SPECTRE || lastCategory == Category.KURO;
            sourceView = wasSourceView;
            currentCategory = lastCategory;
            scrollOffset = Math.max(0, lastScroll);
        } else {
            sourceView = false;
            currentCategory = Category.COMBAT;
            scrollOffset = 0;
        }
        // Panel geometry and the open settings window survive a full client restart too.
        loadVapeLayout();
        if (vapeSettingsModule == null) vapeSettingsModule = lastVapeSettingsModule;
        // Restore the list position of the section that is about to be shown.
        vapeScroll = Math.max(0, vapeScrollMemory.getOrDefault(currentCategory, 0));
        if (vapeSettingsModule != null) {
            vapeSetScroll = Math.max(0, vapeSetScrollMemory.getOrDefault(vapeSettingsModule.getName(), 0));
        }
    }

    @Override
    public void removed() {
        super.removed();
        playThemeCloseSound();
        var cs = ModuleManager.getModule(com.anormal.client.module.impl.client.ClientSettings.class);
        if (cs == null || cs.rememberPosition.isEnabled()) {
            lastCategory = currentCategory;
            lastScroll = scrollOffset;
        }
        lastVapeSettingsModule = vapeSettingsModule;
        saveVapeLayout();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        hoveredTooltip = null;
        // Leaving the Developer tab re-arms its entrance animation for the next visit.
        if (currentCategory != Category.DEVELOPER) devPanelStart = 0L;

        com.anormal.client.module.impl.client.ClientSettings cs = ModuleManager.getModule(com.anormal.client.module.impl.client.ClientSettings.class);
        boolean blur = cs != null ? cs.blurBackground.isEnabled() : true;
        boolean showTooltips = cs != null ? cs.showTooltips.isEnabled() : false;
        float targetScale = cs != null ? cs.guiScale.getValue().floatValue() : 1.0f;
        if (targetScale < 0.4f || targetScale > 2.0f) targetScale = 1.0f;

        // While dragging guiScale, lock interaction scale snapshot to eliminate feedback jitter / oscillation
        if (draggingSlider != null && cs != null && draggingSlider == cs.guiScale) {
            if (lockedScale == null) {
                lockedScale = targetScale;
            }
        } else {
            lockedScale = null;
        }

        float scale = (lockedScale != null) ? lockedScale : targetScale;

        // 1. Background dark tint / blur
        if (blur) {
            context.fill(0, 0, width, height, 0xD5080B12);
            context.fill(0, 0, width, height / 3, 0x25141E30);
            context.fill(0, height * 2 / 3, width, height, 0x30050810);
        } else {
            context.fill(0, 0, width, height, 0x55000000);
        }

        float cx = width / 2.0f;
        float cy = height / 2.0f;
        int mX = (int) ((mouseX - cx) / scale + cx);
        int mY = (int) ((mouseY - cy) / scale + cy);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, cy);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-cx, -cy);

        int guiWidth = Math.min(520, width - 20);
        int guiHeight = Math.min(300, height - 20);
        int guiX = (width - guiWidth) / 2;
        int guiY = (height - guiHeight) / 2;

        Theme activeTheme = ThemeManager.getActiveTheme();

        // Vape V4 theme is a different ClickGUI, not a recolour: sidebar card + floating module
        // panel + settings panel, laid out like Vape's own frames.
        if (usesVapeLayout()) {
            renderVapeGui(context, mX, mY);
            context.getMatrices().popMatrix();
            // Ambient layer over the whole window, in screen space so it keeps one size.
            FrozenFx.snowfield(context, width, height);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // 2. Main Window Frame
        ThemeManager.renderWindow(context, guiX, guiY, guiWidth, guiHeight, "Anormal Client");

        // 4. Top Header: ANORMAL wordmark fully ABOVE the divider line.
        try {
            context.drawTexturedQuad(
                    com.anormal.client.module.impl.legit.Watermark.guiLogo(),
                    guiX + 12, guiY + 0, guiX + 108, guiY + 24, 0.0f, 1.0f, 0.0f, 1.0f);
        } catch (Throwable ignored) {
            RenderUtils.drawText(context, textRenderer, "§lANORMAL", guiX + 12, guiY + 6, 0xFFFFFFFF, true);
        }

        if (activeTheme == Theme.BRUTALISM) {
            int tagX = guiX + 114;
            int tagY = guiY + 5;
            RenderUtils.fill(context, tagX, tagY, tagX + 78, tagY + 15, ColorUtils.rgba(255, 230, 0, 255));
            RenderUtils.drawBorder(context, tagX, tagY, tagX + 78, tagY + 15, 1, 0xFF000000);
            RenderUtils.drawText(context, textRenderer, "BRUTALIST V4", tagX + 4, tagY + 3, 0xFF000000, false);
        }

        // Search Bar at Top Right
        int searchW = 110;
        int searchX = guiX + guiWidth - searchW - 12;
        int searchY = guiY + 6;
        RenderUtils.fill(context, searchX, searchY, searchX + searchW, searchY + 14, ColorUtils.rgba(10, 10, 10, 200));
        RenderUtils.drawBorder(context, searchX, searchY, searchX + searchW, searchY + 14, 1, searchFocused ? ThemeManager.getAccentColor() : ThemeManager.getBorderColor());
        String searchDisplay = searchQuery.isEmpty() ? (searchFocused ? "§f|" : "§7" + com.anormal.client.i18n.I18n.tr("Search...")) : (searchFocused ? searchQuery + "§6|" : searchQuery);
        RenderUtils.drawText(context, textRenderer, searchDisplay, searchX + 5, searchY + 3, 0xFFCCCCCC, true);

        // Edit HUD Button
        int hudBtnW = 64;
        int hudBtnX = searchX - hudBtnW - 6;
        int hudBtnY = guiY + 6;
        boolean hudHovered = mX >= hudBtnX && mX <= hudBtnX + hudBtnW && mY >= hudBtnY && mY <= hudBtnY + 14;
        RenderUtils.fill(context, hudBtnX, hudBtnY, hudBtnX + hudBtnW, hudBtnY + 14, hudHovered ? ThemeManager.getAccentColor() : ThemeManager.getSurfaceColor(200));
        RenderUtils.drawBorder(context, hudBtnX, hudBtnY, hudBtnX + hudBtnW, hudBtnY + 14, 1, ThemeManager.getBorderColor());
        CategoryIcon.gear(context, hudBtnX + 5, hudBtnY + 3, 8, hudHovered ? 0xFFFFFFFF : 0xFFDDDDDD);
        RenderUtils.drawText(context, textRenderer, com.anormal.client.i18n.I18n.tr("Edit HUD"), hudBtnX + 16, hudBtnY + 3, hudHovered ? 0xFFFFFFFF : 0xFFDDDDDD, true);

        // Waypoints Manager Button
        int wpBtnW = 78;
        int wpBtnX = hudBtnX - wpBtnW - 6;
        int wpBtnY = guiY + 6;
        boolean wpHovered = mX >= wpBtnX && mX <= wpBtnX + wpBtnW && mY >= wpBtnY && mY <= wpBtnY + 14;
        RenderUtils.fill(context, wpBtnX, wpBtnY, wpBtnX + wpBtnW, wpBtnY + 14, wpHovered ? ThemeManager.getAccentColor() : ThemeManager.getSurfaceColor(200));
        RenderUtils.drawBorder(context, wpBtnX, wpBtnY, wpBtnX + wpBtnW, wpBtnY + 14, 1, ThemeManager.getBorderColor());
        CategoryIcon.waypoint(context, wpBtnX + 5, wpBtnY + 3, 8, wpHovered ? 0xFFFFFFFF : 0xFFDDDDDD);
        RenderUtils.drawText(context, textRenderer, com.anormal.client.i18n.I18n.tr("Waypoints"), wpBtnX + 16, wpBtnY + 3, wpHovered ? 0xFFFFFFFF : 0xFFDDDDDD, true);

        // 5. Left Sidebar (Category Navigation Tabs)
        int sidebarWidth = 110;
        int sidebarX = guiX + 8;
        int sidebarY = guiY + 26;
        int sidebarHeight = guiHeight - 34;

        RenderUtils.fill(context, sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, ThemeManager.getSurfaceColor(170));
        RenderUtils.drawBorder(context, sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, 1, ThemeManager.getBorderColor());

        Category[] categories = visibleCategories();
        int tabY = sidebarY + 4;
        // Reserve room for the source-view toggle pinned to the sidebar's bottom.
        int srcBtnH = 16;
        int srcBtnY = sidebarY + sidebarHeight - srcBtnH - 6;
        int tabReserve = 6 + srcBtnH + 6;
        int tabPitch = Math.max(15, Math.min(28, (sidebarHeight - tabReserve) / categories.length));
        int tabHeight = tabPitch - 4;

        for (Category category : categories) {
            boolean isSelected = category == currentCategory;
            boolean isHovered = mX >= sidebarX + 4 && mX <= sidebarX + sidebarWidth - 4 && mY >= tabY && mY <= tabY + tabHeight;

            int tabBg = isSelected ? ThemeManager.getAccentColor() : (isHovered ? ThemeManager.getCardColor(false, true) : ThemeManager.getSurfaceColor(140));
            int textColor = isSelected
                    ? ((activeTheme == Theme.BRUTALISM || activeTheme == Theme.VAPE || activeTheme == Theme.FROZEN) ? 0xFF000000 : 0xFFFFFFFF)
                    : (isHovered ? 0xFFE0E0E0 : ThemeManager.getTextColor(false));

            RenderUtils.fill(context, sidebarX + 4, tabY, sidebarX + sidebarWidth - 4, tabY + tabHeight, tabBg);
            int borderCol = isSelected ? (activeTheme == Theme.BRUTALISM ? 0xFFFFFFFF : ThemeManager.getAccentColor()) : ThemeManager.getBorderColor();
            RenderUtils.drawBorder(context, sidebarX + 4, tabY, sidebarX + sidebarWidth - 4, tabY + tabHeight, activeTheme == Theme.BRUTALISM ? 2 : 1, borderCol);

            int tabIcon = Math.max(7, Math.min(10, tabHeight - 5));
            CategoryIcon.draw(context, category, sidebarX + 9, tabY + Math.max(0, (tabHeight - tabIcon) / 2), tabIcon, textColor);
            RenderUtils.drawText(context, textRenderer, category.getName(), sidebarX + 12 + tabIcon, tabY + Math.max(1, (tabHeight - 8) / 2), textColor, true);

            tabY += tabPitch;
        }

        // Source-view toggle (▼): collapses the sidebar to the Spectre / Kuro tabs.
        boolean srcHovered = mX >= sidebarX + 4 && mX <= sidebarX + sidebarWidth - 4 && mY >= srcBtnY && mY <= srcBtnY + srcBtnH;
        int srcBg = sourceView ? ThemeManager.getAccentColor() : (srcHovered ? ThemeManager.getCardColor(false, true) : ThemeManager.getSurfaceColor(140));
        int srcText = sourceView
                ? ((activeTheme == Theme.BRUTALISM || activeTheme == Theme.VAPE || activeTheme == Theme.FROZEN) ? 0xFF000000 : 0xFFFFFFFF)
                : (srcHovered ? 0xFFE0E0E0 : ThemeManager.getTextColor(false));
        int srcBorder = sourceView ? (activeTheme == Theme.BRUTALISM ? 0xFFFFFFFF : ThemeManager.getAccentColor()) : ThemeManager.getBorderColor();
        RenderUtils.fill(context, sidebarX + 4, srcBtnY, sidebarX + sidebarWidth - 4, srcBtnY + srcBtnH, srcBg);
        RenderUtils.drawBorder(context, sidebarX + 4, srcBtnY, sidebarX + sidebarWidth - 4, srcBtnY + srcBtnH, activeTheme == Theme.BRUTALISM ? 2 : 1, srcBorder);
        int triY = srcBtnY + Math.max(1, (srcBtnH - 5) / 2);
        if (sourceView) {
            CategoryIcon.triangleUp(context, sidebarX + 10, triY, 5, 5, srcText);
        } else {
            CategoryIcon.triangleDown(context, sidebarX + 10, triY, 5, 5, srcText);
        }
        RenderUtils.drawText(context, textRenderer, com.anormal.client.i18n.I18n.tr(sourceView ? "Main" : "Sources"), sidebarX + 19, srcBtnY + Math.max(1, (srcBtnH - 8) / 2), srcText, true);

        // 6. Right Main Panel (Scrollable Module List)
        int contentX = sidebarX + sidebarWidth + 6;
        int contentY = guiY + 26;
        int contentWidth = guiWidth - sidebarWidth - 22;
        int contentHeight = guiHeight - 34;

        RenderUtils.fill(context, contentX, contentY, contentX + contentWidth, contentY + contentHeight, ThemeManager.getSurfaceColor(150));
        RenderUtils.drawBorder(context, contentX, contentY, contentX + contentWidth, contentY + contentHeight, 1, ThemeManager.getBorderColor());

        // The Developer tab is an information panel, not a module list.
        boolean infoOnly = currentCategory.isInfoOnly();

        List<Module> categoryModules = infoOnly ? java.util.List.of()
                : ModuleManager.getModulesByCategory(currentCategory).stream()
                .filter(m -> searchQuery.isEmpty()
                        || m.getName().toLowerCase().contains(searchQuery.toLowerCase())
                        || com.anormal.client.i18n.I18n.tr(m.getName()).toLowerCase().contains(searchQuery.toLowerCase()))
                .toList();

        // Calculate max scroll height (visible settings only)
        int totalContentHeight = 6;
        for (Module m : categoryModules) {
            totalContentHeight += 26;
            if (m.isExpanded()) {
                for (Setting<?> s : m.getSettings()) {
                    if (s.isVisible()) totalContentHeight += 20;
                    if (s == expandedMode && s instanceof com.anormal.client.setting.ModeSetting em) {
                        totalContentHeight += em.getModes().size() * 16;
                    }
                    if (s == expandedMulti && s instanceof MultiSelectSetting ms) {
                        totalContentHeight += ms.getOptions().size() * 16;
                    }
                }
                if (m instanceof com.anormal.client.module.impl.world.XRay) totalContentHeight += 22;
                if (m instanceof com.anormal.client.module.impl.client.ActionWheel) totalContentHeight += 22;
            }
        }
        int maxScroll = Math.max(0, totalContentHeight - contentHeight + 10);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Enable Scissor to prevent any module from overflowing borders
        try {
            context.enableScissor(contentX + 1, contentY + 1, contentX + contentWidth - 1, contentY + contentHeight - 1);
        } catch (Throwable ignored) {}

        int modY = contentY + 6 - scrollOffset;

        for (Module module : categoryModules) {
            renderModuleCard(context, module, contentX + 6, modY, contentWidth - 16, mouseX, mouseY);
            modY += 26;

            if (module.isExpanded()) {
                for (Setting<?> setting : module.getSettings()) {
                    if (!setting.isVisible()) continue;
                    renderSetting(context, module, setting, contentX + 16, modY, contentWidth - 36, mouseX, mouseY);
                    modY += 20;
                    // Expanded option list under a mode row: click an option to pick it
                    if (setting == expandedMode && setting instanceof com.anormal.client.setting.ModeSetting em) {
                        for (String opt : em.getModes()) {
                            boolean sel = opt.equals(em.getValue());
                            boolean hov = mouseX >= contentX + 26 && mouseX <= contentX + 6 + contentWidth - 26
                                    && mouseY >= modY && mouseY <= modY + 14;
                            RenderUtils.fill(context, contentX + 26, modY, contentX + 6 + contentWidth - 26, modY + 14,
                                    sel ? ThemeManager.getAccentColor() : (hov ? ColorUtils.rgba(35, 40, 55, 220) : ColorUtils.rgba(16, 18, 24, 220)));
                            CategoryIcon.radio(context, contentX + 32, modY + 4, 7, sel, sel ? 0xFFFFFFFF : 0xFFBBBBBB);
                            RenderUtils.drawText(context, textRenderer, com.anormal.client.i18n.I18n.tr(opt),
                                    contentX + 43, modY + 3, sel ? 0xFFFFFFFF : 0xFFBBBBBB, true);
                            modY += 16;
                        }
                    }
                    // Expanded option list under a multi-select row: each option toggles.
                    if (setting == expandedMulti && setting instanceof MultiSelectSetting ms) {
                        for (String opt : ms.getOptions()) {
                            boolean sel = ms.isSelected(opt);
                            boolean hov = mouseX >= contentX + 26 && mouseX <= contentX + 6 + contentWidth - 26
                                    && mouseY >= modY && mouseY <= modY + 14;
                            RenderUtils.fill(context, contentX + 26, modY, contentX + 6 + contentWidth - 26, modY + 14,
                                    sel ? ThemeManager.getAccentColor() : (hov ? ColorUtils.rgba(35, 40, 55, 220) : ColorUtils.rgba(16, 18, 24, 220)));
                            CategoryIcon.checkbox(context, contentX + 32, modY + 4, 7, sel, sel ? 0xFFFFFFFF : 0xFFBBBBBB);
                            RenderUtils.drawText(context, textRenderer, opt,
                                    contentX + 43, modY + 3, sel ? 0xFFFFFFFF : 0xFFBBBBBB, true);
                            modY += 16;
                        }
                    }
                }
                if (module instanceof com.anormal.client.module.impl.world.XRay) {
                    int btnX = contentX + 16;
                    int btnW = contentWidth - 36;
                    boolean hov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= modY && mouseY <= modY + 18;
                    RenderUtils.fill(context, btnX, modY, btnX + btnW, modY + 18, hov ? ThemeManager.getAccentColor() : ColorUtils.rgba(20, 24, 32, 220));
                    RenderUtils.drawBorder(context, btnX, modY, btnX + btnW, modY + 18, 1, ThemeManager.getBorderColor());
                    CategoryIcon.search(context, btnX + 8, modY + 5, 8, 0xFFFFFFFF);
                    RenderUtils.drawText(context, textRenderer, com.anormal.client.i18n.I18n.tr("Select Blocks..."), btnX + 19, modY + 5, 0xFFFFFFFF, true);
                    modY += 22;
                }
                if (module instanceof com.anormal.client.module.impl.client.ActionWheel) {
                    int btnX = contentX + 16;
                    int btnW = contentWidth - 36;
                    boolean hov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= modY && mouseY <= modY + 18;
                    RenderUtils.fill(context, btnX, modY, btnX + btnW, modY + 18, hov ? ThemeManager.getAccentColor() : ColorUtils.rgba(20, 24, 32, 220));
                    RenderUtils.drawBorder(context, btnX, modY, btnX + btnW, modY + 18, 1, ThemeManager.getBorderColor());
                    CategoryIcon.gear(context, btnX + 8, modY + 5, 8, 0xFFFFFFFF);
                    RenderUtils.drawText(context, textRenderer, com.anormal.client.i18n.I18n.tr("Customize Wheel Actions..."), btnX + 19, modY + 5, 0xFFFFFFFF, true);
                    modY += 22;
                }
            }
        }

        // Disable Scissor after rendering modules
        try {
            context.disableScissor();
        } catch (Throwable ignored) {}

        // Panels that replace the module list entirely.
        if (infoOnly) {
            renderDeveloperPanel(context, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY);
        } else if (currentCategory == Category.KEYBINDS) {
            renderKeybindsPanel(context, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY);
        }

        // Scrollbar track & thumb
        lastMaxScroll = maxScroll;
        if (maxScroll > 0) {
            int scrollbarX = contentX + contentWidth - 6;
            int scrollbarY = contentY + 2;
            int scrollbarH = contentHeight - 4;
            RenderUtils.fill(context, scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarH, ColorUtils.rgba(20, 20, 25, 150));

            float thumbRatio = (float) contentHeight / (float) totalContentHeight;
            int thumbH = Math.max(16, (int) (scrollbarH * thumbRatio));
            float scrollRatio = (float) scrollOffset / (float) maxScroll;
            int thumbY = scrollbarY + (int) ((scrollbarH - thumbH) * scrollRatio);

            // Wider hit box than the 4px visual so it is easy to grab.
            sbHitX = scrollbarX - 3;
            sbHitW = 12;
            sbTrackY = scrollbarY;
            sbTrackH = scrollbarH;
            sbThumbH = thumbH;

            boolean sbHovered = mouseX >= sbHitX && mouseX <= sbHitX + sbHitW
                    && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarH;
            int thumbColor = draggingScrollbar ? 0xFFFFFFFF
                    : (sbHovered ? ThemeManager.getAccentColor() : ThemeManager.getAccentColor());
            RenderUtils.fill(context, scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbH, thumbColor);
        } else {
            sbHitW = 0;
        }

        // Dragging the scrollbar with the mouse cursor.
        if (draggingScrollbar && lastMaxScroll > 0 && sbTrackH > sbThumbH) {
            double ratio = (double) (mouseY - sbTrackY - sbThumbH / 2.0) / (double) (sbTrackH - sbThumbH);
            ratio = Math.max(0.0, Math.min(1.0, ratio));
            scrollOffset = (int) Math.round(ratio * lastMaxScroll);
        }

        // Handle active slider dragging
        if (draggingSlider != null) {
            updateSliderValue(mX, contentX, contentWidth);
        }
        if (draggingRange != null) {
            updateRangeValue(mX, contentX, contentWidth);
        }

        // Free RGB picker popup (any ColorSetting, right-click the color box)
        renderPicker(context, mX);

        context.getMatrices().popMatrix();

        if (showTooltips && hoveredTooltip != null && !hoveredTooltip.isEmpty() && textRenderer != null) {
            int tw = textRenderer.getWidth(hoveredTooltip);
            int tx = Math.min(mouseX + 12, width - tw - 12);
            int ty = Math.min(mouseY + 12, height - 24);
            if (tx < 6) tx = 6;
            if (ty < 6) ty = 6;
            RenderUtils.fill(context, tx - 5, ty - 4, tx + tw + 5, ty + 12, ColorUtils.rgba(12, 14, 22, 245));
            RenderUtils.drawBorder(context, tx - 5, ty - 4, tx + tw + 5, ty + 12, 1, ThemeManager.getAccentColor());
            RenderUtils.drawText(context, textRenderer, hoveredTooltip, tx, ty, 0xFFFFFFFF, true);
        }

        FrozenFx.snowfield(context, width, height);
        super.render(context, mouseX, mouseY, delta);
    }

    private void processClick(int mouseX, int mouseY, int button, int guiX, int guiY, int guiWidth, int guiHeight) {
        // 1. Edit HUD Button Click
        int searchW = 110;
        int searchX = guiX + guiWidth - searchW - 12;
        int searchY = guiY + 6;
        int hudBtnW = 64;
        int hudBtnX = searchX - hudBtnW - 6;
        int hudBtnY = guiY + 6;
        if (mouseX >= hudBtnX && mouseX <= hudBtnX + hudBtnW && mouseY >= hudBtnY && mouseY <= hudBtnY + 14) {
            if (client != null) {
                client.setScreen(new HudEditorScreen(this));
            }
            return;
        }

        // Waypoints manager button
        int wpBtnW = 78;
        int wpBtnX = hudBtnX - wpBtnW - 6;
        if (mouseX >= wpBtnX && mouseX <= wpBtnX + wpBtnW && mouseY >= hudBtnY && mouseY <= hudBtnY + 14) {
            if (client != null) {
                client.setScreen(new WaypointsScreen(this));
            }
            return;
        }

        // 2. Search Bar Click
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + 14) {
            searchFocused = true;
            return;
        } else {
            searchFocused = false;
        }

        // 2. Category Sidebar Tabs
        int sidebarWidth = 110;
        int sidebarX = guiX + 8;
        int sidebarY = guiY + 26;

        Category[] categories = visibleCategories();
        int sidebarHeight = guiHeight - 34;
        int srcBtnH = 16;
        int srcBtnY = sidebarY + sidebarHeight - srcBtnH - 6;

        // Source-view toggle (▼): switch between the main tabs and the Spectre / Kuro tabs.
        if (mouseX >= sidebarX + 4 && mouseX <= sidebarX + sidebarWidth - 4 && mouseY >= srcBtnY && mouseY <= srcBtnY + srcBtnH) {
            sourceView = !sourceView;
            if (sourceView) {
                if (currentCategory != Category.SPECTRE && currentCategory != Category.KURO) currentCategory = Category.SPECTRE;
            } else if (currentCategory == Category.SPECTRE || currentCategory == Category.KURO) {
                currentCategory = Category.COMBAT;
            }
            scrollOffset = 0;
            return;
        }

        // Reserve the same room as the toggle button drawn in render().
        int tabReserve = 6 + srcBtnH + 6;
        int tabY = sidebarY + 4;
        int tabPitch = Math.max(15, Math.min(28, (sidebarHeight - tabReserve) / categories.length));
        int tabHeight = tabPitch - 4;

        for (Category category : categories) {
            if (mouseX >= sidebarX + 4 && mouseX <= sidebarX + sidebarWidth - 4 && mouseY >= tabY && mouseY <= tabY + tabHeight) {
                currentCategory = category;
                scrollOffset = 0;
                kbScroll = 0;
                expandedMode = null;
                expandedMulti = null;
                return;
            }
            tabY += tabPitch;
        }

        // 3. Module Cards & Settings Interaction
        int contentX = sidebarX + sidebarWidth + 6;
        int contentY = guiY + 26;
        int contentWidth = guiWidth - sidebarWidth - 22;
        int contentHeight = guiHeight - 34;

        // Ensure clicks are within content box
        if (mouseX < contentX || mouseX > contentX + contentWidth || mouseY < contentY || mouseY > contentY + contentHeight) {
            return;
        }

        // Developer tab: the credit rows are clickable to copy the handle.
        if (currentCategory.isInfoOnly()) {
            if (button == 0) handleDeveloperClick(mouseX, mouseY);
            return;
        }

        // Keybinds tab: its own list of bound modules.
        if (currentCategory == Category.KEYBINDS) {
            handleKeybindsClick(mouseX, mouseY, button, contentX, contentY, contentWidth, contentHeight);
            return;
        }

        List<Module> categoryModules = ModuleManager.getModulesByCategory(currentCategory).stream()
                .filter(m -> searchQuery.isEmpty() || m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .toList();

        int modY = contentY + 6 - scrollOffset;

        for (Module module : categoryModules) {
            int cardX = contentX + 6;
            int cardW = contentWidth - 16;

            if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= modY && mouseY <= modY + 22) {
                // Small "x" next to the keybind badge: clear the keybind instead of toggling.
                // ClientSettings' bindi qulflangan (GUI ochish kaliti) — remove tugmasi ishlamaydi.
                int clearX0 = cardX + cardW - 34;
                if (button == 0 && module.getKey() != GLFW.GLFW_KEY_UNKNOWN
                        && !com.anormal.client.module.impl.client.ClientSettings.isBindLocked(module)
                        && mouseX >= clearX0 && mouseX <= clearX0 + 12) {
                    module.setKey(GLFW.GLFW_KEY_UNKNOWN);
                    return;
                }
                if (button == 1 || mouseX >= cardX + cardW - 20) {
                    // SkinShower has its own picker: right-click opens it instead of the
                    // inline settings, and setScreen replaces this GUI so the client's own
                    // ClickGUI is not visible behind it.
                    if (module instanceof com.anormal.client.module.impl.player.SkinShower skinShower) {
                        net.minecraft.client.MinecraftClient.getInstance()
                                .setScreen(new com.anormal.client.gui.SkinShowerScreen(skinShower));
                        return;
                    }
                    if (module instanceof com.anormal.client.module.impl.player.NameShower nameShower) {
                        net.minecraft.client.MinecraftClient.getInstance()
                                .setScreen(new com.anormal.client.gui.NameShowerScreen(nameShower));
                        return;
                    }
                    if (module instanceof com.anormal.client.module.impl.legit.MusicPlayer musicPlayer) {
                        net.minecraft.client.MinecraftClient.getInstance()
                                .setScreen(new com.anormal.client.gui.MusicPlayerScreen(musicPlayer));
                        return;
                    }
                    // Right-click or clicking expand arrow toggles settings
                    module.setExpanded(!module.isExpanded());
                } else {
                    // Left-click toggles module ON/OFF
                    module.toggle();
                }
                return;
            }

            modY += 26;

            if (module.isExpanded()) {
                for (Setting<?> setting : module.getSettings()) {
                    if (!setting.isVisible()) continue;
                    if (setting instanceof HeaderSetting) {
                        modY += 20;
                        continue;
                    }
                    int setX = contentX + 16;
                    int setW = contentWidth - 36;

                    if (setting instanceof com.anormal.client.setting.ActionSetting action) {
                        if (mouseX >= setX && mouseX <= setX + setW && mouseY >= modY && mouseY <= modY + 18) {
                            action.run();
                            return;
                        }
                        modY += 20;
                        continue;
                    }
                    if (mouseX >= setX && mouseX <= setX + setW && mouseY >= modY && mouseY <= modY + 18) {
                        if (setting instanceof BooleanSetting bool) {
                            bool.toggle();
                            return;
                        } else if (setting instanceof ModeSetting mode) {
                            // Click toggles the option list (no cycling arrows)
                            expandedMode = (expandedMode == mode) ? null : mode;
                            return;
                        } else if (setting instanceof com.anormal.client.lucid.StringSetting str) {
                            // Click to focus, click again to leave. Typing is handled in
                            // keyPressed/charTyped so hotkeys do not fire mid-edit.
                            editingString = (editingString == str) ? null : str;
                            listeningSetting = null;
                            expandedMode = null;
                            return;
                        } else if (setting instanceof KeybindSetting key) {
                            // "x" on the right edge clears the bind without starting to listen.
                            // ClientSettings'ning xuddi shu Keybind'i qulflangan (GUI ochish kaliti).
                            if (key.getValue() != GLFW.GLFW_KEY_UNKNOWN
                                    && !com.anormal.client.module.impl.client.ClientSettings.isKeybindLocked(key)
                                    && mouseX >= setX + setW - 14) {
                                key.setValue(GLFW.GLFW_KEY_UNKNOWN);
                                return;
                            }
                            listeningSetting = key;
                            return;
                        } else if (setting instanceof NumberSetting num) {
                            draggingSlider = num;
                            updateSliderValue(mouseX, contentX, contentWidth);
                            return;
                        } else if (setting instanceof ColorSetting color) {
                            if (button == 1) {
                                // Right-click: open free RGB picker
                                editingColor = color;
                                editDrag = 0;
                                return;
                            }
                            int[] palette = {
                                ColorUtils.rgba(255, 120, 0, 255),  // Orange
                                ColorUtils.rgba(0, 230, 255, 255),  // Neon Cyan
                                ColorUtils.rgba(255, 50, 50, 255),   // Crimson Red
                                ColorUtils.rgba(0, 255, 127, 255),   // Emerald Green
                                ColorUtils.rgba(180, 50, 255, 255),  // Royal Purple
                                ColorUtils.rgba(255, 220, 0, 255),   // Gold Yellow
                                ColorUtils.rgba(255, 255, 255, 255)  // Pure White
                            };
                            int cur = color.getValue();
                            int nextIdx = 0;
                            for (int i = 0; i < palette.length; i++) {
                                if (palette[i] == cur) {
                                    nextIdx = (i + 1) % palette.length;
                                    break;
                                }
                            }
                            color.setValue(palette[nextIdx]);
                            return;
                        } else if (setting instanceof IntRangeSetting range) {
                            draggingRange = range;
                            draggingRangeMax = pickRangeHandle(mouseX, contentX, contentWidth,
                                    range.getMin(), range.getMax(), range.getAbsMin(), range.getAbsMax());
                            updateRangeValue(mouseX, contentX, contentWidth);
                            return;
                        } else if (setting instanceof DoubleRangeSetting range) {
                            draggingRange = range;
                            draggingRangeMax = pickRangeHandle(mouseX, contentX, contentWidth,
                                    range.getMin(), range.getMax(), range.getAbsMin(), range.getAbsMax());
                            updateRangeValue(mouseX, contentX, contentWidth);
                            return;
                        } else if (setting instanceof MultiSelectSetting multi) {
                            expandedMulti = (expandedMulti == multi) ? null : multi;
                            return;
                        }
                    }
                    modY += 20;
                    // Option rows under an expanded mode: pick directly
                    if (setting == expandedMode && setting instanceof ModeSetting em) {
                        for (String opt : em.getModes()) {
                            if (mouseX >= setX + 10 && mouseX <= setX + setW - 10 && mouseY >= modY && mouseY <= modY + 14) {
                                try {
                                    em.setMode(opt);
                                } catch (Throwable ignored) {}
                                expandedMode = null;
                                return;
                            }
                            modY += 16;
                        }
                    }
                    if (setting == expandedMulti && setting instanceof MultiSelectSetting ms) {
                        for (String opt : ms.getOptions()) {
                            if (mouseX >= setX + 10 && mouseX <= setX + setW - 10 && mouseY >= modY && mouseY <= modY + 14) {
                                ms.toggle(opt);
                                return;
                            }
                            modY += 16;
                        }
                    }
                }
                if (module instanceof com.anormal.client.module.impl.world.XRay xrayModule) {
                    int btnX = contentX + 16;
                    int btnW = contentWidth - 36;
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= modY && mouseY <= modY + 18) {
                        if (client != null) {
                            client.setScreen(new XRayBlockSelectorScreen(this, xrayModule));
                        }
                        return;
                    }
                    modY += 22;
                }
                if (module instanceof com.anormal.client.module.impl.client.ActionWheel wheelMod) {
                    int btnX = contentX + 16;
                    int btnW = contentWidth - 36;
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= modY && mouseY <= modY + 18) {
                        if (client != null) {
                            client.setScreen(new ActionWheelEditorScreen(wheelMod));
                        }
                        return;
                    }
                    modY += 22;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Developer tab
    // ------------------------------------------------------------------

    /** Author credits shown by the Developer tab. */
    private static final String DEV_INSTAGRAM = "Instagram: @maqsadbek_37";
    private static final String DEV_TELEGRAM = "telegram: @Qurbonboy3v";
    private static final String DEV_INSTAGRAM_HANDLE = "maqsadbek_37";
    private static final String DEV_TELEGRAM_HANDLE = "Qurbonboy3v";

    // Row hit boxes, rebuilt every frame and used by the click handler.
    private int devRowX, devRowW, devIgY, devTgY, devRowH;
    /** Entrance animation start (0 = not shown yet); reset whenever another tab is active. */
    private long devPanelStart = 0L;
    /** Last copied credit row (0 Instagram, 1 Telegram, -1 none) and when it happened. */
    private int devCopiedRow = -1;
    private long devCopiedAt = 0L;

    /**
     * Draws the credits card. The two rows are laid out on a shared grid so the
     * icons, labels and handles line up exactly, and each row is clickable to
     * copy the handle.
     */
    private void renderDeveloperPanel(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
        int cardX = x + 10;
        int cardY = y + 10;
        int cardW = w - 20;
        int cardH = Math.min(h - 20, 150);

        boolean glass = isGlassTheme();
        boolean brutal = ThemeManager.getActiveTheme() == Theme.BRUTALISM;

        // Card chrome follows the active theme instead of one fixed dark-blue box.
        if (brutal) {
            // Hard accent shadow and a flat body; the white frame is drawn last with the tape.
            RenderUtils.fill(context, cardX + 5, cardY + 5, cardX + cardW + 5, cardY + cardH + 5,
                    ThemeManager.getAccentColor());
            RenderUtils.fill(context, cardX, cardY, cardX + cardW, cardY + cardH, BrutalismFx.surface());
        } else if (glass) {
            // Same layered glass as the floating frames: soft shadow, bright rim, frosted body.
            RenderUtils.fillRounded(context, cardX + 2, cardY + 4, cardW, cardH, 8, ColorUtils.rgba(0, 0, 0, 40));
            RenderUtils.fillRounded(context, cardX, cardY, cardW, cardH, 7, glassRim());
            RenderUtils.fillRounded(context, cardX + 1, cardY + 1, cardW - 2, cardH - 2, 6, glassBody());
        } else {
            RenderUtils.fill(context, cardX, cardY, cardX + cardW, cardY + cardH, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, cardX, cardY, cardX + cardW, cardY + cardH, 1, ThemeManager.getBorderColor());
        }

        // Header: a floating chip on glass, a black plate with a white rule on brutalism, an
        // accent bar on the flat themes. Deliberately NOT translated - stays in English.
        int headH = glass ? 24 : 26;
        int headX = glass ? cardX + 6 : cardX;
        int headW = glass ? cardW - 12 : cardW;
        int headY = glass ? cardY + 6 : cardY;
        if (brutal) {
            RenderUtils.fill(context, headX + 2, headY + 2, headX + headW - 2, headY + headH, 0xFF000000);
            RenderUtils.fill(context, headX + 2, headY + headH, headX + headW - 2, headY + headH + 2, 0xFFFFFFFF);
        } else if (glass) {
            RenderUtils.fillRounded(context, headX, headY, headW, headH, 5, glassTint(56));
        } else {
            RenderUtils.fill(context, headX, headY, headX + headW, headY + headH, ThemeManager.getAccentColor());
        }

        String title = "Developer";
        int headerColor;
        if (brutal) {
            headerColor = 0xFFFFFFFF;
        } else if (ThemeManager.getActiveTheme() == Theme.VAPE) {
            headerColor = ThemeManager.VAPE_ON_ACCENT;
        } else {
            headerColor = 0xFFFFFFFF;
        }
        int devIcon = 9;
        int titleW = textRenderer.getWidth(title);
        int titleX = cardX + (cardW - (devIcon + 5 + titleW)) / 2;
        CategoryIcon.draw(context, Category.DEVELOPER, titleX, headY + (headH - devIcon) / 2, devIcon, headerColor);
        RenderUtils.drawText(context, textRenderer, title, titleX + devIcon + 5,
                headY + (headH - 8) / 2, headerColor, true);

        // Shared grid: icon column, label column, copy hint right-aligned.
        devRowX = cardX + 12;
        devRowW = cardW - 24;
        devRowH = 30;
        devIgY = cardY + 42;
        devTgY = devIgY + devRowH + 10;

        // Rows rise into place when the tab is opened, so it never just pops in.
        long now = System.currentTimeMillis();
        if (devPanelStart == 0L) devPanelStart = now;
        float anim = Math.min(1f, (now - devPanelStart) / 240f);
        int slide = Math.round((1f - easeOut(anim)) * 10f);
        float fade = 0.35f + 0.65f * easeOut(anim);

        drawCreditRow(context, devRowX, devIgY, devRowW, devRowH,
                0, DEV_INSTAGRAM, DEV_INSTAGRAM_HANDLE, mouseX, mouseY,
                ColorUtils.rgba(225, 48, 108, 255), slide, fade);
        drawCreditRow(context, devRowX, devTgY, devRowW, devRowH,
                1, DEV_TELEGRAM, DEV_TELEGRAM_HANDLE, mouseX, mouseY,
                ColorUtils.rgba(0, 170, 230, 255), slide, fade);

        String hint = "Click to copy";
        RenderUtils.drawText(context, textRenderer, hint,
                cardX + (cardW - textRenderer.getWidth(hint)) / 2, devTgY + devRowH + 14,
                scaleAlpha(glass ? 0xFFA6BCCB : (brutal ? 0xFFFFFFFF : 0xFF888888), fade), false);

        // Theme effect layer on the card itself (ice, liquid glass or hazard tape).
        if (brutal) {
            BrutalismFx.decoratePanel(context, cardX, cardY, cardW, cardH, 26);
        } else if (glass) {
            if (ThemeManager.getActiveTheme() == Theme.FROZEN) {
                FrozenFx.decoratePanel(context, cardX, cardY, cardW, cardH);
            } else {
                GlassFx.decoratePanel(context, cardX, cardY, cardW, cardH, mouseX, mouseY);
            }
        }
    }

    private void drawCreditRow(DrawContext context, int x, int y, int w, int h,
                               int iconKind, String label, String handle,
                               int mouseX, int mouseY, int accent, int slide, float fade) {
        boolean glass = isGlassTheme();
        boolean brutal = ThemeManager.getActiveTheme() == Theme.BRUTALISM;
        int radius = glass ? 5 : 0;
        int drawY = y + slide;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= drawY && mouseY <= drawY + h;

        // Row body in the theme's own material.
        if (brutal) {
            RenderUtils.fill(context, x + 4, drawY + 4, x + w + 4, drawY + h + 4, scaleAlpha(0xFF000000, fade));
            RenderUtils.fill(context, x, drawY, x + w, drawY + h, scaleAlpha(0xFF1A1A1E, fade));
            RenderUtils.drawBorder(context, x, drawY, x + w, drawY + h, 1,
                    scaleAlpha(hovered ? 0xFFFFFFFF : 0xFF444444, fade));
        } else if (glass) {
            RenderUtils.fillRounded(context, x, drawY, w, h, radius, scaleAlpha(glassTint(hovered ? 52 : 26), fade));
            if (hovered) {
                RenderUtils.fill(context, x + 7, drawY + 1, x + w - 7, drawY + 2,
                        scaleAlpha(withAlpha(0xFFFFFF, 70), fade));
            }
        } else {
            RenderUtils.fill(context, x, drawY, x + w, drawY + h,
                    scaleAlpha(hovered ? ThemeManager.getCardColor(false, true) : ThemeManager.getSurfaceColor(220), fade));
            RenderUtils.drawBorder(context, x, drawY, x + w, drawY + h, 1,
                    scaleAlpha(hovered ? accent : ThemeManager.getBorderColor(), fade));
        }

        // Accent strip so the two rows read as a matched pair (rounded on glass).
        int accentTint = scaleAlpha(accent, fade);
        if (brutal) {
            RenderUtils.fill(context, x, drawY, x + 4, drawY + h, accentTint);
        } else if (glass) {
            RenderUtils.fillRounded(context, x + 4, drawY + 6, 3, h - 12, 1, accentTint);
        } else {
            RenderUtils.fill(context, x, drawY, x + 3, drawY + h, accentTint);
        }

        // Icon badge - fixed width column keeps both rows aligned.
        int iconW = 18;
        int badgeX = x + 8;
        int badgeY = drawY + (h - iconW) / 2;
        if (brutal) {
            RenderUtils.fill(context, badgeX, badgeY, badgeX + iconW, badgeY + iconW, scaleAlpha(0xFF000000, fade));
            RenderUtils.drawBorder(context, badgeX, badgeY, badgeX + iconW, badgeY + iconW, 1, accentTint);
        } else if (glass) {
            RenderUtils.fillRounded(context, badgeX, badgeY, iconW, iconW, 4, scaleAlpha(withAlpha(0xFFFFFF, 30), fade));
        } else {
            RenderUtils.fill(context, badgeX, badgeY, badgeX + iconW, badgeY + iconW, scaleAlpha(ColorUtils.rgba(0, 0, 0, 120), fade));
        }
        int iconX = badgeX + (iconW - 10) / 2;
        int iconY = badgeY + (iconW - 10) / 2;
        if (iconKind == 1) {
            CategoryIcon.plane(context, iconX, iconY, 10, accentTint);
        } else {
            CategoryIcon.camera(context, iconX, iconY, 10, accentTint);
        }

        // Label column starts at a fixed offset for both rows.
        int labelX = badgeX + iconW + 10;
        int textY = drawY + (h - 8) / 2;
        RenderUtils.drawText(context, textRenderer, label, labelX, textY,
                scaleAlpha(hovered ? 0xFFFFFFFF : 0xFFDDDDDD, fade), true);

        // Right column: a copy hint on hover, and a confirmation after the click.
        long sinceCopy = System.currentTimeMillis() - devCopiedAt;
        boolean copied = devCopiedRow == iconKind && sinceCopy < 1100L;
        if (copied) {
            String done = "COPIED";
            int cw = textRenderer.getWidth(done);
            RenderUtils.drawText(context, textRenderer, done, x + w - cw - 10, textY, accentTint, true);
            // Flash the row in its accent colour for a moment.
            if (sinceCopy < 520L) {
                int a = Math.round(70f * (1f - sinceCopy / 520f));
                if (glass) {
                    RenderUtils.fillRounded(context, x, drawY, w, h, radius, withAlpha(accent, a));
                } else {
                    RenderUtils.fill(context, x, drawY, x + w, drawY + h, withAlpha(accent, a));
                }
            }
        } else if (hovered) {
            String copy = "COPY";
            int cw = textRenderer.getWidth(copy);
            RenderUtils.drawText(context, textRenderer, copy, x + w - cw - 10, textY,
                    scaleAlpha(0xFFAAAAAA, fade), false);
        }
    }

    /** @return true when the click landed on a credit row (and copied it). */
    private boolean handleDeveloperClick(int mouseX, int mouseY) {
        if (mouseY >= devIgY && mouseY <= devIgY + devRowH) {
            copyToClipboard(DEV_INSTAGRAM_HANDLE);
            devCopiedRow = 0;
            devCopiedAt = System.currentTimeMillis();
            playClickSound(1.90f, 0.24f);
            addVapeRipple(devRowX, devIgY, devRowW, devRowH);
            return true;
        }
        if (mouseY >= devTgY && mouseY <= devTgY + devRowH) {
            copyToClipboard(DEV_TELEGRAM_HANDLE);
            devCopiedRow = 1;
            devCopiedAt = System.currentTimeMillis();
            playClickSound(1.72f, 0.24f);
            addVapeRipple(devRowX, devTgY, devRowW, devRowH);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Keybinds tab
    // ------------------------------------------------------------------

    /** Scroll position of the keybind list (independent from the module list). */
    private int kbScroll = 0;
    private int kbMaxScroll = 0;
    private static final int KB_ROW_H = 20;
    private static final int KB_ROW_GAP = 3;

    /** Every module that currently carries a keybind, filtered by the search box. */
    private List<Module> keybindModules() {
        String q = searchQuery.toLowerCase();
        return ModuleManager.getModules().stream()
                .filter(m -> m.getKey() != GLFW.GLFW_KEY_UNKNOWN)
                .filter(m -> q.isEmpty()
                        || m.getName().toLowerCase().contains(q)
                        || com.anormal.client.i18n.I18n.tr(m.getName()).toLowerCase().contains(q)
                        || m.getKeybindSetting().getKeyName().toLowerCase().contains(q))
                .sorted(java.util.Comparator.comparing(
                        m -> com.anormal.client.i18n.I18n.tr(m.getName()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private int kbRowY(int contentY, int index) {
        return contentY + 30 + index * (KB_ROW_H + KB_ROW_GAP) - kbScroll;
    }

    private int kbRowX(int contentX) {
        return contentX + 8;
    }

    private int kbRowW(int contentWidth) {
        return contentWidth - 16;
    }

    /** Clears every keybind except the ones the client itself needs (GUI key). */
    private void unbindAllKeybinds() {
        for (Module m : ModuleManager.getModules()) {
            if (m.getKey() == GLFW.GLFW_KEY_UNKNOWN) continue;
            if (com.anormal.client.module.impl.client.ClientSettings.isBindLocked(m)) continue;
            m.setKey(GLFW.GLFW_KEY_UNKNOWN);
        }
        kbScroll = 0;
    }

    private void renderKeybindsPanel(DrawContext context, int x, int y, int w, int h,
                                     int mouseX, int mouseY) {
        List<Module> bound = keybindModules();

        // Header: title + count on the left, "Unbind All" on the right.
        String title = "\u2328 " + com.anormal.client.i18n.I18n.tr("Keybinds") + " (" + bound.size() + ")";
        RenderUtils.drawText(context, textRenderer, title, x + 10, y + 10, 0xFFFFFFFF, true);

        int btnW = 88;
        int btnH = 16;
        int btnX = x + w - btnW - 8;
        int btnY = y + 6;
        boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean anyBound = ModuleManager.getModules().stream()
                .anyMatch(m -> m.getKey() != GLFW.GLFW_KEY_UNKNOWN
                        && !com.anormal.client.module.impl.client.ClientSettings.isBindLocked(m));
        RenderUtils.fill(context, btnX, btnY, btnX + btnW, btnY + btnH,
                btnHover && anyBound ? ColorUtils.rgba(200, 60, 60, 230) : ColorUtils.rgba(24, 27, 35, 220));
        RenderUtils.drawBorder(context, btnX, btnY, btnX + btnW, btnY + btnH, 1,
                btnHover && anyBound ? 0xFFFF8888 : ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, "\u2716 " + com.anormal.client.i18n.I18n.tr("Unbind All"),
                btnX + 6, btnY + 4, anyBound ? 0xFFFFFFFF : 0xFF777777, true);

        int listTop = y + 30;
        int listBottom = y + h - 4;
        int visibleH = Math.max(1, listBottom - listTop);

        int total = bound.size() * (KB_ROW_H + KB_ROW_GAP);
        kbMaxScroll = Math.max(0, total - visibleH);
        kbScroll = Math.max(0, Math.min(kbScroll, kbMaxScroll));

        try {
            context.enableScissor(x + 1, listTop, x + w - 2, listBottom);
        } catch (Throwable ignored) {
        }

        if (bound.isEmpty()) {
            String empty = searchQuery.isEmpty()
                    ? com.anormal.client.i18n.I18n.tr("No keybinds set")
                    : com.anormal.client.i18n.I18n.tr("Nothing matches the search");
            RenderUtils.drawText(context, textRenderer, "\u00a78" + empty,
                    x + (w - textRenderer.getWidth(empty)) / 2, listTop + 24, 0xFF888888, true);
        }

        int rowX = kbRowX(x);
        int rowW = kbRowW(w);
        for (int i = 0; i < bound.size(); i++) {
            Module module = bound.get(i);
            int rowY = kbRowY(y, i);
            if (rowY + KB_ROW_H < listTop || rowY > listBottom) continue;

            boolean hovered = mouseX >= rowX && mouseX <= rowX + rowW
                    && mouseY >= rowY && mouseY <= rowY + KB_ROW_H;

            RenderUtils.fill(context, rowX, rowY, rowX + rowW, rowY + KB_ROW_H,
                    hovered ? ColorUtils.rgba(34, 38, 50, 240) : ColorUtils.rgba(20, 22, 28, 220));
            RenderUtils.drawBorder(context, rowX, rowY, rowX + rowW, rowY + KB_ROW_H, 1,
                    module.isEnabled() ? ThemeManager.getAccentColor() : ColorUtils.rgba(42, 47, 60, 200));

            // Enabled modules keep the accent strip so the list reads like the module cards.
            if (module.isEnabled()) {
                RenderUtils.fill(context, rowX, rowY, rowX + 3, rowY + KB_ROW_H, ThemeManager.getAccentColor());
            }

            String name = com.anormal.client.i18n.I18n.tr(module.getName());
            RenderUtils.drawText(context, textRenderer, name, rowX + 9, rowY + 6,
                    module.isEnabled() ? 0xFFFFFFFF : ThemeManager.getTextColor(false), true);

            String category = com.anormal.client.i18n.I18n.tr(module.getCategory().getRawName());
            RenderUtils.drawText(context, textRenderer, "\u00a78" + category,
                    rowX + 9 + textRenderer.getWidth(name) + 8, rowY + 6, 0xFF7A8090, false);

            // Key badge: click to rebind, "x" to clear.
            boolean locked = com.anormal.client.module.impl.client.ClientSettings.isBindLocked(module);
            boolean listening = listeningSetting == module.getKeybindSetting();
            String keyStr = listening ? "[...]" : "[" + module.getKeybindSetting().getKeyName() + "]";
            int kw = textRenderer.getWidth(keyStr);
            int clearW = locked ? 0 : 14;
            int badgeX = rowX + rowW - kw - 10 - clearW;
            RenderUtils.fill(context, badgeX - 4, rowY + 4, badgeX + kw + 4, rowY + KB_ROW_H - 4,
                    listening ? ColorUtils.rgba(70, 60, 20, 230) : ColorUtils.rgba(30, 33, 42, 220));
            RenderUtils.drawText(context, textRenderer, keyStr, badgeX, rowY + 6,
                    listening ? 0xFFFFDD55 : 0xFFDDDDDD, true);

            if (!locked) {
                int cx = rowX + rowW - clearW - 2;
                boolean hovClear = mouseX >= cx && mouseX <= cx + 12
                        && mouseY >= rowY + 4 && mouseY <= rowY + KB_ROW_H - 4;
                RenderUtils.fill(context, cx, rowY + 4, cx + 12, rowY + KB_ROW_H - 4,
                        hovClear ? ColorUtils.rgba(200, 60, 60, 230) : ColorUtils.rgba(38, 40, 48, 200));
                RenderUtils.drawText(context, textRenderer, "x", cx + 4, rowY + 6,
                        hovClear ? 0xFFFFFFFF : 0xFF999999, true);
            }
        }

        try {
            context.disableScissor();
        } catch (Throwable ignored) {
        }

        // Scrollbar for long lists.
        if (kbMaxScroll > 0) {
            int sbX = x + w - 6;
            int thumbH = Math.max(16, (int) (visibleH * ((float) visibleH / (float) total)));
            int thumbY = listTop + (int) ((visibleH - thumbH) * ((float) kbScroll / (float) kbMaxScroll));
            RenderUtils.fill(context, sbX, listTop, sbX + 4, listBottom, ColorUtils.rgba(20, 20, 25, 150));
            RenderUtils.fill(context, sbX, thumbY, sbX + 4, thumbY + thumbH, ThemeManager.getAccentColor());
        }
    }

    private void handleKeybindsClick(int mouseX, int mouseY, int button,
                                     int contentX, int contentY, int contentWidth, int contentHeight) {
        if (button != 0) return;

        // "Unbind All" header button.
        int btnW = 88;
        int btnH = 16;
        int btnX = contentX + contentWidth - btnW - 8;
        int btnY = contentY + 6;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            unbindAllKeybinds();
            return;
        }

        int listTop = contentY + 30;
        int listBottom = contentY + contentHeight - 4;
        if (mouseY < listTop || mouseY > listBottom) return;

        List<Module> bound = keybindModules();
        int rowX = kbRowX(contentX);
        int rowW = kbRowW(contentWidth);
        for (int i = 0; i < bound.size(); i++) {
            Module module = bound.get(i);
            int rowY = kbRowY(contentY, i);
            if (mouseY < rowY || mouseY > rowY + KB_ROW_H) continue;
            if (mouseX < rowX || mouseX > rowX + rowW) return;

            boolean locked = com.anormal.client.module.impl.client.ClientSettings.isBindLocked(module);
            if (!locked && mouseX >= rowX + rowW - 16) {
                module.setKey(GLFW.GLFW_KEY_UNKNOWN);
                return;
            }
            // Anywhere else on the row (notably the key badge) starts listening for a new key.
            listeningSetting = module.getKeybindSetting();
            editingString = null;
            expandedMode = null;
            return;
        }
    }

    private void copyToClipboard(String text) {
        try {
            if (client != null) client.keyboard.setClipboard(text);
        } catch (Throwable ignored) {
        }
    }

    private void renderModuleCard(DrawContext context, Module module, int x, int y, int width, int mouseX, int mouseY) {
        String displayName = com.anormal.client.i18n.I18n.tr(module.getName());
        String displayDesc = com.anormal.client.i18n.I18n.tr(module.getDescription());
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 22;
        if (hovered && displayDesc != null && !displayDesc.isEmpty()) {
            hoveredTooltip = "§6" + displayName + "§7: " + displayDesc;
        }
        boolean vape = ThemeManager.getActiveTheme() == Theme.VAPE;
        int cardBg = ThemeManager.getCardColor(module.isEnabled(), hovered);

        RenderUtils.fill(context, x, y, x + width, y + 22, cardBg);
        if (ThemeManager.getActiveTheme() == Theme.BRUTALISM) {
            int border = module.isEnabled() ? ThemeManager.getAccentColor() : (hovered ? ColorUtils.rgba(0, 229, 255, 255) : 0xFF444D66);
            RenderUtils.drawBorder(context, x, y, x + width, y + 22, 2, border);
        } else {
            RenderUtils.drawBorder(context, x, y, x + width, y + 22, 1, ThemeManager.getBorderColor());
        }

        // Active indicator strip. Vape already fills the whole row with the accent instead.
        if (module.isEnabled() && !vape) {
            RenderUtils.fill(context, x, y, x + 3, y + 22, ThemeManager.getAccentColor());
        }

        // Module Name
        int textColor = module.isEnabled()
                ? (vape ? ThemeManager.VAPE_ON_ACCENT : 0xFFFFFFFF)
                : ThemeManager.getTextColor(false);
        RenderUtils.drawText(context, textRenderer, displayName, x + 8, y + 7, textColor, true);

        // Keybind badge (+ clear button). The badge is pushed left so the small "x" never
        // overlaps the expand arrow at the far right.
        String keyText = "[" + module.getKeybindSetting().getKeyName() + "]";
        int keyWidth = textRenderer.getWidth(keyText);
        boolean hasKey = module.getKey() != GLFW.GLFW_KEY_UNKNOWN;
        int keyColor = vape && module.isEnabled() ? 0x77000000 : 0xFF888888;
        RenderUtils.drawText(context, textRenderer, keyText, x + width - keyWidth - 38, y + 7, keyColor, true);

        if (hasKey && !com.anormal.client.module.impl.client.ClientSettings.isBindLocked(module)) {
            int cx = x + width - 34;
            boolean hovClear = mouseX >= cx && mouseX <= cx + 12 && mouseY >= y + 5 && mouseY <= y + 17;
            RenderUtils.fill(context, cx, y + 5, cx + 12, y + 17,
                    hovClear ? ColorUtils.rgba(200, 60, 60, 230) : ColorUtils.rgba(38, 40, 48, 200));
            RenderUtils.drawText(context, textRenderer, "x", cx + 4, y + 7, hovClear ? 0xFFFFFFFF : 0xFF999999, true);
        }

        // Expand settings indicator
        String expandText = module.isExpanded() ? "-" : "+";
        int expandColor = vape && module.isEnabled() ? ThemeManager.VAPE_ON_ACCENT : ThemeManager.getAccentColor();
        RenderUtils.drawText(context, textRenderer, expandText, x + width - 14, y + 7, expandColor, true);
    }

    private void renderSetting(DrawContext context, Module module, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY) {
        String setDisplayName = com.anormal.client.i18n.I18n.tr(setting.getName());
        String setDisplayDesc = com.anormal.client.i18n.I18n.tr(setting.getDescription());
        boolean setHov = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 18;
        if (setHov && setDisplayDesc != null && !setDisplayDesc.isEmpty()) {
            hoveredTooltip = "§e" + setDisplayName + "§7: " + setDisplayDesc;
        }
        if (setting instanceof HeaderSetting header) {
            int hy = y + 5;
            String title = com.anormal.client.i18n.I18n.tr(header.getName()).toUpperCase();
            RenderUtils.drawText(context, textRenderer, "§7" + title, x + 2, hy, 0xFFA0A5B5, false);
            int tw = textRenderer.getWidth(title);
            int lineX1 = x + 2 + tw + 8;
            int lineX2 = x + width - 4;
            if (lineX2 > lineX1) {
                RenderUtils.fill(context, lineX1, hy + 4, lineX2, hy + 5, ColorUtils.rgba(50, 55, 70, 180));
            }
            return;
        }

        if (setting instanceof com.anormal.client.setting.ActionSetting action) {
            RenderUtils.fill(context, x, y, x + width, y + 18, ThemeManager.getSurfaceColor(225));
            RenderUtils.drawBorder(context, x, y, x + width, y + 18, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, textRenderer, setDisplayName, x + 6, y + 5, 0xFFDDDDDD, true);
            String badge = "OPEN >";
            RenderUtils.drawText(context, textRenderer, badge,
                    x + width - 8 - textRenderer.getWidth(badge), y + 5,
                    ThemeManager.getAccentColor(), true);
            return;
        }

        RenderUtils.fill(context, x, y, x + width, y + 18, ThemeManager.getSurfaceColor(225));
        RenderUtils.drawBorder(context, x, y, x + width, y + 18, 1, ThemeManager.getBorderColor());

        // Keep the name clear of the slider + value on the right so the number stays readable.
        String numValStr = null;
        int reservedRight = 12;
        if (setting instanceof NumberSetting numVal) {
            numValStr = String.format("%.1f", numVal.getValue());
            reservedRight += 70 + 4 + textRenderer.getWidth(numValStr) + 6;
        } else if (setting instanceof IntRangeSetting || setting instanceof DoubleRangeSetting) {
            reservedRight += 70 + 4 + 42 + 6;
        }
        String nameShown = setDisplayName;
        int nameMaxW = width - reservedRight - 6;
        while (nameShown.length() > 1 && textRenderer.getWidth(nameShown + "…") > nameMaxW) {
            nameShown = nameShown.substring(0, nameShown.length() - 1);
        }
        if (nameShown.length() < setDisplayName.length()) nameShown += "…";
        RenderUtils.drawText(context, textRenderer, nameShown, x + 6, y + 5, 0xFFDDDDDD, true);

        if (setting instanceof BooleanSetting bool) {
            if (ThemeManager.getActiveTheme() == Theme.BRUTALISM) {
                int toggleW = 28;
                int toggleX = x + width - toggleW - 6;
                int toggleY = y + 2;
                int toggleBg = bool.isEnabled() ? ThemeManager.getAccentColor() : ColorUtils.rgba(24, 28, 38, 255);
                RenderUtils.fill(context, toggleX, toggleY, toggleX + toggleW, toggleY + 14, toggleBg);
                RenderUtils.drawBorder(context, toggleX, toggleY, toggleX + toggleW, toggleY + 14, 1, bool.isEnabled() ? 0xFFFFFFFF : 0xFF555F7A);
                String tag = bool.isEnabled() ? "ON" : "OFF";
                int col = bool.isEnabled() ? 0xFF000000 : 0xFF8B95AF;
                RenderUtils.drawText(context, textRenderer, tag, toggleX + (bool.isEnabled() ? 7 : 5), toggleY + 3, col, true);
            } else {
                int toggleX = x + width - 20;
                int toggleY = y + 4;
                int toggleBg = bool.isEnabled() ? ThemeManager.getAccentColor() : ColorUtils.rgba(40, 40, 40, 255);
                RenderUtils.fill(context, toggleX, toggleY, toggleX + 14, toggleY + 10, toggleBg);
                RenderUtils.drawBorder(context, toggleX, toggleY, toggleX + 14, toggleY + 10, 1, ThemeManager.getBorderColor());
                if (bool.isEnabled()) {
                    RenderUtils.drawText(context, textRenderer, "✓", toggleX + 3, toggleY + 1, 0xFFFFFFFF, true);
                }
            }
        } else if (setting instanceof NumberSetting num) {
            int sliderWidth = 70;
            int sliderX = x + width - sliderWidth - 6;
            int sliderY = y + 6;
            int sliderHeight = 6;

            // Track background
            RenderUtils.fill(context, sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight, ColorUtils.rgba(30, 30, 35, 255));

            // Filled progress
            double percent = (num.getValue() - num.getMin()) / (num.getMax() - num.getMin());
            int fillWidth = (int) (sliderWidth * Math.max(0, Math.min(1, percent)));
            RenderUtils.fill(context, sliderX, sliderY, sliderX + fillWidth, sliderY + sliderHeight, ThemeManager.getAccentColor());

            // Value label
            RenderUtils.drawText(context, textRenderer, numValStr, sliderX - textRenderer.getWidth(numValStr) - 4, y + 5, 0xFFAAAAAA, true);
        } else if (setting instanceof ModeSetting mode) {
            String modeStr = com.anormal.client.i18n.I18n.tr(mode.getValue()) + (setting == expandedMode ? "  [-]" : "  [+]");
            int mw = textRenderer.getWidth(modeStr);
            RenderUtils.drawText(context, textRenderer, modeStr, x + width - mw - 6, y + 5, ThemeManager.getAccentColor(), true);
        } else if (setting instanceof com.anormal.client.lucid.StringSetting str) {
            // Free-text settings used to fall through the chain and render nothing at all, which
            // made FakeStats' Money/Shards/Rank fields look like they did not exist.
            boolean editing = (editingString == str);
            String value = str.get();
            if (value == null) value = "";
            String shown = value.isEmpty() ? (editing ? "" : "click to type") : value;

            int maxW = Math.max(30, width - textRenderer.getWidth(setDisplayName) - 28);
            while (textRenderer.getWidth(shown) > maxW && shown.length() > 3) {
                shown = shown.substring(1);
            }

            int tw = textRenderer.getWidth(shown);
            int boxX = x + width - tw - 12;
            RenderUtils.fill(context, boxX - 3, y + 3, x + width - 4, y + 15,
                    editing ? ColorUtils.rgba(50, 70, 110, 220) : ColorUtils.rgba(34, 34, 40, 200));
            RenderUtils.drawBorder(context, boxX - 3, y + 3, x + width - 4, y + 15, 1,
                    editing ? ThemeManager.getAccentColor() : ThemeManager.getBorderColor());
            RenderUtils.drawText(context, textRenderer, shown, boxX, y + 5,
                    value.isEmpty() ? 0xFF777777 : 0xFFFFFFFF, true);
            if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
                int caretX = boxX + tw + 1;
                RenderUtils.fill(context, caretX, y + 4, caretX + 1, y + 14, ThemeManager.getAccentColor());
            }
        } else if (setting instanceof KeybindSetting key) {
            boolean hasKey = key.getValue() != GLFW.GLFW_KEY_UNKNOWN;
            boolean clearLocked = com.anormal.client.module.impl.client.ClientSettings.isKeybindLocked(key);
            String keyStr = (listeningSetting == key) ? "§e[...]" : "[" + key.getKeyName() + "]";
            int kw = textRenderer.getWidth(keyStr);
            int pad = hasKey ? 18 : 6;
            RenderUtils.drawText(context, textRenderer, keyStr, x + width - kw - pad, y + 5, 0xFF888888, true);

            if (hasKey && !clearLocked) {
                int cx = x + width - 14;
                boolean hovClear = mouseX >= cx && mouseX <= cx + 12 && mouseY >= y + 3 && mouseY <= y + 15;
                RenderUtils.fill(context, cx, y + 4, cx + 12, y + 14,
                        hovClear ? ColorUtils.rgba(200, 60, 60, 230) : ColorUtils.rgba(38, 40, 48, 200));
                RenderUtils.drawText(context, textRenderer, "x", cx + 4, y + 5,
                        hovClear ? 0xFFFFFFFF : 0xFF999999, true);
            }
        } else if (setting instanceof ColorSetting color) {
            int boxX = x + width - 36;
            int boxY = y + 4;
            RenderUtils.fill(context, boxX, boxY, boxX + 30, boxY + 10, color.getValue());
            RenderUtils.drawBorder(context, boxX, boxY, boxX + 30, boxY + 10, 1, 0xFFFFFFFF);
            String colLabel = color.isRainbow() ? "§dRAINBOW" : String.format("#%06X", (0xFFFFFF & color.getValue()));
            RenderUtils.drawText(context, textRenderer, colLabel, boxX - textRenderer.getWidth(colLabel) - 4, y + 5, 0xFFAAAAAA, true);
        } else if (setting instanceof IntRangeSetting range) {
            drawRange(context, x, y, width, range.getMin(), range.getMax(), range.getAbsMin(), range.getAbsMax());
        } else if (setting instanceof DoubleRangeSetting range) {
            drawRange(context, x, y, width, range.getMin(), range.getMax(), range.getAbsMin(), range.getAbsMax());
        } else if (setting instanceof MultiSelectSetting multi) {
            String text = multi.display() + (setting == expandedMulti ? "  [-]" : "  [+]");
            int mw = textRenderer.getWidth(text);
            RenderUtils.drawText(context, textRenderer, text, x + width - mw - 6, y + 5, ThemeManager.getAccentColor(), true);
        }
    }

    /** Two-handle range slider used by IntRangeSetting / DoubleRangeSetting. */
    private void drawRange(DrawContext context, int x, int y, int width,
                           double min, double max, double absMin, double absMax) {
        int trackX = x + width - RANGE_TRACK_OFFSET;
        int trackY = y + 6;
        int trackH = 6;
        RenderUtils.fill(context, trackX, trackY, trackX + RANGE_TRACK_W, trackY + trackH, ColorUtils.rgba(30, 30, 35, 255));

        double span = Math.max(1e-9, absMax - absMin);
        int pxMin = (int) Math.round(RANGE_TRACK_W * ((min - absMin) / span));
        int pxMax = (int) Math.round(RANGE_TRACK_W * ((max - absMin) / span));
        pxMin = Math.max(0, Math.min(RANGE_TRACK_W, pxMin));
        pxMax = Math.max(0, Math.min(RANGE_TRACK_W, pxMax));

        RenderUtils.fill(context, trackX + pxMin, trackY, trackX + pxMax, trackY + trackH, ThemeManager.getAccentColor());
        RenderUtils.fill(context, trackX + pxMin - 1, trackY - 1, trackX + pxMin + 1, trackY + trackH + 1, 0xFFFFFFFF);
        RenderUtils.fill(context, trackX + pxMax - 1, trackY - 1, trackX + pxMax + 1, trackY + trackH + 1, 0xFFFFFFFF);

        String val = fmtRange(min) + " - " + fmtRange(max);
        RenderUtils.drawText(context, textRenderer, val, trackX - 4 - textRenderer.getWidth(val), y + 5, 0xFFAAAAAA, true);
    }

    private String fmtRange(double v) {
        return Math.abs(v - Math.round(v)) < 0.001 ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private int rangeTrackX(int contentX, int contentWidth) {
        return contentX + 16 + (contentWidth - 36) - RANGE_TRACK_OFFSET;
    }

    private boolean pickRangeHandle(int mouseX, int contentX, int contentWidth,
                                    double min, double max, double absMin, double absMax) {
        int trackX = rangeTrackX(contentX, contentWidth);
        double span = Math.max(1e-9, absMax - absMin);
        int pxMin = (int) Math.round(RANGE_TRACK_W * ((min - absMin) / span));
        int pxMax = (int) Math.round(RANGE_TRACK_W * ((max - absMin) / span));
        return Math.abs(mouseX - (trackX + pxMax)) < Math.abs(mouseX - (trackX + pxMin));
    }

    private void updateRangeValue(int mouseX, int contentX, int contentWidth) {
        if (draggingRange == null) return;
        int trackX = rangeTrackX(contentX, contentWidth);
        double percent = Math.max(0.0, Math.min(1.0, (double) (mouseX - trackX) / RANGE_TRACK_W));
        if (draggingRange instanceof IntRangeSetting r) {
            int v = (int) Math.round(r.getAbsMin() + percent * (r.getAbsMax() - r.getAbsMin()));
            if (draggingRangeMax) r.setMax(v);
            else r.setMin(v);
        } else if (draggingRange instanceof DoubleRangeSetting r) {
            double v = r.getAbsMin() + percent * (r.getAbsMax() - r.getAbsMin());
            if (draggingRangeMax) r.setMax(v);
            else r.setMin(v);
        }
    }

    // 1.21.11 input API: Screen dispatches mouseClicked(Click, boolean).
    // Old (double,double,int) overload is kept as plain logic — new overload delegates to it.
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleGuiClick(click.x(), click.y(), click.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleGuiClick(mouseX, mouseY, button);
    }

    private boolean handleGuiClick(double mouseX, double mouseY, int button) {
        // Any click first ends an active text edit; the row handler below re-focuses it when the
        // click actually landed on a text setting.
        editingString = null;

        float scale = 1.0f;
        com.anormal.client.module.impl.client.ClientSettings cs = ModuleManager.getModule(com.anormal.client.module.impl.client.ClientSettings.class);
        if (cs != null && cs.guiScale != null) {
            scale = cs.guiScale.getValue().floatValue();
            if (scale < 0.4f || scale > 2.0f) scale = 1.0f;
        }
        if (lockedScale != null) {
            scale = lockedScale;
        }
        float cx = width / 2.0f;
        float cy = height / 2.0f;
        double smX = (mouseX - cx) / scale + cx;
        double smY = (mouseY - cy) / scale + cy;

        // Color picker popup eats all clicks while open
        if (editingColor != null && handlePickerClick((int) smX, (int) smY, button)) {
            return true;
        }
        // Binding mode: any mouse button becomes the bind — EXCEPT left click (never bindable)
        if (listeningSetting != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                listeningSetting = null; // left click cancels instead of binding
                return true;
            }
            listeningSetting.setValue(KeybindSetting.mouseCode(button));
            listeningSetting = null;
            return true;
        }
        if (usesVapeLayout()) {
            return vapeClick((int) smX, (int) smY, button);
        }
        int guiWidth = Math.min(520, width - 20);
        int guiHeight = Math.min(300, height - 20);
        int guiX = (width - guiWidth) / 2;
        int guiY = (height - guiHeight) / 2;

        // Scrollbar drag has priority over everything else in the content area.
        if (button == GLFW.GLFW_MOUSE_BUTTON_1 && sbHitW > 0 && lastMaxScroll > 0
                && smX >= sbHitX && smX <= sbHitX + sbHitW
                && smY >= sbTrackY && smY <= sbTrackY + sbTrackH) {
            draggingScrollbar = true;
            if (sbTrackH > sbThumbH) {
                double ratio = (double) (smY - sbTrackY - sbThumbH / 2.0) / (double) (sbTrackH - sbThumbH);
                ratio = Math.max(0.0, Math.min(1.0, ratio));
                scrollOffset = (int) Math.round(ratio * lastMaxScroll);
            }
            return true;
        }

        processClick((int) smX, (int) smY, button, guiX, guiY, guiWidth, guiHeight);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        lockedScale = null;
        draggingSlider = null;
        draggingRange = null;
        draggingScrollbar = false;
        editDrag = 0;
        // Dropping a drag/resize is the natural moment to persist the new geometry.
        boolean vapeGeometryChanged = vapeDragPanel != 0 || vapeResizeMode != 0;
        vapeDragPanel = 0;
        vapeResizeMode = 0;
        if (vapeGeometryChanged) saveVapeLayout();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (usesVapeLayout() && button == GLFW.GLFW_MOUSE_BUTTON_1
                && (vapeDragPanel != 0 || vapeResizeMode != 0)) {
            float scale = 1.0f;
            com.anormal.client.module.impl.client.ClientSettings cs =
                    ModuleManager.getModule(com.anormal.client.module.impl.client.ClientSettings.class);
            if (cs != null && cs.guiScale != null) {
                scale = cs.guiScale.getValue().floatValue();
                if (scale < 0.4f || scale > 2.0f) scale = 1.0f;
            }
            if (lockedScale != null) scale = lockedScale;
            float cx = width / 2.0f;
            float cy = height / 2.0f;
            int smX = (int) ((mouseX - cx) / scale + cx);
            int smY = (int) ((mouseY - cy) / scale + cy);
            VapeGeom g = vapeGeom();
            if (vapeResizeMode != 0) {
                if (vapeResizeMode == 1 || vapeResizeMode == 3) {
                    vapeSetW = Math.max(140, Math.min(smX - g.setX, Math.max(140, width - g.setX - 4)));
                }
                if (vapeResizeMode == 2 || vapeResizeMode == 3) {
                    vapeSetH = Math.max(90, Math.min(smY - g.setY, Math.max(90, height - g.setY - 4)));
                }
                return true;
            }
            int nx = smX - vapeDragOffX;
            int ny = smY - vapeDragOffY;
            if (vapeDragPanel == 1) {
                vapePanX = Math.max(0, Math.min(nx, Math.max(0, width - g.panW)));
                vapePanY = Math.max(0, Math.min(ny, Math.max(0, height - g.panH)));
            } else if (vapeDragPanel == 2) {
                vapeSetX = Math.max(0, Math.min(nx, Math.max(0, width - g.setW)));
                vapeSetY = Math.max(0, Math.min(ny, Math.max(0, height - g.setH)));
            } else if (vapeDragPanel == 3) {
                vapeSbX = Math.max(0, Math.min(nx, Math.max(0, width - g.sbW)));
                vapeSbY = Math.max(0, Math.min(ny, Math.max(0, height - g.sbH)));
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0.0) return true;
        if (usesVapeLayout()) {
            return vapeMouseScrolled(mouseX, mouseY, verticalAmount);
        }
        // Wheel step scaled to the content so a notch always moves a sensible
        // amount, then clamped to the real content bounds.
        int step = (int) Math.round(verticalAmount * 26.0);
        if (step == 0) step = verticalAmount > 0 ? 1 : -1;
        if (currentCategory == Category.KEYBINDS) {
            kbScroll = Math.max(0, Math.min(kbMaxScroll, kbScroll - step));
            return true;
        }
        scrollOffset = Math.max(0, Math.min(lastMaxScroll, scrollOffset - step));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int modifiers = input.modifiers();
        // Picker open: ESC closes it first
        if (editingColor != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            editingColor = null;
            return true;
        }
        // 0. Free-text editing swallows everything so no hotkey fires while typing.
        if (editingString != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                editingString = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String v = editingString.get();
                if (v != null && !v.isEmpty()) {
                    editingString.set(v.substring(0, v.length() - 1));
                }
                return true;
            }
            return true;
        }

        // 1. Keybind listening has top priority: ANY key (incl. mouse handled in mouseClicked) binds here
        if (listeningSetting != null) {
            if (com.anormal.client.module.impl.client.ClientSettings.isKeybindLocked(listeningSetting)
                    && (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE
                        || keyCode == GLFW.GLFW_KEY_BACKSPACE)) {
                listeningSetting = null;   // ClientSettings bindi o'chirilmasin — ESC faqat tinchlik bilan yopiladi
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE
                    || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                listeningSetting.setValue(GLFW.GLFW_KEY_UNKNOWN);
            } else {
                listeningSetting.setValue(keyCode);
            }
            listeningSetting = null;
            return true;
        }

        // 2. Search field: control keys only — printable chars arrive via charTyped
        // (covers ALL keyboard layouts: EN/RU/UZ; manual A-Z mapping broke non-Latin layouts)
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            // Any other key while searching: swallow it so hotkeys don't leak through,
            // printable result comes via charTyped right after.
            return true;
        }

        // Escape always closes; the GUI key closes only when it is the key the user actually bound
        // to ClientSettings (it used to be hardcoded to Right Shift).
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || keyCode == com.anormal.client.module.impl.client.ClientSettings.menuKey()) {
            close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (editingString != null) {
            String typed = input.asString();
            if (!typed.isEmpty()) {
                String v = editingString.get();
                if (v == null) v = "";
                if (v.length() + typed.length() <= editingString.getMaxLength()) {
                    editingString.set(v + typed);
                }
            }
            return true;
        }
        if (listeningSetting != null) return true; // don't leak typed chars into search while binding
        String s = input.asString();
        if (searchFocused && s.length() == 1) {
            char chr = s.charAt(0);
            if (chr >= 32 && chr <= 126) {
                searchQuery += chr;
                return true;
            }
        }
        return super.charTyped(input);
    }

    private boolean handlePickerClick(int mouseX, int mouseY, int button) {
        int pw = 180, ph = 112;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY > py + ph) return false;
        if (button != 0) {
            // Right-click anywhere on picker closes it
            if (button == 1) editingColor = null;
            return true;
        }
        int trackX = px + 34, trackW = 110;
        int[][] rows = {{py + 38, 1}, {py + 54, 2}, {py + 70, 3}};
        for (int[] row : rows) {
            if (mouseY >= row[0] - 2 && mouseY <= row[0] + 8 && mouseX >= trackX - 4 && mouseX <= trackX + trackW + 4) {
                editDrag = row[1];
                applyPickerDrag(mouseX);
                return true;
            }
        }
        // Rainbow toggle
        if (mouseX >= px + 8 && mouseX <= px + 100 && mouseY >= py + 86 && mouseY <= py + 100) {
            editingColor.setRainbow(!editingColor.isRainbow());
            return true;
        }
        // Close button
        if (mouseX >= px + pw - 30 && mouseX <= px + pw - 6 && mouseY >= py + 4 && mouseY <= py + 16) {
            editingColor = null;
            return true;
        }
        return true;
    }

    private void applyPickerDrag(int mouseX) {
        if (editingColor == null || editDrag < 1 || editDrag > 3) return;
        int pw = 180;
        int px = (width - pw) / 2;
        int trackX = px + 34, trackW = 110;
        double percent = Math.max(0.0, Math.min(1.0, (double) (mouseX - trackX) / trackW));
        int v = (int) Math.round(percent * 255);
        int r = editingColor.getRed(), g = editingColor.getGreen(), b = editingColor.getBlue();
        if (editDrag == 1) r = v;
        else if (editDrag == 2) g = v;
        else b = v;
        editingColor.setRainbow(false);
        editingColor.setValue(ColorUtils.rgba(r, g, b, 255));
    }

    private void renderPicker(DrawContext context, int mouseX) {
        if (editingColor == null) return;
        if (editDrag != 0) applyPickerDrag(mouseX);
        int pw = 180, ph = 112;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        RenderUtils.fill(context, px, py, px + pw, py + ph, ColorUtils.rgba(12, 14, 20, 245));
        RenderUtils.drawBorder(context, px, py, px + pw, py + ph, 1, ThemeManager.getAccentColor());
        RenderUtils.drawText(context, textRenderer, "§eColor Picker", px + 8, py + 5, 0xFFFFFFFF, true);
        RenderUtils.drawText(context, textRenderer, "§8[X]", px + pw - 30, py + 5, 0xFFAAAAAA, true);
        // Preview
        RenderUtils.fill(context, px + 8, py + 18, px + pw - 8, py + 30, editingColor.getValue());
        RenderUtils.drawBorder(context, px + 8, py + 18, px + pw - 8, py + 30, 1, 0xFFFFFFFF);
        String hex = String.format("#%06X", 0xFFFFFF & editingColor.getValue());
        RenderUtils.drawText(context, textRenderer, hex, px + pw - 8 - textRenderer.getWidth(hex), py + 31, 0xFFAAAAAA, true);
        // RGB sliders
        int trackX = px + 34, trackW = 110;
        int[] vals = {editingColor.getRed(), editingColor.getGreen(), editingColor.getBlue()};
        String[] names = {"R", "G", "B"};
        int[] cols = {0xFFFF5555, 0xFF55FF55, 0xFF5555FF};
        for (int i = 0; i < 3; i++) {
            int ry = py + 38 + i * 16;
            RenderUtils.drawText(context, textRenderer, names[i], px + 8, ry - 1, cols[i], true);
            RenderUtils.fill(context, trackX, ry, trackX + trackW, ry + 6, ColorUtils.rgba(30, 30, 35, 255));
            int fill = (int) (trackW * vals[i] / 255.0);
            RenderUtils.fill(context, trackX, ry, trackX + fill, ry + 6, cols[i]);
            String vs = String.valueOf(vals[i]);
            RenderUtils.drawText(context, textRenderer, vs, trackX + trackW + 4, ry - 1, 0xFFAAAAAA, true);
        }
        // Rainbow + hint
        int rbY = py + 88;
        int rbBg = editingColor.isRainbow() ? ThemeManager.getAccentColor() : ColorUtils.rgba(40, 40, 40, 255);
        RenderUtils.fill(context, px + 8, rbY, px + 22, rbY + 10, rbBg);
        RenderUtils.drawBorder(context, px + 8, rbY, px + 22, rbY + 10, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, textRenderer, "Rainbow", px + 26, rbY + 1, 0xFFDDDDDD, true);
        RenderUtils.drawText(context, textRenderer, "§8L:slide R:close", px + 8, py + 101, 0xFF777777, true);
    }

    // ======================= Vape V4 style ClickGUI =======================
    //
    // While the Vape theme is active the GUI stops being one bordered window with a sidebar and
    // becomes Vape's floating-frame layout: a wordmark/sidebar card on the left, a module panel
    // next to it and a settings panel for the module whose ⋮ was clicked. State below is separate
    // from the classic layout so switching themes never disturbs the other one.

    private static final int VAPE_ROW_H = 20;
    private static final int VAPE_HEADER_H = 22;

    private Module vapeSettingsModule = null;
    private int vapeScroll = 0;
    private int vapeSetScroll = 0;
    // Scroll offsets per section/module: reopening the GUI (or switching sections) lands where the
    // user left off instead of jumping back to the top of the list.
    private final java.util.Map<Category, Integer> vapeScrollMemory = new java.util.EnumMap<>(Category.class);
    private final java.util.Map<String, Integer> vapeSetScrollMemory = new java.util.HashMap<>();
    private int vapeSliderX = 0, vapeSliderW = 0;
    private int vapeRangeX = 0, vapeRangeW = 0;
    // Draggable floating panels: remembered top-left corner, -1 = auto (centered default).
    private int vapeSbX = -1, vapeSbY = -1;
    private int vapePanX = -1, vapePanY = -1;
    private int vapeSetX = -1, vapeSetY = -1;
    private int vapeSetW = -1, vapeSetH = -1; // user-resized settings window, -1 = auto
    private int vapeDragPanel = 0; // 0 = none, 1 = module panel, 2 = settings panel, 3 = sidebar
    private int vapeResizeMode = 0; // 0 = none, 1 = right edge, 2 = bottom edge, 3 = corner
    private int vapeDragOffX = 0, vapeDragOffY = 0;
    private static Module lastVapeSettingsModule = null;
    // Mode settings open a list of their values instead of cycling on every click.
    private ModeSetting openVapeMode = null;
    private int vapeModeX = 0, vapeModeY = 0, vapeModeW = 0;
    // Smooth section transitions
    private float vapeSelY = -1f;
    private float vapeRowsAnim = 1f;
    private float vapeSetRowsAnim = 1f;
    // Click ripple: start time (0 = idle) plus the rect it expands from.
    private long vapeRippleStart;
    private int vapeRippleX, vapeRippleY, vapeRippleW, vapeRippleH;
    /** Frozen theme: the last hovered module row, so the icy hover tick only plays on change. */
    private String frozenHoverRow;

    /** Measured geometry of the Vape layout, shared by the renderer and the click handler. */
    private static final class VapeGeom {
        int sbX, sbY, sbW, sbH;
        int panX, panY, panW, panH;
        int setX, setY, setW, setH;
        int searchX, searchY, searchW, searchH;
        int rowsTop, rowsBottom, setRowsTop, setRowsBottom;
        Category[] cats;
        List<Module> modules;
        List<Setting<?>> settings;
        int maxScroll, maxSetScroll;
        boolean searching;
    }

    private VapeGeom vapeGeom() {
        VapeGeom g = new VapeGeom();
        g.cats = visibleCategories();
        g.searching = !searchQuery.isEmpty();
        g.modules = new java.util.ArrayList<>();
        if (g.searching) {
            // Search only inside the section that is currently selected.
            String q = searchQuery.toLowerCase();
            if (!currentCategory.isCustomPanel()) {
                for (Module m : ModuleManager.getModulesByCategory(currentCategory)) {
                    if (m.getName().toLowerCase().contains(q)
                            || com.anormal.client.i18n.I18n.tr(m.getName()).toLowerCase().contains(q)) {
                        g.modules.add(m);
                    }
                }
            }
        } else if (!currentCategory.isCustomPanel()) {
            g.modules.addAll(ModuleManager.getModulesByCategory(currentCategory));
        }

        g.settings = new java.util.ArrayList<>();
        if (vapeSettingsModule != null) {
            for (Setting<?> s : vapeSettingsModule.getSettings()) {
                if (s.isVisible()) g.settings.add(s);
            }
        }

        int maxPanelH = Math.min(320, Math.max(150, height - 40));
        g.panW = 214;
        g.sbW = 152;
        g.setW = vapeSetW >= 140 ? Math.min(vapeSetW, Math.max(140, width - 12)) : g.panW;

        int settingRows = 0;
        for (Setting<?> s : g.settings) {
            settingRows++;
            if (s instanceof MultiSelectSetting ms) settingRows += ms.getOptions().size();
        }

        int rowsArea = Math.max(VAPE_ROW_H * 3, maxPanelH - VAPE_HEADER_H - 8);
        int panRows = Math.max(3, Math.min(Math.max(g.modules.size(), 3), rowsArea / VAPE_ROW_H));
        g.panH = VAPE_HEADER_H + 8 + panRows * VAPE_ROW_H;
        if (currentCategory.isCustomPanel()) {
            // Credits / keybind tabs need a taller frame than a two-row module list.
            g.panH = Math.max(g.panH, Math.min(maxPanelH, VAPE_HEADER_H + 8 + 190));
        }
        g.maxScroll = Math.max(0, g.modules.size() * VAPE_ROW_H - panRows * VAPE_ROW_H);

        int setRowsShown = Math.max(3, Math.min(Math.max(settingRows, 3), rowsArea / VAPE_ROW_H));
        g.setH = VAPE_HEADER_H + 8 + (settingRows == 0 ? 40 : setRowsShown * VAPE_ROW_H);
        if (vapeSetH >= 90) g.setH = Math.min(vapeSetH, Math.max(90, height - 12)); // user-resized
        g.maxSetScroll = Math.max(0, settingRows * VAPE_ROW_H - Math.max(0, g.setH - VAPE_HEADER_H - 8));

        g.sbH = Math.min(maxPanelH, 27 + g.cats.length * VAPE_ROW_H + 26);

        // The anchor uses the DEFAULT column widths only: the settings column is always reserved at
        // its default width, so opening or resizing it never shifts the sidebar or module panel.
        int baseW = g.sbW + 8 + g.panW + 8 + g.panW;
        int startX = Math.max(6, (width - baseW) / 2);
        int topY = Math.max(30, (height - Math.max(g.sbH, g.panH)) / 2);

        // Panels remember where the user dragged them; -1 keeps the centered default.
        g.sbX = vapeSbX >= 0 ? vapeSbX : startX;
        g.sbY = vapeSbY >= 0 ? vapeSbY : topY;
        g.sbX = Math.max(0, Math.min(g.sbX, Math.max(0, width - g.sbW)));
        g.sbY = Math.max(0, Math.min(g.sbY, Math.max(0, height - g.sbH)));
        g.panX = vapePanX >= 0 ? vapePanX : startX + g.sbW + 8;
        g.panY = vapePanY >= 0 ? vapePanY : topY;
        g.setX = vapeSetX >= 0 ? vapeSetX : g.panX + g.panW + 8;
        g.setY = vapeSetY >= 0 ? vapeSetY : topY;
        g.panX = Math.max(0, Math.min(g.panX, Math.max(0, width - g.panW)));
        g.panY = Math.max(0, Math.min(g.panY, Math.max(0, height - g.panH)));
        g.setX = Math.max(0, Math.min(g.setX, Math.max(0, width - g.setW)));
        g.setY = Math.max(0, Math.min(g.setY, Math.max(0, height - g.setH)));
        g.searchW = 190;
        g.searchH = 15;
        g.searchX = g.panX + (g.panW - g.searchW) / 2;
        g.searchY = g.panY - 22;
        g.rowsTop = g.panY + VAPE_HEADER_H;
        g.rowsBottom = g.panY + g.panH;
        g.setRowsTop = g.setY + VAPE_HEADER_H;
        g.setRowsBottom = g.setY + g.setH;
        return g;
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Glassmorphism / Frozen keep the Vape frame layout but paint it as liquid glass. */
    private static boolean isGlassTheme() {
        Theme t = ThemeManager.getActiveTheme();
        return t == Theme.GLASSMORPHISM || t == Theme.FROZEN;
    }

    /** Themes that use the Vape frame layout (sidebar + module panel + settings panel). */
    private static boolean usesVapeLayout() {
        return ThemeManager.isVapeLayout() || ThemeManager.getActiveTheme() == Theme.VAPE;
    }

    /** Outer 1px rim of a liquid glass panel. */
    private static int glassRim() {
        return ThemeManager.getActiveTheme() == Theme.FROZEN
                ? ColorUtils.rgba(205, 240, 255, 80) : GlassFx.rimColor();
    }

    /** Translucent body of a liquid glass panel. */
    private static int glassBody() {
        return ThemeManager.getActiveTheme() == Theme.FROZEN
                ? ColorUtils.rgba(16, 34, 48, 175) : ColorUtils.rgba(18, 24, 40, 165);
    }

    /** Cool white-blue wash used for rows, tabs and fields on top of the glass body. */
    private static int glassTint(int a) {
        return ThemeManager.getActiveTheme() == Theme.FROZEN
                ? ColorUtils.rgba(210, 240, 255, a) : ColorUtils.rgba(160, 200, 255, a);
    }

    private static int withAlpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Multiplies an existing alpha by 0..1, keeping the RGB. */
    private static int scaleAlpha(int color, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        int a = (int) (((color >>> 24) & 0xFF) * f);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** 0..1 -> eased 0..1 (ease-out cubic). */
    private static float easeOut(float t) {
        float p = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - p * p * p;
    }

    private void playSectionSound() {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                if (FrozenFx.active()) {
                    // Crystal shard tap plus a high chime: switching sections sounds like frost cracking.
                    client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 0.34f, 1.55f);
                    client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.20f, 2.0f);
                    return;
                }
                if (BrutalismFx.active()) {
                    // Two hard thunks: a lever slamming into its next notch.
                    client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.40f, 0.55f);
                    client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.28f, 0.70f);
                    return;
                }
                if (GlassFx.active()) {
                    // A glass bell answered by a high ping.
                    client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.20f, 1.45f);
                    client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.16f, 1.75f);
                    return;
                }
            }
        } catch (Throwable ignored) {}
        playClickSound(1.6f, 0.30f);
    }

    /** Short UI click for every interactive element; pitch changes make toggles feel distinct. */
    private void playClickSound(float pitch, float volume) {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            if (FrozenFx.active()) {
                // Frozen uses an amethyst chime instead of the vanilla button tick: same family of
                // crystalline sounds as the rest of the theme, and the pitch still marks toggles on/off.
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                        volume * 0.85f, Math.min(2.0f, pitch * 1.05f));
                return;
            }
            if (BrutalismFx.active()) {
                // A deep wooden thunk instead of the soft vanilla tick: heavy, no shimmer.
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                        Math.min(1.0f, volume * 1.35f), Math.min(1.6f, pitch * 0.42f));
                return;
            }
            if (GlassFx.active()) {
                // Glass rings instead of clicking, so the theme sounds like its own material.
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                        volume * 0.8f, Math.min(2.0f, pitch));
                return;
            }
            client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), volume, pitch);
        } catch (Throwable ignored) {}
    }

    /** Theme entrance sound: each theme announces itself in its own material. */
    private void playThemeOpenSound() {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            if (FrozenFx.active()) {
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 0.30f, 1.75f);
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 0.22f, 1.95f);
            } else if (BrutalismFx.active()) {
                // One heavy clang: the panel has been stamped onto the screen.
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND, 0.17f, 1.75f);
            } else if (GlassFx.active()) {
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.22f, 1.25f);
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.14f, 1.85f);
            }
        } catch (Throwable ignored) {}
    }

    /** Theme exit sound. */
    private void playThemeCloseSound() {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            if (FrozenFx.active()) {
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 0.22f, 1.85f);
            } else if (BrutalismFx.active()) {
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.32f, 0.5f);
            } else if (GlassFx.active()) {
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.16f, 1.45f);
            }
        } catch (Throwable ignored) {}
    }

    /** Faint tick when the pointer moves onto another module row. */
    private void playThemeHoverTick() {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            if (FrozenFx.active()) {
                client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 0.06f, 2.25f);
            } else if (BrutalismFx.active()) {
                client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.05f, 0.55f);
            } else if (GlassFx.active()) {
                client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.05f, 1.5f);
            }
        } catch (Throwable ignored) {}
    }

    /** Starts the glass click ripple at a control's rect. */
    private void addVapeRipple(int x, int y, int w, int h) {
        vapeRippleStart = System.currentTimeMillis();
        vapeRippleX = x;
        vapeRippleY = y;
        vapeRippleW = w;
        vapeRippleH = h;
    }

    /** Expanding, fading highlight drawn on top of the panel after a click. */
    private void renderVapeRipple(DrawContext context) {
        if (vapeRippleStart <= 0L) return;
        float p = (System.currentTimeMillis() - vapeRippleStart) / 260f;
        if (p >= 1f) {
            vapeRippleStart = 0L;
            return;
        }
        int accent = ThemeManager.getAccentColor();
        int grow = Math.round(6f * easeOut(p));
        int alpha = Math.round(70f * (1f - p));
        RenderUtils.fillRounded(context, vapeRippleX - grow, vapeRippleY - grow,
                vapeRippleW + grow * 2, vapeRippleH + grow * 2, 5, withAlpha(accent, alpha));
    }

    private static java.nio.file.Path vapeLayoutFile() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("anormal-vape-layout.txt");
    }

    /** Persists the dragged panel positions and the resized settings window across restarts. */
    private void saveVapeLayout() {
        try {
            java.util.Properties p = new java.util.Properties();
            p.setProperty("sbX", String.valueOf(vapeSbX));
            p.setProperty("sbY", String.valueOf(vapeSbY));
            p.setProperty("panX", String.valueOf(vapePanX));
            p.setProperty("panY", String.valueOf(vapePanY));
            p.setProperty("setX", String.valueOf(vapeSetX));
            p.setProperty("setY", String.valueOf(vapeSetY));
            p.setProperty("setW", String.valueOf(vapeSetW));
            p.setProperty("setH", String.valueOf(vapeSetH));
            p.setProperty("category", currentCategory.name());
            p.setProperty("module", vapeSettingsModule == null ? "" : vapeSettingsModule.getName());
            // Anormal-layout list scroll, so it survives a restart as well.
            p.setProperty("listScroll", String.valueOf(scrollOffset));
            // Fold the live offsets into the per-section memory before writing, so the position is
            // kept even when the GUI is closed by something other than the ESC key.
            vapeScrollMemory.put(currentCategory, vapeScroll);
            if (vapeSettingsModule != null) {
                vapeSetScrollMemory.put(vapeSettingsModule.getName(), vapeSetScroll);
            }
            for (java.util.Map.Entry<Category, Integer> e : vapeScrollMemory.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    p.setProperty("scroll." + e.getKey().name(), String.valueOf(e.getValue()));
                }
            }
            for (java.util.Map.Entry<String, Integer> e : vapeSetScrollMemory.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    p.setProperty("setScroll." + e.getKey(), String.valueOf(e.getValue()));
                }
            }
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(vapeLayoutFile())) {
                p.store(out, "Anormal Vape layout");
            }
        } catch (Throwable ignored) {}
    }

    private void loadVapeLayout() {
        try {
            java.nio.file.Path file = vapeLayoutFile();
            if (!java.nio.file.Files.exists(file)) return;
            java.util.Properties p = new java.util.Properties();
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
                p.load(in);
            }
            vapeSbX = intProp(p, "sbX");
            vapeSbY = intProp(p, "sbY");
            vapePanX = intProp(p, "panX");
            vapePanY = intProp(p, "panY");
            vapeSetX = intProp(p, "setX");
            vapeSetY = intProp(p, "setY");
            vapeSetW = intProp(p, "setW");
            vapeSetH = intProp(p, "setH");

            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("scroll.")) {
                    String name = key.substring("scroll.".length());
                    for (Category c : Category.values()) {
                        if (c.name().equals(name)) {
                            vapeScrollMemory.put(c, Math.max(0, intProp(p, key)));
                            break;
                        }
                    }
                } else if (key.startsWith("setScroll.")) {
                    vapeSetScrollMemory.put(key.substring("setScroll.".length()), Math.max(0, intProp(p, key)));
                }
            }

            com.anormal.client.module.impl.client.ClientSettings cs =
                    ModuleManager.getModule(com.anormal.client.module.impl.client.ClientSettings.class);
            if (cs != null && !cs.rememberPosition.isEnabled()) return;

            scrollOffset = Math.max(0, intProp(p, "listScroll"));

            String category = p.getProperty("category", "");
            for (Category c : Category.values()) {
                if (c.name().equals(category)) {
                    currentCategory = c;
                    break;
                }
            }
            String module = p.getProperty("module", "");
            if (!module.isEmpty()) {
                for (Module m : ModuleManager.getModules()) {
                    if (m.getName().equals(module)) {
                        vapeSettingsModule = m;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int intProp(java.util.Properties p, String key) {
        try {
            return Integer.parseInt(p.getProperty(key, "-1"));
        } catch (Throwable t) {
            return -1;
        }
    }

    private void vapeCard(DrawContext context, int x, int y, int w, int h) {
        if (ThemeManager.getActiveTheme() == Theme.BRUTALISM) {
            // Neo-brutalist card: hard un-blurred accent shadow, flat near-black body. The 2px white
            // frame is drawn last by BrutalismFx so the header fill never eats it.
            RenderUtils.fill(context, x + 5, y + 5, x + w + 5, y + h + 5, ThemeManager.getAccentColor());
            RenderUtils.fill(context, x, y, x + w, y + h, BrutalismFx.surface());
            return;
        }
        if (isGlassTheme()) {
            int accent = ThemeManager.getAccentColor();
            // Layered drop shadow: two soft passes make the panel read as floating glass.
            RenderUtils.fillRounded(context, x + 3, y + 6, w, h, 9, ColorUtils.rgba(0, 0, 0, 26));
            RenderUtils.fillRounded(context, x + 1, y + 3, w, h, 8, ColorUtils.rgba(0, 0, 0, 44));
            // Bright outer rim, then the translucent body.
            RenderUtils.fillRounded(context, x, y, w, h, 7, glassRim());
            RenderUtils.fillRounded(context, x + 1, y + 1, w - 2, h - 2, 6, glassBody());
            // Inner specular rim: light catches the top and left edges of the glass.
            RenderUtils.fill(context, x + 2, y + 2, x + w - 2, y + 3, glassTint(64));
            RenderUtils.fill(context, x + 2, y + 3, x + 3, y + h - 3, glassTint(30));
            // Liquid sheen: gloss band across the top and a softer reflection under it.
            RenderUtils.fillRounded(context, x + 2, y + 2, w - 4, 14, 5, glassTint(30));
            RenderUtils.fillRounded(context, x + 4, y + 17, w - 8, 8, 4, glassTint(12));
            // Accent glow hugging the bottom edge plus a hairline of accent under the header.
            RenderUtils.fillRounded(context, x + 6, y + h - 3, w - 12, 1, 1, withAlpha(accent, 75));
            RenderUtils.fill(context, x + 2, y + VAPE_HEADER_H + 1, x + w - 2, y + VAPE_HEADER_H + 2,
                    withAlpha(accent, 38));
            return;
        }
        RenderUtils.fillRounded(context, x, y, w, h, 5, ColorUtils.rgba(255, 255, 255, 22));
        RenderUtils.fillRounded(context, x + 1, y + 1, w - 2, h - 2, 4, ColorUtils.rgba(23, 23, 23, 250));
    }

    private void vapeScissor(DrawContext context, int x1, int y1, int x2, int y2) {
        // Logical coordinates only: DrawContext's scissor stack applies the current matrix
        // itself, so scaling by hand here would double the scale and clip rows wrongly.
        try {
            context.enableScissor(x1, y1, Math.max(x1 + 1, x2), Math.max(y1 + 1, y2));
        } catch (Throwable ignored) {}
    }

    private void vapeScissorOff(DrawContext context) {
        try {
            context.disableScissor();
        } catch (Throwable ignored) {}
    }

    /** Live crosshair preview window size inside the GUI. */
    private static final int XHAIR_PREVIEW_W = 136;
    private static final int XHAIR_PREVIEW_H = 124;

    /** The CustomCrosshair whose settings panel is open with its Preview toggle on, else null. */
    private com.anormal.client.module.impl.render.CustomCrosshair crosshairPreviewModule() {
        if (vapeSettingsModule instanceof com.anormal.client.module.impl.render.CustomCrosshair cc
                && cc.preview.isEnabled()) {
            return cc;
        }
        return null;
    }

    /** Screen rect of the preview window: {x, y, w, h}. Tries right, then left of the panel. */
    private int[] crosshairPreviewGeom(VapeGeom g) {
        int w = XHAIR_PREVIEW_W;
        int h = XHAIR_PREVIEW_H;
        int x = g.setX + g.setW + 8;
        int y = g.setY;
        if (x + w > width - 4) x = g.setX - w - 8;
        if (x < 4) {
            x = Math.max(4, width - w - 4);
            y = g.setY + g.setH + 8;
            if (y + h > height - 4) y = Math.max(4, g.setY - h - 8);
        }
        x = Math.max(4, Math.min(x, Math.max(4, width - w - 4)));
        y = Math.max(4, Math.min(y, Math.max(4, height - h - 4)));
        return new int[]{x, y, w, h};
    }

    private void renderCrosshairPreview(DrawContext context, VapeGeom g) {
        com.anormal.client.module.impl.render.CustomCrosshair cc = crosshairPreviewModule();
        if (cc == null) return;
        int[] r = crosshairPreviewGeom(g);
        int x = r[0], y = r[1], w = r[2], h = r[3];
        boolean glass = isGlassTheme();

        vapeCard(context, x, y, w, h);
        RenderUtils.fill(context, x + 1, y + 1, x + w - 1, y + VAPE_HEADER_H,
                glass ? glassTint(28) : ColorUtils.rgba(17, 17, 17, 255));
        RenderUtils.drawText(context, textRenderer, "Crosshair Preview", x + 10, y + 7,
                glass ? 0xFFFFFFFF : 0xFFF2F2F2, false);
        RenderUtils.drawText(context, textRenderer, "×", x + w - 16, y + 6,
                glass ? 0xFFC2D4E0 : 0xFF8A8A8A, false);
        RenderUtils.fill(context, x + 1, y + VAPE_HEADER_H, x + w - 1, y + VAPE_HEADER_H + 1,
                ColorUtils.rgba(255, 255, 255, 14));

        int sx = x + 6, sy = y + VAPE_HEADER_H + 6, sw = w - 12, sh = h - VAPE_HEADER_H - 12;
        // Dark stage with a faint grid, so the crosshair's size and outline read as they would in game.
        RenderUtils.fillRounded(context, sx, sy, sw, sh, 4, ColorUtils.rgba(9, 13, 20, 240));
        for (int gx = sx + 12; gx < sx + sw; gx += 12) {
            RenderUtils.fill(context, gx, sy + 2, gx + 1, sy + sh - 2, ColorUtils.rgba(255, 255, 255, 8));
        }
        for (int gy = sy + 12; gy < sy + sh; gy += 12) {
            RenderUtils.fill(context, sx + 2, gy, sx + sw - 2, gy + 1, ColorUtils.rgba(255, 255, 255, 8));
        }
        cc.renderPreview(context, sx + sw / 2, sy + sh / 2);
    }

    private void renderVapeGui(DrawContext context, int mouseX, int mouseY) {
        VapeGeom g = vapeGeom();
        vapeScroll = Math.max(0, Math.min(vapeScroll, g.maxScroll));
        vapeSetScroll = Math.max(0, Math.min(vapeSetScroll, g.maxSetScroll));

        boolean glass = isGlassTheme();
        int accent = ThemeManager.getAccentColor();
        int onAccent = glass ? 0xFF0B1620 : ThemeManager.VAPE_ON_ACCENT;
        int dim = glass ? 0xFFB9CBD8 : 0xFF9A9A9A;

        // --- Search field, floating above the module panel -------------------------------
        boolean searchHover = inRect(mouseX, mouseY, g.searchX, g.searchY, g.searchW, g.searchH);
        RenderUtils.fillRounded(context, g.searchX, g.searchY, g.searchW, g.searchH, 4,
                glass ? glassTint(searchFocused ? 54 : (searchHover ? 40 : 26))
                        : (searchFocused ? ColorUtils.rgba(40, 40, 40, 255)
                        : (searchHover ? ColorUtils.rgba(31, 31, 31, 255) : ColorUtils.rgba(24, 24, 24, 245))));
        RenderUtils.drawText(context, textRenderer, "⌕", g.searchX + 6, g.searchY + 4,
                glass ? 0xFFA9C2D2 : 0xFF7A7A7A, false);
        String shown = searchQuery.isEmpty() ? (searchFocused ? "" : "Search modules...") : searchQuery;
        RenderUtils.drawText(context, textRenderer, shown, g.searchX + 19, g.searchY + 4,
                searchQuery.isEmpty() ? (glass ? 0xFF9FB6C6 : 0xFF6E6E6E) : (glass ? 0xFFF2F8FF : 0xFFE6E6E6), false);
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cw = textRenderer.getWidth(shown);
            RenderUtils.fill(context, g.searchX + 20 + cw, g.searchY + 4, g.searchX + 21 + cw, g.searchY + 12, accent);
        }

        // --- Sidebar: wordmark, categories, footer buttons -------------------------------
        vapeCard(context, g.sbX, g.sbY, g.sbW, g.sbH);
        RenderUtils.fill(context, g.sbX + 1, g.sbY + 1, g.sbX + g.sbW - 1, g.sbY + VAPE_HEADER_H,
                glass ? glassTint(28) : ColorUtils.rgba(17, 17, 17, 255));
        try {
            // logo_banner.png is 1024x256 (4:1): fit it into the header by height, as large as it gets.
            int logoH = VAPE_HEADER_H - 4;
            int logoW = logoH * 4;
            int logoX = g.sbX + (g.sbW - logoW) / 2;
            context.drawTexturedQuad(
                    com.anormal.client.module.impl.legit.Watermark.bannerLogo(),
                    logoX, g.sbY + 2, logoX + logoW, g.sbY + 2 + logoH, 0.0f, 1.0f, 0.0f, 1.0f);
        } catch (Throwable ignored) {
            RenderUtils.drawText(context, textRenderer, "§lANORMAL", g.sbX + 11, g.sbY + 7, 0xFFF2F2F2, false);
        }
        RenderUtils.drawText(context, textRenderer, "⚙", g.sbX + g.sbW - 18, g.sbY + 7,
                glass ? 0xFF9FB6C6 : 0xFF6E6E6E, false);
        RenderUtils.fill(context, g.sbX + 1, g.sbY + VAPE_HEADER_H, g.sbX + g.sbW - 1, g.sbY + VAPE_HEADER_H + 1,
                ColorUtils.rgba(255, 255, 255, 14));

        int catY = g.sbY + 27;
        int targetSelY = catY;
        int catIdx = 0;
        for (Category c : g.cats) {
            if (c == currentCategory) {
                targetSelY = g.sbY + 27 + catIdx * VAPE_ROW_H;
                break;
            }
            catIdx++;
        }
        // The selection pill eases between sections instead of jumping.
        vapeSelY = vapeSelY < 0f ? targetSelY : vapeSelY + (targetSelY - vapeSelY) * 0.30f;
        if (Math.abs(vapeSelY - targetSelY) < 0.4f) vapeSelY = targetSelY;
        int pillY = Math.round(vapeSelY);
        RenderUtils.fillRounded(context, g.sbX + 5, pillY, g.sbW - 10, 19, 3,
                glass ? glassTint(46) : ColorUtils.rgba(41, 41, 41, 255));
        RenderUtils.fill(context, g.sbX + 5, pillY + 4, g.sbX + 7, pillY + 15, accent);
        if (glass) {
            // Slow liquid shimmer travelling across the selected section pill.
            int span = g.sbW - 10;
            int bandW = 26;
            int bandX = g.sbX + 5 + Math.round((span + bandW) * ((System.currentTimeMillis() % 2600L) / 2600f)) - bandW;
            int bx1 = Math.max(g.sbX + 6, bandX);
            int bx2 = Math.min(g.sbX + g.sbW - 6, bandX + bandW);
            if (bx2 > bx1) {
                RenderUtils.fill(context, bx1, pillY + 3, bx2, pillY + 16, withAlpha(0xFFFFFF, 15));
            }
        }

        for (Category cat : g.cats) {
            boolean selected = cat == currentCategory;
            boolean hovered = inRect(mouseX, mouseY, g.sbX + 5, catY, g.sbW - 10, 19);
            if (!selected && hovered) {
                RenderUtils.fillRounded(context, g.sbX + 5, catY, g.sbW - 10, 19, 3,
                        glass ? glassTint(26) : ColorUtils.rgba(32, 32, 32, 255));
            }
            CategoryIcon.draw(context, cat, g.sbX + 11, catY + 5, 9,
                    selected ? 0xFFFFFFFF : (hovered ? (glass ? 0xFFFFFFFF : 0xFFD0D0D0) : dim));
            RenderUtils.drawText(context, textRenderer, cat.getName(), g.sbX + 24, catY + 6,
                    selected ? 0xFFFFFFFF : (hovered ? (glass ? 0xFFFFFFFF : 0xFFD0D0D0) : dim), false);
            CategoryIcon.chevronRight(context, g.sbX + g.sbW - 15, catY + 7, 6,
                    selected ? accent : (glass ? 0xFF7E93A3 : 0xFF5A5A5A));
            catY += VAPE_ROW_H;
        }

        int footY = g.sbY + g.sbH - 26;
        int footW = (g.sbW - 28) / 3;
        String[] footLabels = { "HUD", "WP", "SRC" };
        for (int i = 0; i < footLabels.length; i++) {
            int fx = g.sbX + 8 + i * (footW + 4);
            boolean srcActive = i == 2 && sourceView;
            boolean hovered = inRect(mouseX, mouseY, fx, footY, footW, 18);
            RenderUtils.fillRounded(context, fx, footY, footW, 18, 3,
                    srcActive ? accent : (hovered ? (glass ? glassTint(46) : ColorUtils.rgba(42, 42, 42, 255))
                            : (glass ? glassTint(28) : ColorUtils.rgba(31, 31, 31, 255))));
            int labelW = textRenderer.getWidth(footLabels[i]);
            RenderUtils.drawText(context, textRenderer, footLabels[i], fx + (footW - labelW) / 2, footY + 5,
                    srcActive ? onAccent : (glass ? 0xFFE2EEF6 : 0xFFBDBDBD), false);
        }

        // --- Module panel ----------------------------------------------------------------
        vapeCard(context, g.panX, g.panY, g.panW, g.panH);
        RenderUtils.fill(context, g.panX + 1, g.panY + 1, g.panX + g.panW - 1, g.panY + VAPE_HEADER_H,
                glass ? glassTint(28) : ColorUtils.rgba(17, 17, 17, 255));
        int titleColor = glass ? 0xFFFFFFFF : 0xFFF2F2F2;
        if (g.searching) {
            RenderUtils.drawText(context, textRenderer, "Search", g.panX + 11, g.panY + 7, titleColor, false);
        } else {
            CategoryIcon.draw(context, currentCategory, g.panX + 11, g.panY + 6, 9, titleColor);
            RenderUtils.drawText(context, textRenderer, currentCategory.getName(), g.panX + 24, g.panY + 7, titleColor, false);
        }
        String countLabel = String.valueOf(g.modules.size());
        RenderUtils.drawText(context, textRenderer, countLabel,
                g.panX + g.panW - 12 - textRenderer.getWidth(countLabel), g.panY + 7,
                glass ? 0xFF8FA6B5 : 0xFF5E5E5E, false);
        RenderUtils.fill(context, g.panX + 1, g.panY + VAPE_HEADER_H, g.panX + g.panW - 1, g.panY + VAPE_HEADER_H + 1,
                ColorUtils.rgba(255, 255, 255, 14));

        if (currentCategory.isCustomPanel()) {
            // Developer and Keybinds are not module lists: they reuse their own panels inside
            // the Vape frame, so both tabs work in every layout instead of showing a notice.
            int cpx = g.panX + 4;
            int cpy = g.panY + VAPE_HEADER_H + 4;
            int cpw = g.panW - 8;
            int cph = g.panH - VAPE_HEADER_H - 8;
            if (currentCategory == Category.DEVELOPER) {
                renderDeveloperPanel(context, cpx, cpy, cpw, cph, mouseX, mouseY);
            } else {
                renderKeybindsPanel(context, cpx, cpy, cpw, cph, mouseX, mouseY);
            }
        }
        vapeScissor(context, g.panX + 2, g.rowsTop, g.panX + g.panW - 2, g.rowsBottom - 2);
        if (g.modules.isEmpty() && !currentCategory.isCustomPanel()) {
            RenderUtils.drawText(context, textRenderer, "No modules",
                    g.panX + (g.panW - textRenderer.getWidth("No modules")) / 2, g.rowsTop + 14,
                    glass ? 0xFFA6BCCB : 0xFF6A6A6A, false);
        }
        // Rows ease in (slide up + fade) on a section change; Brutalism snaps them into place.
        vapeRowsAnim = BrutalismFx.active() ? 1f : Math.min(1f, vapeRowsAnim + 0.14f);
        float rowsP = easeOut(vapeRowsAnim);
        int rowSlide = Math.round((1f - rowsP) * 10f);
        float rowFade = 0.25f + 0.75f * rowsP;
        String hoverNow = null;
        int rowY = g.rowsTop + 4 - vapeScroll;
        for (Module module : g.modules) {
            int drawY = rowY + rowSlide;
            boolean enabled = module.isEnabled();
            boolean hovered = mouseY >= g.rowsTop && mouseY < g.rowsBottom - 2
                    && inRect(mouseX, mouseY, g.panX + 4, rowY, g.panW - 8, 19);
            if (hovered) hoverNow = module.getName();
            int rowBg = enabled
                    ? (glass ? withAlpha(accent, hovered ? 235 : 205) : (hovered ? 0xFF19E0AE : accent))
                    : (glass ? glassTint(hovered ? 52 : 26)
                            : (hovered ? ColorUtils.rgba(45, 45, 45, 255) : ColorUtils.rgba(32, 32, 32, 255)));
            if (hovered && FrozenFx.active()) {
                // Frozen: a frost bloom under the hovered row, breathing gently.
                FrozenFx.rowBloom(context, g.panX + 4, drawY, g.panW - 8, 19);
            } else if (hovered && GlassFx.active()) {
                // Glassmorphism: a violet light pool under the hovered row.
                GlassFx.rowGlow(context, g.panX + 4, drawY, g.panW - 8, 19);
            }
            RenderUtils.fillRounded(context, g.panX + 4, drawY, g.panW - 8, 19, 3, scaleAlpha(rowBg, rowFade));
            if (hovered && BrutalismFx.active()) {
                // Brutalism: hard white frame and a chunky accent spine, no blur.
                BrutalismFx.rowDecor(context, g.panX + 4, drawY, g.panW - 8, 19, enabled);
            }
            if (glass) {
                // Glass rows catch a highlight on top and glow softly while hovered.
                RenderUtils.fill(context, g.panX + 7, drawY + 1, g.panX + g.panW - 7, drawY + 2,
                        scaleAlpha(withAlpha(0xFFFFFF, hovered ? 62 : 32), rowFade));
                if (hovered) {
                    RenderUtils.drawBorder(context, g.panX + 4, drawY, g.panX + g.panW - 4, drawY + 19, 1,
                            scaleAlpha(glassRim(), rowFade));
                }
            } else if (enabled) {
                // Accent spine on enabled rows (the Anormal/Vape palette).
                RenderUtils.fill(context, g.panX + 4, drawY + 3, g.panX + 6, drawY + 16, 0xCC0E0E0E);
            }
            RenderUtils.drawText(context, textRenderer, com.anormal.client.i18n.I18n.tr(module.getName()),
                    g.panX + 11, drawY + 6,
                    scaleAlpha(enabled ? onAccent : (glass ? 0xFFE6F1F8 : 0xFFD2D2D2), rowFade), false);
            RenderUtils.drawText(context, textRenderer, "⋮", g.panX + g.panW - 18, drawY + 5,
                    scaleAlpha(enabled ? 0xAA0E0E0E : (glass ? 0xFF9FB2C0 : 0xFF7A7A7A), rowFade), false);
            rowY += VAPE_ROW_H;
        }
        vapeScissorOff(context);

        // One quiet tick per row the pointer enters, never while sweeping past.
        if (FrozenFx.active() || GlassFx.active() || BrutalismFx.active()) {
            if (hoverNow != null && !hoverNow.equals(frozenHoverRow)) {
                frozenHoverRow = hoverNow;
                playThemeHoverTick();
            } else if (hoverNow == null) {
                frozenHoverRow = null;
            }
        }

        if (g.maxScroll > 0) {
            int trackH = g.panH - VAPE_HEADER_H - 10;
            int thumbH = Math.max(14, trackH * (g.panH - VAPE_HEADER_H - 8) / (g.modules.size() * VAPE_ROW_H + 1));
            int thumbY = g.rowsTop + 4 + (int) ((trackH - thumbH) * ((float) vapeScroll / g.maxScroll));
            RenderUtils.fill(context, g.panX + g.panW - 4, thumbY, g.panX + g.panW - 2, thumbY + thumbH,
                    glass ? glassTint(110) : ColorUtils.rgba(255, 255, 255, 45));
        }

        // --- Settings panel ---------------------------------------------------------------
        if (vapeSettingsModule != null) {
            vapeCard(context, g.setX, g.setY, g.setW, g.setH);
            RenderUtils.fill(context, g.setX + 1, g.setY + 1, g.setX + g.setW - 1, g.setY + VAPE_HEADER_H,
                    glass ? glassTint(28) : ColorUtils.rgba(17, 17, 17, 255));
            RenderUtils.drawText(context, textRenderer,
                    com.anormal.client.i18n.I18n.tr(vapeSettingsModule.getName()),
                    g.setX + 11, g.setY + 7, glass ? 0xFFFFFFFF : 0xFFF2F2F2, false);
            RenderUtils.drawText(context, textRenderer, "×", g.setX + g.setW - 18, g.setY + 6,
                    glass ? 0xFFC2D4E0 : 0xFF8A8A8A, false);
            RenderUtils.fill(context, g.setX + 1, g.setY + VAPE_HEADER_H, g.setX + g.setW - 1, g.setY + VAPE_HEADER_H + 1,
                    ColorUtils.rgba(255, 255, 255, 14));

            vapeScissor(context, g.setX + 2, g.setRowsTop, g.setX + g.setW - 2, g.setRowsBottom - 2);
            if (g.settings.isEmpty()) {
                RenderUtils.drawText(context, textRenderer, "No settings", g.setX + 12, g.setRowsTop + 12,
                        glass ? 0xFFA6BCCB : 0xFF6A6A6A, false);
            }
            vapeSetRowsAnim = Math.min(1f, vapeSetRowsAnim + 0.14f);
            int setSlide = Math.round((1f - easeOut(vapeSetRowsAnim)) * 8f);
            int setY = g.setRowsTop + 4 - vapeSetScroll + setSlide;
            for (Setting<?> setting : g.settings) {
                setY = renderVapeSetting(context, g, setting, setY, mouseX, mouseY, accent, onAccent);
            }
            vapeScissorOff(context);
        }

        // CustomCrosshair: its Preview toggle opens a second, live window next to the settings
        // panel, so the shape can be judged without leaving the client.
        renderCrosshairPreview(context, g);

        // Frozen: animated ice on every glass card (rim, corner facets, sheen, surface snow) plus
        // the short opening shockwave around the module panel.
        if (FrozenFx.active()) {
            FrozenFx.decoratePanel(context, g.sbX, g.sbY, g.sbW, g.sbH);
            FrozenFx.decoratePanel(context, g.panX, g.panY, g.panW, g.panH);
            if (vapeSettingsModule != null) {
                FrozenFx.decoratePanel(context, g.setX, g.setY, g.setW, g.setH);
            }
            FrozenFx.openBurst(context, g.panX, g.panY, g.panW, g.panH);
        } else if (GlassFx.active()) {
            GlassFx.decoratePanel(context, g.sbX, g.sbY, g.sbW, g.sbH, mouseX, mouseY);
            GlassFx.decoratePanel(context, g.panX, g.panY, g.panW, g.panH, mouseX, mouseY);
            if (vapeSettingsModule != null) {
                GlassFx.decoratePanel(context, g.setX, g.setY, g.setW, g.setH, mouseX, mouseY);
            }
            GlassFx.openBurst(context, g.panX, g.panY, g.panW, g.panH);
        } else if (BrutalismFx.active()) {
            BrutalismFx.decoratePanel(context, g.sbX, g.sbY, g.sbW, g.sbH, VAPE_HEADER_H);
            BrutalismFx.decoratePanel(context, g.panX, g.panY, g.panW, g.panH, VAPE_HEADER_H);
            if (vapeSettingsModule != null) {
                BrutalismFx.decoratePanel(context, g.setX, g.setY, g.setW, g.setH, VAPE_HEADER_H);
            }
            BrutalismFx.openStamp(context, g.panX, g.panY, g.panW, g.panH);
        }

        renderVapeModeMenu(context, mouseX, mouseY);
        renderVapeRipple(context);

        if (draggingSlider != null) {
            updateVapeSlider(mouseX);
        }
        if (draggingRange != null) {
            updateVapeRangeValue(mouseX);
        }

        renderPicker(context, mouseX);
    }

    /** Draws one settings row (plus inline multi-select options) and returns the next row's y. */
    private int renderVapeSetting(DrawContext context, VapeGeom g, Setting<?> setting, int y,
                                  int mouseX, int mouseY, int accent, int onAccent) {
        int x = g.setX + 4;
        int w = g.setW - 8;
        boolean glass = isGlassTheme();
        String label = com.anormal.client.i18n.I18n.tr(setting.getName());

        if (setting instanceof HeaderSetting) {
            RenderUtils.drawText(context, textRenderer, label.toUpperCase(), x + 8, y + 6,
                    glass ? 0xFF9FB6C6 : 0xFF6E6E6E, false);
            return y + 19;
        }

        boolean hovered = inRect(mouseX, mouseY, x, y, w, 19)
                && mouseY >= g.setRowsTop && mouseY < g.setRowsBottom - 2;
        RenderUtils.fillRounded(context, x, y, w, 19, 3,
                hovered ? (glass ? glassTint(40) : ColorUtils.rgba(42, 42, 42, 255))
                        : (glass ? glassTint(22) : ColorUtils.rgba(31, 31, 31, 255)));
        if (glass && hovered) {
            RenderUtils.drawBorder(context, x, y, x + w, y + 19, 1, glassRim());
        }
        // Truncate long names so they never run into the slider value on the right.
        int labelMaxW = w - 16;
        if (setting instanceof NumberSetting || setting instanceof IntRangeSetting || setting instanceof DoubleRangeSetting) {
            labelMaxW = w - 16 - 80;
        }
        String labelRaw = label;
        while (label.length() > 1 && textRenderer.getWidth(label + "…") > labelMaxW) {
            label = label.substring(0, label.length() - 1);
        }
        if (label.length() < labelRaw.length()) label += "…";
        RenderUtils.drawText(context, textRenderer, label, x + 8, y + 6,
                glass ? 0xFFE8F2F9 : 0xFFCFCFCF, false);

        int right = x + w - 8;
        if (setting instanceof BooleanSetting bool) {
            int tw = 20, th = 11;
            int tx = right - tw, ty = y + 4;
            RenderUtils.fillRounded(context, tx, ty, tw, th, 5,
                    bool.isEnabled() ? accent : (glass ? glassTint(50) : ColorUtils.rgba(55, 55, 55, 255)));
            int kx = bool.isEnabled() ? tx + tw - 9 : tx + 2;
            RenderUtils.fillRounded(context, kx, ty + 2, 7, 7, 3, bool.isEnabled() ? onAccent : 0xFFB0B0B0);
        } else if (setting instanceof NumberSetting num) {
            int trackW = 62;
            int trackX = right - 70;
            int trackY = y + 9;
            double pct = (num.getMax() - num.getMin()) <= 0 ? 0
                    : (num.getValue() - num.getMin()) / (num.getMax() - num.getMin());
            pct = Math.max(0, Math.min(1, pct));
            RenderUtils.fill(context, trackX, trackY, trackX + trackW, trackY + 3,
                    isGlassTheme() ? glassTint(44) : ColorUtils.rgba(58, 58, 58, 255));
            RenderUtils.fill(context, trackX, trackY, trackX + (int) (trackW * pct), trackY + 3, accent);
            RenderUtils.fill(context, trackX + (int) (trackW * pct) - 2, trackY - 2, trackX + (int) (trackW * pct) + 2, trackY + 5, 0xFFFFFFFF);
            String val = Math.abs(num.getValue()) >= 100 ? String.format("%.0f", num.getValue())
                    : String.format("%.2f", num.getValue());
            RenderUtils.drawText(context, textRenderer, val, trackX - 5 - textRenderer.getWidth(val), y + 6,
                    glass ? 0xFFC6D8E4 : 0xFF9A9A9A, false);
            vapeSliderX = trackX;
            vapeSliderW = trackW;
        } else if (setting instanceof ModeSetting mode) {
            String text = com.anormal.client.i18n.I18n.tr(mode.getValue()) + "  ▾";
            RenderUtils.drawText(context, textRenderer, text, right - textRenderer.getWidth(text), y + 6, accent, false);
        } else if (setting instanceof com.anormal.client.lucid.StringSetting str) {
            boolean editing = editingString == str;
            String value = str.get() == null ? "" : str.get();
            String shown = value.isEmpty() ? (editing ? "" : "type...") : value;
            int maxW = Math.max(20, w - textRenderer.getWidth(label) - 30);
            while (textRenderer.getWidth(shown) > maxW && shown.length() > 3) shown = shown.substring(1);
            int bw = Math.min(84, textRenderer.getWidth(shown) + 8);
            int bx = right - bw;
            RenderUtils.fillRounded(context, bx, y + 3, bw, 13, 2,
                    glass ? (editing ? glassTint(58) : glassTint(32))
                            : (editing ? ColorUtils.rgba(54, 54, 54, 255) : ColorUtils.rgba(38, 38, 38, 255)));
            RenderUtils.drawText(context, textRenderer, shown, bx + 4, y + 6,
                    value.isEmpty() ? (glass ? 0xFF9FB6C6 : 0xFF777777) : (glass ? 0xFFF2F8FF : 0xFFE6E6E6), false);
        } else if (setting instanceof KeybindSetting key) {
            String keyStr = listeningSetting == key ? "[ ... ]" : "[" + key.getKeyName() + "]";
            RenderUtils.drawText(context, textRenderer, keyStr, right - textRenderer.getWidth(keyStr), y + 6,
                    listeningSetting == key ? accent : (glass ? 0xFFC6D8E4 : 0xFF9A9A9A), false);
        } else if (setting instanceof ColorSetting color) {
            RenderUtils.fillRounded(context, right - 20, y + 5, 20, 9, 2, color.getValue() | 0xFF000000);
            String hex = String.format("#%06X", 0xFFFFFF & color.getValue());
            RenderUtils.drawText(context, textRenderer, hex, right - 24 - textRenderer.getWidth(hex), y + 6,
                    glass ? 0xFFAEC3D2 : 0xFF8A8A8A, false);
        } else if (setting instanceof IntRangeSetting range) {
            drawVapeRange(context, right, y, range.getMin(), range.getMax(), range.getAbsMin(), range.getAbsMax());
        } else if (setting instanceof DoubleRangeSetting range) {
            drawVapeRange(context, right, y, range.getMin(), range.getMax(), range.getAbsMin(), range.getAbsMax());
        } else if (setting instanceof MultiSelectSetting ms) {
            String text = ms.anySelected() ? ms.display() : "None";
            int maxW = Math.max(20, w - textRenderer.getWidth(label) - 26);
            while (textRenderer.getWidth(text) > maxW && text.length() > 3) text = text.substring(1);
            RenderUtils.drawText(context, textRenderer, text, right - textRenderer.getWidth(text), y + 6, accent, false);
            int optY = y + 19;
            for (String opt : ms.getOptions()) {
                boolean selected = ms.isSelected(opt);
                boolean optHovered = inRect(mouseX, mouseY, g.setX + 12, optY, g.setW - 24, 17)
                        && mouseY >= g.setRowsTop && mouseY < g.setRowsBottom - 2;
                RenderUtils.fill(context, g.setX + 12, optY + 4, g.setX + 21, optY + 13,
                        selected ? accent : (optHovered ? (glass ? glassTint(56) : ColorUtils.rgba(48, 48, 48, 255))
                                : (glass ? glassTint(34) : ColorUtils.rgba(36, 36, 36, 255))));
                RenderUtils.drawText(context, textRenderer, opt, g.setX + 26, optY + 5,
                        selected ? (glass ? 0xFFF0F7FC : 0xFFE6E6E6)
                                : (optHovered ? 0xFFBDBDBD : (glass ? 0xFF9FB6C6 : 0xFF8A8A8A)), false);
                optY += 17;
            }
            return optY;
        }
        return y + 19;
    }

    private void drawVapeRange(DrawContext context, int right, int y,
                               double min, double max, double absMin, double absMax) {
        int trackW = 62;
        int trackX = right - 70;
        int trackY = y + 9;
        RenderUtils.fill(context, trackX, trackY, trackX + trackW, trackY + 3,
                    isGlassTheme() ? glassTint(44) : ColorUtils.rgba(58, 58, 58, 255));
        double span = Math.max(1e-9, absMax - absMin);
        int pxMin = (int) Math.round(trackW * ((min - absMin) / span));
        int pxMax = (int) Math.round(trackW * ((max - absMin) / span));
        pxMin = Math.max(0, Math.min(trackW, pxMin));
        pxMax = Math.max(0, Math.min(trackW, pxMax));
        RenderUtils.fill(context, trackX + pxMin, trackY, trackX + pxMax, trackY + 3, ThemeManager.getAccentColor());
        RenderUtils.fill(context, trackX + pxMin - 1, trackY - 2, trackX + pxMin + 2, trackY + 5, 0xFFFFFFFF);
        RenderUtils.fill(context, trackX + pxMax - 1, trackY - 2, trackX + pxMax + 2, trackY + 5, 0xFFFFFFFF);
        String val = fmtRange(min) + "-" + fmtRange(max);
        RenderUtils.drawText(context, textRenderer, val, trackX - 5 - textRenderer.getWidth(val), y + 6, 0xFF9A9A9A, false);
        vapeRangeX = trackX;
        vapeRangeW = trackW;
    }

    private void updateVapeSlider(int mouseX) {
        if (!(draggingSlider instanceof NumberSetting num) || vapeSliderW <= 0) return;
        double pct = (double) (mouseX - vapeSliderX) / vapeSliderW;
        pct = Math.max(0, Math.min(1, pct));
        num.setValue(num.getMin() + pct * (num.getMax() - num.getMin()));
    }

    private void updateVapeRangeValue(int mouseX) {
        if (draggingRange == null || vapeRangeW <= 0) return;
        double pct = Math.max(0, Math.min(1, (double) (mouseX - vapeRangeX) / vapeRangeW));
        if (draggingRange instanceof IntRangeSetting r) {
            int v = (int) Math.round(r.getAbsMin() + pct * (r.getAbsMax() - r.getAbsMin()));
            if (draggingRangeMax) r.setMax(v);
            else r.setMin(v);
        } else if (draggingRange instanceof DoubleRangeSetting r) {
            double v = r.getAbsMin() + pct * (r.getAbsMax() - r.getAbsMin());
            if (draggingRangeMax) r.setMax(v);
            else r.setMin(v);
        }
    }

    private boolean handleVapeSettingClick(int mouseX, int mouseY, int button, VapeGeom g,
                                           Setting<?> setting, int y) {
        int x = g.setX + 4;
        int w = g.setW - 8;
        if (!inRect(mouseX, mouseY, x, y, w, 19)) return false;
        if (setting instanceof HeaderSetting) return true;
        if (setting instanceof BooleanSetting bool) {
            bool.toggle();
            playClickSound(bool.isEnabled() ? 1.95f : 1.45f, 0.22f);
            addVapeRipple(x, y, w, 19);
            return true;
        }
        if (setting instanceof NumberSetting num) {
            draggingSlider = num;
            vapeSliderX = x + w - 8 - 70;
            vapeSliderW = 62;
            updateVapeSlider(mouseX);
            return true;
        }
        if (setting instanceof ModeSetting mode) {
            playClickSound(1.8f, 0.22f);
            addVapeRipple(x, y, w, 19);
            openVapeMode = openVapeMode == mode ? null : mode;
            vapeModeX = x;
            vapeModeY = y;
            vapeModeW = w;
            return true;
        }
        if (setting instanceof KeybindSetting key) {
            listeningSetting = key;
            playClickSound(1.8f, 0.22f);
            addVapeRipple(x, y, w, 19);
            return true;
        }
        if (setting instanceof ColorSetting color) {
            editingColor = color;
            editDrag = 0;
            playClickSound(1.8f, 0.22f);
            addVapeRipple(x, y, w, 19);
            return true;
        }
        if (setting instanceof com.anormal.client.lucid.StringSetting str) {
            editingString = editingString == str ? null : str;
            listeningSetting = null;
            return true;
        }
        int trackX = x + w - 8 - 70;
        if (setting instanceof IntRangeSetting range) {
            draggingRange = range;
            draggingRangeMax = pickVapeRangeHandle(mouseX, trackX, 62, range.getMin(), range.getMax(), range.getAbsMin(), range.getAbsMax());
            vapeRangeX = trackX;
            vapeRangeW = 62;
            updateVapeRangeValue(mouseX);
            return true;
        }
        if (setting instanceof DoubleRangeSetting range) {
            draggingRange = range;
            draggingRangeMax = pickVapeRangeHandle(mouseX, trackX, 62, range.getMin(), range.getMax(), range.getAbsMin(), range.getAbsMax());
            vapeRangeX = trackX;
            vapeRangeW = 62;
            updateVapeRangeValue(mouseX);
            return true;
        }
        // MultiSelect rows: the options underneath are the clickable ones.
        return setting instanceof MultiSelectSetting;
    }

    private boolean pickVapeRangeHandle(int mouseX, int trackX, int trackW,
                                        double min, double max, double absMin, double absMax) {
        double span = Math.max(1e-9, absMax - absMin);
        int pxMin = (int) Math.round(trackW * ((min - absMin) / span));
        int pxMax = (int) Math.round(trackW * ((max - absMin) / span));
        return Math.abs(mouseX - (trackX + pxMax)) < Math.abs(mouseX - (trackX + pxMin));
    }

    /** Screen rect of the open mode list: {x, y, w, h, rowHeight}. */
    private int[] vapeModeMenuGeom() {
        int rowH = 16;
        java.util.List<String> modes = openVapeMode.getModes();
        int listW = 90;
        for (String m : modes) {
            listW = Math.max(listW, textRenderer.getWidth(com.anormal.client.i18n.I18n.tr(m)) + 18);
        }
        int x = Math.max(4, Math.min(vapeModeX + vapeModeW - listW, width - listW - 4));
        int h = modes.size() * rowH + 6;
        int y = vapeModeY + 19;
        if (y + h > height - 4) y = Math.max(4, vapeModeY - h);
        return new int[] { x, y, listW, h, rowH };
    }

    private void renderVapeModeMenu(DrawContext context, int mouseX, int mouseY) {
        if (openVapeMode == null) return;
        int[] m = vapeModeMenuGeom();
        int x = m[0], y = m[1], listW = m[2], h = m[3], rowH = m[4];
        boolean glass = isGlassTheme();
        RenderUtils.fillRounded(context, x, y, listW, h, 5, glass ? glassRim() : ColorUtils.rgba(255, 255, 255, 30));
        RenderUtils.fillRounded(context, x + 1, y + 1, listW - 2, h - 2, 4,
                glass ? glassBody() : ColorUtils.rgba(26, 26, 26, 252));
        int ry = y + 3;
        for (String mode : openVapeMode.getModes()) {
            boolean selected = openVapeMode.is(mode);
            boolean hovered = inRect(mouseX, mouseY, x + 2, ry, listW - 4, rowH);
            if (selected || hovered) {
                RenderUtils.fillRounded(context, x + 2, ry, listW - 4, rowH, 3,
                        selected ? ThemeManager.getAccentColor()
                                : (glass ? glassTint(40) : ColorUtils.rgba(255, 255, 255, 20)));
            }
            RenderUtils.drawText(context, textRenderer, com.anormal.client.i18n.I18n.tr(mode), x + 8, ry + 5,
                    selected ? ThemeManager.VAPE_ON_ACCENT : (glass ? 0xFFE8F2F9 : 0xFFD6D6D6), false);
            ry += rowH;
        }
    }

    private boolean vapeClick(int mouseX, int mouseY, int button) {
        VapeGeom g = vapeGeom();
        vapeScroll = Math.max(0, Math.min(vapeScroll, g.maxScroll));
        vapeSetScroll = Math.max(0, Math.min(vapeSetScroll, g.maxSetScroll));

        // An open mode list swallows the click: pick the clicked entry, or close on an outside click.
        if (openVapeMode != null) {
            int[] m = vapeModeMenuGeom();
            if (inRect(mouseX, mouseY, m[0], m[1], m[2], m[3])) {
                int idx = (mouseY - (m[1] + 3)) / m[4];
                java.util.List<String> modes = openVapeMode.getModes();
                if (idx >= 0 && idx < modes.size()) {
                    openVapeMode.setMode(modes.get(idx));
                    playClickSound(1.85f, 0.22f);
                    addVapeRipple(m[0] + 2, m[1] + 3 + idx * m[4], m[2] - 4, m[4]);
                }
                openVapeMode = null;
                return true;
            }
            openVapeMode = null;
        }

        if (button == 0) {
            // Gear next to the sidebar logo opens the client customization panel (gui scale etc.).
            if (inRect(mouseX, mouseY, g.sbX + g.sbW - 24, g.sbY + 3, 20, 16)) {
                if (vapeSettingsModule != null) {
                    vapeSetScrollMemory.put(vapeSettingsModule.getName(), vapeSetScroll);
                }
                vapeSettingsModule = ModuleManager.getModule(com.anormal.client.module.impl.client.ClientSettings.class);
                vapeSetScroll = vapeSettingsModule == null ? 0
                        : Math.max(0, vapeSetScrollMemory.getOrDefault(vapeSettingsModule.getName(), 0));
                vapeSetRowsAnim = 0f;
                playClickSound(1.75f, 0.24f);
                addVapeRipple(g.sbX + g.sbW - 24, g.sbY + 3, 20, 16);
                return true;
            }
            // Resize the settings window from its edges/corner, like a desktop window.
            if (vapeSettingsModule != null) {
                boolean onRight = mouseX >= g.setX + g.setW - 5 && mouseX <= g.setX + g.setW + 2
                        && mouseY >= g.setY && mouseY <= g.setY + g.setH;
                boolean onBottom = mouseY >= g.setY + g.setH - 5 && mouseY <= g.setY + g.setH + 2
                        && mouseX >= g.setX && mouseX <= g.setX + g.setW;
                if (onRight && onBottom) { vapeResizeMode = 3; return true; }
                if (onRight) { vapeResizeMode = 1; return true; }
                if (onBottom) { vapeResizeMode = 2; return true; }
            }
            // Drag the floating panels by their header bars (settings panel first — it floats on top).
            if (vapeSettingsModule != null
                    && inRect(mouseX, mouseY, g.setX, g.setY, g.setW, VAPE_HEADER_H)
                    && !inRect(mouseX, mouseY, g.setX + g.setW - 24, g.setY + 4, 18, 14)) {
                vapeDragPanel = 2;
                vapeDragOffX = mouseX - g.setX;
                vapeDragOffY = mouseY - g.setY;
                return true;
            }
            if (inRect(mouseX, mouseY, g.panX, g.panY, g.panW, VAPE_HEADER_H)) {
                vapeDragPanel = 1;
                vapeDragOffX = mouseX - g.panX;
                vapeDragOffY = mouseY - g.panY;
                return true;
            }
            if (inRect(mouseX, mouseY, g.sbX, g.sbY, g.sbW, VAPE_HEADER_H)
                    && !inRect(mouseX, mouseY, g.sbX + g.sbW - 24, g.sbY + 3, 20, 16)) {
                vapeDragPanel = 3;
                vapeDragOffX = mouseX - g.sbX;
                vapeDragOffY = mouseY - g.sbY;
                return true;
            }
        }

        if (inRect(mouseX, mouseY, g.searchX, g.searchY, g.searchW, g.searchH)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        // Sidebar footer: HUD editor, waypoints, ported-source tabs.
        int footY = g.sbY + g.sbH - 26;
        int footW = (g.sbW - 28) / 3;
        if (inRect(mouseX, mouseY, g.sbX + 8, footY, footW, 18)) {
            playClickSound(1.8f, 0.24f);
            addVapeRipple(g.sbX + 8, footY, footW, 18);
            if (client != null) client.setScreen(new HudEditorScreen(this));
            return true;
        }
        if (inRect(mouseX, mouseY, g.sbX + 12 + footW, footY, footW, 18)) {
            playClickSound(1.8f, 0.24f);
            addVapeRipple(g.sbX + 12 + footW, footY, footW, 18);
            if (client != null) client.setScreen(new WaypointsScreen(this));
            return true;
        }
        if (inRect(mouseX, mouseY, g.sbX + 16 + footW * 2, footY, footW, 18)) {
            sourceView = !sourceView;
            if (sourceView) {
                if (currentCategory != Category.SPECTRE && currentCategory != Category.KURO) currentCategory = Category.SPECTRE;
            } else if (currentCategory == Category.SPECTRE || currentCategory == Category.KURO) {
                currentCategory = Category.COMBAT;
            }
            vapeScroll = 0;
            vapeSetScroll = 0;
            vapeRowsAnim = 0f;
            playSectionSound();
            vapeSettingsModule = null;
            openVapeMode = null;
            return true;
        }

        int catY = g.sbY + 27;
        for (Category cat : g.cats) {
            if (inRect(mouseX, mouseY, g.sbX + 5, catY, g.sbW - 10, 19)) {
                // Remember the old section's list position and reopen the new one where it was left.
                vapeScrollMemory.put(currentCategory, vapeScroll);
                currentCategory = cat;
                scrollOffset = 0;
                vapeScroll = Math.max(0, vapeScrollMemory.getOrDefault(cat, 0));
                vapeSetScroll = 0;
                vapeRowsAnim = 0f;
                playSectionSound();
                addVapeRipple(g.sbX + 5, catY, g.sbW - 10, 19);
                vapeSettingsModule = null;
                openVapeMode = null;
                expandedMode = null;
                expandedMulti = null;
                return true;
            }
            catY += VAPE_ROW_H;
        }

        if (inRect(mouseX, mouseY, g.panX, g.panY, g.panW, g.panH)) {
            int rowY = g.rowsTop + 4 - vapeScroll;
            for (Module module : g.modules) {
                if (mouseY >= g.rowsTop && mouseY < g.rowsBottom - 2
                        && inRect(mouseX, mouseY, g.panX + 4, rowY, g.panW - 8, 19)) {
                    boolean dots = mouseX >= g.panX + g.panW - 24;
                    if (button == 1 || dots) {
                        if (vapeSettingsModule != null) {
                            vapeSetScrollMemory.put(vapeSettingsModule.getName(), vapeSetScroll);
                        }
                        vapeSettingsModule = vapeSettingsModule == module ? null : module;
                        vapeSetScroll = vapeSettingsModule == null ? 0
                                : Math.max(0, vapeSetScrollMemory.getOrDefault(vapeSettingsModule.getName(), 0));
                        vapeSetRowsAnim = 0f;
                        openVapeMode = null;
                        editingString = null;
                        expandedMode = null;
                        expandedMulti = null;
                        playClickSound(1.75f, 0.22f);
                    } else {
                        module.toggle();
                        playClickSound(module.isEnabled() ? 1.95f : 1.45f, 0.22f);
                    }
                    addVapeRipple(g.panX + 4, rowY, g.panW - 8, 19);
                    return true;
                }
                rowY += VAPE_ROW_H;
            }
        }

        if (currentCategory.isCustomPanel()
                && inRect(mouseX, mouseY, g.panX + 4, g.panY + VAPE_HEADER_H + 4, g.panW - 8, g.panH - VAPE_HEADER_H - 8)) {
            int cpx = g.panX + 4;
            int cpy = g.panY + VAPE_HEADER_H + 4;
            int cpw = g.panW - 8;
            int cph = g.panH - VAPE_HEADER_H - 8;
            if (currentCategory == Category.DEVELOPER) {
                if (button == 0 && handleDeveloperClick(mouseX, mouseY)) return true;
            } else {
                handleKeybindsClick(mouseX, mouseY, button, cpx, cpy, cpw, cph);
                return true;
            }
        }

                // The crosshair preview window sits outside the settings panel, so it is hit-tested first.
        com.anormal.client.module.impl.render.CustomCrosshair xhairPreview = crosshairPreviewModule();
        if (xhairPreview != null) {
            int[] pr = crosshairPreviewGeom(g);
            if (inRect(mouseX, mouseY, pr[0], pr[1], pr[2], pr[3])) {
                if (button == 0 && inRect(mouseX, mouseY, pr[0] + pr[2] - 22, pr[1] + 3, 18, 14)) {
                    xhairPreview.preview.toggle();
                    playClickSound(1.45f, 0.22f);
                }
                return true;
            }
        }

        if (vapeSettingsModule != null && inRect(mouseX, mouseY, g.setX, g.setY, g.setW, g.setH)) {
            if (inRect(mouseX, mouseY, g.setX + g.setW - 24, g.setY + 4, 18, 14)) {
                if (vapeSettingsModule != null) {
                    vapeSetScrollMemory.put(vapeSettingsModule.getName(), vapeSetScroll);
                }
                vapeSettingsModule = null;
                openVapeMode = null;
                playClickSound(1.5f, 0.22f);
                addVapeRipple(g.setX + g.setW - 24, g.setY + 4, 18, 14);
                return true;
            }
            int setY = g.setRowsTop + 4 - vapeSetScroll;
            for (Setting<?> setting : g.settings) {
                if (mouseY >= g.setRowsTop && mouseY < g.setRowsBottom - 2) {
                    if (handleVapeSettingClick(mouseX, mouseY, button, g, setting, setY)) return true;
                    if (setting instanceof MultiSelectSetting ms) {
                        int optY = setY + 19;
                        for (String opt : ms.getOptions()) {
                            if (inRect(mouseX, mouseY, g.setX + 12, optY, g.setW - 24, 17)) {
                                ms.toggle(opt);
                                return true;
                            }
                            optY += 17;
                        }
                    }
                }
                setY += 19;
                if (setting instanceof MultiSelectSetting ms) setY += ms.getOptions().size() * 17;
            }
        }
        return true;
    }

    private boolean vapeMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        float scale = 1.0f;
        com.anormal.client.module.impl.client.ClientSettings cs =
                ModuleManager.getModule(com.anormal.client.module.impl.client.ClientSettings.class);
        if (cs != null && cs.guiScale != null) {
            scale = cs.guiScale.getValue().floatValue();
            if (scale < 0.4f || scale > 2.0f) scale = 1.0f;
        }
        if (lockedScale != null) scale = lockedScale;
        float ccx = width / 2.0f;
        float ccy = height / 2.0f;
        int smX = (int) ((mouseX - ccx) / scale + ccx);
        int smY = (int) ((mouseY - ccy) / scale + ccy);

        VapeGeom g = vapeGeom();
        int step = (int) Math.round(verticalAmount * 24.0);
        if (step == 0) step = verticalAmount > 0 ? 1 : -1;
        if (currentCategory == Category.KEYBINDS) {
            kbScroll = Math.max(0, Math.min(kbMaxScroll, kbScroll - step));
        } else if (vapeSettingsModule != null && inRect(smX, smY, g.setX, g.setY, g.setW, g.setH)) {
            vapeSetScroll = Math.max(0, Math.min(g.maxSetScroll, vapeSetScroll - step));
        } else {
            vapeScroll = Math.max(0, Math.min(g.maxScroll, vapeScroll - step));
        }
        // Persist immediately: the user may close the client without leaving the GUI first.
        saveVapeLayout();
        return true;
    }

    private void updateSliderValue(int mouseX, int contentX, int contentWidth) {
        if (draggingSlider == null) return;
        int setW = contentWidth - 36;
        int sliderWidth = 70;
        int sliderX = contentX + 16 + setW - sliderWidth - 6;

        double percent = (double) (mouseX - sliderX) / (double) sliderWidth;
        percent = Math.max(0.0, Math.min(1.0, percent));
        double value = draggingSlider.getMin() + (percent * (draggingSlider.getMax() - draggingSlider.getMin()));
        draggingSlider.setValue(value);
    }
}
