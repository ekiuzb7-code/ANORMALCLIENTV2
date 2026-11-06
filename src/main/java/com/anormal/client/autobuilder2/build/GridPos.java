package com.anormal.client.autobuilder2.build;

/**
 * An integer block coordinate.
 *
 * <p>Kept deliberately free of any Minecraft type so the planning logic in this package can be
 * compiled and exercised on its own, without a running game.
 */
public record GridPos(int x, int y, int z) {
    public GridPos offset(int dx, int dy, int dz) {
        return new GridPos(this.x + dx, this.y + dy, this.z + dz);
    }

    public GridPos below() {
        return new GridPos(this.x, this.y - 1, this.z);
    }

    public GridPos above() {
        return new GridPos(this.x, this.y + 1, this.z);
    }

    public long squaredDistanceTo(GridPos other) {
        long dx = (long) this.x - other.x;
        long dy = (long) this.y - other.y;
        long dz = (long) this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public int manhattanTo(GridPos other) {
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y) + Math.abs(this.z - other.z);
    }

    @Override
    public String toString() {
        return this.x + "," + this.y + "," + this.z;
    }
}
