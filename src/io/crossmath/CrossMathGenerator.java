package io.crossmath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates fully solved CrossMath puzzle grids.
 *
 * <h2>Algorithm</h2>
 *
 * <h3>Phase 1 — Fill free rows</h3>
 * Free rows (0, 1, 3, …) choose their own values.  The generator picks values
 * using a {@link BracketedPicker} that cycles through equal segments of the
 * valid range, ensuring variety.  Operators are tried in ascending usage order
 * so underused operators get first pick.
 *
 * <h3>Phase 2 — Derive constrained rows</h3>
 * Derived rows (2, 4, …) are computed from two source rows via a backtracking
 * search that prunes bad branches early at result columns.
 *
 * <h3>Seeding — guaranteeing operator coverage in derived rows</h3>
 * Source row pairs are seeded so each registered operator gets at least one
 * compatible (top, bottom) column pair that it can process in {@code deriveRow}.
 * Free columns (col 0, 1, 3, …) are assigned to operators round-robin; each
 * operator provides a valid bottom value for the assigned column via
 * {@link Operator#validRightOperands}.  This prevents add from monopolising
 * all derived-row columns.
 */
public class CrossMathGenerator {

    private static final int DERIVE_ROW_VARIETY_RETRIES = 5;
    private static final int MAX_ROW_FILL_TRIES         = 500;

    private final PuzzleConfig     config;
    private final OperatorRegistry registry;
    private final Random           random;
    private final BracketedPicker  picker;

    // ── Construction ──────────────────────────────────────────────────────────

    public CrossMathGenerator(PuzzleConfig config, OperatorRegistry registry, Random random) {
        this.config  = config;
        this.registry = registry;
        this.random  = random;
        this.picker  = new BracketedPicker(
            config.minCellValue, config.maxChainSafeOperand, config.numBrackets, random);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public PuzzleGrid generate() {
        for (int attempt = 1; attempt <= config.maxGenerationAttempts; attempt++) {
            PuzzleGrid candidate = tryBuildGrid();
            if (candidate == null) {
                continue;
            }
            OperatorUsageReport usage = countOperatorUsage(candidate);
            if (!usage.meetsMinimum(config.minUsagePerOperator)) {
                System.out.printf(
                    "[Generator] Attempt %d discarded — usage below minimum: %s%n",
                    attempt, usage.summary());
                continue;
            }
            System.out.printf("[Generator] Solved on attempt %d. Usage: %s%n",
                attempt, usage.summary());
            assertVerified(candidate);
            return candidate;
        }
        throw new IllegalStateException(
            "Failed after " + config.maxGenerationAttempts + " attempts. " +
            "Try a different seed, relax operator constraints, " +
            "or lower minUsagePerOperator.");
    }

    // ── Top-level attempt ─────────────────────────────────────────────────────

    private PuzzleGrid tryBuildGrid() {
        PuzzleGrid              grid        = new PuzzleGrid(config);
        Map<Character, Integer> usageCounts = initialUsageCounts();

        int[] topSeeds    = new int[config.matrixSize];
        int[] bottomSeeds = new int[config.matrixSize];
        seedCompatiblePairs(topSeeds, bottomSeeds, initialUsageCounts());

        if (!fillRow(grid, 0, usageCounts, topSeeds))    return null;
        if (!fillRow(grid, 1, usageCounts, bottomSeeds)) return null;

        for (int equationIndex = 0; equationIndex < config.equationsPerLine; equationIndex++) {
            if (!deriveRow(grid, equationIndex, usageCounts)) return null;

            boolean moreRowsFollow = equationIndex < config.equationsPerLine - 1;
            if (moreRowsFollow) {
                int derivedRow  = equationIndex * 2 + 2;
                int nextFreeRow = equationIndex * 2 + 3;
                int[] nextSeeds = seedCompatibleBottoms(grid, derivedRow, usageCounts);
                if (!fillRow(grid, nextFreeRow, usageCounts, nextSeeds)) return null;
            }
        }
        return grid;
    }

    private Map<Character, Integer> initialUsageCounts() {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (Operator op : registry.all()) {
            counts.put(op.symbol(), 0);
        }
        return counts;
    }

    // ── Column-pair seeding ───────────────────────────────────────────────────

    /**
     * Seeds top and bottom values for all free column positions.
     *
     * <p>Free columns (col 0, 1, 3, …) are assigned to operators round-robin
     * in ascending usage order.  For each column the assigned operator provides
     * a valid bottom value via {@link Operator#validRightOperands}, guaranteeing
     * that operator has at least one usable (top, bottom) pair in the derived row.
     * Unseeded positions fall back to {@link #nextOperand()}.
     */
    private void seedCompatiblePairs(int[] topSeeds, int[] bottomSeeds,
                                     Map<Character, Integer> usageCounts) {
        Arrays.fill(topSeeds,    Integer.MIN_VALUE);
        Arrays.fill(bottomSeeds, Integer.MIN_VALUE);

        List<Operator> operators = operatorsByAscendingUsage(usageCounts);
        int[]          freeCols  = freeColumnPositions();

        for (int i = 0; i < freeCols.length; i++) {
            int      freeCol  = freeCols[i];
            Operator operator = operators.get(i % operators.size());

            for (int attempt = 0; attempt < 50; attempt++) {
                int           topValue    = picker.next();
                List<Integer> validBottoms = operator.validRightOperands(topValue, config, random);
                if (!validBottoms.isEmpty()) {
                    topSeeds[freeCol]    = topValue;
                    bottomSeeds[freeCol] = validBottoms.get(0);   // already shuffled
                    break;
                }
            }
        }
    }

    /**
     * Seeds bottom values for a free row that is vertically paired with an
     * already-derived {@code topRow}.
     *
     * <p>The top value is fixed (from the derived row).  Each free column is
     * assigned to the most underused operator; if that operator cannot pair
     * with the fixed top value, any working operator is used as fallback.
     */
    private int[] seedCompatibleBottoms(PuzzleGrid grid, int topRow,
                                         Map<Character, Integer> usageCounts) {
        int[]          bottomSeeds = new int[config.matrixSize];
        int[]          freeCols    = freeColumnPositions();
        List<Operator> operators   = operatorsByAscendingUsage(usageCounts);
        Arrays.fill(bottomSeeds, Integer.MIN_VALUE);

        for (int i = 0; i < freeCols.length; i++) {
            int freeCol  = freeCols[i];
            int topValue = grid.numbers[topRow][freeCol];

            // Try the assigned operator first, then fall back to any working operator
            Operator target = operators.get(i % operators.size());
            List<Integer> candidates = target.validRightOperands(topValue, config, random);

            if (candidates.isEmpty()) {
                for (Operator fallback : operators) {
                    candidates = fallback.validRightOperands(topValue, config, random);
                    if (!candidates.isEmpty()) break;
                }
            }

            if (!candidates.isEmpty()) {
                bottomSeeds[freeCol] = candidates.get(0);
            }
        }
        return bottomSeeds;
    }

    /** Free column positions in a row: col 0 and each right-operand col (1, 3, 5, …). */
    private int[] freeColumnPositions() {
        int[] free = new int[1 + config.equationsPerLine];
        free[0] = 0;
        for (int eq = 0; eq < config.equationsPerLine; eq++) {
            free[eq + 1] = eq * 2 + 1;
        }
        return free;
    }

    // ── Phase 1: fill a free row ──────────────────────────────────────────────

    private boolean fillRow(PuzzleGrid grid, int row,
                            Map<Character, Integer> usageCounts,
                            int[] seededValues) {
        int matrixSize       = config.matrixSize;
        int equationsPerLine = config.equationsPerLine;

        for (int tryCount = 0; tryCount < MAX_ROW_FILL_TRIES; tryCount++) {
            int[]      rowValues    = new int[matrixSize];
            Operator[] rowOperators = new Operator[equationsPerLine];
            boolean    success      = true;

            rowValues[0] = seededOrRandom(seededValues, 0);

            for (int eq = 0; eq < equationsPerLine; eq++) {
                int leftCol   = eq * 2;
                int rightCol  = leftCol + 1;
                int resultCol = leftCol + 2;
                int leftValue = rowValues[leftCol];
                int seededRight = seededOrRandom(seededValues, rightCol);

                boolean filled = (seededRight != Integer.MIN_VALUE)
                    ? fillEquationWithFixedRight(rowValues, rowOperators, eq,
                                                  leftValue, seededRight, rightCol, resultCol, usageCounts)
                    : fillEquationWithFreeRight(rowValues, rowOperators, eq,
                                                 leftValue, rightCol, resultCol, usageCounts);

                if (!filled) {
                    success = false;
                    break;
                }
            }

            if (!success) continue;

            System.arraycopy(rowValues,    0, grid.numbers[row],             0, matrixSize);
            System.arraycopy(rowOperators, 0, grid.horizontalOperators[row], 0, equationsPerLine);
            updateUsageCounts(usageCounts, rowOperators);
            return true;
        }
        return false;
    }

    private boolean fillEquationWithFixedRight(
            int[] rowValues, Operator[] rowOperators, int equationIndex,
            int leftValue, int seededRight, int rightCol, int resultCol,
            Map<Character, Integer> usageCounts) {

        for (Operator op : operatorsByAscendingUsage(usageCounts)) {
            int result = op.apply(leftValue, seededRight, config);
            if (result == Integer.MIN_VALUE) continue;
            rowValues[rightCol]         = seededRight;
            rowValues[resultCol]        = result;
            rowOperators[equationIndex] = op;
            return true;
        }
        return false;
    }

    private boolean fillEquationWithFreeRight(
            int[] rowValues, Operator[] rowOperators, int equationIndex,
            int leftValue, int rightCol, int resultCol,
            Map<Character, Integer> usageCounts) {

        for (Operator op : operatorsByAscendingUsage(usageCounts)) {
            for (int rightValue : op.validRightOperands(leftValue, config, random)) {
                int result = op.apply(leftValue, rightValue, config);
                if (result == Integer.MIN_VALUE) continue;
                rowValues[rightCol]         = rightValue;
                rowValues[resultCol]        = result;
                rowOperators[equationIndex] = op;
                return true;
            }
        }
        return false;
    }

    // ── Phase 2: derive a constrained row — backtracking ──────────────────────

    private record ColumnCandidate(Operator operator, int derivedValue) {}

    /**
     * Derives row {@code 2*equationIndex+2} from the two rows above it.
     * Uses backtracking with early pruning at result columns to avoid
     * propagating dead-end assignments to later columns.
     */
    private boolean deriveRow(PuzzleGrid grid, int equationIndex,
                               Map<Character, Integer> usageCounts) {
        int topRow    = equationIndex * 2;
        int bottomRow = topRow + 1;
        int derivedRow = topRow + 2;
        int matrixSize       = config.matrixSize;
        int equationsPerLine = config.equationsPerLine;

        for (int retry = 0; retry < DERIVE_ROW_VARIETY_RETRIES; retry++) {

            List<List<ColumnCandidate>> candidatesPerColumn =
                buildColumnCandidates(grid, topRow, bottomRow, matrixSize, usageCounts);

            if (candidatesPerColumn.stream().anyMatch(List::isEmpty)) continue;

            int[]      tryIndex        = new int[matrixSize];
            int[]      derivedValues   = new int[matrixSize];
            Operator[] columnOperators = new Operator[matrixSize];
            int column = 0;

            while (column >= 0) {

                if (column == matrixSize) {
                    Operator[] hOps = buildHorizontalOperators(derivedValues, equationsPerLine);
                    if (hOps == null) {
                        column = matrixSize - 1;
                        continue;
                    }
                    for (int col = 0; col < matrixSize; col++) {
                        grid.verticalOperators[col][equationIndex] = columnOperators[col];
                        grid.numbers[derivedRow][col]              = derivedValues[col];
                    }
                    System.arraycopy(hOps, 0, grid.horizontalOperators[derivedRow], 0, equationsPerLine);
                    updateUsageCounts(usageCounts, columnOperators);
                    updateUsageCounts(usageCounts, hOps);
                    return true;
                }

                List<ColumnCandidate> candidates    = candidatesPerColumn.get(column);
                boolean              advancedColumn = false;

                while (tryIndex[column] < candidates.size()) {
                    ColumnCandidate pick = candidates.get(tryIndex[column]++);
                    columnOperators[column] = pick.operator();
                    derivedValues[column]   = pick.derivedValue();

                    // Early pruning: at result columns, check horizontal equation immediately
                    boolean isResultColumn = column >= 2 && column % 2 == 0;
                    if (isResultColumn) {
                        Operator check = registry.findOperatorForResult(
                            derivedValues[column - 2],
                            derivedValues[column - 1],
                            derivedValues[column]);
                        if (check == null) continue;
                    }
                    advancedColumn = true;
                    break;
                }

                if (advancedColumn) {
                    column++;
                } else {
                    tryIndex[column] = 0;
                    column--;
                }
            }
        }
        return false;
    }

    private List<List<ColumnCandidate>> buildColumnCandidates(
            PuzzleGrid grid, int topRow, int bottomRow, int matrixSize,
            Map<Character, Integer> usageCounts) {

        List<Operator>              ordered = operatorsByAscendingUsageWithJitter(usageCounts);
        List<List<ColumnCandidate>> result  = new ArrayList<>(matrixSize);

        for (int col = 0; col < matrixSize; col++) {
            int topValue    = grid.numbers[topRow][col];
            int bottomValue = grid.numbers[bottomRow][col];
            List<ColumnCandidate> candidates = new ArrayList<>();
            for (Operator op : ordered) {
                int derived = op.apply(topValue, bottomValue, config);
                if (derived != Integer.MIN_VALUE) {
                    candidates.add(new ColumnCandidate(op, derived));
                }
            }
            result.add(candidates);
        }
        return result;
    }

    private Operator[] buildHorizontalOperators(int[] derivedValues, int equationsPerLine) {
        Operator[] ops = new Operator[equationsPerLine];
        for (int eq = 0; eq < equationsPerLine; eq++) {
            int leftCol   = eq * 2;
            int rightCol  = leftCol + 1;
            int resultCol = leftCol + 2;
            Operator op = registry.findOperatorForResult(
                derivedValues[leftCol], derivedValues[rightCol], derivedValues[resultCol]);
            if (op == null) return null;
            ops[eq] = op;
        }
        return ops;
    }

    // ── Operator ordering ─────────────────────────────────────────────────────

    private List<Operator> operatorsByAscendingUsage(Map<Character, Integer> usageCounts) {
        List<Operator> sorted = registry.all();
        sorted.sort(Comparator.comparingInt(op -> usageCounts.getOrDefault(op.symbol(), 0)));
        return sorted;
    }

    private List<Operator> operatorsByAscendingUsageWithJitter(Map<Character, Integer> usageCounts) {
        List<Operator> shuffled = registry.all();
        Collections.shuffle(shuffled, random);
        shuffled.sort(Comparator.comparingInt(op -> usageCounts.getOrDefault(op.symbol(), 0)));
        return shuffled;
    }

    // ── Usage counting ─────────────────────────────────────────────────────────

    private static void updateUsageCounts(Map<Character, Integer> usageCounts, Operator[] ops) {
        for (Operator op : ops) {
            usageCounts.merge(op.symbol(), 1, Integer::sum);
        }
    }

    private OperatorUsageReport countOperatorUsage(PuzzleGrid grid) {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (Operator op : registry.all()) counts.put(op.symbol(), 0);
        for (int row = 0; row < config.matrixSize; row++)
            for (int eq = 0; eq < config.equationsPerLine; eq++)
                counts.merge(grid.horizontalOperators[row][eq].symbol(), 1, Integer::sum);
        for (int col = 0; col < config.matrixSize; col++)
            for (int eq = 0; eq < config.equationsPerLine; eq++)
                counts.merge(grid.verticalOperators[col][eq].symbol(), 1, Integer::sum);
        return new OperatorUsageReport(counts);
    }

    private record OperatorUsageReport(Map<Character, Integer> countBySymbol) {

        boolean meetsMinimum(int minimumCount) {
            if (minimumCount <= 0) return true;
            return countBySymbol.values().stream().allMatch(count -> count >= minimumCount);
        }

        String summary() {
            StringBuilder sb = new StringBuilder("{");
            countBySymbol.forEach((sym, cnt) -> sb.append(sym).append("=").append(cnt).append(", "));
            if (sb.length() > 1) sb.setLength(sb.length() - 2);
            return sb.append("}").toString();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int nextOperand() {
        return picker.next();
    }

    private int seededOrRandom(int[] seeds, int column) {
        if (seeds != null && seeds[column] != Integer.MIN_VALUE) {
            return seeds[column];
        }
        return nextOperand();
    }

    private static void assertVerified(PuzzleGrid grid) {
        if (!grid.verify()) {
            throw new IllegalStateException("BUG: grid failed verification.");
        }
    }
}