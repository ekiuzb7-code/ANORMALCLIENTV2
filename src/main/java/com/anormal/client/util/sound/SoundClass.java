package com.anormal.client.util.sound;

/** Event families exposed by the Sound Detector, mirroring the reference client's toggles. */
public enum SoundClass {
    MUSIC("Music"),
    MOVEMENT("Movement"),
    COMBAT("Combat"),
    TRANSPORT("Transport"),
    EXPLOSIONS("Explosions"),
    FARMING("Farming"),
    SCULK("Sculk"),
    NATURAL("Natural"),
    /** Container interactions; controlled by the "Custom" category toggle like the reference client. */
    CONTAINER("Container Interaction"),
    CUSTOM("Custom");

    private final String display;

    SoundClass(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
