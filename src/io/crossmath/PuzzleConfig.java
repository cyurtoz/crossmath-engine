package io.crossmath;

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
     *
     * <p>Default is 2 — every operator the caller registers will appear at
     * least twice, ensuring no operator is silently ignored.  Set to 1 to
     * require a single occurrence, or 0 to disable the check entirely.
     */
    public final int minUsagePerOperator;

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
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format(
            "PuzzleConfig{matrixSize=%d, cellValues=[%d..%d], equationsPerLine=%d, " +
            "maxAddOperand=%d, maxMultiplyOperand=%d, maxDivisionDivisor=%d, minUsagePerOperator=%d}",
            matrixSize, minCellValue, maxCellValue, equationsPerLine,
            maxAddOperand, maxMultiplyOperand, maxDivisionDivisor, minUsagePerOperator);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {

        private int matrixSize            = 5;
        private int minCellValue          = 1;
        private int maxCellValue          = 100;
        private int maxGenerationAttempts = 1000;
        private int minUsagePerOperator   = 2;

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
         * (default 2).  Set to 1 for at-least-once, 0 to disable the check.
         */
        public Builder minUsagePerOperator(int minUsagePerOperator) {
            this.minUsagePerOperator = minUsagePerOperator;
            return this;
        }

        public PuzzleConfig build() {
            return new PuzzleConfig(this);
        }
    }
}