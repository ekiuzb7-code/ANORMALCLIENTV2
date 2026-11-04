package com.anormal.client.module;

import com.anormal.client.module.impl.client.*;
import com.anormal.client.module.impl.combat.*;
import com.anormal.client.module.impl.inventory.*;
import com.anormal.client.module.impl.legit.*;
import com.anormal.client.module.impl.movement.*;
import com.anormal.client.module.impl.optimization.*;
import com.anormal.client.module.impl.player.*;
import com.anormal.client.module.impl.render.*;
import com.anormal.client.module.impl.world.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();
    // Fast class -> instance lookup. getModule() is called from very hot mixin
    // hooks (per-block / per-face), so a linear stream scan over ~170 modules
    // was a real performance problem.
    private static final java.util.Map<Class<?>, Module> BY_CLASS = new java.util.HashMap<>();

    public static void init() {
        // --- COMBAT ---
        register(new AimAssist());
        register(new Aimbot());
        register(new AutoClicker());
        register(new Triggerbot());
        register(new BlockHit());
        register(new HitFlick());
        register(new HitSelect());
        register(new Reach());
        register(new RightClicker());
        register(new SilentAura());
        register(new KillAura());
        register(new TargetLock());
        register(new AutoCrystal());
        register(new AntiCrystal());
        register(new AutoBed());
        register(new BowAimbot());
        register(new AutoDisconnect());
        register(new Velocity());
        register(new WTap());
        register(new JumpReset());
        register(new Criticals());
        register(new KeepSprint());
        register(new FakeLagV2());
        register(new BackTrackV2());

        // --- MOVEMENT ---
        register(new Sprint());
        register(new NoJumpDelay());
        register(new InvMove());
        register(new SafeWalk());
        register(new AntiHunger());
        register(new Parkour());
        register(new Spider());
        register(new ElytraFly());
        register(new NoFall());
        register(new FastLadder());
        register(new Step());
        register(new FastSwim());
        register(new SneakTapBridging());
        register(new NoWeb());
        register(new NoSlowdown());
        register(new BoatFly());
        register(new Flight());

        // --- RENDER ---
        register(new Fullbright());
        register(new ESP());
        register(new ESPv2());
        register(new PlayerESP());
        register(new PlayerESPv2());
        register(new Effects());
        register(new Chams());
        register(new AntiBlind());
        register(new Tracers());
        register(new BlockOverlay());
        register(new NoHurtCam());
        register(new NoLevitation());
        register(new ClearWater());
        register(new FreeLook());
        register(new Health());
        register(new Indicators());
        register(new ItemESP());
        register(new NameTags());
        // Fully qualified on purpose: an old, no-longer-used
        // com.anormal.client.module.impl.legit.NoWeather may still exist in
        // checkouts where files were copied over instead of committed, and a
        // plain `new NoWeather()` then fails with "reference is ambiguous".
        register(new com.anormal.client.module.impl.render.NoWeather());
        register(new Zoom());
        register(new CustomCrosshair());
        register(new Projectiles());
        register(new SpawnerFinder());
        register(new StorageESP());
        register(new StashFinder());
        register(new DeathPoints());
        register(new EntityCulling());
        register(new ParticleOptimizer());
        register(new DynamicRenderDistance());
        register(new PerformanceOverlay());
        register(new Waypoints());
        register(new Trajectories());
        register(new PearlTrajectory());
        register(new Search());
        register(new AntiDebuff());
        register(new Arrows());
        register(new Explosions());
        register(new PortalFinder());
        register(new BedESP());
        register(new DonkeyFinder());
        register(new ShulkerFinder());
        register(new BaseFinder());
        register(new EndPortalFinder());
        register(new AncientCityFinder());
        register(new StrongholdFinder());
        register(new TrialChambersFinder());
        register(new ChunkFinder());
        register(new Bas3Finderv2());
        register(new Bas3esp());
        register(new AncientDebrisFinder());
        register(new OreESP());
        register(new ArmorStandESP());
        register(new ItemFrameESP());
        register(new PaintingESP());
        register(new BannerESP());
        register(new SignESP());
        register(new BlockESPCoord());
        register(new StructureESP());
        register(new VillageESP());
        register(new VehicleESP());
        register(new ProjectileESP());
        register(new LogoutSpots());
        register(new LogoutESP());
        register(new BaseCoords());
        register(new BaseManager());

        // --- PLAYER ---
        register(new FastPlace());
        register(new AutoTool());
        register(new NoClickDelay());
        register(new ChestStealer());
        register(new AutoEat());
        register(new StorageManager());
        register(new ChestLabels());
        register(new ShulkerLabels());
        register(new PortalManager());
        register(new AutoBaseHunt());

        // --- WORLD ---
        register(new Scaffold());
        register(new BlockIn());
        register(new MLG());
        register(new XRay());
        register(new NoFog());
        register(new InstantMine());
        register(new Tunnel());
        register(new AutoBuilder());
        register(new AutoBuilderV2());
        register(new FastBreak());
        register(new PacketMine());
        register(new Excavator());
        register(new Freecam());
        register(new AntiAFK());
        register(new AutoFish());
        register(new AutoAnchor());
        register(new AutoMace());
        register(new AutoPearl());
        register(new Clutch());
        register(new HitSwap());
        register(new ShieldBreaker());
        register(new WindCharge());
        register(new Surround());
        register(new AntiFireball());
        register(new PearlCatch());
        register(new TargetFilter());
        register(new Panic());
        register(new Blink());
        register(new FakeLag());
        register(new BackTrack());
        register(new CoordinateLogger());

        // --- INVENTORY ---
        register(new InvCleaner());
        register(new ArmorSwitch());
        register(new AutoArmor());
        register(new AutoHotbar());
        register(new AutoTotem());
        register(new InventoryManager());
        register(new InventoryFill());
        register(new Refill());
        register(new ThrowDebuff());
        register(new Throwpot());
        register(new CopperSell());

        // --- OPTIMIZATION ---
        // All of these start DISABLED on purpose: they trade visuals for frame
        // time, so the user opts in rather than being surprised by them.
        register(new NoRender());
        register(new FastRender());
        register(new FogCull());
        register(new SmartCulling());
        register(new BlockEntityOptimizer());
        register(new TextureOptimizer());
        register(new ParticleLimit());
        register(new ChunkUpdateLimiter());
        register(new NoAnimation());
        register(new RenderDistanceScaler());
        register(new SkyOptimizer());
        register(new EntityLodOptimizer());
        register(new AsyncChunkMeshing());

        // --- LEGIT & OVERLAYS ---
        register(new ArmorStatus());
        register(new Coords());
        register(new FPS());
        register(new Clock());
        register(new ReachDisplay());
        register(new PotionStatus());
        register(new DuelInfo());
        register(new InventoryOverlay());
        register(new PartyOverlay());
        register(new Radar());
        register(new Rearview());
        register(new TargetInfo());
        register(new BlockhitAnimation());
        register(new Compass());
        register(new HitColor());
        register(new InventoryBlur());
        register(new MouseDelayFix());
        register(new Scoreboard());
        register(new TimeChanger());
        register(new Weather());
        register(new BiomeHUD());
        register(new EnemyArmor());
        register(new EnemyInventory());
        register(new ToolDurability());
        register(new ArmorDurability());
        register(new FoodStatus());
        register(new ItemCounter());
        register(new BlockCounter());
        register(new EnchantHelper());
        register(new AnvilHelper());
        register(new MemoryHUD());
        register(new RenderStats());
        register(new DirectionHUD());
        register(new TravelDistance());
        register(new CoordinateShare());

        // --- CLIENT & SETTINGS ---
        register(new Timer());
        register(new TextGUI());
        register(new Notifications());
        register(new ClientSettings());
        register(new Macros());
        register(new ActionWheel());
        register(new FarmFpsOptimizer());
        register(new Friends());
        register(new Profiles());
        register(new AntiBot());
        register(new NameProtect());

        // ── Ported from Lucid Client (1.21.11, same mappings) ──
        register(new com.anormal.client.module.impl.combat.ShieldBreakerV2());
        register(new com.anormal.client.module.impl.combat.MaceBomber());
        register(new com.anormal.client.module.impl.combat.DoubleAnchor());
        register(new com.anormal.client.module.impl.combat.AnchorMacro());
        register(new com.anormal.client.module.impl.combat.LungeSwapper());
        register(new com.anormal.client.module.impl.combat.CrystalOptimizer());
        register(new com.anormal.client.module.impl.player.AutoLoot());
        register(new com.anormal.client.module.impl.player.AutoMine());
        register(new com.anormal.client.module.impl.player.AutoLog());
        register(new com.anormal.client.module.impl.player.AccuratePlacement());
        register(new com.anormal.client.module.impl.player.AutoSell());
        register(new com.anormal.client.module.impl.player.FakePay());
        register(new com.anormal.client.module.impl.player.FakeStats());
        register(new com.anormal.client.module.impl.player.SkinProtect());
        register(new com.anormal.client.module.impl.player.SkinShower());
        register(new com.anormal.client.module.impl.legit.MusicPlayer());
        register(new com.anormal.client.module.impl.client.HudTheme());
        register(new com.anormal.client.module.impl.player.NameShower());
        register(new com.anormal.client.module.impl.misc.WorthDisplay());
        register(new com.anormal.client.module.impl.misc.FakeItem());
        register(new com.anormal.client.module.impl.render.PearlTrajectoryV2());
        register(new com.anormal.client.module.impl.render.OverworldRay());
        register(new com.anormal.client.module.impl.kuro.KuroSusChunkFinder());
        register(new com.anormal.client.module.impl.world.RtpBaseFinder());
        register(new com.anormal.client.module.impl.client.NameProtectV2());

        // Watermark renders last so the logo is never covered by other HUD
        register(new Watermark());
        register(new PlayerLogger());

        // ── Ported from kuro (Mojmap -> Yarn 1.21.11): Category.KURO must contain
        //    every module until the source is fully converted. Class names are
        //    Kuro-prefixed so the duplicate-simple-name guard stays quiet; the
        //    modules keep their original display names.
        register(new com.anormal.client.module.impl.kuro.KuroVelocity());
        register(new com.anormal.client.module.impl.kuro.KuroAttackPackets());
        register(new com.anormal.client.module.impl.kuro.KuroAutoTotem());
        register(new com.anormal.client.module.impl.kuro.KuroCriticals());
        register(new com.anormal.client.module.impl.kuro.KuroAntiBot());
        register(new com.anormal.client.module.impl.kuro.KuroHoverTotem());
        register(new com.anormal.client.module.impl.kuro.KuroHitboxExpand());
        register(new com.anormal.client.module.impl.kuro.KuroAutoReplenish());
        register(new com.anormal.client.module.impl.kuro.KuroAutoDtap());
        register(new com.anormal.client.module.impl.kuro.KuroReach());
        register(new com.anormal.client.module.impl.kuro.KuroNoFall());
        register(new com.anormal.client.module.impl.kuro.KuroKeepSprint());
        register(new com.anormal.client.module.impl.kuro.KuroNoJumpDelay());
        register(new com.anormal.client.module.impl.kuro.KuroTriggerBot());
        register(new com.anormal.client.module.impl.kuro.KuroJumpReset());
        register(new com.anormal.client.module.impl.kuro.KuroWTap());
        register(new com.anormal.client.module.impl.kuro.KuroBacktrack());
        register(new com.anormal.client.module.impl.kuro.KuroAimAssist());
        register(new com.anormal.client.module.impl.kuro.KuroSpearSwap());
        register(new com.anormal.client.module.impl.kuro.KuroBreachSwap());
        register(new com.anormal.client.module.impl.kuro.KuroAutoShieldBreak());
        register(new com.anormal.client.module.impl.kuro.KuroAutoWeapon());
        register(new com.anormal.client.module.impl.kuro.KuroAutoArmor());
        register(new com.anormal.client.module.impl.kuro.KuroAutoBox());
        register(new com.anormal.client.module.impl.kuro.KuroAutoCobweb());
        register(new com.anormal.client.module.impl.kuro.KuroAutoMLG());
        register(new com.anormal.client.module.impl.kuro.KuroBowAim());
        register(new com.anormal.client.module.impl.kuro.KuroCrystalAura());
        register(new com.anormal.client.module.impl.kuro.KuroCrystalDtap());
        register(new com.anormal.client.module.impl.kuro.KuroFastCrystal());
        register(new com.anormal.client.module.impl.kuro.KuroHitbox());
        register(new com.anormal.client.module.impl.kuro.KuroSimpleCrystalAura());
        register(new com.anormal.client.module.impl.kuro.KuroSurround());
        register(new com.anormal.client.module.impl.kuro.KuroKillAura());
        register(new com.anormal.client.module.impl.kuro.KuroSilentAura());
        register(new com.anormal.client.module.impl.kuro.KuroAttackAura());
        register(new com.anormal.client.module.impl.kuro.KuroAutoMace());
        register(new com.anormal.client.module.impl.kuro.KuroAutoClicker());
        register(new com.anormal.client.module.impl.kuro.KuroHitSounds());
        register(new com.anormal.client.module.impl.kuro.KuroProjectileAimBot());
        register(new com.anormal.client.module.impl.kuro.KuroTotemPops());
        register(new com.anormal.client.module.impl.kuro.KuroAutoAnchor());
        register(new com.anormal.client.module.impl.kuro.KuroAnchorAura());
        register(new com.anormal.client.module.impl.kuro.KuroAnchorBomb());
        register(new com.anormal.client.module.impl.kuro.KuroSafeAnchor());

        // ── kuro movement tab (kuro's Category.MOVEMENT, one-to-one ports)
        register(new com.anormal.client.module.impl.kuro.KuroBhop());
        register(new com.anormal.client.module.impl.kuro.KuroBoatFly());
        register(new com.anormal.client.module.impl.kuro.KuroElytraFly());
        register(new com.anormal.client.module.impl.kuro.KuroElytraHop());
        register(new com.anormal.client.module.impl.kuro.KuroElytraSwap());
        register(new com.anormal.client.module.impl.kuro.KuroEntitySpeed());
        register(new com.anormal.client.module.impl.kuro.KuroFlight());
        register(new com.anormal.client.module.impl.kuro.KuroNoSlow());
        register(new com.anormal.client.module.impl.kuro.KuroNoWeb());
        register(new com.anormal.client.module.impl.kuro.KuroSafeWalk());
        register(new com.anormal.client.module.impl.kuro.KuroScaffold());
        register(new com.anormal.client.module.impl.kuro.KuroSprint());
        register(new com.anormal.client.module.impl.kuro.KuroStep());
        
        // ── kuro utility/world tab (kuro's utility & world modules, one-to-one ports)
        register(new com.anormal.client.module.impl.kuro.KuroAutoWalk());
        register(new com.anormal.client.module.impl.kuro.KuroFastPlace());
        register(new com.anormal.client.module.impl.kuro.KuroChestStealer());
        register(new com.anormal.client.module.impl.kuro.KuroToggleAll());
        register(new com.anormal.client.module.impl.kuro.KuroStopwatch());
        register(new com.anormal.client.module.impl.kuro.KuroAutoTool());
        register(new com.anormal.client.module.impl.kuro.KuroAutoEat());
        register(new com.anormal.client.module.impl.kuro.KuroAutoFish());
        register(new com.anormal.client.module.impl.kuro.KuroMacro());
        register(new com.anormal.client.module.impl.kuro.KuroMacroRecord());
        register(new com.anormal.client.module.impl.kuro.KuroInvSorter());
        register(new com.anormal.client.module.impl.kuro.KuroPearlCatch());
        register(new com.anormal.client.module.impl.kuro.KuroPearlFinder());
        register(new com.anormal.client.module.impl.kuro.KuroAutoTunnel());
        register(new com.anormal.client.module.impl.kuro.KuroDingDongBuilder());
        register(new com.anormal.client.module.impl.kuro.KuroFakePlayer());
        register(new com.anormal.client.module.impl.kuro.KuroAncientDebris());
        register(new com.anormal.client.module.impl.kuro.KuroBaritoneControl());
        register(new com.anormal.client.module.impl.kuro.KuroAutoMiner());
        register(new com.anormal.client.module.impl.kuro.KuroAutoBuild());
        register(new com.anormal.client.module.impl.kuro.KuroFullbright());
        register(new com.anormal.client.module.impl.kuro.KuroFoundList());
        register(new com.anormal.client.module.impl.kuro.KuroTracers());
        register(new com.anormal.client.module.impl.kuro.KuroClock());
        register(new com.anormal.client.module.impl.kuro.KuroSnowflakes());
        register(new com.anormal.client.module.impl.kuro.KuroReachCircle());
        register(new com.anormal.client.module.impl.kuro.KuroPlayerInfo());
        register(new com.anormal.client.module.impl.kuro.KuroItemEsp());
        register(new com.anormal.client.module.impl.kuro.KuroNewChunks());
        register(new com.anormal.client.module.impl.kuro.KuroNametags());
        register(new com.anormal.client.module.impl.kuro.KuroTrailTracker());
        register(new com.anormal.client.module.impl.kuro.KuroPlayerIndicator());
        register(new com.anormal.client.module.impl.kuro.KuroStashFinder());
        register(new com.anormal.client.module.impl.kuro.KuroGeodeEsp());
        register(new com.anormal.client.module.impl.kuro.KuroBlockEsp());
        register(new com.anormal.client.module.impl.kuro.KuroHud());
        register(new com.anormal.client.module.impl.kuro.KuroPlayerTrails());
        register(new com.anormal.client.module.impl.kuro.KuroCoordShare());
        register(new com.anormal.client.module.impl.kuro.KuroJumpBubbles());
        register(new com.anormal.client.module.impl.kuro.KuroLogoutSpots());
        register(new com.anormal.client.module.impl.kuro.KuroNoBreakDelay());
        register(new com.anormal.client.module.impl.kuro.KuroFreeze());
        register(new com.anormal.client.module.impl.kuro.KuroChatTimestamps());
        register(new com.anormal.client.module.impl.kuro.KuroChatMentions());
        register(new com.anormal.client.module.impl.kuro.KuroUsernameHider());
        register(new com.anormal.client.module.impl.kuro.KuroSkyModule());
        register(new com.anormal.client.module.impl.kuro.KuroSidebarUi());
        register(new com.anormal.client.module.impl.kuro.KuroBlobUi());
        register(new com.anormal.client.module.impl.kuro.KuroGlassUi());
        register(new com.anormal.client.module.impl.kuro.KuroRadialMenu());
        register(new com.anormal.client.module.impl.kuro.KuroConfigModule());
        register(new com.anormal.client.module.impl.kuro.KuroKnifeModule());
        register(new com.anormal.client.module.impl.kuro.KuroFreecam());
    }

    public static void register(Module module) {
        modules.add(module);
        BY_CLASS.put(module.getClass(), module);
    }

    public static List<Module> getModules() {
        return modules;
    }

    public static List<Module> getModulesByCategory(Category category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T getModule(Class<T> clazz) {
        return (T) BY_CLASS.get(clazz);
    }

    public static Module getModuleByName(String name) {
        if (name == null) return null;
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name.trim())) return m;
        }
        return null;
    }

    public static void onTick() {
        // Kuro tick-start hook (kuro START_CLIENT_TICK'dagi ish): ushlangan hujum
        // paketlari hech qanday modul ishga tushmasidan oldin chiqadi, vanilla
        // harakat paketi bilan bir xil sim-tartibida.
        MinecraftClient kuroMc = MinecraftClient.getInstance();
        try {
            // Rotatsiya delta trackeri: har bir tick HEAD'ida yangilanadi, graf
            // kutayotgan modullar ishlaganda aynan "o'tgan tick'dagi delta"ni
            // ko'rsatadi (Grim DuplicateRotPlace bypass). Dunyadan chiqib ketganda
            // namunalar eskiradi — clear qilamiz.
            if (kuroMc != null && kuroMc.player != null) {
                com.anormal.client.util.kuro.KuroRotationConvergenceTracker.update();
            } else {
                com.anormal.client.util.kuro.KuroRotationConvergenceTracker.reset();
            }
            com.anormal.client.util.kuro.KuroSwapStateManager.tickEarly();
            com.anormal.client.util.kuro.KuroTickPhase.startTick();
            com.anormal.client.util.kuro.KuroSwapLimit.nextTick();
            com.anormal.client.util.kuro.KuroServerSync.nextTick();
            com.anormal.client.module.impl.kuro.KuroAttackPackets.flush();
            com.anormal.client.util.kuro.KuroCombatControl.tick();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        for (Module module : modules) {
            try {
                // Background hooks run even when the module is off (toggle keys, caches).
                module.onTickAlways();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (module.isEnabled()) {
                try {
                    module.onTick();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        // Tick POST: tayyorlangan hotbar qaytishlari modullar ishini chalg'itmasligi
        // uchun barcha onTick'lar bitib keyin bajariladi.
        try {
            com.anormal.client.util.kuro.KuroSwapStateManager.tickPost();
            if (kuroMc == null || kuroMc.player == null) {
                com.anormal.client.util.kuro.KuroSwapStateManager.clear();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Tick START phase (START_CLIENT_TICK), i.e. before vanilla reads the input and moves the player.
     * kuro's modules hang their movement work off this phase (kuro: {@code onPreTick}), so a port that
     * sets velocity or holds a key does it in the same order the original did.
     */
    public static void onPreTick() {
        for (Module module : modules) {
            if (!module.isEnabled()) continue;
            try {
                module.onPreTick();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** Measured frame of each enabled overlay this frame: {x, y, w, h}. */
    public static final java.util.Map<Module, int[]> FRAMES = new java.util.IdentityHashMap<>();

    public static void onRender2D(DrawContext context, float tickDelta) {
        // Snapshot the interpolated render camera once per frame so every
        // marker module projects against the same camera the world was drawn
        // with. Without this the markers slide while moving.
        try {
            com.anormal.client.util.ProjectionUtil.beginFrame(tickDelta);
        } catch (Throwable ignored) {
        }
        // Each overlay is measured while it draws, so HudStyle can frame it afterwards. The
        // measurement is also what the HUD editor uses, which is why modules that draw through
        // DrawContext directly now report a correct box.
        com.anormal.client.util.HudStyle.beginPass();
        FRAMES.clear();
        for (Module module : modules) {
            if (!module.isEnabled()) continue;
            try {
                com.anormal.client.util.RenderUtils.beginMeasure();
                module.onRender2D(context, tickDelta);
                int[] frame = com.anormal.client.util.RenderUtils.endMeasure();
                if (frame != null && frame[2] > 0 && frame[3] > 0) {
                    FRAMES.put(module, frame);
                }
            } catch (Exception e) {
                e.printStackTrace();
                // A module that threw mid-draw leaves the measurement open; close it so the
                // next overlay is not measured against this one's bounds.
                com.anormal.client.util.RenderUtils.endMeasure();
            }
        }
        com.anormal.client.util.HudStyle.endPass();
        com.anormal.client.util.HudStyle.drawFrames(context, FRAMES);
    }

    /**
     * The global key event for any module toggle/activation.
     * For Hold mode the real handling lives in {@link com.anormal.client.AnormalClient}'s
     * per-tick key poller; this path only handles the press-edge for Toggle mode.
     */
    public static void onKeyPressed(int keyCode) {
        if (keyCode <= 0) return;
        for (Module module : modules) {
            if (module.getKey() == keyCode && module.isToggleMode()) {
                module.toggle();
            }
        }
    }
}
