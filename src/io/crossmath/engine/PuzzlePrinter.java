package io.crossmath.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a sparse {@link PuzzleGrid} to the console.
 *
 * <p>The display grid is {@code (2n−1) × (2n−1)} where {@code n = matrixSize}.
 * Only cells claimed by at least one arm are rendered; all others are blank.
 *
 * <p>Display positions:
 * <pre>
 *   (even row, even col)  →  number cell
 *   (even row, odd  col)  →  horizontal operator or '='
 *   (odd  row, even col)  →  vertical operator or '='
 *   (odd  row, odd  col)  →  empty spacer
 * </pre>
 */
public class PuzzlePrinter {

    private final PuzzleGrid   grid;
    private final EquationMask mask;
    private final int          matrixSize;
    private final int          displaySize;

    /** Precomputed symbol map: display position → character (operator or '='). */
    private final Map<Long, Character> symbolMap;

    /** Precomputed set of occupied number-cell positions (in matrix coords). */
    private final boolean[][] occupied;
    /** Bounding box of occupied cells so sparse shapes print without blank tails. */
    private int minOccupiedRow;
    private int maxOccupiedRow;
    private int minOccupiedCol;
    private int maxOccupiedCol;

    public PuzzlePrinter(PuzzleGrid grid, EquationMask mask) {
        this.grid        = grid;
        this.mask        = mask;
        this.matrixSize  = grid.config.matrixSize;
        this.displaySize = 2 * matrixSize - 1;
        this.symbolMap   = new HashMap<>();
        this.occupied    = new boolean[matrixSize][matrixSize];
        this.minOccupiedRow = matrixSize;
        this.maxOccupiedRow = -1;
        this.minOccupiedCol = matrixSize;
        this.maxOccupiedCol = -1;
        buildDisplayData();
    }

    // ── Public print methods ────────────────────────────────────────────────

    public void printSolution() {
        printBanner("Solved");
        printGrid(false);
    }

    public void printPuzzle() {
        printBanner("Puzzle  — fill in the ?s");
        printGrid(true);
    }

    public void printEquations() {
        List<EquationArm> arms = grid.shape.arms();
        System.out.println("  ── Equations ────────────────────────────────────");
        for (int i = 0; i < arms.size(); i++) {
            EquationArm arm = arms.get(i);
            char dir = arm.direction() == ArmDirection.HORIZONTAL ? 'H' : 'V';
            GridCell a = arm.operandCells().get(0);
            GridCell b = arm.operandCells().get(1);
            GridCell r = arm.resultCell();
            System.out.printf("  [%d] %c %s:  %4d  %c  %4d  =  %4d%n",
                i, dir, a + "→" + r,
                grid.getValue(a),
                arm.operator() != null ? arm.operator().symbol() : '?',
                grid.getValue(b),
                grid.getValue(r));
        }
        System.out.println();
    }

    // ── Core rendering ──────────────────────────────────────────────────────

    private void printGrid(boolean hideNumbers) {
        // Determine field width from max value
        int maxVal = 0;
        for (int r = 0; r < matrixSize; r++)
            for (int c = 0; c < matrixSize; c++)
                if (occupied[r][c]) maxVal = Math.max(maxVal, Math.abs(grid.values[r][c]));
        int numWidth = Math.max(2, String.valueOf(maxVal).length());
        String numFormat = "%" + (numWidth + 2) + "s";
        // Trim rendering to the occupied bounding box so normalized sparse
        // shapes do not carry a full matrix of empty border cells.
        int startDisplayRow = minOccupiedRow <= maxOccupiedRow ? minOccupiedRow * 2 : 0;
        int endDisplayRow   = minOccupiedRow <= maxOccupiedRow ? maxOccupiedRow * 2 : displaySize - 1;
        int startDisplayCol = minOccupiedCol <= maxOccupiedCol ? minOccupiedCol * 2 : 0;
        int endDisplayCol   = minOccupiedCol <= maxOccupiedCol ? maxOccupiedCol * 2 : displaySize - 1;

        System.out.println();
        for (int dr = startDisplayRow; dr <= endDisplayRow; dr++) {
            System.out.print("    ");
            for (int dc = startDisplayCol; dc <= endDisplayCol; dc++) {
                boolean rowEven = dr % 2 == 0;
                boolean colEven = dc % 2 == 0;

                if (rowEven && colEven) {
                    // Number cell
                    int mr = dr / 2, mc = dc / 2;
                    if (occupied[mr][mc]) {
                        String token = hideNumbers ? "?"
                            : String.valueOf(grid.values[mr][mc]);
                        System.out.printf(numFormat, token);
                    } else {
                        System.out.printf(numFormat, "");
                    }
                } else if (rowEven && !colEven) {
                    // Horizontal symbol
                    Character sym = symbolMap.get(key(dr, dc));
                    if (sym != null) {
                        System.out.printf(numFormat, String.valueOf(sym));
                    } else {
                        System.out.printf(numFormat, "");
                    }
                } else if (!rowEven && colEven) {
                    // Vertical symbol
                    Character sym = symbolMap.get(key(dr, dc));
                    if (sym != null) {
                        System.out.printf(numFormat, String.valueOf(sym));
                    } else {
                        System.out.printf(numFormat, "");
                    }
                } else {
                    // Odd-odd spacer
                    System.out.printf(numFormat, "");
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    // ── Display data pre-computation ────────────────────────────────────────

    private void buildDisplayData() {
        List<EquationArm> arms = grid.shape.arms();
        for (int i = 0; i < arms.size(); i++) {
            EquationArm arm = arms.get(i);
            boolean visible = mask.isArmVisible(i);

            // Mark all cells as occupied
            for (GridCell cell : arm.allCells()) {
                occupied[cell.row()][cell.col()] = true;
                minOccupiedRow = Math.min(minOccupiedRow, cell.row());
                maxOccupiedRow = Math.max(maxOccupiedRow, cell.row());
                minOccupiedCol = Math.min(minOccupiedCol, cell.col());
                maxOccupiedCol = Math.max(maxOccupiedCol, cell.col());
            }

            if (!visible) continue;

            // Place operator symbol between operand[0] and operand[1]
            GridCell a = arm.operandCells().get(0);
            GridCell b = arm.operandCells().get(1);
            GridCell r = arm.resultCell();
            char opSym = arm.operator() != null ? arm.operator().symbol() : '?';

            if (arm.direction() == ArmDirection.HORIZONTAL) {
                // Operator between a and b: display row = a.row*2, col = a.col*2 + 1
                symbolMap.put(key(a.row() * 2, a.col() * 2 + 1), opSym);
                // = between b and r: display row = b.row*2, col = b.col*2 + 1
                symbolMap.put(key(b.row() * 2, b.col() * 2 + 1), '=');
            } else {
                // Operator between a and b: display row = a.row*2 + 1, col = a.col*2
                symbolMap.put(key(a.row() * 2 + 1, a.col() * 2), opSym);
                // = between b and r: display row = b.row*2 + 1, col = b.col*2
                symbolMap.put(key(b.row() * 2 + 1, b.col() * 2), '=');
            }
        }
    }

    private static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }

    // ── Banner ──────────────────────────────────────────────────────────────

    private static void printBanner(String title) {
        String hr = "═".repeat(46);
        System.out.println("\n  ╔" + hr + "╗");
        System.out.printf(  "  ║  %-44s  ║%n", "CROSSMATH  —  " + title);
        System.out.println( "  ╚" + hr + "╝");
    }
}
