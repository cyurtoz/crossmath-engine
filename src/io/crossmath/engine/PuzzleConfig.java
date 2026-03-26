package io.crossmath.engine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable puzzle configuration.
 *
 * <h2>Root inputs</h2>
 * Three core parameters drive all derived constants:
 * {@code matrixSize}, {@code minCellValue}, {@code maxCellValue}.
 *
 * <h2>Per-operator operand caps</h2>
 * {@code maxAddOperand}, {@code maxMultiplyOperand}, and {@code maxDivisionDivisor}
 * are derived from {@code maxCellValue} by default but can be overridden in the
 * builder for grading purposes (e.g. {@code maxMultiplyOperand(2)} limits
 * multiplication to the 2× table).
 *
 * <h2>Grading support</h2>
 * Fields like {@code allowedOperators}, {@code resultChainingPlayable},
 * visibility percentages, and masking controls are included so a future grading
 * layer can configure age-appropriate puzzles without changing this class.
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

    // ── Derived constants (overridable via builder) ───────────────────────────

    /**
     * Number of chained equations per row and per column.
     * Derived: {@code (matrixSize - 1) / 2}.
     * Example: matrixSize=5 → 2 equations; matrixSize=7 → 3 equations.
     */
    public final int equationsPerLine;

    /**
     * Maximum operand allowed for addition and subtraction.
     * Default derived: {@code maxCellValue / 2}.
     * Override via builder for grading (e.g. addition only up to 50).
     */
    public final int maxAddOperand;

    /**
     * Maximum operand allowed for multiplication.
     * Default derived: {@code ⌊√maxCellValue⌋}.
     * Override via builder for grading (e.g. {@code 2} for 2× table only).
     */
    public final int maxMultiplyOperand;

    /**
     * Maximum divisor allowed for division.
     * Default derived: {@code maxCellValue / 2}.
     * Override via builder for grading.
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
     * Maximum value for numbers introduced directly by the sparse shape
     * generator (seeded roots and fully free operands).
     *
     * <p>This is intentionally separate from {@link #maxChainSafeOperand}.
     * The old chain-safe cap was derived for dense matrix generation and is
     * too conservative for sparse asymmetric shapes. By default we allow
     * introduced values up to {@code maxAddOperand}, which keeps addition
     * viable while still letting the picker reach the upper half of the range.
     */
    public final int maxSeedValue;

    /**
     * Number of equal-width brackets used for operand selection.
     *
     * <p>The range {@code [minCellValue, maxSeedValue]} is divided into
     * this many segments and picks cycle through them round-robin, ensuring
     * small, medium, and large values all appear in the puzzle rather than
     * clustering near the minimum.
     *
     * <p>Default: 4.  Set to 1 for uniform random selection.
     */
    public final int numBrackets;

    /**
     * Target number of equation arms for the shape generator.
     * For matrixSize=3 (dense educational grid): defaults to 6 (full 3×3).
     * For matrixSize=5: defaults to 10 (sparse asymmetric shape).
     * For matrixSize=7: defaults to 21.
     * Override via builder for custom density.
     */
    public final int targetEquationCount;

    // ── Operator filtering ────────────────────────────────────────────────────

    /**
     * Which operator symbols to activate.  {@code null} means all registered
     * operators are used.  When non-null, only operators whose symbol is in
     * this set will be registered.
     *
     * <p>Examples:
     * <ul>
     *   <li>Level 0 (addition only):        {@code Set.of('+')}</li>
     *   <li>Level 1.5 (add + subtract):     {@code Set.of('+', '-')}</li>
     *   <li>Level 4 (all four):             {@code Set.of('+', '-', '*', '/')}</li>
     * </ul>
     */
    public final Set<Character> allowedOperators;

    // ── Result chaining ───────────────────────────────────────────────────────

    /**
     * Whether result-chaining patterns ({@code a op b = c op d = e}) are
     * allowed in the playable puzzle display.
     *
     * <p>The dense matrix generator naturally produces these chains.  When
     * {@code false} (default), the masking layer must ensure chained result
     * equations are not both visible to the player simultaneously — though
     * they remain valid as internal solution combinations.
     */
    public final boolean resultChainingPlayable;

    // ── Masking / display (grading layer) ─────────────────────────────────────

    /**
     * Minimum percentage of cells visible in the puzzle display (0.0–1.0).
     * The grading layer uses this to control difficulty.
     * Default: 0.0 (no constraint — masking layer decides).
     */
    public final double minVisibilityPercent;

    /**
     * Maximum percentage of cells visible in the puzzle display (0.0–1.0).
     * Default: 1.0 (no constraint).
     */
    public final double maxVisibilityPercent;

    /**
     * Whether operator symbols can be hidden from the player.
     * Early levels (0–2.5) keep operators always visible.
     * Default: true.
     */
    public final boolean operatorsCanBeHidden;

    /**
     * Whether result cells can be hidden from the player.
     * Early levels keep results always or mostly visible.
     * Default: true.
     */
    public final boolean resultsCanBeHidden;

    /**
     * Maximum number of unknowns (hidden cells) within a single equation.
     * Early levels allow only 1; advanced levels allow multi-unknown.
     * Default: {@code Integer.MAX_VALUE} (no limit).
     */
    public final int maxUnknownsPerEquation;

    /**
     * Number of multiple-choice answer options presented to the player.
     * Early levels use 3; advanced levels use 4–5.
     * Default: 4.
     */
    public final int numChoices;

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
        if (builder.maxSeedValue >= 0 &&
            (builder.maxSeedValue < builder.minCellValue || builder.maxSeedValue > builder.maxCellValue)) {
            throw new IllegalArgumentException(
                "maxSeedValue must be in [minCellValue, maxCellValue]");
        }

        this.matrixSize             = builder.matrixSize;
        this.minCellValue           = builder.minCellValue;
        this.maxCellValue           = builder.maxCellValue;
        this.equationsPerLine       = (builder.matrixSize - 1) / 2;
        this.maxGenerationAttempts  = builder.maxGenerationAttempts;
        this.minUsagePerOperator    = builder.minUsagePerOperator;

        // Per-operator caps: use explicit override if set, otherwise derive
        this.maxAddOperand      = builder.maxAddOperand      >= 0
                                ? builder.maxAddOperand
                                : builder.maxCellValue / 2;
        this.maxMultiplyOperand = builder.maxMultiplyOperand >= 0
                                ? builder.maxMultiplyOperand
                                : Math.max(2, (int) Math.sqrt(builder.maxCellValue));
        this.maxDivisionDivisor = builder.maxDivisionDivisor >= 0
                                ? builder.maxDivisionDivisor
                                : builder.maxCellValue / 2;

        int chainLength             = (builder.matrixSize - 1) / 2 + 1;  // equationsPerLine + 1
        this.maxChainSafeOperand    = Math.max(1, builder.maxCellValue / (2 * chainLength));
        this.maxSeedValue           = builder.maxSeedValue >= 0
                                    ? builder.maxSeedValue
                                    : Math.max(builder.minCellValue, this.maxAddOperand);
        this.numBrackets            = Math.max(1, builder.numBrackets);

        // Target equation count
        if (builder.targetEquationCount > 0) {
            this.targetEquationCount = builder.targetEquationCount;
        } else {
            // Dense for small grids (matrixSize=3), sparse for larger
            int epl = this.equationsPerLine;
            this.targetEquationCount = builder.matrixSize <= 3
                ? builder.matrixSize * epl * 2   // full dense (e.g. 6 for 3×3)
                : builder.matrixSize * epl;       // ~half density
        }

        // Operator filtering
        this.allowedOperators       = builder.allowedOperators == null
                                    ? null
                                    : Collections.unmodifiableSet(new LinkedHashSet<>(builder.allowedOperators));

        // Result chaining
        this.resultChainingPlayable = builder.resultChainingPlayable;

        // Masking / grading
        this.minVisibilityPercent   = builder.minVisibilityPercent;
        this.maxVisibilityPercent   = builder.maxVisibilityPercent;
        this.operatorsCanBeHidden   = builder.operatorsCanBeHidden;
        this.resultsCanBeHidden     = builder.resultsCanBeHidden;
        this.maxUnknownsPerEquation = builder.maxUnknownsPerEquation;
        this.numChoices             = builder.numChoices;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format(
            "PuzzleConfig{matrixSize=%d, cellValues=[%d..%d], targetArms=%d, " +
            "maxSeedValue=%d, numBrackets=%d, minUsagePerOperator=%d, " +
            "allowedOperators=%s}",
            matrixSize, minCellValue, maxCellValue, targetEquationCount,
            maxSeedValue, numBrackets, minUsagePerOperator,
            allowedOperators == null ? "all" : allowedOperators);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {

        private int matrixSize            = 5;
        private int minCellValue          = 1;
        private int maxCellValue          = 100;
        private int maxGenerationAttempts = 1000;
        private int minUsagePerOperator   = 2;
        private int numBrackets           = 4;

        // Per-operator caps: -1 means "derive from maxCellValue"
        private int maxAddOperand         = -1;
        private int maxMultiplyOperand    = -1;
        private int maxDivisionDivisor    = -1;
        private int maxSeedValue          = -1;

        // Shape generation
        private int targetEquationCount   = -1;  // -1 = auto-compute

        // Operator filtering
        private Set<Character> allowedOperators = null;

        // Result chaining
        private boolean resultChainingPlayable = false;

        // Masking / grading defaults
        private double  minVisibilityPercent   = 0.0;
        private double  maxVisibilityPercent   = 1.0;
        private boolean operatorsCanBeHidden   = true;
        private boolean resultsCanBeHidden     = true;
        private int     maxUnknownsPerEquation = Integer.MAX_VALUE;
        private int     numChoices             = 4;

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

        /**
         * Override the maximum operand for addition/subtraction.
         * Default: derived as {@code maxCellValue / 2}.
         * Grading example: {@code maxAddOperand(25)} limits addition to sums ≤ 50.
         */
        public Builder maxAddOperand(int maxAddOperand) {
            this.maxAddOperand = maxAddOperand;
            return this;
        }

        /**
         * Override the maximum operand for multiplication.
         * Default: derived as {@code ⌊√maxCellValue⌋}.
         * Grading example: {@code maxMultiplyOperand(2)} restricts to the 2× table.
         */
        public Builder maxMultiplyOperand(int maxMultiplyOperand) {
            this.maxMultiplyOperand = maxMultiplyOperand;
            return this;
        }

        /**
         * Override the maximum divisor for division.
         * Default: derived as {@code maxCellValue / 2}.
         * Grading example: {@code maxDivisionDivisor(5)} for simple division.
         */
        public Builder maxDivisionDivisor(int maxDivisionDivisor) {
            this.maxDivisionDivisor = maxDivisionDivisor;
            return this;
        }

        /**
         * Maximum value for directly introduced sparse-shape numbers.
         * Default: {@code maxAddOperand}, which is typically {@code maxCellValue / 2}.
         */
        public Builder maxSeedValue(int maxSeedValue) {
            this.maxSeedValue = maxSeedValue;
            return this;
        }

        /**
         * Target number of equation arms for shape generation.
         * Default: auto-computed (dense for matrixSize=3, sparse for larger).
         */
        public Builder targetEquationCount(int targetEquationCount) {
            this.targetEquationCount = targetEquationCount;
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

        /**
         * Restrict which operators are used. Pass {@code null} for all registered.
         * <pre>
         *   .allowedOperators(Set.of('+'))           // addition only (level 0)
         *   .allowedOperators(Set.of('+', '-'))      // level 1.5
         *   .allowedOperators(Set.of('+', '-', '*')) // level 3
         *   .allowedOperators(null)                  // all registered
         * </pre>
         */
        public Builder allowedOperators(Set<Character> allowedOperators) {
            this.allowedOperators = allowedOperators;
            return this;
        }

        /**
         * Whether {@code a op b = c op d = e} chains are displayed to the player.
         * Default: {@code false} — the masking layer hides chained equations.
         * The dense matrix always generates them internally as valid combinations.
         */
        public Builder resultChainingPlayable(boolean resultChainingPlayable) {
            this.resultChainingPlayable = resultChainingPlayable;
            return this;
        }

        /**
         * Minimum percentage of cells that must remain visible (0.0–1.0).
         * Grading: level 0 uses 0.85–0.95; level 6+ uses 0.20–0.35.
         */
        public Builder minVisibilityPercent(double minVisibilityPercent) {
            this.minVisibilityPercent = minVisibilityPercent;
            return this;
        }

        /**
         * Maximum percentage of cells visible (0.0–1.0).
         * Combined with {@code minVisibilityPercent} defines the difficulty band.
         */
        public Builder maxVisibilityPercent(double maxVisibilityPercent) {
            this.maxVisibilityPercent = maxVisibilityPercent;
            return this;
        }

        /**
         * Whether operator symbols can be hidden from the player.
         * Early levels (0–2.5) keep operators always visible.
         */
        public Builder operatorsCanBeHidden(boolean operatorsCanBeHidden) {
            this.operatorsCanBeHidden = operatorsCanBeHidden;
            return this;
        }

        /**
         * Whether result cells can be hidden from the player.
         * Early levels keep results always or mostly visible.
         */
        public Builder resultsCanBeHidden(boolean resultsCanBeHidden) {
            this.resultsCanBeHidden = resultsCanBeHidden;
            return this;
        }

        /**
         * Maximum unknowns allowed in a single equation (default: no limit).
         * Early levels allow only 1 unknown per equation.
         */
        public Builder maxUnknownsPerEquation(int maxUnknownsPerEquation) {
            this.maxUnknownsPerEquation = maxUnknownsPerEquation;
            return this;
        }

        /**
         * Number of multiple-choice answer options (default 4).
         * Level 0–1: 3 choices; level 3+: 4; level 5+: 5.
         */
        public Builder numChoices(int numChoices) {
            this.numChoices = numChoices;
            return this;
        }

        public PuzzleConfig build() {
            return new PuzzleConfig(this);
        }
    }
}
