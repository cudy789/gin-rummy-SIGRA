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

public class SiftAgent implements GinRummyPlayer {
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
  public boolean willDrawFaceUpCard(Card card) {
    double averageUnknowns = computeExpectedDeadwoodOfUnknowns();
    Card cardToDiscardIfFaceUpPicked = drawAndPickBestDiscard(card, pickHandWithLeastDeadwood);

    // If true, pick up Face Up card.
    ArrayList<Card> handAfterPickingFaceUpCard =
        this.handDrawingAndDiscarding(card, cardToDiscardIfFaceUpPicked);
    if (deadwoodMinusMelds(handAfterPickingFaceUpCard) < averageUnknowns) {
      my_hand = handAfterPickingFaceUpCard;
      return true;
    } else {
      // We will draw a random card. Add the Face Up card to our copy of the discard pile.
      discard_pile.push(card);
      return false;
    }
  }

  @Override
  public void reportDraw(int playerNum, Card drawnCard) {
    // we pushed the faceup card onto the discard_pile in wilLDrawFaceUpCard
    if (discard_pile.size() > 0 && drawnCard != null && drawnCard.equals(discard_pile.peek())) {
      // if the drawn card is the top of the discard, remove it. we'll add it to the appropriate
      // hand later
      discard_pile.pop();
    } else if (playerNum != my_number) {
      // it's not our turn and our opponent passed on the top card
      opponent_passed.add(drawnCard);
    }
    if (playerNum == my_number) {
      my_hand.add(drawnCard);
    } else {
      opponent_hand_known.add(drawnCard);
    }
  }

  @Override
  public Card getDiscard() {
    // TODO: uhhhhh... just give 'em a card from our hand
    return my_hand.remove(0);
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
    // we never initiate a knock, so we only report melds if the other player knocks
    if (opponent_melds != null) {
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
        // Possibly could be parallelStream() but this might be faster, because less mess of
        // threads?
        this.my_hand.stream()
            .map(discardAndAddDrawnCardMapper(drawn))
            .reduce(pickHandWithLeastDeadwood)
            .orElse(new ArrayList<Card>());
    // This should always have exactly one card
    ArrayList<Card> rv = diffHands(bestHand, this.my_hand);
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
      return this.handDrawingAndDiscarding(drawn, discarded);
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

  ArrayList<Card> handDrawingAndDiscarding(Card drawn, Card discarded) {
    ArrayList<Card> rv = new ArrayList<Card>(this.my_hand);
    rv.remove(discarded);
    rv.add(drawn);
    return rv;
  }

  static int deadwoodMinusMelds(ArrayList<Card> hand) {
    return GinRummyUtil.getDeadwoodPoints(GinRummyUtil.cardsToBestMeldSets(hand).get(0), hand);
  }

  static ArrayList<Card> diffHands(ArrayList<Card> a, ArrayList<Card> b) {
    ArrayList<Card> rv = new ArrayList<Card>(a);
    rv.removeAll(b);
    return rv;
  }
}
