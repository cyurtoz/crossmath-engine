import java.util.*;

public class CrossmathCrossword {
    static class Cell {
        String val;
        boolean isNumber;
        Cell(String v, boolean n) { this.val = v; this.isNumber = n; }
    }

    private static final String[] OPS = {"+", "-", "*", "/"};
    private static final Random RAND = new Random();

    public static void main(String[] args) {
        // We use a large enough map to allow "erratic" branching
        Cell[][] grid = new Cell[30][30]; 
        for (int i = 0; i < 30; i++) {
            for (int j = 0; j < 30; j++) grid[i][j] = new Cell(" ", false);
        }

        generate(grid, 15, 15, 8); // Start in middle, generate 8 branches
        printGrid(grid);
    }

    public static void generate(Cell[][] grid, int startR, int startC, int count) {
        List<int[]> numCoords = new ArrayList<>();
        
        // Place initial horizontal equation
        placeEq(grid, startR, startC, true, numCoords);

        int placed = 1;
        while (placed < count) {
            if (numCoords.isEmpty()) break;
            
            // Pick an existing number to sprout from
            int[] anchor = numCoords.get(RAND.nextInt(numCoords.size()));
            boolean vertical = !hasAdjacentOp(grid, anchor[0], anchor[1]); // Sprout perpendicular

            if (trySprout(grid, anchor[0], anchor[1], vertical, numCoords)) {
                placed++;
            }
        }
    }

    private static boolean trySprout(Cell[][] grid, int r, int c, boolean vert, List<int[]> numCoords) {
        int existingVal = Integer.parseInt(grid[r][c].val);
        String op = OPS[RAND.nextInt(4)];
        
        // Logic: Is our anchor the A, B, or Result of the new equation?
        int pos = RAND.nextInt(3); 
        int a=0, b=0, res=0;

        // Math constraints for 0-100
        if (pos == 0) { a = existingVal; b = genValidB(a, op); res = doMath(a, op, b); }
        else if (pos == 1) { b = existingVal; a = RAND.nextInt(101); res = doMath(a, op, b); }
        else { res = existingVal; a = RAND.nextInt(101); op = (a > res) ? "-" : "+"; b = Math.abs(a - res); }

        if (res < 0 || res > 100 || a > 100) return false;

        // Calculate the new start point so the anchor is at 'pos'
        int sR = vert ? r - (pos * 2) : r;
        int sC = vert ? c : c - (pos * 2);

        if (isAreaClear(grid, sR, sC, vert, r, c)) {
            fill(grid, sR, sC, vert, a, op, b, res, numCoords);
            return true;
        }
        return false;
    }

    private static void fill(Cell[][] grid, int r, int c, boolean v, int a, String op, int b, int res, List<int[]> coords) {
        int dr = v ? 1 : 0; int dc = v ? 0 : 1;
        
        grid[r][c] = new Cell(String.valueOf(a), true);
        grid[r+dr][c+dc] = new Cell(op, false);
        grid[r+2*dr][c+2*dc] = new Cell(String.valueOf(b), true);
        grid[r+3*dr][c+3*dc] = new Cell("=", false);
        grid[r+4*dr][c+4*dc] = new Cell(String.valueOf(res), true);

        coords.add(new int[]{r, c});
        coords.add(new int[]{r+2*dr, c+2*dc});
        coords.add(new int[]{r+4*dr, c+4*dc});
    }

    private static int genValidB(int a, String op) {
        if (!op.equals("/")) return RAND.nextInt(101);
        List<Integer> factors = new ArrayList<>();
        for (int i = 1; i <= 100; i++) if (a % i == 0) factors.add(i);
        return factors.get(RAND.nextInt(factors.size()));
    }

    private static int doMath(int a, String op, int b) {
        if (op.equals("+")) return a + b;
        if (op.equals("-")) return a - b;
        if (op.equals("*")) return a * b;
        return (b == 0) ? 0 : a / b;
    }

    private static boolean isAreaClear(Cell[][] grid, int r, int c, boolean v, int skipR, int skipC) {
        // Logic to ensure we don't overwrite existing equations (except at the anchor)
        try {
            int dr = v ? 1 : 0; int dc = v ? 0 : 1;
            for (int i = 0; i < 5; i++) {
                int currR = r + (i * dr);
                int currC = c + (i * dc);
                if (currR == skipR && currC == skipC) continue;
                if (!grid[currR][currC].val.equals(" ")) return false;
            }
            return true;
        } catch (Exception e) { return false; }
    }

    private static boolean hasAdjacentOp(Cell[][] grid, int r, int c) {
        // Checks if there's already an operator above/below to force perpendicularity
        try {
            return !grid[r-1][c].val.equals(" ") || !grid[r+1][c].val.equals(" ");
        } catch (Exception e) { return false; }
    }

    private static void printGrid(Cell[][] grid) {
        for (Cell[] row : grid) {
            boolean empty = true;
            for (Cell cell : row) if (!cell.val.equals(" ")) empty = false;
            if (empty) continue;
            for (Cell cell : row) System.out.print(String.format("%3s", cell.val));
            System.out.println();
        }
    }

    private static void placeEq(Cell[][] grid, int r, int c, boolean h, List<int[]> coords) {
        fill(grid, r, c, !h, RAND.nextInt(50), "+", RAND.nextInt(50), 0, coords); // Dummy start
    }
}