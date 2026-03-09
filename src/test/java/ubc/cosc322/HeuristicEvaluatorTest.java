package ubc.cosc322;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

public class HeuristicEvaluatorTest {

    private static final int BOARD_DIM = 11;
    private static final int EMPTY = 0;
    private static final int BLACK_QUEEN = 1;
    private static final int WHITE_QUEEN = 2;
    private static final int ARROW = 3;

    private static int idx(int row, int col) {
        return BOARD_DIM * row + col;
    }

    private static ArrayList<Integer> pos(int row, int col) {
        return new ArrayList<>(Arrays.asList(row, col));
    }

    private static ArrayList<Integer> emptyBoard() {
        ArrayList<Integer> board = new ArrayList<>(BOARD_DIM * BOARD_DIM);
        for (int i = 0; i < BOARD_DIM * BOARD_DIM; i++) {
            board.add(EMPTY);
        }
        return board;
    }

    @Test
    public void generateValidMoves_fromCenterOnEmptyBoard_returnsAllRaySquares() {
        HeuristicEvaluator evaluator = new HeuristicEvaluator();
        ArrayList<Integer> board = emptyBoard();
        board.set(idx(5, 5), BLACK_QUEEN);

        ArrayList<ArrayList<Integer>> moves = evaluator.generateValidMoves(board, pos(5, 5));

        assertEquals(35, moves.size());
        assertTrue(moves.contains(pos(1, 5)));
        assertTrue(moves.contains(pos(10, 5)));
        assertTrue(moves.contains(pos(5, 1)));
        assertTrue(moves.contains(pos(10, 10)));
    }

    @Test
    public void applyMove_updatesSourceDestinationAndArrow() {
        HeuristicEvaluator evaluator = new HeuristicEvaluator();
        ArrayList<Integer> board = emptyBoard();
        board.set(idx(4, 4), WHITE_QUEEN);

        evaluator.applyMove(board, pos(4, 4), pos(4, 8), pos(8, 8));

        assertEquals(EMPTY, (int) board.get(idx(4, 4)));
        assertEquals(WHITE_QUEEN, (int) board.get(idx(4, 8)));
        assertEquals(ARROW, (int) board.get(idx(8, 8)));
    }

    @Test
    public void evaluate_whenOpponentIsFullyTrapped_isStronglyPositiveForCurrentPlayer() {
        HeuristicEvaluator evaluator = new HeuristicEvaluator();
        ArrayList<Integer> board = emptyBoard();
        board.set(idx(5, 5), BLACK_QUEEN);
        board.set(idx(1, 1), WHITE_QUEEN);

        board.set(idx(1, 2), ARROW);
        board.set(idx(2, 1), ARROW);
        board.set(idx(2, 2), ARROW);

        int blackScore = evaluator.evaluate(board, BLACK_QUEEN);
        int whiteScore = evaluator.evaluate(board, WHITE_QUEEN);

        assertTrue(blackScore > 0);
        assertTrue(whiteScore < 0);
        assertEquals(blackScore, -whiteScore);
    }
}
