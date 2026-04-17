package io.crossmath.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * BFS-based random asymmetric shape growth.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Pick a random {@link GridCell} near the center as the first intersection.</li>
 *   <li>Add to BFS queue.</li>
 *   <li>While queue not empty and {@code armCount < targetEquationCount}:
 *     <ul>
 *       <li>Pop a cell.</li>
 *       <li>Try all 4 growth directions (left, right, up, down) in shuffled order.</li>
 *       <li>For each grown arm, randomly promote free operand cells to
 *           intersections at ~45% probability and add them to the queue.</li>
 *     </ul>
 *   </li>
 *   <li>Return {@link PuzzleShape}.</li>
 * </ol>
 */
public class ShapeGenerator {

    private static final double PROMOTION_PROBABILITY = 0.45;

    private final PuzzleConfig config;
    private final Random random;

    public ShapeGenerator(PuzzleConfig config, Random random) {
        this.config = config;
        this.random = random;
    }

    public PuzzleShape generate() {
        int matrixSize = config.matrixSize;
        Map<GridCell, CellRole> cellRoles = new LinkedHashMap<>();
        Set<GridCell> intersections = new LinkedHashSet<>();
        List<EquationArm> arms = new ArrayList<>();

        int centerBias = matrixSize / 4;
        GridCell seed = new GridCell(
                centerBias + random.nextInt(matrixSize - 2 * centerBias),
                centerBias + random.nextInt(matrixSize - 2 * centerBias));
        cellRoles.put(seed, CellRole.INTERSECTION);
        intersections.add(seed);

        Deque<GridCell> queue = new ArrayDeque<>();
        queue.add(seed);

        while (!queue.isEmpty() && arms.size() < config.targetEquationCount) {
            GridCell source = queue.poll();

            List<int[]> directions = new ArrayList<>();
            directions.add(new int[]{0, 1});   // right
            directions.add(new int[]{0, -1});  // left
            directions.add(new int[]{1, 0});   // down
            directions.add(new int[]{-1, 0});  // up
            Collections.shuffle(directions, random);

            for (int[] dir : directions) {
                if (arms.size() >= config.targetEquationCount) break;

                ArmDirection armDir = dir[0] == 0 ? ArmDirection.HORIZONTAL : ArmDirection.VERTICAL;
                EquationArm arm = tryGrowArm(source, dir[0], dir[1], armDir, cellRoles, matrixSize);
                if (arm == null) continue;

                arms.add(arm);
                applyArm(arm, cellRoles, intersections, queue);
            }
        }

        ensureIntersectionConsistency(cellRoles, intersections, arms);

        return new PuzzleShape(arms, cellRoles, intersections, matrixSize);
    }

    private EquationArm tryGrowArm(GridCell source, int dr, int dc,
                                    ArmDirection armDir,
                                    Map<GridCell, CellRole> cellRoles, int matrixSize) {
        List<GridCell> operandCells = new ArrayList<>();
        operandCells.add(source);

        int freeCount = 0;
        int row = source.row() + dr;
        int col = source.col() + dc;

        while (inBounds(row, col, matrixSize)) {
            GridCell cell = new GridCell(row, col);
            CellRole existingRole = cellRoles.getOrDefault(cell, CellRole.EMPTY);

            if (existingRole == CellRole.RESULT) break;
            if (existingRole == CellRole.FREE_OPERAND) break;

            if (existingRole == CellRole.INTERSECTION) {
                operandCells.add(cell);
                row += dr;
                col += dc;
                continue;
            }

            // EMPTY cell
            if (freeCount >= config.maxArmFreeOperands) break;

            int nextRow = row + dr;
            int nextCol = col + dc;
            if (!inBounds(nextRow, nextCol, matrixSize)) break;

            operandCells.add(cell);
            freeCount++;
            row += dr;
            col += dc;
        }

        if (!inBounds(row, col, matrixSize)) return null;

        GridCell resultCell = new GridCell(row, col);
        CellRole resultRole = cellRoles.getOrDefault(resultCell, CellRole.EMPTY);
        if (resultRole != CellRole.EMPTY) return null;

        if (operandCells.size() < 2) return null;

        return new EquationArm(List.copyOf(operandCells), resultCell, armDir);
    }

    private void applyArm(EquationArm arm, Map<GridCell, CellRole> cellRoles,
                           Set<GridCell> intersections, Deque<GridCell> queue) {
        cellRoles.put(arm.resultCell(), CellRole.RESULT);

        for (int i = 1; i < arm.operandCells().size(); i++) {
            GridCell cell = arm.operandCells().get(i);
            CellRole existing = cellRoles.getOrDefault(cell, CellRole.EMPTY);

            if (existing == CellRole.INTERSECTION) continue;

            if (random.nextDouble() < PROMOTION_PROBABILITY) {
                cellRoles.put(cell, CellRole.INTERSECTION);
                intersections.add(cell);
                queue.add(cell);
            } else {
                cellRoles.put(cell, CellRole.FREE_OPERAND);
            }
        }
    }

    private void ensureIntersectionConsistency(Map<GridCell, CellRole> cellRoles,
                                                Set<GridCell> intersections,
                                                List<EquationArm> arms) {
        Map<GridCell, Set<ArmDirection>> directionsByCell = new LinkedHashMap<>();
        for (EquationArm arm : arms) {
            for (GridCell cell : arm.operandCells()) {
                directionsByCell.computeIfAbsent(cell, k -> new LinkedHashSet<>())
                        .add(arm.direction());
            }
        }

        for (var entry : directionsByCell.entrySet()) {
            if (entry.getValue().size() > 1) {
                GridCell cell = entry.getKey();
                cellRoles.put(cell, CellRole.INTERSECTION);
                intersections.add(cell);
            }
        }
    }

    private static boolean inBounds(int row, int col, int matrixSize) {
        return row >= 0 && row < matrixSize && col >= 0 && col < matrixSize;
    }
}
