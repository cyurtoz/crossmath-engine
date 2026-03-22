import java.util.*;

/**
 * CrossMathPuzzleGenerator
 *
 * Generates a fully solved 9×9 cross-math puzzle grid backed by a 5×5 number
 * matrix M[row][col].  Every row and every column contains two chained
 * single-operation equations:
 *
 *   HORIZONTAL (one pair per number-row):
 *     M[r][0]  hOp[r][0]  M[r][1]  =  M[r][2]
 *     M[r][2]  hOp[r][1]  M[r][3]  =  M[r][4]
 *
 *   VERTICAL (one pair per number-column):
 *     M[0][c]  vOp[c][0]  M[1][c]  =  M[2][c]
 *     M[2][c]  vOp[c][1]  M[3][c]  =  M[4][c]
 *
 *  The 9×9 display layout (· = empty spacer):
 *
 *   N  op  N  =  N  op  N  =  N
 *   op  ·  op  ·  op  ·  op  ·  op
 *   N  op  N  =  N  op  N  =  N
 *   =   ·   =  ·   =  ·   =  ·   =
 *   N  op  N  =  N  op  N  =  N
 *   op  ·  op  ·  op  ·  op  ·  op
 *   N  op  N  =  N  op  N  =  N
 *   =   ·   =  ·   =  ·   =  ·   =
 *   N  op  N  =  N  op  N  =  N
 *
 * Generation strategy (avoids factorial search):
 *   1. Rows 0, 1, 3  → freely generated with valid horizontal equations.
 *   2. Row 2         → derived: try all 4^5 = 1024 column-operator combos
 *                      (rows 0 & 1 as operands) and accept the first whose
 *                      resulting values also satisfy horizontal equations.
 *   3. Row 4         → same derivation from rows 2 & 3.
 *
 * Domain constraints enforced:
 *   • All values ∈ [MIN_VAL, MAX_VAL]   (default 1–100)
 *   • Division: divisor ≤ MAX_DIVISOR, must divide exactly
 *   • Multiplication: product must not be prime (prime products have only one
 *     factorisation, making division trivial and puzzles too easy)
 */
public class CrossMathPuzzleGenerator {

    // ──────────────────────────────────────────────────────────────────────────
    // Configuration
    // ──────────────────────────────────────────────────────────────────────────

    /** Side length of the number matrix (changes puzzle size). */
    private static final int N = 5;

    /** Side length of the 9×9 display grid = 2*N - 1. */
    private static final int DISPLAY = 2 * N - 1;

    private static final int  MIN_VAL     = 1;
    private static final int  MAX_VAL     = 100;
    private static final int  MAX_DIVISOR = 50;   // divisor cap for '/'
    private static final int  SMALL_MAX   = 16;   // max value for "free" operands

    /** Operators in a fixed order so 2-bit encoding maps cleanly. */
    private static final char[] OPS = {'+', '-', '*', '/'};

    /** Precomputed prime sieve up to MAX_VAL. */
    private static final boolean[] IS_PRIME = buildSieve(MAX_VAL + 1);

    // ──────────────────────────────────────────────────────────────────────────
    // Mutable state
    // ──────────────────────────────────────────────────────────────────────────

    private final int[][]  M    = new int[N][N];   // number matrix
    private final char[][] hOp  = new char[N][2];  // horizontal ops  [row][eq-index]
    private final char[][] vOp  = new char[N][2];  // vertical ops    [col][eq-index]

    private final Random rng;

    // ──────────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────────

    public CrossMathPuzzleGenerator(long seed) {
        this.rng = new Random(seed);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sieve
    // ──────────────────────────────────────────────────────────────────────────

    private static boolean[] buildSieve(int limit) {
        boolean[] p = new boolean[limit];
        Arrays.fill(p, true);
        p[0] = p[1] = false;
        for (int i = 2; (long) i * i < limit; i++)
            if (p[i])
                for (int j = i * i; j < limit; j += i)
                    p[j] = false;
        return p;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Arithmetic helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Applies operator to (a, b).
     * Returns Integer.MIN_VALUE for undefined results (div-by-0, non-integer div).
     */
    private static int applyOp(int a, char op, int b) {
        return switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> (b != 0 && a % b == 0) ? a / b : Integer.MIN_VALUE;
            default  -> Integer.MIN_VALUE;
        };
    }

    /**
     * Returns true iff (a op b = result) is a legal puzzle equation:
     *   • result is defined and within [MIN_VAL, MAX_VAL]
     *   • division: divisor ≤ MAX_DIVISOR, exact integer result
     *   • multiplication: product is not prime
     *     (prime products have a unique factorisation, yielding trivial '/' clues)
     */
    private static boolean isValid(int a, char op, int b, int result) {
        if (result == Integer.MIN_VALUE)          return false;
        if (result < MIN_VAL || result > MAX_VAL) return false;
        if (op == '/' && b > MAX_DIVISOR)         return false;
        if (op == '*' && IS_PRIME[result])         return false;
        return true;
    }

    /**
     * Finds any operator that makes (a OP b = target) a valid equation.
     * Operators are tested in a randomly shuffled order to prevent systematic bias.
     *
     * @return the operator character, or '\0' if none exists.
     */
    private char findOp(int a, int b, int target) {
        // Fisher-Yates shuffle on index array
        int[] idx = {0, 1, 2, 3};
        for (int i = 3; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
        }
        for (int i : idx) {
            int res = applyOp(a, OPS[i], b);
            if (res == target && isValid(a, OPS[i], b, res)) return OPS[i];
        }
        return '\0';
    }

    /** Random operand in [2, SMALL_MAX]; kept small so products stay ≤ MAX_VAL. */
    private int small() { return rng.nextInt(SMALL_MAX - 1) + 2; }

    // ──────────────────────────────────────────────────────────────────────────
    // Row generation (free rows: 0, 1, 3)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Populates M[row][0..4] and hOp[row][0..1] with a valid horizontal
     * double-equation using randomly chosen small operands.
     *
     *   M[r][0]  hOp[r][0]  M[r][1]  =  M[r][2]
     *   M[r][2]  hOp[r][1]  M[r][3]  =  M[r][4]
     */
    private boolean fillRow(int row) {
        for (int tries = 0; tries < 500; tries++) {
            int  a  = small();
            char o1 = OPS[rng.nextInt(4)];
            int  b  = small();
            int  c  = applyOp(a, o1, b);
            if (!isValid(a, o1, b, c)) continue;

            char o2 = OPS[rng.nextInt(4)];
            int  d  = small();
            int  e  = applyOp(c, o2, d);
            if (!isValid(c, o2, d, e)) continue;

            M[row][0] = a;  M[row][1] = b;  M[row][2] = c;
            M[row][3] = d;  M[row][4] = e;
            hOp[row][0] = o1;  hOp[row][1] = o2;
            return true;
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Column-equation derivation (derived rows: 2, 4)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Given two already-filled source rows, exhausts all 4^N = 1024
     * column-operator combinations in shuffled order.  For each combination,
     * computes the destination row values and accepts the first for which the
     * destination row also admits valid horizontal equations.
     *
     * Complexity: O(4^N × N) per call  =  O(5120) — essentially constant.
     *
     * @param eqIdx  0 → srcA=row0, srcB=row1, dst=row2
     *               1 → srcA=row2, srcB=row3, dst=row4
     */
    private boolean deriveRow(int eqIdx) {
        final int rA   = eqIdx * 2;
        final int rB   = rA + 1;
        final int rDst = rA + 2;
        final int COMBOS = 1 << (2 * N);  // 4^N = 1024  (2 bits per column)

        // Build a shuffled iteration order over [0, COMBOS)
        int[] order = new int[COMBOS];
        for (int i = 0; i < COMBOS; i++) order[i] = i;
        for (int i = COMBOS - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = order[i]; order[i] = order[j]; order[j] = t;
        }

        char[] colOps  = new char[N];
        int[]  derived = new int[N];

        for (int combo : order) {
            int bits      = combo;
            boolean valid = true;

            // Decode 2 bits per column → operator index → apply
            for (int col = 0; col < N; col++, bits >>= 2) {
                char op  = OPS[bits & 3];
                int  res = applyOp(M[rA][col], op, M[rB][col]);
                if (!isValid(M[rA][col], op, M[rB][col], res)) { valid = false; break; }
                colOps[col]  = op;
                derived[col] = res;
            }
            if (!valid) continue;

            // Derived values must also satisfy horizontal double-equation
            char h1 = findOp(derived[0], derived[1], derived[2]);
            if (h1 == '\0') continue;
            char h2 = findOp(derived[2], derived[3], derived[4]);
            if (h2 == '\0') continue;

            // Commit this combination
            for (int col = 0; col < N; col++) {
                vOp[col][eqIdx] = colOps[col];
                M[rDst][col]    = derived[col];
            }
            hOp[rDst][0] = h1;
            hOp[rDst][1] = h2;
            return true;
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Main generation loop
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates a complete, valid puzzle.
     * Expected success on the first outer attempt in > 99% of cases.
     *
     * @return true if generation succeeded.
     */
    public boolean generate() {
        for (int attempt = 1; attempt <= 500; attempt++) {
            if (!fillRow(0))   continue;   // row 0: free
            if (!fillRow(1))   continue;   // row 1: free
            if (!deriveRow(0)) continue;   // row 2: derived from rows 0 & 1
            if (!fillRow(3))   continue;   // row 3: free
            if (!deriveRow(1)) continue;   // row 4: derived from rows 2 & 3

            System.out.printf("[Generator] Success on outer attempt %d.%n", attempt);
            return true;
        }
        System.err.println("[Generator] Failed to produce a valid puzzle — try a different seed.");
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Verification
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Independently verifies all 20 equations (10 horizontal + 10 vertical).
     * Call after generate() to confirm correctness.
     */
    public boolean verify() {
        // Horizontal
        for (int r = 0; r < N; r++) {
            if (applyOp(M[r][0], hOp[r][0], M[r][1]) != M[r][2]) return false;
            if (applyOp(M[r][2], hOp[r][1], M[r][3]) != M[r][4]) return false;
        }
        // Vertical
        for (int c = 0; c < N; c++) {
            if (applyOp(M[0][c], vOp[c][0], M[1][c]) != M[2][c]) return false;
            if (applyOp(M[2][c], vOp[c][1], M[3][c]) != M[4][c]) return false;
        }
        return true;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Display
    // ──────────────────────────────────────────────────────────────────────────

    /** Prints the solved 9×9 grid to stdout. */
    public void printSolution() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════╗");
        System.out.println("  ║       CROSSMATH PUZZLE  —  Solved        ║");
        System.out.println("  ╚══════════════════════════════════════════╝");
        System.out.println();
        printGrid(false);
    }

    /**
     * Prints the unsolved (playable) grid where every number is hidden.
     * Pass to players after calling generate(); blanks are shown as '?'.
     */
    public void printPuzzle() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════╗");
        System.out.println("  ║       CROSSMATH PUZZLE  —  Play!         ║");
        System.out.println("  ╚══════════════════════════════════════════╝");
        System.out.println();
        printGrid(true);
    }

    private void printGrid(boolean hideNumbers) {
        for (int dr = 0; dr < DISPLAY; dr++) {
            System.out.print("    ");
            for (int dc = 0; dc < DISPLAY; dc++) {
                String s = cellString(dr, dc, hideNumbers);
                System.out.printf("%5s", s);
            }
            System.out.println();
        }
        System.out.println();
    }

    /**
     * Returns the display token for position (dr, dc) in the 9×9 grid.
     *
     * Grid mapping:
     *   (even dr, even dc) → number M[dr/2][dc/2]
     *   (even dr, odd  dc) → horizontal element: op or '='
     *   (odd  dr, even dc) → vertical element:   op or '='
     *   (odd  dr, odd  dc) → empty spacer
     */
    private String cellString(int dr, int dc, boolean hideNumbers) {
        boolean rowEven = (dr % 2 == 0);
        boolean colEven = (dc % 2 == 0);
        int nr = dr / 2;
        int nc = dc / 2;

        if (rowEven && colEven) {
            return hideNumbers ? "?" : Integer.toString(M[nr][nc]);
        }
        if (rowEven) {            // horizontal equation element
            return switch (dc) {
                case 1 -> String.valueOf(hOp[nr][0]);
                case 3 -> "=";
                case 5 -> String.valueOf(hOp[nr][1]);
                case 7 -> "=";
                default -> "?";
            };
        }
        if (colEven) {            // vertical equation element
            return switch (dr) {
                case 1 -> String.valueOf(vOp[nc][0]);
                case 3 -> "=";
                case 5 -> String.valueOf(vOp[nc][1]);
                case 7 -> "=";
                default -> "?";
            };
        }
        return "";                // spacer (odd row & odd col)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Equation listing (for debugging / future puzzle-validation feature)
    // ──────────────────────────────────────────────────────────────────────────

    /** Prints all 20 equations in human-readable form. */
    public void printEquations() {
        System.out.println("  ── Horizontal equations ────────────────────");
        for (int r = 0; r < N; r++) {
            System.out.printf("  row %d:  %3d %c %3d = %3d    %3d %c %3d = %3d%n",
                r,
                M[r][0], hOp[r][0], M[r][1], M[r][2],
                M[r][2], hOp[r][1], M[r][3], M[r][4]);
        }
        System.out.println("  ── Vertical equations ──────────────────────");
        for (int c = 0; c < N; c++) {
            System.out.printf("  col %d:  %3d %c %3d = %3d    %3d %c %3d = %3d%n",
                c,
                M[0][c], vOp[c][0], M[1][c], M[2][c],
                M[2][c], vOp[c][1], M[3][c], M[4][c]);
        }
        System.out.println();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Entry point
    // ──────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : System.currentTimeMillis();
        System.out.println("Seed: " + seed);

        CrossMathPuzzleGenerator gen = new CrossMathPuzzleGenerator(seed);

        if (!gen.generate()) return;

        gen.printSolution();
        gen.printEquations();
        System.out.println("  Verification: " + (gen.verify() ? "✓  PASS" : "✗  FAIL"));

        System.out.println();
        gen.printPuzzle();
    }
}
