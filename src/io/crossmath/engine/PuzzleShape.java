package io.crossmath.engine;

import java.util.List;
import java.util.Set;

/**
 * The topology of a puzzle: which cells are used, how arms are laid out,
 * and which cells are intersections. Contains no numeric values.
 */
public class PuzzleShape {

    private final List<EquationArm> arms;
    private final Set<GridCell>     intersections;
    private final int               matrixSize;

    public PuzzleShape(List<EquationArm> arms, Set<GridCell> intersections, int matrixSize) {
        this.arms          = List.copyOf(arms);
        this.intersections = Set.copyOf(intersections);
        this.matrixSize    = matrixSize;
    }

    public List<EquationArm> arms()          { return arms; }
    public Set<GridCell>     intersections()  { return intersections; }
    public int               matrixSize()     { return matrixSize; }
    public int               armCount()       { return arms.size(); }
}
