package io.crossmath.engine;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Multiplication:  leftOperand × rightOperand = product
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>Both operands ≥ 2 — eliminates trivial ×1 equations.</li>
 *   <li>Product ≤ {@code maxCellValue}.</li>
 *   <li>Product must not be prime — a prime product has exactly one non-trivial
 *       factorisation, making any paired ÷ equation trivially solvable.</li>
 * </ul>
 *
 * <h3>Data structure: BitSet for the prime sieve</h3>
 *
 * The previous implementation used {@code boolean[]}, which allocates 1 byte
 * per entry due to JVM padding.  A {@code BitSet} packs 64 entries per
 * {@code long} word — 8× less heap.  For the default {@code maxCellValue=100}
 * the saving is modest, but for 3-digit puzzles ({@code maxCellValue=999}) the
 * sieve shrinks from ~1 KB to ~128 bytes, and for very large future values the
 * difference is meaningful.
 *
 * <p>A set bit means the number IS composite (not prime), matching the
 * natural "mark composites" direction of the Sieve of Eratosthenes.
 * Checking primality is then {@code !compositeFlags.get(n)}.
 */
public class MultiplyOperator implements Operator {

    /**
     * Bit is SET when the index is composite (or 0 or 1).
     * Bit is CLEAR when the index is prime.
     * Using "composite" polarity matches the sieve's "mark composites" algorithm
     * without needing to invert the sense after construction.
     */
    private final BitSet compositeFlags;

    public MultiplyOperator(int maxCellValue) {
        this.compositeFlags = buildCompositeSieve(maxCellValue + 1);
    }

    @Override
    public char symbol() {
        return '*';
    }

    @Override
    public int apply(int leftOperand, int rightOperand, PuzzleConfig config) {
        if (leftOperand  < 2) return Integer.MIN_VALUE;
        if (rightOperand < 2) return Integer.MIN_VALUE;

        if ((long) leftOperand * rightOperand > config.maxCellValue) {
            return Integer.MIN_VALUE;
        }

        int product = leftOperand * rightOperand;

        if (product < config.minCellValue) {
            return Integer.MIN_VALUE;
        }

        // compositeFlags.get(product) is false for primes — reject them
        if (product < compositeFlags.size() && !compositeFlags.get(product)) {
            return Integer.MIN_VALUE;
        }

        return product;
    }

    /**
     * Enumerates all right operands in [2, {@code maxCellValue / leftOperand}]
     * whose product is non-prime and within bounds.
     * Exact — no sampling, no misses.
     */
    @Override
    public List<Integer> validRightOperands(int leftOperand, PuzzleConfig config, Random random) {
        List<Integer> valid = new ArrayList<>();

        if (leftOperand < 2) {
            return valid;
        }

        int maxRight = config.maxCellValue / leftOperand;

        for (int right = 2; right <= maxRight; right++) {
            if (apply(leftOperand, right, config) != Integer.MIN_VALUE) {
                valid.add(right);
            }
        }

        Collections.shuffle(valid, random);
        return valid;
    }

    @Override
    public String constraints() {
        return "both operands ≥ 2;  product ∈ [minCellValue, maxCellValue];  product not prime";
    }

    // ── Sieve of Eratosthenes using BitSet ────────────────────────────────────

    /**
     * Builds a sieve where bit {@code n} is SET when {@code n} is composite
     * (including 0 and 1).  Bit CLEAR means {@code n} is prime.
     *
     * <p>Memory: ~{@code sieveSize / 64} longs = ~{@code sieveSize / 8} bytes,
     * versus ~{@code sieveSize} bytes for {@code boolean[]}.
     */
    private static BitSet buildCompositeSieve(int sieveSize) {
        BitSet composite = new BitSet(sieveSize);

        // 0 and 1 are not prime
        composite.set(0);
        composite.set(1);

        for (int candidate = 2; (long) candidate * candidate < sieveSize; candidate++) {
            if (!composite.get(candidate)) {
                // candidate is prime — mark all its multiples as composite
                for (int multiple = candidate * candidate; multiple < sieveSize; multiple += candidate) {
                    composite.set(multiple);
                }
            }
        }

        return composite;
    }
}