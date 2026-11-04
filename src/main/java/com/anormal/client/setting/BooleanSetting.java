package com.anormal.client.setting;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    /** Short form used by the ported kuro modules. */
    public BooleanSetting(String name, boolean defaultValue) {
        this(name, name, defaultValue);
    }

    /** Lucid-style setter used by the ported modules. */
    public void setEnabled(boolean value) {
        setValue(value);
    }

    public void toggle() {
        setValue(!getValue());
    }

    /** Lucid-style accessor used by the ported modules. */
    public boolean get() {
        return isEnabled();
    }

    @Override
    public void setValue(Boolean value) {
        super.setValue(value != null && value);
    }

    public boolean isEnabled() {
        return getValue();
    }
}
