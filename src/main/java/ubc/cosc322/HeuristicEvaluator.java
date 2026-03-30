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

    private static final int[][] DIRS = new int[][]{
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},           {0, 1},
            {1, -1},  {1, 0},  {1, 1}
    };

    private final HeuristicConfig config;

    public HeuristicEvaluator() {
        this(HeuristicConfig.defaultConfig());
    }

    public HeuristicEvaluator(HeuristicConfig config) {
        this.config = (config == null) ? HeuristicConfig.defaultConfig() : config;
    }

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
     */
    public int evaluate(ArrayList<Integer> boardState, int playerCode) {
        if (boardState == null || boardState.size() < BOARD_DIM * BOARD_DIM) {
            return 0;
        }

        int otherCode = (playerCode == BLACK_QUEEN) ? WHITE_QUEEN : BLACK_QUEEN;

        int mobilitySelf = countMobility(boardState, playerCode);
        int mobilityOpp = countMobility(boardState, otherCode);
        if (mobilitySelf == 0) return -config.winScore;
        if (mobilityOpp == 0) return config.winScore;

        int[] distSelf = computeQueenDistanceMap(boardState, playerCode);
        int[] distOpp = computeQueenDistanceMap(boardState, otherCode);

        int mobilityScore = mobilitySelf - mobilityOpp;
        int territoryScore = computeTerritoryScore(boardState, distSelf, distOpp);
        int regionScore = computeRegionScore(boardState, distSelf, distOpp);
        int continuityScore = computeContinuityScore(boardState, distSelf, distOpp);
        int trapPressureScore = computeTrapPressureScore(boardState, playerCode, otherCode);

        PhaseWeights weights = config.phaseWeightsForArrowCount(countArrows(boardState));
        return weights.mobility * mobilityScore
                + weights.territory * territoryScore
                + weights.region * regionScore
                + weights.continuity * continuityScore
                + weights.trap * trapPressureScore;
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
                    score += config.territoryExclusiveSquareBonus;
                } else if (!selfReach && oppReach) {
                    score -= config.territoryExclusiveSquareBonus;
                } else if (selfReach) {
                    int diff = clamp(doo - ds, -config.territoryDistanceDiffClamp, config.territoryDistanceDiffClamp);
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
                    score += config.regionExclusiveLinearWeight * regionSize
                            + (regionSize * regionSize) / config.regionExclusiveQuadraticDivisor;
                } else if (!selfReach && oppReach) {
                    score -= config.regionExclusiveLinearWeight * regionSize
                            + (regionSize * regionSize) / config.regionExclusiveQuadraticDivisor;
                } else if (selfReach) {
                    int entryAdv = clamp(minOppDist - minSelfDist, -config.regionEntryDiffClamp, config.regionEntryDiffClamp);
                    int entryWeight = Math.max(config.regionEntryMinWeight, regionSize / config.regionEntryScaleDivisor);
                    int control = selfCloserCells - oppCloserCells;
                    score += entryAdv * entryWeight;
                    score += control / config.regionControlDivisor;
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
                } else if (selfReach) {
                    int adv = selfCloser - oppCloser;
                    int dominanceThreshold = size / config.continuitySharedDominanceDivisor;
                    if (adv > dominanceThreshold) selfDominatedShared += size;
                    else if (-adv > dominanceThreshold) oppDominatedShared += size;
                }
            }
        }

        return config.continuityExclusiveAreaWeight * (selfExclusive - oppExclusive)
                + config.continuityLargestRegionWeight * (selfLargest - oppLargest)
                + config.continuitySharedControlWeight * (selfDominatedShared - oppDominatedShared);
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
                    score += config.trapZeroMobilityPenalty;
                    continue;
                }

                score += clamp(config.trapMobilityTarget - info.totalMoves, 0, config.trapMobilityClamp)
                        * config.trapMobilityWeight;
                score += clamp(config.trapAdjacentTarget - info.adjacentEmpty, 0, config.trapAdjacentClamp)
                        * config.trapAdjacentWeight;
                score += clamp(config.trapEscapeTarget - info.escapeDirections, 0, config.trapEscapeClamp)
                        * config.trapEscapeWeight;
                score += clamp(info.longestRay - info.secondLongestRay - config.trapRayImbalanceOffset,
                        0, config.trapRayImbalanceClamp) * config.trapRayImbalanceWeight;
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

    public static class HeuristicConfig {
        final int winScore;
        final int openingArrowCutoff;
        final int midgameArrowCutoff;
        final PhaseWeights openingWeights;
        final PhaseWeights midgameWeights;
        final PhaseWeights endgameWeights;

        final int territoryExclusiveSquareBonus;
        final int territoryDistanceDiffClamp;

        final int regionExclusiveLinearWeight;
        final int regionExclusiveQuadraticDivisor;
        final int regionEntryDiffClamp;
        final int regionEntryMinWeight;
        final int regionEntryScaleDivisor;
        final int regionControlDivisor;

        final int continuityExclusiveAreaWeight;
        final int continuityLargestRegionWeight;
        final int continuitySharedControlWeight;
        final int continuitySharedDominanceDivisor;

        final int trapZeroMobilityPenalty;
        final int trapMobilityTarget;
        final int trapMobilityClamp;
        final int trapMobilityWeight;
        final int trapAdjacentTarget;
        final int trapAdjacentClamp;
        final int trapAdjacentWeight;
        final int trapEscapeTarget;
        final int trapEscapeClamp;
        final int trapEscapeWeight;
        final int trapRayImbalanceOffset;
        final int trapRayImbalanceClamp;
        final int trapRayImbalanceWeight;

        private HeuristicConfig(int winScore,
                                int openingArrowCutoff,
                                int midgameArrowCutoff,
                                PhaseWeights openingWeights,
                                PhaseWeights midgameWeights,
                                PhaseWeights endgameWeights,
                                int territoryExclusiveSquareBonus,
                                int territoryDistanceDiffClamp,
                                int regionExclusiveLinearWeight,
                                int regionExclusiveQuadraticDivisor,
                                int regionEntryDiffClamp,
                                int regionEntryMinWeight,
                                int regionEntryScaleDivisor,
                                int regionControlDivisor,
                                int continuityExclusiveAreaWeight,
                                int continuityLargestRegionWeight,
                                int continuitySharedControlWeight,
                                int continuitySharedDominanceDivisor,
                                int trapZeroMobilityPenalty,
                                int trapMobilityTarget,
                                int trapMobilityClamp,
                                int trapMobilityWeight,
                                int trapAdjacentTarget,
                                int trapAdjacentClamp,
                                int trapAdjacentWeight,
                                int trapEscapeTarget,
                                int trapEscapeClamp,
                                int trapEscapeWeight,
                                int trapRayImbalanceOffset,
                                int trapRayImbalanceClamp,
                                int trapRayImbalanceWeight) {
            this.winScore = winScore;
            this.openingArrowCutoff = openingArrowCutoff;
            this.midgameArrowCutoff = midgameArrowCutoff;
            this.openingWeights = openingWeights;
            this.midgameWeights = midgameWeights;
            this.endgameWeights = endgameWeights;
            this.territoryExclusiveSquareBonus = territoryExclusiveSquareBonus;
            this.territoryDistanceDiffClamp = territoryDistanceDiffClamp;
            this.regionExclusiveLinearWeight = regionExclusiveLinearWeight;
            this.regionExclusiveQuadraticDivisor = regionExclusiveQuadraticDivisor;
            this.regionEntryDiffClamp = regionEntryDiffClamp;
            this.regionEntryMinWeight = regionEntryMinWeight;
            this.regionEntryScaleDivisor = regionEntryScaleDivisor;
            this.regionControlDivisor = regionControlDivisor;
            this.continuityExclusiveAreaWeight = continuityExclusiveAreaWeight;
            this.continuityLargestRegionWeight = continuityLargestRegionWeight;
            this.continuitySharedControlWeight = continuitySharedControlWeight;
            this.continuitySharedDominanceDivisor = continuitySharedDominanceDivisor;
            this.trapZeroMobilityPenalty = trapZeroMobilityPenalty;
            this.trapMobilityTarget = trapMobilityTarget;
            this.trapMobilityClamp = trapMobilityClamp;
            this.trapMobilityWeight = trapMobilityWeight;
            this.trapAdjacentTarget = trapAdjacentTarget;
            this.trapAdjacentClamp = trapAdjacentClamp;
            this.trapAdjacentWeight = trapAdjacentWeight;
            this.trapEscapeTarget = trapEscapeTarget;
            this.trapEscapeClamp = trapEscapeClamp;
            this.trapEscapeWeight = trapEscapeWeight;
            this.trapRayImbalanceOffset = trapRayImbalanceOffset;
            this.trapRayImbalanceClamp = trapRayImbalanceClamp;
            this.trapRayImbalanceWeight = trapRayImbalanceWeight;
        }

        public PhaseWeights phaseWeightsForArrowCount(int arrowCount) {
            if (arrowCount < openingArrowCutoff) return openingWeights;
            if (arrowCount < midgameArrowCutoff) return midgameWeights;
            return endgameWeights;
        }

        public static HeuristicConfig defaultConfig() {
            return new HeuristicConfig(
                    900_000,
                    18,
                    45,
                    new PhaseWeights(4, 3, 2, 2, 2),
                    new PhaseWeights(3, 5, 5, 4, 5),
                    new PhaseWeights(2, 6, 7, 7, 9),
                    7,
                    3,
                    6,
                    12,
                    3,
                    2,
                    4,
                    2,
                    3,
                    2,
                    1,
                    4,
                    2000,
                    18,
                    18,
                    5,
                    5,
                    5,
                    8,
                    4,
                    4,
                    12,
                    2,
                    8,
                    3
            );
        }

        public static HeuristicConfig aggressiveTrapConfig() {
            return new HeuristicConfig(
                    900_000,
                    16,
                    40,
                    new PhaseWeights(4, 3, 2, 2, 3),
                    new PhaseWeights(3, 4, 5, 4, 7),
                    new PhaseWeights(2, 5, 7, 7, 12),
                    7,
                    3,
                    6,
                    12,
                    3,
                    2,
                    4,
                    2,
                    3,
                    2,
                    1,
                    4,
                    2600,
                    20,
                    20,
                    6,
                    6,
                    6,
                    10,
                    4,
                    4,
                    16,
                    1,
                    9,
                    5
            );
        }
    }

    public static class PhaseWeights {
        final int mobility;
        final int territory;
        final int region;
        final int continuity;
        final int trap;

        public PhaseWeights(int mobility, int territory, int region, int continuity, int trap) {
            this.mobility = mobility;
            this.territory = territory;
            this.region = region;
            this.continuity = continuity;
            this.trap = trap;
        }
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
