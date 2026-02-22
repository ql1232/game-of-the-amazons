
package ubc.cosc322;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sfs2x.client.entities.Room;
import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GameMessage;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.amazons.AmazonsBoard;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/**
 * An example illustrating how to implement a GamePlayer
 * @author Yong Gao (yong.gao@ubc.ca)
 * Jan 5, 2021
 *
 */
public class COSC322Test extends GamePlayer{
    private static final String DEFAULT_ROOM = "Okanagan Lake";

    private GameClient gameClient = null; 
    private BaseGameGUI gamegui = null;
	
    private String userName = "cosc322";
    private String passwd = "cosc322";
    private String autoJoinRoom = null;
	
    /**
     * The main method
     * @param args for name and passwd (current, any string would work)
     */
    public static void main(String[] args) {				 
        String userName = (args.length > 0 && !args[0].trim().isEmpty())
            ? args[0].trim()
            : "cosc322_" + System.currentTimeMillis() % 100000;
        String passwd = (args.length > 1 && !args[1].trim().isEmpty())
            ? args[1].trim()
            : "cosc322";
        String roomToJoin = (args.length > 2 && !args[2].trim().isEmpty())
            ? args[2].trim()
            : DEFAULT_ROOM;

        System.out.println("Starting player: user=" + userName + ", room=" + roomToJoin);

    	COSC322Test player = new COSC322Test(userName, passwd, roomToJoin);

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
    public COSC322Test(String userName, String passwd) {
        this(userName, passwd, null);
    }

    public COSC322Test(String userName, String passwd, String autoJoinRoom) {
        this.userName = userName;
        this.passwd = passwd;
        this.autoJoinRoom = autoJoinRoom;

    	//To make a GUI-based player, create an instance of BaseGameGUI
    	//and implement the method getGameGUI() accordingly
        this.gamegui = new BaseGameGUI(this);
    }
 


    @Override
		public void onLogin() {
		userName = gameClient.getUserName();
		if(gamegui != null) {
			gamegui.setRoomInformation(gameClient.getRoomList());
		}

        if (autoJoinRoom != null && !autoJoinRoom.isEmpty()) {
            gameClient.joinRoom(autoJoinRoom);
        }
	}

    @Override
    public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {
    	//This method will be called by the GameClient when it receives a game-related message
    	//from the server.
	
    	//For a detailed description of the message types and format, 
    	//see the method GamePlayer.handleGameMessage() in the game-client-api document.
		if (GameMessage.GAME_STATE_BOARD.equals(messageType)){
            ArrayList<Integer> gameState = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE);
            if (this.getGameGUI() != null) {
			    this.getGameGUI().setGameState(gameState);
            }
		} else if (GameMessage.GAME_ACTION_START.equals(messageType)) {
            String blackPlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_BLACK);
            String whitePlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_WHITE);
            System.out.println("Game started. Black=" + blackPlayer + ", White=" + whitePlayer + ", Me=" + userName);
            ArrayList<Integer> gameState = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE);
            if (gameState != null && this.getGameGUI() != null) {
                this.getGameGUI().setGameState(gameState);
            }
        } else if (GameMessage.GAME_ACTION_MOVE.equals(messageType)){
            if (this.getGameGUI() != null) {
			    this.getGameGUI().updateGameState(msgDetails);
            }
		}
    	return true;
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
