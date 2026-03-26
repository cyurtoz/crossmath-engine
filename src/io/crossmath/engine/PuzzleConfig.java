package io.crossmath.engine;

/**
 * Immutable puzzle configuration.
 *
 * Every limit used by operators and the generator is derived here from
 * three root inputs: {@code matrixSize}, {@code minCellValue}, {@code maxCellValue}.
 * No magic numbers exist anywhere else in the codebase.
 *
 * <pre>
 *   matrixSize=5  →  5×5 number matrix  →  9×9 display grid
 *   matrixSize=7  →  7×7 number matrix  →  13×13 display grid
 * </pre>
 *
 * {@code matrixSize} must be odd and ≥ 3.
 */
public final class PuzzleConfig {

    // ── Root inputs ───────────────────────────────────────────────────────────

    /** Side length of the number matrix. Must be odd and ≥ 3. */
    public final int matrixSize;

    /** Minimum allowed value for any number cell (default 1). */
    public final int minCellValue;

    /** Maximum allowed value for any number cell (e.g. 100 or 999). */
    public final int maxCellValue;

    // ── Derived constants ─────────────────────────────────────────────────────

    /**
     * Number of chained equations per row and per column.
     * Derived: {@code (matrixSize - 1) / 2}.
     * Example: matrixSize=5 → 2 equations; matrixSize=7 → 3 equations.
     */
    public final int equationsPerLine;

    /**
     * Maximum operand allowed for addition and subtraction.
     * Derived: {@code maxCellValue / 2}.
     * Guarantees {@code a + b ≤ maxCellValue} by construction when
     * both operands are drawn from {@code [minCellValue, maxAddOperand]}.
     */
    public final int maxAddOperand;

    /**
     * Maximum operand allowed for multiplication.
     * Derived: {@code ⌊√maxCellValue⌋}.
     * Guarantees {@code a × b ≤ maxCellValue} by construction.
     */
    public final int maxMultiplyOperand;

    /**
     * Maximum divisor allowed for division.
     * Derived: {@code maxCellValue / 2}.
     * Prevents trivially large divisors that force the quotient to always be 1.
     */
    public final int maxDivisionDivisor;

    /** Maximum number of outer generation attempts before throwing. */
    public final int maxGenerationAttempts;

    /**
     * Minimum number of times each registered operator must appear across
     * all equations in the final grid.
     */
    public final int minUsagePerOperator;

    /**
     * Maximum safe value for a freshly picked free operand.
     *
     * <p>This is a <b>grid geometry constraint</b>, not an operator constraint.
     * A horizontal chain of {@code equationsPerLine} additions applied to free
     * operands can produce a result column value up to
     * {@code (equationsPerLine + 1) × cap}.  Two such source rows added
     * vertically in the derived row produce a value up to
     * {@code 2 × (equationsPerLine + 1) × cap}.  Setting that equal to
     * {@code maxCellValue} gives:
     * <pre>
     *   cap = maxCellValue / (2 × (equationsPerLine + 1))
     * </pre>
     * Example: matrixSize=5, maxCellValue=100 → cap = 100/6 ≈ 16.
     *
     * <p>This cap is applied to ALL free operand picks regardless of operator.
     * It is conservative for subtract and divide (whose results shrink or are
     * bounded), but guarantees correctness for addition-only puzzles — which
     * is the only case that would otherwise overflow.  For larger numbers in
     * addition-only puzzles, increase {@code maxCellValue} (e.g. 500 or 999).
     */
    public final int maxChainSafeOperand;

    /**
     * Number of equal-width brackets used for operand selection.
     *
     * <p>The range {@code [minCellValue, maxChainSafeOperand]} is divided into
     * this many segments and picks cycle through them round-robin, ensuring
     * small, medium, and large values all appear in the puzzle rather than
     * clustering near the minimum.
     *
     * <p>Default: 4.  Set to 1 for uniform random selection.
     */
    public final int numBrackets;

    // ── Construction via builder ──────────────────────────────────────────────

    private PuzzleConfig(Builder builder) {
        if (builder.matrixSize < 3 || builder.matrixSize % 2 == 0) {
            throw new IllegalArgumentException(
                "matrixSize must be odd and ≥ 3, got: " + builder.matrixSize);
        }
        if (builder.minCellValue < 0) {
            throw new IllegalArgumentException("minCellValue must be ≥ 0");
        }
        if (builder.maxCellValue <= builder.minCellValue) {
            throw new IllegalArgumentException("maxCellValue must be > minCellValue");
        }

        this.matrixSize             = builder.matrixSize;
        this.minCellValue           = builder.minCellValue;
        this.maxCellValue           = builder.maxCellValue;
        this.equationsPerLine       = (builder.matrixSize - 1) / 2;
        this.maxAddOperand          = builder.maxCellValue / 2;
        this.maxMultiplyOperand     = Math.max(2, (int) Math.sqrt(builder.maxCellValue));
        this.maxDivisionDivisor     = builder.maxCellValue / 2;
        this.maxGenerationAttempts  = builder.maxGenerationAttempts;
        this.minUsagePerOperator    = builder.minUsagePerOperator;

        int chainLength             = (builder.matrixSize - 1) / 2 + 1;  // equationsPerLine + 1
        this.maxChainSafeOperand    = Math.max(1, builder.maxCellValue / (2 * chainLength));
        this.numBrackets            = Math.max(1, builder.numBrackets);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format(
            "PuzzleConfig{matrixSize=%d, cellValues=[%d..%d], equationsPerLine=%d, " +
            "maxChainSafeOperand=%d, numBrackets=%d, minUsagePerOperator=%d}",
            matrixSize, minCellValue, maxCellValue, equationsPerLine,
            maxChainSafeOperand, numBrackets, minUsagePerOperator);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {

        private int matrixSize            = 5;
        private int minCellValue          = 1;
        private int maxCellValue          = 100;
        private int maxGenerationAttempts = 1000;
        private int minUsagePerOperator   = 2;
        private int numBrackets           = 4;

        /** Grid dimension — must be odd and ≥ 3 (default 5 → 9×9 display). */
        public Builder matrixSize(int matrixSize) {
            this.matrixSize = matrixSize;
            return this;
        }

        /** Minimum cell value (default 1). */
        public Builder minCellValue(int minCellValue) {
            this.minCellValue = minCellValue;
            return this;
        }

        /** Maximum cell value; supports 3-digit numbers up to 999 (default 100). */
        public Builder maxCellValue(int maxCellValue) {
            this.maxCellValue = maxCellValue;
            return this;
        }

        /** How many times the generator retries before giving up (default 1000). */
        public Builder maxGenerationAttempts(int maxGenerationAttempts) {
            this.maxGenerationAttempts = maxGenerationAttempts;
            return this;
        }

        /**
         * Minimum times each registered operator must appear in the finished grid
         * (default 2). Set to 1 for at-least-once, 0 to disable.
         */
        public Builder minUsagePerOperator(int minUsagePerOperator) {
            this.minUsagePerOperator = minUsagePerOperator;
            return this;
        }

        /**
         * Number of equal-width brackets for operand selection (default 4).
         * Picks cycle round-robin through brackets so all value ranges appear.
         * Set to 1 for uniform random selection.
         */
        public Builder numBrackets(int numBrackets) {
            this.numBrackets = numBrackets;
            return this;
        }

        public PuzzleConfig build() {
            return new PuzzleConfig(this);
        }
    }
}
