package io.crossmath.engine;

/**
 * A cell position in the number matrix (not the display grid).
 * Row and column are zero-based indices in [0, matrixSize).
 */
public record GridCell(int row, int col) {

    public GridCell step(ArmDirection direction) {
        return switch (direction) {
            case HORIZONTAL -> new GridCell(row, col + 1);
            case VERTICAL   -> new GridCell(row + 1, col);
        };
    }

    public GridCell stepBack(ArmDirection direction) {
        return switch (direction) {
            case HORIZONTAL -> new GridCell(row, col - 1);
            case VERTICAL   -> new GridCell(row - 1, col);
        };
    }

    public boolean inBounds(int matrixSize) {
        return row >= 0 && row < matrixSize && col >= 0 && col < matrixSize;
    }

    @Override
    public String toString() {
        return "(" + row + "," + col + ")";
    }
}
