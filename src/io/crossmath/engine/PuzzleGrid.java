package io.crossmath.engine;

/**
 * Fully solved puzzle grid: the number matrix and all operators.
 *
 * <h2>Layout (matrixSize=5, equationsPerLine=2, display grid=9×9)</h2>
 * <pre>
 *   numbers[0][0]  hOp[0][0]  numbers[0][1]  =  numbers[0][2]  hOp[0][1]  numbers[0][3]  =  numbers[0][4]
 *   vOp[0][0]                 vOp[1][0]          vOp[2][0]                 vOp[3][0]          vOp[4][0]
 *   numbers[1][0]  hOp[1][0]  numbers[1][1]  =  numbers[1][2]  hOp[1][1]  numbers[1][3]  =  numbers[1][4]
 *      =                         =                  =                         =                  =
 *   numbers[2][0]  hOp[2][0]  numbers[2][1]  =  numbers[2][2]  hOp[2][1]  numbers[2][3]  =  numbers[2][4]
 *   ...
 * </pre>
 *
 * <h2>Equation indexing</h2>
 * <ul>
 *   <li>Horizontal equation {@code eq} in row {@code row}:
 *       {@code numbers[row][eq*2]  hOp[row][eq]  numbers[row][eq*2+1]  =  numbers[row][eq*2+2]}</li>
 *   <li>Vertical equation {@code eq} in column {@code col}:
 *       {@code numbers[eq*2][col]  vOp[col][eq]  numbers[eq*2+1][col]  =  numbers[eq*2+2][col]}</li>
 * </ul>
 */
public class PuzzleGrid {

    public final PuzzleConfig config;

    /** Number matrix indexed as {@code numbers[row][column]}. */
    public final int[][] numbers;

    /** Horizontal operators indexed as {@code horizontalOperators[row][equationIndex]}. */
    public final Operator[][] horizontalOperators;

    /** Vertical operators indexed as {@code verticalOperators[column][equationIndex]}. */
    public final Operator[][] verticalOperators;

    // ── Construction ──────────────────────────────────────────────────────────

    public PuzzleGrid(PuzzleConfig config) {
        this.config               = config;
        this.numbers              = new int[config.matrixSize][config.matrixSize];
        this.horizontalOperators  = new Operator[config.matrixSize][config.equationsPerLine];
        this.verticalOperators    = new Operator[config.matrixSize][config.equationsPerLine];
    }

    // ── Verification ──────────────────────────────────────────────────────────

    /**
     * Independently verifies every equation in the grid.
     * Checks all {@code 2 × matrixSize × equationsPerLine} equations.
     *
     * @return {@code true} if every equation is satisfied; {@code false} otherwise
     */
    public boolean verify() {
        for (int row = 0; row < config.matrixSize; row++) {
            for (int equationIndex = 0; equationIndex < config.equationsPerLine; equationIndex++) {
                int leftColumn   = equationIndex * 2;
                int rightColumn  = leftColumn + 1;
                int resultColumn = leftColumn + 2;

                Operator horizontalOperator = horizontalOperators[row][equationIndex];
                int computedResult = horizontalOperator.apply(
                    numbers[row][leftColumn],
                    numbers[row][rightColumn],
                    config
                );
                if (computedResult != numbers[row][resultColumn]) {
                    return false;
                }
            }
        }

        for (int column = 0; column < config.matrixSize; column++) {
            for (int equationIndex = 0; equationIndex < config.equationsPerLine; equationIndex++) {
                int topRow    = equationIndex * 2;
                int bottomRow = topRow + 1;
                int resultRow = topRow + 2;

                Operator verticalOperator = verticalOperators[column][equationIndex];
                int computedResult = verticalOperator.apply(
                    numbers[topRow][column],
                    numbers[bottomRow][column],
                    config
                );
                if (computedResult != numbers[resultRow][column]) {
                    return false;
                }
            }
        }

        return true;
    }
}
