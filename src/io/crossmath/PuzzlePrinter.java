package io.crossmath;

/**
 * Renders a {@link PuzzleGrid} to the console as a formatted 2D display grid.
 *
 * <h2>Display grid layout</h2>
 * The {@code (2n−1) × (2n−1)} display grid maps to the {@code n×n} number
 * matrix as follows:
 * <pre>
 *   (even displayRow, even displayCol)  →  number cell
 *   (even displayRow, odd  displayCol)  →  horizontal: operator symbol or '='
 *   (odd  displayRow, even displayCol)  →  vertical:   operator symbol or '='
 *   (odd  displayRow, odd  displayCol)  →  empty spacer (cross-point)
 * </pre>
 *
 * <h2>Operator and '=' index formula</h2>
 * For a horizontal cell at {@code (even displayRow, odd displayCol)}:
 * <pre>
 *   equationIndex = (displayCol − 1) / 4
 *   isEqualSign   = (displayCol % 4 == 3)
 *
 *   displayCol:  1 → op[eq=0],  3 → '='[eq=0],  5 → op[eq=1],  7 → '='[eq=1], …
 * </pre>
 * The same formula applies vertically with {@code displayRow} in place of
 * {@code displayCol}.
 *
 * <h2>Equation masking</h2>
 * An equation hidden in the {@link EquationMask} renders its operator and
 * '=' sign as blank space. Number cells are always shown because they
 * participate in perpendicular equations.
 */
public class PuzzlePrinter {

    private static final String BLANK_CELL = " ";

    private final PuzzleGrid   grid;
    private final EquationMask mask;
    private final int          matrixSize;
    private final int          displaySize;   // = 2 * matrixSize - 1

    // ── Construction ──────────────────────────────────────────────────────────

    public PuzzlePrinter(PuzzleGrid grid, EquationMask mask) {
        this.grid        = grid;
        this.mask        = mask;
        this.matrixSize  = grid.config.matrixSize;
        this.displaySize = 2 * matrixSize - 1;
    }

    // ── Public print methods ──────────────────────────────────────────────────

    /** Prints the fully solved grid with all numbers visible. */
    public void printSolution() {
        printBanner("Solved");
        printGrid(false);
    }

    /** Prints the playable puzzle with all number cells shown as '?'. */
    public void printPuzzle() {
        printBanner("Puzzle  — fill in the ?s");
        printGrid(true);
    }

    /** Prints all equations in human-readable form — useful for debugging. */
    public void printEquations() {
        System.out.println("  ── Horizontal equations ─────────────────────────");
        for (int row = 0; row < matrixSize; row++) {
            for (int equationIndex = 0; equationIndex < grid.config.equationsPerLine; equationIndex++) {
                int leftColumn   = equationIndex * 2;
                int rightColumn  = leftColumn + 1;
                int resultColumn = leftColumn + 2;

                System.out.printf("  H[row=%d][eq=%d]:  %4d  %c  %4d  =  %4d%n",
                    row, equationIndex,
                    grid.numbers[row][leftColumn],
                    grid.horizontalOperators[row][equationIndex].symbol(),
                    grid.numbers[row][rightColumn],
                    grid.numbers[row][resultColumn]);
            }
        }

        System.out.println("  ── Vertical equations ───────────────────────────");
        for (int column = 0; column < matrixSize; column++) {
            for (int equationIndex = 0; equationIndex < grid.config.equationsPerLine; equationIndex++) {
                int topRow    = equationIndex * 2;
                int bottomRow = topRow + 1;
                int resultRow = topRow + 2;

                System.out.printf("  V[col=%d][eq=%d]:  %4d  %c  %4d  =  %4d%n",
                    column, equationIndex,
                    grid.numbers[topRow][column],
                    grid.verticalOperators[column][equationIndex].symbol(),
                    grid.numbers[bottomRow][column],
                    grid.numbers[resultRow][column]);
            }
        }
        System.out.println();
    }

    // ── Core rendering ────────────────────────────────────────────────────────

    private void printGrid(boolean hideNumbers) {
        System.out.println();
        for (int displayRow = 0; displayRow < displaySize; displayRow++) {
            System.out.print("    ");
            for (int displayCol = 0; displayCol < displaySize; displayCol++) {
                System.out.printf("%5s", tokenAt(displayRow, displayCol, hideNumbers));
            }
            System.out.println();
        }
        System.out.println();
    }

    /**
     * Returns the display token for position ({@code displayRow}, {@code displayCol})
     * in the {@code (2n−1)×(2n−1)} display grid.
     */
    private String tokenAt(int displayRow, int displayCol, boolean hideNumbers) {
        boolean rowIsEven = displayRow % 2 == 0;
        boolean colIsEven = displayCol % 2 == 0;

        if (rowIsEven && colIsEven) {
            return numberCellToken(displayRow, displayCol, hideNumbers);
        }

        if (rowIsEven) {
            return horizontalCellToken(displayRow, displayCol);
        }

        if (colIsEven) {
            return verticalCellToken(displayRow, displayCol);
        }

        // Odd displayRow AND odd displayCol — cross-point spacer
        return "";
    }

    private String numberCellToken(int displayRow, int displayCol, boolean hideNumbers) {
        if (hideNumbers) {
            return "?";
        }
        int matrixRow    = displayRow / 2;
        int matrixColumn = displayCol / 2;
        return String.valueOf(grid.numbers[matrixRow][matrixColumn]);
    }

    private String horizontalCellToken(int displayRow, int displayCol) {
        int row           = displayRow / 2;
        int equationIndex = (displayCol - 1) / 4;
        boolean isEqualSign = displayCol % 4 == 3;

        if (!mask.isVisible(EquationId.Axis.HORIZONTAL, row, equationIndex)) {
            return BLANK_CELL;
        }

        if (isEqualSign) {
            return "=";
        }
        return String.valueOf(grid.horizontalOperators[row][equationIndex].symbol());
    }

    private String verticalCellToken(int displayRow, int displayCol) {
        int column        = displayCol / 2;
        int equationIndex = (displayRow - 1) / 4;
        boolean isEqualSign = displayRow % 4 == 3;

        if (!mask.isVisible(EquationId.Axis.VERTICAL, column, equationIndex)) {
            return BLANK_CELL;
        }

        if (isEqualSign) {
            return "=";
        }
        return String.valueOf(grid.verticalOperators[column][equationIndex].symbol());
    }

    // ── Banner helper ─────────────────────────────────────────────────────────

    private static void printBanner(String title) {
        String horizontalRule = "═".repeat(46);
        System.out.println("\n  ╔" + horizontalRule + "╗");
        System.out.printf(  "  ║  %-44s  ║%n", "CROSSMATH  —  " + title);
        System.out.println( "  ╚" + horizontalRule + "╝");
    }
}
