package io.crossmath.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates fully solved CrossMath puzzle grids using shape-based generation.
 *
 * <h2>Algorithm</h2>
 *
 * <h3>Step 1 — Generate shape</h3>
 * {@link ShapeGenerator} grows an asymmetric shape via BFS from a random
 * starting cell. Each arm is a 3-cell equation ({@code A op B = C}).
 *
 * <h3>Step 2 - Seed root cells</h3>
 * Non-result entries from {@link PuzzleShape#intersections()} receive values
 * from the {@link BracketedPicker}. In sparse shapes this set includes both
 * true crossings and promoted branch anchors, so later arms have more roots
 * to grow from.
 *
 * <h3>Step 3 — Fill arms in dependency order</h3>
 * Arms are topologically sorted: an arm that uses another arm's result cell
 * as an operand is processed after it. Each arm is filled independently by
 * choosing an operator (usage-biased) and computing/assigning values for
 * free operands and the result cell.
 */
public class CrossMathGenerator {

    private final PuzzleConfig     config;
    private final OperatorRegistry registry;
    private final Random           random;
    private final BracketedPicker  picker;
    private final ShapeGenerator   shapeGenerator;

    public CrossMathGenerator(PuzzleConfig config, OperatorRegistry registry, Random random) {
        this.config         = config;
        this.registry       = registry;
        this.random         = random;
        this.picker         = new BracketedPicker(
            config.minCellValue, config.maxSeedValue, config.numBrackets, random);
        this.shapeGenerator = new ShapeGenerator(config, random);
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public PuzzleGrid generate() {
        // Fail fast when the operator floor demands more arms than the target
        // shape size can ever supply.
        validateUsageFeasibility();
        int minimumArmsRequired = minimumArmsRequired();

        for (int attempt = 1; attempt <= config.maxGenerationAttempts; attempt++) {
            PuzzleShape shape = shapeGenerator.generate();

            // Small shapes cannot satisfy the operator-usage floor, so skip
            // them before spending time on numeric filling.
            if (shape.armCount() < minimumArmsRequired) {
                if (attempt % 200 == 0) {
                    System.out.printf(
                        "[Generator] Attempt %d - shape too small: %d arms (need >= %d)%n",
                        attempt, shape.armCount(), minimumArmsRequired);
                }
                continue;
            }

            PuzzleGrid grid = tryFillShape(shape);
            if (grid == null) continue;

            OperatorUsageReport usage = countOperatorUsage(grid);
            if (!usage.meetsMinimum(config.minUsagePerOperator)) {
                if (attempt % 200 == 0) {
                    System.out.printf(
                        "[Generator] Attempt %d — usage below minimum: %s%n",
                        attempt, usage.summary());
                }
                continue;
            }

            System.out.printf("[Generator] Solved on attempt %d (%d arms). Usage: %s%n",
                attempt, shape.armCount(), usage.summary());
            assertVerified(grid);
            return grid;
        }
        throw new IllegalStateException(
            "Failed after " + config.maxGenerationAttempts + " attempts. " +
            "Try a different seed, relax operator constraints, " +
            "or lower minUsagePerOperator.");
    }

    // ── Shape filling ───────────────────────────────────────────────────────

    private PuzzleGrid tryFillShape(PuzzleShape shape) {
        PuzzleGrid              grid        = new PuzzleGrid(config, shape);
        Map<Character, Integer> usageCounts = initialUsageCounts();

        // Step 2: Seed root cells that are not themselves produced by an arm.
        Set<GridCell> resultCells = new HashSet<>();
        for (EquationArm arm : shape.arms()) {
            resultCells.add(arm.resultCell());
        }
        for (GridCell cell : shape.intersections()) {
            if (!resultCells.contains(cell)) {
                grid.setValue(cell, picker.next());
            }
        }

        // Step 3: Topological sort and fill
        List<EquationArm> sorted = topologicalSort(shape);
        for (EquationArm arm : sorted) {
            if (!fillArm(grid, arm, usageCounts)) {
                return null;
            }
        }

        return grid;
    }

    // ── Topological sort ────────────────────────────────────────────────────

    /**
     * Sorts arms so that if arm A uses arm B's result cell as an operand,
     * B comes before A. Uses Kahn's algorithm.
     */
    private List<EquationArm> topologicalSort(PuzzleShape shape) {
        List<EquationArm> arms = shape.arms();
        int n = arms.size();

        // Map: result cell → arm index
        Map<GridCell, Integer> resultToArm = new HashMap<>();
        for (int i = 0; i < n; i++) {
            resultToArm.put(arms.get(i).resultCell(), i);
        }

        // Build dependency adjacency: deps[i] = set of arms that i depends on
        int[] inDegree = new int[n];
        List<List<Integer>> dependents = new ArrayList<>();
        for (int i = 0; i < n; i++) dependents.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            for (GridCell operand : arms.get(i).operandCells()) {
                Integer dep = resultToArm.get(operand);
                if (dep != null && dep != i) {
                    dependents.get(dep).add(i);
                    inDegree[i]++;
                }
            }
        }

        // Kahn's algorithm
        List<Integer> order = new ArrayList<>();
        List<Integer> ready = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (inDegree[i] == 0) ready.add(i);
        }
        Collections.shuffle(ready, random);

        while (!ready.isEmpty()) {
            int idx = ready.remove(ready.size() - 1);
            order.add(idx);
            for (int dep : dependents.get(idx)) {
                if (--inDegree[dep] == 0) ready.add(dep);
            }
        }

        // If cycle detected (shouldn't happen), fall back to original order
        if (order.size() < n) {
            for (int i = 0; i < n; i++) {
                if (!order.contains(i)) order.add(i);
            }
        }

        List<EquationArm> sorted = new ArrayList<>();
        for (int idx : order) sorted.add(arms.get(idx));
        return sorted;
    }

    // ── Arm filling ─────────────────────────────────────────────────────────

    /**
     * Fills a single arm's operator and free cell values.
     * The arm's operand cells may already have values (seeded root cells or
     * results from previously filled arms). The result cell is always free.
     */
    private boolean fillArm(PuzzleGrid grid, EquationArm arm,
                             Map<Character, Integer> usageCounts) {
        GridCell cellA = arm.operandCells().get(0);
        GridCell cellB = arm.operandCells().get(1);
        GridCell cellR = arm.resultCell();

        boolean aKnown = grid.hasValue(cellA);
        boolean bKnown = grid.hasValue(cellB);
        int va = aKnown ? grid.getValue(cellA) : Integer.MIN_VALUE;
        int vb = bKnown ? grid.getValue(cellB) : Integer.MIN_VALUE;

        // Result is always unknown at this point (topological order guarantees it)

        if (aKnown && bKnown) {
            return fillBothKnown(grid, arm, va, vb, cellR, usageCounts);
        } else if (aKnown) {
            return fillLeftKnown(grid, arm, va, cellB, cellR, usageCounts);
        } else if (bKnown) {
            return fillRightKnown(grid, arm, vb, cellA, cellR, usageCounts);
        } else {
            return fillBothFree(grid, arm, cellA, cellB, cellR, usageCounts);
        }
    }

    /** Both operands known → find operator, compute result. */
    private boolean fillBothKnown(PuzzleGrid grid, EquationArm arm,
                                   int va, int vb, GridCell cellR,
                                   Map<Character, Integer> usageCounts) {
        for (Operator op : operatorsByAscendingUsage(usageCounts)) {
            int result = op.apply(va, vb, config);
            if (result != Integer.MIN_VALUE) {
                grid.setValue(cellR, result);
                arm.setOperator(op);
                usageCounts.merge(op.symbol(), 1, Integer::sum);
                return true;
            }
        }
        return false;
    }

    /** Left operand known, right free → pick operator + right operand. */
    private boolean fillLeftKnown(PuzzleGrid grid, EquationArm arm,
                                   int va, GridCell cellB, GridCell cellR,
                                   Map<Character, Integer> usageCounts) {
        for (Operator op : operatorsByAscendingUsage(usageCounts)) {
            List<Integer> rights = op.validRightOperands(va, config, random);
            if (!rights.isEmpty()) {
                int vb = rights.get(0);
                int result = op.apply(va, vb, config);
                if (result != Integer.MIN_VALUE) {
                    grid.setValue(cellB, vb);
                    grid.setValue(cellR, result);
                    arm.setOperator(op);
                    usageCounts.merge(op.symbol(), 1, Integer::sum);
                    return true;
                }
            }
        }
        return false;
    }

    /** Right operand known, left free → pick operator + left operand. */
    private boolean fillRightKnown(PuzzleGrid grid, EquationArm arm,
                                    int vb, GridCell cellA, GridCell cellR,
                                    Map<Character, Integer> usageCounts) {
        for (Operator op : operatorsByAscendingUsage(usageCounts)) {
            List<Integer> lefts = findValidLeftOperands(op, vb);
            if (!lefts.isEmpty()) {
                int va = lefts.get(0);
                int result = op.apply(va, vb, config);
                if (result != Integer.MIN_VALUE) {
                    grid.setValue(cellA, va);
                    grid.setValue(cellR, result);
                    arm.setOperator(op);
                    usageCounts.merge(op.symbol(), 1, Integer::sum);
                    return true;
                }
            }
        }
        return false;
    }

    /** Both operands free → pick operator + both operands. */
    private boolean fillBothFree(PuzzleGrid grid, EquationArm arm,
                                   GridCell cellA, GridCell cellB, GridCell cellR,
                                   Map<Character, Integer> usageCounts) {
        for (Operator op : operatorsByAscendingUsage(usageCounts)) {
            // Sparse shapes can tolerate a wider introduced-value range, so
            // try a shuffled seedable pool instead of a single picker draw.
            for (int va : candidateFreeOperands()) {
                List<Integer> rights = op.validRightOperands(va, config, random);
                if (!rights.isEmpty()) {
                    int vb = rights.get(0);
                    int result = op.apply(va, vb, config);
                    if (result != Integer.MIN_VALUE) {
                        grid.setValue(cellA, va);
                        grid.setValue(cellB, vb);
                        grid.setValue(cellR, result);
                        arm.setOperator(op);
                        usageCounts.merge(op.symbol(), 1, Integer::sum);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ── Left-operand enumeration ────────────────────────────────────────────

    /**
     * Finds all valid left operand values for a given operator and fixed
     * right operand. Enumerates [minCellValue, maxCellValue] and keeps
     * those where {@code op.apply(left, rightOperand)} is valid.
     */
    private List<Integer> findValidLeftOperands(Operator op, int rightOperand) {
        List<Integer> valid = new ArrayList<>();
        for (int left = config.minCellValue; left <= config.maxCellValue; left++) {
            if (op.apply(left, rightOperand, config) != Integer.MIN_VALUE) {
                valid.add(left);
            }
        }
        Collections.shuffle(valid, random);
        return valid;
    }

    // ── Operator ordering ───────────────────────────────────────────────────

    private List<Operator> operatorsByAscendingUsage(Map<Character, Integer> usageCounts) {
        List<Operator> sorted = registry.all();
        Collections.shuffle(sorted, random);
        sorted.sort(Comparator
            .comparingInt((Operator op) -> operatorPriority(op, usageCounts))
            .thenComparingInt(op -> usageCounts.getOrDefault(op.symbol(), 0)));
        return sorted;
    }

    private int operatorPriority(Operator op, Map<Character, Integer> usageCounts) {
        // Keep under-used operators at the front until they reach the minimum.
        if (usageCounts.getOrDefault(op.symbol(), 0) < config.minUsagePerOperator) {
            return 0;
        }
        return 1;
    }

    private List<Integer> candidateFreeOperands() {
        List<Integer> candidates = new ArrayList<>();
        // Sparse-shape roots and fully free operands now use maxSeedValue
        // rather than the older dense-grid chain-safe cap.
        for (int value = config.minCellValue; value <= config.maxSeedValue; value++) {
            candidates.add(value);
        }
        Collections.shuffle(candidates, random);
        return candidates;
    }

    // ── Usage counting ──────────────────────────────────────────────────────

    private Map<Character, Integer> initialUsageCounts() {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (Operator op : registry.all()) {
            counts.put(op.symbol(), 0);
        }
        return counts;
    }

    private OperatorUsageReport countOperatorUsage(PuzzleGrid grid) {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (Operator op : registry.all()) counts.put(op.symbol(), 0);
        for (EquationArm arm : grid.shape.arms()) {
            if (arm.operator() != null) {
                counts.merge(arm.operator().symbol(), 1, Integer::sum);
            }
        }
        return new OperatorUsageReport(counts);
    }

    private record OperatorUsageReport(Map<Character, Integer> countBySymbol) {
        boolean meetsMinimum(int minimumCount) {
            if (minimumCount <= 0) return true;
            return countBySymbol.values().stream().allMatch(c -> c >= minimumCount);
        }
        String summary() {
            StringBuilder sb = new StringBuilder("{");
            countBySymbol.forEach((sym, cnt) -> sb.append(sym).append("=").append(cnt).append(", "));
            if (sb.length() > 1) sb.setLength(sb.length() - 2);
            return sb.append("}").toString();
        }
    }

    // ── Verification helper ─────────────────────────────────────────────────

    private static void assertVerified(PuzzleGrid grid) {
        if (!grid.verify()) {
            throw new IllegalStateException("BUG: grid failed verification.");
        }
    }

    private void validateUsageFeasibility() {
        // Reject impossible operator quotas before burning generation attempts.
        int requiredArms = minimumArmsRequiredByUsage();
        if (requiredArms > config.targetEquationCount) {
            throw new IllegalArgumentException(
                "minUsagePerOperator=" + config.minUsagePerOperator +
                " with " + registry.size() + " operators requires at least " +
                requiredArms + " arms, but targetEquationCount=" +
                config.targetEquationCount + ".");
        }
    }

    private int minimumArmsRequired() {
        return Math.max(2, minimumArmsRequiredByUsage());
    }

    private int minimumArmsRequiredByUsage() {
        return Math.max(0, config.minUsagePerOperator) * registry.size();
    }
}
