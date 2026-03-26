package io.crossmath.engine;

import java.util.Random;

/**
 * CrossMath puzzle generator — entry point.
 *
 * <h2>Usage</h2>
 * <pre>
 *   javac -d out src/io/crossmath/*.java
 *   java -cp out io.crossmath.CrossMathMain [seed] [equationsToHide] [maxCellValue] [matrixSize] [minUsagePerOperator]
 *
 *   Examples:
 *     java ... CrossMathMain                        # random seed, defaults
 *     java ... CrossMathMain 42                     # fixed seed
 *     java ... CrossMathMain 42 6                   # hide 6 equations
 *     java ... CrossMathMain 42 0 999               # 3-digit numbers
 *     java ... CrossMathMain 42 4 100 7             # 13×13 display grid
 *     java ... CrossMathMain 42 4 100 5 1           # each operator used at least once
 *     java ... CrossMathMain 42 4 100 5 0           # disable usage check
 * </pre>
 */
public class CrossMathMain {

    public static void main(String[] args) {

    try {
        run(args);
    } catch (Exception e) {
        main(args);
    }
    }

    static void run(String[] args) {
                long seed              = args.length > 0 ? Long.parseLong(args[0])    : System.currentTimeMillis();
        int  equationsToHide   = args.length > 1 ? Integer.parseInt(args[1])  : 4;
        int  maxCellValue      = args.length > 2 ? Integer.parseInt(args[2])  : 299;
        int  matrixSize        = args.length > 3 ? Integer.parseInt(args[3])  : 5;
        int  minUsagePerOp     = args.length > 4 ? Integer.parseInt(args[4])  : 2;
        int  numBrackets       = args.length > 5 ? Integer.parseInt(args[5])  : 4;

        // ── Configuration ─────────────────────────────────────────────────────
        PuzzleConfig config = PuzzleConfig.builder()
            .matrixSize(matrixSize)
            .minCellValue(1)
            .maxCellValue(100)
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
        PuzzleGrid solvedGrid = generator.generate();
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
        EquationMask puzzleMask = EquationMask.random(config, equationsToHide, random);
        System.out.printf("  Hidden equations (%d): %s%n%n",
            puzzleMask.hiddenCount(),
            puzzleMask.hiddenSet());

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
}
