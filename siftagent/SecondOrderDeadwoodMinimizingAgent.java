package siftagent;

import ginrummy.Card;
import ginrummy.GinRummyUtil;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SecondOrderDeadwoodMinimizingAgent extends NaiveDeadwoodMinimizingAgent {

  // Optimal seems to be around 0.8 to 0.85
  double SECOND_ORDER_REDUCTION_WEIGHT = 0.85;
  double OPPONENT_MELDS_REDUCTION_WEIGHT = 0.45;

  boolean REMOVE_CARDS_ALREADY_IN_MELDS = true;
  boolean TRY_TO_PREDICT_OPPONENT_MELDS = false;

  public SecondOrderDeadwoodMinimizingAgent() {
    super();
  }

  public SecondOrderDeadwoodMinimizingAgent(double SecondOrderWeight) {
    super();
    this.SECOND_ORDER_REDUCTION_WEIGHT = SecondOrderWeight;
    this.TRY_TO_PREDICT_OPPONENT_MELDS = false;
  }

  protected SecondOrderDeadwoodMinimizingAgent(double SecondOrderWeight, double OppMeldsWeight) {
    super();
    this.SECOND_ORDER_REDUCTION_WEIGHT = SecondOrderWeight;
    this.TRY_TO_PREDICT_OPPONENT_MELDS = true;
    this.OPPONENT_MELDS_REDUCTION_WEIGHT = OppMeldsWeight;
  }

  @Override
  public Function<ArrayList<Card>, Double> evaluator(ArrayList<Card> unknowns) {
    return (hand) -> {
      return (double) valueHand(hand, unknowns);
    };
  }

  // Compute the score for a given hand.
  double valueHand(ArrayList<Card> hand, ArrayList<Card> unknowns) {
    double value = deadwoodMinusMelds(hand);

    value -= this.SECOND_ORDER_REDUCTION_WEIGHT * approximateSecondOrderReduction(hand, unknowns);

    // Enabled only for opponent modeling
    if (this.TRY_TO_PREDICT_OPPONENT_MELDS) {
      value -=
          this.OPPONENT_MELDS_REDUCTION_WEIGHT * approximateOpponentLayoffReduction(hand, unknowns);
    }
    return value;
  }

  ////
  // Second Order Reduction code
  ////

  // Iterate over all cards in `unknowns`, add that card to our hand, and see which new melds we are
  // now able to create.
  static ArrayList<ArrayList<Card>> meldsOneAway(ArrayList<Card> hand, ArrayList<Card> unknowns) {
    ArrayList<ArrayList<Card>> initialMelds = GinRummyUtil.cardsToAllMelds(hand);
    return unknowns.stream()
        .map(
            (card) -> {
              ArrayList<Card> tmp = new ArrayList<Card>(hand);
              tmp.add(card);

              ArrayList<ArrayList<Card>> additionalMelds = GinRummyUtil.cardsToAllMelds(tmp);
              additionalMelds.removeAll(initialMelds);
              return additionalMelds;
            })
        .collect(
            Collectors.reducing(
                new ArrayList<ArrayList<Card>>(),
                (x) -> {
                  return x;
                },
                (a, b) -> {
                  ArrayList<ArrayList<Card>> rv = new ArrayList<ArrayList<Card>>();
                  rv.addAll(a);
                  rv.addAll(b);
                  return rv;
                }));
  }

  // Hashtable:
  // - Key: Card in our hand
  // - Value: Set of cards which when added to our hand, would produce a meld which contains the key
  // card.
  static Hashtable<Card, ArrayList<Card>> meldsOneAwayTable(
      ArrayList<Card> hand, ArrayList<Card> unknowns) {
    Hashtable<Card, ArrayList<Card>> table = new Hashtable<Card, ArrayList<Card>>();

    meldsOneAway(hand, unknowns).stream()
        .forEach(
            (meld) -> {
              // Determine which card was added to create this meld.
              ArrayList<Card> possibles = new ArrayList<Card>(meld);
              possibles.removeAll(hand);
              if (possibles.size() != 1) {
                System.out.println(possibles.size());
                System.exit(1);
              }
              Card addedCard = possibles.get(0);

              // Now do bookkeeping for cards that are in our hand, which will be in a meld if
              // `addedCard` is drawn.
              meld.stream()
                  .filter(
                      (card) -> {
                        return hand.contains(card);
                      })
                  .forEach(
                      (card) -> {

                        // Get possibly exant value from hashtable, otherwise create new array.
                        ArrayList<Card> value;
                        if (!table.containsKey(card)) {
                          value = new ArrayList<Card>();
                        } else {
                          value = table.get(card);
                        }
                        // Then insert `addedCard` to array, and put array back into table.
                        value.add(addedCard);
                        table.put(card, value);
                      });
            });
    return table;
  }

  double approximateSecondOrderReduction(ArrayList<Card> hand, ArrayList<Card> unknowns) {
    Hashtable<Card, ArrayList<Card>> table = meldsOneAwayTable(hand, unknowns);
    ArrayList<Card> cardsAlreadyInMelds = flattenMeldSet(getBestMeldsWrapper(hand));
    return table.entrySet().stream()
        .filter(
            (entry) -> {
              return !(this.REMOVE_CARDS_ALREADY_IN_MELDS
                  && cardsAlreadyInMelds.contains(entry.getKey()));
            })
        .collect(
            Collectors.reducing(
                0.0,
                (entry) -> {
                  // Weight of the deadwood times the probability that we draw a card which makes it
                  // into a meld.
                  return (double) GinRummyUtil.getDeadwoodPoints(entry.getKey())
                      * ((double) entry.getValue().size() / (double) unknowns.size());
                },
                (a, b) -> {
                  return a + b;
                }));
  }

  ////
  // Opponent Modeling which is not enabled.
  ////

  double approximateOpponentLayoffReduction(ArrayList<Card> hand, ArrayList<Card> unknowns) {
    if (this.opponent_hand_known.isEmpty()) {
      // We know noting.
      return 0.0;
    }
    return cardsPossiblyInOpponentMelds(unknowns).stream()
        .filter(
            (card) -> {
              // Give reduction only for cards we actually have in the hand we are looking at.
              return hand.contains(card);
            })
        .collect(
            Collectors.reducing(
                0.0,
                (card) -> {
                  return (double) GinRummyUtil.getDeadwoodPoints(card);
                },
                (a, b) -> {
                  return a + b;
                }));
  }

  ArrayList<Card> cardsPossiblyInOpponentMelds(ArrayList<Card> hand) {
    ArrayList<Card> rv = new ArrayList<Card>();

    ArrayList<Card> cardsInOpponentsMelds =
        flattenMeldSet(getBestMeldsWrapper(this.opponent_hand_known));

    // Find cards in our hand of interest which we might be able to lay off on opponents melds.
    Hashtable<Card, ArrayList<Card>> meldsWeCanMake =
        meldsOneAwayTable(this.opponent_hand_known, hand);
    ArrayList<Card> cardsOneAway =
        meldsWeCanMake.entrySet().stream()
            .collect(
                Collectors.reducing(
                    new ArrayList<Card>(),
                    (x) -> {
                      return x.getValue();
                    },
                    (a, b) -> {
                      ArrayList<Card> x = new ArrayList<Card>();
                      x.addAll(a);
                      x.addAll(b);
                      return x;
                    }));

    rv.addAll(cardsInOpponentsMelds);
    rv.addAll(cardsOneAway);

    return rv;
  }
}
