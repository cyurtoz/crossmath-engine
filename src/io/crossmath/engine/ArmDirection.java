package io.crossmath.engine;

public enum ArmDirection {
    HORIZONTAL,
    VERTICAL;

    public int rowDelta() {
        return this == VERTICAL ? 1 : 0;
    }

    public int colDelta() {
        return this == HORIZONTAL ? 1 : 0;
    }

    public ArmDirection perpendicular() {
        return this == HORIZONTAL ? VERTICAL : HORIZONTAL;
    }
}
