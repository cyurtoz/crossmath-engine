package io.crossmath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Division:  leftOperand ÷ rightOperand = quotient  (exact integer only)
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>{@code rightOperand ≥ 2} — eliminates trivial ÷1 equations.</li>
 *   <li>{@code rightOperand ≤ maxDivisionDivisor} (= {@code maxCellValue / 2}).</li>
 *   <li>{@code leftOperand % rightOperand == 0} — exact integer division only.</li>
 *   <li>{@code quotient ≥ minCellValue}.</li>
 * </ul>
 */
public class DivideOperator implements Operator {

    @Override
    public char symbol() {
        return '/';
    }

    @Override
    public int apply(int leftOperand, int rightOperand, PuzzleConfig config) {
        if (rightOperand < 2 || rightOperand > config.maxDivisionDivisor) {
            return Integer.MIN_VALUE;
        }
        if (leftOperand % rightOperand != 0) {
            return Integer.MIN_VALUE;
        }

        int quotient = leftOperand / rightOperand;

        if (quotient < config.minCellValue || quotient > config.maxCellValue) {
            return Integer.MIN_VALUE;
        }
        return quotient;
    }

    /**
     * Enumerates all exact divisors of {@code leftOperand} in
     * [2, {@code maxDivisionDivisor}] whose quotient is within bounds.
     *
     * <p>This is the complete valid domain — no sampling, no misses.
     * For {@code leftOperand=12}, returns all of {@code [2, 3, 4, 6]}
     * (shuffled), giving the caller the full set to try before giving up.
     */
    @Override
    public List<Integer> validRightOperands(int leftOperand, PuzzleConfig config, Random random) {
        List<Integer> validDivisors = new ArrayList<>();

        int upperBound = Math.min(leftOperand, config.maxDivisionDivisor);

        for (int divisor = 2; divisor <= upperBound; divisor++) {
            if (leftOperand % divisor != 0) {
                continue;
            }
            int quotient = leftOperand / divisor;
            if (quotient >= config.minCellValue && quotient <= config.maxCellValue) {
                validDivisors.add(divisor);
            }
        }

        Collections.shuffle(validDivisors, random);
        return validDivisors;
    }

    @Override
    public String constraints() {
        return "rightOperand ∈ [2, maxCellValue/2];  exact division only;  quotient ≥ minCellValue";
    }
}
