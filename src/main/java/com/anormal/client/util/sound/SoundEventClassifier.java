package com.anormal.client.util.sound;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps a Minecraft sound id to a category, a display name and a score kind.
 *
 * <p>Ids are kept as plain strings (the path after {@code minecraft:}) so a version bump only
 * needs this file updated, never the detector logic. The table covers the sounds the client
 * receives most often; everything else falls through to token rules and finally to
 * {@link SoundClass#CUSTOM} with the raw id shown in debug mode.</p>
 */
public final class SoundEventClassifier {

    /** Score families from the spec, resolved to user-configurable numbers by the module. */
    public enum Kind {
        AMBIENT, INTERACTION, CONTAINER, PLAYER, COMBAT, EXPLOSION
    }

    public static final class Match {
        public final SoundClass soundClass;
        public final String name;
        public final Kind kind;

        Match(SoundClass soundClass, String name, Kind kind) {
            this.soundClass = soundClass;
            this.name = name;
            this.kind = kind;
        }
    }

    private static final Map<String, Match> TABLE = new HashMap<>();

    private SoundEventClassifier() {
    }

    private static void put(String path, SoundClass soundClass, String name, Kind kind) {
        TABLE.put(path, new Match(soundClass, name, kind));
    }

    /**
     * Containers are reported generically, exactly like the reference card ("Container opened"):
     * the sound id proves a container moved, not which one the player is looking at. The exact id
     * stays available in history and debug mode.
     */
    private static void containers(String prefix) {
        put(prefix + ".open", SoundClass.CONTAINER, "Container opened", Kind.CONTAINER);
        put(prefix + ".close", SoundClass.CONTAINER, "Container closed", Kind.CONTAINER);
    }

    static {
        // ---- Containers -------------------------------------------------
        containers("block.chest");
        containers("block.ender_chest");
        containers("block.shulker_box");
        containers("block.barrel");
        containers("block.trapped_chest");
        put("block.dispenser.dispense", SoundClass.CONTAINER, "Dispenser", Kind.CONTAINER);
        put("block.dispenser.fail", SoundClass.CONTAINER, "Dispenser Empty", Kind.CONTAINER);
        put("block.dropper.dispense", SoundClass.CONTAINER, "Dropper", Kind.CONTAINER);
        put("block.vault.open_shutter", SoundClass.CONTAINER, "Vault Opened", Kind.CONTAINER);
        put("block.vault.close_shutter", SoundClass.CONTAINER, "Vault Closed", Kind.CONTAINER);

        // ---- Doors, buttons, plates, placement --------------------------
        put("block.wooden_door.open", SoundClass.CUSTOM, "Door Opened", Kind.INTERACTION);
        put("block.wooden_door.close", SoundClass.CUSTOM, "Door Closed", Kind.INTERACTION);
        put("block.iron_door.open", SoundClass.CUSTOM, "Iron Door Opened", Kind.INTERACTION);
        put("block.iron_door.close", SoundClass.CUSTOM, "Iron Door Closed", Kind.INTERACTION);
        put("block.wooden_trapdoor.open", SoundClass.CUSTOM, "Trapdoor Opened", Kind.INTERACTION);
        put("block.wooden_trapdoor.close", SoundClass.CUSTOM, "Trapdoor Closed", Kind.INTERACTION);
        put("block.fence_gate.open", SoundClass.CUSTOM, "Fence Gate Opened", Kind.INTERACTION);
        put("block.fence_gate.close", SoundClass.CUSTOM, "Fence Gate Closed", Kind.INTERACTION);
        put("block.lever.click", SoundClass.CUSTOM, "Lever", Kind.INTERACTION);
        put("block.stone_button.click_on", SoundClass.CUSTOM, "Button", Kind.INTERACTION);
        put("block.stone_button.click_off", SoundClass.CUSTOM, "Button", Kind.INTERACTION);
        put("block.wooden_button.click_on", SoundClass.CUSTOM, "Button", Kind.INTERACTION);
        put("block.wooden_button.click_off", SoundClass.CUSTOM, "Button", Kind.INTERACTION);
        put("block.pressure_plate.click_on", SoundClass.CUSTOM, "Pressure Plate", Kind.INTERACTION);
        put("block.pressure_plate.click_off", SoundClass.CUSTOM, "Pressure Plate", Kind.INTERACTION);
        put("block.stone.break", SoundClass.CUSTOM, "Block Broken", Kind.INTERACTION);
        put("block.wood.break", SoundClass.CUSTOM, "Block Broken", Kind.INTERACTION);
        put("block.stone.place", SoundClass.CUSTOM, "Block Placed", Kind.INTERACTION);
        put("block.wood.place", SoundClass.CUSTOM, "Block Placed", Kind.INTERACTION);
        put("block.piston.extend", SoundClass.CUSTOM, "Piston", Kind.INTERACTION);
        put("block.piston.contract", SoundClass.CUSTOM, "Piston", Kind.INTERACTION);
        put("block.bell.use", SoundClass.CUSTOM, "Bell", Kind.INTERACTION);
        put("block.bell.resonate", SoundClass.CUSTOM, "Bell", Kind.INTERACTION);
        put("block.anvil.use", SoundClass.CUSTOM, "Anvil", Kind.INTERACTION);
        put("block.enchantment_table.use", SoundClass.CUSTOM, "Enchanting Table", Kind.INTERACTION);
        put("block.respawn_anchor.set_spawn", SoundClass.CUSTOM, "Respawn Anchor", Kind.INTERACTION);
        put("block.end_portal.spawn", SoundClass.CUSTOM, "End Portal", Kind.INTERACTION);
        put("entity.ender_eye.launch", SoundClass.CUSTOM, "Eye of Ender", Kind.INTERACTION);

        // ---- Movement ---------------------------------------------------
        put("entity.player.splash", SoundClass.MOVEMENT, "Splash", Kind.AMBIENT);
        put("entity.player.splash.high_speed", SoundClass.MOVEMENT, "Splash", Kind.AMBIENT);
        put("entity.player.swim", SoundClass.MOVEMENT, "Swimming", Kind.AMBIENT);
        put("entity.player.big_fall", SoundClass.MOVEMENT, "Hard Landing", Kind.INTERACTION);
        put("entity.player.small_fall", SoundClass.MOVEMENT, "Landing", Kind.AMBIENT);
        put("entity.generic.splash", SoundClass.MOVEMENT, "Splash", Kind.AMBIENT);
        put("entity.boat.paddle_water", SoundClass.TRANSPORT, "Boat", Kind.AMBIENT);
        put("entity.boat.paddle_land", SoundClass.TRANSPORT, "Boat", Kind.AMBIENT);

        // ---- Combat -----------------------------------------------------
        put("entity.player.attack.strong", SoundClass.COMBAT, "Attack", Kind.COMBAT);
        put("entity.player.attack.weak", SoundClass.COMBAT, "Attack", Kind.COMBAT);
        put("entity.player.attack.sweep", SoundClass.COMBAT, "Sweep Attack", Kind.COMBAT);
        put("entity.player.attack.crit", SoundClass.COMBAT, "Critical Hit", Kind.COMBAT);
        put("entity.player.attack.knockback", SoundClass.COMBAT, "Knockback", Kind.COMBAT);
        put("entity.player.attack.nodamage", SoundClass.COMBAT, "Attack", Kind.COMBAT);
        put("entity.player.hurt", SoundClass.COMBAT, "Damage event", Kind.PLAYER);
        put("entity.player.hurt_drown", SoundClass.COMBAT, "Damage event", Kind.PLAYER);
        put("entity.player.hurt_on_fire", SoundClass.COMBAT, "Damage event", Kind.PLAYER);
        put("entity.player.death", SoundClass.COMBAT, "Player died", Kind.PLAYER);
        put("item.shield.block", SoundClass.COMBAT, "Shield Block", Kind.COMBAT);
        put("item.shield.break", SoundClass.COMBAT, "Shield Broken", Kind.COMBAT);
        put("item.trident.throw", SoundClass.COMBAT, "Trident Thrown", Kind.COMBAT);
        put("item.trident.hit", SoundClass.COMBAT, "Trident Hit", Kind.COMBAT);
        put("entity.arrow.shoot", SoundClass.COMBAT, "Arrow Shot", Kind.COMBAT);
        put("entity.arrow.hit", SoundClass.COMBAT, "Arrow Hit", Kind.COMBAT);
        put("entity.arrow.hit_player", SoundClass.COMBAT, "Arrow Hit Player", Kind.PLAYER);
        put("entity.crossbow.shoot", SoundClass.COMBAT, "Crossbow", Kind.COMBAT);
        put("entity.ender_pearl.throw", SoundClass.COMBAT, "Pearl Thrown", Kind.COMBAT);
        put("entity.snowball.throw", SoundClass.COMBAT, "Snowball", Kind.INTERACTION);
        put("entity.egg.throw", SoundClass.COMBAT, "Egg Thrown", Kind.INTERACTION);

        // ---- Villagers and common mobs ----------------------------------
        put("entity.villager.ambient", SoundClass.CUSTOM, "Villager", Kind.INTERACTION);
        put("entity.villager.hurt", SoundClass.COMBAT, "Villager hurt", Kind.COMBAT);
        put("entity.villager.death", SoundClass.COMBAT, "Villager died", Kind.COMBAT);
        put("entity.villager.trade", SoundClass.CUSTOM, "Villager trade", Kind.INTERACTION);
        put("entity.wandering_trader.ambient", SoundClass.CUSTOM, "Wandering trader", Kind.INTERACTION);
        put("entity.wandering_trader.trade", SoundClass.CUSTOM, "Trader trade", Kind.INTERACTION);
        put("entity.zombie_villager.ambient", SoundClass.CUSTOM, "Zombie villager", Kind.INTERACTION);
        put("entity.iron_golem.attack", SoundClass.COMBAT, "Iron golem attack", Kind.COMBAT);
        put("entity.iron_golem.hurt", SoundClass.COMBAT, "Iron golem hurt", Kind.PLAYER);

        // ---- Transport --------------------------------------------------
        put("entity.minecart.rolling", SoundClass.TRANSPORT, "Minecart Moving", Kind.INTERACTION);
        put("entity.minecart.inside", SoundClass.TRANSPORT, "Minecart", Kind.INTERACTION);
        put("entity.minecart.inside.underwater", SoundClass.TRANSPORT, "Minecart", Kind.INTERACTION);
        put("entity.minecart.riding", SoundClass.TRANSPORT, "Minecart Riding", Kind.INTERACTION);

        // ---- Explosions -------------------------------------------------
        put("entity.generic.explode", SoundClass.EXPLOSIONS, "Explosion", Kind.EXPLOSION);
        put("entity.tnt.primed", SoundClass.EXPLOSIONS, "TNT Primed", Kind.COMBAT);
        put("entity.creeper.primed", SoundClass.EXPLOSIONS, "Creeper Primed", Kind.COMBAT);
        put("entity.dragon_fireball.explode", SoundClass.EXPLOSIONS, "Dragon Fireball", Kind.EXPLOSION);
        put("entity.firework_rocket.blast", SoundClass.EXPLOSIONS, "Firework Blast", Kind.COMBAT);
        put("entity.firework_rocket.launch", SoundClass.EXPLOSIONS, "Firework", Kind.AMBIENT);
        put("entity.lightning_bolt.thunder", SoundClass.EXPLOSIONS, "Thunder", Kind.COMBAT);
        put("entity.wither.spawn", SoundClass.EXPLOSIONS, "Wither Spawned", Kind.EXPLOSION);
        put("entity.wither.shoot", SoundClass.COMBAT, "Wither Shooting", Kind.COMBAT);

        // ---- Farming ----------------------------------------------------
        put("block.crop.break", SoundClass.FARMING, "Crop Harvested", Kind.INTERACTION);
        put("item.crop.plant", SoundClass.FARMING, "Crop Planted", Kind.INTERACTION);
        put("item.bone_meal.use", SoundClass.FARMING, "Bone Meal", Kind.INTERACTION);
        put("block.composter.fill", SoundClass.FARMING, "Composter", Kind.INTERACTION);
        put("block.composter.fill_success", SoundClass.FARMING, "Composter Ready", Kind.INTERACTION);
        put("block.composter.ready", SoundClass.FARMING, "Composter Ready", Kind.INTERACTION);
        put("block.sweet_berry_bush.pick", SoundClass.FARMING, "Berries Picked", Kind.INTERACTION);
        put("block.beehive.enter", SoundClass.FARMING, "Beehive", Kind.INTERACTION);
        put("block.beehive.exit", SoundClass.FARMING, "Beehive", Kind.INTERACTION);
        put("block.beehive.shear", SoundClass.FARMING, "Beehive Sheared", Kind.INTERACTION);
        put("block.pumpkin.carve", SoundClass.FARMING, "Pumpkin Carved", Kind.INTERACTION);

        // ---- Sculk ------------------------------------------------------
        put("block.sculk_sensor.clicking", SoundClass.SCULK, "Sculk Sensor", Kind.INTERACTION);
        put("block.sculk_sensor.clicking_stop", SoundClass.SCULK, "Sculk Sensor", Kind.INTERACTION);
        put("block.sculk_shrieker.shriek", SoundClass.SCULK, "Sculk Shrieker", Kind.EXPLOSION);
        put("block.sculk.spread", SoundClass.SCULK, "Sculk Spread", Kind.INTERACTION);
        put("block.sculk.charge", SoundClass.SCULK, "Sculk Charge", Kind.INTERACTION);
        put("block.sculk_catalyst.bloom", SoundClass.SCULK, "Sculk Catalyst", Kind.INTERACTION);
        put("block.sculk_vein.break", SoundClass.SCULK, "Sculk Vein", Kind.INTERACTION);

        // ---- Liquid / environment / weather -----------------------------
        put("block.water.ambient", SoundClass.NATURAL, "Water", Kind.AMBIENT);
        put("block.lava.ambient", SoundClass.NATURAL, "Lava", Kind.AMBIENT);
        put("block.lava.pop", SoundClass.NATURAL, "Lava Pop", Kind.AMBIENT);
        put("block.bubble_column.upwards_ambient", SoundClass.NATURAL, "Bubble Column", Kind.AMBIENT);
        put("block.bubble_column.whirlpool_ambient", SoundClass.NATURAL, "Bubble Column", Kind.AMBIENT);
        put("block.fire.ambient", SoundClass.NATURAL, "Fire", Kind.AMBIENT);
        put("block.fire.extinguish", SoundClass.NATURAL, "Fire Out", Kind.AMBIENT);
        put("weather.rain", SoundClass.NATURAL, "Rain", Kind.AMBIENT);
        put("weather.rain.above", SoundClass.NATURAL, "Rain", Kind.AMBIENT);
        put("ambient.cave", SoundClass.NATURAL, "Cave Ambience", Kind.AMBIENT);
        put("ambient.underwater.enter", SoundClass.NATURAL, "Underwater", Kind.AMBIENT);
        put("ambient.underwater.exit", SoundClass.NATURAL, "Underwater", Kind.AMBIENT);
        put("ambient.underwater.loop", SoundClass.NATURAL, "Underwater", Kind.AMBIENT);
        put("entity.fishing_bobber.splash", SoundClass.NATURAL, "Fishing Splash", Kind.INTERACTION);
        put("entity.generic.splash", SoundClass.NATURAL, "Splash", Kind.AMBIENT);

        // ---- Music ------------------------------------------------------
        put("block.jukebox.play", SoundClass.MUSIC, "Jukebox", Kind.AMBIENT);
        put("block.jukebox.stop", SoundClass.MUSIC, "Jukebox Stopped", Kind.AMBIENT);
        put("block.note_block.banjo", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
        put("block.note_block.bass", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
        put("block.note_block.bell", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
        put("block.note_block.bit", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
        put("block.note_block.chime", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
        put("block.note_block.flute", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
        put("block.note_block.guitar", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
        put("block.note_block.harp", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
        put("block.note_block.pling", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
        put("block.note_block.snare", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
        put("block.note_block.xylophone", SoundClass.MUSIC, "Note Block", Kind.AMBIENT);
    }

    /**
     * Classifies a sound id. Unknown ids are never dropped: they become
     * {@link SoundClass#CUSTOM} with the raw id as their name so debug mode can be used to extend
     * the table for a specific server.
     */
    public static Match classify(Identifier id) {
        if (id == null) return new Match(SoundClass.CUSTOM, "Unknown Sound", Kind.INTERACTION);
        String full = id.toString();
        String path = id.getPath();
        Match exact = TABLE.get(path);
        if (exact != null) return exact;
        return rules(path, full);
    }

    private static Match rules(String path, String full) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        if (p.endsWith(".step") || p.contains("footstep")) {
            return new Match(SoundClass.MOVEMENT, "Footstep", Kind.AMBIENT);
        }
        if (p.startsWith("music.")) return new Match(SoundClass.MUSIC, "Music", Kind.AMBIENT);
        if (p.startsWith("ambient.") || p.startsWith("weather.")) {
            return new Match(SoundClass.NATURAL, "Ambience", Kind.AMBIENT);
        }
        // Any entity ambient/hurt/trade sound the table does not name explicitly still says which
        // mob is nearby, which is exactly what a cave full of villagers or animals leaks.
        if (p.startsWith("entity.")) {
            String[] parts = p.split("\\.");
            if (parts.length >= 3) {
                String kind = parts[parts.length - 1];
                if (kind.equals("ambient") || kind.equals("hurt") || kind.equals("death")
                        || kind.equals("trade") || kind.equals("attack")) {
                    String mob = parts[1].replace('_', ' ');
                    String label = Character.toUpperCase(mob.charAt(0)) + mob.substring(1);
                    if (kind.equals("hurt") || kind.equals("death")) {
                        return new Match(SoundClass.COMBAT, label + " hurt", Kind.PLAYER);
                    }
                    if (kind.equals("trade")) {
                        return new Match(SoundClass.CUSTOM, label + " trade", Kind.INTERACTION);
                    }
                    return new Match(SoundClass.CUSTOM, label, Kind.INTERACTION);
                }
            }
        }
        if (p.endsWith(".ambient")) return new Match(SoundClass.NATURAL, "Ambient", Kind.AMBIENT);
        if (p.contains("sculk")) return new Match(SoundClass.SCULK, "Sculk", Kind.INTERACTION);
        if (p.contains("minecart")) return new Match(SoundClass.TRANSPORT, "Minecart", Kind.INTERACTION);
        if (p.contains("boat")) return new Match(SoundClass.TRANSPORT, "Boat", Kind.AMBIENT);
        if (p.contains("explode") || p.contains("explosion")) {
            return new Match(SoundClass.EXPLOSIONS, "Explosion", Kind.EXPLOSION);
        }
        if (p.contains("chest") || p.contains("barrel") || p.contains("shulker")
                || p.contains("dispenser") || p.contains("dropper") || p.contains("hopper")) {
            return new Match(SoundClass.CONTAINER, "Container", Kind.CONTAINER);
        }
        if (p.contains("hurt") || p.contains("death")) {
            return new Match(SoundClass.COMBAT, "Damage event", Kind.PLAYER);
        }
        if (p.contains("attack") || p.contains("crit") || p.contains("shield")) {
            return new Match(SoundClass.COMBAT, "Combat", Kind.COMBAT);
        }
        if (p.contains("crop") || p.contains("composter") || p.contains("bone_meal")
                || p.contains("beehive")) {
            return new Match(SoundClass.FARMING, "Farming", Kind.INTERACTION);
        }
        if (p.contains("door") || p.contains("trapdoor") || p.contains("fence_gate")
                || p.contains("lever") || p.contains("button") || p.contains("pressure_plate")) {
            return new Match(SoundClass.CUSTOM, "Interaction", Kind.INTERACTION);
        }
        if (p.contains("note_block") || p.contains("jukebox")) {
            return new Match(SoundClass.MUSIC, "Music Block", Kind.AMBIENT);
        }
        if (p.contains("break")) return new Match(SoundClass.CUSTOM, "Block Broken", Kind.INTERACTION);
        if (p.contains("place")) return new Match(SoundClass.CUSTOM, "Block Placed", Kind.INTERACTION);
        if (p.contains("water") || p.contains("lava")) {
            return new Match(SoundClass.NATURAL, "Liquid", Kind.AMBIENT);
        }
        String shortName = path.startsWith("minecraft:") ? path : full;
        return new Match(SoundClass.CUSTOM, shortName, Kind.INTERACTION);
    }
}
