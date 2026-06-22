package io.crossmath.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores the difficulty of a generated puzzle on a 0–100 scale.
 *
 * <p>Factors considered:
 * <ul>
 *   <li>Hidden cell ratio (more hidden = harder)</li>
 *   <li>Operator complexity (advanced ops like ^, log, % = harder)</li>
 *   <li>Hidden intersections (deduction nodes hidden = harder)</li>
 *   <li>Hidden operators (guessing operators = harder)</li>
 *   <li>Hidden results (no anchoring result = harder)</li>
 *   <li>Unknowns per equation (more unknowns per eq = harder)</li>
 * </ul>
 */
public class DifficultyScorer {

    private static final double WEIGHT_HIDDEN_RATIO        = 25.0;
    private static final double WEIGHT_OPERATOR_COMPLEXITY = 20.0;
    private static final double WEIGHT_INTERSECTION_HIDDEN = 20.0;
    private static final double WEIGHT_OPERATOR_HIDDEN     = 15.0;
    private static final double WEIGHT_RESULT_HIDDEN       = 10.0;
    private static final double WEIGHT_UNKNOWNS_PER_EQ    = 10.0;

    public record Score(
            double total,
            double hiddenRatio,
            double operatorComplexity,
            double intersectionHidden,
            double operatorHidden,
            double resultHidden,
            double unknownsPerEquation
    ) {
        public String toJson() {
            return String.format(
                    "{\"total\": %.1f, \"hiddenRatio\": %.2f, \"operatorComplexity\": %.2f, " +
                    "\"intersectionHidden\": %.2f, \"operatorHidden\": %.2f, " +
                    "\"resultHidden\": %.2f, \"unknownsPerEquation\": %.2f}",
                    total, hiddenRatio, operatorComplexity, intersectionHidden,
                    operatorHidden, resultHidden, unknownsPerEquation);
        }
    }

    public static Score score(PuzzleGrid grid, EquationMask mask, DifficultyLevel level) {
        double hiddenRatio;
        double opComplexity;
        double intersectionHiddenRatio;
        double opHidden;
        double resultHidden;
        double unknownsPerEq;

        if (grid.isShapeMode()) {
            hiddenRatio = computeHiddenRatioShape(grid, mask);
            opComplexity = computeOperatorComplexityShape(grid);
            intersectionHiddenRatio = computeIntersectionHiddenShape(grid, mask);
            opHidden = level != null ? level.operatorHideChance : 0.0;
            resultHidden = level != null ? level.resultHideChance : 0.0;
            unknownsPerEq = computeUnknownsPerEqShape(grid, mask);
        } else {
            hiddenRatio = computeHiddenRatioFixed(grid, mask);
            opComplexity = computeOperatorComplexityFixed(grid);
            intersectionHiddenRatio = computeIntersectionHiddenFixed(grid, mask);
            opHidden = level != null ? level.operatorHideChance : 0.0;
            resultHidden = level != null ? level.resultHideChance : 0.0;
            unknownsPerEq = computeUnknownsPerEqFixed(grid, mask);
        }

        double total = hiddenRatio * WEIGHT_HIDDEN_RATIO
                     + opComplexity * WEIGHT_OPERATOR_COMPLEXITY
                     + intersectionHiddenRatio * WEIGHT_INTERSECTION_HIDDEN
                     + opHidden * WEIGHT_OPERATOR_HIDDEN
                     + resultHidden * WEIGHT_RESULT_HIDDEN
                     + unknownsPerEq * WEIGHT_UNKNOWNS_PER_EQ;

        return new Score(total, hiddenRatio, opComplexity,
                intersectionHiddenRatio, opHidden, resultHidden, unknownsPerEq);
    }

    private static double computeHiddenRatioShape(PuzzleGrid grid, EquationMask mask) {
        int totalArms = grid.shape().armCount();
        if (totalArms == 0) return 0.0;
        int hiddenCount = mask.hiddenArmSet().size();
        return (double) hiddenCount / totalArms;
    }

    private static double computeHiddenRatioFixed(PuzzleGrid grid, EquationMask mask) {
        int totalEquations = 2 * grid.config.matrixSize * grid.config.equationsPerLine;
        if (totalEquations == 0) return 0.0;
        return (double) mask.hiddenSet().size() / totalEquations;
    }

    private static double computeOperatorComplexityShape(PuzzleGrid grid) {
        int totalOps = 0;
        double complexitySum = 0.0;
        for (EquationArm arm : grid.shape().arms()) {
            for (Operator op : grid.getArmOperators(arm)) {
                complexitySum += operatorWeight(op.symbol());
                totalOps++;
            }
        }
        return totalOps > 0 ? complexitySum / totalOps : 0.0;
    }

    private static double computeOperatorComplexityFixed(PuzzleGrid grid) {
        int totalOps = 0;
        double complexitySum = 0.0;
        for (int row = 0; row < grid.config.matrixSize; row++) {
            for (int eq = 0; eq < grid.config.equationsPerLine; eq++) {
                complexitySum += operatorWeight(grid.horizontalOperators[row][eq].symbol());
                totalOps++;
            }
        }
        for (int col = 0; col < grid.config.matrixSize; col++) {
            for (int eq = 0; eq < grid.config.equationsPerLine; eq++) {
                complexitySum += operatorWeight(grid.verticalOperators[col][eq].symbol());
                totalOps++;
            }
        }
        return totalOps > 0 ? complexitySum / totalOps : 0.0;
    }

    private static double computeIntersectionHiddenShape(PuzzleGrid grid, EquationMask mask) {
        PuzzleShape shape = grid.shape();
        Set<GridCell> hiddenIntersections = new HashSet<>();
        Set<GridCell> allIntersections = shape.intersections();

        int armIndex = 0;
        for (EquationArm arm : shape.arms()) {
            if (!mask.isArmVisible(armIndex)) {
                for (GridCell cell : arm.operandCells()) {
                    if (allIntersections.contains(cell)) {
                        hiddenIntersections.add(cell);
                    }
                }
            }
            armIndex++;
        }

        return allIntersections.isEmpty() ? 0.0
                : (double) hiddenIntersections.size() / allIntersections.size();
    }

    private static double computeIntersectionHiddenFixed(PuzzleGrid grid, EquationMask mask) {
        PuzzleConfig config = grid.config;
        int totalIntersections = config.matrixSize * config.matrixSize;
        int hiddenIntersections = 0;

        for (int row = 0; row < config.matrixSize; row++) {
            for (int col = 0; col < config.matrixSize; col++) {
                boolean allHidden = true;
                for (int eq = 0; eq < config.equationsPerLine; eq++) {
                    if (col >= eq * 2 && col <= eq * 2 + 2) {
                        if (mask.isVisible(EquationId.Axis.HORIZONTAL, row, eq)) {
                            allHidden = false;
                            break;
                        }
                    }
                    if (row >= eq * 2 && row <= eq * 2 + 2) {
                        if (mask.isVisible(EquationId.Axis.VERTICAL, col, eq)) {
                            allHidden = false;
                            break;
                        }
                    }
                }
                if (allHidden) hiddenIntersections++;
            }
        }

        return (double) hiddenIntersections / totalIntersections;
    }

    private static double computeUnknownsPerEqShape(PuzzleGrid grid, EquationMask mask) {
        PuzzleShape shape = grid.shape();
        List<EquationArm> arms = shape.arms();
        Set<GridCell> hiddenCells = new HashSet<>();

        int armIndex = 0;
        for (EquationArm arm : arms) {
            if (!mask.isArmVisible(armIndex)) {
                hiddenCells.addAll(arm.operandCells());
                hiddenCells.add(arm.resultCell());
            }
            armIndex++;
        }

        int totalVisible = 0;
        int totalUnknowns = 0;
        armIndex = 0;
        for (EquationArm arm : arms) {
            if (mask.isArmVisible(armIndex)) {
                totalVisible++;
                for (GridCell cell : arm.allCells()) {
                    if (hiddenCells.contains(cell)) {
                        totalUnknowns++;
                    }
                }
            }
            armIndex++;
        }

        if (totalVisible == 0) return 1.0;
        double avg = (double) totalUnknowns / totalVisible;
        double maxPossible = 3.0;
        return Math.min(1.0, avg / maxPossible);
    }

    private static double computeUnknownsPerEqFixed(PuzzleGrid grid, EquationMask mask) {
        PuzzleConfig config = grid.config;
        int totalVisible = 0;
        int totalUnknowns = 0;

        for (int row = 0; row < config.matrixSize; row++) {
            for (int eq = 0; eq < config.equationsPerLine; eq++) {
                if (mask.isVisible(EquationId.Axis.HORIZONTAL, row, eq)) {
                    totalVisible++;
                    int left = eq * 2;
                    int[] cols = {left, left + 1, left + 2};
                    for (int col : cols) {
                        if (isCellFullyHidden(row, col, mask, config)) {
                            totalUnknowns++;
                        }
                    }
                }
            }
        }
        for (int col = 0; col < config.matrixSize; col++) {
            for (int eq = 0; eq < config.equationsPerLine; eq++) {
                if (mask.isVisible(EquationId.Axis.VERTICAL, col, eq)) {
                    totalVisible++;
                    int top = eq * 2;
                    int[] rows = {top, top + 1, top + 2};
                    for (int row : rows) {
                        if (isCellFullyHidden(row, col, mask, config)) {
                            totalUnknowns++;
                        }
                    }
                }
            }
        }

        if (totalVisible == 0) return 1.0;
        double avg = (double) totalUnknowns / totalVisible;
        double maxPossible = 3.0;
        return Math.min(1.0, avg / maxPossible);
    }

    private static boolean isCellFullyHidden(int row, int col,
                                              EquationMask mask, PuzzleConfig config) {
        for (int eq = 0; eq < config.equationsPerLine; eq++) {
            if (col >= eq * 2 && col <= eq * 2 + 2) {
                if (mask.isVisible(EquationId.Axis.HORIZONTAL, row, eq)) return false;
            }
            if (row >= eq * 2 && row <= eq * 2 + 2) {
                if (mask.isVisible(EquationId.Axis.VERTICAL, col, eq)) return false;
            }
        }
        return true;
    }

    private static double operatorWeight(char symbol) {
        return switch (symbol) {
            case '+' -> 0.1;
            case '-' -> 0.2;
            case '*' -> 0.4;
            case '/' -> 0.5;
            case 'a' -> 0.6;  // avg
            case 'm' -> 0.3;  // min
            case 'M' -> 0.3;  // max
            case '^' -> 0.8;  // exp
            case 'r' -> 0.7;  // sqrt
            case '%' -> 0.7;  // modulo
            case 'L' -> 0.9;  // log
            default  -> 0.5;
        };
    }
}
