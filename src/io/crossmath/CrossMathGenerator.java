package io.crossmath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates fully solved CrossMath puzzle grids.
 *
 * <h2>Phase 1 — Operator-first free row filling</h2>
 *
 * Free rows (0, 1, 3, 5, …) choose their own values.  Each equation slot
 * tries operators in ascending usage order so underused operators get first
 * pick.  Each operator returns its full list of valid right operands via
 * {@link Operator#validRightOperands} — all candidates are tried before
 * moving to the next operator.
 *
 * <h2>Phase 2 — Backtracking stack in deriveRow</h2>
 *
 * Derived rows (2, 4, 6, …) inherit values from two source rows via vertical
 * operators.  The previous greedy approach tried one operator per column and
 * then restarted ALL columns from scratch when the final horizontal check
 * failed.
 *
 * <p>The new approach uses a <b>backtracking stack</b>:
 * <ol>
 *   <li>For each column, precompute all valid {@link ColumnCandidate}s
 *       (operator + derived value) in usage-biased, jittered order.</li>
 *   <li>Walk columns left to right, selecting the next untried candidate.</li>
 *   <li>At every <b>result column</b> (columns 2, 4, 6, … where
 *       {@code column % 2 == 0 && column >= 2}), immediately check whether
 *       the partial horizontal equation is satisfiable.  If not, reject the
 *       current candidate and try the next one for that column — without
 *       touching any subsequent column.</li>
 *   <li>When a column exhausts all candidates, backtrack: reset its index
 *       and move back to the previous column to try its next candidate.</li>
 * </ol>
 *
 * <p>This is O(candidates^matrixSize) worst case but in practice terminates
 * in very few steps because the partial horizontal pruning rejects bad branches
 * at the earliest possible column rather than letting them propagate.
 *
 * <h2>Division-compatible seeding</h2>
 *
 * Division in derived rows requires {@code topRow[c] % bottomRow[c] == 0}.
 * Source rows are seeded so that several free-column pairs are an exact
 * multiple/divisor relationship, giving {@code deriveRow} real divisible pairs
 * to work with.
 *
 * <h2>Reproducibility</h2>
 * Identical seed + config + registry → identical grid every time.
 */
public class CrossMathGenerator {

    /**
     * How many times {@code deriveRow} retries with a freshly shuffled operator
     * ordering.  One attempt is usually sufficient thanks to backtracking, but
     * a few retries provide variety across seeds.
     */
    private static final int DERIVE_ROW_VARIETY_RETRIES = 5;

    private static final int MAX_ROW_FILL_TRIES = 500;

    private final PuzzleConfig     config;
    private final OperatorRegistry registry;
    private final Random           random;

    // ── Construction ──────────────────────────────────────────────────────────

    public CrossMathGenerator(PuzzleConfig config, OperatorRegistry registry, Random random) {
        this.config   = config;
        this.registry = registry;
        this.random   = random;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public PuzzleGrid generate() {
        for (int attempt = 1; attempt <= config.maxGenerationAttempts; attempt++) {
            PuzzleGrid candidate = tryBuildGrid();
            if (candidate == null) {
                continue;
            }
            OperatorUsageReport usageReport = countOperatorUsage(candidate);
            if (!usageReport.meetsMinimum(config.minUsagePerOperator)) {
                System.out.printf(
                    "[Generator] Attempt %d discarded — usage below minimum: %s%n",
                    attempt, usageReport.summary());
                continue;
            }
            System.out.printf("[Generator] Solved on attempt %d. Usage: %s%n",
                attempt, usageReport.summary());
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

        int pairsToSeed = config.minUsagePerOperator;

        int[] divisorSeeds  = buildDivisorSeeds(pairsToSeed);
        int[] dividendSeeds = buildDividendSeeds(divisorSeeds);

        if (!fillRow(grid, 0, usageCounts, dividendSeeds)) return null;
        if (!fillRow(grid, 1, usageCounts, divisorSeeds))  return null;

        for (int equationIndex = 0; equationIndex < config.equationsPerLine; equationIndex++) {
            if (!deriveRow(grid, equationIndex, usageCounts)) return null;

            boolean moreRowsFollow = equationIndex < config.equationsPerLine - 1;
            if (moreRowsFollow) {
                int derivedRow  = equationIndex * 2 + 2;
                int nextFreeRow = equationIndex * 2 + 3;
                int[] nextSeeds = buildDivisorSeedsFrom(grid, derivedRow, pairsToSeed);
                if (!fillRow(grid, nextFreeRow, usageCounts, nextSeeds)) return null;
            }
        }
        return grid;
    }

    private Map<Character, Integer> initialUsageCounts() {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (Operator operator : registry.all()) {
            counts.put(operator.symbol(), 0);
        }
        return counts;
    }

    // ── Phase 1: fill a free row ──────────────────────────────────────────────

    private boolean fillRow(PuzzleGrid grid, int row,
                            Map<Character, Integer> usageCounts,
                            int[] seededFreeValues) {
        int matrixSize       = config.matrixSize;
        int equationsPerLine = config.equationsPerLine;

        for (int tryCount = 0; tryCount < MAX_ROW_FILL_TRIES; tryCount++) {
            int[]      rowValues    = new int[matrixSize];
            Operator[] rowOperators = new Operator[equationsPerLine];
            boolean    success      = true;

            rowValues[0] = seededOrRandom(seededFreeValues, 0);

            for (int equationIndex = 0; equationIndex < equationsPerLine; equationIndex++) {
                int leftColumn   = equationIndex * 2;
                int rightColumn  = leftColumn + 1;
                int resultColumn = leftColumn + 2;

                int     leftValue   = rowValues[leftColumn];
                int     seededRight = seededOrRandom(seededFreeValues, rightColumn);
                boolean filled;

                if (seededRight != Integer.MIN_VALUE) {
                    filled = fillEquationWithFixedRight(
                        rowValues, rowOperators, equationIndex,
                        leftValue, seededRight, rightColumn, resultColumn, usageCounts);
                } else {
                    filled = fillEquationWithFreeRight(
                        rowValues, rowOperators, equationIndex,
                        leftValue, rightColumn, resultColumn, usageCounts);
                }

                if (!filled) {
                    success = false;
                    break;
                }
            }

            if (!success) {
                continue;
            }

            System.arraycopy(rowValues,    0, grid.numbers[row],             0, matrixSize);
            System.arraycopy(rowOperators, 0, grid.horizontalOperators[row], 0, equationsPerLine);
            updateUsageCounts(usageCounts, rowOperators);
            return true;
        }
        return false;
    }

    private boolean fillEquationWithFixedRight(
            int[] rowValues, Operator[] rowOperators, int equationIndex,
            int leftValue, int seededRight, int rightColumn, int resultColumn,
            Map<Character, Integer> usageCounts) {

        for (Operator candidate : operatorsByAscendingUsage(usageCounts)) {
            int result = candidate.apply(leftValue, seededRight, config);
            if (result == Integer.MIN_VALUE) {
                continue;
            }
            rowValues[rightColumn]      = seededRight;
            rowValues[resultColumn]     = result;
            rowOperators[equationIndex] = candidate;
            return true;
        }
        return false;
    }

    private boolean fillEquationWithFreeRight(
            int[] rowValues, Operator[] rowOperators, int equationIndex,
            int leftValue, int rightColumn, int resultColumn,
            Map<Character, Integer> usageCounts) {

        for (Operator candidate : operatorsByAscendingUsage(usageCounts)) {
            for (int rightValue : candidate.validRightOperands(leftValue, config, random)) {
                int result = candidate.apply(leftValue, rightValue, config);
                if (result == Integer.MIN_VALUE) {
                    continue;
                }
                rowValues[rightColumn]      = rightValue;
                rowValues[resultColumn]     = result;
                rowOperators[equationIndex] = candidate;
                return true;
            }
        }
        return false;
    }

    // ── Phase 2: derive a constrained row — backtracking search ───────────────

    /**
     * Pairs a vertical operator with the derived cell value it produces.
     * Precomputed once per column before the backtracking search begins.
     */
    private record ColumnCandidate(Operator operator, int derivedValue) {}

    /**
     * Derives row {@code 2*equationIndex+2} from the two rows above it.
     *
     * <h3>Backtracking algorithm</h3>
     *
     * <ol>
     *   <li>Precompute, for each column, all valid {@link ColumnCandidate}s
     *       (operator + derived value pairs) in usage-biased jittered order.
     *       If any column has zero valid candidates the attempt fails immediately
     *       without entering the search loop.</li>
     *   <li>Walk columns left to right.  A {@code tryIndex[column]} cursor
     *       tracks the next untried candidate at each column depth — this forms
     *       the implicit backtracking stack.</li>
     *   <li>At every <b>result column</b> (column ≥ 2, column even), apply early
     *       horizontal pruning: check immediately whether any operator can satisfy
     *       {@code derived[col-2] op derived[col-1] = derived[col]}.  If not,
     *       reject the current candidate and advance the cursor — no downstream
     *       columns are ever touched for this bad branch.</li>
     *   <li>When a column's cursor exhausts all candidates, reset it and
     *       backtrack one column (decrement the column pointer).  The previous
     *       column's cursor is already past the value it last chose, so it
     *       automatically tries its next candidate.</li>
     *   <li>Success when the column pointer reaches {@code matrixSize}: all
     *       partial horizontal checks passed, so the horizontal operators are
     *       re-fetched (guaranteed to exist) and committed to the grid.</li>
     * </ol>
     *
     * <p>Retried {@link #DERIVE_ROW_VARIETY_RETRIES} times with a freshly
     * jittered candidate ordering so different seeds produce different-looking grids.
     */
    private boolean deriveRow(PuzzleGrid grid, int equationIndex,
                               Map<Character, Integer> usageCounts) {
        int topSourceRow    = equationIndex * 2;
        int bottomSourceRow = topSourceRow + 1;
        int derivedRow      = topSourceRow + 2;

        int matrixSize       = config.matrixSize;
        int equationsPerLine = config.equationsPerLine;

        for (int retryCount = 0; retryCount < DERIVE_ROW_VARIETY_RETRIES; retryCount++) {

            // ── Precompute valid candidates per column ─────────────────────────
            // Each entry is the ordered list of (operator, derivedValue) pairs
            // for that column, in usage-biased jittered order.
            List<List<ColumnCandidate>> candidatesPerColumn =
                buildColumnCandidates(grid, topSourceRow, bottomSourceRow, matrixSize, usageCounts);

            // If any column has no valid operator at all, fail this retry immediately
            boolean anyColumnEmpty = candidatesPerColumn.stream().anyMatch(List::isEmpty);
            if (anyColumnEmpty) {
                continue;
            }

            // ── Backtracking search ────────────────────────────────────────────
            // tryIndex[c] = index of the next untried candidate at column c.
            // This array IS the implicit stack — no explicit Deque needed.
            int[]      tryIndex       = new int[matrixSize];
            int[]      derivedValues  = new int[matrixSize];
            Operator[] columnOperators = new Operator[matrixSize];

            int column = 0;

            while (column >= 0) {

                if (column == matrixSize) {
                    // ── All columns assigned, partial checks all passed ────────
                    // Re-fetch horizontal operators (guaranteed non-null because
                    // partial checks at each result column already verified them).
                    Operator[] horizontalOperators = buildHorizontalOperators(derivedValues, equationsPerLine);

                    if (horizontalOperators == null) {
                        // Should never happen — partial checks guarantee this.
                        // Treat as backtrack from the last column.
                        column = matrixSize - 1;
                        continue;
                    }

                    // Commit to grid
                    for (int col = 0; col < matrixSize; col++) {
                        grid.verticalOperators[col][equationIndex] = columnOperators[col];
                        grid.numbers[derivedRow][col]              = derivedValues[col];
                    }
                    System.arraycopy(horizontalOperators, 0,
                        grid.horizontalOperators[derivedRow], 0, equationsPerLine);
                    updateUsageCounts(usageCounts, columnOperators);
                    updateUsageCounts(usageCounts, horizontalOperators);
                    return true;
                }

                // ── Try candidates for this column in order ────────────────────
                List<ColumnCandidate> candidates = candidatesPerColumn.get(column);
                boolean advancedToNextColumn     = false;

                while (tryIndex[column] < candidates.size()) {
                    ColumnCandidate candidate = candidates.get(tryIndex[column]);
                    tryIndex[column]++;

                    columnOperators[column] = candidate.operator();
                    derivedValues[column]   = candidate.derivedValue();

                    // ── Early horizontal pruning at result columns ─────────────
                    // A result column is any even column ≥ 2 (columns 2, 4, 6, …).
                    // At column k, the horizontal equation is:
                    //   derived[k-2]  op  derived[k-1]  =  derived[k]
                    // If no operator can satisfy this, reject now — don't advance.
                    boolean isResultColumn = column >= 2 && column % 2 == 0;
                    if (isResultColumn) {
                        Operator partialCheck = registry.findOperatorForResult(
                            derivedValues[column - 2],
                            derivedValues[column - 1],
                            derivedValues[column]
                        );
                        if (partialCheck == null) {
                            continue;   // pruned — try next candidate for this column
                        }
                    }

                    advancedToNextColumn = true;
                    break;
                }

                if (advancedToNextColumn) {
                    column++;
                } else {
                    // All candidates for this column exhausted — backtrack
                    tryIndex[column] = 0;   // reset so this column can be re-entered on a future retry
                    column--;
                }
            }
            // column < 0 means the full backtracking search failed for this retry
        }
        return false;
    }

    /**
     * Precomputes all valid {@link ColumnCandidate}s per column.
     * The candidate list for each column is sorted by ascending usage count
     * with random jitter within equal-usage tiers (same strategy as
     * {@link #fillRow}).
     */
    private List<List<ColumnCandidate>> buildColumnCandidates(
            PuzzleGrid grid, int topRow, int bottomRow, int matrixSize,
            Map<Character, Integer> usageCounts) {

        List<Operator> orderedOperators = operatorsByAscendingUsageWithJitter(usageCounts);
        List<List<ColumnCandidate>> result = new ArrayList<>(matrixSize);

        for (int column = 0; column < matrixSize; column++) {
            int topValue    = grid.numbers[topRow][column];
            int bottomValue = grid.numbers[bottomRow][column];
            List<ColumnCandidate> candidates = new ArrayList<>();

            for (Operator operator : orderedOperators) {
                int derivedValue = operator.apply(topValue, bottomValue, config);
                if (derivedValue != Integer.MIN_VALUE) {
                    candidates.add(new ColumnCandidate(operator, derivedValue));
                }
            }
            result.add(candidates);
        }
        return result;
    }

    /**
     * Builds the horizontal operator array for the derived row.
     * Called only after all partial checks have passed, so every
     * {@code findOperatorForResult} call is guaranteed to succeed.
     *
     * @return the operator array, or {@code null} on unexpected failure
     */
    private Operator[] buildHorizontalOperators(int[] derivedValues, int equationsPerLine) {
        Operator[] horizontalOperators = new Operator[equationsPerLine];

        for (int equationIndex = 0; equationIndex < equationsPerLine; equationIndex++) {
            int leftColumn   = equationIndex * 2;
            int rightColumn  = leftColumn + 1;
            int resultColumn = leftColumn + 2;

            Operator operator = registry.findOperatorForResult(
                derivedValues[leftColumn],
                derivedValues[rightColumn],
                derivedValues[resultColumn]
            );
            if (operator == null) {
                return null;
            }
            horizontalOperators[equationIndex] = operator;
        }
        return horizontalOperators;
    }

    // ── Division-compatible seeding ───────────────────────────────────────────

    private int[] freeColumnPositions() {
        int[] free = new int[1 + config.equationsPerLine];
        free[0] = 0;
        for (int eq = 0; eq < config.equationsPerLine; eq++) {
            free[eq + 1] = eq * 2 + 1;
        }
        return free;
    }

    private int[] buildDivisorSeeds(int pairsToSeed) {
        int[] seeds       = new int[config.matrixSize];
        int[] freeColumns = freeColumnPositions();
        java.util.Arrays.fill(seeds, Integer.MIN_VALUE);

        if (config.maxDivisionDivisor < 2) {
            return seeds;
        }

        int seeded = 0;
        for (int freeCol : freeColumns) {
            if (seeded >= pairsToSeed) break;
            seeds[freeCol] = 2 + random.nextInt(config.maxDivisionDivisor - 1);
            seeded++;
        }
        return seeds;
    }

    private int[] buildDividendSeeds(int[] divisorSeeds) {
        int[] dividends = new int[config.matrixSize];
        java.util.Arrays.fill(dividends, Integer.MIN_VALUE);

        for (int col = 0; col < config.matrixSize; col++) {
            int divisor = divisorSeeds[col];
            if (divisor == Integer.MIN_VALUE) {
                continue;
            }
            int maxQuotient = config.maxCellValue / divisor;
            if (maxQuotient < config.minCellValue) {
                divisorSeeds[col] = Integer.MIN_VALUE;
                continue;
            }
            int quotientRange = maxQuotient - config.minCellValue + 1;
            int quotient      = config.minCellValue + random.nextInt(quotientRange);
            dividends[col]    = divisor * quotient;
        }
        return dividends;
    }

    private int[] buildDivisorSeedsFrom(PuzzleGrid grid, int referenceRow, int pairsToSeed) {
        int[] seeds       = new int[config.matrixSize];
        int[] freeColumns = freeColumnPositions();
        java.util.Arrays.fill(seeds, Integer.MIN_VALUE);

        int seeded = 0;
        for (int freeCol : freeColumns) {
            if (seeded >= pairsToSeed) break;

            int referenceValue = grid.numbers[referenceRow][freeCol];
            int upperBound     = Math.min(referenceValue, config.maxDivisionDivisor);

            List<Integer> validDivisors = new ArrayList<>();
            for (int divisor = 2; divisor <= upperBound; divisor++) {
                if (referenceValue % divisor != 0) continue;
                int quotient = referenceValue / divisor;
                if (quotient >= config.minCellValue && quotient <= config.maxCellValue) {
                    validDivisors.add(divisor);
                }
            }

            if (!validDivisors.isEmpty()) {
                seeds[freeCol] = validDivisors.get(random.nextInt(validDivisors.size()));
                seeded++;
            }
        }
        return seeds;
    }

    private int seededOrRandom(int[] seeds, int column) {
        if (seeds != null && seeds[column] != Integer.MIN_VALUE) {
            return seeds[column];
        }
        return randomSmallOperand();
    }

    // ── Operator ordering ─────────────────────────────────────────────────────

    /** Ascending usage order, stable within tied counts (insertion order). */
    private List<Operator> operatorsByAscendingUsage(Map<Character, Integer> usageCounts) {
        List<Operator> sorted = registry.all();
        sorted.sort(Comparator.comparingInt(op -> usageCounts.getOrDefault(op.symbol(), 0)));
        return sorted;
    }

    /** Ascending usage order with random jitter within tied counts. */
    private List<Operator> operatorsByAscendingUsageWithJitter(Map<Character, Integer> usageCounts) {
        List<Operator> shuffled = registry.all();
        Collections.shuffle(shuffled, random);
        shuffled.sort(Comparator.comparingInt(op -> usageCounts.getOrDefault(op.symbol(), 0)));
        return shuffled;
    }

    // ── Usage counting ─────────────────────────────────────────────────────────

    private static void updateUsageCounts(Map<Character, Integer> usageCounts,
                                          Operator[] operators) {
        for (Operator operator : operators) {
            usageCounts.merge(operator.symbol(), 1, Integer::sum);
        }
    }

    private OperatorUsageReport countOperatorUsage(PuzzleGrid grid) {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (Operator operator : registry.all()) {
            counts.put(operator.symbol(), 0);
        }
        for (int row = 0; row < config.matrixSize; row++) {
            for (int eq = 0; eq < config.equationsPerLine; eq++) {
                counts.merge(grid.horizontalOperators[row][eq].symbol(), 1, Integer::sum);
            }
        }
        for (int col = 0; col < config.matrixSize; col++) {
            for (int eq = 0; eq < config.equationsPerLine; eq++) {
                counts.merge(grid.verticalOperators[col][eq].symbol(), 1, Integer::sum);
            }
        }
        return new OperatorUsageReport(counts);
    }

    private record OperatorUsageReport(Map<Character, Integer> countBySymbol) {

        boolean meetsMinimum(int minimumCount) {
            if (minimumCount <= 0) return true;
            return countBySymbol.values().stream().allMatch(count -> count >= minimumCount);
        }

        String summary() {
            StringBuilder sb = new StringBuilder("{");
            countBySymbol.forEach((symbol, count) ->
                sb.append(symbol).append("=").append(count).append(", "));
            if (sb.length() > 1) sb.setLength(sb.length() - 2);
            sb.append("}");
            return sb.toString();
        }
    }

    // ── Low-level helpers ──────────────────────────────────────────────────────

    private int randomSmallOperand() {
        int rangeSize = config.maxAddOperand - config.minCellValue + 1;
        return config.minCellValue + random.nextInt(Math.max(1, rangeSize));
    }

    private static void assertVerified(PuzzleGrid grid) {
        if (!grid.verify()) {
            throw new IllegalStateException(
                "BUG: Generated grid failed verification. Please file a bug report.");
        }
    }
}