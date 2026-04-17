package io.crossmath.engine;

import java.util.List;

/**
 * An equation arm: a chain of operand cells ending in a result cell.
 *
 * <pre>
 *   operandCells[0]  op[0]  operandCells[1]  =  resultCell
 * </pre>
 *
 * The first operand cell (index 0) is always the source intersection
 * from which this arm was grown. Additional operand cells may be
 * intersections (shared with perpendicular arms) or free operands
 * (exclusively owned by this arm).
 */
public record EquationArm(List<GridCell> operandCells, GridCell resultCell, ArmDirection direction) {

    public int operandCount() {
        return operandCells.size();
    }

    public GridCell sourceCell() {
        return operandCells.get(0);
    }

    public List<GridCell> allCells() {
        var all = new java.util.ArrayList<>(operandCells);
        all.add(resultCell);
        return List.copyOf(all);
    }

    @Override
    public String toString() {
        return direction + " arm: " + operandCells + " = " + resultCell;
    }
}
