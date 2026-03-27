package io.crossmath.engine;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive regression test suite for the CrossMath puzzle engine.
 *
 * <h2>Coverage matrix</h2>
 * <ul>
 *   <li>Grid sizes: 3, 5, 7</li>
 *   <li>Operator sets: {+}, {+,-}, {+,-,*}, {+,-,*,/}</li>
 *   <li>minUsagePerOperator: 0, 1, 2</li>
 *   <li>maxCellValue: 10, 20, 50, 100</li>
 *   <li>Multiple seeds per configuration</li>
 * </ul>
 *
 * <h2>Invariants verified per puzzle</h2>
 * <ol>
 *   <li>Generation completes without exception</li>
 *   <li>{@code grid.verify()} returns true (op.apply(a,b) == result for every arm)</li>
 *   <li>Every arm has an operator assigned (non-null)</li>
 *   <li>All cell values within [minCellValue, maxCellValue]</li>
 *   <li>Operator usage meets the configured minimum</li>
 *   <li>Only allowed operators appear in the grid</li>
 *   <li>Shape arm count ≥ numOperators × minUsagePerOperator</li>
 *   <li>No cell is the result of two different arms</li>
 *   <li>Each arm has exactly 2 operand cells + 1 result cell</li>
 *   <li>Arm cells stay within matrix bounds</li>
 *   <li>Each arm direction is HORIZONTAL or VERTICAL with consistent cell layout</li>
 *   <li>EquationMask can hide/show arms without breaking verification</li>
 *   <li>PuzzlePrinter runs without exception for solution and puzzle views</li>
 * </ol>
 *
 * <h2>Running</h2>
 * <pre>
 *   javac -d out src/io/crossmath/engine/*.java
 *   java -cp out io.crossmath.engine.CrossMathTest
 * </pre>
 */
public class CrossMathTest {

    private int passed  = 0;
    private int failed  = 0;
    private int skipped = 0;
    private final List<String> failures = new ArrayList<>();

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        CrossMathTest suite = new CrossMathTest();
        suite.runAll();
        suite.printSummary();
        System.exit(suite.failed > 0 ? 1 : 0);
    }

    // ── Test orchestration ───────────────────────────────────────────────────

    private void runAll() {
        System.out.println("═".repeat(70));
        System.out.println("  CrossMath Engine — Regression Test Suite");
        System.out.println("═".repeat(70));
        System.out.println();

        testGridSizeOperatorCombinations();
        testMaxCellValueVariations();
        testMinUsagePerOperatorVariations();
        testMultipleSeedsStability();
        testEdgeCases();
        testShapeInvariants();
        testMaskingInvariants();
        testPrinterDoesNotCrash();
        testConfigValidation();
    }

    // ── 1. Grid size × operator set matrix ───────────────────────────────────

    private void testGridSizeOperatorCombinations() {
        section("Grid Size × Operator Set Combinations");

        // Each entry: matrixSize, maxCellValue, operators, minUsagePerOperator.
        // Configurations must be feasible:
        //   - arms needed = numOps × minUsage ≤ targetEquationCount
        //   - addition-only needs generous maxVal to avoid sum overflow
        //   - n=3 shapes produce ~5 arms max, so 3+ ops × 2 is infeasible
        Object[][] combos = {
            // ── matrixSize=3 (target=6, shape ~5 arms) ──
            {3, 50,  Set.of('+'),               0},  // add-only: needs maxVal headroom for sum cascades
            {3, 20,  Set.of('+', '-'),           1},
            {3, 20,  Set.of('+', '-', '*'),      1},  // 3 ops × 1 = 3 ≤ 5
            {3, 20,  Set.of('+', '-', '*', '/'), 1},  // 4 ops × 1 = 4 ≤ 5

            // ── matrixSize=5 (target=10, shape ~9-10 arms) ──
            {5, 100, Set.of('+'),               0},  // add-only: relaxed usage
            {5, 100, Set.of('+', '-'),           2},
            {5, 100, Set.of('+', '-', '*'),      2},
            {5, 100, Set.of('+', '-', '*', '/'), 2},

            // ── matrixSize=7 (target=21, shape ~15-21 arms) ──
            {7, 200, Set.of('+'),               0},  // add-only: needs higher maxVal
            {7, 100, Set.of('+', '-'),           2},
            {7, 100, Set.of('+', '-', '*'),      2},
            {7, 100, Set.of('+', '-', '*', '/'), 2},
        };

        for (Object[] combo : combos) {
            int matrixSize              = (int) combo[0];
            int maxVal                  = (int) combo[1];
            @SuppressWarnings("unchecked")
            Set<Character> ops          = (Set<Character>) combo[2];
            int minUsage                = (int) combo[3];

            String label = String.format("n=%d ops=%s maxVal=%d minU=%d",
                matrixSize, opsLabel(ops), maxVal, minUsage);

            try {
                PuzzleConfig config = PuzzleConfig.builder()
                    .matrixSize(matrixSize)
                    .maxCellValue(maxVal)
                    .minUsagePerOperator(minUsage)
                    .allowedOperators(ops)
                    .maxGenerationAttempts(5000)
                    .build();

                PuzzleGrid grid = generateGrid(config, 42);
                verifyAllInvariants(label, grid, config, ops, minUsage);
            } catch (Exception e) {
                fail(label, "Exception: " + e.getMessage());
            }
        }
    }

    // ── 2. maxCellValue variations ───────────────────────────────────────────

    private void testMaxCellValueVariations() {
        section("maxCellValue Variations");

        for (int maxVal : new int[]{10, 20, 50, 100, 200}) {
            String label = "n=5 maxVal=" + maxVal;
            try {
                PuzzleConfig config = PuzzleConfig.builder()
                    .matrixSize(5)
                    .maxCellValue(maxVal)
                    .minUsagePerOperator(1)
                    .maxGenerationAttempts(5000)
                    .build();

                PuzzleGrid grid = generateGrid(config, 123);
                verifyAllInvariants(label, grid, config, null, 1);
            } catch (Exception e) {
                fail(label, "Exception: " + e.getMessage());
            }
        }
    }

    // ── 3. minUsagePerOperator variations ────────────────────────────────────

    private void testMinUsagePerOperatorVariations() {
        section("minUsagePerOperator Variations");

        for (int minUsage : new int[]{0, 1, 2}) {
            String label = "n=5 allOps minUsage=" + minUsage;
            try {
                PuzzleConfig config = PuzzleConfig.builder()
                    .matrixSize(5)
                    .maxCellValue(100)
                    .minUsagePerOperator(minUsage)
                    .maxGenerationAttempts(5000)
                    .build();

                PuzzleGrid grid = generateGrid(config, 789);
                verifyAllInvariants(label, grid, config, null, minUsage);
            } catch (Exception e) {
                fail(label, "Exception: " + e.getMessage());
            }
        }
    }

    // ── 4. Multiple seeds per config ─────────────────────────────────────────

    private void testMultipleSeedsStability() {
        section("Multiple Seeds Stability (20 seeds × 3 configs)");

        int[][] configs = {
            // matrixSize, maxVal, minUsage
            {3, 20,  1},
            {5, 100, 2},
            {7, 100, 2},
        };

        long[] seeds = {1, 7, 13, 42, 99, 123, 256, 456, 789, 1000,
                        1337, 2024, 3141, 4444, 5555, 6789, 7777, 8888, 9999, 31337};

        for (int[] cfg : configs) {
            int matrixSize = cfg[0];
            int maxVal     = cfg[1];
            int minUsage   = cfg[2];

            int seedPassed = 0;
            int seedFailed = 0;

            for (long seed : seeds) {
                String label = String.format("n=%d maxVal=%d minUsage=%d seed=%d",
                    matrixSize, maxVal, minUsage, seed);
                try {
                    PuzzleConfig config = PuzzleConfig.builder()
                        .matrixSize(matrixSize)
                        .maxCellValue(maxVal)
                        .minUsagePerOperator(minUsage)
                        .maxGenerationAttempts(5000)
                        .build();

                    PuzzleGrid grid = generateGrid(config, seed);
                    verifyAllInvariants(label, grid, config, null, minUsage);
                    seedPassed++;
                } catch (Exception e) {
                    fail(label, "Exception: " + e.getMessage());
                    seedFailed++;
                }
            }

            System.out.printf("    n=%d: %d/%d seeds passed%n",
                matrixSize, seedPassed, seeds.length);
        }
    }

    // ── 5. Edge cases ────────────────────────────────────────────────────────

    private void testEdgeCases() {
        section("Edge Cases");

        // Single operator, small grid
        {
            String label = "n=3 addOnly maxVal=10";
            try {
                PuzzleConfig config = PuzzleConfig.builder()
                    .matrixSize(3)
                    .maxCellValue(10)
                    .minUsagePerOperator(0)
                    .allowedOperators(Set.of('+'))
                    .maxGenerationAttempts(5000)
                    .build();

                PuzzleGrid grid = generateGrid(config, 42);
                verifyAllInvariants(label, grid, config, Set.of('+'), 0);

                // All operators must be +
                for (EquationArm arm : grid.shape.arms()) {
                    check(label + " opSymbol", arm.operator().symbol() == '+',
                        "Expected '+' but got '" + arm.operator().symbol() + "'");
                }
            } catch (Exception e) {
                fail(label, "Exception: " + e.getMessage());
            }
        }

        // Two operators, small values
        {
            String label = "n=3 addSub maxVal=10";
            try {
                PuzzleConfig config = PuzzleConfig.builder()
                    .matrixSize(3)
                    .maxCellValue(10)
                    .minUsagePerOperator(1)
                    .allowedOperators(Set.of('+', '-'))
                    .maxGenerationAttempts(5000)
                    .build();

                PuzzleGrid grid = generateGrid(config, 99);
                verifyAllInvariants(label, grid, config, Set.of('+', '-'), 1);
            } catch (Exception e) {
                fail(label, "Exception: " + e.getMessage());
            }
        }

        // minUsage=0 should always succeed (no operator constraint)
        {
            String label = "n=5 minUsage=0 (no constraint)";
            try {
                PuzzleConfig config = PuzzleConfig.builder()
                    .matrixSize(5)
                    .maxCellValue(50)
                    .minUsagePerOperator(0)
                    .maxGenerationAttempts(1000)
                    .build();

                PuzzleGrid grid = generateGrid(config, 1);
                verifyAllInvariants(label, grid, config, null, 0);
            } catch (Exception e) {
                fail(label, "Exception: " + e.getMessage());
            }
        }

        // Config rejects impossible usage quota
        {
            String label = "reject impossible minUsage";
            try {
                PuzzleConfig config = PuzzleConfig.builder()
                    .matrixSize(3)
                    .maxCellValue(20)
                    .minUsagePerOperator(5) // 4 ops × 5 = 20, target=6
                    .maxGenerationAttempts(100)
                    .build();

                Random random = new Random(42);
                OperatorRegistry registry = new OperatorRegistry(config, random);
                CrossMathGenerator gen = new CrossMathGenerator(config, registry, random);

                try {
                    gen.generate();
                    fail(label, "Should have thrown for impossible minUsage");
                } catch (IllegalArgumentException e) {
                    pass(label);
                }
            } catch (IllegalArgumentException e) {
                // Config-level rejection is also acceptable
                pass(label);
            }
        }
    }

    // ── 6. Shape structural invariants ───────────────────────────────────────

    private void testShapeInvariants() {
        section("Shape Structural Invariants");

        for (int matrixSize : new int[]{3, 5, 7}) {
            String label = "shape n=" + matrixSize;
            try {
                PuzzleConfig config = PuzzleConfig.builder()
                    .matrixSize(matrixSize)
                    .maxCellValue(100)
                    .minUsagePerOperator(1)
                    .maxGenerationAttempts(5000)
                    .build();

                PuzzleGrid grid = generateGrid(config, 42);
                PuzzleShape shape = grid.shape;

                // No cell is result of two arms
                Set<GridCell> resultCells = new HashSet<>();
                for (EquationArm arm : shape.arms()) {
                    check(label + " uniqueResult",
                        resultCells.add(arm.resultCell()),
                        "Cell " + arm.resultCell() + " is result of multiple arms");
                }

                // Per-direction claim uniqueness
                Set<String> hClaims = new HashSet<>();
                Set<String> vClaims = new HashSet<>();
                for (EquationArm arm : shape.arms()) {
                    Set<String> claims = arm.direction() == ArmDirection.HORIZONTAL ? hClaims : vClaims;
                    for (GridCell cell : arm.allCells()) {
                        String key = cell.row() + "," + cell.col();
                        check(label + " dirClaim " + arm.direction(),
                            claims.add(key),
                            "Cell " + cell + " claimed twice in " + arm.direction());
                    }
                }

                // Arm cell layout consistency
                for (EquationArm arm : shape.arms()) {
                    check(label + " operandCount",
                        arm.operandCells().size() == 2,
                        "Expected 2 operands, got " + arm.operandCells().size());

                    check(label + " allCellsCount",
                        arm.allCells().size() == 3,
                        "Expected 3 cells, got " + arm.allCells().size());

                    // Direction consistency: H arms same row, V arms same col
                    if (arm.direction() == ArmDirection.HORIZONTAL) {
                        int row = arm.operandCells().get(0).row();
                        for (GridCell cell : arm.allCells()) {
                            check(label + " hRow", cell.row() == row,
                                "H arm cells not in same row");
                        }
                    } else {
                        int col = arm.operandCells().get(0).col();
                        for (GridCell cell : arm.allCells()) {
                            check(label + " vCol", cell.col() == col,
                                "V arm cells not in same col");
                        }
                    }

                    // All cells in bounds
                    for (GridCell cell : arm.allCells()) {
                        check(label + " inBounds",
                            cell.row() >= 0 && cell.row() < matrixSize &&
                            cell.col() >= 0 && cell.col() < matrixSize,
                            "Cell " + cell + " out of bounds for n=" + matrixSize);
                    }
                }

                pass(label);
            } catch (Exception e) {
                fail(label, "Exception: " + e.getMessage());
            }
        }
    }

    // ── 7. Masking invariants ────────────────────────────────────────────────

    private void testMaskingInvariants() {
        section("Masking Invariants");

        String label = "mask operations";
        try {
            PuzzleConfig config = PuzzleConfig.builder()
                .matrixSize(5)
                .maxCellValue(100)
                .minUsagePerOperator(1)
                .maxGenerationAttempts(5000)
                .build();

            PuzzleGrid grid = generateGrid(config, 42);
            int armCount = grid.shape.armCount();

            // Verification still passes regardless of masking (mask is display-only)
            check(label + " verify", grid.verify(), "Grid must verify before masking");

            // allVisible mask
            EquationMask allVis = EquationMask.allVisible();
            check(label + " allVis", allVis.hiddenCount() == 0,
                "allVisible should have 0 hidden");
            for (int i = 0; i < armCount; i++) {
                check(label + " allVisArm" + i, allVis.isArmVisible(i),
                    "Arm " + i + " should be visible");
            }

            // random mask
            int toHide = Math.min(armCount / 2, armCount);
            EquationMask rndMask = EquationMask.random(armCount, toHide, new Random(42));
            check(label + " rndHiddenCount", rndMask.hiddenCount() == toHide,
                "Expected " + toHide + " hidden, got " + rndMask.hiddenCount());

            // hide all then show all
            EquationMask custom = new EquationMask();
            for (int i = 0; i < armCount; i++) custom.hideArm(i);
            check(label + " hideAll", custom.hiddenCount() == armCount,
                "All arms should be hidden");
            for (int i = 0; i < armCount; i++) custom.showArm(i);
            check(label + " showAll", custom.hiddenCount() == 0,
                "All arms should be visible after showAll");

            // Request to hide more arms than exist
            EquationMask overHide = EquationMask.random(armCount, armCount + 10, new Random(1));
            check(label + " overHide", overHide.hiddenCount() == armCount,
                "Should cap at armCount");

            pass(label);
        } catch (Exception e) {
            fail(label, "Exception: " + e.getMessage());
        }
    }

    // ── 8. Printer smoke test ────────────────────────────────────────────────

    private void testPrinterDoesNotCrash() {
        section("PuzzlePrinter Smoke Tests");

        for (int matrixSize : new int[]{3, 5, 7}) {
            String label = "printer n=" + matrixSize;
            try {
                PuzzleConfig config = PuzzleConfig.builder()
                    .matrixSize(matrixSize)
                    .maxCellValue(100)
                    .minUsagePerOperator(1)
                    .maxGenerationAttempts(5000)
                    .build();

                PuzzleGrid grid = generateGrid(config, 42);

                // Redirect stdout to suppress output during test
                var origOut = System.out;
                System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
                try {
                    PuzzlePrinter solPrinter = new PuzzlePrinter(grid, EquationMask.allVisible());
                    solPrinter.printSolution();
                    solPrinter.printEquations();

                    int toHide = Math.min(3, grid.shape.armCount());
                    EquationMask mask = EquationMask.random(grid.shape.armCount(), toHide, new Random(1));
                    PuzzlePrinter pzlPrinter = new PuzzlePrinter(grid, mask);
                    pzlPrinter.printPuzzle();
                } finally {
                    System.setOut(origOut);
                }

                pass(label);
            } catch (Exception e) {
                fail(label, "Exception: " + e.getMessage());
            }
        }
    }

    // ── 9. Config validation ─────────────────────────────────────────────────

    private void testConfigValidation() {
        section("Config Validation");

        // Even matrixSize
        expectConfigFail("even matrixSize", () ->
            PuzzleConfig.builder().matrixSize(4).build());

        // matrixSize < 3
        expectConfigFail("matrixSize=1", () ->
            PuzzleConfig.builder().matrixSize(1).build());

        // maxCellValue <= minCellValue
        expectConfigFail("maxVal<=minVal", () ->
            PuzzleConfig.builder().minCellValue(10).maxCellValue(5).build());

        // Negative minCellValue
        expectConfigFail("negative minCellValue", () ->
            PuzzleConfig.builder().minCellValue(-1).build());

        // Valid configs should NOT throw
        try {
            PuzzleConfig.builder().matrixSize(3).maxCellValue(10).build();
            pass("valid n=3 maxVal=10");
        } catch (Exception e) {
            fail("valid n=3 maxVal=10", "Should not throw: " + e.getMessage());
        }

        try {
            PuzzleConfig.builder().matrixSize(9).maxCellValue(999).build();
            pass("valid n=9 maxVal=999");
        } catch (Exception e) {
            fail("valid n=9 maxVal=999", "Should not throw: " + e.getMessage());
        }
    }

    // ── Invariant verification ───────────────────────────────────────────────

    private void verifyAllInvariants(String label, PuzzleGrid grid,
                                     PuzzleConfig config,
                                     Set<Character> allowedOps, int minUsage) {
        // 1. Grid verification
        check(label + " verify", grid.verify(),
            "grid.verify() returned false");

        // 2. Every arm has an operator
        for (int i = 0; i < grid.shape.armCount(); i++) {
            EquationArm arm = grid.shape.arms().get(i);
            check(label + " op[" + i + "]", arm.operator() != null,
                "Arm " + i + " has null operator");
        }

        // 3. All values within bounds
        for (EquationArm arm : grid.shape.arms()) {
            for (GridCell cell : arm.allCells()) {
                int val = grid.getValue(cell);
                check(label + " valBounds " + cell,
                    val >= config.minCellValue && val <= config.maxCellValue,
                    "Value " + val + " at " + cell + " outside [" +
                    config.minCellValue + "," + config.maxCellValue + "]");
            }
        }

        // 4. Operator usage meets minimum
        if (minUsage > 0) {
            Map<Character, Integer> usage = new HashMap<>();
            for (EquationArm arm : grid.shape.arms()) {
                if (arm.operator() != null) {
                    usage.merge(arm.operator().symbol(), 1, Integer::sum);
                }
            }

            // Determine which operators were registered
            Random tmpRandom = new Random(0);
            OperatorRegistry tmpReg = new OperatorRegistry(config, tmpRandom);
            for (Operator op : tmpReg.all()) {
                int count = usage.getOrDefault(op.symbol(), 0);
                check(label + " usage[" + op.symbol() + "]",
                    count >= minUsage,
                    "Operator '" + op.symbol() + "' used " + count +
                    " times, need >= " + minUsage);
            }
        }

        // 5. Only allowed operators used
        if (allowedOps != null) {
            for (EquationArm arm : grid.shape.arms()) {
                if (arm.operator() != null) {
                    check(label + " allowedOp",
                        allowedOps.contains(arm.operator().symbol()),
                        "Operator '" + arm.operator().symbol() +
                        "' not in allowed set " + allowedOps);
                }
            }
        }

        // 6. Shape arm count feasibility
        if (minUsage > 0) {
            Random tmpRandom = new Random(0);
            OperatorRegistry tmpReg = new OperatorRegistry(config, tmpRandom);
            int required = tmpReg.size() * minUsage;
            check(label + " armCount",
                grid.shape.armCount() >= required,
                "Only " + grid.shape.armCount() + " arms but need >= " +
                required + " (" + tmpReg.size() + " ops × " + minUsage + ")");
        }

        // 7. Unique result cells
        Set<GridCell> resultCells = new HashSet<>();
        for (EquationArm arm : grid.shape.arms()) {
            check(label + " uniqueResult",
                resultCells.add(arm.resultCell()),
                "Duplicate result cell: " + arm.resultCell());
        }

        // 8. Equation arithmetic spot-check
        for (EquationArm arm : grid.shape.arms()) {
            int left   = grid.getValue(arm.operandCells().get(0));
            int right  = grid.getValue(arm.operandCells().get(1));
            int result = grid.getValue(arm.resultCell());
            int computed = arm.operator().apply(left, right, config);
            check(label + " arithmetic",
                computed == result,
                left + " " + arm.operator().symbol() + " " + right +
                " = " + computed + " but grid has " + result);
        }

        pass(label);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PuzzleGrid generateGrid(PuzzleConfig config, long seed) {
        Random random = new Random(seed);
        OperatorRegistry registry = new OperatorRegistry(config, random);
        CrossMathGenerator generator = new CrossMathGenerator(config, registry, random);
        return generator.generate();
    }

    private void expectConfigFail(String label, Runnable action) {
        try {
            action.run();
            fail(label, "Expected IllegalArgumentException but none was thrown");
        } catch (IllegalArgumentException e) {
            pass(label);
        } catch (Exception e) {
            fail(label, "Expected IllegalArgumentException but got " + e.getClass().getSimpleName());
        }
    }

    private void check(String context, boolean condition, String detail) {
        if (!condition) {
            throw new AssertionError("[FAIL] " + context + ": " + detail);
        }
    }

    private void pass(String label) {
        passed++;
        System.out.println("  [PASS] " + label);
    }

    private void fail(String label, String reason) {
        failed++;
        String msg = "  [FAIL] " + label + " — " + reason;
        System.out.println(msg);
        failures.add(msg);
    }

    private void section(String title) {
        System.out.println();
        System.out.println("── " + title + " " + "─".repeat(Math.max(0, 66 - title.length())));
    }

    private String opsLabel(Set<Character> ops) {
        return ops.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining("", "{", "}"));
    }

    private void printSummary() {
        System.out.println();
        System.out.println("═".repeat(70));
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.println("═".repeat(70));

        if (!failures.isEmpty()) {
            System.out.println();
            System.out.println("  Failures:");
            for (String f : failures) {
                System.out.println("    " + f);
            }
        }

        System.out.println();
    }
}
