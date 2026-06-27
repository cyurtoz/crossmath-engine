package io.crossmath.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Generates wrong answer options (distractors) for hidden cells.
 *
 * <p>Distractors are near-miss values that look plausible but are incorrect.
 * The strategy varies by difficulty level:
 * <ul>
 *   <li>Low levels: ±1 from correct answer, swapped operands</li>
 *   <li>Mid levels: inverse confusion (a+b vs a-b), carry mistakes</li>
 *   <li>High levels: table confusion, factor confusion, multi-step traps</li>
 * </ul>
 *
 * <p>Each hidden cell gets exactly {@code optionCount} choices: 1 correct + (optionCount-1) distractors.
 */
public class DistractorGenerator {

    public record EquationContext(int left, int right, char op) {}

    private final PuzzleConfig config;
    private final Random random;

    public DistractorGenerator(PuzzleConfig config, Random random) {
        this.config = config;
        this.random = random;
    }

    public List<Integer> generateOptions(int correctAnswer, int optionCount, DifficultyLevel level) {
        return generateOptions(correctAnswer, optionCount, level, null);
    }

    public List<Integer> generateOptions(int correctAnswer, int optionCount,
                                         DifficultyLevel level, EquationContext ctx) {
        DifficultyLevel effectiveLevel = level != null ? level : DifficultyLevel.LEVEL_4;

        Set<Integer> eqAware = new LinkedHashSet<>();
        if (ctx != null) {
            addEquationAwareDistractions(eqAware, correctAnswer, effectiveLevel, ctx);
            eqAware.remove(correctAnswer);
            eqAware.removeIf(d -> d < config.minCellValue || d > config.maxCellValue);
        }

        Set<Integer> general = new LinkedHashSet<>();
        addNearMiss(general, correctAnswer, effectiveLevel);
        addOperationConfusion(general, correctAnswer, effectiveLevel);
        addRandomNear(general, correctAnswer, effectiveLevel);
        general.remove(correctAnswer);
        general.removeIf(d -> d < config.minCellValue || d > config.maxCellValue);
        general.removeAll(eqAware);

        List<Integer> eqAwareList = new ArrayList<>(eqAware);
        Collections.shuffle(eqAwareList, random);
        List<Integer> generalList = new ArrayList<>(general);
        Collections.shuffle(generalList, random);

        int needed = optionCount - 1;
        List<Integer> distractorList = new ArrayList<>(needed);
        for (int d : eqAwareList) {
            if (distractorList.size() >= needed) break;
            distractorList.add(d);
        }
        for (int d : generalList) {
            if (distractorList.size() >= needed) break;
            distractorList.add(d);
        }
        while (distractorList.size() < needed) {
            int fallback = generateFallback(correctAnswer, distractorList);
            if (fallback != correctAnswer && fallback >= config.minCellValue && fallback <= config.maxCellValue) {
                distractorList.add(fallback);
            }
        }

        List<Integer> options = new ArrayList<>(distractorList);
        options.add(correctAnswer);
        Collections.shuffle(options, random);
        return options;
    }

    private void addNearMiss(Set<Integer> distractors, int correct, DifficultyLevel level) {
        distractors.add(correct + 1);
        distractors.add(correct - 1);

        if (level.ordinal() >= DifficultyLevel.LEVEL_1_5.ordinal()) {
            distractors.add(correct + 2);
            distractors.add(correct - 2);
        }
    }

    private void addOperationConfusion(Set<Integer> distractors, int correct, DifficultyLevel level) {
        if (level.ordinal() >= DifficultyLevel.LEVEL_1_5.ordinal()) {
            if (correct > 1) distractors.add(correct * 2);
        }

        if (level.ordinal() >= DifficultyLevel.LEVEL_1_5.ordinal()
                && level.ordinal() <= DifficultyLevel.LEVEL_2_5.ordinal()) {
            int half = Math.max(1, correct / 2);
            distractors.add(correct - half);
            distractors.add(correct + half);
        }

        if (level.ordinal() >= DifficultyLevel.LEVEL_3.ordinal()) {
            if (correct >= 4) distractors.add(correct / 2);
            int reversed = reverseDigits(correct);
            if (reversed != correct) distractors.add(reversed);

            for (int factor = 2; factor <= 5; factor++) {
                distractors.add((correct - 1) * factor);
                distractors.add((correct + 1) * factor);
                if (correct > factor && correct % factor == 0) {
                    distractors.add(correct + factor);
                    distractors.add(correct - factor);
                }
            }
        }

        if (level.ordinal() >= DifficultyLevel.LEVEL_4.ordinal()) {
            distractors.add(correct + 10);
            distractors.add(correct - 10);
            if (correct >= 2) {
                int nearFactor = findNearFactor(correct);
                if (nearFactor != correct) distractors.add(nearFactor);
            }
            if (correct >= 2) {
                for (int d = 2; d <= Math.min(12, correct); d++) {
                    if (correct % d == 0) {
                        distractors.add(correct / d + 1);
                        distractors.add(correct / d - 1);
                        break;
                    }
                }
            }
        }

        if (level.ordinal() >= DifficultyLevel.LEVEL_4_5.ordinal()) {
            distractors.add(correct + 5);
            distractors.add(correct - 5);
            if (correct > 3) distractors.add(correct * 2 - 1);
            if (correct > 1) distractors.add((correct - 1) * (correct - 1) > config.maxCellValue
                    ? correct + 3 : (correct - 1) * (correct - 1));
        }

        if (level.ordinal() >= DifficultyLevel.LEVEL_5.ordinal()) {
            distractors.add(correct + 15);
            distractors.add(correct - 15);
            if (correct >= 4) {
                distractors.add(correct * 3 / 2);
                distractors.add(correct * 2 / 3);
            }
            int swapOp = correct > 10 ? correct - (correct % 10) + (correct / 10 % 10) : correct + 7;
            distractors.add(swapOp);
        }
    }

    private void addEquationAwareDistractions(Set<Integer> distractors, int correct,
                                               DifficultyLevel effectiveLevel, EquationContext ctx) {
        int left = ctx.left();
        int right = ctx.right();
        char op = ctx.op();

        // Level 1+: swapped operands (grades.txt: "swapped numbers")
        if (effectiveLevel.ordinal() >= DifficultyLevel.LEVEL_1.ordinal()) {
            if (left != right) {
                distractors.add(right);
                distractors.add(left);
            }
        }

        // Level 1.5-2.5: inverse confusion (grades.txt: "inverse confusion (a+b vs a-b)")
        if (effectiveLevel.ordinal() >= DifficultyLevel.LEVEL_1_5.ordinal()
                && effectiveLevel.ordinal() <= DifficultyLevel.LEVEL_2_5.ordinal()) {
            if (op == '+' && left > right) {
                distractors.add(left - right);
            } else if (op == '-') {
                int sum = left + right;
                if (sum <= config.maxCellValue) distractors.add(sum);
            }
        }

        // Level 3+: table confusion (grades.txt: "table confusion (e.g., 6×3 vs 5×3)")
        if (effectiveLevel.ordinal() >= DifficultyLevel.LEVEL_3.ordinal()) {
            if (op == '*') {
                if (left >= 2) distractors.add((left - 1) * right);
                if ((left + 1) * right <= config.maxCellValue) distractors.add((left + 1) * right);
                if (right >= 2) distractors.add(left * (right - 1));
                if (left * (right + 1) <= config.maxCellValue) distractors.add(left * (right + 1));
            }
            if (op == '/') {
                if (left > 0 && right > 0) {
                    int nearQuotient = left / right;
                    distractors.add(nearQuotient + 1);
                    if (nearQuotient > 1) distractors.add(nearQuotient - 1);
                    distractors.add(right);
                }
            }
        }

        // Level 4+: operation swap confusion
        if (effectiveLevel.ordinal() >= DifficultyLevel.LEVEL_4.ordinal()) {
            if (op == '*' && right != 0) {
                distractors.add(left + right);
            } else if (op == '+' && left >= 2 && right >= 2
                       && (long) left * right <= config.maxCellValue) {
                distractors.add(left * right);
            }
            if (op == '/' && left >= right) {
                distractors.add(left - right);
            } else if (op == '-' && right != 0 && left % right == 0) {
                distractors.add(left / right);
            }
        }

        // Level 5+: custom operator confusion (avg, min, max)
        if (effectiveLevel.ordinal() >= DifficultyLevel.LEVEL_5.ordinal()) {
            if (op == 'm') {
                // min confused with max
                distractors.add(Math.max(left, right));
                distractors.add(Math.abs(left - right));
            } else if (op == 'M') {
                // max confused with min
                distractors.add(Math.min(left, right));
                int sum = left + right;
                if (sum <= config.maxCellValue) distractors.add(sum);
            } else if (op == 'a') {
                // avg confused with sum, individual operands, half-operand
                int sum = left + right;
                if (sum <= config.maxCellValue) distractors.add(sum);
                distractors.add(left);
                distractors.add(right);
                distractors.add(Math.max(1, (left + right + 1) / 2));
            }
        }

        // Level 6+: advanced operator confusion (exp, sqrt)
        if (effectiveLevel.ordinal() >= DifficultyLevel.LEVEL_6.ordinal()) {
            if (op == '^') {
                // exp confused with multiplication
                int product = left * right;
                if (product > 0 && product <= config.maxCellValue) distractors.add(product);
                // adjacent powers
                long prevPower = 1;
                for (int i = 0; i < right - 1 && prevPower <= config.maxCellValue; i++) prevPower *= left;
                if (prevPower > 0 && prevPower <= config.maxCellValue) distractors.add((int) prevPower);
                long nextPower = 1;
                for (int i = 0; i < right + 1 && nextPower <= config.maxCellValue; i++) nextPower *= left;
                if (nextPower > 0 && nextPower <= config.maxCellValue) distractors.add((int) nextPower);
            } else if (op == 'r') {
                // sqrt confused with half, or square root of adjacent values
                distractors.add(Math.max(1, left / 2));
                int sqrtFloor = (int) Math.sqrt(left);
                if (sqrtFloor > 0) distractors.add(sqrtFloor + 1);
                if (sqrtFloor > 1) distractors.add(sqrtFloor - 1);
            }
        }

        // Level 7: modulo and log confusion
        if (effectiveLevel.ordinal() >= DifficultyLevel.LEVEL_7.ordinal()) {
            if (op == '%') {
                // modulo confused with division quotient
                if (right > 0) distractors.add(left / right);
                distractors.add(right);
                // adjacent remainders
                if (right > 0) {
                    int rem = left % right;
                    if (rem + 1 <= config.maxCellValue) distractors.add(rem + 1);
                    if (rem - 1 >= config.minCellValue) distractors.add(rem - 1);
                }
            } else if (op == 'L') {
                // log confused with base/exponent swap, adjacent exponents
                distractors.add(left);
                distractors.add(right);
                if (correct + 1 <= config.maxCellValue) distractors.add(correct + 1);
                if (correct - 1 >= config.minCellValue) distractors.add(correct - 1);
            }
        }
    }

    private void addRandomNear(Set<Integer> distractors, int correct, DifficultyLevel level) {
        int range = Math.max(3, (int) (correct * 0.3));
        for (int i = 0; i < 3; i++) {
            int offset = 1 + random.nextInt(range);
            distractors.add(correct + (random.nextBoolean() ? offset : -offset));
        }
    }

    private int generateFallback(int correct, List<Integer> existing) {
        for (int attempt = 0; attempt < 50; attempt++) {
            int range = Math.max(5, correct / 2);
            int candidate = correct + (random.nextInt(range * 2 + 1) - range);
            if (candidate != correct && candidate >= config.minCellValue
                    && candidate <= config.maxCellValue && !existing.contains(candidate)) {
                return candidate;
            }
        }
        int offset = existing.size() + 2;
        return Math.min(config.maxCellValue, correct + offset);
    }

    private static int reverseDigits(int n) {
        if (n < 10) return n;
        int reversed = 0;
        int original = n;
        while (original > 0) {
            reversed = reversed * 10 + original % 10;
            original /= 10;
        }
        return reversed;
    }

    private int findNearFactor(int value) {
        for (int f = 2; f <= Math.sqrt(value) + 1; f++) {
            if (value % f == 0) {
                return value / f;
            }
        }
        return value - 1;
    }
}
