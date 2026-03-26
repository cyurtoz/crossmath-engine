package io.crossmath.engine;

/**
 * Identifies a single equation within the puzzle grid.
 *
 * <pre>
 *   axis=HORIZONTAL, lineIndex=row,    equationIndex=k  →  horizontal eq k in that row
 *   axis=VERTICAL,   lineIndex=column, equationIndex=k  →  vertical   eq k in that column
 *   equationIndex ∈ [0, equationsPerLine)
 * </pre>
 */
public record EquationId(Axis axis, int lineIndex, int equationIndex) {

    public enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    @Override
    public String toString() {
        return axis + "[line=" + lineIndex + ", eq=" + equationIndex + "]";
    }
}
