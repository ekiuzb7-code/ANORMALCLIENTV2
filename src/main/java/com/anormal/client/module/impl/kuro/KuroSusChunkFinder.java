package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * kuro {@code SusChunkFinder} porti (dev/kuro/client/module/impl/SusChunkFinder.java —
 * haqiqiy kuro manbasidan 1:1 tarjima; avvalgi fayldaki glazed ChunkFinderV5
 * asosidagi kod almashtirildi).
 *
 * <p>Yuklangan har chunkga o'yinchi bazasi borligi ehtimolini beradi va eng
 * shubhali chunklar ustida foizli belgi ko'rsatadi. Har bir dalil turi (mashinalar,
 * zaxira, dehqonchilik, qurilgan bloklar, tuproq ostidagi mash'alalar, item ramkalari
 * va moblar, eski o'sish) 0..1 kuchga aylanadi va kuchlar mustaqil dalillar kabi
 * birlashtiriladi (noisy-OR), shuning uchun bir necha xil dalil foizni bitta turdan
 * tezro ko'taradi. Qo'shni 8 chunk ozgina qo'shadi, chunki bazalar chunk chegaralarini
 * kesib o'tadi. Foiz geyristik ball, haqiqiy ehtimol emas.</p>
 *
 * <p>Anormaldagi farqlar (API cheklovlari, mohiyatan o'zgarmas): kuro'dagi
 * Setting.Button/Text Anormalda yo'q — "Rescan all", "Discord test" va webhook/token
 * saqlash bitta Action rejimi bilan bajariladi (clipboard'dan oladi). Kuro'dagi
 * SdfText/Ui/Look/Projector/Gfx/WorldGfx utiltlari Anormalda yo'q — matn
 * vanilla TextRenderer bilan, 3D chizish {@link WorldRender3D} bilan. Kuro'dagi
 * "kuro:alert" ovozi assets yo'qligi uchun vanilla EXP pickup ovozi ishlatiladi.
 * HudPanel (HUD layout muharriri) port qilinmagan — panellar sabit joyda.
 * Kuro'dagi Finds (Found list) utiliga ulanish yo'q.</p>
 */
public class KuroSusChunkFinder extends Module {
    private enum Sig {
        //        label            ishonchlilik k   struktura masshtabi
        MACHINE("machines",      0.85, 3,  0.4),
        STORAGE("storage",       0.70, 4,  0.3),
        FARM   ("farming",       0.55, 8,  0.3),
        PLACED ("built blocks",  0.55, 14, 0.2),
        LIGHT  ("torches",       0.50, 8,  0.2),
        ENTITY ("frames/mobs",   0.55, 5,  0.5),
        GROWTH ("old growth",    0.20, 12, 1.0),
        REDSTONE("redstone",     0.75, 6,  0.4),
        PORTAL ("portals",       0.55, 6,  0.3),
        DECOR  ("crafted/decor", 0.50, 10, 0.3);

        final String label;
        final double reliability, k, structScale;
        Sig(String label, double reliability, double k, double structScale) {
            this.label = label; this.reliability = reliability; this.k = k; this.structScale = structScale;
        }
    }

    // skan paytida topilgan marker bitlari — tabiiy strukturani aniqlash uchun
    private static final int M_COBWEB = 1, M_RAIL = 2, M_STRUCT = 4, M_WOOD = 8, M_FENCE = 16,
            M_PLAYER = 32, M_SHIPWOOD = 64, M_TRIALCORE = 128, M_TUFF = 256, M_COPPERFX = 512,
            M_DWOOD = 1024, M_DFENCE = 2048;

    /** Bir chunk ichidagi shaxta dalili: o'rgimchak to'ri + rels/yog'och, yoki rels + panjara + taxta kombinatsiyasi. */
    private static boolean isMine(int m) {
        boolean web = (m & M_COBWEB) != 0, rail = (m & M_RAIL) != 0;
        boolean wood = (m & M_WOOD) != 0, fence = (m & M_FENCE) != 0;
        boolean deepWood = (m & M_DWOOD) != 0, deepFence = (m & M_DFENCE) != 0;
        return (web && (rail || wood || fence)) || (rail && wood && fence)
                || (deepWood && deepFence) || (rail && (deepWood || deepFence));
    }

    /** Trial chamber dalili: trial spawner / vault / heavy core, yoki tuff g'isht + mis detallar. */
    private static boolean isTrial(int m) {
        return (m & M_TRIALCORE) != 0 || ((m & M_TUFF) != 0 && (m & M_COPPERFX) != 0);
    }

    private static final Set<String> MACHINE_IDS = Set.of("hopper", "dropper", "dispenser", "observer", "piston",
            "sticky_piston", "repeater", "comparator", "tnt", "daylight_detector", "beacon", "enchanting_table",
            "redstone_lamp", "note_block", "jukebox", "crafter");
    private static final Set<String> REDSTONE_IDS = Set.of("redstone_wire", "lever", "tripwire_hook", "target",
            "slime_block", "honey_block", "lightning_rod", "sculk_sensor", "calibrated_sculk_sensor", "redstone_torch",
            "redstone_wall_torch", "powered_rail", "activator_rail");
    private static final Set<String> DECOR_IDS = Set.of("flower_pot", "bookshelf", "chiseled_bookshelf", "lectern",
            "brewing_stand", "cauldron", "water_cauldron", "lava_cauldron", "grindstone", "lodestone", "respawn_anchor",
            "conduit", "bricks", "smooth_stone", "quartz_block", "chiseled_quartz_block", "quartz_pillar",
            "smooth_quartz", "polished_andesite", "polished_diorite", "polished_granite", "decorated_pot",
            "beehive", "jack_o_lantern", "carved_pumpkin", "cake", "end_crystal");
    private static final Set<String> FARM_IDS = Set.of("farmland", "wheat", "carrots", "potatoes", "beetroots",
            "nether_wart", "composter");
    private static final Set<String> PLACED_IDS = Set.of("crafting_table", "furnace", "blast_furnace", "smoker",
            "smithing_table", "fletching_table", "cartography_table", "loom", "stonecutter", "anvil", "chipped_anvil",
            "damaged_anvil", "ladder", "scaffolding", "glass", "tinted_glass", "glass_pane", "obsidian", "iron_block",
            "gold_block", "diamond_block", "emerald_block", "netherite_block", "lapis_block", "redstone_block");
    private static final Set<String> STRUCT_IDS = Set.of("end_portal_frame", "bell", "reinforced_deepslate",
            "sculk_shrieker", "trial_spawner", "vault", "nether_brick_fence", "gilded_blackstone", "purpur_block",
            "prismarine_bricks", "chiseled_sandstone", "crying_obsidian");

    // ---- sozlamalar ----
    private final NumberSetting range = new NumberSetting("Range (chunks)", "Qidiruv radiusi (chunk)", 6.0, 2.0, 12.0, 1.0);
    private final NumberSetting scanSpeed = new NumberSetting("Scan speed (chunks/tick)", "Skan tezligi: tickdagi chunk soni", 3.0, 1.0, 40.0, 1.0);
    private final NumberSetting passDelay = new NumberSetting("Rescan delay (ticks)", "Yangi pass oradagi tick kutish", 40.0, 0.0, 40.0, 5.0);
    private final BooleanSetting skipScanned = new BooleanSetting("Skip scanned chunks", "Har chunk bir marta skanlanadi", true);
    private final BooleanSetting progress = new BooleanSetting("Scan progress", "Ekranda skan holati", true);
    private final NumberSetting minPercent = new NumberSetting("Min % to highlight", "Kam sal foiz bật-ni belgilash", 30.0, 10.0, 90.0, 5.0);
    private final NumberSetting maxHighlights = new NumberSetting("Max highlights", "Maksimal belgilar soni", 60.0, 5.0, 200.0, 5.0);
    private final NumberSetting maxDistance = new NumberSetting("Max distance", "Maksimal masofa (blok)", 800.0, 100.0, 3000.0, 100.0);
    private final BooleanSetting flatFloor = new BooleanSetting("Flat at world bottom", "Belgini dunyo pastiga tekis qo'yish", true);
    private final BooleanSetting atBase = new BooleanSetting("Marker at base height", "Belgini dalillar haqiqiy balandligida", true);
    private final BooleanSetting indicator = new BooleanSetting("Corner indicator", "Burchak ko'rsatkichi", true);
    private final NumberSetting baseAt = new NumberSetting("Base indicator at %", "Baza ko'rsatkichi uchun foiz", 50.0, 20.0, 95.0, 5.0);
    private final BooleanSetting hoverInfo = new BooleanSetting("Hover info", "Nishonga kelib qolgan chunk sabablari", true);
    private final BooleanSetting chatAlert = new BooleanSetting("Chat alert", "Chat ogohlantirish", true);
    private final BooleanSetting soundAlert = new BooleanSetting("Alert sound", "Ovoz ogohlantirish", false);
    private final NumberSetting alertAt = new NumberSetting("Alert at %", "Ogohlantirish foizi", 60.0, 20.0, 95.0, 5.0);
    private final BooleanSetting discordAlert = new BooleanSetting("Discord alert", "Discord ogohlantirish", true);
    private final BooleanSetting discordPlayers = new BooleanSetting("Discord: live players", "Discord: jonli o'yinchilar", false);
    private final BooleanSetting flagPlayers = new BooleanSetting("Flag live players", "Jonli o'yinchilarni belgilash", true);
    private final BooleanSetting ignoreFriends = new BooleanSetting("Ignore friends", "Do'stllarigni hisobga olmaslik", true);
    private final BooleanSetting playerChat = new BooleanSetting("Player chat alert", "Jonli o'yinchi chat ogohlantirish", true);
    private final BooleanSetting playerSound = new BooleanSetting("Player alert sound", "O'yinchi ovoz ogohlantirish", true);
    private final NumberSetting playerKeep = new NumberSetting("Player flag time (s)", "O'yinchi belgisi saqlash vaqt", 120.0, 10.0, 600.0, 10.0);
    private final BooleanSetting ignoreStructures = new BooleanSetting("Ignore structures", "Tabiiy strukturani hisobga olmaslik", true);
    private final BooleanSetting regionMap = new BooleanSetting("Region map", "Mintaqa xaritasi", true);
    private final NumberSetting mapRadius = new NumberSetting("Map radius (chunks)", "Xarita radiusi (chunk)", 12.0, 4.0, 24.0, 2.0);
    private final NumberSetting mapCell = new NumberSetting("Map cell size", "Xarita katakcha o'lchami", 4.0, 2.0, 8.0, 1.0);
    private final BooleanSetting tracerBest = new BooleanSetting("Tracer to best", "Eng shubhalisiga tracer", false);
    private final BooleanSetting machines = new BooleanSetting("Sig: machines", "Dalil: mashinalar", true);
    private final BooleanSetting storage = new BooleanSetting("Sig: storage", "Dalil: zaxira", true);
    private final BooleanSetting farming = new BooleanSetting("Sig: farming", "Dalil: dehqonchilik", true);
    private final BooleanSetting placed = new BooleanSetting("Sig: built blocks", "Dalil: qurilgan bloklar", true);
    private final BooleanSetting light = new BooleanSetting("Sig: torches", "Dalil: mash'alalar", true);
    private final BooleanSetting entities = new BooleanSetting("Sig: frames/mobs", "Dalil: ramkalar/moblar", true);
    private final BooleanSetting growth = new BooleanSetting("Sig: old growth", "Dalil: eski o'sish", true);
    private final BooleanSetting redstone = new BooleanSetting("Sig: redstone", "Dalil: redstone", true);
    private final BooleanSetting portals = new BooleanSetting("Sig: portals", "Dalil: portalaji", true);
    private final BooleanSetting decor = new BooleanSetting("Sig: crafted/decor", "Dalil: tayyor/dekor", true);
    private final BooleanSetting pets = new BooleanSetting("Sig: pets/leashed", "Dalil: uy hayvonlari/lugmst", true);
    private final NumberSetting wMachines = new NumberSetting("Weight: machines %", "Og'irlig: mashinalar", 100.0, 0.0, 200.0, 10.0);
    private final NumberSetting wStorage = new NumberSetting("Weight: storage %", "Og'irlig: zaxira", 100.0, 0.0, 200.0, 10.0);
    private final NumberSetting wFarming = new NumberSetting("Weight: farming %", "Og'irlig: dehqonchilik", 100.0, 0.0, 200.0, 10.0);
    private final NumberSetting wPlaced = new NumberSetting("Weight: built blocks %", "Og'irlig: qurilgan bloklar", 100.0, 0.0, 200.0, 10.0);
    private final NumberSetting wLight = new NumberSetting("Weight: torches %", "Og'irlig: mash'alalar", 100.0, 0.0, 200.0, 10.0);
    private final NumberSetting wEntities = new NumberSetting("Weight: frames/mobs %", "Og'irlig: ramkalar/moblar", 100.0, 0.0, 200.0, 10.0);
    private final NumberSetting wGrowth = new NumberSetting("Weight: old growth %", "Og'irlig: eski o'sish", 100.0, 0.0, 200.0, 10.0);
    private final NumberSetting wRedstone = new NumberSetting("Weight: redstone %", "Og'irlig: redstone", 100.0, 0.0, 200.0, 10.0);
    private final NumberSetting wPortals = new NumberSetting("Weight: portals %", "Og'irlig: portalaji", 100.0, 0.0, 200.0, 10.0);
    private final NumberSetting wDecor = new NumberSetting("Weight: crafted/decor %", "Og'irlig: tayyor/dekor", 100.0, 0.0, 200.0, 10.0);
    // kuro'dagi Setting.Button/Text o'rniga: amal onEnable'da bir marta bajariladi (Anormal API cheklovi)
    private final ModeSetting action = new ModeSetting("Action",
            "Bir marta bajariladigan amal (onEnable): webhoek/token clipboard'dan oladi",
            "Off", "Off", "Rescan all chunks", "Discord: test", "Discord: save webhook", "Discord: save bot token", "Discord: save user ID");

    private static final int LIVE = 0xFF38D9FF;
    private static final double LIVE_RELIABILITY = 0.60;

    private static final int BG = 0xC0101010, BORDER = 0xFF2A2A2A, TEXT = 0xFFECEFF1, MUTED = 0xFF9E9E9E, ACCENT = 0xFF7C4DFF;
    private static final int GOOD = 0xFF66BB6A, WARN = 0xFFFFB74D, BAD = 0xFFEF5350, ORANGE = 0xFFF57C00, TRACK = 0x40FFFFFF;

    /** Chunkda ko'rilgan jonli o'yinchi; chunk "flag vaqti" o'tguncha belgilangan qoladi. */
    private static final class Spot {
        String name;
        double x, y, z;
        long last;
        Spot(String name, double x, double y, double z, long last) { this.name = name; this.x = x; this.y = y; this.z = z; this.last = last; }
    }

    private static final class Info {
        final int cx, cz;
        final double[] counts = new double[Sig.values().length];
        int markers;
        double hx, hy, hz, hw;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double own, pct;
        String reasons = "";
        final List<String[]> rows = new ArrayList<>();
        int signals;
        Spot live;
        boolean structure, mine, shipRaw, ship, trial;

        Info(int cx, int cz) { this.cx = cx; this.cz = cz; }

        void add(Sig s, double n, double x, double y, double z) {
            counts[s.ordinal()] += n;
            double base = switch (s) {
                case MACHINE, STORAGE, REDSTONE -> 3.0;
                case PORTAL -> 0.5;
                case FARM -> 2.0;
                case GROWTH -> 0.2;
                default -> 1.0;
            };
            double w = base * n;
            hx += x * w; hy += y * w; hz += z * w; hw += w;
            if (s != Sig.GROWTH) { minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
        }

        double midX() { return hw > 0 ? hx / hw : cx * 16 + 8; }
        double midZ() { return hw > 0 ? hz / hw : cz * 16 + 8; }
        double midY(double fallback) { return hw > 0 ? hy / hw : fallback; }
        long key() { return ChunkPos.toLong(cx, cz); }

        /** Belgilash hajmi: butun chunk poyasi, pastki dalildan bir oz past, yuqori dalildan bir oz baland. */
        void box(double[] out, double fallbackY) {
            double lo = minY <= maxY ? minY : fallbackY - 2, hi = minY <= maxY ? maxY : fallbackY + 3;
            lo -= 1.0; hi += 2.0;
            if (hi - lo < 6) { double m = (lo + hi) / 2; lo = m - 3; hi = m + 3; }
            out[0] = cx * 16.0; out[1] = lo; out[2] = cz * 16.0;
            out[3] = cx * 16.0 + 16; out[4] = hi; out[5] = cz * 16.0 + 16;
        }
    }

    private final Map<Block, Sig> classes = new HashMap<>();
    private final Map<Block, Integer> markers = new HashMap<>();

    private final Map<Long, Info> infos = new HashMap<>();
    private Map<Long, Info> fresh = new HashMap<>();
    private volatile List<Info> display = List.of();
    private final Set<Long> alerted = new HashSet<>();
    private final Set<Long> mineChunks = new HashSet<>();
    private final Set<Long> trialChunks = new HashSet<>();
    private final List<long[]> queue = new ArrayList<>();
    private final Map<Long, Integer> scanned = new HashMap<>();
    private int queuePos, timer;
    private World lastWorld;
    private final double[] bx = new double[6];
    private final Map<Long, Spot> liveSpots = new HashMap<>();
    private final Map<UUID, Long> seenAt = new HashMap<>();

    public KuroSusChunkFinder() {
        super("Sus Chunk Finder", "Rates loaded chunks for player bases and shows the worst ones (kuro port)", Category.KURO);
        addSetting(range); addSetting(scanSpeed); addSetting(passDelay); addSetting(skipScanned); addSetting(action);
        addSetting(progress); addSetting(minPercent); addSetting(maxHighlights); addSetting(maxDistance);
        addSetting(flatFloor); addSetting(atBase); addSetting(indicator); addSetting(baseAt); addSetting(hoverInfo);
        addSetting(chatAlert); addSetting(soundAlert); addSetting(alertAt); addSetting(discordAlert); addSetting(discordPlayers);
        addSetting(flagPlayers); addSetting(ignoreFriends); addSetting(playerChat); addSetting(playerSound); addSetting(playerKeep);
        addSetting(ignoreStructures); addSetting(regionMap); addSetting(mapRadius); addSetting(mapCell); addSetting(tracerBest);
        addSetting(machines); addSetting(storage); addSetting(farming); addSetting(placed); addSetting(light);
        addSetting(entities); addSetting(growth); addSetting(redstone); addSetting(portals); addSetting(decor); addSetting(pets);
        addSetting(wMachines); addSetting(wStorage); addSetting(wFarming); addSetting(wPlaced); addSetting(wLight);
        addSetting(wEntities); addSetting(wGrowth); addSetting(wRedstone); addSetting(wPortals); addSetting(wDecor);

        for (Block b : Registries.BLOCK) {
            String p = Registries.BLOCK.getId(b).getPath();
            Sig s = classify(p);
            if (s != null) classes.put(b, s);
            int bits = 0;
            if (s == Sig.MACHINE || s == Sig.FARM || s == Sig.LIGHT || s == Sig.REDSTONE || s == Sig.PORTAL) bits |= M_PLAYER;
            if (s == Sig.PLACED) bits |= (p.endsWith("_planks") || p.endsWith("_trapdoor") || p.endsWith("_fence")) ? M_SHIPWOOD : M_PLAYER;
            if (p.equals("cobweb")) bits |= M_COBWEB;
            if (p.endsWith("rail")) bits |= M_RAIL;
            if (STRUCT_IDS.contains(p)) bits |= M_STRUCT;
            if (p.equals("trial_spawner") || p.equals("vault") || p.equals("heavy_core")) bits |= M_TRIALCORE;
            if (p.contains("tuff_brick") || p.startsWith("polished_tuff") || p.startsWith("chiseled_tuff")) bits |= M_TUFF;
            if (p.contains("copper_bulb") || p.contains("copper_grate") || p.contains("copper_door")
                    || p.contains("copper_trapdoor") || p.contains("chiseled_copper")) bits |= M_COPPERFX;
            if (p.equals("oak_planks") || p.equals("dark_oak_planks")) bits |= M_WOOD;
            if (p.equals("oak_fence") || p.equals("dark_oak_fence")) bits |= M_FENCE;
            if (bits != 0) markers.put(b, bits);
        }
        classes.put(Blocks.AMETHYST_CLUSTER, Sig.GROWTH);
    }

    private static Sig classify(String p) {
        if (MACHINE_IDS.contains(p)) return Sig.MACHINE;
        if (REDSTONE_IDS.contains(p) || p.endsWith("_button") || p.endsWith("_pressure_plate")) return Sig.REDSTONE;
        if (p.equals("nether_portal")) return Sig.PORTAL;
        if (DECOR_IDS.contains(p) || p.startsWith("stripped_") || p.endsWith("_candle") || p.equals("candle")
                || p.endsWith("_skull") || p.endsWith("_head") || p.endsWith("_carpet")) return Sig.DECOR;
        if (FARM_IDS.contains(p)) return Sig.FARM;
        if (PLACED_IDS.contains(p) || p.endsWith("planks") || p.endsWith("_bed") || p.endsWith("_wool")
                || p.endsWith("_concrete") || p.endsWith("glazed_terracotta") || p.endsWith("_door")
                || p.endsWith("_trapdoor") || p.endsWith("_sign") || p.endsWith("_fence_gate")
                || (p.endsWith("_fence") && !p.startsWith("nether_brick")) || p.endsWith("_stained_glass")
                || p.endsWith("_stained_glass_pane") || p.endsWith("_banner")) return Sig.PLACED;
        if (p.endsWith("torch") || p.endsWith("lantern") || p.equals("glowstone") || p.equals("shroomlight")
                || p.equals("end_rod") || p.equals("campfire") || p.equals("soul_campfire")) return Sig.LIGHT;
        return null;
    }

    private double weight(Sig s) {
        return switch (s) {
            case MACHINE -> wMachines.getValue();
            case STORAGE -> wStorage.getValue();
            case FARM -> wFarming.getValue();
            case PLACED -> wPlaced.getValue();
            case LIGHT -> wLight.getValue();
            case ENTITY -> wEntities.getValue();
            case GROWTH -> wGrowth.getValue();
            case REDSTONE -> wRedstone.getValue();
            case PORTAL -> wPortals.getValue();
            case DECOR -> wDecor.getValue();
        } / 100.0;
    }

    private boolean on(Sig s) {
        return switch (s) {
            case MACHINE -> machines.isEnabled();
            case STORAGE -> storage.isEnabled();
            case FARM -> farming.isEnabled();
            case PLACED -> placed.isEnabled();
            case LIGHT -> light.isEnabled();
            case ENTITY -> entities.isEnabled();
            case GROWTH -> growth.isEnabled();
            case REDSTONE -> redstone.isEnabled();
            case PORTAL -> portals.isEnabled();
            case DECOR -> decor.isEnabled();
        };
    }

    @Override
    public void onEnable() {
        timer = 1000;
        if (!registered) {
            try { WorldRenderEvents.END_MAIN.register(this::anormal$dispatchWorld); registered = true; } catch (Throwable ignored) { }
        }
        String a = action.getValue();
        if (a.equals("Off")) { setEnabled(true); return; }
        if (a.equals("Rescan all chunks")) {
            scanned.clear(); timer = 1000;
            say("Katta skan qayta boshlandi.", Formatting.GREEN);
        } else if (a.equals("Discord: test")) {
            sendDiscord("Kuro Sus Chunk Finder: test xabari. Webhook ishlayapti.", true);
        } else if (a.equals("Discord: save webhook") || a.equals("Discord: save bot token") || a.equals("Discord: save user ID")) {
            saveFromClipboard(a);
        }
        setEnabled(false);   // amal bajarildi — Action'dan Off'ga qaytishguncha modul o'chadi
    }

    /** Action rejimlari clipboard'dan qiymat oladi (Anormalda Setting.Text yo'q). */
    private void saveFromClipboard(String which) {
        String u = mc.keyboard.getClipboard() == null ? "" : mc.keyboard.getClipboard().trim();
        if (which.equals("Discord: save webhook")) {
            if (!validHook(u)) {
                if (mc.player != null) mc.player.sendMessage(Text.literal("[Sus Chunk] Discord webhook URL emas "
                        + "(https://discord.com/api/webhooks/ bilan boshlanishi kerak)").formatted(Formatting.RED), false);
                return;
            }
            try {
                Files.createDirectories(HOOK_FILE.getParent());
                Files.writeString(HOOK_FILE, u, StandardCharsets.UTF_8);
                say("Discord webhook saqlandi.", Formatting.GREEN);
            } catch (Exception e) {
                say("Webhook saqlanmadi: " + e.getMessage(), Formatting.RED);
            }
        } else {
            String[] cur = readBot();
            if (which.equals("Discord: save bot token")) cur[0] = u;
            if (which.equals("Discord: save user ID")) {
                if (!u.matches("\\d{15,25}")) { say("User ID faqat raqamlar bo'lishi kerak (Developer Mode, o'zingga o'ng tugma, Copy User ID).", Formatting.RED); return; }
                cur[1] = u;
            }
            try {
                Files.createDirectories(BOT_FILE.getParent());
                Files.writeString(BOT_FILE, cur[0] + "\n" + cur[1] + "\n", StandardCharsets.UTF_8);
                dmChannel = null;
                say("Saqlandi. Bot token " + (cur[0].isEmpty() ? "YO'Q" : "bor") + ", user ID " + (cur[1].isEmpty() ? "YO'Q" : "bor") + ".", Formatting.GREEN);
            } catch (Exception e) {
                say("Saqlanmadi: " + e.getMessage(), Formatting.RED);
            }
        }
    }

    @Override
    public void onDisable() {
        infos.clear(); fresh = new HashMap<>(); display = List.of(); alerted.clear();
        mineChunks.clear(); trialChunks.clear(); scanned.clear(); queue.clear(); queuePos = 0;
        liveSpots.clear(); seenAt.clear();
    }

    // ---------------- skanlash (tickdagi bir necha chunk — lag spike bo'lmaydi) ----------------

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (mc.world != lastWorld) {                       // yangi dunyo / o'lcham
            lastWorld = mc.world;
            infos.clear(); fresh = new HashMap<>(); display = List.of(); alerted.clear();
            mineChunks.clear(); trialChunks.clear(); scanned.clear(); queue.clear(); queuePos = 0;
            liveSpots.clear(); seenAt.clear();
            timer = 1000;
        }

        tickLive();

        if (queuePos >= queue.size()) {
            if (++timer < passDelay.getValue().intValue()) return;
            timer = 0;
            beginPass();
            if (queue.isEmpty()) return;
        }

        int speed = scanSpeed.getValue().intValue();
        long t0 = System.nanoTime();
        for (int n = 0; n < speed && queuePos < queue.size(); n++) {
            long[] c = queue.get(queuePos++);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk((int) c[0], (int) c[1], false);
            if (chunk != null) {
                Info i = scanChunk(chunk);
                fresh.put(i.key(), i);
                scanned.put(i.key(), System.identityHashCode(chunk));
            } else skipped++;
            if (System.nanoTime() - t0 > 12_000_000L) break;
        }
        if (queuePos >= queue.size()) finishPass();
    }

    private long passStart, lastPassMs;
    private int skipped;

    private void beginPass() {
        queue.clear(); queuePos = 0; fresh = new HashMap<>();
        passStart = System.nanoTime(); skipped = 0;
        ChunkPos cp = new ChunkPos(mc.player.getBlockPos());
        int rad = range.getValue().intValue();
        if (skipScanned.isEnabled()) {
            scanned.keySet().removeIf(k -> mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k), false) == null);
        } else scanned.clear();
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) {
                int x = cp.x + dx, z = cp.z + dz;
                if (skipScanned.isEnabled()) {
                    WorldChunk loaded = mc.world.getChunkManager().getWorldChunk(x, z, false);
                    Integer seen = scanned.get(ChunkPos.toLong(x, z));
                    if (loaded != null && seen != null && seen == System.identityHashCode(loaded)) continue;
                }
                queue.add(new long[]{x, z});
            }
        queue.sort((a, b) -> Long.compare(
                (a[0] - cp.x) * (a[0] - cp.x) + (a[1] - cp.z) * (a[1] - cp.z),
                (b[0] - cp.x) * (b[0] - cp.x) + (b[1] - cp.z) * (b[1] - cp.z)));
    }

    private boolean interesting(BlockState s) {
        Block b = s.getBlock();
        return classes.containsKey(b) || markers.containsKey(b) || b == Blocks.KELP;
    }

    private Info scanChunk(WorldChunk chunk) {
        Info info = new Info(chunk.getPos().x, chunk.getPos().z);
        int baseX = chunk.getPos().getStartX(), baseZ = chunk.getPos().getStartZ();

        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(this::interesting)) continue;
            int baseY = (chunk.getBottomY() >> 4) * 16 + i * 16;      // section i ning pastki Y
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++) {
                        BlockState st = sec.getBlockState(x, y, z);
                        if (st.isAir()) continue;
                        Block b = st.getBlock();
                        Integer mk = markers.get(b);
                        if (mk != null) {
                            info.markers |= mk;
                            if (baseY + y < 50) {
                                if ((mk & M_WOOD) != 0) info.markers |= M_DWOOD;
                                if ((mk & M_FENCE) != 0) info.markers |= M_DFENCE;
                            }
                        }
                        Sig sg = classes.get(b);
                        if (b == Blocks.KELP && st.contains(Properties.AGE_25) && st.get(Properties.AGE_25) >= 25)
                            sg = Sig.GROWTH;                       // to'liq o'sgan kelp
                        if (sg == null) continue;
                        int wy = baseY + y;
                        double c = 1;
                        if (sg == Sig.LIGHT && wy >= 55) continue;  // faqat tuproq osti mash'alaları sanaladi
                        if (sg == Sig.FARM && wy < 45) c = 2;       // tuproq osti dehqonchiligi ikki barabar
                        info.add(sg, c, baseX + x + 0.5, wy + 0.5, baseZ + z + 0.5);
                    }
        }

        // zaxira: sandiqlar, barabans, shulker qutilar, ender sandiqlar (qo'sh sandiq bir marta)
        int stores = 0, spawners = 0;
        List<BlockEntity> storeList = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof MobSpawnerBlockEntity) { spawners++; continue; }
            boolean store = be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                    || be instanceof ShulkerBoxBlockEntity || be instanceof EnderChestBlockEntity;
            if (!store) continue;
            BlockState st = be.getCachedState();
            if (st.contains(ChestBlock.CHEST_TYPE) && st.get(ChestBlock.CHEST_TYPE) == ChestType.RIGHT) continue;
            stores++;
            storeList.add(be);
        }
        if (!(spawners > 0 && stores <= 2)) {                     // yolg'iz dungeon sandiq tabiiy
            for (BlockEntity be : storeList)
                info.add(Sig.STORAGE, 1, be.getPos().getX() + 0.5, be.getPos().getY() + 0.5, be.getPos().getZ() + 0.5);
        }

        // kema cho'kmasi: okean yoki sohil, faqat yog'och va bir necha sandiq, o'yinchi qurmagan narsasiz
        if ((info.markers & M_PLAYER) == 0 && ((info.markers & M_SHIPWOOD) != 0 || stores > 0)
                && info.counts[Sig.STORAGE.ordinal()] <= 2) {
            var biome = mc.world.getBiome(new BlockPos(baseX + 8, 62, baseZ + 8));
            info.shipRaw = biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_BEACH);
        }
        return info;
    }

    private void finishPass() {
        lastPassMs = (System.nanoTime() - passStart) / 1_000_000L;
        // item ramkalari, armor standlar, nomli moblar va hayvon/dehqon og'imlar — chunk bo'yicha
        for (Entity e : mc.world.getEntities()) {
            double w = 0;
            if (e instanceof ItemFrameEntity || e instanceof ArmorStandEntity || e instanceof PaintingEntity) w = 1.5;
            else if (pets.isEnabled() && ((e instanceof TameableEntity t && t.isTamed())
                    || (e instanceof AbstractHorseEntity h && h.isTame())
                    || (e instanceof MobEntity m && m.isLeashed()))) w = 2.0;
            else if (e instanceof LivingEntity && !(e instanceof net.minecraft.entity.player.PlayerEntity)) {
                if (e.hasCustomName()) w = 1.5;
                else if (e instanceof AnimalEntity || e instanceof VillagerEntity) w = 0.5;
            }
            if (w == 0) continue;
            Info i = fresh.get(ChunkPos.toLong(e.getBlockX() >> 4, e.getBlockZ() >> 4));
            if (i != null) i.add(Sig.ENTITY, w, e.getX(), e.getY(), e.getZ());
        }

        Set<Long> newStructure = new HashSet<>();
        for (Info i : fresh.values()) {
            if (isMine(i.markers)) { if (mineChunks.add(i.key())) newStructure.add(i.key()); } else mineChunks.remove(i.key());
            if (isTrial(i.markers)) { if (trialChunks.add(i.key())) newStructure.add(i.key()); } else trialChunks.remove(i.key());
        }
        for (Info i : fresh.values()) {
            rate(i);
            if (i.own >= 0.12) infos.put(i.key(), i); else infos.remove(i.key());
        }
        // Avval skan qilingan chunk qo'shni (struktura ko'rsatgan) yuklanishidan oldin baholangan edi: bazadan belgilangan edi.
        // Endi qo'shni ma'lum: struktura chegirmasi qo'llanishi uchun bu chunklarni qayta baholaymiz.
        if (!newStructure.isEmpty()) {
            List<Info> redo = new ArrayList<>();
            for (Info o : infos.values()) {
                if (fresh.containsKey(o.key())) continue;
                for (long k : newStructure) {
                    if (Math.abs(o.cx - ChunkPos.getPackedX(k)) <= 2 && Math.abs(o.cz - ChunkPos.getPackedZ(k)) <= 2) { redo.add(o); break; }
                }
            }
            for (Info o : redo) {
                rate(o);
                if (o.own >= 0.12) infos.put(o.key(), o); else infos.remove(o.key());
            }
        }
        fresh = new HashMap<>();
        rebuildDisplay();
        List<Info> sorted = display;

        // bir martali ogohlantirishlar
        int sent = 0;
        for (Info i : sorted) {
            int pct = (int) Math.round(i.pct * 100);
            if (pct < alertAt.getValue().intValue()) break;
            if (!chatAlert.isEnabled() && !soundAlert.isEnabled() && !discordAlert.isEnabled()) break;
            if (i.live != null) continue;
            if (alerted.contains(i.key())) continue;
            if (sent >= 3) break;
            alerted.add(i.key());
            sent++;
            if (chatAlert.isEnabled()) {
                mc.player.sendMessage(Text.literal("[Sus Chunk] ").formatted(Formatting.GOLD)
                        .append(Text.literal(pct + "% baza ehtimoli " + (int) i.midX() + ", " + (int) i.midZ()
                                + " yaqinida" + (i.reasons.isEmpty() ? "" : " (" + i.reasons + ")")).formatted(Formatting.YELLOW)), false);
            }
            if (soundAlert.isEnabled()) playAlert();
            if (discordAlert.isEnabled()) {
                try {
                    sendDiscord("**Mumkin bo'lgan baza** (" + pct + "%) X " + (int) i.midX() + ", Y " + (int) i.midY(mc.player.getY())
                            + ", Z " + (int) i.midZ() + " yaqinida" + (i.reasons.isEmpty() ? "" : "\nSabablari: " + i.reasons) + serverSuffix(), false);
                } catch (Throwable ignored) { }
            }
        }
    }

    private void playAlert() {
        if (mc.player == null) return;
        mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f));
    }

    // ---------------- Discord webhook / bot DM (kuro'dan 1:1) ----------------

    private static final Path HOOK_FILE = Path.of("config", "kuro-discord-webhook.txt");
    private static HttpClient httpClient;
    private static synchronized HttpClient http() { if (httpClient == null) httpClient = HttpClient.newHttpClient(); return httpClient; }

    private static boolean validHook(String u) {
        return u.startsWith("https://discord.com/api/webhooks/") || u.startsWith("https://discordapp.com/api/webhooks/");
    }

    private String serverSuffix() {
        try {
            var sv = mc.getCurrentServerEntry();
            return sv != null ? "\nServer: " + sv.address : "";
        } catch (Throwable t) { return ""; }
    }

    private static String jsonEscape(String t) {
        StringBuilder b = new StringBuilder();
        for (char c : t.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> { }
                case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(' '); else b.append(c); }
            }
        }
        return b.toString();
    }

    private static final Path BOT_FILE = Path.of("config", "kuro-discord-bot.txt");   // 1-qator: bot token, 2-qator: user ID
    private volatile String dmChannel;

    private void say(String msg, Formatting c) {
        mc.execute(() -> { if (mc.player != null) mc.player.sendMessage(Text.literal("[Sus Chunk] " + msg).formatted(c), false); });
    }

    private String[] readBot() {
        try {
            if (!Files.exists(BOT_FILE)) return new String[]{"", ""};
            List<String> l = Files.readAllLines(BOT_FILE, StandardCharsets.UTF_8);
            return new String[]{l.size() > 0 ? l.get(0).trim() : "", l.size() > 1 ? l.get(1).trim() : ""};
        } catch (Exception e) { return new String[]{"", ""}; }
    }

    private static HttpRequest.Builder botReq(String url, String token) {
        return HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .header("Authorization", "Bot " + token).header("User-Agent", "DiscordBot (kuro, 1.0)");
    }

    /** Bot DM (token + user ID saqlangan bo'lsa), aks holda webhook. O'yin threadidan tashqarida ishlaydi. */
    private String deliver(String text) throws Exception {
        String[] bot = readBot();
        String uid = bot[1].matches("\\d{15,25}") ? bot[1] : "";
        String mention = uid.isEmpty() ? "" : "<@" + uid + "> ";
        String allowed = uid.isEmpty() ? "" : ",\"allowed_mentions\":{\"users\":[\"" + uid + "\"]}";
        String body = "{\"content\":\"" + jsonEscape(mention + text) + "\"" + allowed + "}";
        if (!bot[0].isEmpty() && !bot[1].isEmpty()) {
            if (dmChannel == null) {
                HttpResponse<String> r = http().send(botReq("https://discord.com/api/v10/users/@me/channels", bot[0])
                        .POST(HttpRequest.BodyPublishers.ofString("{\"recipient_id\":\"" + bot[1] + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"(\\d+)\"").matcher(r.body());
                if (r.statusCode() / 100 != 2 || !m.find()) return "Bot DM ocholmadi (HTTP " + r.statusCode() + "): " + r.body();
                dmChannel = m.group(1);
            }
            HttpResponse<String> r = http().send(botReq("https://discord.com/api/v10/channels/" + dmChannel + "/messages", bot[0])
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) { dmChannel = null; return "Bot DM xatosi (HTTP " + r.statusCode() + "): " + r.body(); }
            return "DM yuborildi";
        }
        String hook = Files.exists(HOOK_FILE) ? Files.readString(HOOK_FILE, StandardCharsets.UTF_8).trim() : "";
        if (!validHook(hook)) return "Discord bot yoki webhook hali o'rnatilmagan.";
        HttpResponse<String> r = http().send(HttpRequest.newBuilder(URI.create(hook)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"Sus Chunk Finder\"," + body.substring(1), StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
        return r.statusCode() / 100 == 2 ? "Webhook xabari yuborildi" : "Webhook xatosi (HTTP " + r.statusCode() + ")";
    }

    private void sendDiscord(String text, boolean reportResult) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String res;
            try { res = deliver(text); } catch (Exception e) { res = "Discord yuborish xatosi: " + e.getMessage(); }
            if (reportResult) say(res, res.startsWith("DM") || res.startsWith("Webhook xabari") ? Formatting.GREEN : Formatting.RED);
        });
    }

    // ---------------- jonli o'yinchilar ----------------

    /** Ko'zdan boshqa har bir o'yinchi chunkini belgilaydi va har birini birinchi ko'rganida xabar qiladi. */
    private void tickLive() {
        if (!flagPlayers.isEnabled()) {
            if (!liveSpots.isEmpty()) { liveSpots.clear(); seenAt.clear(); rebuildDisplay(); }
            return;
        }
        long now = System.currentTimeMillis(), keep = playerKeep.getValue().longValue() * 1000L;
        boolean changed = false;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            if (ignoreFriends.isEnabled() && Friends.isFriend(p.getName().getString())) continue;
            String name = p.getName().getString();
            long key = ChunkPos.toLong(p.getBlockX() >> 4, p.getBlockZ() >> 4);
            Spot sp = liveSpots.get(key);
            if (sp == null) { liveSpots.put(key, new Spot(name, p.getX(), p.getY(), p.getZ(), now)); changed = true; }
            else { sp.name = name; sp.x = p.getX(); sp.y = p.getY(); sp.z = p.getZ(); sp.last = now; }
            Long prev = seenAt.get(p.getUuid());
            if (prev == null || now - prev > keep) notifyPlayer(name, p.getX(), p.getY(), p.getZ());
            seenAt.put(p.getUuid(), now);
        }
        for (Iterator<Spot> it = liveSpots.values().iterator(); it.hasNext(); )
            if (now - it.next().last > keep) { it.remove(); changed = true; }
        seenAt.values().removeIf(t -> now - t > keep);
        if (changed) rebuildDisplay();
    }

    private void notifyPlayer(String name, double x, double y, double z) {
        double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
        long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
        if (playerChat.isEnabled()) {
            mc.player.sendMessage(Text.literal("[Sus Chunk] ").formatted(Formatting.AQUA)
                    .append(Text.literal("JONLI O'YINCHI " + name + " — " + (int) x + ", " + (int) z
                            + " (chunk " + ((int) Math.floor(x) >> 4) + ", " + ((int) Math.floor(z) >> 4)
                            + ", " + dist + "m uzoqda)").formatted(Formatting.WHITE)), false);
        }
        if (playerSound.isEnabled()) playAlert();
        if (discordAlert.isEnabled() && discordPlayers.isEnabled()) try {
            sendDiscord("**O'yinchi ko'rindi:** " + name + " X " + (int) x + ", Z " + (int) z
                    + " (" + dist + "m uzoqda)" + serverSuffix(), false);
        } catch (Throwable ignored) { }
    }

    /** Har eslab qolgan va har belgilangan chunkni qayta baholaydi, qo'shnilarga ozgina ball tarqatadi va saralaydi. */
    private void rebuildDisplay() {
        Map<Long, Info> all = new HashMap<>(infos);
        for (Map.Entry<Long, Spot> e : liveSpots.entrySet()) {
            all.computeIfAbsent(e.getKey(), k -> {
                Info i = new Info(ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k));
                i.hx = e.getValue().x; i.hy = e.getValue().y; i.hz = e.getValue().z; i.hw = 1;
                return i;
            });
        }
        for (Info i : all.values()) rate(i);

        // baza chunk chegaralarini kesib o'tadi: qo'shnillar ozgina qo'shadi
        for (Info i : all.values()) {
            double none = 1 - i.own;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Info n = all.get(ChunkPos.toLong(i.cx + dx, i.cz + dz));
                    if (n != null) none *= 1 - 0.35 * n.own;
                }
            i.pct = Math.min(0.99, 1 - none);
        }
        List<Info> sorted = new ArrayList<>(all.values());
        sorted.sort((a, b) -> Double.compare(b.pct, a.pct));
        display = sorted;
    }

    /** Chunk sanoblariga o'z ehtimolsimon ballini va qisqa sabablar ro'yxatini beradi. */
    private void rate(Info i) {
        boolean mine = isMine(i.markers);
        for (int dx = -2; dx <= 2 && !mine; dx++)
            for (int dz = -2; dz <= 2 && !mine; dz++)
                if (mineChunks.contains(ChunkPos.toLong(i.cx + dx, i.cz + dz))) mine = true;
        boolean trial = isTrial(i.markers);
        for (int dx = -2; dx <= 2 && !trial; dx++)
            for (int dz = -2; dz <= 2 && !trial; dz++)
                if (trialChunks.contains(ChunkPos.toLong(i.cx + dx, i.cz + dz))) trial = true;
        i.trial = ignoreStructures.isEnabled() && trial;
        i.mine = ignoreStructures.isEnabled() && mine;
        i.ship = ignoreStructures.isEnabled() && i.shipRaw;
        i.structure = ignoreStructures.isEnabled() && (mine || trial || i.ship || (i.markers & M_STRUCT) != 0);
        double none = 1;
        Sig[] all = Sig.values();
        double[] contrib = new double[all.length];
        for (Sig s : all) {
            if (!on(s)) continue;
            boolean zero = (i.trial && s != Sig.FARM && s != Sig.PORTAL && s != Sig.GROWTH)
                    || (i.mine && (s == Sig.PLACED || s == Sig.LIGHT || s == Sig.DECOR))
                    || (i.ship && (s == Sig.PLACED || s == Sig.STORAGE || s == Sig.DECOR));
            double scale = zero ? 0.0 : i.structure ? s.structScale : 1.0;
            double c = i.counts[s.ordinal()] * scale;
            double p = Math.min(0.99, s.reliability * weight(s) * (1 - Math.exp(-c / s.k)));
            contrib[s.ordinal()] = p;
            none *= 1 - p;
        }
        i.live = liveSpots.get(i.key());
        if (i.live != null) none *= 1 - LIVE_RELIABILITY;
        i.own = 1 - none;

        // sabablar, kuchliroqdan: chat ogohlantirishlari uchun qisa qator va hover tooltip uchun to'liq ro'yxat
        i.rows.clear();
        StringBuilder sb = new StringBuilder();
        boolean[] used = new boolean[all.length];
        int shown = 0;
        i.signals = 0;
        for (int n = 0; n < all.length; n++) {
            int best = -1;
            for (int k = 0; k < all.length; k++)
                if (!used[k] && contrib[k] >= 0.04 && (best < 0 || contrib[k] > contrib[best])) best = k;
            if (best < 0) break;
            used[best] = true;
            i.signals++;
            long cnt = Math.round(i.counts[best]);
            i.rows.add(new String[]{"+" + Math.round(contrib[best] * 100), all[best].label + " " + cnt});
            if (contrib[best] >= 0.08 && shown++ < 3) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(all[best].label).append(' ').append(cnt);
            }
        }
        if (i.live != null) {
            i.rows.add(0, new String[]{"+" + Math.round(LIVE_RELIABILITY * 100), "jonli o'yinchi  " + i.live.name});
            sb.insert(0, "o'yinchi " + i.live.name + (sb.length() > 0 ? " | " : ""));
        }
        if (i.structure) {
            String t = i.trial ? "trial chamber" : i.mine ? "shaxta yonida" : i.ship ? "kema cho'kmasi?" : "struktura yonida?";
            i.rows.add(new String[]{"-", t + " (chegirilgan)"});
            sb.append(sb.length() > 0 ? " | " : "").append(t);
        }
        i.reasons = sb.toString();
    }

    // ---------------- chizish ----------------

    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /** Belgining chizilish balandligi: dalillar real balandligi yoki tekis yuza. */
    private double markerY(Info i, double py) {
        if (atBase.isEnabled() && i.hw > 0) return i.midY(py);
        return flatFloor.isEnabled() ? mc.world.getBottomY() + 0.05 : Math.floor(py) + 0.02;
    }

    private static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        return COMPASS[(int) Math.round(((a % 360) + 360) % 360 / 45.0) % 8];
    }

    private static int colorOf(Info i, int pct) { return i.live != null ? LIVE : tierColor(pct); }

    private static int tierColor(int pct) {
        return pct >= 75 ? BAD : pct >= 50 ? ORANGE : pct >= 25 ? WARN : 0xFF7C8594;
    }

    /** Bu kadringa dunyo pass qaror qildi; HUD pass tooltip/markers/tracer uchun o'qiydi. */
    private List<Vis> curVis = List.of();
    private Vis curHover;

    private record Vis(Info info, int pct, double dist) { }

    private boolean registered;

    /** Dunyo passi: qaysi chunklar masofada/nishonda ekanini aniqlaydi va ularning boxlarini chizadi. */
    private void anormal$dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || mc.player == null || mc.world == null) return;
        curVis = List.of();
        curHover = null;
        List<Info> list = display;
        if (list.isEmpty()) return;

        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        Vec3d c = camera.getCameraPos();
        double maxD = maxDistance.getValue(), max2 = maxD * maxD;
        int min = minPercent.getValue().intValue(), limit = maxHighlights.getValue().intValue();
        double py = mc.player.getY();

        List<Vis> vis = new ArrayList<>();
        for (Info i : list) {
            int pct = (int) Math.round(i.pct * 100);
            if (i.live == null && pct < min) continue;
            double dx = i.midX() - c.x, dy = i.midY(py) - c.y, dz = i.midZ() - c.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > max2) continue;
            vis.add(new Vis(i, pct, Math.sqrt(d2)));
        }
        if (vis.isEmpty()) return;
        vis.sort((a, b) -> Double.compare(a.dist, b.dist));
        if (vis.size() > limit) {
            List<Vis> keep = new ArrayList<>();
            List<Vis> rest = new ArrayList<>();
            for (Vis v : vis) (v.info.live != null ? keep : rest).add(v);
            rest.sort((a, b) -> Double.compare(b.pct, a.pct));
            for (int k = 0; k < rest.size() && keep.size() < limit; k++) keep.add(rest.get(k));
            keep.sort((a, b) -> Double.compare(a.dist, b.dist));
            vis = keep;
        }

        // nishongdagi chunk — ko'z nuri rayining column / dalil qutisiga tegishida (devorlarni ko'radi)
        Vis hover = null;
        if (hoverInfo.isEnabled() && mc.currentScreen == null) {
            double yr = Math.toRadians(camera.getYaw()), pr = Math.toRadians(camera.getPitch());
            double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
            double bottom = mc.world.getBottomY(), top = mc.world.getTopYInclusive();
            double bestKey = Double.MAX_VALUE;
            for (Vis v : vis) {
                double key = Double.MAX_VALUE;
                v.info.box(bx, py);
                double tClue = rayBox(c.x, c.y, c.z, fx, fy, fz, bx[0], bx[1], bx[2], bx[3], bx[4], bx[5], maxD);
                if (tClue >= 0) key = 1e3 + tClue;
                if (key == Double.MAX_VALUE) {
                    double x0 = v.info.cx * 16.0, z0 = v.info.cz * 16.0;
                    double t = rayBox(c.x, c.y, c.z, fx, fy, fz, x0, bottom, z0, x0 + 16, top, z0 + 16, maxD);
                    if (t >= 0) key = 1e8 + t;
                }
                if (key < bestKey) { bestKey = key; hover = v; }
            }
        }
        curVis = vis;
        curHover = hover;

        // shubhali har chunk uchun 16x16 poyasidagi chiziq va o'rtadagi nuqta
        VertexConsumerProvider consumers = wrc.consumers();
        MatrixStack matrices = wrc.matrices();
        if (consumers == null || matrices == null) return;
        WorldRender3D.begin(matrices, camera);
        try {
            for (Vis v : vis) {
                boolean hot = v == hover;
                int col = colorOf(v.info, v.pct) & 0xFFFFFF;
                int a = hot ? 0xFF : 0xC0;
                double y = markerY(v.info, py);
                double x0 = v.info.cx * 16.0, z0 = v.info.cz * 16.0;
                WorldRender3D.drawBox(consumers, new Box(x0, y, z0, x0 + 16, y + (hot ? 3 : 3), z0 + 16), (a << 24) | col);
                double my = markerY(v.info, py);
                double mx = v.info.midX(), mz = v.info.midZ();
                WorldRender3D.drawFilledBox(consumers, new Box(mx - 0.25, my - 0.25, mz - 0.25, mx + 0.25, my + 0.25, mz + 0.25), (a << 24) | col);
                if (tracerBest.isEnabled() && v.info.live == null) {
                    // faqat eng shubhalisiga tracer — pas oxirida kodlash
                }
            }
            if (tracerBest.isEnabled()) {
                Vis best = null;
                for (Vis v : vis) if (best == null || v.pct > best.pct) best = v;
                if (best != null) {
                    double my = markerY(best.info, py);
                    WorldRender3D.drawLine(consumers, c, new Vec3d(best.info.midX(), my, best.info.midZ()), 0xB4 << 24 | tierColor(best.pct) & 0xFFFFFF);
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    /** Ray (AABB) — masofa yoki -1 (o'tkazib yuborilgan). */
    private static double rayBox(double px, double py, double pz, double dx, double dy, double dz,
                                 double x0, double y0, double z0, double x1, double y1, double z1, double maxT) {
        double invX = 1 / dx, invY = 1 / dy, invZ = 1 / dz;
        double tMin = 0, tMax = maxT;
        double[] min = {x0, y0, z0}, max = {x1, y1, z1};
        double[] p = {px, py, pz}, d = {dx, dy, dz}, inv = {invX, invY, invZ};
        for (int i = 0; i < 3; i++) {
            double t0b = (min[i] - p[i]) * inv[i], t1b = (max[i] - p[i]) * inv[i];
            if (t0b > t1b) { double tmp = t0b; t0b = t1b; t1b = tmp; }
            if (t0b > tMin) tMin = t0b;
            if (t1b < tMax) tMax = t1b;
            if (tMin > tMax) return -1;
        }
        return tMin;
    }

    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        boolean have = !display.isEmpty();
        if (progress.isEnabled()) drawProgress(g);
        if (regionMap.isEnabled() && have) drawMap(g, display);
        if (indicator.isEnabled() && have) drawIndicator(g, curVis);
        tickTooltip(g, curHover);
    }

    // ---- hover kartochkasi ochilish/yopilish animatsiyasi ----
    private float tipT;
    private Vis tipVis;
    private long tipNanos;
    private boolean tooltipErrorShown;

    private void tickTooltip(DrawContext g, Vis hover) {
        if (mc.player == null) return;
        long now = System.nanoTime();
        float dt = tipNanos == 0 ? 0f : Math.min(0.1f, (now - tipNanos) / 1e9f);
        tipNanos = now;
        if (hover != null) {
            if (tipVis == null || tipVis.info != hover.info) { if (tipVis == null || tipT <= 0.02f) tipT = 0f; }
            tipVis = hover;
            tipT = Math.min(1f, tipT + dt / 0.16f);
        } else if (tipVis != null) {
            tipT = Math.max(0f, tipT - dt / 0.11f);
            if (tipT <= 0f) tipVis = null;
        }
        if (tipVis == null || tipT <= 0f) return;
        float t = tipT, e = 1f - (1f - t) * (1f - t) * (1f - t);
        try { drawTooltip(g, tipVis, e); }
        catch (RuntimeException ex) {
            if (!tooltipErrorShown) {
                tooltipErrorShown = true;
                if (mc.player != null) mc.player.sendMessage(Text.literal("[Sus Chunk] hover info xatoligi: " + ex), false);
            }
        }
    }

    private static int fade(int argb, float k) {
        return (int) Math.round(((argb >>> 24) & 0xFF) * k) << 24 | (argb & 0xFFFFFF);
    }

    /** Top-left readout: pass qanchalik devordiq, qancha chunk yuklanmagan, o'tgan pass vaqti. */
    private void drawProgress(DrawContext g) {
        TextRenderer tr = mc.textRenderer;
        boolean scanning = queuePos < queue.size();
        double frac = queue.isEmpty() ? 1.0 : Math.min(1.0, queuePos / (double) queue.size());
        String s = "Sus scan " + (scanning ? queuePos + "/" + queue.size() : "idle")
                + (skipped > 0 ? "  " + skipped + " unloaded" : "")
                + (lastPassMs > 0 ? "  last " + String.format("%.1f", lastPassMs / 1000.0) + "s" : "");
        int col = lastPassMs > 8000 ? BAD : lastPassMs > 4000 ? WARN : GOOD;
        int w = tr.getWidth(s) + 16;
        int x = 4, y = 18;
        g.fill(x, y, x + w, y + 17, BG);
        KuroHud.frame(g, x, y, w, 17, BORDER);
        RenderUtils.drawText(g, tr, s, x + 8, y + 3, TEXT, false);
        g.fill(x + 8, y + 13, x + 8 + (int) Math.max(1, (w - 16) * MathHelper.clamp((float) frac, 0f, 1f)), y + 15, col);
    }

    /** Bottom-right cards: o'yinchi belgisi — o'yinchi bosh o'yinchoq, baza topilganda sandiq. */
    private void drawIndicator(DrawContext g, List<Vis> vis) {
        Vis player = null, base = null;
        for (Vis v : vis) {
            if (v.info.live != null) { if (player == null || v.dist < player.dist) player = v; }
            else if (v.pct >= baseAt.getValue().intValue() / 100.0 && (base == null || v.pct > base.pct)) base = v;
        }
        int rows = (player != null ? 1 : 0) + (base != null ? 1 : 0);
        if (rows == 0) return;
        int w = 130, h = 24;
        int x = RenderUtils.screenWidth() - w - 8, y = RenderUtils.screenHeight() - 34 - rows * (h + 4);
        if (player != null) {
            int c = colorOf(player.info, player.pct);
            g.fill(x, y, x + w, y + h, BG);
            KuroHud.frame(g, x, y, w, h, c);
            g.drawItem(new ItemStack(Items.PLAYER_HEAD), x + 4, y + 4);
            RenderUtils.drawText(g, mc.textRenderer, "O'yinchi", x + 26, y + 4, c, false);
            RenderUtils.drawText(g, mc.textRenderer, player.info.live.name + "  " + (int) player.dist + "m", x + 26, y + 13, MUTED, false);
            y += h + 4;
        }
        if (base != null) {
            int c = colorOf(base.info, base.pct);
            g.fill(x, y, x + w, y + h, BG);
            KuroHud.frame(g, x, y, w, h, c);
            g.drawItem(new ItemStack(Items.CHEST), x + 4, y + 4);
            RenderUtils.drawText(g, mc.textRenderer, "Baza  " + base.pct + "%", x + 26, y + 4, c, false);
            double cx = mc.gameRenderer.getCamera().getCameraPos().x, cz = mc.gameRenderer.getCamera().getCameraPos().z;
            RenderUtils.drawText(g, mc.textRenderer,
                    compass(base.info.midX() - cx, base.info.midZ() - cz) + " " + (int) base.dist + "m", x + 26, y + 13, MUTED, false);
        }
    }

    /** Hover kartochkasi: oq accent chiziqli to'q qarta, bold sarlavha + foiz, direction/distance, thin bar, satrlar. */
    private void drawTooltip(DrawContext g, Vis v, float k) {
        Info i = v.info;
        TextRenderer tr = mc.textRenderer;
        int WHITE = 0xFFFFFFFF;
        String title = i.live != null ? "Jonli o'yinchi: " + i.live.name : v.pct >= 75 ? "Juda ehtimol baza" : v.pct >= 50 ? "Ehtimol baza" : "Mumkin baza";
        String badge = Math.round(v.pct * 100) + "%";
        double dx = i.midX() - mc.gameRenderer.getCamera().getCameraPos().x,
               dz = i.midZ() - mc.gameRenderer.getCamera().getCameraPos().z;
        String where = compass(dx, dz) + " " + (int) v.dist + "m";

        int padL = 13, padR = 13;
        int ptsW = 0;
        for (String[] r : i.rows) ptsW = Math.max(ptsW, tr.getWidth(r[0]));
        int w = Math.max(tr.getWidth(title) + tr.getWidth(badge) + 24, tr.getWidth(where) + padL + padR) + padL + padR;
        for (String[] r : i.rows) w = Math.max(w, padL + ptsW + 7 + tr.getWidth(r[1]) + padR);
        w = Math.max(w, 150);
        int rowH = 11;
        int h = 42 + (i.rows.isEmpty() ? 0 : i.rows.size() * rowH + 2) + 5;

        // chunk belgi markazi -> ekranda kamera proyeksiyasi; ekran bilan chiqib ketmasa crosshair markazi
        double[] cp = project(i.midX(), i.midY(mc.player.getY()), i.midZ());
        double scrW = RenderUtils.screenWidth(), scrH = RenderUtils.screenHeight();
        boolean onScreen = cp != null && cp[0] > 0 && cp[0] < scrW && cp[1] > 0 && cp[1] < scrH;
        if (!onScreen) { if (cp == null) cp = new double[2]; cp[0] = scrW / 2.0; cp[1] = scrH / 2.0 + h / 2.0 + 14; }
        int x = (int) cp[0] - w / 2, y = (int) cp[1] - h / 2;
        x = MathHelper.clamp(x, 4, Math.max(4, (int) scrW - w - 4));
        y = MathHelper.clamp(y, 4, Math.max(4, (int) scrH - h - 4));

        // qoraywon qarta (rang-glow yo'q), chap tomonida oq accent chiziq
        g.fill(x, y, x + w, y + h, fade(BG, k));
        KuroHud.frame(g, x, y, w, h, fade(BORDER, k));
        g.fill(x + 5, y + 6, x + 7, y + h - 6, fade(WHITE, k));

        int tx = x + padL, right = x + w - padR;
        RenderUtils.drawText(g, tr, title, tx, y + 7, fade(WHITE, k), false);
        RenderUtils.drawText(g, tr, badge, right - tr.getWidth(badge), y + 7, fade(WHITE, k), false);
        RenderUtils.drawText(g, tr, where, tx, y + 20, fade(MUTED, k), false);

        int bw = right - tx, bf = (int) Math.round(bw * MathHelper.clamp(v.pct, 0, 100) / 100.0 * k);
        g.fill(tx, y + 33, right, y + 36, fade(TRACK, k));
        if (bf > 0) g.fill(tx, y + 33, tx + bf, y + 36, fade(WHITE, k));

        int ry = y + 43;
        for (String[] r : i.rows) {
            boolean plus = r[0].startsWith("+");
            RenderUtils.drawText(g, tr, r[0], tx, ry, fade(plus ? WHITE : MUTED, k), false);
            RenderUtils.drawText(g, tr, r[1], tx + ptsW + 7, ry, fade(plus ? TEXT : MUTED, k), false);
            ry += rowH;
        }
    }

    /** Dunyo-to-ekran proyeksiyasi (kuro'dagi Projector o'rniga): [x,y] yoki null (orqada). */
    private double[] project(double wx, double wy, double wz) {
        Camera cam = mc.gameRenderer.getCamera();
        if (cam == null) return null;
        Vec3d c = cam.getCameraPos();
        double relX = wx - c.x, relY = wy - c.y, relZ = wz - c.z;
        double yr = Math.toRadians(cam.getYaw()), pr = Math.toRadians(cam.getPitch());
        double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
        double rx = -Math.cos(yr), rz = -Math.sin(yr);      // o'ng vektor (unit)
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;   // up = right × forward (cross)
        double zf = relX * fx + relY * fy + relZ * fz;
        if (zf < 0.05) return null;
        Number fovN = mc.options.getFov().getValue();
        double fov = fovN.doubleValue();
        double h = RenderUtils.screenHeight(), wdt = RenderUtils.screenWidth();
        double f = (h / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        double sx = wdt / 2.0 + f * (relX * rx + relY * 0 + relZ * rz) / zf;
        double sy = h / 2.0 - f * (relX * ux + relY * uy + relZ * uz) / zf;
        return new double[]{sx, sy};
    }

    /** O'zingni qamrab olgant topograf xarita (shimol yuqorida, oq nuqta o'zing). */
    private void drawMap(DrawContext g, List<Info> list) {
        int r = mapRadius.getValue().intValue(), cell = mapCell.getValue().intValue(), size = (r * 2 + 1) * cell;
        int x0 = RenderUtils.screenWidth() - size - 22, y0 = Math.max(30, (RenderUtils.screenHeight() - size) / 2);
        g.fill(x0 - 5, y0 - 15, x0 + size + 5, y0 + size + 5, BG);
        KuroHud.frame(g, x0 - 5, y0 - 15, size + 10, size + 20, BORDER);
        g.fill(x0, y0, x0 + size, y0 + size, 0x40000000);
        ChunkPos pc = new ChunkPos(mc.player.getBlockPos());
        int pcx = pc.x, pcz = pc.z;
        for (Info i : list) {
            int dx = i.cx - pcx, dz = i.cz - pcz;
            if (Math.abs(dx) > r || Math.abs(dz) > r) continue;
            int pct = (int) Math.round(i.pct * 100);
            int px = x0 + (dx + r) * cell, py = y0 + (dz + r) * cell;
            g.fill(px, py, px + cell - 1, py + cell - 1, (i.live != null ? LIVE : colorOf(i, pct)));
        }
        int me = r * cell;
        g.fill(x0 + me - 1, y0 + me - 1, x0 + me + cell, y0 + me + cell, 0xFF000000);
        g.fill(x0 + me, y0 + me, x0 + me + cell - 1, y0 + me + cell - 1, 0xFFFFFFFF);
        RenderUtils.drawText(g, mc.textRenderer, "N", x0 + size / 2 - 2, y0 - 12, TEXT, false);
    }
}
