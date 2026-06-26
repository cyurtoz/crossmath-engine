package io.crossmath.engine;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Comprehensive test suite for the CrossMath puzzle engine.
 *
 * <p>Standalone — uses its own main() with simple pass/fail assertions.
 * No JUnit dependency required.
 *
 * <p>Compile:  javac -d out src/io/crossmath/engine/*.java
 * <p>Run:     java -cp out io.crossmath.engine.CrossMathTest
 */
public class CrossMathTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== CrossMath Engine Test Suite ===\n");

        // 1. Fixed-grid generation for matrix sizes 3, 5, 7
        testFixedGrid3x3AddSub();
        testFixedGrid3x3AddOnly();
        testFixedGrid5x5AllOps();
        testFixedGrid5x5AddSub();
        testFixedGrid7x7AllOps();
        testFixedGrid7x7AddSubMul();

        // 2. Shape-based generation for matrix sizes 3, 5, 7
        testShapeMode3x3();
        testShapeMode5x5();
        testShapeMode7x7();

        // 3. All 12 DifficultyLevel presets
        testAllDifficultyLevels();

        // 4. Cell values within bounds
        testCellValuesWithinBoundsFixed();
        testCellValuesWithinBoundsShape();

        // 5. Grid verification passes
        testGridVerificationFixed();
        testGridVerificationShape();

        // 6. Operator usage meets configured minimums
        testOperatorUsageMeetsMinimum();

        // 7. Masking operations (hide/show)
        testMaskingHideShow();

        // 8. EquationMask smart masking
        testSmartMaskFixedGrid();
        testSmartMaskShape();
        testRandomMaskFixedGrid();
        testRandomMaskForShape();

        // 9. Config validation
        testConfigRejectsEvenMatrixSize();
        testConfigRejectsMatrixSizeLessThan3();
        testConfigRejectsNegativeMinCellValue();
        testConfigRejectsMaxLessThanMin();
        testConfigDerivedValues();

        // 10. PuzzlePrinter doesn't crash
        testPuzzlePrinterFixed();
        testPuzzlePrinterShape();

        // 11. JSON export produces valid output
        testJsonExportFixed();
        testJsonExportShape();

        // 12. Distractor generation
        testDistractorGeneration();
        testDistractorContainsCorrectAnswer();
        testDistractorAllWithinBounds();
        testEquationAwareDistractors();

        // 13. Multiple seeds stability (20 seeds per config)
        testMultipleSeedsFixed5x5();
        testMultipleSeedsFixed3x3();
        testMultipleSeedsShape5x5();

        // 14. UniquenessChecker runs without errors
        testUniquenessCheckerFixed();
        testUniquenessCheckerShape();

        // Extra: OperatorRegistry tests
        testRegistryAddRemove();
        testRegistryApplyRandom();
        testRegistryFindOperatorForResult();

        // Extra: EquationArm tests
        testEquationArmAccessors();

        // Extra: PuzzleShape tests
        testPuzzleShapeAccessors();

        // Extra: ShapeGenerator produces valid shapes
        testShapeGeneratorProducesArms();

        // Extra: DifficultyLevel.parse
        testDifficultyLevelParse();

        // 15. ModuloOperator and LogOperator
        testModuloOperator();
        testModuloOperatorValidDomain();
        testLogOperator();
        testLogOperatorValidDomain();
        testLevel7GeneratesAndVerifies();
        testLevel7ShapeMode();
        testLevel7MultipleSeeds();

        // 16. DifficultyScorer
        testDifficultyScorerMonotonic();
        testDifficultyScorerShapeMode();
        testDifficultyScorerBounds();
        testDifficultyScorerJsonExport();

        System.out.println("\n=== Results ===");
        System.out.printf("Passed: %d  |  Failed: %d  |  Total: %d%n", passed, failed, passed + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ── Assertion helpers ────────────────────────────────────────────────────

    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS: " + testName);
        } else {
            failed++;
            System.out.println("  FAIL: " + testName);
        }
    }

    private static void assertFalse(String testName, boolean condition) {
        assertTrue(testName, !condition);
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        boolean eq = (expected == null) ? (actual == null) : expected.equals(actual);
        if (eq) {
            passed++;
            System.out.println("  PASS: " + testName);
        } else {
            failed++;
            System.out.println("  FAIL: " + testName + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static void assertThrows(String testName, Runnable action) {
        try {
            action.run();
            failed++;
            System.out.println("  FAIL: " + testName + " (no exception thrown)");
        } catch (Exception e) {
            passed++;
            System.out.println("  PASS: " + testName + " (caught " + e.getClass().getSimpleName() + ")");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Fixed-grid generation
    // ══════════════════════════════════════════════════════════════════════════

    private static void testFixedGrid3x3AddSub() {
        System.out.println("\n-- Fixed-grid 3x3 add/sub --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(20)
                .minUsagePerOperator(1).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('*');
        registry.remove('/');

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        assertTrue("3x3 add/sub grid not null", grid != null);
        assertTrue("3x3 add/sub grid verifies", grid.verify());
        assertFalse("3x3 add/sub is not shape mode", grid.isShapeMode());
    }

    private static void testFixedGrid3x3AddOnly() {
        System.out.println("\n-- Fixed-grid 3x3 add-only --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(20)
                .minUsagePerOperator(0).build();
        Random random = new Random(123);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('-');
        registry.remove('*');
        registry.remove('/');

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        assertTrue("3x3 add-only grid not null", grid != null);
        assertTrue("3x3 add-only grid verifies", grid.verify());
    }

    private static void testFixedGrid5x5AllOps() {
        System.out.println("\n-- Fixed-grid 5x5 all ops --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .minUsagePerOperator(2).build();
        Random random = new Random(99);
        OperatorRegistry registry = new OperatorRegistry(config, random);

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        assertTrue("5x5 all-ops grid not null", grid != null);
        assertTrue("5x5 all-ops grid verifies", grid.verify());
        assertEquals("5x5 equationsPerLine", 2, config.equationsPerLine);
    }

    private static void testFixedGrid5x5AddSub() {
        System.out.println("\n-- Fixed-grid 5x5 add/sub --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(50)
                .minUsagePerOperator(1).build();
        Random random = new Random(77);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('*');
        registry.remove('/');

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        assertTrue("5x5 add/sub grid not null", grid != null);
        assertTrue("5x5 add/sub grid verifies", grid.verify());
    }

    private static void testFixedGrid7x7AllOps() {
        System.out.println("\n-- Fixed-grid 7x7 all ops --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(7).minCellValue(1).maxCellValue(200)
                .maxGenerationAttempts(10000)
                .minUsagePerOperator(1).build();
        Random random = new Random(7);
        OperatorRegistry registry = new OperatorRegistry(config, random);

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        assertTrue("7x7 all-ops grid not null", grid != null);
        assertTrue("7x7 all-ops grid verifies", grid.verify());
        assertEquals("7x7 equationsPerLine", 3, config.equationsPerLine);
    }

    private static void testFixedGrid7x7AddSubMul() {
        System.out.println("\n-- Fixed-grid 7x7 add/sub/mul --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(7).minCellValue(1).maxCellValue(200)
                .maxGenerationAttempts(10000)
                .minUsagePerOperator(1).build();
        Random random = new Random(11);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('/');

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        assertTrue("7x7 add/sub/mul grid not null", grid != null);
        assertTrue("7x7 add/sub/mul grid verifies", grid.verify());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Shape-based generation
    // ══════════════════════════════════════════════════════════════════════════

    private static void testShapeMode3x3() {
        System.out.println("\n-- Shape mode 3x3 --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(20)
                .maxGenerationAttempts(2000)
                .minUsagePerOperator(0).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('*');
        registry.remove('/');

        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        assertTrue("Shape 3x3 grid not null", grid != null);
        assertTrue("Shape 3x3 is shape mode", grid.isShapeMode());
        assertTrue("Shape 3x3 grid verifies", grid.verify());
        assertTrue("Shape 3x3 has shape", grid.shape() != null);
        assertTrue("Shape 3x3 has arms", grid.shape().armCount() > 0);
    }

    private static void testShapeMode5x5() {
        System.out.println("\n-- Shape mode 5x5 --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .maxGenerationAttempts(5000)
                .minUsagePerOperator(1).build();
        Random random = new Random(123);
        OperatorRegistry registry = new OperatorRegistry(config, random);

        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        assertTrue("Shape 5x5 grid not null", grid != null);
        assertTrue("Shape 5x5 is shape mode", grid.isShapeMode());
        assertTrue("Shape 5x5 grid verifies", grid.verify());
    }

    private static void testShapeMode7x7() {
        System.out.println("\n-- Shape mode 7x7 --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(7).minCellValue(1).maxCellValue(200)
                .maxGenerationAttempts(10000)
                .minUsagePerOperator(0).build();
        Random random = new Random(7);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('*');
        registry.remove('/');

        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        assertTrue("Shape 7x7 grid not null", grid != null);
        assertTrue("Shape 7x7 is shape mode", grid.isShapeMode());
        assertTrue("Shape 7x7 grid verifies", grid.verify());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. All 12 DifficultyLevel presets
    // ══════════════════════════════════════════════════════════════════════════

    private static void testAllDifficultyLevels() {
        System.out.println("\n-- All 12 DifficultyLevel presets --");
        // Use different seeds per level to maximize success probability
        int seedBase = 1;
        for (DifficultyLevel level : DifficultyLevel.values()) {
            String name = "DifficultyLevel " + level.label;
            try {
                PuzzleConfig config = level.buildConfig();
                Random random = new Random(seedBase++);
                OperatorRegistry registry = new OperatorRegistry(config, random);
                level.configureRegistry(registry);

                CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
                PuzzleGrid grid = gen.generate();

                assertTrue(name + " generates", grid != null);
                assertTrue(name + " verifies", grid.verify());
            } catch (Exception e) {
                failed++;
                System.out.println("  FAIL: " + name + " threw " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. Cell values within bounds
    // ══════════════════════════════════════════════════════════════════════════

    private static void testCellValuesWithinBoundsFixed() {
        System.out.println("\n-- Cell values within bounds (fixed-grid) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        boolean allInBounds = true;
        for (int r = 0; r < config.matrixSize; r++) {
            for (int c = 0; c < config.matrixSize; c++) {
                int v = grid.numbers[r][c];
                if (v < config.minCellValue || v > config.maxCellValue) {
                    allInBounds = false;
                }
            }
        }
        assertTrue("Fixed-grid cell values within [1, 100]", allInBounds);
    }

    private static void testCellValuesWithinBoundsShape() {
        System.out.println("\n-- Cell values within bounds (shape) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .maxGenerationAttempts(5000)
                .minUsagePerOperator(0).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        boolean allInBounds = true;
        for (Map.Entry<GridCell, Integer> entry : grid.cellValues().entrySet()) {
            int v = entry.getValue();
            if (v < config.minCellValue || v > config.maxCellValue) {
                allInBounds = false;
            }
        }
        assertTrue("Shape cell values within [1, 100]", allInBounds);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. Grid verification
    // ══════════════════════════════════════════════════════════════════════════

    private static void testGridVerificationFixed() {
        System.out.println("\n-- Grid verification (fixed-grid) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(20).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('*');
        registry.remove('/');

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        assertTrue("Fixed-grid passes verify()", grid.verify());

        // Corrupt a cell and verify it fails
        int original = grid.numbers[0][0];
        grid.numbers[0][0] = original + 999;
        assertFalse("Corrupted fixed-grid fails verify()", grid.verify());
        grid.numbers[0][0] = original; // restore
    }

    private static void testGridVerificationShape() {
        System.out.println("\n-- Grid verification (shape) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .maxGenerationAttempts(5000)
                .minUsagePerOperator(0).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        assertTrue("Shape grid passes verify()", grid.verify());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. Operator usage meets configured minimums
    // ══════════════════════════════════════════════════════════════════════════

    private static void testOperatorUsageMeetsMinimum() {
        System.out.println("\n-- Operator usage meets minimum --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .minUsagePerOperator(2).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        // Count usage of each operator
        java.util.Map<Character, Integer> usage = new java.util.LinkedHashMap<>();
        for (Operator op : registry.all()) usage.put(op.symbol(), 0);

        for (int row = 0; row < config.matrixSize; row++)
            for (int eq = 0; eq < config.equationsPerLine; eq++)
                usage.merge(grid.horizontalOperators[row][eq].symbol(), 1, Integer::sum);
        for (int col = 0; col < config.matrixSize; col++)
            for (int eq = 0; eq < config.equationsPerLine; eq++)
                usage.merge(grid.verticalOperators[col][eq].symbol(), 1, Integer::sum);

        boolean meetsMin = true;
        for (Map.Entry<Character, Integer> entry : usage.entrySet()) {
            if (entry.getValue() < config.minUsagePerOperator) {
                meetsMin = false;
                System.out.println("    Operator '" + entry.getKey() + "' usage " +
                        entry.getValue() + " < min " + config.minUsagePerOperator);
            }
        }
        assertTrue("All operators meet minUsagePerOperator=2", meetsMin);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. Masking operations
    // ══════════════════════════════════════════════════════════════════════════

    private static void testMaskingHideShow() {
        System.out.println("\n-- Masking hide/show --");

        EquationMask mask = EquationMask.allVisible();
        assertEquals("Initial hidden count is 0", 0, mask.hiddenCount());

        EquationId id = new EquationId(EquationId.Axis.HORIZONTAL, 0, 0);
        assertTrue("Initially visible", mask.isVisible(id));

        mask.hide(id);
        assertFalse("After hide, not visible", mask.isVisible(id));
        assertEquals("Hidden count is 1 after hide", 1, mask.hiddenCount());

        mask.show(id);
        assertTrue("After show, visible again", mask.isVisible(id));
        assertEquals("Hidden count is 0 after show", 0, mask.hiddenCount());

        // Arm-based masking
        mask.hideArm(0);
        assertFalse("Arm 0 hidden", mask.isArmVisible(0));
        assertTrue("Arm 1 still visible", mask.isArmVisible(1));

        mask.showArm(0);
        assertTrue("Arm 0 visible after showArm", mask.isArmVisible(0));

        // showAll
        mask.hide(id);
        mask.hideArm(2);
        mask.showAll();
        assertEquals("After showAll, hidden count is 0", 0, mask.hiddenCount());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. EquationMask smart masking
    // ══════════════════════════════════════════════════════════════════════════

    private static void testSmartMaskFixedGrid() {
        System.out.println("\n-- Smart mask (fixed-grid) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100).build();
        Random random = new Random(42);

        EquationMask mask = EquationMask.smartRandom(config, 3, 2, random);
        assertTrue("Smart mask hides some equations", mask.hiddenCount() > 0);
        assertTrue("Smart mask hides <= 3", mask.hiddenCount() <= 3);
    }

    private static void testSmartMaskShape() {
        System.out.println("\n-- Smart mask (shape) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .maxGenerationAttempts(5000)
                .minUsagePerOperator(0).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        PuzzleShape shape = grid.shape();
        int countToHide = Math.max(1, shape.armCount() / 3);
        EquationMask mask = EquationMask.smartRandomForArms(shape, countToHide, new Random(99));
        assertTrue("Smart mask for shape hides some arms", mask.hiddenCount() > 0);
    }

    private static void testRandomMaskFixedGrid() {
        System.out.println("\n-- Random mask (fixed-grid) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100).build();
        Random random = new Random(42);

        EquationMask mask = EquationMask.random(config, 4, random);
        assertEquals("Random mask hides 4", 4, mask.hiddenCount());

        // hiddenSet returns the hidden equation IDs
        Set<EquationId> hidden = mask.hiddenSet();
        assertEquals("hiddenSet size matches", 4, hidden.size());
    }

    private static void testRandomMaskForShape() {
        System.out.println("\n-- Random mask for shape --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .maxGenerationAttempts(5000)
                .minUsagePerOperator(0).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        PuzzleShape shape = grid.shape();
        EquationMask mask = EquationMask.randomForShape(shape, 2, new Random(55));
        assertTrue("randomForShape hides some", mask.hiddenCount() > 0);
        assertTrue("randomForShape hides <= 2", mask.hiddenCount() <= 2);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 9. Config validation
    // ══════════════════════════════════════════════════════════════════════════

    private static void testConfigRejectsEvenMatrixSize() {
        System.out.println("\n-- Config rejects even matrixSize --");
        assertThrows("Even matrixSize=4 rejected", () ->
                PuzzleConfig.builder().matrixSize(4).build());
    }

    private static void testConfigRejectsMatrixSizeLessThan3() {
        System.out.println("\n-- Config rejects matrixSize < 3 --");
        assertThrows("matrixSize=1 rejected", () ->
                PuzzleConfig.builder().matrixSize(1).build());
        assertThrows("matrixSize=2 rejected", () ->
                PuzzleConfig.builder().matrixSize(2).build());
    }

    private static void testConfigRejectsNegativeMinCellValue() {
        System.out.println("\n-- Config rejects negative minCellValue --");
        assertThrows("Negative minCellValue rejected", () ->
                PuzzleConfig.builder().minCellValue(-1).build());
    }

    private static void testConfigRejectsMaxLessThanMin() {
        System.out.println("\n-- Config rejects maxCellValue <= minCellValue --");
        assertThrows("maxCellValue == minCellValue rejected", () ->
                PuzzleConfig.builder().minCellValue(10).maxCellValue(10).build());
        assertThrows("maxCellValue < minCellValue rejected", () ->
                PuzzleConfig.builder().minCellValue(50).maxCellValue(20).build());
    }

    private static void testConfigDerivedValues() {
        System.out.println("\n-- Config derived values --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100).build();

        assertEquals("equationsPerLine for 5x5", 2, config.equationsPerLine);
        assertEquals("maxAddOperand for maxCellValue=100", 50, config.maxAddOperand);
        assertEquals("maxMultiplyOperand for maxCellValue=100", 10, config.maxMultiplyOperand);
        assertEquals("maxDivisionDivisor for maxCellValue=100", 50, config.maxDivisionDivisor);
        assertEquals("targetEquationCount for 5x5", 10, config.targetEquationCount);
        assertEquals("maxArmFreeOperands for 5x5", 2, config.maxArmFreeOperands);

        PuzzleConfig config7 = PuzzleConfig.builder()
                .matrixSize(7).minCellValue(1).maxCellValue(200).build();
        assertEquals("equationsPerLine for 7x7", 3, config7.equationsPerLine);
        assertEquals("targetEquationCount for 7x7", 21, config7.targetEquationCount);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 10. PuzzlePrinter doesn't crash
    // ══════════════════════════════════════════════════════════════════════════

    private static void testPuzzlePrinterFixed() {
        System.out.println("\n-- PuzzlePrinter (fixed-grid) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(20).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('*');
        registry.remove('/');

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();
        EquationMask mask = EquationMask.allVisible();

        try {
            // Redirect stderr to suppress generator noise
            PuzzlePrinter printer = new PuzzlePrinter(grid, mask);
            printer.printSolution();
            printer.printPuzzle();
            printer.printEquations();
            assertTrue("PuzzlePrinter (fixed-grid) didn't crash", true);
        } catch (Exception e) {
            assertTrue("PuzzlePrinter (fixed-grid) didn't crash: " + e.getMessage(), false);
        }
    }

    private static void testPuzzlePrinterShape() {
        System.out.println("\n-- PuzzlePrinter (shape) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .maxGenerationAttempts(5000)
                .minUsagePerOperator(0).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        EquationMask mask = EquationMask.randomForShape(grid.shape(), 2, new Random(99));

        try {
            PuzzlePrinter printer = new PuzzlePrinter(grid, mask);
            printer.printSolution();
            printer.printPuzzle();
            printer.printEquations();
            assertTrue("PuzzlePrinter (shape) didn't crash", true);
        } catch (Exception e) {
            assertTrue("PuzzlePrinter (shape) didn't crash: " + e.getMessage(), false);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 11. JSON export
    // ══════════════════════════════════════════════════════════════════════════

    private static void testJsonExportFixed() {
        System.out.println("\n-- JSON export (fixed-grid) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        EquationMask mask = EquationMask.random(config, 3, new Random(55));
        PuzzleJsonExporter exporter = new PuzzleJsonExporter(
                grid, mask, DifficultyLevel.LEVEL_4, new Random(88), registry);
        String json = exporter.exportJson();

        assertTrue("JSON is non-empty", json != null && !json.isEmpty());
        assertTrue("JSON starts with {", json.trim().startsWith("{"));
        assertTrue("JSON ends with }", json.trim().endsWith("}"));
        assertTrue("JSON contains config", json.contains("\"config\""));
        assertTrue("JSON contains mode", json.contains("\"mode\""));
        assertTrue("JSON contains fixed-grid", json.contains("\"fixed-grid\""));
        assertTrue("JSON contains grid", json.contains("\"grid\""));
        assertTrue("JSON contains puzzle", json.contains("\"puzzle\""));
        assertTrue("JSON contains solution", json.contains("\"solution\""));
        assertTrue("JSON contains matrixSize", json.contains("\"matrixSize\": 5"));
        assertTrue("JSON contains uniqueSolution", json.contains("\"uniqueSolution\""));
    }

    private static void testJsonExportShape() {
        System.out.println("\n-- JSON export (shape) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .maxGenerationAttempts(5000)
                .minUsagePerOperator(0).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        EquationMask mask = EquationMask.randomForShape(grid.shape(), 1, new Random(55));
        PuzzleJsonExporter exporter = new PuzzleJsonExporter(
                grid, mask, DifficultyLevel.LEVEL_3, new Random(88), registry);
        String json = exporter.exportJson();

        assertTrue("Shape JSON is non-empty", json != null && !json.isEmpty());
        assertTrue("Shape JSON contains mode shape", json.contains("\"shape\""));
        assertTrue("Shape JSON contains armIndex", json.contains("\"armIndex\""));
        assertTrue("Shape JSON contains role", json.contains("\"role\""));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 12. Distractor generation
    // ══════════════════════════════════════════════════════════════════════════

    private static void testDistractorGeneration() {
        System.out.println("\n-- Distractor generation --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100).build();
        Random random = new Random(42);
        DistractorGenerator gen = new DistractorGenerator(config, random);

        for (DifficultyLevel level : DifficultyLevel.values()) {
            int optionCount = level.optionCount(random);
            List<Integer> options = gen.generateOptions(50, optionCount, level);
            assertTrue("Level " + level.label + " generates " + optionCount + " options",
                    options.size() == optionCount);
        }
    }

    private static void testDistractorContainsCorrectAnswer() {
        System.out.println("\n-- Distractor contains correct answer --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100).build();
        Random random = new Random(42);
        DistractorGenerator gen = new DistractorGenerator(config, random);

        for (int correctAnswer : new int[]{1, 5, 25, 50, 99}) {
            List<Integer> options = gen.generateOptions(correctAnswer, 4, DifficultyLevel.LEVEL_4);
            assertTrue("Options contain correct answer " + correctAnswer,
                    options.contains(correctAnswer));
        }
    }

    private static void testDistractorAllWithinBounds() {
        System.out.println("\n-- Distractors within bounds --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100).build();
        Random random = new Random(42);
        DistractorGenerator gen = new DistractorGenerator(config, random);

        boolean allInBounds = true;
        for (int correctAnswer = 1; correctAnswer <= 100; correctAnswer += 10) {
            List<Integer> options = gen.generateOptions(correctAnswer, 4, DifficultyLevel.LEVEL_4);
            for (int opt : options) {
                if (opt < config.minCellValue || opt > config.maxCellValue) {
                    allInBounds = false;
                }
            }
        }
        assertTrue("All distractors within [minCellValue, maxCellValue]", allInBounds);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 12b. Equation-aware distractors
    // ══════════════════════════════════════════════════════════════════════════

    private static void testEquationAwareDistractors() {
        System.out.println("\n-- Equation-aware distractors --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(100)
                .minUsagePerOperator(0).build();

        // Level 1: swapped numbers — operands should appear as distractors
        DistractorGenerator gen1 = new DistractorGenerator(config, new Random(42));
        var ctx1 = new DistractorGenerator.EquationContext(3, 5, '+');
        List<Integer> opts1 = gen1.generateOptions(8, 3, DifficultyLevel.LEVEL_1, ctx1);
        assertTrue("Level 1 swapped: options contain correct answer 8", opts1.contains(8));
        boolean hasSwap = opts1.contains(3) || opts1.contains(5);
        assertTrue("Level 1 swapped: operand appears as distractor", hasSwap);

        // Level 1.5: inverse confusion — a+b vs a-b
        DistractorGenerator gen2 = new DistractorGenerator(config, new Random(43));
        var ctx2 = new DistractorGenerator.EquationContext(7, 3, '+');
        List<Integer> opts2 = gen2.generateOptions(10, 4, DifficultyLevel.LEVEL_1_5, ctx2);
        assertTrue("Level 1.5 inverse: options contain correct answer 10", opts2.contains(10));

        // Level 3: table confusion for multiplication (use enough options for table values to survive)
        DistractorGenerator gen3 = new DistractorGenerator(config, new Random(44));
        var ctx3 = new DistractorGenerator.EquationContext(6, 3, '*');
        List<Integer> opts3 = gen3.generateOptions(18, 6, DifficultyLevel.LEVEL_3, ctx3);
        assertTrue("Level 3 table: options contain correct answer 18", opts3.contains(18));
        boolean hasTableConfusion = opts3.contains(15) || opts3.contains(21)
                                 || opts3.contains(12) || opts3.contains(24);
        assertTrue("Level 3 table confusion: adjacent product appears", hasTableConfusion);

        // Level 4: operation swap confusion — multiplication vs addition
        DistractorGenerator gen4 = new DistractorGenerator(config, new Random(45));
        var ctx4 = new DistractorGenerator.EquationContext(5, 4, '*');
        List<Integer> opts4 = gen4.generateOptions(20, 4, DifficultyLevel.LEVEL_4, ctx4);
        assertTrue("Level 4 op swap: options contain correct answer 20", opts4.contains(20));

        // Equation context with division
        DistractorGenerator gen5 = new DistractorGenerator(config, new Random(46));
        var ctx5 = new DistractorGenerator.EquationContext(12, 3, '/');
        List<Integer> opts5 = gen5.generateOptions(4, 4, DifficultyLevel.LEVEL_4, ctx5);
        assertTrue("Level 4 division: options contain correct answer 4", opts5.contains(4));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 13. Multiple seeds stability
    // ══════════════════════════════════════════════════════════════════════════

    private static void testMultipleSeedsFixed5x5() {
        System.out.println("\n-- Multiple seeds stability (fixed 5x5, 20 seeds) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .minUsagePerOperator(1).build();

        int successCount = 0;
        for (int seed = 0; seed < 20; seed++) {
            try {
                Random random = new Random(seed);
                OperatorRegistry registry = new OperatorRegistry(config, random);
                CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
                PuzzleGrid grid = gen.generate();
                if (grid != null && grid.verify()) {
                    successCount++;
                }
            } catch (Exception e) {
                // Count as failure
            }
        }
        assertTrue("Fixed 5x5 succeeds for all 20 seeds (" + successCount + "/20)",
                successCount == 20);
    }

    private static void testMultipleSeedsFixed3x3() {
        System.out.println("\n-- Multiple seeds stability (fixed 3x3, 20 seeds) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(20)
                .minUsagePerOperator(0).build();

        int successCount = 0;
        for (int seed = 0; seed < 20; seed++) {
            try {
                Random random = new Random(seed);
                OperatorRegistry registry = new OperatorRegistry(config, random);
                registry.remove('*');
                registry.remove('/');
                CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
                PuzzleGrid grid = gen.generate();
                if (grid != null && grid.verify()) {
                    successCount++;
                }
            } catch (Exception e) {
                // Count as failure
            }
        }
        assertTrue("Fixed 3x3 succeeds for all 20 seeds (" + successCount + "/20)",
                successCount == 20);
    }

    private static void testMultipleSeedsShape5x5() {
        System.out.println("\n-- Multiple seeds stability (shape 5x5, 20 seeds) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .maxGenerationAttempts(5000)
                .minUsagePerOperator(0).build();

        int successCount = 0;
        for (int seed = 0; seed < 20; seed++) {
            try {
                Random random = new Random(seed);
                OperatorRegistry registry = new OperatorRegistry(config, random);
                ShapeGenerator shapeGen = new ShapeGenerator(config, random);
                CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
                PuzzleGrid grid = gen.generate(shapeGen);
                if (grid != null && grid.isShapeMode() && grid.verify()) {
                    successCount++;
                }
            } catch (Exception e) {
                // Count as failure
            }
        }
        assertTrue("Shape 5x5 succeeds for all 20 seeds (" + successCount + "/20)",
                successCount == 20);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 14. UniquenessChecker
    // ══════════════════════════════════════════════════════════════════════════

    private static void testUniquenessCheckerFixed() {
        System.out.println("\n-- UniquenessChecker (fixed-grid) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(20).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('*');
        registry.remove('/');

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        // With all visible, should be unique trivially
        EquationMask allVisible = EquationMask.allVisible();
        try {
            boolean result = UniquenessChecker.isUnique(grid, allVisible, registry);
            assertTrue("UniquenessChecker runs on fixed-grid (all visible)", true);
            assertTrue("All-visible fixed-grid is unique", result);
        } catch (Exception e) {
            assertTrue("UniquenessChecker (fixed-grid) didn't throw: " + e.getMessage(), false);
        }

        // With some hidden
        EquationMask mask = EquationMask.random(config, 1, new Random(55));
        try {
            UniquenessChecker.isUnique(grid, mask, registry);
            assertTrue("UniquenessChecker runs on fixed-grid (some hidden)", true);
        } catch (Exception e) {
            assertTrue("UniquenessChecker (fixed-grid, some hidden) didn't throw: " + e.getMessage(), false);
        }
    }

    private static void testUniquenessCheckerShape() {
        System.out.println("\n-- UniquenessChecker (shape) --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(50)
                .maxGenerationAttempts(5000)
                .minUsagePerOperator(0).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('*');
        registry.remove('/');
        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        EquationMask allVisible = EquationMask.allVisible();
        try {
            boolean result = UniquenessChecker.isUnique(grid, allVisible, registry);
            assertTrue("UniquenessChecker runs on shape (all visible)", true);
            assertTrue("All-visible shape is unique", result);
        } catch (Exception e) {
            assertTrue("UniquenessChecker (shape) didn't throw: " + e.getMessage(), false);
        }

        // With some hidden
        EquationMask mask = EquationMask.randomForShape(grid.shape(), 1, new Random(55));
        try {
            UniquenessChecker.isUnique(grid, mask, registry);
            assertTrue("UniquenessChecker runs on shape (some hidden)", true);
        } catch (Exception e) {
            assertTrue("UniquenessChecker (shape, some hidden) didn't throw: " + e.getMessage(), false);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Extra: OperatorRegistry
    // ══════════════════════════════════════════════════════════════════════════

    private static void testRegistryAddRemove() {
        System.out.println("\n-- OperatorRegistry add/remove --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(20).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);

        assertEquals("Default registry has 4 operators", 4, registry.size());

        registry.remove('/');
        assertEquals("After removing '/', has 3", 3, registry.size());

        registry.remove('*');
        assertEquals("After removing '*', has 2", 2, registry.size());

        // Re-add should work for a new symbol
        registry.add(new MinOperator());
        assertEquals("After adding min, has 3", 3, registry.size());

        // Adding duplicate should throw
        assertThrows("Adding duplicate 'm' throws", () ->
                registry.add(new MinOperator()));
    }

    private static void testRegistryApplyRandom() {
        System.out.println("\n-- OperatorRegistry applyRandom --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(100).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);

        OperatorRegistry.OperatorResult result = registry.applyRandom(10, 5);
        assertTrue("applyRandom(10, 5) returns a result", result != null);
        assertTrue("applyRandom result is valid (non-MIN_VALUE)",
                result.result() != Integer.MIN_VALUE);
    }

    private static void testRegistryFindOperatorForResult() {
        System.out.println("\n-- OperatorRegistry findOperatorForResult --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(100).build();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);

        Operator found = registry.findOperatorForResult(10, 5, 15);
        assertTrue("Found operator for 10 ? 5 = 15", found != null);
        assertEquals("Operator for 10+5=15 is '+'", '+', found.symbol());

        Operator found2 = registry.findOperatorForResult(10, 5, 5);
        assertTrue("Found operator for 10 ? 5 = 5", found2 != null);
        assertEquals("Operator for 10-5=5 is '-'", '-', found2.symbol());

        Operator notFound = registry.findOperatorForResult(10, 5, 999);
        assertTrue("No operator for 10 ? 5 = 999", notFound == null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Extra: EquationArm
    // ══════════════════════════════════════════════════════════════════════════

    private static void testEquationArmAccessors() {
        System.out.println("\n-- EquationArm accessors --");
        GridCell c0 = new GridCell(0, 0);
        GridCell c1 = new GridCell(0, 1);
        GridCell result = new GridCell(0, 2);
        EquationArm arm = new EquationArm(List.of(c0, c1), result, ArmDirection.HORIZONTAL);

        assertEquals("operandCount is 2", 2, arm.operandCount());
        assertEquals("sourceCell is (0,0)", c0, arm.sourceCell());
        assertEquals("resultCell is (0,2)", result, arm.resultCell());
        assertEquals("allCells has 3 elements", 3, arm.allCells().size());
        assertTrue("allCells contains result", arm.allCells().contains(result));
        assertTrue("allCells contains c0", arm.allCells().contains(c0));
        assertTrue("allCells contains c1", arm.allCells().contains(c1));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Extra: PuzzleShape
    // ══════════════════════════════════════════════════════════════════════════

    private static void testPuzzleShapeAccessors() {
        System.out.println("\n-- PuzzleShape accessors --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5).minCellValue(1).maxCellValue(100)
                .maxGenerationAttempts(5000).build();
        Random random = new Random(42);
        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        PuzzleShape shape = shapeGen.generate();

        assertTrue("Shape has arms", shape.armCount() > 0);
        assertTrue("Shape has cells", shape.allClaimedCells().size() > 0);
        assertEquals("Shape matrixSize is 5", 5, shape.matrixSize());

        // cellRoles should contain entries
        assertTrue("cellRoles not empty", shape.cellRoles().size() > 0);

        // Check that at least one intersection exists
        assertTrue("Shape has intersections", shape.intersections().size() > 0);

        // roleOf for an unclaimed cell should be EMPTY
        assertEquals("roleOf unclaimed cell is EMPTY", CellRole.EMPTY,
                shape.roleOf(new GridCell(config.matrixSize - 1, config.matrixSize - 1)));

        // armsContaining should work
        GridCell someCell = shape.arms().get(0).sourceCell();
        List<EquationArm> arms = shape.armsContaining(someCell);
        assertTrue("armsContaining finds at least 1 arm", arms.size() >= 1);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Extra: ShapeGenerator
    // ══════════════════════════════════════════════════════════════════════════

    private static void testShapeGeneratorProducesArms() {
        System.out.println("\n-- ShapeGenerator produces arms --");
        for (int matrixSize : new int[]{3, 5, 7}) {
            PuzzleConfig config = PuzzleConfig.builder()
                    .matrixSize(matrixSize).minCellValue(1)
                    .maxCellValue(matrixSize * 20).build();
            Random random = new Random(42);
            ShapeGenerator gen = new ShapeGenerator(config, random);
            PuzzleShape shape = gen.generate();

            assertTrue("ShapeGenerator " + matrixSize + "x" + matrixSize + " produces arms",
                    shape.armCount() > 0);

            // Every arm should have at least 2 operands
            boolean allArmsValid = true;
            for (EquationArm arm : shape.arms()) {
                if (arm.operandCount() < 2) {
                    allArmsValid = false;
                }
                if (arm.resultCell() == null) {
                    allArmsValid = false;
                }
            }
            assertTrue("All arms have >= 2 operands and a result cell for " + matrixSize,
                    allArmsValid);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Extra: DifficultyLevel.parse
    // ══════════════════════════════════════════════════════════════════════════

    private static void testDifficultyLevelParse() {
        System.out.println("\n-- DifficultyLevel.parse --");
        assertEquals("parse('0') is LEVEL_0", DifficultyLevel.LEVEL_0, DifficultyLevel.parse("0"));
        assertEquals("parse('1.5') is LEVEL_1_5", DifficultyLevel.LEVEL_1_5, DifficultyLevel.parse("1.5"));
        assertEquals("parse('6') is LEVEL_6", DifficultyLevel.LEVEL_6, DifficultyLevel.parse("6"));

        assertThrows("parse('99') throws", () -> DifficultyLevel.parse("99"));
        assertEquals("parse('7') is LEVEL_7", DifficultyLevel.LEVEL_7, DifficultyLevel.parse("7"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 15. ModuloOperator and LogOperator
    // ══════════════════════════════════════════════════════════════════════════

    private static void testModuloOperator() {
        System.out.println("\n-- ModuloOperator --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(100).build();
        ModuloOperator mod = new ModuloOperator();

        assertEquals("symbol is %", '%', mod.symbol());
        assertFalse("modulo is not unary", mod.isUnary());

        assertEquals("10 % 3 = 1", 1, mod.apply(10, 3, config));
        assertEquals("17 % 5 = 2", 2, mod.apply(17, 5, config));
        assertEquals("29 % 7 = 1", 1, mod.apply(29, 7, config));

        // Exact divisibility rejected (overlaps with division)
        assertEquals("10 % 5 rejected (exact div)", Integer.MIN_VALUE, mod.apply(10, 5, config));
        assertEquals("12 % 4 rejected (exact div)", Integer.MIN_VALUE, mod.apply(12, 4, config));

        // right < 2 rejected
        assertEquals("10 % 1 rejected", Integer.MIN_VALUE, mod.apply(10, 1, config));
        assertEquals("10 % 0 rejected", Integer.MIN_VALUE, mod.apply(10, 0, config));

        // left <= right rejected
        assertEquals("3 % 5 rejected (left <= right)", Integer.MIN_VALUE, mod.apply(3, 5, config));
        assertEquals("5 % 5 rejected (left == right)", Integer.MIN_VALUE, mod.apply(5, 5, config));
    }

    private static void testModuloOperatorValidDomain() {
        System.out.println("\n-- ModuloOperator validRightOperands --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(100).build();
        ModuloOperator mod = new ModuloOperator();
        Random random = new Random(42);

        List<Integer> domain = mod.validRightOperands(10, config, random);
        assertTrue("10 has valid modulo operands", domain.size() > 0);

        boolean allValid = true;
        for (int right : domain) {
            if (mod.apply(10, right, config) == Integer.MIN_VALUE) {
                allValid = false;
            }
        }
        assertTrue("All returned operands are valid for 10", allValid);

        List<Integer> domain3 = mod.validRightOperands(3, config, random);
        assertTrue("3 % 2 = 1 is the only valid operand", domain3.size() == 1 && domain3.contains(2));

        List<Integer> domain2 = mod.validRightOperands(2, config, random);
        assertEquals("2 has no valid modulo operands", 0, domain2.size());
    }

    private static void testLogOperator() {
        System.out.println("\n-- LogOperator --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(500).build();
        LogOperator log = new LogOperator();

        assertEquals("symbol is L", 'L', log.symbol());
        assertFalse("log is not unary", log.isUnary());

        assertEquals("log_2(8) = 3", 3, log.apply(2, 8, config));
        assertEquals("log_2(16) = 4", 4, log.apply(2, 16, config));
        assertEquals("log_3(27) = 3", 3, log.apply(3, 27, config));
        assertEquals("log_10(100) = 2", 2, log.apply(10, 100, config));
        assertEquals("log_5(125) = 3", 3, log.apply(5, 125, config));

        assertEquals("log_2(7) rejected", Integer.MIN_VALUE, log.apply(2, 7, config));
        assertEquals("log_3(10) rejected", Integer.MIN_VALUE, log.apply(3, 10, config));

        assertEquals("log_2(2) = 1 rejected (trivial)", Integer.MIN_VALUE, log.apply(2, 2, config));
        assertEquals("log_5(1) rejected", Integer.MIN_VALUE, log.apply(5, 1, config));

        assertEquals("log_1(1) rejected", Integer.MIN_VALUE, log.apply(1, 1, config));
        assertEquals("log_0(0) rejected", Integer.MIN_VALUE, log.apply(0, 0, config));
    }

    private static void testLogOperatorValidDomain() {
        System.out.println("\n-- LogOperator validRightOperands --");
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(500).build();
        LogOperator log = new LogOperator();
        Random random = new Random(42);

        List<Integer> domain2 = log.validRightOperands(2, config, random);
        assertTrue("Base 2 has multiple valid arguments", domain2.size() >= 5);
        assertTrue("Base 2 includes 4 (2^2)", domain2.contains(4));
        assertTrue("Base 2 includes 8 (2^3)", domain2.contains(8));
        assertTrue("Base 2 includes 256 (2^8)", domain2.contains(256));
        assertFalse("Base 2 excludes 2 (exponent=1 trivial)", domain2.contains(2));

        List<Integer> domain10 = log.validRightOperands(10, config, random);
        assertTrue("Base 10 includes 100", domain10.contains(100));
        assertFalse("Base 10 excludes 1000 (> maxCellValue)", domain10.contains(1000));

        List<Integer> domain1 = log.validRightOperands(1, config, random);
        assertEquals("Base 1 has no valid arguments", 0, domain1.size());

        boolean allValid = true;
        for (int arg : domain2) {
            if (log.apply(2, arg, config) == Integer.MIN_VALUE) {
                allValid = false;
            }
        }
        assertTrue("All returned base-2 arguments are valid", allValid);
    }

    private static void testLevel7GeneratesAndVerifies() {
        System.out.println("\n-- Level 7 generates and verifies --");
        DifficultyLevel level = DifficultyLevel.LEVEL_7;
        PuzzleConfig config = level.buildConfig();
        Random random = new Random(42);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        level.configureRegistry(registry);

        boolean hasModulo = registry.all().stream().anyMatch(op -> op.symbol() == '%');
        boolean hasLog = registry.all().stream().anyMatch(op -> op.symbol() == 'L');
        assertTrue("Level 7 registry has modulo", hasModulo);
        assertTrue("Level 7 registry has log", hasLog);

        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        assertTrue("Level 7 grid not null", grid != null);
        assertTrue("Level 7 grid verifies", grid.verify());
    }

    private static void testLevel7ShapeMode() {
        System.out.println("\n-- Level 7 shape mode --");
        DifficultyLevel level = DifficultyLevel.LEVEL_7;
        PuzzleConfig config = level.buildConfig();
        Random random = new Random(123);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        level.configureRegistry(registry);

        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);

        assertTrue("Level 7 shape grid not null", grid != null);
        assertTrue("Level 7 shape mode", grid.isShapeMode());
        assertTrue("Level 7 shape grid verifies", grid.verify());
    }

    private static void testLevel7MultipleSeeds() {
        System.out.println("\n-- Level 7 multiple seeds (10 seeds) --");
        DifficultyLevel level = DifficultyLevel.LEVEL_7;

        int successCount = 0;
        for (int seed = 0; seed < 10; seed++) {
            try {
                PuzzleConfig config = level.buildConfig();
                Random random = new Random(seed);
                OperatorRegistry registry = new OperatorRegistry(config, random);
                level.configureRegistry(registry);

                CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
                PuzzleGrid grid = gen.generate();
                if (grid != null && grid.verify()) {
                    successCount++;
                }
            } catch (Exception e) {
                // Count as failure
            }
        }
        assertTrue("Level 7 succeeds for all 10 seeds (" + successCount + "/10)",
                successCount == 10);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 16. DifficultyScorer
    // ══════════════════════════════════════════════════════════════════════════

    private static void testDifficultyScorerMonotonic() {
        System.out.println("\n-- DifficultyScorer monotonic progression --");
        DifficultyLevel[] levels = {
                DifficultyLevel.LEVEL_0, DifficultyLevel.LEVEL_1,
                DifficultyLevel.LEVEL_2, DifficultyLevel.LEVEL_3,
                DifficultyLevel.LEVEL_4, DifficultyLevel.LEVEL_5,
                DifficultyLevel.LEVEL_6, DifficultyLevel.LEVEL_7
        };

        double previousScore = -1;
        boolean monotonic = true;
        for (DifficultyLevel level : levels) {
            Random random = new Random(42);
            PuzzleConfig config = level.buildConfig();
            OperatorRegistry registry = new OperatorRegistry(config, random);
            level.configureRegistry(registry);
            CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
            PuzzleGrid grid = gen.generate();
            EquationMask mask = level.buildMask(grid, random);

            DifficultyScorer.Score score = DifficultyScorer.score(grid, mask, level);
            if (score.total() < previousScore) {
                monotonic = false;
            }
            previousScore = score.total();
        }
        assertTrue("Difficulty scores increase with level", monotonic);
    }

    private static void testDifficultyScorerShapeMode() {
        System.out.println("\n-- DifficultyScorer shape mode --");
        DifficultyLevel level = DifficultyLevel.LEVEL_4;
        Random random = new Random(42);
        PuzzleConfig config = level.buildConfig();
        OperatorRegistry registry = new OperatorRegistry(config, random);
        level.configureRegistry(registry);
        ShapeGenerator shapeGen = new ShapeGenerator(config, random);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate(shapeGen);
        EquationMask mask = level.buildMask(grid, random);

        DifficultyScorer.Score score = DifficultyScorer.score(grid, mask, level);
        assertTrue("Shape mode score > 0", score.total() > 0);
        assertTrue("Shape mode score <= 100", score.total() <= 100);
        assertTrue("Shape mode hiddenRatio in [0,1]",
                score.hiddenRatio() >= 0 && score.hiddenRatio() <= 1);
        assertTrue("Shape mode operatorComplexity in [0,1]",
                score.operatorComplexity() >= 0 && score.operatorComplexity() <= 1);
    }

    private static void testDifficultyScorerBounds() {
        System.out.println("\n-- DifficultyScorer bounds check --");
        Random random = new Random(99);
        PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(3).minCellValue(1).maxCellValue(20)
                .minUsagePerOperator(0).build();
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.remove('*');
        registry.remove('/');
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();

        EquationMask allVisible = EquationMask.allVisible();
        DifficultyScorer.Score lowScore = DifficultyScorer.score(grid, allVisible, DifficultyLevel.LEVEL_0);
        assertTrue("All-visible score is low", lowScore.total() < 15);

        EquationMask maxHide = EquationMask.random(config, config.matrixSize * config.equationsPerLine * 2 - 1, random);
        DifficultyScorer.Score highScore = DifficultyScorer.score(grid, maxHide, DifficultyLevel.LEVEL_6);
        assertTrue("Max-hidden score is high", highScore.total() > lowScore.total());
    }

    private static void testDifficultyScorerJsonExport() {
        System.out.println("\n-- DifficultyScorer in JSON export --");
        DifficultyLevel level = DifficultyLevel.LEVEL_3;
        Random random = new Random(42);
        PuzzleConfig config = level.buildConfig();
        OperatorRegistry registry = new OperatorRegistry(config, random);
        level.configureRegistry(registry);
        CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);
        PuzzleGrid grid = gen.generate();
        EquationMask mask = level.buildMask(grid, random, registry);

        PuzzleJsonExporter exporter = new PuzzleJsonExporter(grid, mask, level, random, registry);
        String json = exporter.exportJson();

        assertTrue("JSON contains difficulty field", json.contains("\"difficulty\""));
        assertTrue("JSON contains total score", json.contains("\"total\""));
        assertTrue("JSON contains hiddenRatio", json.contains("\"hiddenRatio\""));
        assertTrue("JSON contains operatorComplexity", json.contains("\"operatorComplexity\""));
    }
}
