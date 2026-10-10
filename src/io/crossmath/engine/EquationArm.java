package io.crossmath.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One equation in the puzzle: {@code operand[0] op operand[1] = result}.
 *
 * <p>The operator is assigned during the filling phase, not during shape
 * generation. The shape only determines cell positions.
 */
public class EquationArm {

    private final List<GridCell>  operandCells;
    private final GridCell        resultCell;
    private final ArmDirection    direction;
    private Operator              operator;   // set during filling

    public EquationArm(List<GridCell> operandCells, GridCell resultCell, ArmDirection direction) {
        this.operandCells = List.copyOf(operandCells);
        this.resultCell   = resultCell;
        this.direction    = direction;
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public List<GridCell> operandCells() { return operandCells; }
    public GridCell       resultCell()   { return resultCell; }
    public ArmDirection   direction()    { return direction; }
    public Operator       operator()     { return operator; }

    public void setOperator(Operator operator) { this.operator = operator; }

    /** All cells: operands first, then result (last). */
    public List<GridCell> allCells() {
        List<GridCell> all = new ArrayList<>(operandCells);
        all.add(resultCell);
        return Collections.unmodifiableList(all);
    }

    @Override
    public String toString() {
        return direction.name().charAt(0) + " " + operandCells + " → " + resultCell;
    }
}
