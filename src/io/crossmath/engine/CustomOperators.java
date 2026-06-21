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
        for (int right = config.minCellValue; right <= config.maxCellValue; right++) {
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
        for (int right = config.minCellValue; right <= config.maxCellValue; right++) {
            if (right != leftOperand) valid.add(right);
        }
        Collections.shuffle(valid, random);
        return valid;
    }

    @Override
    public String constraints() { return "leftOperand ≠ rightOperand;  result = max(left, right)"; }
}

// ── AVG ───────────────────────────────────────────────────────────────────────

/**
 * Integer average:  avg(leftOperand, rightOperand) = (left + right) / 2
 * Constraint: leftOperand + rightOperand must be even; leftOperand ≠ rightOperand.
 */
class AvgOperator implements Operator {

    @Override
    public char symbol() { return 'a'; }

    @Override
    public int apply(int leftOperand, int rightOperand, PuzzleConfig config) {
        if (leftOperand == rightOperand) return Integer.MIN_VALUE;
        int sum = leftOperand + rightOperand;
        if (sum % 2 != 0) return Integer.MIN_VALUE;
        int avg = sum / 2;
        if (avg < config.minCellValue || avg > config.maxCellValue) {
            return Integer.MIN_VALUE;
        }
        return avg;
    }

    @Override
    public List<Integer> validRightOperands(int leftOperand, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();
        for (int right = config.minCellValue; right <= config.maxCellValue; right++) {
            if (right != leftOperand && (leftOperand + right) % 2 == 0) {
                int avg = (leftOperand + right) / 2;
                if (avg >= config.minCellValue && avg <= config.maxCellValue) {
                    valid.add(right);
                }
            }
        }
        Collections.shuffle(valid, random);
        return valid;
    }

    @Override
    public String constraints() { return "leftOperand ≠ rightOperand;  (left + right) is even;  result = avg(left, right)"; }
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

    @Override
    public List<Integer> validRightOperands(int radicand, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();
        if (apply(radicand, 0, config) != Integer.MIN_VALUE) {
            valid.add(config.minCellValue);
        }
        return valid;
    }

    @Override
    public String constraints() { return "radicand is a perfect square;  √radicand ≥ 2"; }
}

// ── MODULO ────────────────────────────────────────────────────────────────────

/**
 * Modulo:  leftOperand % rightOperand = result
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>{@code rightOperand ≥ 2} — avoids division by zero and trivial {@code n % 1 = 0}.</li>
 *   <li>{@code leftOperand > rightOperand} — otherwise result equals leftOperand (trivial).</li>
 *   <li>{@code leftOperand % rightOperand ≠ 0} — avoids exact-divisibility (overlaps with ÷).</li>
 *   <li>{@code result ≥ minCellValue} and {@code result ≤ maxCellValue}.</li>
 * </ul>
 */
class ModuloOperator implements Operator {

    @Override
    public char symbol() { return '%'; }

    @Override
    public int apply(int leftOperand, int rightOperand, PuzzleConfig config) {
        if (rightOperand < 2) return Integer.MIN_VALUE;
        if (leftOperand <= rightOperand) return Integer.MIN_VALUE;
        int result = leftOperand % rightOperand;
        if (result == 0) return Integer.MIN_VALUE;
        if (result < config.minCellValue || result > config.maxCellValue) {
            return Integer.MIN_VALUE;
        }
        return result;
    }

    @Override
    public List<Integer> validRightOperands(int leftOperand, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();
        int maxRight = Math.min(leftOperand - 1, config.maxCellValue);
        for (int right = 2; right <= maxRight; right++) {
            if (leftOperand % right != 0) {
                int result = leftOperand % right;
                if (result >= config.minCellValue && result <= config.maxCellValue) {
                    valid.add(right);
                }
            }
        }
        Collections.shuffle(valid, random);
        return valid;
    }

    @Override
    public String constraints() {
        return "rightOperand ≥ 2;  leftOperand > rightOperand;  leftOperand % rightOperand ≠ 0";
    }
}

// ── LOG ───────────────────────────────────────────────────────────────────────

/**
 * Integer logarithm:  log_base(argument) = exponent, i.e. base^exponent = argument.
 *
 * <p>In the equation grid: {@code base  L  argument  =  exponent}.
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>{@code base ≥ 2} — log base 0 or 1 is undefined/degenerate.</li>
 *   <li>{@code argument} must be an exact power of {@code base}.</li>
 *   <li>{@code exponent ≥ 2} — avoids trivial {@code log_b(b) = 1} and {@code log_b(1) = 0}.</li>
 *   <li>{@code exponent ≤ maxCellValue}.</li>
 * </ul>
 */
class LogOperator implements Operator {

    @Override
    public char symbol() { return 'L'; }

    @Override
    public int apply(int base, int argument, PuzzleConfig config) {
        if (base < 2 || argument < config.minCellValue) return Integer.MIN_VALUE;
        int exponent = 0;
        long power = 1;
        while (power < argument) {
            power *= base;
            exponent++;
            if (power > config.maxCellValue) return Integer.MIN_VALUE;
        }
        if (power != argument) return Integer.MIN_VALUE;
        if (exponent < 2) return Integer.MIN_VALUE;
        return (exponent <= config.maxCellValue) ? exponent : Integer.MIN_VALUE;
    }

    /**
     * For a given base, enumerates all arguments (powers of base) whose
     * logarithm ≥ 2 and fits within maxCellValue.
     */
    @Override
    public List<Integer> validRightOperands(int base, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();
        if (base < 2) return valid;
        long power = (long) base * base; // start at base^2 (exponent = 2)
        while (power <= config.maxCellValue) {
            if (power >= config.minCellValue) {
                valid.add((int) power);
            }
            power *= base;
        }
        Collections.shuffle(valid, random);
        return valid;
    }

    @Override
    public String constraints() {
        return "base ≥ 2;  argument = base^exponent;  exponent ≥ 2";
    }
}
