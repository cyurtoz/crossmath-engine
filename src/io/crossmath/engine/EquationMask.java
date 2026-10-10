package io.crossmath.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Controls which equation arms are visible in the puzzle display.
 *
 * <p>Hiding an arm blanks its operator symbol and '=' sign while keeping
 * all number cells intact — those cells still participate in perpendicular
 * equations.
 */
public class EquationMask {

    private final Set<Integer> hiddenArms = new HashSet<>();

    public void hideArm(int armIndex) {
        hiddenArms.add(armIndex);
    }

    public void showArm(int armIndex) {
        hiddenArms.remove(armIndex);
    }

    public boolean isArmVisible(int armIndex) {
        return !hiddenArms.contains(armIndex);
    }

    public int hiddenCount() {
        return hiddenArms.size();
    }

    public Set<Integer> hiddenSet() {
        return Collections.unmodifiableSet(hiddenArms);
    }

    // ── Factory methods ─────────────────────────────────────────────────────

    /** All arms visible — used for the solution display. */
    public static EquationMask allVisible() {
        return new EquationMask();
    }

    /**
     * Randomly hides {@code countToHide} arms chosen without replacement.
     */
    public static EquationMask random(int armCount, int countToHide, Random random) {
        EquationMask mask = new EquationMask();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < armCount; i++) indices.add(i);
        Collections.shuffle(indices, random);

        int limit = Math.min(countToHide, armCount);
        for (int i = 0; i < limit; i++) {
            mask.hideArm(indices.get(i));
        }
        return mask;
    }
}
