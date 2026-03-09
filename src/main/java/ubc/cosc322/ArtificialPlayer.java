package ubc.cosc322;

import java.util.*;

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

	private long timer = -1;
	private static final int BOARD_DIM = 11;
	private static final int BLACK_QUEEN = 1;
	private static final int WHITE_QUEEN = 2;
	
    // Credentials used by GameClient.connect().
    private String userName = "cosc322";
    private String passwd = "cosc322";
	// Local board snapshot mirrored from server messages.
 	private ArrayList<Integer> gameBoard;
	// Dedicated component that contains heuristic and board utility logic.
	private final HeuristicEvaluator heuristicEvaluator;
	// Perspective used when evaluating the board (set on GAME_ACTION_START).
	private int myPlayerCode = BLACK_QUEEN;
	
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

		// Track last game-event timestamp (useful for time-control logic later).
		timer = System.currentTimeMillis();

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
			} else if (userName.equals(whitePlayer)) {
				myPlayerCode = WHITE_QUEEN;
			}
            System.out.println("\n\nGame started.");
            System.out.println("Room users at start: Black=" + blackPlayer + ", White=" + whitePlayer);
            System.out.println("Current login user: " + userName+"\n\n");
            ArrayList<Integer> gameState = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE);
            if (gameState != null && this.getGameGUI() != null) {
				// Save initial board state and draw it.
				this.gameBoard = new ArrayList<>(gameState);
                this.getGameGUI().setGameState(gameState);
            }
		}
		if (messageType.equals(GameMessage.GAME_STATE_PLAYER_LOST)) {
			
		}
		if (messageType.equals(GameMessage.GAME_ACTION_MOVE)){
			// Incremental move update: source, destination, and arrow position.
			ArrayList<Integer> from = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_CURR);
			ArrayList<Integer> to = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_NEXT);
			ArrayList<Integer> arrow = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.ARROW_POS);
			// Keep local board state in sync for any subsequent AI calculation.
			heuristicEvaluator.applyMove(this.gameBoard, from, to, arrow);
			// Let GUI apply the same update for visualization.
			this.getGameGUI().updateGameState(msgDetails);
		}
    	return true;
    }

	public ArrayList<ArrayList<Integer>> valid_moves(ArrayList<Integer> pos){
		// Return all queen-like ray moves from pos on current board snapshot.
		// This utility can be used for both queen movement and arrow shooting.
		return heuristicEvaluator.generateValidMoves(this.gameBoard, pos);
	}

	public int determine_board_value(){
		// Evaluate board quality from our side's perspective.
		// Higher value means a better strategic position for this player.
		return heuristicEvaluator.evaluate(this.gameBoard, myPlayerCode);
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
