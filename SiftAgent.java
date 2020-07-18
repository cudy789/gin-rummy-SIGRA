package siftagent;

import ginrummy.Card;
import ginrummy.GinRummyPlayer;

class SiftAgent implements GinRummyPlayer {
    void startGame(int playerNum, int startingPlayerNum, Card[] cards);
    boolean willDrawFaceUpCard(Card card);
    void reportDraw(int playerNum, Card drawnCard);
    Card getDiscard() {}
    void reportDiscard(int playerNum, Card discardedCard);
    ArrayList<ArrayList<Card>> getFinalMelds();
    void reportFinalMelds(int playerNum, ArrayList<ArrayList<Card>> melds);
    void reportScores(int[] scores);
    void reportLayoff(int playerNum, Card layoffCard, ArrayList<Card> opponentMeld);
    void reportFinalHand(int playerNum, ArrayList<Card> hand);
}
