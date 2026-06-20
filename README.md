# CrossMath Puzzle Engine

Java-based generator for CrossMath / Crossmatch-style math puzzles on a 2D sparse grid.

## Prerequisites

- Java Development Kit (JDK) 17 or higher

## Build & Run

```bash
# Compile
mkdir -p out
javac -d out src/io/crossmath/engine/*.java

# Run with difficulty level (0, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 5.5, 6)
java -cp out io.crossmath.engine.CrossMathMain --level 3 42

# Shape mode
java -cp out io.crossmath.engine.CrossMathMain --level 4 42 shape

# JSON output for frontend
java -cp out io.crossmath.engine.CrossMathMain --json --level 3 42

# Manual mode (seed, hide, maxVal, matrixSize, minUsage, brackets)
java -cp out io.crossmath.engine.CrossMathMain 42 4 100 5 2 4
```

## Run Tests

```bash
javac -d out src/io/crossmath/engine/*.java
java -cp out io.crossmath.engine.CrossMathTest
```

## Project Structure

```
src/io/crossmath/engine/
  CrossMathMain.java       CLI entry point
  CrossMathGenerator.java  Orchestrates shape generation and arm filling
  PuzzleConfig.java        All derived constants from root inputs
  PuzzleGrid.java          Shape + cell values + operators
  PuzzleShape.java         Arms + cell roles + intersection set
  ShapeGenerator.java      BFS random asymmetric shape growth
  EquationMask.java        Controls which equations are hidden
  DifficultyLevel.java     12 education-grade presets (levels 0-6)
  DistractorGenerator.java Plausible wrong answer generation
  UniquenessChecker.java   Verifies puzzles have exactly one solution
  PuzzleJsonExporter.java  JSON serialization for frontend clients
  PuzzlePrinter.java       Console renderer
  Operator.java            Strategy interface for operators
  AddOperator.java         +
  SubtractOperator.java    -
  MultiplyOperator.java    x
  DivideOperator.java      /
  CustomOperators.java     Min, Max, Avg, Exp, Sqrt
  OperatorRegistry.java    Operator lookup and management
  BracketedPicker.java     Round-robin value selection
  CrossMathTest.java       Regression test suite
```
