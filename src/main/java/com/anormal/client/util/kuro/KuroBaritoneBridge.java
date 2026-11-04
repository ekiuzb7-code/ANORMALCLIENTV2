package com.anormal.client.util.kuro;

import com.anormal.client.module.impl.kuro.KuroBaritoneControl;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * kuro {@code BaritoneBridge} port: Baritone API ustida yupqa reflection qatlami,
 * shuning uchun Kuro Baritone o'rnatilgan yoki o'rnatilmagan holatda ham kompilyatsiya bo'ladi va ishlaydi.
 * BaritoneControl.sendPlainChat bilan bir xil yondashuv: klasslarni runtime'da nomi orqali izlaymiz, import qilmaymiz.
 *
 * <p>{@link #set} orqali o'zgartirilgan sozlamalar esda saqlanadi va {@link #restoreAll} ularning har birini
 * asl holiga qaytaradi, shuning uchun modulni o'chirish sizning Baritone sozlamalaringizga tegmaydi.</p>
 */
public final class KuroBaritoneBridge {
    private static final String API = "baritone.api.BaritoneAPI";
    private static final String PROVIDER = "baritone.api.IBaritoneProvider";
    private static final String BARITONE = "baritone.api.IBaritone";
    private static final String BUILDER = "baritone.api.process.IBuilderProcess";
    private static final String GOAL_PROCESS = "baritone.api.process.ICustomGoalProcess";
    private static final String PATHING = "baritone.api.behavior.IPathingBehavior";
    private static final String GOAL = "baritone.api.pathing.goals.Goal";
    private static final String GET_TO_BLOCK = "baritone.api.pathing.goals.GoalGetToBlock";

    /** setting name -> biz tegishdan oldingi qiymati */
    private static final Map<String, Object> SAVED = new HashMap<>();

    private static volatile String lastError;

    private KuroBaritoneBridge() {}

    private static void note(Throwable t) {
        Throwable c = t;
        while ((c instanceof java.lang.reflect.InvocationTargetException || c instanceof ExceptionInInitializerError) && c.getCause() != null) c = c.getCause();
        lastError = c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    /** Oxirgi reflection chaqiruvi nima uchun muvaffaqiyatsiz bo'ldi (chat uchun), yoki null. */
    public static String lastError() { return lastError; }

    /** Baritone API reflection orqali boshqariladigan bo'lsa true (sozlamalar, builder holati). */
    public static boolean apiWorks() {
        lastError = null;
        try {
            Object b = builder();
            Class.forName(BUILDER).getMethod("isActive").invoke(b);
            Object settings = Class.forName(API).getMethod("getSettings").invoke(null);
            Object setting = settings.getClass().getField("allowSprint").get(settings);
            setting.getClass().getField("value").get(setting);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e);
            return false;
        }
    }

    public static boolean installed() { return KuroBaritoneControl.installed(); }

    // ------------------------------------------------------------------ reflection plumbing

    private static Object primary() throws ReflectiveOperationException {
        Object provider = Class.forName(API).getMethod("getProvider").invoke(null);
        return Class.forName(PROVIDER).getMethod("getPrimaryBaritone").invoke(provider);
    }

    private static Object invoke0(Object target, String iface, String method) throws ReflectiveOperationException {
        java.lang.reflect.Method m = Class.forName(iface).getMethod(method);
        try { m.setAccessible(true); } catch (RuntimeException ignored) {}
        return m.invoke(target);
    }

    private static Object builder() throws ReflectiveOperationException {
        return invoke0(primary(), BARITONE, "getBuilderProcess");
    }

    // ------------------------------------------------------------------ settings

    /**
     * Baritone sozlamasini maydon nomi bilan o'rnatadi (masalan "smoothLook"). Eski qiymat birinchi marta esda qoladi.
     * Bu Baritone'da bunday sozlama yo'q yoki qiymat turiga mos kelmasa false qaytaradi.
     */
    public static boolean set(String name, Object value) {
        try {
            Object settings = Class.forName(API).getMethod("getSettings").invoke(null);
            Object setting = settings.getClass().getField(name).get(settings);
            Field valueField = setting.getClass().getField("value");
            try { valueField.setAccessible(true); } catch (RuntimeException ignored) {}
            Object old = valueField.get(setting);
            Object coerced = coerce(old, value);
            if (coerced == null) return false;
            SAVED.putIfAbsent(name, old);
            valueField.set(setting, coerced);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e);
            return false;
        }
    }

    /**
     * List-turidagi Baritone sozlamasiga (masalan buildIgnoreProperties) mavjud elementlarini yo'qotmasdan qo'shadi.
     * Asl ro'yxat esda saqlanadi va {@link #restoreAll} uni qaytaradi. Bunday sozlama bo'lmasa false qaytaradi.
     */
    public static boolean addToList(String name, String entry) {
        try {
            Object settings = Class.forName(API).getMethod("getSettings").invoke(null);
            Object setting = settings.getClass().getField(name).get(settings);
            Field valueField = setting.getClass().getField("value");
            try { valueField.setAccessible(true); } catch (RuntimeException ignored) {}
            Object old = valueField.get(setting);
            if (!(old instanceof java.util.List<?> list)) return false;
            if (list.contains(entry)) return true;
            java.util.List<Object> copy = new java.util.ArrayList<>(list);
            copy.add(entry);
            SAVED.putIfAbsent(name, old);
            valueField.set(setting, copy);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e);
            return false;
        }
    }

    /**
     * List-turidagi Baritone sozlamasini "ishlamasdan oldingi ro'yxat" + berilgan elementlarga tenglaydi.
     * Boshqa elementlar bilan yana chaqirilsa oldingilarni almashtiradi; bo'sh ro'yxat aslini qaytaradi.
     * {@link #restoreAll} o'chirishda yana aslini qaytaradi.
     */
    public static boolean setListEntries(String name, java.util.List<String> entries) {
        try {
            Object settings = Class.forName(API).getMethod("getSettings").invoke(null);
            Object setting = settings.getClass().getField(name).get(settings);
            Field valueField = setting.getClass().getField("value");
            try { valueField.setAccessible(true); } catch (RuntimeException ignored) {}
            Object current = valueField.get(setting);
            if (!(current instanceof java.util.List<?>)) return false;
            Object base = SAVED.computeIfAbsent(name, k -> current);
            if (!(base instanceof java.util.List<?> baseList)) return false;
            java.util.List<Object> copy = new java.util.ArrayList<>(baseList);
            for (String e : entries) if (!copy.contains(e)) copy.add(e);
            valueField.set(setting, copy);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e);
            return false;
        }
    }

    private static Object coerce(Object old, Object value) {
        if (old == null || value == null) return null;
        if (old instanceof Boolean && value instanceof Boolean) return value;
        if (value instanceof Number n) {
            if (old instanceof Integer) return n.intValue();
            if (old instanceof Double) return n.doubleValue();
            if (old instanceof Float) return n.floatValue();
            if (old instanceof Long) return n.longValue();
        }
        return old.getClass() == value.getClass() ? value : null;
    }

    /** {@link #set} orqali o'zgartirilgan har sozlamani eski qiymatiga qaytaradi. */
    public static void restoreAll() {
        if (SAVED.isEmpty()) return;
        try {
            Object settings = Class.forName(API).getMethod("getSettings").invoke(null);
            for (Map.Entry<String, Object> e : SAVED.entrySet()) {
                try {
                    Object setting = settings.getClass().getField(e.getKey()).get(settings);
                    setting.getClass().getField("value").set(setting, e.getValue());
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        } finally {
            SAVED.clear();
        }
    }

    // ------------------------------------------------------------------ builder state

    /** Shemasi builderda yuklangan (u qurilmoqda yoki pauza). Bitib yoki bekor qilinganda false bo'ladi. */
    public static boolean builderActive() {
        try { return Boolean.TRUE.equals(invoke0(builder(), BUILDER, "isActive")); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e); return false; }
    }

    /** Builder pauzada: biz pauzaga qo'ydim yoki Baritone materiallar yetmasdi. */
    public static boolean builderPaused() {
        try { return Boolean.TRUE.equals(invoke0(builder(), BUILDER, "isPaused")); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e); return false; }
    }

    public static boolean pauseBuilder() {
        try { invoke0(builder(), BUILDER, "pause"); return true; }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e); return false; }
    }

    public static boolean resumeBuilder() {
        try { invoke0(builder(), BUILDER, "resume"); return true; }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e); return false; }
    }

    // ------------------------------------------------------------------ walking

    /** Blok yonida (yoki ustida) yurishni boshlaydi, masalan sandiqchaga. Builder yuklangan holda qoladi: faqat shu maqsadga bo'ysunadi. */
    public static boolean goToBlock(BlockPos pos) {
        try {
            Object goal = Class.forName(GET_TO_BLOCK).getConstructor(BlockPos.class).newInstance(pos);
            Object process = invoke0(primary(), BARITONE, "getCustomGoalProcess");
            Class.forName(GOAL_PROCESS).getMethod("setGoalAndPath", Class.forName(GOAL)).invoke(process, goal);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e);
            return false;
        }
    }

    /** {@link #goToBlock} tomonidan boshlangan yurish hozir davom etsa true. */
    public static boolean goalActive() {
        try {
            Object process = invoke0(primary(), BARITONE, "getCustomGoalProcess");
            return Boolean.TRUE.equals(invoke0(process, GOAL_PROCESS, "isActive"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e);
            return false;
        }
    }

    /**
     * Baritone hozir yurayotgan yo'lning bir nechta keyingi nuqtalari (yo'q yoki Baritone versiyasi klasslarni
     * boshqacha nomlaganda bo'sh). Yo'lda yopiq eshikni player urilib ketmasdan avval ko'rish uchun ishlatiladi.
     */
    public static java.util.List<BlockPos> upcomingPath(int n) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        try {
            Object pathing = invoke0(primary(), BARITONE, "getPathingBehavior");
            Object exec = invoke0(pathing, PATHING, "getCurrent");
            if (exec == null) return out;
            String ex = "baritone.api.pathing.path.IPathExecutor";
            Object posObj = invoke0(exec, ex, "getPosition");
            int from = posObj instanceof Integer i ? Math.max(0, i) : 0;
            Object path = invoke0(exec, ex, "getPath");
            if (path == null) return out;
            Object all = invoke0(path, "baritone.api.pathing.calc.IPath", "positions");
            if (!(all instanceof java.util.List<?> list)) return out;
            for (int i = from; i < list.size() && out.size() < n; i++) {
                if (list.get(i) instanceof BlockPos b) out.add(b.toImmutable());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // ixtiyoriy yordamchi: chaqiruvchi "eshik yonida qolib ketdim" ga qaytadi
        }
        return out;
    }

    /** Baritone'ning barcha ishlarini to'xtatadi (yurish, goal va yuklangan shema). */
    public static boolean cancelEverything() {
        try {
            Object pathing = invoke0(primary(), BARITONE, "getPathingBehavior");
            invoke0(pathing, PATHING, "cancelEverything");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            note(e);
            return false;
        }
    }
}
