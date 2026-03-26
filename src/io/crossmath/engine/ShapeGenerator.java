package io.crossmath.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * BFS growth algorithm that builds asymmetric puzzle shapes.
 *
 * <ol>
 *   <li>Pick a random cell as the first intersection and add to queue.</li>
 *   <li>While queue is not empty and armCount &lt; target:
 *       <ul>
 *         <li>Pop a cell.</li>
 *         <li>Try HORIZONTAL and VERTICAL in shuffled order.</li>
 *         <li>For each direction, attempt to grow a 3-cell arm
 *             ({@code A op B = C}) that includes the cell as an operand.</li>
 *         <li>On success: register the arm, promote ~45% of free operand
 *             cells to intersections, add them to the queue.</li>
 *       </ul>
 *   </li>
 *   <li>Return the {@link PuzzleShape}.</li>
 * </ol>
 *
 * <h3>Cell claiming rules</h3>
 * Each cell can be claimed by at most one horizontal arm and one vertical arm.
 * A cell claimed in both directions is an intersection. A result cell of one
 * arm can serve as an operand of a perpendicular arm — this creates
 * cross-direction intersections naturally.
 */
public class ShapeGenerator {

    private static final double PROMOTION_PROBABILITY = 0.45;

    private final PuzzleConfig config;
    private final Random       random;

    public ShapeGenerator(PuzzleConfig config, Random random) {
        this.config = config;
        this.random = random;
    }

    public PuzzleShape generate() {
        int matrixSize = config.matrixSize;
        int target     = config.targetEquationCount;

        // Per-direction claim tracking
        boolean[][] hClaimed = new boolean[matrixSize][matrixSize];
        boolean[][] vClaimed = new boolean[matrixSize][matrixSize];
        Set<GridCell> resultCells = new HashSet<>();

        List<EquationArm>   arms          = new ArrayList<>();
        Set<GridCell>        intersections = new LinkedHashSet<>();

        // Start near the centre for maximum growth room
        GridCell start = new GridCell(
            1 + random.nextInt(Math.max(1, matrixSize - 2)),
            1 + random.nextInt(Math.max(1, matrixSize - 2))
        );
        intersections.add(start);

        Deque<GridCell> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty() && arms.size() < target) {
            GridCell source = queue.poll();

            List<ArmDirection> dirs = new ArrayList<>(List.of(ArmDirection.values()));
            Collections.shuffle(dirs, random);

            for (ArmDirection dir : dirs) {
                if (arms.size() >= target) break;

                // Skip if source already claimed in this direction
                if (isClaimed(source, dir, hClaimed, vClaimed)) continue;

                EquationArm arm = tryGrowArm(source, dir, hClaimed, vClaimed,
                                              resultCells, matrixSize);
                if (arm == null) continue;

                // Register arm
                arms.add(arm);
                for (GridCell cell : arm.allCells()) {
                    claim(cell, dir, hClaimed, vClaimed);
                }
                resultCells.add(arm.resultCell());

                // Detect new intersections (cells now claimed in both directions)
                for (GridCell cell : arm.allCells()) {
                    if (hClaimed[cell.row()][cell.col()] && vClaimed[cell.row()][cell.col()]) {
                        if (intersections.add(cell)) {
                            queue.add(cell);
                        }
                    }
                }

                // Promote free operand cells to intersections (~45%)
                for (GridCell cell : arm.operandCells()) {
                    if (intersections.contains(cell)) continue;
                    if (random.nextDouble() < PROMOTION_PROBABILITY) {
                        intersections.add(cell);
                        queue.add(cell);
                    }
                }
            }
        }

        return new PuzzleShape(arms, intersections, matrixSize);
    }

    // ── Arm growth ─────────────────────────────────────────────────────────

    /**
     * Tries to place a 3-cell arm ({@code A op B = C}) that includes
     * {@code source} as one of the two operand cells. The source can be
     * at position 0 (first operand) or position 1 (second operand); both
     * placements are tried in shuffled order.
     */
    private EquationArm tryGrowArm(GridCell source, ArmDirection dir,
                                    boolean[][] hClaimed, boolean[][] vClaimed,
                                    Set<GridCell> resultCells, int matrixSize) {

        List<Integer> positions = new ArrayList<>(List.of(0, 1));
        Collections.shuffle(positions, random);

        for (int sourcePos : positions) {
            GridCell first  = stepN(source, dir, -sourcePos);
            GridCell second = stepN(source, dir, -sourcePos + 1);
            GridCell third  = stepN(source, dir, -sourcePos + 2);

            if (!first.inBounds(matrixSize)  ||
                !second.inBounds(matrixSize) ||
                !third.inBounds(matrixSize)) {
                continue;
            }

            // All three must be unclaimed in this direction
            if (isClaimed(first,  dir, hClaimed, vClaimed) ||
                isClaimed(second, dir, hClaimed, vClaimed) ||
                isClaimed(third,  dir, hClaimed, vClaimed)) {
                continue;
            }

            // Result cell must not already be the result of another arm
            if (resultCells.contains(third)) {
                continue;
            }

            return new EquationArm(List.of(first, second), third, dir);
        }

        return null;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static GridCell stepN(GridCell cell, ArmDirection dir, int steps) {
        return switch (dir) {
            case HORIZONTAL -> new GridCell(cell.row(), cell.col() + steps);
            case VERTICAL   -> new GridCell(cell.row() + steps, cell.col());
        };
    }

    private static boolean isClaimed(GridCell cell, ArmDirection dir,
                                      boolean[][] hClaimed, boolean[][] vClaimed) {
        return dir == ArmDirection.HORIZONTAL
            ? hClaimed[cell.row()][cell.col()]
            : vClaimed[cell.row()][cell.col()];
    }

    private static void claim(GridCell cell, ArmDirection dir,
                               boolean[][] hClaimed, boolean[][] vClaimed) {
        if (dir == ArmDirection.HORIZONTAL) {
            hClaimed[cell.row()][cell.col()] = true;
        } else {
            vClaimed[cell.row()][cell.col()] = true;
        }
    }
}
