package io.crossmath.engine;

import java.util.Random;

/**
 * Picks integer values with even distribution across a range.
 *
 * <h2>How it works</h2>
 *
 * The range [{@code minValue}, {@code maxValue}] is divided into
 * {@code numBrackets} equal segments.  Picks cycle round-robin through the
 * segments, so small, medium, and large values all appear roughly equally
 * often rather than clustering near the bottom.
 *
 * <h2>Automatic fallback to uniform random</h2>
 *
 * Brackets only add value when each segment contains at least 2 distinct
 * numbers — otherwise every pick within a segment is deterministic and the
 * "variety" guarantee is meaningless.
 *
 * <p>If {@code rangeSize < 2 × numBrackets} the picker automatically reduces
 * {@code numBrackets} to {@code max(1, rangeSize / 2)}.  In the extreme case
 * where the entire range contains only one value, a single bracket is used and
 * every call returns that value — which is the only correct behaviour.
 *
 * <p>This means callers never need to check whether brackets are feasible for
 * a given range: the picker self-adjusts silently.
 *
 * <h2>Example — 4 brackets over [1, 50], rangeSize=50</h2>
 * <pre>
 *   50 / 2 = 25 ≥ 4  →  brackets kept
 *   Bracket 0: [ 1–13]
 *   Bracket 1: [14–25]
 *   Bracket 2: [26–38]
 *   Bracket 3: [39–50]
 * </pre>
 *
 * <h2>Example — 4 brackets over [1, 10], rangeSize=10</h2>
 * <pre>
 *   10 / 2 = 5 ≥ 4  →  brackets kept
 *   Bracket 0: [1–3]
 *   Bracket 1: [4–5]
 *   Bracket 2: [6–8]
 *   Bracket 3: [9–10]
 * </pre>
 *
 * <h2>Example — 4 brackets over [1, 5], rangeSize=5</h2>
 * <pre>
 *   5 / 2 = 2 &lt; 4  →  reduced to 2 brackets
 *   Bracket 0: [1–3]
 *   Bracket 1: [4–5]
 * </pre>
 *
 * <h2>Example — 4 brackets over [1, 1], rangeSize=1</h2>
 * <pre>
 *   1 / 2 = 0  →  reduced to 1 bracket (uniform, always returns 1)
 * </pre>
 */
public class BracketedPicker {

    private final int[]  bracketLow;
    private final int[]  bracketHigh;
    private final int    effectiveBrackets;
    private final Random random;
    private       int    currentBracket = 0;

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * @param minValue    inclusive lower bound of the range
     * @param maxValue    inclusive upper bound of the range (clamped to ≥ minValue)
     * @param numBrackets desired number of segments; automatically reduced if the
     *                    range is too small to give each segment at least 2 values
     * @param random      shared random source
     */
    public BracketedPicker(int minValue, int maxValue, int numBrackets, Random random) {
        this.random = random;

        int rangeSize = Math.max(1, maxValue - minValue + 1);

        // Each bracket needs at least 2 values to provide any randomness.
        // Reduce numBrackets until that condition holds, down to a minimum of 1.
        int feasibleBrackets = Math.max(1, Math.min(numBrackets, rangeSize / 2));
        this.effectiveBrackets = feasibleBrackets;

        this.bracketLow  = new int[effectiveBrackets];
        this.bracketHigh = new int[effectiveBrackets];

        int baseWidth = rangeSize / effectiveBrackets;
        int remainder = rangeSize % effectiveBrackets;

        int cursor = minValue;
        for (int i = 0; i < effectiveBrackets; i++) {
            bracketLow[i]  = cursor;
            int width      = baseWidth + (i < remainder ? 1 : 0);
            bracketHigh[i] = cursor + width - 1;
            cursor        += width;
        }
    }

    // ── Picking ───────────────────────────────────────────────────────────────

    /**
     * Returns a uniformly random value from the current bracket and advances
     * to the next bracket (wrapping round-robin).
     */
    public int next() {
        int low  = bracketLow[currentBracket];
        int high = bracketHigh[currentBracket];
        currentBracket = (currentBracket + 1) % effectiveBrackets;

        if (low >= high) {
            return low;
        }
        return low + random.nextInt(high - low + 1);
    }

    /**
     * Returns the number of brackets actually in use (may be less than the
     * requested {@code numBrackets} if the range was too small).
     */
    public int effectiveBrackets() {
        return effectiveBrackets;
    }

    /** Diagnostic summary, e.g. {@code "Brackets(4): [1–13] [14–25] [26–38] [39–50]"}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Brackets(")
            .append(effectiveBrackets).append("): ");
        for (int i = 0; i < effectiveBrackets; i++) {
            sb.append("[").append(bracketLow[i]).append("–")
              .append(bracketHigh[i]).append("] ");
        }
        return sb.toString().trim();
    }
}