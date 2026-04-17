package io.crossmath.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/**
 * Central registry of all arithmetic operators available in a puzzle.
 *
 * <h2>Data structure: LinkedHashMap&lt;Character, Operator&gt;</h2>
 * Provides O(1) lookup and removal by symbol, enforces symbol uniqueness at
 * registration time, and preserves insertion order for stable deterministic
 * iteration across seeds.
 *
 * <h2>Adding custom operators</h2>
 * <pre>{@code
 *   registry.add(new MinOperator());
 *   registry.add(new ExpOperator());
 *   registry.remove('/');   // disable division for simpler puzzles
 * }</pre>
 */
public class OperatorRegistry {

    private final LinkedHashMap<Character, Operator> operatorMap = new LinkedHashMap<>();

    private final PuzzleConfig config;
    private final Random       random;

    // ── Construction ──────────────────────────────────────────────────────────

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
     * A new list is returned on each call so callers may freely mutate it.
     */
    public List<Operator> all() {
        return new ArrayList<>(operatorMap.values());
    }

    /**
     * Tries all operators in shuffled order; returns the first that produces
     * a valid result for ({@code leftOperand}, {@code rightOperand}), or
     * {@code null} if none applies.
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
     * Finds an operator such that {@code apply(left, right) == targetResult}.
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

    private void register(Operator operator) {
        operatorMap.put(operator.symbol(), operator);
    }

    private List<Operator> shuffledOperators() {
        List<Operator> shuffled = all();
        Collections.shuffle(shuffled, random);
        return shuffled;
    }

    // ── Value object ──────────────────────────────────────────────────────────

    public record OperatorResult(Operator operator, int result) {}
}
