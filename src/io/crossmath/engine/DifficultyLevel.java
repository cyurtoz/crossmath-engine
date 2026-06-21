package io.crossmath.engine;

import java.util.Random;
import java.util.Set;

public enum DifficultyLevel {

    LEVEL_0  ("0",   0,  10, "+",           3, 0.85, 0.95, 3, 3, 0.0,  0.0,  true,  1),
    LEVEL_1  ("1",   0,  10, "+",           3, 0.75, 0.85, 3, 3, 0.0,  0.0,  false, 1),
    LEVEL_1_5("1.5", 0,  10, "+-",          3, 0.70, 0.80, 3, 4, 0.0,  0.0,  false, 1),
    LEVEL_2  ("2",   0,  20, "+-",          3, 0.65, 0.75, 3, 4, 0.0,  0.0,  false, 1),
    LEVEL_2_5("2.5", 0,  20, "+-",          3, 0.60, 0.70, 4, 4, 0.0,  0.2,  false, 1),
    LEVEL_3  ("3",   0,  50, "+-*",         5, 0.55, 0.65, 4, 4, 0.0,  0.2,  false, 2),
    LEVEL_3_5("3.5", 0,  50, "+-*",         5, 0.50, 0.60, 4, 4, 0.3,  0.5,  false, 2),
    LEVEL_4  ("4",   0, 100, "+-*/",        5, 0.45, 0.55, 4, 4, 0.5,  0.7,  false, 3),
    LEVEL_4_5("4.5", 0, 100, "+-*/",        5, 0.40, 0.50, 4, 5, 0.5,  0.7,  false, -1),
    LEVEL_5  ("5",   0, 200, "+-*/amM",     7, 0.35, 0.45, 4, 5, 0.7,  0.85, false, -1),
    LEVEL_5_5("5.5", 0, 200, "+-*/amM",     7, 0.30, 0.40, 5, 5, 0.7,  0.85, false, -1),
    LEVEL_6  ("6",   0, 500, "+-*/amM^r",   7, 0.20, 0.35, 5, 5, 0.85, 0.85, false, -1),
    LEVEL_7  ("7",   0, 999, "+-*/amM^r%L", 7, 0.15, 0.30, 5, 6, 0.85, 0.90, false, -1);

    public final String label;
    public final int    minVal;
    public final int    maxVal;
    public final String allowedOps;
    public final int    matrixSize;
    public final double minVisible;
    public final double maxVisible;
    public final int    minOptionCount;
    public final int    maxOptionCount;
    public final double operatorHideChance;
    public final double resultHideChance;
    public final boolean avoidCarry;
    public final int    maxUnknownsPerEquation;

    DifficultyLevel(String label, int minVal, int maxVal, String allowedOps,
                    int matrixSize, double minVisible, double maxVisible,
                    int minOptionCount, int maxOptionCount,
                    double operatorHideChance, double resultHideChance,
                    boolean avoidCarry, int maxUnknownsPerEquation) {
        this.label                  = label;
        this.minVal                 = minVal;
        this.maxVal                 = maxVal;
        this.allowedOps             = allowedOps;
        this.matrixSize             = matrixSize;
        this.minVisible             = minVisible;
        this.maxVisible             = maxVisible;
        this.minOptionCount         = minOptionCount;
        this.maxOptionCount         = maxOptionCount;
        this.operatorHideChance     = operatorHideChance;
        this.resultHideChance       = resultHideChance;
        this.avoidCarry             = avoidCarry;
        this.maxUnknownsPerEquation = maxUnknownsPerEquation;
    }

    /**
     * Returns a random option count in [{@link #minOptionCount}, {@link #maxOptionCount}].
     */
    public int optionCount(Random random) {
        if (minOptionCount == maxOptionCount) return minOptionCount;
        return minOptionCount + random.nextInt(maxOptionCount - minOptionCount + 1);
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
                .avoidCarry(avoidCarry)
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

        if (allowed.contains('a')) registry.add(new AvgOperator());
        if (allowed.contains('m')) registry.add(new MinOperator());
        if (allowed.contains('M')) registry.add(new MaxOperator());
        if (allowed.contains('^')) registry.add(new ExpOperator());
        if (allowed.contains('r')) registry.add(new SqrtOperator());
        if (allowed.contains('%')) registry.add(new ModuloOperator());
        if (allowed.contains('L')) registry.add(new LogOperator());
    }

    public EquationMask buildMask(PuzzleGrid grid, Random random) {
        return buildMask(grid, random, null);
    }

    /**
     * Builds a mask and, when a non-null {@code registry} is supplied,
     * retries up to {@value #MAX_UNIQUENESS_RETRIES} times to find a mask
     * that produces a uniquely solvable puzzle. Falls back to the best
     * available mask if uniqueness cannot be achieved.
     */
    public EquationMask buildMask(PuzzleGrid grid, Random random,
                                  OperatorRegistry registry) {
        PuzzleConfig config = grid.config;
        int armCount = grid.isShapeMode() ? grid.shape().armCount() : 0;

        double visibleFraction = minVisible + random.nextDouble() * (maxVisible - minVisible);
        int totalEquations = armCount > 0 ? armCount
                : 2 * config.matrixSize * config.equationsPerLine;
        int countToHide = Math.max(1, totalEquations - (int) Math.round(totalEquations * visibleFraction));
        countToHide = Math.min(countToHide, totalEquations - 1);

        EquationMask bestMask = null;

        int attempts = (registry != null) ? MAX_UNIQUENESS_RETRIES : 1;
        for (int i = 0; i < attempts; i++) {
            EquationMask candidate;
            if (armCount > 0) {
                boolean intersectionsAreKey = ordinal() >= LEVEL_3.ordinal();
                candidate = EquationMask.smartRandomForArms(grid.shape(), countToHide, random, intersectionsAreKey);
            } else {
                candidate = EquationMask.smartRandom(config, countToHide, maxUnknownsPerEquation, random);
            }

            if (bestMask == null) {
                bestMask = candidate;
            }

            if (registry == null) {
                return candidate;
            }

            if (UniquenessChecker.isUnique(grid, candidate, registry)) {
                return candidate;
            }
            bestMask = candidate; // keep the latest attempt as fallback
        }

        return bestMask;
    }

    private static final int MAX_UNIQUENESS_RETRIES = 20;

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
