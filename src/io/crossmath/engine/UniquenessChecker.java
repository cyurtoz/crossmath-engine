package io.crossmath.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies that a masked puzzle has exactly one valid solution.
 *
 * <p>A puzzle is "uniquely solvable" when the visible equations, together with
 * the revealed cell values, leave only one possible assignment for every
 * hidden (unknown) cell. This is the "ensure single correct answer" rule
 * from the grades.txt specification.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Identify <em>unknown cells</em> &mdash; cells whose value the player
 *       must deduce because every equation they belong to is hidden.</li>
 *   <li>Collect <em>constraining equations</em> &mdash; visible equations that
 *       reference at least one unknown cell.</li>
 *   <li>Initialise a domain {@code [minCellValue..maxCellValue]} for each
 *       unknown cell.</li>
 *   <li>Run arc-consistency constraint propagation to prune domains.</li>
 *   <li>If any domain is empty the puzzle is unsolvable (return false).</li>
 *   <li>If all domains are singletons, the solution is unique (return true).</li>
 *   <li>Otherwise, use depth-first search with backtracking, counting
 *       solutions. Return true if exactly one solution is found.</li>
 * </ol>
 *
 * <p>Supports both fixed-grid and shape modes.
 */
public class UniquenessChecker {

    /** Maximum number of solutions to discover before stopping early. */
    private static final int MAX_SOLUTIONS_TO_COUNT = 2;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the masked puzzle has exactly one valid solution.
     *
     * @param grid     the fully solved puzzle grid
     * @param mask     the equation mask controlling which equations are hidden
     * @param registry the operator registry (used to enumerate operators in
     *                 shape mode where hidden arms have unknown operators)
     * @return {@code true} if there is exactly one valid assignment for all
     *         unknown cells; {@code false} if zero or multiple solutions exist
     */
    public static boolean isUnique(PuzzleGrid grid, EquationMask mask,
                                   OperatorRegistry registry) {
        if (grid.isShapeMode()) {
            return isUniqueShape(grid, mask, registry);
        }
        return isUniqueFixedGrid(grid, mask);
    }

    // ── Fixed-grid mode ──────────────────────────────────────────────────────

    /**
     * A constraint from a visible equation in fixed-grid mode.
     * Represents: numbers[cells[0]] op numbers[cells[1]] = numbers[cells[2]]
     */
    private static final class FixedConstraint {
        final int[][] cells;   // 3 cells: [row,col] each
        final Operator operator;

        FixedConstraint(int[][] cells, Operator operator) {
            this.cells = cells;
            this.operator = operator;
        }
    }

    private static boolean isUniqueFixedGrid(PuzzleGrid grid, EquationMask mask) {
        PuzzleConfig config = grid.config;

        // Step 1: identify unknown cells
        Set<String> unknownKeys = new LinkedHashSet<>();
        for (int row = 0; row < config.matrixSize; row++) {
            for (int col = 0; col < config.matrixSize; col++) {
                if (isCellUnknown(row, col, mask, config)) {
                    unknownKeys.add(row + "," + col);
                }
            }
        }

        if (unknownKeys.isEmpty()) {
            return true; // nothing to solve, trivially unique
        }

        // Step 2: collect constraining visible equations that touch unknown cells
        List<FixedConstraint> constraints = new ArrayList<>();

        // Horizontal equations
        for (int row = 0; row < config.matrixSize; row++) {
            for (int eq = 0; eq < config.equationsPerLine; eq++) {
                if (!mask.isVisible(EquationId.Axis.HORIZONTAL, row, eq)) continue;
                int leftCol = eq * 2;
                int[][] cells = {
                    {row, leftCol}, {row, leftCol + 1}, {row, leftCol + 2}
                };
                boolean touchesUnknown = false;
                for (int[] c : cells) {
                    if (unknownKeys.contains(c[0] + "," + c[1])) {
                        touchesUnknown = true;
                        break;
                    }
                }
                if (touchesUnknown) {
                    constraints.add(new FixedConstraint(cells,
                            grid.horizontalOperators[row][eq]));
                }
            }
        }

        // Vertical equations
        for (int col = 0; col < config.matrixSize; col++) {
            for (int eq = 0; eq < config.equationsPerLine; eq++) {
                if (!mask.isVisible(EquationId.Axis.VERTICAL, col, eq)) continue;
                int topRow = eq * 2;
                int[][] cells = {
                    {topRow, col}, {topRow + 1, col}, {topRow + 2, col}
                };
                boolean touchesUnknown = false;
                for (int[] c : cells) {
                    if (unknownKeys.contains(c[0] + "," + c[1])) {
                        touchesUnknown = true;
                        break;
                    }
                }
                if (touchesUnknown) {
                    constraints.add(new FixedConstraint(cells,
                            grid.verticalOperators[col][eq]));
                }
            }
        }

        // Step 3: build domains for unknown cells
        List<String> unknownList = new ArrayList<>(unknownKeys);
        Map<String, int[]> domains = new HashMap<>();
        for (String key : unknownList) {
            domains.put(key, range(config.minCellValue, config.maxCellValue));
        }

        // Step 4: constraint propagation
        boolean changed = true;
        while (changed) {
            changed = false;
            for (FixedConstraint constraint : constraints) {
                changed |= propagateFixed(constraint, domains, unknownKeys,
                        grid, config);
            }
        }

        // Step 5: check for empty domains
        for (String key : unknownList) {
            if (domains.get(key).length == 0) return false;
        }

        // Step 6: check if all singletons
        boolean allSingleton = true;
        for (String key : unknownList) {
            if (domains.get(key).length > 1) {
                allSingleton = false;
                break;
            }
        }
        if (allSingleton) return true;

        // Step 7: backtracking search counting solutions
        int[] solutionCount = {0};
        Map<String, Integer> assignment = new HashMap<>();
        // Pre-fill known cell values
        for (int row = 0; row < config.matrixSize; row++) {
            for (int col = 0; col < config.matrixSize; col++) {
                String key = row + "," + col;
                if (!unknownKeys.contains(key)) {
                    assignment.put(key, grid.numbers[row][col]);
                }
            }
        }
        backtrackFixed(0, unknownList, domains, constraints, assignment,
                config, solutionCount);
        return solutionCount[0] == 1;
    }

    /**
     * Propagates a single visible fixed-grid constraint to prune domains.
     * The equation is: cell[0] op cell[1] = cell[2].
     * Returns true if any domain was changed.
     */
    private static boolean propagateFixed(FixedConstraint constraint,
                                          Map<String, int[]> domains,
                                          Set<String> unknownKeys,
                                          PuzzleGrid grid,
                                          PuzzleConfig config) {
        String key0 = constraint.cells[0][0] + "," + constraint.cells[0][1];
        String key1 = constraint.cells[1][0] + "," + constraint.cells[1][1];
        String key2 = constraint.cells[2][0] + "," + constraint.cells[2][1];
        Operator op = constraint.operator;

        boolean isUnknown0 = unknownKeys.contains(key0);
        boolean isUnknown1 = unknownKeys.contains(key1);
        boolean isUnknown2 = unknownKeys.contains(key2);

        int[] dom0 = isUnknown0 ? domains.get(key0) : new int[]{grid.numbers[constraint.cells[0][0]][constraint.cells[0][1]]};
        int[] dom1 = isUnknown1 ? domains.get(key1) : new int[]{grid.numbers[constraint.cells[1][0]][constraint.cells[1][1]]};
        int[] dom2 = isUnknown2 ? domains.get(key2) : new int[]{grid.numbers[constraint.cells[2][0]][constraint.cells[2][1]]};

        // For each unknown cell, compute its feasible values given the other domains
        boolean changed = false;

        if (isUnknown0) {
            Set<Integer> feasible = new HashSet<>();
            for (int v0 : dom0) {
                for (int v1 : dom1) {
                    int result = op.apply(v0, v1, config);
                    if (result != Integer.MIN_VALUE && contains(dom2, result)) {
                        feasible.add(v0);
                        break;
                    }
                }
            }
            int[] newDom = toSortedArray(feasible);
            if (newDom.length < dom0.length) {
                domains.put(key0, newDom);
                changed = true;
            }
        }

        if (isUnknown1) {
            Set<Integer> feasible = new HashSet<>();
            for (int v1 : dom1) {
                for (int v0 : dom0) {
                    int result = op.apply(v0, v1, config);
                    if (result != Integer.MIN_VALUE && contains(dom2, result)) {
                        feasible.add(v1);
                        break;
                    }
                }
            }
            int[] newDom = toSortedArray(feasible);
            if (newDom.length < dom1.length) {
                domains.put(key1, newDom);
                changed = true;
            }
        }

        if (isUnknown2) {
            Set<Integer> feasible = new HashSet<>();
            for (int v0 : dom0) {
                for (int v1 : dom1) {
                    int result = op.apply(v0, v1, config);
                    if (result != Integer.MIN_VALUE && contains(dom2, result)) {
                        feasible.add(result);
                    }
                }
            }
            int[] newDom = toSortedArray(feasible);
            if (newDom.length < dom2.length) {
                domains.put(key2, newDom);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Depth-first backtracking search for fixed-grid mode.
     * Stops as soon as more than one solution is found.
     */
    private static void backtrackFixed(int idx, List<String> unknownList,
                                       Map<String, int[]> domains,
                                       List<FixedConstraint> constraints,
                                       Map<String, Integer> assignment,
                                       PuzzleConfig config,
                                       int[] solutionCount) {
        if (solutionCount[0] >= MAX_SOLUTIONS_TO_COUNT) return;

        if (idx == unknownList.size()) {
            // All unknowns assigned; verify all constraints
            if (verifyAllFixed(constraints, assignment, config)) {
                solutionCount[0]++;
            }
            return;
        }

        String key = unknownList.get(idx);
        int[] domain = domains.get(key);

        for (int value : domain) {
            assignment.put(key, value);

            // Forward check: verify constraints that are fully assigned
            if (partialCheckFixed(constraints, assignment, config, key)) {
                backtrackFixed(idx + 1, unknownList, domains, constraints,
                        assignment, config, solutionCount);
            }

            if (solutionCount[0] >= MAX_SOLUTIONS_TO_COUNT) {
                assignment.remove(key);
                return;
            }
        }
        assignment.remove(key);
    }

    /** Checks all constraints that are fully assigned. */
    private static boolean verifyAllFixed(List<FixedConstraint> constraints,
                                          Map<String, Integer> assignment,
                                          PuzzleConfig config) {
        for (FixedConstraint c : constraints) {
            String k0 = c.cells[0][0] + "," + c.cells[0][1];
            String k1 = c.cells[1][0] + "," + c.cells[1][1];
            String k2 = c.cells[2][0] + "," + c.cells[2][1];
            int v0 = assignment.get(k0);
            int v1 = assignment.get(k1);
            int v2 = assignment.get(k2);
            int result = c.operator.apply(v0, v1, config);
            if (result != v2) return false;
        }
        return true;
    }

    /**
     * Partial forward check: for every constraint that involves the given key
     * and where all cells are assigned, verify the equation holds.
     */
    private static boolean partialCheckFixed(List<FixedConstraint> constraints,
                                             Map<String, Integer> assignment,
                                             PuzzleConfig config,
                                             String justAssigned) {
        for (FixedConstraint c : constraints) {
            String k0 = c.cells[0][0] + "," + c.cells[0][1];
            String k1 = c.cells[1][0] + "," + c.cells[1][1];
            String k2 = c.cells[2][0] + "," + c.cells[2][1];

            // Only check if this constraint involves the just-assigned key
            if (!k0.equals(justAssigned) && !k1.equals(justAssigned)
                    && !k2.equals(justAssigned)) {
                continue;
            }

            // Only check if all three cells are assigned
            if (!assignment.containsKey(k0) || !assignment.containsKey(k1)
                    || !assignment.containsKey(k2)) {
                continue;
            }

            int v0 = assignment.get(k0);
            int v1 = assignment.get(k1);
            int v2 = assignment.get(k2);
            int result = c.operator.apply(v0, v1, config);
            if (result != v2) return false;
        }
        return true;
    }

    // ── Shape mode ───────────────────────────────────────────────────────────

    /**
     * A constraint from a visible arm in shape mode.
     * Represents: operands[0] op[0] operands[1] op[1] ... = resultCell
     */
    private static final class ShapeConstraint {
        final List<GridCell> operandCells;
        final GridCell resultCell;
        final List<Operator> operators;

        ShapeConstraint(List<GridCell> operandCells, GridCell resultCell,
                        List<Operator> operators) {
            this.operandCells = operandCells;
            this.resultCell = resultCell;
            this.operators = operators;
        }
    }

    private static boolean isUniqueShape(PuzzleGrid grid, EquationMask mask,
                                         OperatorRegistry registry) {
        PuzzleConfig config = grid.config;
        PuzzleShape shape = grid.shape();

        // Step 1: identify unknown cells
        // A cell is unknown if every arm it belongs to is hidden
        Set<GridCell> unknownCells = new LinkedHashSet<>();
        for (GridCell cell : shape.allClaimedCells()) {
            boolean allHidden = true;
            int armIndex = 0;
            for (EquationArm arm : shape.arms()) {
                if (arm.allCells().contains(cell) && mask.isArmVisible(armIndex)) {
                    allHidden = false;
                    break;
                }
                armIndex++;
            }
            if (allHidden) {
                unknownCells.add(cell);
            }
        }

        if (unknownCells.isEmpty()) {
            return true;
        }

        // Step 2: collect constraining visible arms that touch unknown cells
        List<ShapeConstraint> constraints = new ArrayList<>();
        int armIndex = 0;
        for (EquationArm arm : shape.arms()) {
            if (!mask.isArmVisible(armIndex)) {
                armIndex++;
                continue;
            }
            boolean touchesUnknown = false;
            for (GridCell cell : arm.allCells()) {
                if (unknownCells.contains(cell)) {
                    touchesUnknown = true;
                    break;
                }
            }
            if (touchesUnknown) {
                constraints.add(new ShapeConstraint(
                        arm.operandCells(), arm.resultCell(),
                        grid.getArmOperators(arm)));
            }
            armIndex++;
        }

        // Step 3: build domains
        List<GridCell> unknownList = new ArrayList<>(unknownCells);
        Map<GridCell, int[]> domains = new HashMap<>();
        for (GridCell cell : unknownList) {
            domains.put(cell, range(config.minCellValue, config.maxCellValue));
        }

        // Step 4: constraint propagation
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ShapeConstraint constraint : constraints) {
                changed |= propagateShape(constraint, domains, unknownCells,
                        grid, config);
            }
        }

        // Step 5: check for empty domains
        for (GridCell cell : unknownList) {
            if (domains.get(cell).length == 0) return false;
        }

        // Step 6: check if all singletons
        boolean allSingleton = true;
        for (GridCell cell : unknownList) {
            if (domains.get(cell).length > 1) {
                allSingleton = false;
                break;
            }
        }
        if (allSingleton) return true;

        // Step 7: backtracking search
        int[] solutionCount = {0};
        Map<GridCell, Integer> assignment = new HashMap<>();
        // Pre-fill known cell values
        for (GridCell cell : shape.allClaimedCells()) {
            if (!unknownCells.contains(cell)) {
                assignment.put(cell, grid.getCellValue(cell));
            }
        }
        backtrackShape(0, unknownList, domains, constraints, assignment,
                config, solutionCount);
        return solutionCount[0] == 1;
    }

    /**
     * Propagates a single visible shape constraint to prune domains.
     * The equation chain is: operands[0] op[0] operands[1] op[1] ... = resultCell.
     *
     * <p>For shape constraints with more than 2 operands, the propagation is
     * more complex. We enumerate feasible values for each unknown cell by
     * trying all combinations of the other cells' domain values.
     */
    private static boolean propagateShape(ShapeConstraint constraint,
                                          Map<GridCell, int[]> domains,
                                          Set<GridCell> unknownCells,
                                          PuzzleGrid grid,
                                          PuzzleConfig config) {
        // Collect all cells in this constraint
        List<GridCell> allCells = new ArrayList<>(constraint.operandCells);
        allCells.add(constraint.resultCell);

        // Find which cells are unknown
        List<Integer> unknownIndices = new ArrayList<>();
        for (int i = 0; i < allCells.size(); i++) {
            if (unknownCells.contains(allCells.get(i))) {
                unknownIndices.add(i);
            }
        }

        if (unknownIndices.isEmpty()) return false;

        // For efficiency, if there's only one unknown cell, solve directly
        if (unknownIndices.size() == 1) {
            return propagateShapeSingleUnknown(constraint, allCells,
                    unknownIndices.get(0), domains, unknownCells, grid, config);
        }

        // For multiple unknowns, do pairwise pruning: for each unknown,
        // check which values are feasible given other domains
        boolean changed = false;
        for (int unknownIdx : unknownIndices) {
            GridCell unknownCell = allCells.get(unknownIdx);
            int[] currentDom = domains.get(unknownCell);
            Set<Integer> feasible = new HashSet<>();

            for (int candidateVal : currentDom) {
                if (isShapeValueFeasible(constraint, allCells, unknownIdx,
                        candidateVal, unknownIndices, domains, unknownCells,
                        grid, config)) {
                    feasible.add(candidateVal);
                }
            }

            int[] newDom = toSortedArray(feasible);
            if (newDom.length < currentDom.length) {
                domains.put(unknownCell, newDom);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Optimised propagation when only one cell in the constraint is unknown.
     */
    private static boolean propagateShapeSingleUnknown(
            ShapeConstraint constraint, List<GridCell> allCells,
            int unknownIdx, Map<GridCell, int[]> domains,
            Set<GridCell> unknownCells, PuzzleGrid grid,
            PuzzleConfig config) {

        GridCell unknownCell = allCells.get(unknownIdx);
        int[] currentDom = domains.get(unknownCell);
        int resultIdx = constraint.operandCells.size(); // last cell is result

        // Get known values for all other cells
        int[] knownValues = new int[allCells.size()];
        for (int i = 0; i < allCells.size(); i++) {
            if (i == unknownIdx) continue;
            GridCell cell = allCells.get(i);
            if (unknownCells.contains(cell)) {
                knownValues[i] = domains.get(cell)[0]; // should be singleton if we got here
            } else {
                knownValues[i] = grid.getCellValue(cell);
            }
        }

        Set<Integer> feasible = new HashSet<>();
        for (int candidateVal : currentDom) {
            knownValues[unknownIdx] = candidateVal;
            if (evaluateShapeChain(constraint, knownValues, config)) {
                feasible.add(candidateVal);
            }
        }

        int[] newDom = toSortedArray(feasible);
        if (newDom.length < currentDom.length) {
            domains.put(unknownCell, newDom);
            return true;
        }
        return false;
    }

    /**
     * Checks if a candidate value for one unknown cell can be part of a
     * valid assignment by checking if any combination of other unknown cells'
     * domain values satisfies the constraint.
     */
    private static boolean isShapeValueFeasible(
            ShapeConstraint constraint, List<GridCell> allCells,
            int targetIdx, int targetValue,
            List<Integer> allUnknownIndices,
            Map<GridCell, int[]> domains, Set<GridCell> unknownCells,
            PuzzleGrid grid, PuzzleConfig config) {

        // Build list of other unknown indices to enumerate
        List<Integer> otherUnknowns = new ArrayList<>();
        for (int idx : allUnknownIndices) {
            if (idx != targetIdx) otherUnknowns.add(idx);
        }

        int[] values = new int[allCells.size()];
        for (int i = 0; i < allCells.size(); i++) {
            if (i == targetIdx) {
                values[i] = targetValue;
            } else if (!unknownCells.contains(allCells.get(i))) {
                values[i] = grid.getCellValue(allCells.get(i));
            }
        }

        return enumerateAndCheck(constraint, values, otherUnknowns, 0,
                allCells, domains, config);
    }

    /**
     * Recursively enumerates domain values for other unknowns and checks
     * if the constraint can be satisfied.
     */
    private static boolean enumerateAndCheck(
            ShapeConstraint constraint, int[] values,
            List<Integer> otherUnknowns, int depth,
            List<GridCell> allCells, Map<GridCell, int[]> domains,
            PuzzleConfig config) {

        if (depth == otherUnknowns.size()) {
            return evaluateShapeChain(constraint, values, config);
        }

        int idx = otherUnknowns.get(depth);
        GridCell cell = allCells.get(idx);
        int[] dom = domains.get(cell);

        for (int val : dom) {
            values[idx] = val;
            if (enumerateAndCheck(constraint, values, otherUnknowns,
                    depth + 1, allCells, domains, config)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the shape chain equation:
     * values[0] op[0] values[1] op[1] ... == values[resultIdx]
     */
    private static boolean evaluateShapeChain(ShapeConstraint constraint,
                                              int[] values,
                                              PuzzleConfig config) {
        int operandCount = constraint.operandCells.size();
        int running = values[0];
        for (int i = 0; i < constraint.operators.size(); i++) {
            int rightValue = values[i + 1];
            int result = constraint.operators.get(i).apply(running, rightValue, config);
            if (result == Integer.MIN_VALUE) return false;
            running = result;
        }
        return running == values[operandCount]; // compare with result cell
    }

    /**
     * Depth-first backtracking search for shape mode.
     */
    private static void backtrackShape(int idx, List<GridCell> unknownList,
                                       Map<GridCell, int[]> domains,
                                       List<ShapeConstraint> constraints,
                                       Map<GridCell, Integer> assignment,
                                       PuzzleConfig config,
                                       int[] solutionCount) {
        if (solutionCount[0] >= MAX_SOLUTIONS_TO_COUNT) return;

        if (idx == unknownList.size()) {
            if (verifyAllShape(constraints, assignment, config)) {
                solutionCount[0]++;
            }
            return;
        }

        GridCell cell = unknownList.get(idx);
        int[] domain = domains.get(cell);

        for (int value : domain) {
            assignment.put(cell, value);

            if (partialCheckShape(constraints, assignment, config, cell)) {
                backtrackShape(idx + 1, unknownList, domains, constraints,
                        assignment, config, solutionCount);
            }

            if (solutionCount[0] >= MAX_SOLUTIONS_TO_COUNT) {
                assignment.remove(cell);
                return;
            }
        }
        assignment.remove(cell);
    }

    /** Verifies all shape constraints are satisfied. */
    private static boolean verifyAllShape(List<ShapeConstraint> constraints,
                                          Map<GridCell, Integer> assignment,
                                          PuzzleConfig config) {
        for (ShapeConstraint c : constraints) {
            if (!evalShapeConstraint(c, assignment, config)) return false;
        }
        return true;
    }

    /**
     * Forward check for shape mode: only checks constraints involving the
     * just-assigned cell where all cells are assigned.
     */
    private static boolean partialCheckShape(List<ShapeConstraint> constraints,
                                             Map<GridCell, Integer> assignment,
                                             PuzzleConfig config,
                                             GridCell justAssigned) {
        for (ShapeConstraint c : constraints) {
            boolean involvesCell = false;
            boolean allAssigned = true;

            for (GridCell cell : c.operandCells) {
                if (cell.equals(justAssigned)) involvesCell = true;
                if (!assignment.containsKey(cell)) {
                    allAssigned = false;
                    break;
                }
            }
            if (allAssigned && !assignment.containsKey(c.resultCell)) {
                allAssigned = false;
            }
            if (c.resultCell.equals(justAssigned)) involvesCell = true;

            if (!involvesCell || !allAssigned) continue;

            if (!evalShapeConstraint(c, assignment, config)) return false;
        }
        return true;
    }

    /** Evaluates a shape constraint against an assignment map. */
    private static boolean evalShapeConstraint(ShapeConstraint c,
                                               Map<GridCell, Integer> assignment,
                                               PuzzleConfig config) {
        int running = assignment.get(c.operandCells.get(0));
        for (int i = 0; i < c.operators.size(); i++) {
            int rightValue = assignment.get(c.operandCells.get(i + 1));
            int result = c.operators.get(i).apply(running, rightValue, config);
            if (result == Integer.MIN_VALUE) return false;
            running = result;
        }
        return running == assignment.get(c.resultCell);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Determines if a cell in fixed-grid mode is unknown (all its equations
     * are hidden).
     */
    private static boolean isCellUnknown(int row, int col,
                                         EquationMask mask,
                                         PuzzleConfig config) {
        for (int eq = 0; eq < config.equationsPerLine; eq++) {
            if (col >= eq * 2 && col <= eq * 2 + 2) {
                if (mask.isVisible(EquationId.Axis.HORIZONTAL, row, eq)) {
                    return false;
                }
            }
            if (row >= eq * 2 && row <= eq * 2 + 2) {
                if (mask.isVisible(EquationId.Axis.VERTICAL, col, eq)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Creates an inclusive range array [min..max]. */
    private static int[] range(int min, int max) {
        if (max < min) return new int[0];
        int[] arr = new int[max - min + 1];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = min + i;
        }
        return arr;
    }

    /** Checks if a sorted int array contains a value. */
    private static boolean contains(int[] sorted, int value) {
        // Binary search for sorted arrays, linear scan for small arrays
        if (sorted.length <= 16) {
            for (int v : sorted) {
                if (v == value) return true;
                if (v > value) return false;
            }
            return false;
        }
        int lo = 0, hi = sorted.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] == value) return true;
            if (sorted[mid] < value) lo = mid + 1;
            else hi = mid - 1;
        }
        return false;
    }

    /** Converts a Set<Integer> to a sorted int[]. */
    private static int[] toSortedArray(Set<Integer> set) {
        int[] arr = new int[set.size()];
        int i = 0;
        for (int v : set) arr[i++] = v;
        java.util.Arrays.sort(arr);
        return arr;
    }
}
