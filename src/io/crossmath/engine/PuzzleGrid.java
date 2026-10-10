package io.crossmath.engine;

/**
 * Fully solved puzzle grid: shape + cell values + arm operators.
 *
 * <p>The grid is sparse — only cells claimed by at least one arm have
 * meaningful values. Unclaimed cells hold {@link Integer#MIN_VALUE}.
 *
 * <p>Operators are stored on each {@link EquationArm} (set during filling).
 */
public class PuzzleGrid {

    public final PuzzleConfig config;
    public final PuzzleShape  shape;

    /**
     * Number values indexed as {@code values[row][col]}.
     * {@code Integer.MIN_VALUE} means the cell is unused.
     */
    public final int[][] values;

    // ── Construction ──────────────────────────────────────────────────────────

    public PuzzleGrid(PuzzleConfig config, PuzzleShape shape) {
        this.config = config;
        this.shape  = shape;
        this.values = new int[config.matrixSize][config.matrixSize];
        for (int[] row : values) {
            java.util.Arrays.fill(row, Integer.MIN_VALUE);
        }
    }

    // ── Cell access ───────────────────────────────────────────────────────────

    public int getValue(GridCell cell) {
        return values[cell.row()][cell.col()];
    }

    public void setValue(GridCell cell, int value) {
        values[cell.row()][cell.col()] = value;
    }

    public boolean hasValue(GridCell cell) {
        return values[cell.row()][cell.col()] != Integer.MIN_VALUE;
    }

    // ── Verification ──────────────────────────────────────────────────────────

    /**
     * Independently verifies every arm equation in the grid.
     * For each arm: {@code op.apply(operand[0], operand[1]) == result}.
     *
     * @return {@code true} if every equation is satisfied
     */
    public boolean verify() {
        for (EquationArm arm : shape.arms()) {
            Operator op = arm.operator();
            if (op == null) return false;

            int left   = getValue(arm.operandCells().get(0));
            int right  = getValue(arm.operandCells().get(1));
            int result = getValue(arm.resultCell());

            int computed = op.apply(left, right, config);
            if (computed != result) {
                return false;
            }
        }
        return true;
    }
}
