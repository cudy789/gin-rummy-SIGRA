package siftagent;

import java.util.ArrayList;

import ginrummy.Card;
import ginrummy.GinRummyPlayer;
import ginrummy.GinRummyUtil;

class SiftAgent implements GinRummyPlayer {
    int my_number;
    ArrayList<Card> my_hand = new ArrayList<Card>();
    ArrayList<Card> opponent_hand = new ArrayList<Card>();
    ArrayList<Card> discarded = new ArrayList<Card>();
    
    @Override
    public void startGame(int playerNum, int startingPlayerNum, Card[] cards) {
        my_number = playerNum;
        for (Card card : cards) {
            my_hand.add(card);
        }        
    }
    @Override
    public boolean willDrawFaceUpCard(Card card) {
        // TODO: uhhhh...
        return false;
    }
    @Override
    public void reportDraw(int playerNum, Card drawnCard) {
        if (playerNum == my_number) {
            my_hand.add(drawnCard);
        } else {
            opponent_hand.add(drawnCard);
        }
    }
    @Override
    public Card getDiscard() {
        // TODO: uhhhhh...
        return my_hand.get(0);
    }
    @Override
    public void reportDiscard(int playerNum, Card discardedCard) {
        if (playerNum == my_number) {
            my_hand.remove(discardedCard);
        } else {
            opponent_hand.remove(discardedCard);
        }
        discarded.remove(discardedCard);
    }
    @Override
    public ArrayList<ArrayList<Card>> getFinalMelds() {
        return null;
    }
    @Override
    public void reportFinalMelds(int playerNum, ArrayList<ArrayList<Card>> melds) {}
    @Override
    public void reportScores(int[] scores) {}
    @Override
    public void reportLayoff(int playerNum, Card layoffCard, ArrayList<Card> opponentMeld) {}
    @Override
    public void reportFinalHand(int playerNum, ArrayList<Card> hand) {}
}
