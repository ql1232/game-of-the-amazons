package ubc.cosc322;

import java.util.*;
import java.util.Arrays;

import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GameMessage;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/**
 * Game client entry point for COSC322 Amazons.
 *
 * This class is responsible for:
 * 1) Connecting to the SmartFox-based game server.
 * 2) Receiving and forwarding board updates to the GUI.
 * 3) Keeping a local board snapshot for AI utilities.
 * 4) Delegating move generation and board scoring to HeuristicEvaluator.
 *
 * Board encoding used by the server/client API:
 * - 0: empty square
 * - 1: black queen
 * - 2: white queen
 * - 3: blocked square (arrow)
 *
 * The board is represented as a flattened 11x11 array-list where valid game
 * coordinates are in [1..10] for both row and column. Index 0 rows/columns are
 * padding used by the original framework.
 */
public class ArtificialPlayer extends GamePlayer{

    private GameClient gameClient = null; 
    private BaseGameGUI gamegui = null;
	public int turn_tracker = 0;

	private static final int BOARD_DIM = 11;
	private static final int BLACK_QUEEN = 1;
	private static final int WHITE_QUEEN = 2;
	
    // Credentials used by GameClient.connect().
    private String userName = "cosc322";
    private String passwd = "cosc322";
	// Local board snapshot mirrored from server messages.
	ArrayList<Integer> gameBoard;
	// Dedicated component that contains heuristic and board utility logic.
	final HeuristicEvaluator heuristicEvaluator;
	// Perspective used when evaluating the board (set on GAME_ACTION_START).
	int myPlayerCode = BLACK_QUEEN;

	public ArtificialMoveTree moveTree;
	
    /**
     * Program entry.
     *
     * args[0]: username (optional)
     * args[1]: password (optional)
     *
     * If no username is provided, a timestamp-based name is generated.
     *
     * @param args runtime arguments
     */
    public static void main(String[] args) {				 
    	String userName = (args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) 
    			? args[0] 
    			: "cosc322_" + (System.currentTimeMillis() % 100000);
    	String passwd = (args.length > 1 && args[1] != null && !args[1].trim().isEmpty()) ? args[1] : "cosc322";
    	ArtificialPlayer player = new ArtificialPlayer(userName, passwd);

    	if(player.getGameGUI() == null) {
    		player.Go();
    	}
    	else {
    		BaseGameGUI.sys_setup();
            java.awt.EventQueue.invokeLater(new Runnable() {
                public void run() {
                	player.Go();
                }
            });
    	}
    }
	
    /**
     * Constructs a player instance with GUI and evaluator.
     *
     * @param userName login name used by the game service
     * @param passwd login password used by the game service
     */
    public ArtificialPlayer(String userName, String passwd) {
    	this.userName = userName;
    	this.passwd = passwd;
		this.heuristicEvaluator = new HeuristicEvaluator();

    	// Initialize a zero-filled board snapshot with framework-compatible size.
		this.gameBoard=new ArrayList<>();
		for(int i = 0; i < BOARD_DIM * BOARD_DIM; i++){
			this.gameBoard.add(0);
		}
		// GUI is optional in framework design; this project enables it.
    	this.gamegui = new BaseGameGUI(this);
    }

	@Override
		public void onLogin() {
		userName = gameClient.getUserName();
		System.out.println("Login success. User=" + userName);
		if(gamegui != null) {
			gamegui.setRoomInformation(gameClient.getRoomList());
		}
	}

    @Override
    public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {

    	//This method will be called by the GameClient when it receives a game-related message
    	//from the server.
	
    	//For a detailed description of the message types and format, 
    	//see the method GamePlayer.handleGameMessage() in the game-client-api document.
		if (messageType.equals(GameMessage.GAME_STATE_BOARD)){
			// Full board snapshot from server; replace local copy.
			ArrayList<Integer> boardState = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE);
			if (boardState != null) {
				this.gameBoard = new ArrayList<>(boardState);
				this.getGameGUI().setGameState(boardState);
			}
		}
		if (messageType.equals(GameMessage.GAME_ACTION_START)) {
            String blackPlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_BLACK);
            String whitePlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_WHITE);
			// Detect our side once the game starts; used by heuristic perspective.
			if (userName.equals(blackPlayer)) {
				myPlayerCode = BLACK_QUEEN;
				this.turn_tracker =1;
			} else if (userName.equals(whitePlayer)) {
				this.turn_tracker =0;
				myPlayerCode = WHITE_QUEEN;
			}
            System.out.println("\n\nGame started.");
            System.out.println("Room users at start: Black=" + blackPlayer + ", White=" + whitePlayer);
            System.out.println("Current login user: " + userName+"\n\n");
            // GAME_ACTION_START may or may not carry a game-state payload.
            // If it does, sync our board; otherwise use the board already set by GAME_STATE_BOARD.
            ArrayList<Integer> gameState = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE);
            if (gameState != null) {
				this.gameBoard = new ArrayList<>(gameState);
				if (this.getGameGUI() != null) {
					this.getGameGUI().setGameState(gameState);
				}
            }
			// Build the move tree in the background; it is NOT needed to send moves.
			new Thread(() -> this.moveTree = new ArtificialMoveTree(this), "MoveTree-Init").start();
			// Black moves first — start computing immediately without waiting for the tree.
			if (myPlayerCode == BLACK_QUEEN) {
				new Thread(this::sendMyMove, "AI-Move-0").start();
			}
		}
		if (messageType.equals(GameMessage.GAME_STATE_PLAYER_LOST)) {
			
		}
		if (messageType.equals(GameMessage.GAME_ACTION_MOVE)){
			// Incremental move update: source, destination, and arrow position.
			ArrayList<Integer> from = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_CURR);
			ArrayList<Integer> to = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_NEXT);
			ArrayList<Integer> arrow = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.ARROW_POS);
			// Identify whose move this is before mutating the board.
			boolean isOpponentMove = (this.heuristicEvaluator.getCell(this.gameBoard, from.get(0), from.get(1)) != myPlayerCode);
			// Keep local board state in sync for any subsequent AI calculation.
			this.heuristicEvaluator.applyMove(this.gameBoard, from, to, arrow);
			if (this.moveTree != null) {
				try { this.moveTree.progressMove(); } catch (Exception ignored) {}
			}
			// Let GUI apply the same update for visualization.
			this.getGameGUI().updateGameState(msgDetails);
			this.turn_tracker++;
			// If the opponent just moved, send our response on a background thread.
			if (isOpponentMove) {
				new Thread(this::sendMyMove, "AI-Move").start();
			}
		}
    	return true;
    }

	//getter method to obtain the best next move from the ArtificialMoveTree
	//detailed return syntax is written in AMT's actual method
	//realistically should be called at the start of this player's turn
	public ArrayList<ArrayList<Integer>> getNextMove(){
		return this.moveTree.getNextMove();
	}

	/**
	 * Computes the best greedy move (depth-1) and sends it to the game server.
	 * Does not depend on ArtificialMoveTree, so it is safe to call immediately after
	 * game start without waiting for the tree to be built.
	 */
	private void sendMyMove() {
		try {
			if (this.gameClient == null) return;
			ArrayList<ArrayList<Integer>> move = computeBestMove();
			if (move == null || move.size() < 3) {
				System.err.println("[AI] No valid move found.");
				return;
			}
			this.gameClient.sendMoveMessage(move.get(0), move.get(1), move.get(2));
		} catch (Exception e) {
			System.err.println("[AI] Failed to send move: " + e.getMessage());
		}
	}

	/**
	 * Depth-1 greedy search: enumerate all legal moves for our color and return
	 * the one that maximises HeuristicEvaluator.evaluate() after the move.
	 *
	 * Board copy is made at entry so the real board is never modified.
	 */
	private ArrayList<ArrayList<Integer>> computeBestMove() {
		ArrayList<ArrayList<Integer>> bestMove = null;
		int bestScore = Integer.MIN_VALUE;
		ArrayList<Integer> board = new ArrayList<>(this.gameBoard);

		for (int i = 1; i <= 10; i++) {
			for (int j = 1; j <= 10; j++) {
				if (heuristicEvaluator.getCell(board, i, j) != myPlayerCode) continue;
				ArrayList<Integer> from = new ArrayList<>(Arrays.asList(i, j));
				for (ArrayList<Integer> to : heuristicEvaluator.generateValidMoves(board, from)) {
					// Temporarily clear queen's source so arrows can pass through it.
					ArrayList<Integer> boardQ = new ArrayList<>(board);
					heuristicEvaluator.setCell(boardQ, i, j, 0);
					for (ArrayList<Integer> arrow : heuristicEvaluator.generateValidMoves(boardQ, to)) {
						ArrayList<Integer> finalBoard = new ArrayList<>(boardQ);
						heuristicEvaluator.setCell(finalBoard, to.get(0), to.get(1), myPlayerCode);
						heuristicEvaluator.setCell(finalBoard, arrow.get(0), arrow.get(1), 3);
						int score = heuristicEvaluator.evaluate(finalBoard, myPlayerCode);
						if (score > bestScore) {
							bestScore = score;
							bestMove = new ArrayList<>(Arrays.asList(
								new ArrayList<>(from),
								new ArrayList<>(to),
								new ArrayList<>(arrow)
							));
						}
					}
				}
			}
		}
		return bestMove;
	}
    
    @Override
    public String userName() {
    	return userName;
    }

	@Override
	public GameClient getGameClient() {
		// TODO Auto-generated method stub
		return this.gameClient;
	}

	@Override
	public BaseGameGUI getGameGUI() {
		// TODO Auto-generated method stub
		return  this.gamegui;
	}

	@Override
	public void connect() {
		// TODO Auto-generated method stub
    	gameClient = new GameClient(userName, passwd, this);			
	}

 
}//end of class
