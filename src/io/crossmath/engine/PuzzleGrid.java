package io.crossmath.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fully solved puzzle grid: cell values and operators for every equation arm.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>Fixed-grid mode</b> — dense {@code matrixSize × matrixSize} arrays for
 *       number values and operators. Used by the legacy row-based generator.</li>
 *   <li><b>Shape mode</b> — sparse maps keyed by {@link GridCell} and
 *       {@link EquationArm}. Used when built from a {@link PuzzleShape}.</li>
 * </ul>
 */
public class PuzzleGrid {

    public final PuzzleConfig config;

    // ── Fixed-grid mode fields ───────────────────────────────────────────────

    public final int[][] numbers;
    public final Operator[][] horizontalOperators;
    public final Operator[][] verticalOperators;

    // ── Shape mode fields ────────────────────────────────────────────────────

    private PuzzleShape shape;
    private Map<GridCell, Integer> cellValues;
    private Map<EquationArm, List<Operator>> armOperators;

    // ── Construction ──────────────────────────────────────────────────────────

    public PuzzleGrid(PuzzleConfig config) {
        this.config              = config;
        this.numbers             = new int[config.matrixSize][config.matrixSize];
        this.horizontalOperators = new Operator[config.matrixSize][config.equationsPerLine];
        this.verticalOperators   = new Operator[config.matrixSize][config.equationsPerLine];
    }

    public PuzzleGrid(PuzzleConfig config, PuzzleShape shape) {
        this(config);
        this.shape        = shape;
        this.cellValues   = new LinkedHashMap<>();
        this.armOperators = new LinkedHashMap<>();
    }

    // ── Shape mode accessors ─────────────────────────────────────────────────

    public boolean isShapeMode() {
        return shape != null;
    }

    public PuzzleShape shape() {
        return shape;
    }

    public Map<GridCell, Integer> cellValues() {
        return cellValues != null ? Collections.unmodifiableMap(cellValues) : Map.of();
    }

    public void setCellValue(GridCell cell, int value) {
        if (cellValues == null) cellValues = new LinkedHashMap<>();
        cellValues.put(cell, value);
        numbers[cell.row()][cell.col()] = value;
    }

    public int getCellValue(GridCell cell) {
        if (cellValues != null && cellValues.containsKey(cell)) {
            return cellValues.get(cell);
        }
        return numbers[cell.row()][cell.col()];
    }

    public boolean hasCellValue(GridCell cell) {
        return cellValues != null && cellValues.containsKey(cell);
    }

    public void setArmOperators(EquationArm arm, List<Operator> operators) {
        if (armOperators == null) armOperators = new LinkedHashMap<>();
        armOperators.put(arm, List.copyOf(operators));
    }

    public List<Operator> getArmOperators(EquationArm arm) {
        if (armOperators == null) return List.of();
        return armOperators.getOrDefault(arm, List.of());
    }

    public Map<EquationArm, List<Operator>> allArmOperators() {
        return armOperators != null ? Collections.unmodifiableMap(armOperators) : Map.of();
    }

    // ── Verification ──────────────────────────────────────────────────────────

    public boolean verify() {
        if (isShapeMode()) {
            return verifyShape();
        }
        return verifyFixedGrid();
    }

    private boolean verifyFixedGrid() {
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

    private boolean verifyShape() {
        for (EquationArm arm : shape.arms()) {
            List<Operator> ops = getArmOperators(arm);
            List<GridCell> operands = arm.operandCells();

            if (ops.size() != operands.size() - 1) return false;

            int running = getCellValue(operands.get(0));
            for (int i = 0; i < ops.size(); i++) {
                int rightValue = getCellValue(operands.get(i + 1));
                int result = ops.get(i).apply(running, rightValue, config);
                if (result == Integer.MIN_VALUE) return false;
                running = result;
            }
            if (running != getCellValue(arm.resultCell())) return false;
        }
        return true;
    }
}
