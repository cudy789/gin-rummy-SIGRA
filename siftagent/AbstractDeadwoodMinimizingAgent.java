package siftagent;

import ginrummy.Card;
import java.util.ArrayList;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public abstract class AbstractDeadwoodMinimizingAgent extends SiftAgent
    implements DeadwoodMinimizingAgent {
  @Override
  // We will not set the new hand here. Do that in reportDraw.
  public boolean willDrawFaceUpCard(Card card) {
    // If card can join meld pick it up.
    if (willCardMakeOrJoinMeldInHand(card)) {
      return true;
    }

    double averageUnknowns = computeExpectedDeadwoodOfUnknowns();
    Card cardToDiscardIfFaceUpPicked = drawAndPickBestDiscard(card);
    ArrayList<Card> handAfterPickingFaceUpCard =
        this.handByDrawingAndDiscarding(card, cardToDiscardIfFaceUpPicked);

    // If true, pick up Face Up card.
    if (deadwoodMinusMelds(handAfterPickingFaceUpCard) < averageUnknowns) {
      return true;
    } else {
      // We will draw the random card. Add the Face Up card to our copy of the discard
      // pile, neither
      // player will see it again.
      discard_pile.push(card);
      return false;
    }
  }

  @Override
  public void reportDraw(int playerNum, Card drawnCard) {
    // we pushed the faceup card onto the discard_pile in willDrawFaceUpCard
    if (discard_pile.size() > 0 && drawnCard != null && drawnCard.equals(discard_pile.peek())) {
      // if the drawn card is the top of the discard, remove it. we'll add it to the
      // appropriate hand later
      discard_pile.pop();
    } else if (playerNum != my_number) {
      // it's not our turn and our opponent passed on the top card
      opponent_passed.add(drawnCard);
    }
    if (playerNum == my_number) {
      cardToDiscard = drawAndPickBestDiscard(drawnCard);
      my_hand = this.handByDrawingAndDiscarding(drawnCard, cardToDiscard);
    } else {
      opponent_hand_known.add(drawnCard);
    }
  }

  double computeExpectedDeadwoodOfUnknowns() {
    // For each possible card we could draw, figure out the best way we could discard.
    return unknownCards().stream()
        .parallel()
        .map(drawAndPickBestDiscardMapper)
        .mapToInt(computeDeadwoodOfHandMapper)
        .average()
        .orElse(Double.MAX_VALUE);
  }

  // Try every way of discarding, and return the best choice given the result of 'accumulator'.
  Card drawAndPickBestDiscard(Card drawn) {
    ArrayList<Card> tmp = new ArrayList<Card>(this.my_hand);
    tmp.add(drawn);
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
            .reduce(this.accumulator(this.unknownCardsMinus(drawn)))
            .orElseThrow();
    ArrayList<Card> rv = fromHandSubtractHand(this.my_hand, bestHand);
    if (rv.isEmpty()) {
      return drawn;
    }
    return rv.get(0);
  }

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

  Function<? super Card, ArrayList<Card>> drawAndPickBestDiscardMapper = (drawn) -> {
      return this.handByDrawingAndDiscarding(drawn, drawAndPickBestDiscard(drawn));
    };

  ArrayList<Card> handByDrawingAndDiscarding(Card drawn, Card discarded) {
    ArrayList<Card> rv = new ArrayList<Card>(this.my_hand);
    rv.add(drawn);
    rv.remove(discarded);
    return rv;
  }
}
