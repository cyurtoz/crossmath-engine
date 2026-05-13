package io.crossmath.engine;

import java.util.Random;
import java.util.Set;

public enum DifficultyLevel {

    LEVEL_0  ("0",   0,  10, "+",           3, 0.85, 0.95, 3, false, false, true),
    LEVEL_1  ("1",   0,  10, "+",           3, 0.75, 0.85, 3, false, false, false),
    LEVEL_1_5("1.5", 0,  10, "+-",          3, 0.70, 0.80, 4, false, false, false),
    LEVEL_2  ("2",   0,  20, "+-",          3, 0.65, 0.75, 4, false, false, false),
    LEVEL_2_5("2.5", 0,  20, "+-",          3, 0.60, 0.70, 4, false, false, false),
    LEVEL_3  ("3",   0,  50, "+-*",         5, 0.55, 0.65, 4, false, false, false),
    LEVEL_3_5("3.5", 0,  50, "+-*",         5, 0.50, 0.60, 4, true,  false, false),
    LEVEL_4  ("4",   0, 100, "+-*/",        5, 0.45, 0.55, 4, true,  true,  false),
    LEVEL_4_5("4.5", 0, 100, "+-*/",        5, 0.40, 0.50, 5, true,  true,  false),
    LEVEL_5  ("5",   0, 200, "+-*/mM",      7, 0.35, 0.45, 5, true,  true,  false),
    LEVEL_5_5("5.5", 0, 200, "+-*/mM",      7, 0.30, 0.40, 5, true,  true,  false),
    LEVEL_6  ("6",   0, 500, "+-*/mM^",     7, 0.20, 0.35, 5, true,  true,  false);

    public final String label;
    public final int    minVal;
    public final int    maxVal;
    public final String allowedOps;
    public final int    matrixSize;
    public final double minVisible;
    public final double maxVisible;
    public final int    optionCount;
    public final boolean hideOperators;
    public final boolean hideResults;
    public final boolean avoidCarry;

    DifficultyLevel(String label, int minVal, int maxVal, String allowedOps,
                    int matrixSize, double minVisible, double maxVisible,
                    int optionCount, boolean hideOperators, boolean hideResults,
                    boolean avoidCarry) {
        this.label         = label;
        this.minVal        = minVal;
        this.maxVal        = maxVal;
        this.allowedOps    = allowedOps;
        this.matrixSize    = matrixSize;
        this.minVisible    = minVisible;
        this.maxVisible    = maxVisible;
        this.optionCount   = optionCount;
        this.hideOperators = hideOperators;
        this.hideResults   = hideResults;
        this.avoidCarry    = avoidCarry;
    }

    public PuzzleConfig buildConfig() {
        int opCount = allowedOps.length();
        int minUsage = opCount <= 2 ? 1 : opCount <= 4 ? 2 : 0;
        return PuzzleConfig.builder()
                .matrixSize(matrixSize)
                .minCellValue(Math.max(1, minVal))
                .maxCellValue(maxVal)
                .maxGenerationAttempts(10000)
                .minUsagePerOperator(minUsage)
                .numBrackets(4)
                .build();
    }

    public void configureRegistry(OperatorRegistry registry) {
        Set<Character> allowed = Set.copyOf(
                allowedOps.chars().mapToObj(c -> (char) c).toList());

        for (char sym : new char[]{'+', '-', '*', '/'}) {
            if (!allowed.contains(sym)) {
                registry.remove(sym);
            }
        }

        if (allowed.contains('m')) registry.add(new MinOperator());
        if (allowed.contains('M')) registry.add(new MaxOperator());
        if (allowed.contains('^')) registry.add(new ExpOperator());
    }

    public EquationMask buildMask(PuzzleConfig config, int armCount, Random random) {
        double visibleFraction = minVisible + random.nextDouble() * (maxVisible - minVisible);
        int totalEquations = armCount > 0 ? armCount
                : 2 * config.matrixSize * config.equationsPerLine;
        int countToHide = Math.max(0, totalEquations - (int) Math.round(totalEquations * visibleFraction));
        countToHide = Math.min(countToHide, totalEquations - 1);

        if (armCount > 0) {
            return EquationMask.randomForArms(armCount, countToHide, random);
        }
        return EquationMask.random(config, countToHide, random);
    }

    public static boolean hasCarry(int a, int b) {
        return (a % 10) + (b % 10) >= 10;
    }

    public static DifficultyLevel parse(String text) {
        for (DifficultyLevel level : values()) {
            if (level.label.equals(text)) return level;
        }
        throw new IllegalArgumentException(
                "Unknown difficulty level: '" + text + "'. Valid levels: " +
                String.join(", ", java.util.Arrays.stream(values())
                        .map(l -> l.label).toArray(String[]::new)));
    }

    @Override
    public String toString() {
        return String.format("Level %s [%d–%d] ops=%s matrix=%d visible=%.0f–%.0f%%",
                label, minVal, maxVal, allowedOps, matrixSize,
                minVisible * 100, maxVisible * 100);
    }
}
