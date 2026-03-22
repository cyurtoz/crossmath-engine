package io.crossmath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Addition:  leftOperand + rightOperand = sum
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>Both operands ≥ {@code minCellValue}.</li>
 *   <li>Sum ∈ [{@code minCellValue}, {@code maxCellValue}].</li>
 * </ul>
 *
 * <p>The {@code maxAddOperand} cap is applied when picking fresh free operands
 * (via {@link #validRightOperands}) to keep sums in a reasonable range.
 * It is intentionally absent from {@link #apply} so that chained result values
 * larger than {@code maxAddOperand} are not rejected when they appear as left
 * operands of subsequent equations.
 */
public class AddOperator implements Operator {

    @Override
    public char symbol() {
        return '+';
    }

    @Override
    public int apply(int leftOperand, int rightOperand, PuzzleConfig config) {
        if (leftOperand  < config.minCellValue) return Integer.MIN_VALUE;
        if (rightOperand < config.minCellValue) return Integer.MIN_VALUE;

        int sum = leftOperand + rightOperand;

        if (sum < config.minCellValue || sum > config.maxCellValue) {
            return Integer.MIN_VALUE;
        }
        return sum;
    }

    /**
     * Enumerates all right operands in [{@code minCellValue},
     * min({@code maxAddOperand}, {@code maxCellValue − leftOperand})]
     * so every returned value produces a sum within bounds.
     */
    @Override
    public List<Integer> validRightOperands(int leftOperand, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();

        int maxRight = Math.min(config.maxCellValue - leftOperand, config.maxAddOperand);

        for (int right = config.minCellValue; right <= maxRight; right++) {
            valid.add(right);
        }

        Collections.shuffle(valid, random);
        return valid;
    }

    @Override
    public String constraints() {
        return "both operands ≥ minCellValue;  sum ∈ [minCellValue, maxCellValue]";
    }
}
