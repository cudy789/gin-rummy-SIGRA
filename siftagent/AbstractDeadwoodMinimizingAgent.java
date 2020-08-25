package siftagent;

import ginrummy.Card;
import java.util.ArrayList;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public abstract class AbstractDeadwoodMinimizingAgent extends SiftAgent
    implements DeadwoodMinimizingAgent {

  public BinaryOperator<ArrayList<Card>> accumulator(ArrayList<Card> unknowns) {
    Function<ArrayList<Card>, Double> evaluator = this.evaluator(unknowns);
    return (a, b) -> {
      if (evaluator.apply(a) < evaluator.apply(b)) {
        return a;
      } else {
        return b;
      }
    };
  }

  @Override
  // We will not set the new hand here. Do that in reportDraw.
  public boolean willDrawFaceUpCard(Card card) {
    // If card can join meld pick it up.
    if (willCardMakeOrJoinMeldInHand(this.my_hand, card)) {
      return true;
    }

    // Expected value of drawing from unknown cards
    double expectedValueOfUnknowns =
        computeExpectedValueOfUnknowns(this.my_hand, this.unknownCards());

    // Compute value of hand after drawing face up card, given that we picked the best discard.
    Card cardToDiscardIfFaceUpPicked =
        drawAndPickBestDiscard(this.my_hand, card, this.unknownCards());
    ArrayList<Card> handAfterPickingFaceUpCard =
        handByDrawingAndDiscarding(this.my_hand, card, cardToDiscardIfFaceUpPicked);
    double valueOfBestHandAfterDrawningFaceUpCard =
        this.evaluator(this.unknownCards()).apply(handAfterPickingFaceUpCard);

    // If true, pick up Face Up card.
    if (valueOfBestHandAfterDrawningFaceUpCard < expectedValueOfUnknowns) {
      return true;
    } else {
      // We will draw the random card. Add the Face Up card to our copy of the discard
      // pile, neither player will see it again.
      discard_pile.push(card);
      return false;
    }
  }

  @Override
  public void reportDraw(int playerNum, Card drawnCard) {
    // we pushed the faceup card onto the discard_pile in willDrawFaceUpCard
    if (discard_pile.size() > 0 && drawnCard != null && drawnCard.equals(discard_pile.peek())) {
      // if the drawn card is the top of the discard, remove it from the discard pile.
      // we'll add it to the appropriate hand later.
      discard_pile.pop();
    } else if (playerNum != my_number) {
      // it's not our turn and our opponent passed on the top card
      opponent_passed.add(drawnCard);
    }

    if (playerNum == my_number) {
      cardToDiscard = drawAndPickBestDiscard(this.my_hand, drawnCard, this.unknownCards());
      my_hand = handByDrawingAndDiscarding(this.my_hand, drawnCard, cardToDiscard);
    } else if (drawnCard != null) {
      opponent_hand_known.add(drawnCard);
    }
  }

  // Iterates over each possible hand, given a paricular unknown card, and determines the hand with
  // the best (least) value, according to 'evaluator'.
  // Then takes the expectation value of the best hand for all cards in unknowns.
  double computeExpectedValueOfUnknowns(ArrayList<Card> hand, ArrayList<Card> unknowns) {
    // For each possible card we could draw, figure out the best way we could discard.
    return unknowns.stream()
        .parallel()
        .map(drawAndPickBestDiscardMapper(hand, unknowns))
        .mapToDouble(evaluateHandMapper(unknowns))
        .average()
        .orElse(Double.MAX_VALUE);
  }

  // Try every way of discarding, and return the best choice given the result of 'evaluator'.
  Card drawAndPickBestDiscard(ArrayList<Card> hand, Card drawn, ArrayList<Card> unknowns) {
    ArrayList<Card> tmp = new ArrayList<Card>(hand);
    tmp.add(drawn);

    ArrayList<Card> unknownsMinusDrawn = new ArrayList<Card>(unknowns);
    unknownsMinusDrawn.remove(drawn);

    // Compute best hand, over all possible hands. Uses 'evaluator' to determine the best hand.
    ArrayList<Card> bestHand =
        // Actually faster to run this sequentially, otherwise it probably bogs down the thread
        // pool.
        tmp.stream() // .parallel()
            .map(
                (c) -> {
                  ArrayList<Card> rv = new ArrayList<Card>(tmp);
                  rv.remove(c);
                  return rv;
                })
            .reduce(this.accumulator(unknownsMinusDrawn))
            .orElseThrow(null);

    ArrayList<Card> rv = fromHandSubtractHand(hand, bestHand);
    if (rv.isEmpty()) {
      return drawn;
    }
    return rv.get(0);
  }

  // Maps an ArrayList<Card> to its DeadwoodMinusMelds
  ToDoubleFunction<? super ArrayList<Card>> evaluateHandMapper(ArrayList<Card> unknowns) {
    return (x) -> {
      return this.evaluator(unknowns).apply(x);
    };
  }

  Function<? super Card, ArrayList<Card>> drawAndPickBestDiscardMapper(
      ArrayList<Card> hand, ArrayList<Card> unknowns) {
    return (drawn) -> {
      return handByDrawingAndDiscarding(
          hand, drawn, this.drawAndPickBestDiscard(hand, drawn, unknowns));
    };
  }

  // Returns a copy of hand, swapping discarded with drawn.
  static ArrayList<Card> handByDrawingAndDiscarding(
      ArrayList<Card> hand, Card drawn, Card discarded) {
    ArrayList<Card> rv = new ArrayList<Card>(hand);
    rv.add(drawn);
    rv.remove(discarded);
    return rv;
  }

  // Mapper for above function. Mapper takes discarded card as argument.
  static Function<? super Card, ArrayList<Card>> discardAndAddDrawnCardMapper(
      ArrayList<Card> hand, Card drawn) {
    return (discarded) -> {
      return handByDrawingAndDiscarding(hand, drawn, discarded);
    };
  }
}
