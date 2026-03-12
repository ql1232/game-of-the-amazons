package ubc.cosc322;

import java.util.*;

import com.smartfoxserver.v2.entities.data.SFSObject;
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
	private boolean gameEnded = false;

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
		System.out.println("Current turn: " + this.turn_tracker);
		System.out.println("GAME MESSAGE RECEIVED");

    	//This method will be called by the GameClient when it receives a game-related message
    	//from the server.
	
    	//For a detailed description of the message types and format, 
    	//see the method GamePlayer.handleGameMessage() in the game-client-api document.
		if (messageType.equals(GameMessage.GAME_STATE_BOARD)){
			System.out.println("\nGAME STATE UPDATE RECEIVED");
			ArrayList<Integer> gameS = (ArrayList)msgDetails.get("game-state");
			System.out.println("Game Board: " + gameS);
			this.gameBoard = gameS;
			this.gamegui.setGameState(gameS);
			this.turn_tracker=0;
		}
		if (messageType.equals(GameMessage.GAME_ACTION_START)) {
			System.err.println("\nGAME START MESSAGE RECEIVED");
            String blackPlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_BLACK);
            String whitePlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_WHITE);
			// Detect our side once the game starts; used by heuristic perspective.
			if (userName.equals(blackPlayer)) {
				myPlayerCode = BLACK_QUEEN;
			} else if (userName.equals(whitePlayer)) {
				myPlayerCode = WHITE_QUEEN;
			}

			this.turn_tracker=0;
			this.gameEnded = false;
			this.moveTree = new ArtificialMoveTree(this);


            System.out.println("\n\nGame started.");
            System.out.println("Room users at start: Black=" + blackPlayer + ", White=" + whitePlayer);
            System.out.println("Current login user: " + userName+"\n\n");
			if(this.sendNextMoveIfTurn()){
				System.out.println("Made first move.");
			}
		}
		if (messageType.equals(GameMessage.GAME_STATE_PLAYER_LOST)) {
			System.err.println("\nGAME END MESSAGE RECEIVED");
			gameEnded = true;
			// Get information about the losing player (if included in the message)
			Object loserObj = msgDetails.get("player-lost");
			String loserName = null;
			if (loserObj instanceof String) {
				loserName = (String) loserObj;
			}
			
			System.out.println("\n=========================================");
			System.out.println("Game ended!");
			
			if (loserName != null) {
				System.out.println("Player lost: " + loserName);
				if (loserName.equals(userName)) {
					System.out.println("You lost!");
				} else {
					System.out.println("You won!");
				}
			} else {
				System.out.println("Game ended.");
			}
			System.out.println("=========================================\n");
		}
		if (messageType.equals(GameMessage.GAME_ACTION_MOVE)){
			System.out.println("MOVE MESSAGE RECEIVED");
			// Incremental move update: source, destination, and arrow position.
			ArrayList<Integer> from = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_CURR);
			ArrayList<Integer> to = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_NEXT);
			ArrayList<Integer> arrow = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.ARROW_POS);
			// Keep local board state in sync for any subsequent AI calculation.
			this.heuristicEvaluator.applyMove(this.gameBoard, from, to, arrow);
			this.getGameGUI().updateGameState(msgDetails);
			// Let GUI apply the same update for visualization.
			this.turn_tracker++;
			this.moveTree.progressMove();
			this.sendNextMoveIfTurn();

		}
    	return true;
    }

	//getter method to obtain the best next move from the ArtificialMoveTree
	//detailed return syntax is written in AMT's actual method
	//realistically should be called at the start of this player's turn
	public ArrayList<ArrayList<Integer>> getNextMove(){
		return this.moveTree.getNextMove();
	}

	public boolean sendNextMoveIfTurn() {
		if (gameEnded) {
			return false;
		}
		
		if(this.turn_tracker%2 + 1==this.myPlayerCode){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			
			// Check if we have valid moves before trying to get one; if not, we lose by default.
			if (!this.moveTree.hasValidMoves()) {
				gameEnded = true;
				System.out.println("\n=========================================");
				System.out.println("Game ended!");
				System.out.println("Player " + userName + " has no valid moves.");
				System.out.println("You lost!");
				System.out.println("=========================================\n");
				return false;
			}
			
			System.out.println("Sending move...");
			ArrayList<ArrayList<Integer>> moves = this.getNextMove();
			
			if (moves == null) {
				gameEnded = true;
				System.out.println("\n=========================================");
				System.out.println("Game ended!");
				System.out.println("Unable to generate valid moves.");
				System.out.println("You lost!");
				System.out.println("=========================================\n");
				return false;
			}
			
			this.heuristicEvaluator.applyMove(this.gameBoard, moves.get(0),moves.get(1),moves.get(2));
			this.gameClient.sendMoveMessage(moves.get(0),moves.get(1),moves.get(2));
			this.updateMove(moves.get(0),moves.get(1),moves.get(2));
			this.turn_tracker++;
			this.moveTree.progressMove();
			return true;
		}

		return false;
	}

	public void updateMove(ArrayList<Integer> queenPosCurrent, ArrayList<Integer> queenPosNew, ArrayList<Integer> arrowPos) {
		Map<String, Object> data = new HashMap<>();
		data.put(AmazonsGameMessage.QUEEN_POS_CURR, queenPosCurrent);
		data.put(AmazonsGameMessage.QUEEN_POS_NEXT, queenPosNew);
		data.put(AmazonsGameMessage.ARROW_POS, arrowPos);
		this.gamegui.updateGameState(data);


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
