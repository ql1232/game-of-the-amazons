package ubc.cosc322;

import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.List;

public class ArtificialMoveTree {
    ArtificialPlayer player;
    MoveNode current;

    // Search depth (plies). Increase for stronger play at the cost of time.
    private static final int SEARCH_DEPTH = 2;

    // Per-search statistics (reset each call to getNextMoveNode).
    private int nodesEvaluated;
    private int betaCutoffs;
    private int alphaCutoffs;

    public ArtificialMoveTree(ArtificialPlayer player) {
        System.out.println("Move tree initialized.");
        this.player = player;
        current = new MoveNode(null, player.gameBoard);
    }

    /**
     * Returns the MoveNode representing the best move found via alpha-beta
     * minimax search to SEARCH_DEPTH plies.
     */
    MoveNode getNextMoveNode() {
        int myColor = player.myPlayerCode;
        List<ArrayList<ArrayList<Integer>>> moves = getAllMoves(current.gameState, myColor);
        if (moves.isEmpty()) return null;

        // Reset per-search counters.
        nodesEvaluated = 0;
        betaCutoffs    = 0;
        alphaCutoffs   = 0;
        long startTime = System.currentTimeMillis();

        int bestValue = Integer.MIN_VALUE;
        MoveNode bestChild = null;
        int alpha = Integer.MIN_VALUE;
        int beta  = Integer.MAX_VALUE;

        for (ArrayList<ArrayList<Integer>> move : moves) {
            ArrayList<Integer> newBoard = applyMoveToBoard(current.gameState, move);
            int value = alphaBeta(newBoard, SEARCH_DEPTH - 1, alpha, beta, false, myColor);
            if (value > bestValue) {
                bestValue = value;
                bestChild = new MoveNode(move, newBoard);
            }
            alpha = Math.max(alpha, value);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        String colorName = (myColor == 1) ? "BLACK" : "WHITE";
        System.out.println("\n========== Alpha-Beta Search Report ==========");
        System.out.printf("  Player     : %s (code=%d)%n", colorName, myColor);
        System.out.printf("  Depth      : %d plies%n", SEARCH_DEPTH);
        System.out.printf("  Root moves : %d candidates%n", moves.size());
        System.out.printf("  Nodes eval : %d%n", nodesEvaluated);
        System.out.printf("  Beta cuts  : %d%n", betaCutoffs);
        System.out.printf("  Alpha cuts : %d%n", alphaCutoffs);
        System.out.printf("  Best score : %d%n", bestValue);
        if (bestChild != null && bestChild.move != null) {
            System.out.printf("  Best move  : from%s -> to%s, arrow%s%n",
                    bestChild.move.get(0), bestChild.move.get(1), bestChild.move.get(2));
        }
        System.out.printf("  Time taken : %d ms%n", elapsed);
        System.out.println("===============================================\n");

        return bestChild;
    }

    /**
     * Recursive alpha-beta minimax.
     *
     * @param boardState   current board position to evaluate or expand
     * @param depth        remaining depth (0 = leaf, evaluate immediately)
     * @param alpha        best score the maximizer can guarantee so far
     * @param beta         best score the minimizer can guarantee so far
     * @param isMaximizing true when it is myColor's turn at this ply
     * @param myColor      the AI's own piece code (1 = black, 2 = white)
     * @return heuristic score from myColor's perspective
     */
    private int alphaBeta(ArrayList<Integer> boardState, int depth,
                          int alpha, int beta, boolean isMaximizing, int myColor) {
        if (depth == 0) {
            nodesEvaluated++;
            return player.heuristicEvaluator.evaluate(boardState, myColor);
        }

        int color = isMaximizing ? myColor : (3 - myColor);
        List<ArrayList<ArrayList<Integer>>> moves = getAllMoves(boardState, color);

        if (moves.isEmpty()) {
            // Current mover has no legal moves — terminal loss for that side.
            return isMaximizing ? Integer.MIN_VALUE / 2 : Integer.MAX_VALUE / 2;
        }

        if (isMaximizing) {
            int value = Integer.MIN_VALUE;
            for (ArrayList<ArrayList<Integer>> move : moves) {
                ArrayList<Integer> newBoard = applyMoveToBoard(boardState, move);
                value = Math.max(value,
                        alphaBeta(newBoard, depth - 1, alpha, beta, false, myColor));
                alpha = Math.max(alpha, value);
                if (beta <= alpha) {
                    betaCutoffs++;
                    break; // beta cutoff
                }
            }
            return value;
        } else {
            int value = Integer.MAX_VALUE;
            for (ArrayList<ArrayList<Integer>> move : moves) {
                ArrayList<Integer> newBoard = applyMoveToBoard(boardState, move);
                value = Math.min(value,
                        alphaBeta(newBoard, depth - 1, alpha, beta, true, myColor));
                beta = Math.min(beta, value);
                if (beta <= alpha) {
                    alphaCutoffs++;
                    break; // alpha cutoff
                }
            }
            return value;
        }
    }

    /**
     * Generates all legal moves for {@code color} on {@code boardState}.
     * Each move is encoded as [from, to, arrow], each element being [row, col].
     */
    List<ArrayList<ArrayList<Integer>>> getAllMoves(ArrayList<Integer> boardState, int color) {
        List<ArrayList<ArrayList<Integer>>> allMoves = new ArrayList<>();
        HeuristicEvaluator eval = player.heuristicEvaluator;

        for (int i = 1; i <= 10; i++) {
            for (int j = 1; j <= 10; j++) {
                if (eval.getCell(boardState, i, j) != color) continue;
                ArrayList<Integer> from = new ArrayList<>(asList(i, j));
                for (ArrayList<Integer> to : eval.generateValidMoves(boardState, from)) {
                    // Clear the queen's source square so the arrow may pass back through it.
                    ArrayList<Integer> tempBoard = new ArrayList<>(boardState);
                    eval.setCell(tempBoard, i, j, 0);
                    for (ArrayList<Integer> arrow : eval.generateValidMoves(tempBoard, to)) {
                        ArrayList<ArrayList<Integer>> move = new ArrayList<>(3);
                        move.add(new ArrayList<>(from));
                        move.add(new ArrayList<>(to));
                        move.add(new ArrayList<>(arrow));
                        allMoves.add(move);
                    }
                }
            }
        }
        return allMoves;
    }

    /** Returns a new board state with the given move applied, leaving boardState unchanged. */
    private ArrayList<Integer> applyMoveToBoard(ArrayList<Integer> boardState,
                                                ArrayList<ArrayList<Integer>> move) {
        ArrayList<Integer> newBoard = new ArrayList<>(boardState);
        player.heuristicEvaluator.applyMove(newBoard, move.get(0), move.get(1), move.get(2));
        return newBoard;
    }

    ArrayList<ArrayList<Integer>> getNextMove() {
        MoveNode nextNode = getNextMoveNode();
        return (nextNode != null) ? nextNode.move : null;
    }

    boolean hasValidMoves() {
        return !getAllMoves(current.gameState, player.myPlayerCode).isEmpty();
    }

    boolean is_max() {
        return true;
    }

    /**
     * Syncs the search root to the latest actual game board after any player's move.
     */
    void progressMove() {
        current = new MoveNode(null, player.gameBoard);
        System.out.println("Progressed move: search root updated.");
    }
}

class MoveNode {
    ArrayList<Integer> gameState;
    ArrayList<ArrayList<Integer>> move;

    public MoveNode(ArrayList<ArrayList<Integer>> move, ArrayList<Integer> boardState) {
        this.move = move;
        this.gameState = new ArrayList<>(boardState);
    }
}