package com.anormal.client.theme;

public enum Theme {
    VAPE_V4("Anormal", "Dark sleek charcoal theme with vibrant orange/neon accents"),
    VAPE("Vape", "Vape V4 inspired: near-black rounded panels with the teal accent"),
    FROZEN("Frozen", "Icy arctic glass: pale blue frost, crystalline borders, drifting snow and icy chimes"),
    GLASSMORPHISM("Glassmorphism", "Modern frosted glass: a moving light rim and a cursor glow"),
    BRUTALISM("Brutalism", "Neo-brutalist high contrast: hard accent shadows, hazard tape, blinking corner blocks and stepped motion");

    private final String displayName;
    private final String description;

    Theme(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
