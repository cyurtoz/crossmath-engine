package io.crossmath.engine;

import java.util.Random;

/**
 * CrossMath puzzle generator — entry point.
 *
 * <h2>Usage</h2>
 * <pre>
 *   javac -d out src/io/crossmath/engine/*.java
 *   java -cp out io.crossmath.engine.CrossMathMain [seed] [equationsToHide] [maxCellValue] [matrixSize] [minUsagePerOperator] [numBrackets] [shape]
 *   java -cp out io.crossmath.engine.CrossMathMain --level LEVEL [seed] [shape]
 *
 *   Level mode examples:
 *     java ... CrossMathMain --level 0               # grade 1 beginner (addition only, 0-10)
 *     java ... CrossMathMain --level 2 42            # grade 2 with fixed seed
 *     java ... CrossMathMain --level 4 42 shape      # grade 4, shape mode
 *     java ... CrossMathMain --level 6 99            # advanced (all operators, 0-500)
 *
 *   Manual mode examples:
 *     java ... CrossMathMain                          # random seed, defaults
 *     java ... CrossMathMain 42                       # fixed seed
 *     java ... CrossMathMain 42 4 100 5 2 4 shape     # full params, shape mode
 * </pre>
 */
public class CrossMathMain {

    public static void main(String[] args) {

        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Generation failed: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args) {
        if (args.length >= 2 && "--level".equals(args[0])) {
            runWithLevel(args);
            return;
        }

                long seed              = args.length > 0 ? Long.parseLong(args[0])    : System.currentTimeMillis();
        int  equationsToHide   = args.length > 1 ? Integer.parseInt(args[1])  : 4;
        int  maxCellValue      = args.length > 2 ? Integer.parseInt(args[2])  : 299;
        int  matrixSize        = args.length > 3 ? Integer.parseInt(args[3])  : 5;
        int  minUsagePerOp     = args.length > 4 ? Integer.parseInt(args[4])  : 2;
        int  numBrackets       = args.length > 5 ? Integer.parseInt(args[5])  : 4;
        boolean useShape       = args.length > 6 && "shape".equalsIgnoreCase(args[6]);

        // ── Configuration ─────────────────────────────────────────────────────
        PuzzleConfig config = PuzzleConfig.builder()
            .matrixSize(matrixSize)
            .minCellValue(1)
            .maxCellValue(maxCellValue)
            .maxGenerationAttempts(10000)
            .minUsagePerOperator(minUsagePerOp)
            .numBrackets(numBrackets)
            .build();



        // ── Shared random source — one instance guarantees full reproducibility ──
        Random random = new Random(seed);

        // ── Operator registry ──────────────────────────────────────────────────
        // To activate optional operators, uncomment any lines below:
        //   registry.add(new MinOperator());
        //   registry.add(new MaxOperator());
        //   registry.add(new ExpOperator());
        //   registry.add(new SqrtOperator());
        //   registry.remove('/');    // disable division for simpler puzzles
        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.printSummary();
        System.out.println();

        // ── Generate ───────────────────────────────────────────────────────────
        CrossMathGenerator generator = new CrossMathGenerator(config, registry, random);
        PuzzleGrid solvedGrid;

        if (useShape) {
            ShapeGenerator shapeGen = new ShapeGenerator(config, random);
            PuzzleShape shape = shapeGen.generate();
            System.out.println("  " + shape);
            solvedGrid = generator.generate(shape);
        } else {
            solvedGrid = generator.generate();
        }
        printHeader(seed, equationsToHide, maxCellValue, matrixSize, minUsagePerOp, numBrackets);

        System.out.println("  " + config);
        System.out.println();

        // ── Verify ─────────────────────────────────────────────────────────────
        System.out.println("  Verification: " + (solvedGrid.verify() ? "✓  PASS" : "✗  FAIL"));

        // ── Print solution (all equations visible, all numbers shown) ───────────
        PuzzlePrinter solutionPrinter = new PuzzlePrinter(solvedGrid, EquationMask.allVisible());
        solutionPrinter.printSolution();
        solutionPrinter.printEquations();

        // ── Print puzzle (some equations hidden, all number cells as '?') ────────
        EquationMask puzzleMask;
        if (useShape) {
            puzzleMask = EquationMask.randomForShape(solvedGrid.shape(), equationsToHide, random);
            System.out.printf("  Hidden arms (%d): %s%n%n",
                puzzleMask.hiddenCount(),
                puzzleMask.hiddenArmSet());
        } else {
            puzzleMask = EquationMask.random(config, equationsToHide, random);
            System.out.printf("  Hidden equations (%d): %s%n%n",
                puzzleMask.hiddenCount(),
                puzzleMask.hiddenSet());
        }

        PuzzlePrinter puzzlePrinter = new PuzzlePrinter(solvedGrid, puzzleMask);
        puzzlePrinter.printPuzzle();
    }

    static void runWithLevel(String[] args) {
        DifficultyLevel level = DifficultyLevel.parse(args[1]);
        long seed = args.length > 2 ? Long.parseLong(args[2]) : System.currentTimeMillis();
        boolean useShape = args.length > 3 && "shape".equalsIgnoreCase(args[3]);

        Random random = new Random(seed);
        PuzzleConfig config = level.buildConfig();
        OperatorRegistry registry = new OperatorRegistry(config, random);
        level.configureRegistry(registry);

        System.out.println("  " + level);
        registry.printSummary();
        System.out.println();

        CrossMathGenerator generator = new CrossMathGenerator(config, registry, random);
        PuzzleGrid solvedGrid;
        int armCount = 0;

        if (useShape) {
            ShapeGenerator shapeGen = new ShapeGenerator(config, random);
            PuzzleShape shape = shapeGen.generate();
            System.out.println("  " + shape);
            solvedGrid = generator.generate(shape);
            armCount = shape.armCount();
        } else {
            solvedGrid = generator.generate();
        }

        printLevelHeader(level, seed, useShape);
        System.out.println("  " + config);
        System.out.println();
        System.out.println("  Verification: " + (solvedGrid.verify() ? "✓  PASS" : "✗  FAIL"));

        PuzzlePrinter solutionPrinter = new PuzzlePrinter(solvedGrid, EquationMask.allVisible());
        solutionPrinter.printSolution();
        solutionPrinter.printEquations();

        EquationMask puzzleMask = level.buildMask(config, armCount, random);
        System.out.printf("  Hidden: %d%n%n", puzzleMask.hiddenCount());

        PuzzlePrinter puzzlePrinter = new PuzzlePrinter(solvedGrid, puzzleMask);
        puzzlePrinter.printPuzzle();
    }

     

    private static void printHeader(long seed, int equationsToHide, int maxCellValue,
                                    int matrixSize, int minUsagePerOp, int numBrackets) {
        String horizontalRule = "═".repeat(60);
        System.out.println(horizontalRule);
        System.out.println("  CrossMath Puzzle Generator");
        System.out.printf("  seed=%-12d  hide=%-3d  maxVal=%-4d  n=%d  minUsage=%d  brackets=%d%n",
            seed, equationsToHide, maxCellValue, matrixSize, minUsagePerOp, numBrackets);
        System.out.println(horizontalRule);
        System.out.println();
    }

    private static void printLevelHeader(DifficultyLevel level, long seed, boolean useShape) {
        String horizontalRule = "═".repeat(60);
        System.out.println(horizontalRule);
        System.out.println("  CrossMath Puzzle Generator");
        System.out.printf("  level=%-6s  seed=%-12d  mode=%s%n",
            level.label, seed, useShape ? "shape" : "fixed-grid");
        System.out.println(horizontalRule);
        System.out.println();
    }
}
