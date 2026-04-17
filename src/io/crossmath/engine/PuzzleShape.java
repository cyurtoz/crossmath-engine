package io.crossmath.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The geometric structure of a puzzle: arms, cell roles, and intersections.
 * Contains no numeric values — only the topology that {@link CrossMathGenerator}
 * will later fill with numbers and operators.
 *
 * <p>Built by {@link ShapeGenerator} via BFS growth from a seed cell.
 */
public class PuzzleShape {

    private final List<EquationArm> arms;
    private final Map<GridCell, CellRole> cellRoles;
    private final Set<GridCell> intersections;
    private final int matrixSize;

    public PuzzleShape(List<EquationArm> arms, Map<GridCell, CellRole> cellRoles,
                       Set<GridCell> intersections, int matrixSize) {
        this.arms          = List.copyOf(arms);
        this.cellRoles     = Collections.unmodifiableMap(new LinkedHashMap<>(cellRoles));
        this.intersections = Collections.unmodifiableSet(new LinkedHashSet<>(intersections));
        this.matrixSize    = matrixSize;
    }

    public List<EquationArm> arms() {
        return arms;
    }

    public int armCount() {
        return arms.size();
    }

    public Map<GridCell, CellRole> cellRoles() {
        return cellRoles;
    }

    public CellRole roleOf(GridCell cell) {
        return cellRoles.getOrDefault(cell, CellRole.EMPTY);
    }

    public Set<GridCell> intersections() {
        return intersections;
    }

    public int matrixSize() {
        return matrixSize;
    }

    public Set<GridCell> allClaimedCells() {
        return cellRoles.keySet();
    }

    public List<EquationArm> armsContaining(GridCell cell) {
        List<EquationArm> result = new ArrayList<>();
        for (EquationArm arm : arms) {
            if (arm.operandCells().contains(cell) || arm.resultCell().equals(cell)) {
                result.add(arm);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format("PuzzleShape{arms=%d, cells=%d, intersections=%d, matrixSize=%d}",
                arms.size(), cellRoles.size(), intersections.size(), matrixSize);
    }
}
