package ubc.cosc322;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;

/**
 * Heuristic evaluator and board utility methods for the Game of the Amazons.
 *
 * Board model:
 * - Flattened 11x11 array list.
 * - Playable coordinates are [1..10] x [1..10].
 * - 0 = empty, 1 = black queen, 2 = white queen, 3 = arrow.
 */
public class HeuristicEvaluator {

    private static final int BOARD_DIM = 11;
    private static final int BOARD_MIN = 1;
    private static final int BOARD_MAX = 10;

    private static final int EMPTY = 0;
    private static final int BLACK_QUEEN = 1;
    private static final int WHITE_QUEEN = 2;
    private static final int ARROW = 3;

    private static final int INF = Integer.MAX_VALUE;
    private static final int WIN_SCORE = 900_000;

    private static final int[][] DIRS = new int[][]{
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},           {0, 1},
            {1, -1},  {1, 0},  {1, 1}
    };

    /**
     * Generates all legal queen-like moves from a position on the current board.
     */
    public ArrayList<ArrayList<Integer>> generateValidMoves(ArrayList<Integer> boardState, ArrayList<Integer> pos) {
        ArrayList<ArrayList<Integer>> moves = new ArrayList<>();
        if (boardState == null || pos == null || pos.size() < 2) {
            return moves;
        }

        int row = pos.get(0);
        int col = pos.get(1);
        for (int[] dir : DIRS) {
            int r = row + dir[0];
            int c = col + dir[1];
            while (isInside(r, c) && getCell(boardState, r, c) == EMPTY) {
                ArrayList<Integer> next = new ArrayList<>(2);
                next.add(r);
                next.add(c);
                moves.add(next);
                r += dir[0];
                c += dir[1];
            }
        }
        return moves;
    }

    /**
     * Applies a move (queen movement + arrow shot) to a board state in place.
     */
    public void applyMove(ArrayList<Integer> boardState, ArrayList<Integer> from,
                          ArrayList<Integer> to, ArrayList<Integer> arrow) {
        if (boardState == null || from == null || to == null || arrow == null) {
            return;
        }
        if (from.size() < 2 || to.size() < 2 || arrow.size() < 2) {
            return;
        }

        int fromR = from.get(0), fromC = from.get(1);
        int toR = to.get(0), toC = to.get(1);
        int arrowR = arrow.get(0), arrowC = arrow.get(1);
        if (!isInside(fromR, fromC) || !isInside(toR, toC) || !isInside(arrowR, arrowC)) {
            return;
        }

        int movingPiece = getCell(boardState, fromR, fromC);
        setCell(boardState, fromR, fromC, EMPTY);
        setCell(boardState, toR, toC, movingPiece);
        setCell(boardState, arrowR, arrowC, ARROW);
    }

    /**
     * Evaluates the board from the perspective of playerCode.
     *
     * This evaluator is designed to:
     * 1) value large and continuous reachable space,
     * 2) reward positions that restrict/close opponent movement channels.
     */
    public int evaluate(ArrayList<Integer> boardState, int playerCode) {
        if (boardState == null || boardState.size() < BOARD_DIM * BOARD_DIM) {
            return 0;
        }

        int otherCode = (playerCode == BLACK_QUEEN) ? WHITE_QUEEN : BLACK_QUEEN;

        int mobilitySelf = countMobility(boardState, playerCode);
        int mobilityOpp = countMobility(boardState, otherCode);
        if (mobilitySelf == 0) return -WIN_SCORE;
        if (mobilityOpp == 0) return WIN_SCORE;

        int[] distSelf = computeQueenDistanceMap(boardState, playerCode);
        int[] distOpp = computeQueenDistanceMap(boardState, otherCode);

        int mobilityScore = mobilitySelf - mobilityOpp;
        int territoryScore = computeTerritoryScore(boardState, distSelf, distOpp);
        int regionScore = computeRegionScore(boardState, distSelf, distOpp);
        int continuityScore = computeContinuityScore(boardState, distSelf, distOpp);
        int trapPressureScore = computeTrapPressureScore(boardState, playerCode, otherCode);

        int arrowCount = countArrows(boardState);
        int mobilityWeight;
        int territoryWeight;
        int regionWeight;
        int continuityWeight;
        int trapWeight;

        if (arrowCount < 18) {
            mobilityWeight = 4;
            territoryWeight = 3;
            regionWeight = 2;
            continuityWeight = 2;
            trapWeight = 2;
        } else if (arrowCount < 45) {
            mobilityWeight = 3;
            territoryWeight = 5;
            regionWeight = 5;
            continuityWeight = 4;
            trapWeight = 5;
        } else {
            mobilityWeight = 2;
            territoryWeight = 6;
            regionWeight = 7;
            continuityWeight = 7;
            trapWeight = 9;
        }

        return mobilityWeight * mobilityScore
                + territoryWeight * territoryScore
                + regionWeight * regionScore
                + continuityWeight * continuityScore
                + trapWeight * trapPressureScore;
    }

    /**
     * Counts total legal queen destinations over all queens of a side.
     */
    private int countMobility(ArrayList<Integer> boardState, int playerCode) {
        int total = 0;
        for (int r = BOARD_MIN; r <= BOARD_MAX; r++) {
            for (int c = BOARD_MIN; c <= BOARD_MAX; c++) {
                if (getCell(boardState, r, c) == playerCode) {
                    total += countMovesFrom(boardState, r, c);
                }
            }
        }
        return total;
    }

    /**
     * Counts all queen-like ray moves from one square.
     */
    private int countMovesFrom(ArrayList<Integer> boardState, int row, int col) {
        int count = 0;
        for (int[] dir : DIRS) {
            int r = row + dir[0];
            int c = col + dir[1];
            while (isInside(r, c) && getCell(boardState, r, c) == EMPTY) {
                count++;
                r += dir[0];
                c += dir[1];
            }
        }
        return count;
    }

    /**
     * Computes minimum queen-move distance from player's queens to every square.
     */
    private int[] computeQueenDistanceMap(ArrayList<Integer> boardState, int playerCode) {
        int[] dist = new int[BOARD_DIM * BOARD_DIM];
        Arrays.fill(dist, INF);
        Deque<int[]> queue = new ArrayDeque<>();

        for (int r = BOARD_MIN; r <= BOARD_MAX; r++) {
            for (int c = BOARD_MIN; c <= BOARD_MAX; c++) {
                if (getCell(boardState, r, c) == playerCode) {
                    int idx = toIndex(r, c);
                    dist[idx] = 0;
                    queue.offer(new int[]{r, c});
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int row = current[0];
            int col = current[1];
            int baseDist = dist[toIndex(row, col)];

            for (int[] dir : DIRS) {
                int r = row + dir[0];
                int c = col + dir[1];
                while (isInside(r, c) && getCell(boardState, r, c) == EMPTY) {
                    int idx = toIndex(r, c);
                    if (dist[idx] > baseDist + 1) {
                        dist[idx] = baseDist + 1;
                        queue.offer(new int[]{r, c});
                    }
                    r += dir[0];
                    c += dir[1];
                }
            }
        }
        return dist;
    }

    /**
     * Square-wise space control (voronoi-like with bounded distance advantage).
     */
    private int computeTerritoryScore(ArrayList<Integer> boardState, int[] distSelf, int[] distOpp) {
        int score = 0;

        for (int r = BOARD_MIN; r <= BOARD_MAX; r++) {
            for (int c = BOARD_MIN; c <= BOARD_MAX; c++) {
                if (getCell(boardState, r, c) != EMPTY) {
                    continue;
                }

                int idx = toIndex(r, c);
                int ds = distSelf[idx];
                int doo = distOpp[idx];
                boolean selfReach = ds != INF;
                boolean oppReach = doo != INF;

                if (selfReach && !oppReach) {
                    score += 7;
                } else if (!selfReach && oppReach) {
                    score -= 7;
                } else if (selfReach) {
                    int diff = clamp(doo - ds, -3, 3);
                    score += diff;
                }
            }
        }
        return score;
    }

    /**
     * Region ownership by connected empty components.
     */
    private int computeRegionScore(ArrayList<Integer> boardState, int[] distSelf, int[] distOpp) {
        boolean[] visited = new boolean[BOARD_DIM * BOARD_DIM];
        int score = 0;

        for (int r = BOARD_MIN; r <= BOARD_MAX; r++) {
            for (int c = BOARD_MIN; c <= BOARD_MAX; c++) {
                if (getCell(boardState, r, c) != EMPTY) {
                    continue;
                }

                int startIdx = toIndex(r, c);
                if (visited[startIdx]) {
                    continue;
                }

                int regionSize = 0;
                int minSelfDist = INF;
                int minOppDist = INF;
                int selfCloserCells = 0;
                int oppCloserCells = 0;
                boolean selfReach = false;
                boolean oppReach = false;

                Deque<int[]> queue = new ArrayDeque<>();
                queue.offer(new int[]{r, c});
                visited[startIdx] = true;

                while (!queue.isEmpty()) {
                    int[] cur = queue.poll();
                    int row = cur[0];
                    int col = cur[1];
                    int idx = toIndex(row, col);
                    regionSize++;

                    int ds = distSelf[idx];
                    int doo = distOpp[idx];
                    boolean cellSelfReach = ds != INF;
                    boolean cellOppReach = doo != INF;

                    if (cellSelfReach) {
                        selfReach = true;
                        minSelfDist = Math.min(minSelfDist, ds);
                    }
                    if (cellOppReach) {
                        oppReach = true;
                        minOppDist = Math.min(minOppDist, doo);
                    }
                    if (cellSelfReach && cellOppReach) {
                        if (ds < doo) selfCloserCells++;
                        else if (doo < ds) oppCloserCells++;
                    }

                    for (int[] dir : DIRS) {
                        int nr = row + dir[0];
                        int nc = col + dir[1];
                        if (!isInside(nr, nc) || getCell(boardState, nr, nc) != EMPTY) {
                            continue;
                        }
                        int nIdx = toIndex(nr, nc);
                        if (!visited[nIdx]) {
                            visited[nIdx] = true;
                            queue.offer(new int[]{nr, nc});
                        }
                    }
                }

                if (selfReach && !oppReach) {
                    score += 6 * regionSize + (regionSize * regionSize) / 12;
                } else if (!selfReach && oppReach) {
                    score -= 6 * regionSize + (regionSize * regionSize) / 12;
                } else if (selfReach && oppReach) {
                    int entryAdv = clamp(minOppDist - minSelfDist, -3, 3);
                    int entryWeight = Math.max(2, regionSize / 4);
                    int control = selfCloserCells - oppCloserCells;
                    score += entryAdv * entryWeight;
                    score += control / 2;
                }
            }
        }
        return score;
    }

    /**
     * Explicitly values continuous reachable space and large owned areas.
     */
    private int computeContinuityScore(ArrayList<Integer> boardState, int[] distSelf, int[] distOpp) {
        boolean[] visited = new boolean[BOARD_DIM * BOARD_DIM];

        int selfExclusive = 0;
        int oppExclusive = 0;
        int selfLargest = 0;
        int oppLargest = 0;
        int selfDominatedShared = 0;
        int oppDominatedShared = 0;

        for (int r = BOARD_MIN; r <= BOARD_MAX; r++) {
            for (int c = BOARD_MIN; c <= BOARD_MAX; c++) {
                if (getCell(boardState, r, c) != EMPTY) continue;
                int idx = toIndex(r, c);
                if (visited[idx]) continue;

                int size = 0;
                int selfCloser = 0;
                int oppCloser = 0;
                boolean selfReach = false;
                boolean oppReach = false;

                Deque<int[]> queue = new ArrayDeque<>();
                queue.offer(new int[]{r, c});
                visited[idx] = true;

                while (!queue.isEmpty()) {
                    int[] cur = queue.poll();
                    int row = cur[0];
                    int col = cur[1];
                    int curIdx = toIndex(row, col);
                    size++;

                    int ds = distSelf[curIdx];
                    int doo = distOpp[curIdx];
                    boolean sReach = ds != INF;
                    boolean oReach = doo != INF;

                    if (sReach) selfReach = true;
                    if (oReach) oppReach = true;
                    if (sReach && oReach) {
                        if (ds < doo) selfCloser++;
                        else if (doo < ds) oppCloser++;
                    }

                    for (int[] dir : DIRS) {
                        int nr = row + dir[0];
                        int nc = col + dir[1];
                        if (!isInside(nr, nc) || getCell(boardState, nr, nc) != EMPTY) continue;
                        int nIdx = toIndex(nr, nc);
                        if (!visited[nIdx]) {
                            visited[nIdx] = true;
                            queue.offer(new int[]{nr, nc});
                        }
                    }
                }

                if (selfReach && !oppReach) {
                    selfExclusive += size;
                    selfLargest = Math.max(selfLargest, size);
                } else if (!selfReach && oppReach) {
                    oppExclusive += size;
                    oppLargest = Math.max(oppLargest, size);
                } else if (selfReach && oppReach) {
                    int adv = selfCloser - oppCloser;
                    if (adv > size / 4) selfDominatedShared += size;
                    else if (-adv > size / 4) oppDominatedShared += size;
                }
            }
        }

        return 3 * (selfExclusive - oppExclusive)
                + 2 * (selfLargest - oppLargest)
                + (selfDominatedShared - oppDominatedShared);
    }

    /**
     * Measures how close each side is to sealing queens/channels.
     * Positive means stronger pressure on opponent movement network.
     */
    private int computeTrapPressureScore(ArrayList<Integer> boardState, int selfCode, int oppCode) {
        int oppRestriction = computeSideRestriction(boardState, oppCode);
        int selfRestriction = computeSideRestriction(boardState, selfCode);
        return oppRestriction - selfRestriction;
    }

    private int computeSideRestriction(ArrayList<Integer> boardState, int sideCode) {
        int score = 0;

        for (int r = BOARD_MIN; r <= BOARD_MAX; r++) {
            for (int c = BOARD_MIN; c <= BOARD_MAX; c++) {
                if (getCell(boardState, r, c) != sideCode) continue;

                QueenMobilityInfo info = analyzeQueenMobility(boardState, r, c);
                if (info.totalMoves == 0) {
                    score += 2000;
                    continue;
                }

                score += clamp(18 - info.totalMoves, 0, 18) * 5;
                score += clamp(5 - info.adjacentEmpty, 0, 5) * 8;
                score += clamp(4 - info.escapeDirections, 0, 4) * 12;
                score += clamp(info.longestRay - info.secondLongestRay - 2, 0, 8) * 3;
            }
        }

        return score;
    }

    private QueenMobilityInfo analyzeQueenMobility(ArrayList<Integer> boardState, int row, int col) {
        int totalMoves = 0;
        int escapeDirections = 0;
        int longestRay = 0;
        int secondLongestRay = 0;

        for (int[] dir : DIRS) {
            int ray = 0;
            int r = row + dir[0];
            int c = col + dir[1];
            while (isInside(r, c) && getCell(boardState, r, c) == EMPTY) {
                ray++;
                r += dir[0];
                c += dir[1];
            }

            if (ray > 0) escapeDirections++;
            totalMoves += ray;

            if (ray > longestRay) {
                secondLongestRay = longestRay;
                longestRay = ray;
            } else if (ray > secondLongestRay) {
                secondLongestRay = ray;
            }
        }

        int adjacentEmpty = countAdjacentEmpty(boardState, row, col);
        return new QueenMobilityInfo(totalMoves, adjacentEmpty, escapeDirections, longestRay, secondLongestRay);
    }

    private int countAdjacentEmpty(ArrayList<Integer> boardState, int row, int col) {
        int count = 0;
        for (int[] dir : DIRS) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (isInside(nr, nc) && getCell(boardState, nr, nc) == EMPTY) {
                count++;
            }
        }
        return count;
    }

    /** Counts the number of arrow-blocked squares on the board. */
    private int countArrows(ArrayList<Integer> boardState) {
        int count = 0;
        for (int r = BOARD_MIN; r <= BOARD_MAX; r++) {
            for (int c = BOARD_MIN; c <= BOARD_MAX; c++) {
                if (getCell(boardState, r, c) == ARROW) count++;
            }
        }
        return count;
    }

    /**
     * Reads a cell by converting 2D board coordinates into flattened index.
     */
    public int getCell(ArrayList<Integer> boardState, int row, int col) {
        return boardState.get(toIndex(row, col));
    }

    /**
     * Writes a cell by converting 2D board coordinates into flattened index.
     */
    public void setCell(ArrayList<Integer> boardState, int row, int col, int value) {
        boardState.set(toIndex(row, col), value);
    }

    /**
     * Converts [row, col] to the framework's 1D layout index.
     */
    private int toIndex(int row, int col) {
        return BOARD_DIM * row + col;
    }

    private int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    /**
     * Returns true if the coordinate lies in the playable 10x10 region.
     */
    private boolean isInside(int row, int col) {
        return row >= BOARD_MIN && row <= BOARD_MAX && col >= BOARD_MIN && col <= BOARD_MAX;
    }

    private static class QueenMobilityInfo {
        final int totalMoves;
        final int adjacentEmpty;
        final int escapeDirections;
        final int longestRay;
        final int secondLongestRay;

        QueenMobilityInfo(int totalMoves, int adjacentEmpty, int escapeDirections,
                          int longestRay, int secondLongestRay) {
            this.totalMoves = totalMoves;
            this.adjacentEmpty = adjacentEmpty;
            this.escapeDirections = escapeDirections;
            this.longestRay = longestRay;
            this.secondLongestRay = secondLongestRay;
        }
    }
}
