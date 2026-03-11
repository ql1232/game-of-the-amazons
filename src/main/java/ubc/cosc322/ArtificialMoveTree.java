package ubc.cosc322;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.PriorityQueue;

import static java.lang.Integer.valueOf;
import static java.util.Arrays.asList;

public class ArtificialMoveTree {
    ArtificialPlayer player;
    int max_depth = 10;

    ArrayList<MoveNode> parents = new ArrayList<>(); //this stores the parents of the leaf nodes to make minimax sorting easier
    MoveNode current;
    public ArtificialMoveTree(ArtificialPlayer player){
        System.out.println("Move tree initialized.");
        this.player=player;
        current = new MoveNode(null,this.player,null,0);
        this.expandDepth(current,0);
    }

    public void expandDepth(MoveNode root, int count){
        if(count==this.max_depth){
            return;
        }
        root.generateChildrenInitial();
        if(count==this.max_depth-1){
            this.parents.add(root);
        }
        for(MoveNode child: root.children){
            this.expandDepth(child, count+1);
        }
    }

    //gets the best immediate next move based on depth search
    //returns an arraylist of arraylists containing the 3 arraylists corresponding to move syntax
    MoveNode getNextMoveNode(){
        //we take advantage of pqueue sorting to simply peek the best values of the minimax tree and take them

        PriorityQueue<MoveNode> parent_comp=new PriorityQueue<>();
        if(this.is_max()){
            parent_comp=new PriorityQueue<>(Collections.reverseOrder());
        }

        for(MoveNode m: this.parents){
            if(!m.children.isEmpty()){
                parent_comp.add(m.children.peek());
            }else{
                parent_comp.add(m);
            }
        }
        return this.findImmediateNode(parent_comp.peek());
    }
    ArrayList<ArrayList<Integer>> getNextMove(){
        return this.getNextMoveNode().move;
    }
    boolean is_max(){ //determines if we need to take the max of the min children (true) or min of the max children (false)
        return (player.turn_tracker+this.max_depth) % 2 != 0;
    }
    void progressMove(){
        //applies a move to traverse the tree
        //the children nodes need to be updated to the next max_depth

        //updates the current pointer node to be the actual game state
        for(MoveNode m: this.current.children){
            if(this.player.gameBoard.equals(m.gameState)){
                this.current = m;
                break;
            }
        }

        this.pruneTree();

        ArrayList<MoveNode> new_parents = new ArrayList<>();
        for(MoveNode m: this.parents){
            for(MoveNode mn: m.children){
                mn.generateChildren();
                if(mn.children.isEmpty()){
                    new_parents.add(mn.parent);
                }else{
                new_parents.add(mn);}
            }
        }
        this.parents=new_parents;
    }
    void pruneTree(){
        //cleans up the tree to avoid generating for dead nodes that cannot be achieved
        ArrayList<MoveNode> new_parents = new ArrayList<>();
        for(MoveNode m: this.parents){
            if(findImmediateNode(m).parent.equals(this.current)){
                new_parents.add(m);
            }
        }
        this.parents=new_parents;
    }

    MoveNode findImmediateNode(MoveNode ideal_node){
        //traverse up the ideal future game state's tree to find the immediate next move
        if(ideal_node==null){
            return null;
        }
        MoveNode next_node = ideal_node;
        while(true){
            ideal_node=ideal_node.parent;
            if(ideal_node.equals(this.current)){
                break;
            }
            next_node=next_node.parent;
        }
        return next_node;
    }
}

class MoveNode implements Comparable<MoveNode>{
    PriorityQueue<MoveNode> children = new PriorityQueue<>();
    ArrayList<Integer> gameState = new ArrayList<>();
    MoveNode parent;
    int value;
    int max_depth;
    ArrayList<ArrayList<Integer>> move;
    HeuristicEvaluator eval;
    ArtificialPlayer player;
    public MoveNode(MoveNode parent, ArtificialPlayer player, ArrayList<ArrayList<Integer>> associatedMove, int depth){
        this.move=associatedMove;
        this.player=player;
        this.max_depth = depth;
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

        if((this.player.turn_tracker+this.max_depth) % 2 == 0){
            children = new PriorityQueue<>(Collections.reverseOrder());
        }
    }
    public void generateChildren(){
        int color = (this.player.turn_tracker+this.max_depth +1) % 2 + 1;
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
                            MoveNode mn = new MoveNode(this,this.player, new ArrayList<>(asList(from, to, arrow)), max_depth);
                            this.children.add(mn);
                        }

                    }
                }
            }
        }
    }
    public void generateChildrenInitial(){
        int color = (this.player.turn_tracker+this.max_depth +1) % 2 + 1;
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
                            MoveNode mn = new MoveNode(this,this.player, new ArrayList<>(asList(from, to, arrow)), max_depth+1);
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