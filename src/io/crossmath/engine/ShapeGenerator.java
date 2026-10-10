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
 *   <li>Build several random BFS candidates from near-centre seed cells.</li>
 *   <li>While a candidate queue is not empty and armCount &lt; target:
 *       grow 3-cell arms ({@code A op B = C}) in shuffled directions.</li>
 *   <li>After each successful arm, keep true crossings and promote extra
 *       branch anchors so the queue continues expanding.</li>
 *   <li>Score candidates by arm count, crossings, coverage, and compactness.</li>
 *   <li>Normalize the winner to the top-left corner and return it.</li>
 * </ol>
 *
 * <h3>Cell claiming rules</h3>
 * Each cell can be claimed by at most one horizontal arm and one vertical arm.
 * A cell claimed in both directions is an intersection. A result cell of one
 * arm can serve as an operand of a perpendicular arm — this creates
 * cross-direction intersections naturally.
 *
 * <p>The returned "intersection" set is also reused as the list of seedable
 * root cells, so it may contain promoted branch anchors in addition to literal
 * horizontal/vertical crossings.
 */
public class ShapeGenerator {

    private static final int    SHAPE_CANDIDATE_ATTEMPTS     = 24;
    private static final double OPERAND_PROMOTION_PROBABILITY = 0.75;
    private static final double RESULT_PROMOTION_PROBABILITY  = 1.00;

    private final PuzzleConfig config;
    private final Random       random;

    public ShapeGenerator(PuzzleConfig config, Random random) {
        this.config = config;
        this.random = random;
    }

    public PuzzleShape generate() {
        CandidateShape best = null;

        // Any single BFS run can get boxed in early, so keep the best shape
        // seen across several randomized growth attempts.
        for (int i = 0; i < SHAPE_CANDIDATE_ATTEMPTS; i++) {
            CandidateShape candidate = buildCandidateShape();
            if (best == null || candidate.score().isBetterThan(best.score())) {
                best = candidate;
            }
            if (candidate.shape().armCount() >= config.targetEquationCount) {
                break;
            }
        }

        if (best == null) {
            return new PuzzleShape(List.of(), Set.of(), config.matrixSize);
        }

        return normalizeToTopLeft(best.shape());
    }

    private CandidateShape buildCandidateShape() {
        int matrixSize = config.matrixSize;
        int target     = config.targetEquationCount;

        // Per-direction claim tracking
        boolean[][] hClaimed = new boolean[matrixSize][matrixSize];
        boolean[][] vClaimed = new boolean[matrixSize][matrixSize];
        Set<GridCell> resultCells = new HashSet<>();

        List<EquationArm> arms = new ArrayList<>();
        // seedCells drive both future BFS expansion and later root-value
        // seeding. actualIntersections tracks only true cross-direction claims.
        Set<GridCell> seedCells = new LinkedHashSet<>();
        Set<GridCell> actualIntersections = new LinkedHashSet<>();

        // Start near the centre for maximum growth room
        GridCell start = new GridCell(
            1 + random.nextInt(Math.max(1, matrixSize - 2)),
            1 + random.nextInt(Math.max(1, matrixSize - 2))
        );
        seedCells.add(start);

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
                        actualIntersections.add(cell);
                        if (seedCells.add(cell)) {
                            queue.add(cell);
                        }
                    }
                }

                // Promote free operand cells so the shape keeps branching.
                for (GridCell cell : arm.operandCells()) {
                    promoteSeedCell(cell, seedCells, queue, OPERAND_PROMOTION_PROBABILITY);
                }

                // Result cells make good cross-branch anchors for future arms.
                promoteSeedCell(arm.resultCell(), seedCells, queue, RESULT_PROMOTION_PROBABILITY);
            }
        }

        PuzzleShape shape = new PuzzleShape(arms, seedCells, matrixSize);
        // Prefer candidates that hit the target arm count, form crossings,
        // cover more cells, and only then sprawl less.
        return new CandidateShape(
            shape,
            new ShapeScore(
                shape.armCount(),
                actualIntersections.size(),
                countOccupiedCells(shape),
                boundingArea(shape))
        );
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

    private void promoteSeedCell(GridCell cell, Set<GridCell> seedCells,
                                  Deque<GridCell> queue, double probability) {
        if (seedCells.contains(cell)) {
            return;
        }
        // Re-queue promoted cells as future branching points even if they are
        // not geometric intersections yet.
        if (random.nextDouble() <= probability) {
            seedCells.add(cell);
            queue.add(cell);
        }
    }

    private static PuzzleShape normalizeToTopLeft(PuzzleShape shape) {
        if (shape.armCount() == 0) {
            return shape;
        }

        // Canonicalize coordinates so the chosen shape does not depend on the
        // random starting position inside the matrix.
        int minRow = Integer.MAX_VALUE;
        int minCol = Integer.MAX_VALUE;

        for (EquationArm arm : shape.arms()) {
            for (GridCell cell : arm.allCells()) {
                minRow = Math.min(minRow, cell.row());
                minCol = Math.min(minCol, cell.col());
            }
        }
        for (GridCell cell : shape.intersections()) {
            minRow = Math.min(minRow, cell.row());
            minCol = Math.min(minCol, cell.col());
        }

        if (minRow == 0 && minCol == 0) {
            return shape;
        }

        List<EquationArm> shiftedArms = new ArrayList<>();
        for (EquationArm arm : shape.arms()) {
            List<GridCell> shiftedOperands = new ArrayList<>();
            for (GridCell cell : arm.operandCells()) {
                shiftedOperands.add(shift(cell, minRow, minCol));
            }
            shiftedArms.add(new EquationArm(
                shiftedOperands,
                shift(arm.resultCell(), minRow, minCol),
                arm.direction()));
        }

        Set<GridCell> shiftedSeedCells = new LinkedHashSet<>();
        for (GridCell cell : shape.intersections()) {
            shiftedSeedCells.add(shift(cell, minRow, minCol));
        }

        return new PuzzleShape(shiftedArms, shiftedSeedCells, shape.matrixSize());
    }

    private static GridCell shift(GridCell cell, int rowOffset, int colOffset) {
        return new GridCell(cell.row() - rowOffset, cell.col() - colOffset);
    }

    private static int countOccupiedCells(PuzzleShape shape) {
        Set<GridCell> occupiedCells = new HashSet<>();
        for (EquationArm arm : shape.arms()) {
            occupiedCells.addAll(arm.allCells());
        }
        return occupiedCells.size();
    }

    private static int boundingArea(PuzzleShape shape) {
        if (shape.armCount() == 0) {
            return 0;
        }

        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        int minCol = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;

        for (EquationArm arm : shape.arms()) {
            for (GridCell cell : arm.allCells()) {
                minRow = Math.min(minRow, cell.row());
                maxRow = Math.max(maxRow, cell.row());
                minCol = Math.min(minCol, cell.col());
                maxCol = Math.max(maxCol, cell.col());
            }
        }

        return (maxRow - minRow + 1) * (maxCol - minCol + 1);
    }

    private record CandidateShape(PuzzleShape shape, ShapeScore score) {}

    private record ShapeScore(int armCount, int intersectionCount,
                              int occupiedCellCount, int boundingArea) {
        boolean isBetterThan(ShapeScore other) {
            // Prefer larger and more interconnected shapes; compactness only
            // breaks ties between otherwise similar candidates.
            if (armCount != other.armCount) {
                return armCount > other.armCount;
            }
            if (intersectionCount != other.intersectionCount) {
                return intersectionCount > other.intersectionCount;
            }
            if (occupiedCellCount != other.occupiedCellCount) {
                return occupiedCellCount > other.occupiedCellCount;
            }
            return boundingArea < other.boundingArea;
        }
    }
}
