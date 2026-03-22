package io.crossmath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Strategy interface for a single arithmetic operation.
 *
 * <h2>Extension contract</h2>
 * To add a new operator (MIN, MAX, EXP, SQRT, …):
 * <ol>
 *   <li>Create a class that implements this interface.</li>
 *   <li>Register an instance via {@link OperatorRegistry#add}.</li>
 * </ol>
 * No other class needs to change.
 *
 * <h2>Unary operators (e.g. SQRT)</h2>
 * Override {@link #isUnary()} to return {@code true}.
 * By convention, {@code rightOperand = 0} is passed for unary operations;
 * the implementation ignores it.
 *
 * <h2>Operator-first generation</h2>
 * The generator picks the most underused operator first, then calls
 * {@link #validRightOperands} to get the full list of right operands that
 * produce a valid result for a given left operand.  The caller iterates the
 * entire list before giving up on the operator.
 *
 * <p>Why a list instead of a single value: returning one value means that if
 * it fails to connect the horizontal equation chain, the caller abandons the
 * operator entirely and falls back to the next one.  But there may be several
 * valid right operands for that operator, and a different one might have
 * worked.  Returning the full list exhausts all possibilities before giving up.
 *
 * <p>Example — division with {@code leftValue=12}, {@code maxDivisor=50}:
 * {@code validRightOperands} returns {@code [3, 6, 2, 4]} (shuffled).
 * If {@code 12/3=4} fails the chain, the caller still tries {@code 12/6=2},
 * {@code 12/2=6}, {@code 12/4=3} before abandoning division.
 */
public interface Operator {

    /**
     * Single-character symbol shown in the puzzle grid (e.g. {@code '+'}).
     */
    char symbol();

    /**
     * Applies this operator to ({@code leftOperand}, {@code rightOperand}) and
     * validates all constraints owned by this operator.
     *
     * @param leftOperand  left side of the equation
     * @param rightOperand right side (ignored when {@link #isUnary()} is true)
     * @param config       active puzzle configuration supplying derived limits
     * @return             the valid result, or {@code Integer.MIN_VALUE} if any
     *                     constraint is violated or the operation is undefined
     */
    int apply(int leftOperand, int rightOperand, PuzzleConfig config);

    /**
     * Returns all right operands that produce a valid result when combined with
     * {@code leftOperand} under this operator, in a shuffled order.
     *
     * <p>The default implementation scans the additive range and collects all
     * candidates that pass {@link #apply}.  Operators with a computable valid
     * domain (divide, multiply) should override this to enumerate that domain
     * directly rather than scanning — it is both faster and more complete.
     *
     * @param leftOperand left operand of this equation (already fixed)
     * @param config      active puzzle configuration
     * @param random      shared random source used only for shuffling
     * @return            shuffled list of all valid right operands; empty if none
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

    /**
     * Human-readable description of this operator's constraints.
     */
    String constraints();

    /**
     * Returns {@code true} if this operator takes only one operand (e.g. SQRT).
     * Default: {@code false}.
     */
    default boolean isUnary() {
        return false;
    }
}
