package siftagent;

import ginrummy.Card;
import java.util.ArrayList;

public class NaiveDeadwoodMinimizingAgent extends SiftAgent {

  @Override
  // We will not set the new hand here. Do that in reportDraw.
  public boolean willDrawFaceUpCard(Card card) {
      // If card can join meld pick it up.
    if (willCardMakeOrJoinMeldInHand(card)) {
        return true;
    }

    double averageUnknowns = computeExpectedDeadwoodOfUnknowns();
    Card cardToDiscardIfFaceUpPicked = drawAndPickBestDiscard(card, pickHandWithLeastDeadwood);
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
      // appropriate
      // hand later
      discard_pile.pop();
    } else if (playerNum != my_number) {
      // it's not our turn and our opponent passed on the top card
      opponent_passed.add(drawnCard);
    }
    if (playerNum == my_number) {
      cardToDiscard = drawAndPickBestDiscard(drawnCard, pickHandWithLeastDeadwood);
      my_hand = this.handByDrawingAndDiscarding(drawnCard, cardToDiscard);
    } else {
      opponent_hand_known.add(drawnCard);
    }
  }
}
