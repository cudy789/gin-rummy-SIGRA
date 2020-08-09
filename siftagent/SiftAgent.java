package siftagent;

import ginrummy.Card;
import ginrummy.GinRummyPlayer;
import ginrummy.GinRummyUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

public abstract class SiftAgent implements GinRummyPlayer {
  static final int HAND_SIZE = 10;

  int my_score;
  int opponent_score;

  boolean i_play_first;
  int my_number;

  // cards in our hand
  ArrayList<Card> my_hand;
  // known cards in opponent's hand
  ArrayList<Card> opponent_hand_known;
  // cards that we discarded and our opponent didn't pick up
  ArrayList<Card> opponent_passed;
  // cards in the discard pile (including the top card after any player discards)
  Stack<Card> discard_pile;

  // list of list of opponent's melds, used when knocking
  ArrayList<ArrayList<Card>> opponent_melds;

  // Card to discard, set in reportDraw.
  Card cardToDiscard;

  void reset_for_new_hand() {
    my_hand = new ArrayList<Card>();
    opponent_hand_known = new ArrayList<Card>();
    opponent_passed = new ArrayList<Card>();
    opponent_melds = null;
    discard_pile = new Stack<Card>();
  }

  // despite being called `startGame`, this function is called at the start of each new hand.
  @Override
  public void startGame(int playerNum, int startingPlayerNum, Card[] cards) {
    reset_for_new_hand();
    my_number = playerNum;
    i_play_first = my_number == startingPlayerNum;
    my_hand = new ArrayList<Card>(Arrays.asList(cards));
  }

  @Override
  public Card getDiscard() {
    // Hand was already updated in reportDraw.
    Card rv = this.cardToDiscard;
    this.cardToDiscard = null;
    return rv;
  }

  @Override
  public void reportDiscard(int playerNum, Card discardedCard) {
    discard_pile.push(discardedCard);
    if (playerNum != my_number) {
      opponent_hand_known.remove(discardedCard); // try to remove card from opponent's known hand
    }
  }

  @Override
  public ArrayList<ArrayList<Card>> getFinalMelds() {
    // Only knock if we have gin.
    if (this.haveGin() || opponent_melds != null) {
      return GinRummyUtil.cardsToBestMeldSets(my_hand).get(0);
    }
    return null;
  }

  @Override
  public void reportFinalMelds(int playerNum, ArrayList<ArrayList<Card>> melds) {
    if (playerNum != my_number) {
      opponent_melds = melds;
    }
  }

  @Override
  public void reportScores(int[] scores) {
    my_score += scores[my_number];
    opponent_score += scores[(my_number + 1) % 2];
  }

  @Override
  public void reportLayoff(int playerNum, Card layoffCard, ArrayList<Card> opponentMeld) {}

  @Override
  public void reportFinalHand(int playerNum, ArrayList<Card> hand) {}

  boolean haveGin() {
    ArrayList<ArrayList<ArrayList<Card>>> bestMelds =
        GinRummyUtil.cardsToBestMeldSets(this.my_hand);
    if (bestMelds.isEmpty()) {
      return false;
    }
    return bestMelds.get(0).stream()
            .mapToInt(
                (x) -> {
                  return x.size();
                })
            .reduce(
                (a, b) -> {
                  return a + b;
                })
            .orElse(0)
        == this.HAND_SIZE;
  }

  ArrayList<Card> unknownCards() {
    ArrayList<Card> rv = new ArrayList<Card>(Arrays.asList(Card.allCards));
    rv.removeAll(this.discard_pile);
    rv.removeAll(this.my_hand);
    rv.removeAll(this.opponent_hand_known);
    return rv;
  }

  Stream<ArrayList<Card>> enumeratePossibleHandsByAddingFrom(ArrayList<Card> cards) {
    return cards.parallelStream()
        .map(
            (drawn) -> {
              return this.my_hand.stream()
                  .map(
                      (discarded) -> {
                        ArrayList<Card> x = new ArrayList<Card>(this.my_hand);
                        x.remove(discarded);
                        x.add(drawn);
                        return x;
                      });
            })
        .reduce(
            (a, b) -> {
              return Stream.concat(a, b);
            })
        .orElse(Stream.empty());
  }

  double computeExpectedDeadwoodOfUnknowns() {
    // For each possible card we could draw, figure out the best way we could discard.
    return unknownCards().parallelStream()
        .map(drawAndPickBestDiscardMapper(pickHandWithLeastDeadwood))
        .mapToInt(computeDeadwoodOfHandMapper)
        .average()
        .orElse(Double.MAX_VALUE);
  }

  Card drawAndPickBestDiscard(Card drawn, BinaryOperator<ArrayList<Card>> accumulator) {
    ArrayList<Card> bestHand =
        this.my_hand.parallelStream()
            .map(discardAndAddDrawnCardMapper(drawn))
            .reduce(pickHandWithLeastDeadwood)
            .orElse(new ArrayList<Card>());
    // This should always have exactly one card
    ArrayList<Card> rv = fromHandSubtractHand(this.my_hand, bestHand);
    return rv.get(0);
  }

  BinaryOperator<ArrayList<Card>> pickHandWithLeastDeadwood =
      (a, b) -> {
        if (deadwoodMinusMelds(a) < deadwoodMinusMelds(b)) {
          return a;
        } else {
          return b;
        }
      };

  // Maps an ArrayList<Card> to its DeadwoodMinusMelds
  ToIntFunction<? super ArrayList<Card>> computeDeadwoodOfHandMapper =
      (x) -> {
        return deadwoodMinusMelds(x);
      };

  Function<? super Card, ArrayList<Card>> discardAndAddDrawnCardMapper(Card drawn) {
    return (discarded) -> {
      return this.handByDrawingAndDiscarding(drawn, discarded);
    };
  }

  Function<? super Card, ArrayList<Card>> drawAndPickBestDiscardMapper(
      BinaryOperator<ArrayList<Card>> accumulator) {
    return (drawn) -> {
      ArrayList<Card> x = new ArrayList<Card>(this.my_hand);
      x.remove(drawAndPickBestDiscard(drawn, accumulator));
      x.add(drawn);
      return x;
    };
  }

  ArrayList<Card> handByDrawingAndDiscarding(Card drawn, Card discarded) {
    ArrayList<Card> rv = new ArrayList<Card>(this.my_hand);
    rv.remove(discarded);
    rv.add(drawn);
    return rv;
  }

  static int deadwoodMinusMelds(ArrayList<Card> hand) {
    ArrayList<ArrayList<ArrayList<Card>>> bestMelds = GinRummyUtil.cardsToBestMeldSets(hand);
    if (bestMelds.isEmpty()) {
      return GinRummyUtil.getDeadwoodPoints(hand);
    }
    return GinRummyUtil.getDeadwoodPoints(bestMelds.get(0), hand);
  }

  static ArrayList<Card> fromHandSubtractHand(ArrayList<Card> from, ArrayList<Card> h) {
    ArrayList<Card> rv = new ArrayList<Card>(from);
    rv.removeAll(h);
    return rv;
  }
}
