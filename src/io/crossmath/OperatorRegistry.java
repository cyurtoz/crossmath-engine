package io.crossmath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/**
 * Central registry of all arithmetic operators available in a puzzle.
 *
 * <h2>Data structure: LinkedHashMap&lt;Character, Operator&gt;</h2>
 *
 * A plain {@code List<Operator>} was used before.  It had two problems:
 * <ol>
 *   <li>{@code remove(char)} required an O(n) scan via {@code removeIf}.</li>
 *   <li>Nothing prevented registering two operators with the same symbol
 *       (e.g. two {@code '+'} operators), which would silently corrupt usage
 *       counts and printouts.</li>
 * </ol>
 *
 * {@code LinkedHashMap<Character, Operator>} fixes both:
 * <ul>
 *   <li>{@code remove(char)} is O(1) map removal by key.</li>
 *   <li>Duplicate symbols are rejected at registration time with a clear error.</li>
 *   <li>Insertion order is preserved so {@link #all()} and the usage-bias sort
 *       remain stable and deterministic across runs with the same seed.</li>
 * </ul>
 *
 * <h2>Adding custom operators</h2>
 * <pre>{@code
 *   registry.add(new MinOperator());
 *   registry.add(new ExpOperator());
 *   registry.remove('/');    // disable division for simpler puzzles
 * }</pre>
 */
public class OperatorRegistry {

    /** Insertion-ordered map from symbol → operator. Preserves registration order. */
    private final LinkedHashMap<Character, Operator> operatorMap = new LinkedHashMap<>();

    private final PuzzleConfig config;
    private final Random       random;

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * Creates a registry pre-loaded with the four standard operators.
     *
     * @param config shared puzzle configuration
     * @param random shared random source — pass the same instance used by the
     *               generator to guarantee fully deterministic, seed-reproducible output
     */
    public OperatorRegistry(PuzzleConfig config, Random random) {
        this.config = config;
        this.random = random;

        register(new AddOperator());
        register(new SubtractOperator());
        register(new MultiplyOperator(config.maxCellValue));
        register(new DivideOperator());
    }

    // ── Registry mutation ─────────────────────────────────────────────────────

    /**
     * Adds a custom operator.
     *
     * @throws IllegalArgumentException if an operator with the same symbol
     *                                  is already registered
     */
    public void add(Operator operator) {
        if (operatorMap.containsKey(operator.symbol())) {
            throw new IllegalArgumentException(
                "Operator '" + operator.symbol() + "' is already registered. " +
                "Call remove('" + operator.symbol() + "') first.");
        }
        operatorMap.put(operator.symbol(), operator);
    }

    /** Removes the operator with the given symbol in O(1). No-op if not found. */
    public void remove(char symbol) {
        operatorMap.remove(symbol);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Returns the number of registered operators. */
    public int size() {
        return operatorMap.size();
    }

    /**
     * Returns all operators in registration order.
     * A new list is returned on each call so callers may freely mutate it
     * (e.g. for shuffling).
     */
    public List<Operator> all() {
        return new ArrayList<>(operatorMap.values());
    }

    /**
     * Tries all operators in shuffled order and returns the first that
     * produces a valid result for ({@code leftOperand}, {@code rightOperand}).
     *
     * @return an {@link OperatorResult} pairing the operator with its result,
     *         or {@code null} if no operator can produce a valid result
     */
    public OperatorResult applyRandom(int leftOperand, int rightOperand) {
        for (Operator candidate : shuffledOperators()) {
            int result = candidate.apply(leftOperand, rightOperand, config);
            if (result != Integer.MIN_VALUE) {
                return new OperatorResult(candidate, result);
            }
        }
        return null;
    }

    /**
     * Finds an operator such that {@code operator.apply(left, right) == targetResult}.
     * Tries all operators in shuffled order.
     *
     * @return the matching operator, or {@code null} if none can produce the target
     */
    public Operator findOperatorForResult(int leftOperand, int rightOperand, int targetResult) {
        for (Operator candidate : shuffledOperators()) {
            if (candidate.apply(leftOperand, rightOperand, config) == targetResult) {
                return candidate;
            }
        }
        return null;
    }

    /** Prints all registered operators with their constraints to stdout. */
    public void printSummary() {
        System.out.println("  Registered operators:");
        for (Operator operator : operatorMap.values()) {
            System.out.printf("    '%c'  —  %s%n", operator.symbol(), operator.constraints());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Internal registration used by the constructor — skips the duplicate check. */
    private void register(Operator operator) {
        operatorMap.put(operator.symbol(), operator);
    }

    private List<Operator> shuffledOperators() {
        List<Operator> shuffled = all();
        Collections.shuffle(shuffled, random);
        return shuffled;
    }

    // ── Value object ──────────────────────────────────────────────────────────

    /**
     * Pairs a winning operator with the valid result it produced.
     * Returned by {@link #applyRandom}.
     */
    public record OperatorResult(Operator operator, int result) {}
}