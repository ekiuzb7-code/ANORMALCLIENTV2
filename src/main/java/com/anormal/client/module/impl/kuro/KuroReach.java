package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.attribute.EntityAttribute;

/**
 * Ported one-to-one from kuro (dev.kuro.client.module.impl.Reach).
 *
 * <p>Longer interaction range for your own player. Vanilla is 4.5 for blocks and 3.0 for
 * entities. Servers check reach themselves, so values well above vanilla usually get rejected
 * or flagged by anti-cheat. Hit chance (%) decides, once per tick, whether the longer entity
 * reach is used at all: 100 = always, 50 = about half of the ticks, 0 = never.</p>
 *
 * <p>kuro drives this from a PlayerEntityMixin; here it uses the vanilla player attributes
 * ({@code block_interaction_range} / {@code entity_interaction_range}), which the game itself
 * re-reads every interaction, so no mixin is needed and behaviour is identical.</p>
 */
public class KuroReach extends Module {
    private static final double VANILLA_BLOCK = 4.5;
    private static final double VANILLA_ENTITY = 3.0;

    private static KuroReach instance;

    private final NumberSetting blockReach = new NumberSetting("Block reach",
            "Max interaction range for blocks", 6.0, 4.5, 8.0, 0.5);
    private final NumberSetting entityReach = new NumberSetting("Entity reach",
            "Max reach for attacking entities", 4.0, 3.0, 6.0, 0.5);
    private final NumberSetting chance = new NumberSetting("Hit chance (%)",
            "Chance the longer entity reach applies per tick", 100.0, 0.0, 100.0, 5.0);

    private boolean extended = true; // this tick's roll: true = the longer entity reach applies

    public KuroReach() {
        super("Reach", "Longer block/entity interaction range (kuro port)", Category.KURO);
        instance = this;
        addSetting(blockReach);
        addSetting(entityReach);
        addSetting(chance);
    }

    @Override
    public void onEnable() {
        roll();
        apply();
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        setBase(EntityAttributes.BLOCK_INTERACTION_RANGE, VANILLA_BLOCK);
        setBase(EntityAttributes.ENTITY_INTERACTION_RANGE, VANILLA_ENTITY);
    }

    /** One roll per client tick, so a swing and the crosshair check in the same tick always agree. */
    @Override
    public void onTick() {
        if (mc.player == null) return;
        roll();
        apply();
    }

    private void roll() {
        extended = chance.getValue() >= 100.0
                || java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0 < chance.getValue();
    }

    private void apply() {
        setBase(EntityAttributes.BLOCK_INTERACTION_RANGE, blockReach.getValue());
        setBase(EntityAttributes.ENTITY_INTERACTION_RANGE,
                extended ? entityReach.getValue() : VANILLA_ENTITY);
    }

    private void setBase(RegistryEntry<EntityAttribute> attribute, double value) {
        if (mc.player == null) return;
        EntityAttributeInstance inst = mc.player.getAttributeInstance(attribute);
        if (inst != null) inst.setBaseValue(value);
    }

    /** @return the extended block range, or a negative number when the module is off. */
    public static double block() {
        return instance != null && instance.isEnabled() ? instance.blockReach.getValue() : -1;
    }

    /** Entity reach is only extended on ticks that pass the Hit chance roll. */
    public static double entityValue() {
        return instance != null && instance.isEnabled() && instance.extended
                ? instance.entityReach.getValue() : -1;
    }
}
