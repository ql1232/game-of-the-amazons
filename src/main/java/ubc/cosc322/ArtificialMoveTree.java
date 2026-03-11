package ubc.cosc322;

import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.Collections;
import java.util.PriorityQueue;

public class ArtificialMoveTree {
    ArtificialPlayer player;

    ArrayList<MoveNode> parents; //this stores the parents of the leaf nodes to make minimax sorting easier
    MoveNode current;
    public ArtificialMoveTree(ArtificialPlayer player){
        parents = new ArrayList<>();
        System.out.println("Move tree initialized.");
        this.player=player;
        current = new MoveNode(null,this.player,null);
        parents.add(current);
        this.current.generateChildren();
        System.out.println("Current parent nodes:" + this.parents.size());
    }

    //gets the best immediate next move based on depth search
    //returns an arraylist of arraylists containing the 3 arraylists corresponding to move syntax
    MoveNode getNextMoveNode(){
        //we take advantage of pqueue sorting to simply peek the best values of the minimax tree and take them

        PriorityQueue<MoveNode> parent_comp=new PriorityQueue<>(Collections.reverseOrder());

            if(!this.current.children.isEmpty()){
                parent_comp.add(this.current.children.peek());
            }else{
                parent_comp.add(this.current);
            }

        return parent_comp.peek();
    }
    ArrayList<ArrayList<Integer>> getNextMove(){
        MoveNode nextNode = this.getNextMoveNode();
        return (nextNode != null) ? nextNode.move : null;
    }
    
    boolean hasValidMoves() {
        return this.current != null && !this.current.children.isEmpty();
    }
    
    boolean is_max(){ //determines if we need to take the max of the min children (true) or min of the max children (false)
        return true;
    }
    void progressMove(){
        //applies a move to traverse the tree
        //the children nodes need to be updated to the next max_depth


        //updates the current pointer node to be the actual game state
        for(MoveNode m: this.current.children){
            if(this.player.gameBoard.equals(m.gameState)){
                System.out.println("Progressed move to updated game state.");
                this.current = m;
                break;
            }
        }

        this.current.generateChildren();

        System.out.println("Generated parents.");
    }
}

class MoveNode implements Comparable<MoveNode>{
    PriorityQueue<MoveNode> children = new PriorityQueue<>();
    ArrayList<Integer> gameState = new ArrayList<>();
    MoveNode parent;
    int value;
    ArrayList<ArrayList<Integer>> move;
    HeuristicEvaluator eval;
    ArtificialPlayer player;
    int turn_color;
    public MoveNode(MoveNode parent, ArtificialPlayer player, ArrayList<ArrayList<Integer>> associatedMove){
        this.move=associatedMove;
        this.player=player;
        this.eval = this.player.heuristicEvaluator;
        this.parent=parent;

        //make a fake gameboard for evaluation, then store this movenode's gamestate

        ArrayList<Integer> fake_board = new ArrayList<>();
        if(parent!=null){
            fake_board = new ArrayList<>(parent.gameState);
        }else{
            fake_board = new ArrayList<>(player.gameBoard);
        }
        if(associatedMove!=null){
            player.heuristicEvaluator.applyMove(fake_board,associatedMove.get(0),associatedMove.get(1),associatedMove.get(2));
        }
        this.gameState=new ArrayList<>(fake_board);
        this.value = player.heuristicEvaluator.evaluate(fake_board,player.myPlayerCode);

        if((this.player.turn_tracker) % 2 == this.player.myPlayerCode-1){
            children = new PriorityQueue<>(Collections.reverseOrder());
        }
    }
    public void generateChildren(){
        int color = (this.player.turn_tracker) % 2 + 1;
        System.out.println("Generating for turn: " + this.player.turn_tracker);
        for(int i = 1; i<11; i++){
            for(int j = 1; j < 11; j++){
                if(this.eval.getCell(this.gameState,i,j)==color){
                    ArrayList<ArrayList<Integer>> moves = this.eval.generateValidMoves(this.gameState,new ArrayList<>(asList(i,j)));
                    for(ArrayList<Integer> to: moves){

                        ArrayList<Integer> fake_board = new ArrayList<>(this.gameState);
                        this.eval.setCell(fake_board,i,j,0);
                        ArrayList<ArrayList<Integer>> arrows = this.eval.generateValidMoves(fake_board,to);

                        ArrayList<Integer> from = new ArrayList<>(asList(i,j));

                        for(ArrayList<Integer> arrow: arrows){
                            MoveNode mn = new MoveNode(this,this.player, new ArrayList<>(asList(from, to, arrow)));
                            mn.turn_color=color;
                            this.children.add(mn);
                        }

                    }
                }
            }
        }
    }
    @Override
    public int compareTo(MoveNode o) {
        return this.value-o.value;
    }
}