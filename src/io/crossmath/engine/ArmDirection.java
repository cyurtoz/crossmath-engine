package io.crossmath.engine;

/** Direction of an equation arm in the grid. */
public enum ArmDirection {
    HORIZONTAL,
    VERTICAL;

    public ArmDirection perpendicular() {
        return this == HORIZONTAL ? VERTICAL : HORIZONTAL;
    }
}
