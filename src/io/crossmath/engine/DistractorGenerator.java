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
 *   <li>Low levels: ±1 from correct answer</li>
 *   <li>Mid levels: off-by-one, carry mistakes, reversed operations</li>
 *   <li>High levels: factor confusion, multi-step traps, logic traps</li>
 * </ul>
 *
 * <p>Each hidden cell gets exactly {@code optionCount} choices: 1 correct + (optionCount-1) distractors.
 */
public class DistractorGenerator {

    private final PuzzleConfig config;
    private final Random random;

    public DistractorGenerator(PuzzleConfig config, Random random) {
        this.config = config;
        this.random = random;
    }

    public List<Integer> generateOptions(int correctAnswer, int optionCount, DifficultyLevel level) {
        DifficultyLevel effectiveLevel = level != null ? level : DifficultyLevel.LEVEL_4;
        Set<Integer> distractors = new LinkedHashSet<>();

        addNearMiss(distractors, correctAnswer, effectiveLevel);
        addOperationConfusion(distractors, correctAnswer, effectiveLevel);
        addRandomNear(distractors, correctAnswer, effectiveLevel);

        distractors.remove(correctAnswer);
        distractors.removeIf(d -> d < config.minCellValue || d > config.maxCellValue);

        List<Integer> distractorList = new ArrayList<>(distractors);
        Collections.shuffle(distractorList, random);

        int needed = optionCount - 1;
        while (distractorList.size() < needed) {
            int fallback = generateFallback(correctAnswer, distractorList);
            if (fallback != correctAnswer && fallback >= config.minCellValue && fallback <= config.maxCellValue) {
                distractorList.add(fallback);
            }
        }
        if (distractorList.size() > needed) {
            distractorList = distractorList.subList(0, needed);
        }

        List<Integer> options = new ArrayList<>(distractorList);
        options.add(correctAnswer);
        Collections.shuffle(options, random);
        return options;
    }

    private void addNearMiss(Set<Integer> distractors, int correct, DifficultyLevel level) {
        distractors.add(correct + 1);
        distractors.add(correct - 1);

        if (level.ordinal() >= DifficultyLevel.LEVEL_2.ordinal()) {
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
