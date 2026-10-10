package io.crossmath.engine;

import java.util.Random;

/**
 * CrossMath puzzle generator — entry point.
 *
 * <h2>Usage</h2>
 * <pre>
 *   javac -d out src/io/crossmath/engine/*.java
 *   java -cp out io.crossmath.engine.CrossMathMain [seed] [equationsToHide] [maxCellValue] [matrixSize] [minUsagePerOperator] [numBrackets]
 * </pre>
 */
public class CrossMathMain {

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Generation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void run(String[] args) {
        long seed            = args.length > 0 ? Long.parseLong(args[0])   : System.currentTimeMillis();
        int  equationsToHide = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int  maxCellValue    = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        int  matrixSize      = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        int  minUsagePerOp   = args.length > 4 ? Integer.parseInt(args[4]) : 2;
        int  numBrackets     = args.length > 5 ? Integer.parseInt(args[5]) : 4;

        PuzzleConfig config = PuzzleConfig.builder()
            .matrixSize(matrixSize)
            .minCellValue(1)
            .maxCellValue(maxCellValue)
            .maxGenerationAttempts(10000)
            .minUsagePerOperator(minUsagePerOp)
            .numBrackets(numBrackets)
            .build();

        Random random = new Random(seed);

        OperatorRegistry registry = new OperatorRegistry(config, random);
        registry.printSummary();
        System.out.println();

        CrossMathGenerator generator = new CrossMathGenerator(config, registry, random);
        PuzzleGrid solvedGrid = generator.generate();
        printHeader(seed, equationsToHide, maxCellValue, matrixSize, minUsagePerOp, numBrackets);

        System.out.println("  " + config);
        System.out.println();

        System.out.println("  Verification: " + (solvedGrid.verify() ? "PASS" : "FAIL"));

        PuzzlePrinter solutionPrinter = new PuzzlePrinter(solvedGrid, EquationMask.allVisible());
        solutionPrinter.printSolution();
        solutionPrinter.printEquations();

        EquationMask puzzleMask = EquationMask.random(
            solvedGrid.shape.armCount(), equationsToHide, random);
        System.out.printf("  Hidden arms (%d): %s%n%n",
            puzzleMask.hiddenCount(), puzzleMask.hiddenSet());

        PuzzlePrinter puzzlePrinter = new PuzzlePrinter(solvedGrid, puzzleMask);
        puzzlePrinter.printPuzzle();
    }

    private static void printHeader(long seed, int equationsToHide, int maxCellValue,
                                    int matrixSize, int minUsagePerOp, int numBrackets) {
        String hr = "═".repeat(60);
        System.out.println(hr);
        System.out.println("  CrossMath Puzzle Generator");
        System.out.printf("  seed=%-12d  hide=%-3d  maxVal=%-4d  n=%d  minUsage=%d  brackets=%d%n",
            seed, equationsToHide, maxCellValue, matrixSize, minUsagePerOp, numBrackets);
        System.out.println(hr);
        System.out.println();
    }
}
