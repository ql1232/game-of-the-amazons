
package ubc.cosc322;

import java.util.*;

import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GameMessage;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/**
 * An example illustrating how to implement a GamePlayer
 * @author Yong Gao (yong.gao@ubc.ca)
 * Jan 5, 2021
 *
 */
public class ArtificialPlayer extends GamePlayer{

    private GameClient gameClient = null; 
    private BaseGameGUI gamegui = null;

	private long timer = -1;
	
    private String userName = "cosc322";
    private String passwd = "cosc322";
 	private ArrayList<Integer> gameBoard;
	
    /**
     * The main method
     * @param args for name and passwd (current, any string would work)
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
     * Any name and passwd 
     * @param userName
      * @param passwd
     */
    public ArtificialPlayer(String userName, String passwd) {
    	this.userName = userName;
    	this.passwd = passwd;

    	//To make a GUI-based player, create an instance of BaseGameGUI
    	//and implement the method getGameGUI() accordingly
		this.gameBoard=new ArrayList<>();
		for(int i = 0; i<1000; i++){
			this.gameBoard.add(0);
		}
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

		timer = System.currentTimeMillis();

    	//This method will be called by the GameClient when it receives a game-related message
    	//from the server.
	
    	//For a detailed description of the message types and format, 
    	//see the method GamePlayer.handleGameMessage() in the game-client-api document.
		if (messageType.equals(GameMessage.GAME_STATE_BOARD)){
			this.getGameGUI().setGameState((ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE));
		}
		if (messageType.equals(GameMessage.GAME_ACTION_START)) {
            String blackPlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_BLACK);
            String whitePlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_WHITE);
            System.out.println("\n\nGame started.");
            System.out.println("Room users at start: Black=" + blackPlayer + ", White=" + whitePlayer);
            System.out.println("Current login user: " + userName+"\n\n");
            ArrayList<Integer> gameState = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE);
            if (gameState != null && this.getGameGUI() != null) {
                this.getGameGUI().setGameState(gameState);
            }
		}
		if (messageType.equals(GameMessage.GAME_STATE_PLAYER_LOST)) {
			
		}
		if (messageType.equals(GameMessage.GAME_ACTION_MOVE)){
			this.getGameGUI().updateGameState(msgDetails);
		}
    	return true;
    }

	public ArrayList<ArrayList<Integer>> valid_moves(ArrayList<Integer> pos){
		//get the valid moves for a given position's queen or arrow
		//this assumes that there is in fact a queen in the given location
		//returns a list of all valid coordinates
		//since queens and arrows follow the same ruleset, just call this method twice to handle both


		return null;
	}

	public int determine_board_value(){
		//a method to determine the value of a theoretical board state. might be switched to move evaluation in the future.
		return 0;
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
