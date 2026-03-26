package io.crossmath.engine;

import java.util.ArrayList;
import java.util.Collections;
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

    // ── Mutation ──────────────────────────────────────────────────────────────

    public void hide(EquationId equationId) {
        hiddenEquations.add(equationId);
    }

    public void hide(EquationId.Axis axis, int lineIndex, int equationIndex) {
        hiddenEquations.add(new EquationId(axis, lineIndex, equationIndex));
    }

    public void show(EquationId equationId) {
        hiddenEquations.remove(equationId);
    }

    public void showAll() {
        hiddenEquations.clear();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isVisible(EquationId equationId) {
        return !hiddenEquations.contains(equationId);
    }

    public boolean isVisible(EquationId.Axis axis, int lineIndex, int equationIndex) {
        return isVisible(new EquationId(axis, lineIndex, equationIndex));
    }

    public int hiddenCount() {
        return hiddenEquations.size();
    }

    public Set<EquationId> hiddenSet() {
        return Collections.unmodifiableSet(hiddenEquations);
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /** All equations visible — used for the solution display. */
    public static EquationMask allVisible() {
        return new EquationMask();
    }

    /**
     * Randomly hides {@code countToHide} equations chosen without replacement.
     * Each call with a different {@code random} state produces a different shape.
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
}
