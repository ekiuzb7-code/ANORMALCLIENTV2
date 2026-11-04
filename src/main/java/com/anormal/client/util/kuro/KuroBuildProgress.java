package com.anormal.client.util.kuro;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * kuro {@code BuildProgress} port: shemani dunyo bilan slicing bilan solishtiradi (lag spike chiqarmaslik
 * uchun) va bildiradi: qanchasi bitgan va eng ehtimol keyingi bloklar qaysi. Faqat blok turi (facing/half emas),
 * yuklanmagan chanklar o'tkazib yuboriladi.
 */
public final class KuroBuildProgress {
    private static final int BUDGET = 12000;           // har tik qatorda tekshiriladigan pozitsiyalar
    private static final byte UNKNOWN = 0, RIGHT = 1, WRONG = 2;

    public final KuroSchematicLayout layout;
    private final BlockPos origin;
    private final byte[] status;
    private final int[] wrongPerLayer;
    private int right, wrong, cursor;
    private final BlockPos.Mutable probe = new BlockPos.Mutable();

    public KuroBuildProgress(KuroSchematicLayout layout, BlockPos origin) {
        this.layout = layout;
        this.origin = origin.toImmutable();
        this.status = new byte[layout.ids.length];
        this.wrongPerLayer = new int[layout.sy];
    }

    public BlockPos origin() { return origin; }
    public int total() { return layout.targets; }
    public int done() { return right; }
    public int missing() { return wrong; }
    public double fraction() { return layout.targets == 0 ? 1.0 : (double) right / layout.targets; }
    public int layerCount() { return layout.sy; }

    /** Har bir blok turidan nechtasi hali noto'g'ri/yetişmagan (yuklangan chanklarda). */
    public Map<Block, Integer> missingBlocks() {
        Map<Block, Integer> out = new HashMap<>();
        for (int i = 0; i < status.length; i++) if (status[i] == WRONG) out.merge(layout.blocks[layout.ids[i]], 1, Integer::sum);
        return out;
    }

    /** Dunyo pozitsiyasi uchun shema bloki, yoki shemadan tashqari / kuzatilmaydigan bo'lsa null. */
    public Block targetAt(BlockPos p) {
        int x = p.getX() - origin.getX(), y = p.getY() - origin.getY(), z = p.getZ() - origin.getZ();
        if (x < 0 || y < 0 || z < 0 || x >= layout.sx || y >= layout.sy || z >= layout.sz) return null;
        return layout.blockAt(layout.index(x, y, z));
    }

    /** Xato blok qolgan eng pastgi qatlam (0-bazali), yo'qsa -1. */
    public int lowestWrongLayer() {
        for (int y = 0; y < wrongPerLayer.length; y++) if (wrongPerLayer[y] > 0) return y;
        return -1;
    }

    /** Shemanning keyingi qirqimini dunyo bilan solishtiradi. Har tikda bir marta chaqiriladi. */
    public void tick(MinecraftClient mc) {
        if (mc.world == null) return;
        int n = status.length;
        for (int k = 0; k < BUDGET; k++) {
            if (cursor >= n) cursor = 0;
            int i = cursor++;
            Block want = layout.blocks[layout.ids[i]];
            if (want == null) continue;
            int x = i % layout.sx, z = (i / layout.sx) % layout.sz, y = i / (layout.sx * layout.sz);
            probe.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (!mc.world.isChunkLoaded(origin.getX() + x >> 4, origin.getZ() + z >> 4)) continue;
            byte now = mc.world.getBlockState(probe).getBlock() == want ? RIGHT : WRONG;
            byte old = status[i];
            if (now == old) continue;
            if (old == RIGHT) right--; else if (old == WRONG) { wrong--; wrongPerLayer[y]--; }
            if (now == RIGHT) right++; else { wrong++; wrongPerLayer[y]++; }
            status[i] = now;
        }
    }

    /**
     * 'out' ni o'yinchi atrofidagi (gorizontal va vertikal) 'radius' blok ichida hali yetmagan/xa­toto'li bloklarning
     * shema indekslari bilan to'ldiradi, eng ko'pi out.length. Indeksni o'qish uchun blockPos / exposedFaces /
     * blockAtIndex ishlatiladi.
     */
    public int missingNear(int[] out, double px, double py, double pz, int radius) {
        int x0 = Math.max(0, (int) Math.floor(px) - origin.getX() - radius), x1 = Math.min(layout.sx - 1, (int) Math.floor(px) - origin.getX() + radius);
        int y0 = Math.max(0, (int) Math.floor(py) - origin.getY() - radius), y1 = Math.min(layout.sy - 1, (int) Math.floor(py) - origin.getY() + radius);
        int z0 = Math.max(0, (int) Math.floor(pz) - origin.getZ() - radius), z1 = Math.min(layout.sz - 1, (int) Math.floor(pz) - origin.getZ() + radius);
        int n = 0;
        for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            int i = layout.index(x, y, z);
            if (status[i] != WRONG) continue;
            if (n >= out.length) return n;
            out[n++] = i;
        }
        return n;
    }

    public Block blockAtIndex(int i) { return layout.blockAt(i); }

    /** Shema indeksining dunyo pozitsiyasi. */
    public BlockPos blockPos(int i) {
        return new BlockPos(origin.getX() + i % layout.sx, origin.getY() + i / (layout.sx * layout.sz), origin.getZ() + (i / layout.sx) % layout.sz);
    }

    /**
     * In­deksga ko'ra joylashgan blokning qanday yuzlari boshqa shema bloki bilan qo'ylanmagan (bit 0 down, 1 up,
     * 2 north -z, 3 south +z, 4 west -x, 5 east +x). Shemaning boshqa bloki bilan duch kelgan yuz yashirin bo'ladi.
     */
    public int exposedFaces(int i) {
        int x = i % layout.sx, z = (i / layout.sx) % layout.sz, y = i / (layout.sx * layout.sz);
        int m = 0;
        if (!solid(x, y - 1, z)) m |= 1;
        if (!solid(x, y + 1, z)) m |= 2;
        if (!solid(x, y, z - 1)) m |= 4;
        if (!solid(x, y, z + 1)) m |= 8;
        if (!solid(x - 1, y, z)) m |= 16;
        if (!solid(x + 1, y, z)) m |= 32;
        return m;
    }

    private boolean solid(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= layout.sx || y >= layout.sy || z >= layout.sz) return false;
        return layout.blockAt(layout.index(x, y, z)) != null;
    }

    /**
     * Ehtimol keyin quriladigan bloklar, o'yinchiga eng yaqinlari oldin. "Build in layers" bilan bu eng past
     * bitmay qolgan qatlam (Baritone nima qiladi); aks holda o'yinchi yaqinidagi eng yaqin etmagan bloklar.
     */
    public List<BlockPos> next(int count, double px, double py, double pz, boolean layered) {
        List<BlockPos> out = new ArrayList<>();
        int low = lowestWrongLayer();
        if (low < 0) return out;
        int yFrom = low, yTo = low;
        int x0 = 0, x1 = layout.sx - 1, z0 = 0, z1 = layout.sz - 1;
        if (!layered) {
            yFrom = 0; yTo = layout.sy - 1;
            x0 = Math.max(0, (int) px - origin.getX() - 24); x1 = Math.min(layout.sx - 1, (int) px - origin.getX() + 24);
            z0 = Math.max(0, (int) pz - origin.getZ() - 24); z1 = Math.min(layout.sz - 1, (int) pz - origin.getZ() + 24);
        }
        double[] bestD = new double[count];
        int[] bestI = new int[count];
        int found = 0;
        for (int y = yFrom; y <= yTo; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            int i = layout.index(x, y, z);
            if (status[i] != WRONG) continue;
            double dx = origin.getX() + x + 0.5 - px, dy = origin.getY() + y + 0.5 - py, dz = origin.getZ() + z + 0.5 - pz;
            double d = dx * dx + dy * dy + dz * dz;
            if (found == count && d >= bestD[count - 1]) continue;
            int pos = found < count ? found++ : count - 1;
            while (pos > 0 && bestD[pos - 1] > d) { bestD[pos] = bestD[pos - 1]; bestI[pos] = bestI[pos - 1]; pos--; }
            bestD[pos] = d; bestI[pos] = i;
        }
        for (int k = 0; k < found; k++) {
            int i = bestI[k];
            out.add(new BlockPos(origin.getX() + i % layout.sx, origin.getY() + i / (layout.sx * layout.sz), origin.getZ() + (i / layout.sx) % layout.sz));
        }
        return out;
    }
}
