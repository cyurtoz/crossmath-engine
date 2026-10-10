package io.crossmath.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Ready-to-activate operator extensions.
 *
 * <p>To enable any of them, add an instance to the registry in {@code CrossMathMain}:
 * <pre>{@code
 *   registry.add(new MinOperator());
 *   registry.add(new MaxOperator());
 *   registry.add(new ExpOperator());
 *   registry.add(new SqrtOperator());
 *   registry.remove('/');   // disable division for simpler puzzles
 * }</pre>
 *
 * Each operator overrides {@link Operator#validRightOperands} to enumerate
 * its exact valid domain rather than falling back to the default scan.
 */

// ── MIN ────────────────────────────────────────────────────────────────────────

/**
 * Minimum:  min(leftOperand, rightOperand) = result
 * Constraint: leftOperand ≠ rightOperand — min(x, x) = x is trivial.
 */
class MinOperator implements Operator {

    @Override
    public char symbol() { return 'm'; }

    @Override
    public int apply(int leftOperand, int rightOperand, PuzzleConfig config) {
        if (leftOperand == rightOperand) return Integer.MIN_VALUE;
        int minimum = Math.min(leftOperand, rightOperand);
        if (minimum < config.minCellValue || minimum > config.maxCellValue) {
            return Integer.MIN_VALUE;
        }
        return minimum;
    }

    /** All values in [minCellValue, maxCellValue] except leftOperand itself. */
    @Override
    public List<Integer> validRightOperands(int leftOperand, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();
        for (int right = config.minCellValue; right <= config.maxAddOperand; right++) {
            if (right != leftOperand) valid.add(right);
        }
        Collections.shuffle(valid, random);
        return valid;
    }

    @Override
    public String constraints() { return "leftOperand ≠ rightOperand;  result = min(left, right)"; }
}

// ── MAX ────────────────────────────────────────────────────────────────────────

/**
 * Maximum:  max(leftOperand, rightOperand) = result
 * Constraint: leftOperand ≠ rightOperand.
 */
class MaxOperator implements Operator {

    @Override
    public char symbol() { return 'M'; }

    @Override
    public int apply(int leftOperand, int rightOperand, PuzzleConfig config) {
        if (leftOperand == rightOperand) return Integer.MIN_VALUE;
        int maximum = Math.max(leftOperand, rightOperand);
        if (maximum < config.minCellValue || maximum > config.maxCellValue) {
            return Integer.MIN_VALUE;
        }
        return maximum;
    }

    /** All values in [minCellValue, maxAddOperand] except leftOperand itself. */
    @Override
    public List<Integer> validRightOperands(int leftOperand, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();
        for (int right = config.minCellValue; right <= config.maxAddOperand; right++) {
            if (right != leftOperand) valid.add(right);
        }
        Collections.shuffle(valid, random);
        return valid;
    }

    @Override
    public String constraints() { return "leftOperand ≠ rightOperand;  result = max(left, right)"; }
}

// ── EXP ────────────────────────────────────────────────────────────────────────

/**
 * Exponentiation:  base ^ exponent = power
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>{@code base ≥ 2} — avoids trivial 1^n = 1.</li>
 *   <li>{@code exponent ∈ [2, MAX_EXPONENT]} — small exponents only.</li>
 *   <li>{@code power ≤ maxCellValue}.</li>
 * </ul>
 */
class ExpOperator implements Operator {

    private static final int MAX_EXPONENT = 4;

    @Override
    public char symbol() { return '^'; }

    @Override
    public int apply(int base, int exponent, PuzzleConfig config) {
        if (base < 2 || exponent < 2 || exponent > MAX_EXPONENT) {
            return Integer.MIN_VALUE;
        }
        long power = 1;
        for (int i = 0; i < exponent; i++) {
            power *= base;
            if (power > config.maxCellValue) return Integer.MIN_VALUE;
        }
        int result = (int) power;
        return (result >= config.minCellValue) ? result : Integer.MIN_VALUE;
    }

    /** Enumerates all exponents in [2, MAX_EXPONENT] that keep the power in range. */
    @Override
    public List<Integer> validRightOperands(int base, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();
        if (base < 2) return valid;
        for (int exponent = 2; exponent <= MAX_EXPONENT; exponent++) {
            if (apply(base, exponent, config) != Integer.MIN_VALUE) {
                valid.add(exponent);
            }
        }
        Collections.shuffle(valid, random);
        return valid;
    }

    @Override
    public String constraints() {
        return "base ≥ 2;  exponent ∈ [2, " + MAX_EXPONENT + "];  power ≤ maxCellValue";
    }
}

// ── SQRT ───────────────────────────────────────────────────────────────────────

/**
 * Integer square root:  √radicand = root  (unary — rightOperand is ignored)
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>{@code radicand} must be a perfect square.</li>
 *   <li>{@code root ≥ 2} — avoids trivial √1 = 1 and √0 = 0.</li>
 * </ul>
 */
class SqrtOperator implements Operator {

    @Override
    public char symbol() { return 'r'; }

    @Override
    public boolean isUnary() { return true; }

    @Override
    public int apply(int radicand, int ignoredRightOperand, PuzzleConfig config) {
        if (radicand < config.minCellValue || radicand > config.maxCellValue) {
            return Integer.MIN_VALUE;
        }
        int root = (int) Math.sqrt(radicand);
        if ((long) root * root != radicand) return Integer.MIN_VALUE;
        return (root >= 2) ? root : Integer.MIN_VALUE;
    }

    /**
     * Unary — returns a single-element list with {@code 0} as the conventional
     * right operand placeholder, only when the left operand is a perfect square.
     */
    @Override
    public List<Integer> validRightOperands(int radicand, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();
        if (apply(radicand, 0, config) != Integer.MIN_VALUE) {
            valid.add(0);   // convention: 0 as the ignored right operand for unary ops
        }
        return valid;
    }

    @Override
    public String constraints() { return "radicand is a perfect square;  √radicand ≥ 2"; }
}
