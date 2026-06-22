package io.crossmath.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Serializes a solved {@link PuzzleGrid} and its {@link EquationMask} to JSON
 * for consumption by frontend clients.
 *
 * <p>Output structure:
 * <pre>{@code
 * {
 *   "config": { "matrixSize": 5, "minCellValue": 1, "maxCellValue": 100 },
 *   "mode": "shape" | "fixed-grid",
 *   "grid": { "cells": [...], "equations": [...] },
 *   "puzzle": { "hiddenCells": [...], "options": { "row,col": [choices] } },
 *   "solution": { "row,col": value }
 * }
 * }</pre>
 */
public class PuzzleJsonExporter {

    private final PuzzleGrid grid;
    private final EquationMask mask;
    private final DifficultyLevel level;
    private final DistractorGenerator distractorGen;
    private final Random random;
    private final OperatorRegistry registry;

    public PuzzleJsonExporter(PuzzleGrid grid, EquationMask mask,
                              DifficultyLevel level, Random random) {
        this(grid, mask, level, random, null);
    }

    public PuzzleJsonExporter(PuzzleGrid grid, EquationMask mask,
                              DifficultyLevel level, Random random,
                              OperatorRegistry registry) {
        this.grid = grid;
        this.mask = mask;
        this.level = level;
        this.random = random;
        this.registry = registry;
        this.distractorGen = new DistractorGenerator(grid.config, random);
    }

    public String exportJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendConfig(sb);
        sb.append(",\n");
        appendMode(sb);
        sb.append(",\n");
        appendLevel(sb);
        sb.append(",\n");
        appendGrid(sb);
        sb.append(",\n");
        appendPuzzle(sb);
        sb.append(",\n");
        appendSolution(sb);
        sb.append(",\n");
        appendUniqueness(sb);
        sb.append(",\n");
        appendDifficultyScore(sb);
        sb.append("\n}");
        return sb.toString();
    }

    private void appendUniqueness(StringBuilder sb) {
        boolean unique = UniquenessChecker.isUnique(grid, mask, registry);
        sb.append("  \"uniqueSolution\": ").append(unique);
    }

    private void appendDifficultyScore(StringBuilder sb) {
        DifficultyScorer.Score score = DifficultyScorer.score(grid, mask, level);
        sb.append("  \"difficulty\": ").append(score.toJson());
    }

    private void appendConfig(StringBuilder sb) {
        sb.append("  \"config\": {\n");
        sb.append("    \"matrixSize\": ").append(grid.config.matrixSize).append(",\n");
        sb.append("    \"minCellValue\": ").append(grid.config.minCellValue).append(",\n");
        sb.append("    \"maxCellValue\": ").append(grid.config.maxCellValue).append(",\n");
        sb.append("    \"displaySize\": ").append(2 * grid.config.matrixSize - 1).append("\n");
        sb.append("  }");
    }

    private void appendMode(StringBuilder sb) {
        sb.append("  \"mode\": \"").append(grid.isShapeMode() ? "shape" : "fixed-grid").append("\"");
    }

    private void appendLevel(StringBuilder sb) {
        if (level == null) {
            sb.append("  \"level\": null");
            return;
        }
        sb.append("  \"level\": {\n");
        sb.append("    \"id\": \"").append(level.label).append("\",\n");
        sb.append("    \"minOptionCount\": ").append(level.minOptionCount).append(",\n");
        sb.append("    \"maxOptionCount\": ").append(level.maxOptionCount).append(",\n");
        sb.append("    \"operatorHideChance\": ").append(level.operatorHideChance).append(",\n");
        sb.append("    \"resultHideChance\": ").append(level.resultHideChance).append("\n");
        sb.append("  }");
    }

    private void appendGrid(StringBuilder sb) {
        sb.append("  \"grid\": {\n");
        sb.append("    \"cells\": [\n");

        if (grid.isShapeMode()) {
            appendShapeCells(sb);
        } else {
            appendFixedCells(sb);
        }

        sb.append("\n    ],\n");
        sb.append("    \"equations\": [\n");

        if (grid.isShapeMode()) {
            appendShapeEquations(sb);
        } else {
            appendFixedEquations(sb);
        }

        sb.append("\n    ]\n");
        sb.append("  }");
    }

    private void appendFixedCells(StringBuilder sb) {
        boolean first = true;
        for (int row = 0; row < grid.config.matrixSize; row++) {
            for (int col = 0; col < grid.config.matrixSize; col++) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append("      {\"row\": ").append(row)
                  .append(", \"col\": ").append(col)
                  .append(", \"value\": ").append(grid.numbers[row][col])
                  .append("}");
            }
        }
    }

    private void appendShapeCells(StringBuilder sb) {
        boolean first = true;
        PuzzleShape shape = grid.shape();
        for (GridCell cell : shape.allClaimedCells()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("      {\"row\": ").append(cell.row())
              .append(", \"col\": ").append(cell.col())
              .append(", \"value\": ").append(grid.getCellValue(cell))
              .append(", \"role\": \"").append(shape.roleOf(cell)).append("\"")
              .append("}");
        }
    }

    private void appendFixedEquations(StringBuilder sb) {
        boolean first = true;
        for (int row = 0; row < grid.config.matrixSize; row++) {
            for (int eq = 0; eq < grid.config.equationsPerLine; eq++) {
                if (!first) sb.append(",\n");
                first = false;
                int leftCol = eq * 2;
                int rightCol = leftCol + 1;
                int resultCol = leftCol + 2;
                boolean visible = mask.isVisible(EquationId.Axis.HORIZONTAL, row, eq);
                sb.append("      {\"axis\": \"H\", \"row\": ").append(row)
                  .append(", \"eq\": ").append(eq)
                  .append(", \"left\": ").append(grid.numbers[row][leftCol])
                  .append(", \"op\": \"").append(grid.horizontalOperators[row][eq].symbol())
                  .append("\", \"right\": ").append(grid.numbers[row][rightCol])
                  .append(", \"result\": ").append(grid.numbers[row][resultCol])
                  .append(", \"visible\": ").append(visible)
                  .append("}");
            }
        }
        for (int col = 0; col < grid.config.matrixSize; col++) {
            for (int eq = 0; eq < grid.config.equationsPerLine; eq++) {
                if (!first) sb.append(",\n");
                first = false;
                int topRow = eq * 2;
                int botRow = topRow + 1;
                int resRow = topRow + 2;
                boolean visible = mask.isVisible(EquationId.Axis.VERTICAL, col, eq);
                sb.append("      {\"axis\": \"V\", \"col\": ").append(col)
                  .append(", \"eq\": ").append(eq)
                  .append(", \"top\": ").append(grid.numbers[topRow][col])
                  .append(", \"op\": \"").append(grid.verticalOperators[col][eq].symbol())
                  .append("\", \"bottom\": ").append(grid.numbers[botRow][col])
                  .append(", \"result\": ").append(grid.numbers[resRow][col])
                  .append(", \"visible\": ").append(visible)
                  .append("}");
            }
        }
    }

    private void appendShapeEquations(StringBuilder sb) {
        PuzzleShape shape = grid.shape();
        boolean first = true;
        int armIndex = 0;
        for (EquationArm arm : shape.arms()) {
            if (!first) sb.append(",\n");
            first = false;
            List<Operator> ops = grid.getArmOperators(arm);
            List<GridCell> operands = arm.operandCells();
            boolean visible = mask.isArmVisible(armIndex);

            sb.append("      {\"armIndex\": ").append(armIndex)
              .append(", \"direction\": \"").append(arm.direction())
              .append("\", \"visible\": ").append(visible)
              .append(", \"operands\": [");

            for (int i = 0; i < operands.size(); i++) {
                if (i > 0) sb.append(", ");
                GridCell c = operands.get(i);
                sb.append("{\"row\": ").append(c.row())
                  .append(", \"col\": ").append(c.col())
                  .append(", \"value\": ").append(grid.getCellValue(c)).append("}");
            }
            sb.append("], \"operators\": [");
            for (int i = 0; i < ops.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(ops.get(i).symbol()).append("\"");
            }
            GridCell rc = arm.resultCell();
            sb.append("], \"result\": {\"row\": ").append(rc.row())
              .append(", \"col\": ").append(rc.col())
              .append(", \"value\": ").append(grid.getCellValue(rc)).append("}}");

            armIndex++;
        }
    }

    private void appendPuzzle(StringBuilder sb) {
        sb.append("  \"puzzle\": {\n");
        sb.append("    \"hiddenCells\": [\n");

        List<int[]> hiddenCells = collectHiddenCells();
        int optionCount = level != null ? level.optionCount(random) : 4;

        boolean first = true;
        for (int[] cell : hiddenCells) {
            if (!first) sb.append(",\n");
            first = false;
            int row = cell[0];
            int col = cell[1];
            int value = grid.isShapeMode() ? grid.getCellValue(new GridCell(row, col))
                                            : grid.numbers[row][col];
            List<Integer> options = distractorGen.generateOptions(value, optionCount, level);

            sb.append("      {\"row\": ").append(row)
              .append(", \"col\": ").append(col)
              .append(", \"options\": ").append(listToJson(options))
              .append("}");
        }

        sb.append("\n    ]");

        boolean shouldHideOps = level != null && level.operatorHideChance > 0;
        if (shouldHideOps) {
            sb.append(",\n    \"hiddenOperators\": [\n");
            appendHiddenOperators(sb);
            sb.append("\n    ]");
        }

        sb.append("\n  }");
    }

    private void appendSolution(StringBuilder sb) {
        sb.append("  \"solution\": {\n");
        boolean first = true;

        if (grid.isShapeMode()) {
            for (GridCell cell : grid.shape().allClaimedCells()) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append("    \"").append(cell.row()).append(",").append(cell.col())
                  .append("\": ").append(grid.getCellValue(cell));
            }
        } else {
            for (int row = 0; row < grid.config.matrixSize; row++) {
                for (int col = 0; col < grid.config.matrixSize; col++) {
                    if (!first) sb.append(",\n");
                    first = false;
                    sb.append("    \"").append(row).append(",").append(col)
                      .append("\": ").append(grid.numbers[row][col]);
                }
            }
        }

        sb.append("\n  }");
    }

    private List<int[]> collectHiddenCells() {
        List<int[]> hidden = new ArrayList<>();
        double resultChance = level == null ? 1.0 : level.resultHideChance;

        if (grid.isShapeMode()) {
            PuzzleShape shape = grid.shape();
            Set<GridCell> hiddenSet = new java.util.LinkedHashSet<>();
            int armIndex = 0;
            for (EquationArm arm : shape.arms()) {
                if (!mask.isArmVisible(armIndex)) {
                    for (GridCell cell : arm.operandCells()) {
                        hiddenSet.add(cell);
                    }
                    // Roll per-equation to decide whether to also hide the result
                    if (random.nextDouble() < resultChance) {
                        hiddenSet.add(arm.resultCell());
                    }
                }
                armIndex++;
            }
            for (GridCell cell : hiddenSet) {
                hidden.add(new int[]{cell.row(), cell.col()});
            }
        } else {
            Set<String> hiddenSet = new java.util.LinkedHashSet<>();
            for (EquationId id : mask.hiddenSet()) {
                // Roll per-equation to decide whether to also hide the result
                boolean includeResult = random.nextDouble() < resultChance;
                addCellsForEquation(id, hiddenSet, includeResult);
            }
            for (String key : hiddenSet) {
                String[] parts = key.split(",");
                hidden.add(new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
            }
        }
        return hidden;
    }

    private void addCellsForEquation(EquationId id, Set<String> cells, boolean includeResult) {
        if (id.axis() == EquationId.Axis.HORIZONTAL) {
            int row = id.lineIndex();
            int leftCol = id.equationIndex() * 2;
            cells.add(row + "," + leftCol);
            cells.add(row + "," + (leftCol + 1));
            if (includeResult) {
                cells.add(row + "," + (leftCol + 2));
            }
        } else {
            int col = id.lineIndex();
            int topRow = id.equationIndex() * 2;
            cells.add(topRow + "," + col);
            cells.add((topRow + 1) + "," + col);
            if (includeResult) {
                cells.add((topRow + 2) + "," + col);
            }
        }
    }

    private void appendHiddenOperators(StringBuilder sb) {
        List<String> allOpSymbols = collectAllOperatorSymbols();
        double opChance = level != null ? level.operatorHideChance : 1.0;
        boolean first = true;

        if (grid.isShapeMode()) {
            PuzzleShape shape = grid.shape();
            int armIndex = 0;
            for (EquationArm arm : shape.arms()) {
                if (!mask.isArmVisible(armIndex) && random.nextDouble() < opChance) {
                    List<Operator> ops = grid.getArmOperators(arm);
                    List<GridCell> operands = arm.operandCells();
                    for (int i = 0; i < ops.size(); i++) {
                        if (!first) sb.append(",\n");
                        first = false;
                        GridCell a = operands.get(i);
                        GridCell b = operands.get(i + 1);
                        sb.append("      {\"armIndex\": ").append(armIndex)
                          .append(", \"opIndex\": ").append(i)
                          .append(", \"betweenCells\": [{\"row\": ").append(a.row())
                          .append(", \"col\": ").append(a.col())
                          .append("}, {\"row\": ").append(b.row())
                          .append(", \"col\": ").append(b.col())
                          .append("}]")
                          .append(", \"correct\": \"").append(ops.get(i).symbol())
                          .append("\", \"options\": ").append(stringListToJson(allOpSymbols))
                          .append("}");
                    }
                }
                armIndex++;
            }
        } else {
            for (EquationId id : mask.hiddenSet()) {
                // Roll per-equation to decide whether to hide the operator
                if (random.nextDouble() >= opChance) continue;
                if (!first) sb.append(",\n");
                first = false;
                char correctOp;
                if (id.axis() == EquationId.Axis.HORIZONTAL) {
                    int row = id.lineIndex();
                    correctOp = grid.horizontalOperators[row][id.equationIndex()].symbol();
                } else {
                    int col = id.lineIndex();
                    correctOp = grid.verticalOperators[col][id.equationIndex()].symbol();
                }
                sb.append("      {\"axis\": \"").append(id.axis() == EquationId.Axis.HORIZONTAL ? "H" : "V")
                  .append("\", \"lineIndex\": ").append(id.lineIndex())
                  .append(", \"equationIndex\": ").append(id.equationIndex())
                  .append(", \"correct\": \"").append(correctOp)
                  .append("\", \"options\": ").append(stringListToJson(allOpSymbols))
                  .append("}");
            }
        }
    }

    private List<String> collectAllOperatorSymbols() {
        Set<String> symbols = new java.util.LinkedHashSet<>();
        if (registry != null) {
            for (Operator op : registry.all()) {
                symbols.add(String.valueOf(op.symbol()));
            }
        }
        if (grid.isShapeMode()) {
            for (EquationArm arm : grid.shape().arms()) {
                for (Operator op : grid.getArmOperators(arm)) {
                    symbols.add(String.valueOf(op.symbol()));
                }
            }
        } else {
            for (int row = 0; row < grid.config.matrixSize; row++)
                for (int eq = 0; eq < grid.config.equationsPerLine; eq++)
                    symbols.add(String.valueOf(grid.horizontalOperators[row][eq].symbol()));
            for (int col = 0; col < grid.config.matrixSize; col++)
                for (int eq = 0; eq < grid.config.equationsPerLine; eq++)
                    symbols.add(String.valueOf(grid.verticalOperators[col][eq].symbol()));
        }
        return new ArrayList<>(symbols);
    }

    private static String stringListToJson(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(list.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String listToJson(List<Integer> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
