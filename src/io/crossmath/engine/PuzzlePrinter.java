package io.crossmath.engine;

import java.util.List;
import java.util.Map;

/**
 * Renders a {@link PuzzleGrid} to the console as a formatted 2D display grid.
 *
 * <p>Supports both fixed-grid mode (dense n×n) and shape mode (sparse).
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
 */
public class PuzzlePrinter {

    private static final String BLANK_CELL = " ";

    private final PuzzleGrid   grid;
    private final EquationMask mask;
    private final int          matrixSize;
    private final int          displaySize;

    public PuzzlePrinter(PuzzleGrid grid, EquationMask mask) {
        this.grid        = grid;
        this.mask        = mask;
        this.matrixSize  = grid.config.matrixSize;
        this.displaySize = 2 * matrixSize - 1;
    }

    // ── Public print methods ──────────────────────────────────────────────────

    public void printSolution() {
        printBanner("Solved");
        if (grid.isShapeMode()) {
            printShapeGrid(false);
        } else {
            printGrid(false);
        }
    }

    public void printPuzzle() {
        printBanner("Puzzle  — fill in the ?s");
        if (grid.isShapeMode()) {
            printShapeGrid(true);
        } else {
            printGrid(true);
        }
    }

    public void printEquations() {
        if (grid.isShapeMode()) {
            printShapeEquations();
        } else {
            printFixedGridEquations();
        }
    }

    // ── Fixed-grid rendering ─────────────────────────────────────────────────

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
        return "";
    }

    private String numberCellToken(int displayRow, int displayCol, boolean hideNumbers) {
        if (hideNumbers) return "?";
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
        if (isEqualSign) return "=";
        return String.valueOf(grid.horizontalOperators[row][equationIndex].symbol());
    }

    private String verticalCellToken(int displayRow, int displayCol) {
        int column        = displayCol / 2;
        int equationIndex = (displayRow - 1) / 4;
        boolean isEqualSign = displayRow % 4 == 3;

        if (!mask.isVisible(EquationId.Axis.VERTICAL, column, equationIndex)) {
            return BLANK_CELL;
        }
        if (isEqualSign) return "=";
        return String.valueOf(grid.verticalOperators[column][equationIndex].symbol());
    }

    private void printFixedGridEquations() {
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

    // ── Shape-based rendering ────────────────────────────────────────────────

    private void printShapeGrid(boolean hideNumbers) {
        PuzzleShape shape = grid.shape();
        String[][] display = new String[displaySize][displaySize];

        for (int r = 0; r < displaySize; r++)
            for (int c = 0; c < displaySize; c++)
                display[r][c] = "";

        for (GridCell cell : shape.allClaimedCells()) {
            int dr = cell.row() * 2;
            int dc = cell.col() * 2;
            if (hideNumbers) {
                display[dr][dc] = "?";
            } else {
                display[dr][dc] = String.valueOf(grid.getCellValue(cell));
            }
        }

        int armIndex = 0;
        for (EquationArm arm : shape.arms()) {
            boolean visible = mask.isArmVisible(armIndex);
            List<Operator> ops = grid.getArmOperators(arm);
            List<GridCell> operands = arm.operandCells();

            for (int i = 0; i < ops.size(); i++) {
                GridCell a = operands.get(i);
                GridCell b = operands.get(i + 1);
                int displaySymRow = a.row() + b.row();
                int displaySymCol = a.col() + b.col();

                if (displaySymRow >= 0 && displaySymRow < displaySize
                        && displaySymCol >= 0 && displaySymCol < displaySize) {
                    display[displaySymRow][displaySymCol] =
                            visible ? String.valueOf(ops.get(i).symbol()) : BLANK_CELL;
                }
            }

            GridCell lastOperand = operands.get(operands.size() - 1);
            GridCell result = arm.resultCell();
            int eqRow = lastOperand.row() + result.row();
            int eqCol = lastOperand.col() + result.col();

            if (eqRow >= 0 && eqRow < displaySize
                    && eqCol >= 0 && eqCol < displaySize) {
                display[eqRow][eqCol] = visible ? "=" : BLANK_CELL;
            }

            armIndex++;
        }

        System.out.println();
        for (int r = 0; r < displaySize; r++) {
            System.out.print("    ");
            for (int c = 0; c < displaySize; c++) {
                System.out.printf("%5s", display[r][c]);
            }
            System.out.println();
        }
        System.out.println();
    }

    private void printShapeEquations() {
        PuzzleShape shape = grid.shape();
        System.out.println("  ── Equations ────────────────────────────────────");

        int armIndex = 0;
        for (EquationArm arm : shape.arms()) {
            List<Operator> ops = grid.getArmOperators(arm);
            List<GridCell> operands = arm.operandCells();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("  %s[%d]: ", arm.direction(), armIndex));

            for (int i = 0; i < operands.size(); i++) {
                sb.append(String.format("%4d", grid.getCellValue(operands.get(i))));
                if (i < ops.size()) {
                    sb.append(String.format("  %c  ", ops.get(i).symbol()));
                }
            }
            sb.append(String.format("  =  %4d", grid.getCellValue(arm.resultCell())));
            System.out.println(sb);
            armIndex++;
        }
        System.out.println();
    }

    // ── Banner helper ─────────────────────────────────────────────────────────

    private static void printBanner(String title) {
        String horizontalRule = "═".repeat(46);
        System.out.println("\n  ╔" + horizontalRule + "╗");
        System.out.printf(  "  ║  %-44s  ║%n", "CROSSMATH  —  " + title);
        System.out.println( "  ╚" + horizontalRule + "╝");
    }
}
