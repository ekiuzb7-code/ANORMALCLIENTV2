package com.anormal.client.setting;

/**
 * A settings row that runs an action instead of storing a value (for example "Staff list /
 * evidence"). The HUD editor and profile store skip it because it has no value.
 */
public class ActionSetting extends Setting<Void> {

    private final Runnable action;

    public ActionSetting(String name, String description, Runnable action) {
        super(name, description, null);
        this.action = action;
    }

    public void run() {
        if (action == null) return;
        try {
            action.run();
        } catch (Throwable ignored) {
        }
    }
}
