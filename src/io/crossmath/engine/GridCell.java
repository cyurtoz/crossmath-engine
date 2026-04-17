package io.crossmath.engine;

/**
 * A cell position in the number matrix, using matrix coordinates.
 * Row and column are indices into the {@code matrixSize × matrixSize} grid
 * where number cells live at even-row, even-column positions in the display.
 */
public record GridCell(int row, int col) implements Comparable<GridCell> {

    @Override
    public int compareTo(GridCell other) {
        int cmp = Integer.compare(this.row, other.row);
        return cmp != 0 ? cmp : Integer.compare(this.col, other.col);
    }

    @Override
    public String toString() {
        return "(" + row + "," + col + ")";
    }
}
