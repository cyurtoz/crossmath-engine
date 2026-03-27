# CrossMath Puzzle Generation Algorithm

This document describes the two-phase algorithm used to generate CrossMath puzzles: **shape generation** (topology) followed by **numeric filling** (values and operators).

---

## High-Level Pipeline

```
PuzzleConfig
     │
     ▼
ShapeGenerator.generate()          ◄── Phase 1: Build topology
     │
     ▼
PuzzleShape (arms + seed cells, no values)
     │
     ▼
CrossMathGenerator.tryFillShape()  ◄── Phase 2: Assign operators and numbers
     │
     ▼
PuzzleGrid (fully solved)
     │
     ▼
EquationMask + PuzzlePrinter       ◄── Display: hide arms for playable puzzle
```

Each equation in the puzzle is a **3-cell arm**: `A op B = C`, laid out either horizontally or vertically on a sparse 2D grid.

---

## Phase 1: Shape Generation (ShapeGenerator)

The shape generator builds the puzzle's skeleton — deciding *where* arms go and *how* they connect — without assigning any numbers or operators.

### 1.1 Best-of-N Candidate Strategy

A single BFS run can get boxed in early (surrounded by claimed cells, near edges). To compensate, the generator runs **24 independent BFS attempts** and keeps the best one.

```
best ← null
for i in 1..24:
    candidate ← buildCandidateShape()
    if candidate.score > best.score:
        best ← candidate
    if candidate.armCount >= target:
        break early
return normalizeToTopLeft(best.shape)
```

Early exit when the target arm count is reached avoids wasting time on already-sufficient shapes.

### 1.2 BFS Growth Algorithm

Each candidate shape is grown from a single seed cell using breadth-first expansion:

```
Step 1: Pick a random starting cell near the grid centre
        row ← 1 + random(matrixSize - 2)
        col ← 1 + random(matrixSize - 2)

Step 2: Initialize BFS queue with the starting cell

Step 3: While queue is not empty AND armCount < target:
        source ← queue.poll()

        For each direction (H, V) in shuffled order:
            if source is already claimed in this direction → skip

            arm ← tryGrowArm(source, direction)
            if arm is null → skip

            Register arm:
                - Mark all 3 cells as claimed in this direction
                - Record the result cell

            Detect intersections:
                - Any cell now claimed in BOTH H and V → intersection
                - Add new intersections to seed set and queue

            Promote cells for future branching:
                - Each operand cell → 75% chance of entering queue
                - The result cell  → 100% chance of entering queue
```

### 1.3 Arm Placement (tryGrowArm)

An arm is 3 consecutive cells in one direction: `[A] [B] [C]` where A, B are operands and C is the result.

The source cell can be placed at position 0 (first operand) or position 1 (second operand). Both placements are tried in shuffled order.

```
For sourcePos in shuffle([0, 1]):
    first  ← source stepped back by sourcePos
    second ← source stepped back by (sourcePos - 1)
    third  ← source stepped back by (sourcePos - 2)

    Reject if:
        - Any cell is out of bounds
        - Any cell is already claimed in this direction
        - The result cell (third) is already a result of another arm

    Accept: return EquationArm([first, second], third, direction)
```

### 1.4 Cell Claiming Rules

Each grid cell has two independent claim slots: **horizontal** and **vertical**.

| Rule | Purpose |
|---|---|
| A cell can be claimed by at most 1 H arm | Prevents horizontal overlap |
| A cell can be claimed by at most 1 V arm | Prevents vertical overlap |
| A cell claimed in both H and V = intersection | Creates cross-equation dependencies |
| A result cell of arm X can be an operand of a perpendicular arm Y | Natural cross-linking |
| No cell can be the result of two different arms | Prevents conflicting computed values |

### 1.5 Promotion Probabilities

After placing an arm, its cells are probabilistically added back to the BFS queue as future growth points:

| Cell type | Promotion probability | Rationale |
|---|---|---|
| Operand cell | 75% | Good branching candidates; slight randomness creates asymmetry |
| Result cell | 100% | Always promoted — results are ideal cross-branch anchors since they will have known values during filling |

A cell is only promoted once (tracked via the seed cell set). This prevents the queue from growing unboundedly.

### 1.6 Shape Scoring

Each candidate shape is scored on four criteria, compared lexicographically:

| Priority | Metric | Better direction | Why |
|---|---|---|---|
| 1 (highest) | Arm count | More | Main objective: maximize equations |
| 2 | Intersection count | More | More cross-connections = richer puzzle |
| 3 | Occupied cell count | More | Wider coverage of the grid |
| 4 (lowest) | Bounding area | Less | Compactness tiebreaker |

```
isBetterThan(other):
    if armCount ≠ other.armCount → return armCount > other.armCount
    if intersections ≠ other    → return intersections > other
    if occupiedCells ≠ other    → return occupiedCells > other
    return boundingArea < other.boundingArea
```

### 1.7 Normalization

After selecting the best candidate, all coordinates are shifted so the top-left occupied cell is at (0, 0). This makes the shape independent of the random starting position.

```
minRow ← min row across all arm cells and seed cells
minCol ← min col across all arm cells and seed cells
shift every cell by (-minRow, -minCol)
```

---

## Phase 2: Numeric Filling (CrossMathGenerator)

Given a `PuzzleShape`, the filler assigns an operator and numeric values to every arm so that all equations are arithmetically correct.

### 2.1 Feasibility Validation

Before attempting generation, the filler checks:

```
required = numOperators × minUsagePerOperator
if required > targetEquationCount:
    throw error — impossible to satisfy usage quota
```

Shapes smaller than this minimum are also skipped during the retry loop.

### 2.2 Seed Root Cells

Non-result cells from the shape's seed set receive initial values from the **BracketedPicker** — a round-robin value selector that cycles through equal-width segments of the allowed range to ensure variety (not all small or all large numbers).

```
for each cell in shape.intersections():
    if cell is NOT a result cell of any arm:
        grid.setValue(cell, picker.next())
```

This gives the filler known anchor values to build equations from.

### 2.3 Topological Sort (Kahn's Algorithm)

Arms are sorted so that if arm A uses arm B's result cell as an operand, B is filled first. This ensures operand values are available when needed.

```
Build dependency graph:
    For each arm i, for each operand cell:
        if that cell is the result of arm j → edge j → i

Kahn's algorithm:
    ready ← arms with inDegree = 0
    shuffle(ready)  ← randomize processing order among independent arms

    while ready is not empty:
        arm ← ready.pop()
        output arm
        for each arm that depends on this one:
            decrement inDegree
            if inDegree = 0 → add to ready
```

### 2.4 Filling Each Arm

Each arm falls into one of four cases depending on which operands already have values:

#### Case 1: Both operands known

The simplest case — both values are fixed (from seeding or previous arms).

```
for each operator (usage-biased order):
    result ← op.apply(A, B)
    if result is valid (in bounds, not MIN_VALUE):
        assign result, set operator
        return success
```

#### Case 2: Left operand known, right free

```
for each operator (usage-biased order):
    rights ← op.validRightOperands(A, config)  // shuffled list
    if rights is not empty:
        B ← rights[0]
        result ← op.apply(A, B)
        if valid: assign B, result, set operator → success
```

#### Case 3: Right operand known, left free

```
for each operator (usage-biased order):
    lefts ← findValidLeftOperands(op, B)  // enumerate and shuffle
    if lefts is not empty:
        A ← lefts[0]
        result ← op.apply(A, B)
        if valid: assign A, result, set operator → success
```

#### Case 4: Both operands free

Iterates a shuffled pool of all values in `[minCellValue, maxSeedValue]`:

```
for each operator (usage-biased order):
    for each candidateA in shuffle([minCellValue..maxSeedValue]):
        rights ← op.validRightOperands(candidateA, config)
        if rights is not empty:
            B ← rights[0]
            result ← op.apply(candidateA, B)
            if valid: assign A, B, result, set operator → success
```

### 2.5 Usage-Biased Operator Selection

Operators are tried in a carefully designed order to ensure minimum usage requirements are met:

```
operatorsByAscendingUsage(usageCounts):
    1. Shuffle all operators (base randomness)
    2. Sort by priority:
        - Priority 0: operators below minUsagePerOperator (tried first)
        - Priority 1: operators that have met their quota
    3. Within same priority: sort by ascending usage count
```

This means under-represented operators get first chance at each arm, naturally balancing usage across the puzzle.

### 2.6 Retry Loop

If filling fails (no valid operator/value combination exists for some arm), the entire attempt is discarded and a new shape is generated. The outer loop retries up to `maxGenerationAttempts` (default: 10,000).

After successful filling, operator usage is checked against `minUsagePerOperator`. If any operator is under-used, the attempt is also discarded.

---

## Worked Example

### Configuration

```
matrixSize = 5, maxCellValue = 20, operators = {+, -}
minCellValue = 1, minUsagePerOperator = 1
```

### Phase 1: Shape generation

**Start**: Random cell (2, 2) near centre of 5×5 grid.

**BFS iteration 1** — source=(2,2), try HORIZONTAL:
```
Place arm: (2,1) + (2,2) = (2,3)    [H arm]
Claim (2,1), (2,2), (2,3) in H direction
Promote (2,1) → queue [75% chance, say yes]
Promote (2,2) → already in seed set
Promote (2,3) → queue [100%, result cell]
```

**BFS iteration 2** — source=(2,1), try VERTICAL:
```
Place arm: (1,1) + (2,1) = (3,1)    [V arm]
(2,1) now claimed in both H and V → intersection!
Promote (1,1) → queue
Promote (3,1) → queue
```

**BFS iteration 3** — source=(2,3), try VERTICAL:
```
Place arm: (1,3) + (2,3) = (3,3)    [V arm]
(2,3) now claimed in both H and V → intersection!
Promote (1,3) → queue
Promote (3,3) → queue
```

**BFS iteration 4** — source=(1,1), try HORIZONTAL:
```
Place arm: (1,0) + (1,1) = (1,2)    [H arm]
Promote cells...
```

After several more iterations, the shape might look like:

```
col:  0   1   2   3   4
row 0:
row 1: _   _   _
row 2:     _   _   _
row 3:     _       _
```

Where `_` represents occupied cells, forming a tree-like asymmetric structure.

### Phase 2: Numeric filling

**Seed root cells**: Intersections (2,1) and (2,3) get values from BracketedPicker → say 7 and 4.

**Topological sort**: Arms without dependencies first. Suppose the H arm at row 2 depends on no other arm's result.

**Fill arm (2,1)+(2,2)=(2,3)**: Both operands... wait, (2,3) is a result cell.
- (2,1) = 7 (seeded), (2,2) is free → Case 2 (left known, right free)
- Try `-`: validRightOperands(7) → [1,2,3,4,5,6] (7 - x ≥ 1)
- Pick 3 → result = 7 - 3 = 4. Set (2,2) = 3, (2,3) = 4.

**Fill arm (1,1)+(2,1)=(3,1)**: (2,1) = 7 is operand at position 1.
- (1,1) is free → Case 3 (right known, left free)
- Try `+`: findValidLeftOperands(+, 7) → values where A + 7 ≤ 20 → [1..13]
- Pick 5 → result = 5 + 7 = 12. Set (1,1) = 5, (3,1) = 12.

And so on for remaining arms, always respecting the topological order.

---

## Key Design Decisions

### Why BFS instead of random placement?

BFS from a single seed naturally produces **connected, tree-like** shapes. Random placement would create disconnected islands requiring complex merging logic.

### Why 24 candidates?

A single BFS run is highly sensitive to the random starting position and early direction choices. With 24 candidates, the probability of at least one good shape is very high, while the cost is low (shape generation is fast — no arithmetic involved).

### Why separate shape and filling phases?

**Separation of concerns**: the shape determines topology (which cells exist, how arms connect), while filling determines arithmetic (operators and values). This allows:
- Retrying fills on the same shape if needed
- Scoring shapes by structural quality without numeric bias
- Simpler debugging: shape bugs vs. arithmetic bugs are isolated

### Why topological sort for filling order?

When arm B's result cell is arm A's operand, A's value depends on B. Processing arms in dependency order ensures every operand is known when needed, reducing the filling problem to simple local choices.

### Why usage-biased operator selection?

Without bias, random operator choice often leaves one operator under-used (especially division, which has strict constraints). Prioritizing under-used operators ensures minimum quotas are met naturally, without expensive backtracking.

### Why BracketedPicker instead of uniform random?

Uniform random tends to cluster values (e.g., many small numbers). Round-robin bracketing ensures seed values span the full range — small, medium, and large numbers all appear, creating more varied and interesting puzzles.

---

## Complexity and Performance

| Phase | Time complexity | Notes |
|---|---|---|
| Shape generation (1 candidate) | O(target × matrixSize²) | BFS with constant-time claim checks |
| Shape generation (all candidates) | O(24 × target × matrixSize²) | 24 independent runs |
| Topological sort | O(arms²) | Kahn's with operand-to-result lookup |
| Arm filling (per arm) | O(operators × maxValue) | Worst case: enumerate all candidate values |
| Full filling | O(arms × operators × maxValue) | Each arm filled once |
| Outer retry loop | O(attempts × above) | Typically succeeds in 1–50 attempts |

For typical configurations (matrixSize ≤ 7, maxCellValue ≤ 200), generation completes in under 1 second.
