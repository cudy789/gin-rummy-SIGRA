package siftagent;

import ginrummy.Card;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

public class SecondOrderDeadwoodMinimizingAgent extends SiftAgent {

  @Override
  public boolean willDrawFaceUpCard(Card card) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void reportDraw(int playerNum, Card drawnCard) {
    // TODO Auto-generated method stub

  }

  static void matchMeldsOneAway(ArrayList<Card> hand) {
    hand.parallelStream()
        .forEach(
            (card) -> {
              IntStream.range(0, Card.NUM_SUITS)
                  .parallel()
                  .mapToObj(
                      (suit) -> {
                        if (card.getSuit() == suit) {
                          return new ArrayList<Card>();
                        }
                        if (hand.contains(Card.getCard(card.rank, suit))) {
                          return new ArrayList<Card>(Arrays.asList());
                        }
                        return hand;
                      });
            });
  }
}
