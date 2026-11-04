package com.anormal.client.setting;

public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getMin() {
        return min;
    }

    /** Primitive accessor used by the ported kuro modules, so `(int) setting.getAsDouble()` works. */
    public double getAsDouble() {
        Double value = getValue();
        return value == null ? 0.0 : value;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    @Override
    public void setValue(Double value) {
        double clamped = Math.max(min, Math.min(max, value));
        double precision = 1.0 / step;
        double rounded = Math.round(clamped * precision) / precision;
        super.setValue(rounded);
    }
}
