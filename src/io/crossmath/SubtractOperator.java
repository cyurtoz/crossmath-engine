package io.crossmath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Subtraction:  leftOperand − rightOperand = difference
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>{@code difference ≥ minCellValue} — no negative or zero results;
 *       implies {@code leftOperand > rightOperand}.</li>
 *   <li>{@code difference ≤ maxCellValue} — always satisfied when
 *       {@code leftOperand ≤ maxCellValue}.</li>
 * </ul>
 */
public class SubtractOperator implements Operator {

    @Override
    public char symbol() {
        return '-';
    }

    @Override
    public int apply(int leftOperand, int rightOperand, PuzzleConfig config) {
        int difference = leftOperand - rightOperand;

        if (difference < config.minCellValue || difference > config.maxCellValue) {
            return Integer.MIN_VALUE;
        }
        return difference;
    }

    /**
     * Enumerates all right operands in [{@code minCellValue},
     * {@code leftOperand − minCellValue}] so the difference is
     * guaranteed to be ≥ {@code minCellValue}.
     */
    @Override
    public List<Integer> validRightOperands(int leftOperand, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();

        // rightOperand must be strictly less than leftOperand so difference ≥ minCellValue
        int maxRight = leftOperand - config.minCellValue;

        for (int right = config.minCellValue; right <= maxRight; right++) {
            valid.add(right);
        }

        Collections.shuffle(valid, random);
        return valid;
    }

    @Override
    public String constraints() {
        return "difference ≥ minCellValue  (no negatives, no zero; leftOperand > rightOperand)";
    }
}
