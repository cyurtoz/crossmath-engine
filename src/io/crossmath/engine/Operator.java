package io.crossmath.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Strategy interface for a single arithmetic operation.
 *
 * <h2>Extension contract</h2>
 * To add a new operator: implement this interface and register via
 * {@link OperatorRegistry#add}. No other class needs to change.
 *
 * <h2>Two methods, one contract</h2>
 * <ul>
 *   <li>{@link #apply} — validates and computes the result for a given pair;
 *       returns {@code Integer.MIN_VALUE} on any constraint violation.</li>
 *   <li>{@link #validRightOperands} — returns every right operand that passes
 *       {@code apply} for a given left operand, shuffled. The generator
 *       iterates the full list before giving up on an operator.</li>
 * </ul>
 */
public interface Operator {

    /** Single-character symbol shown in the puzzle grid (e.g. {@code '+'}). */
    char symbol();

    /**
     * Applies this operator and validates all constraints.
     *
     * @param leftOperand  left side of the equation
     * @param rightOperand right side (ignored when {@link #isUnary()} is true)
     * @param config       active puzzle configuration
     * @return             the valid result, or {@code Integer.MIN_VALUE} on any violation
     */
    int apply(int leftOperand, int rightOperand, PuzzleConfig config);

    /**
     * Returns every right operand that passes {@link #apply} for this
     * {@code leftOperand}, in shuffled order.
     *
     * <p>Operators with a computable valid domain should enumerate it directly
     * (exact, no misses). The default scans {@code [minCellValue, maxAddOperand]}
     * which is a reasonable fallback for operators with no tighter structure.
     */
    default List<Integer> validRightOperands(int leftOperand, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();
        for (int candidate = config.minCellValue; candidate <= config.maxAddOperand; candidate++) {
            if (apply(leftOperand, candidate, config) != Integer.MIN_VALUE) {
                valid.add(candidate);
            }
        }
        Collections.shuffle(valid, random);
        return valid;
    }

    /** Human-readable description of this operator's constraints. */
    String constraints();

    /**
     * Returns {@code true} if this operator takes only one operand (e.g. SQRT).
     * Default: {@code false}.
     */
    default boolean isUnary() {
        return false;
    }
}
