package io.crossmath.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Controls which equations are visible in the puzzle display.
 *
 * <p>Hiding an equation blanks its operator symbol and '=' sign while keeping
 * all number cells intact — those cells still participate in perpendicular
 * equations. This varies the visible "shape" of the puzzle without
 * regenerating the underlying grid, multiplying the number of distinct
 * puzzles a single solved grid can produce.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   EquationMask mask = EquationMask.allVisible();
 *   mask.hide(new EquationId(Axis.HORIZONTAL, 2, 1));
 *
 *   // Or generate a random mask:
 *   EquationMask randomMask = EquationMask.random(config, 4, random);
 * }</pre>
 */
public class EquationMask {

    private final Set<EquationId> hiddenEquations = new HashSet<>();
    private final Set<Integer> hiddenArmIndices = new HashSet<>();

    // ── Mutation ──────────────────────────────────────────────────────────────

    public void hide(EquationId equationId) {
        hiddenEquations.add(equationId);
    }

    public void hide(EquationId.Axis axis, int lineIndex, int equationIndex) {
        hiddenEquations.add(new EquationId(axis, lineIndex, equationIndex));
    }

    public void hideArm(int armIndex) {
        hiddenArmIndices.add(armIndex);
    }

    public void show(EquationId equationId) {
        hiddenEquations.remove(equationId);
    }

    public void showArm(int armIndex) {
        hiddenArmIndices.remove(armIndex);
    }

    public void showAll() {
        hiddenEquations.clear();
        hiddenArmIndices.clear();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isVisible(EquationId equationId) {
        return !hiddenEquations.contains(equationId);
    }

    public boolean isVisible(EquationId.Axis axis, int lineIndex, int equationIndex) {
        return isVisible(new EquationId(axis, lineIndex, equationIndex));
    }

    public boolean isArmVisible(int armIndex) {
        return !hiddenArmIndices.contains(armIndex);
    }

    public int hiddenCount() {
        return hiddenEquations.size() + hiddenArmIndices.size();
    }

    public Set<EquationId> hiddenSet() {
        return Collections.unmodifiableSet(hiddenEquations);
    }

    public Set<Integer> hiddenArmSet() {
        return Collections.unmodifiableSet(hiddenArmIndices);
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /** All equations visible — used for the solution display. */
    public static EquationMask allVisible() {
        return new EquationMask();
    }

    /**
     * Randomly hides {@code countToHide} equations chosen without replacement.
     * For fixed-grid mode.
     */
    public static EquationMask random(PuzzleConfig config, int countToHide, Random random) {
        EquationMask mask = new EquationMask();
        List<EquationId> allEquationIds = buildAllEquationIds(config);
        Collections.shuffle(allEquationIds, random);

        int limit = Math.min(countToHide, allEquationIds.size());
        for (int i = 0; i < limit; i++) {
            mask.hide(allEquationIds.get(i));
        }
        return mask;
    }

    /**
     * Randomly hides {@code countToHide} arms chosen without replacement.
     * For shape-based mode.
     */
    public static EquationMask randomForShape(PuzzleShape shape, int countToHide, Random random) {
        return randomForArms(shape.arms().size(), countToHide, random);
    }

    /**
     * Randomly hides {@code countToHide} arms from {@code armCount} total.
     */
    public static EquationMask randomForArms(int armCount, int countToHide, Random random) {
        EquationMask mask = new EquationMask();
        List<Integer> indices = new ArrayList<>(armCount);
        for (int i = 0; i < armCount; i++) indices.add(i);
        Collections.shuffle(indices, random);

        int limit = Math.min(countToHide, armCount);
        for (int i = 0; i < limit; i++) {
            mask.hideArm(indices.get(i));
        }
        return mask;
    }

    private static List<EquationId> buildAllEquationIds(PuzzleConfig config) {
        List<EquationId> ids = new ArrayList<>(2 * config.matrixSize * config.equationsPerLine);

        for (int lineIndex = 0; lineIndex < config.matrixSize; lineIndex++) {
            for (int equationIndex = 0; equationIndex < config.equationsPerLine; equationIndex++) {
                ids.add(new EquationId(EquationId.Axis.HORIZONTAL, lineIndex, equationIndex));
                ids.add(new EquationId(EquationId.Axis.VERTICAL,   lineIndex, equationIndex));
            }
        }
        return ids;
    }

    // ── Smart mask builders ──────────────────────────────────────────────────

    /**
     * Smart mask for fixed-grid mode. Greedily hides equations while ensuring:
     * <ul>
     *   <li>Each hidden equation shares at least one cell with a visible equation
     *       (visible anchor constraint from grades.txt).</li>
     *   <li>No visible equation has more than {@code maxUnknownsPerEq} cells
     *       whose every equation is hidden (unknown-limit constraint).
     *       Pass -1 to disable this check.</li>
     * </ul>
     */
    public static EquationMask smartRandom(PuzzleConfig config, int countToHide,
                                           int maxUnknownsPerEq, Random random) {
        List<EquationId> allIds = buildAllEquationIds(config);

        for (int attempt = 0; attempt < 50; attempt++) {
            Collections.shuffle(allIds, random);
            EquationMask mask = new EquationMask();
            int hidden = 0;

            for (EquationId id : allIds) {
                if (hidden >= countToHide) break;
                mask.hide(id);

                if (!hasVisibleAnchors(mask, config) ||
                    (maxUnknownsPerEq >= 0 && !meetsUnknownLimit(mask, config, maxUnknownsPerEq))) {
                    mask.show(id);
                } else {
                    hidden++;
                }
            }

            if (hidden >= countToHide) return mask;
        }

        return random(config, countToHide, random);
    }

    /**
     * Smart mask for shape mode. Prefers hiding arms with fewer intersection
     * cells (grades.txt: "hide non-intersection cells first") and ensures each
     * hidden arm shares at least one cell with a visible arm.
     */
    public static EquationMask smartRandomForArms(PuzzleShape shape, int countToHide,
                                                   Random random) {
        int armCount = shape.armCount();
        List<int[]> scored = new ArrayList<>(armCount);

        for (int i = 0; i < armCount; i++) {
            EquationArm arm = shape.arms().get(i);
            int iCount = 0;
            for (GridCell c : arm.operandCells()) {
                if (shape.roleOf(c) == CellRole.INTERSECTION) iCount++;
            }
            scored.add(new int[]{i, iCount});
        }

        Collections.shuffle(scored, random);
        scored.sort(Comparator.comparingInt(a -> a[1]));

        EquationMask mask = new EquationMask();
        int hidden = 0;

        for (int[] entry : scored) {
            if (hidden >= countToHide) break;
            int idx = entry[0];
            mask.hideArm(idx);

            if (!hasShapeAnchors(mask, shape)) {
                mask.showArm(idx);
            } else {
                hidden++;
            }
        }

        return mask;
    }

    // ── Constraint helpers ───────────────────────────────────────────────────

    private static List<EquationId> equationsForCell(int row, int col, PuzzleConfig config) {
        List<EquationId> ids = new ArrayList<>(4);
        for (int eq = 0; eq < config.equationsPerLine; eq++) {
            if (col >= eq * 2 && col <= eq * 2 + 2) {
                ids.add(new EquationId(EquationId.Axis.HORIZONTAL, row, eq));
            }
            if (row >= eq * 2 && row <= eq * 2 + 2) {
                ids.add(new EquationId(EquationId.Axis.VERTICAL, col, eq));
            }
        }
        return ids;
    }

    private static boolean isCellUnknown(int row, int col,
                                          EquationMask mask, PuzzleConfig config) {
        for (EquationId id : equationsForCell(row, col, config)) {
            if (mask.isVisible(id)) return false;
        }
        return true;
    }

    private static int[][] cellsOfEquation(EquationId id) {
        if (id.axis() == EquationId.Axis.HORIZONTAL) {
            int row = id.lineIndex();
            int left = id.equationIndex() * 2;
            return new int[][]{{row, left}, {row, left + 1}, {row, left + 2}};
        } else {
            int col = id.lineIndex();
            int top = id.equationIndex() * 2;
            return new int[][]{{top, col}, {top + 1, col}, {top + 2, col}};
        }
    }

    private static boolean hasVisibleAnchors(EquationMask mask, PuzzleConfig config) {
        for (EquationId hidden : mask.hiddenSet()) {
            boolean hasAnchor = false;
            for (int[] cell : cellsOfEquation(hidden)) {
                if (!isCellUnknown(cell[0], cell[1], mask, config)) {
                    hasAnchor = true;
                    break;
                }
            }
            if (!hasAnchor) return false;
        }
        return true;
    }

    private static boolean meetsUnknownLimit(EquationMask mask, PuzzleConfig config,
                                              int maxUnknowns) {
        List<EquationId> allIds = buildAllEquationIds(config);
        for (EquationId id : allIds) {
            if (!mask.isVisible(id)) continue;
            int unknowns = 0;
            for (int[] cell : cellsOfEquation(id)) {
                if (isCellUnknown(cell[0], cell[1], mask, config)) {
                    unknowns++;
                }
            }
            if (unknowns > maxUnknowns) return false;
        }
        return true;
    }

    private static boolean hasShapeAnchors(EquationMask mask, PuzzleShape shape) {
        List<EquationArm> arms = shape.arms();
        Set<Integer> hiddenSet = mask.hiddenArmSet();

        for (int hiddenIdx : hiddenSet) {
            EquationArm hiddenArm = arms.get(hiddenIdx);
            boolean hasAnchor = false;

            for (GridCell cell : hiddenArm.allCells()) {
                if (hasAnchor) break;
                for (int otherIdx = 0; otherIdx < arms.size(); otherIdx++) {
                    if (otherIdx == hiddenIdx || hiddenSet.contains(otherIdx)) continue;
                    if (arms.get(otherIdx).allCells().contains(cell)) {
                        hasAnchor = true;
                        break;
                    }
                }
            }

            if (!hasAnchor) return false;
        }
        return true;
    }
}
