package siftagent;

import ginrummy.Card;
import ginrummy.GinRummyUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SecondOrderDeadwoodMinimizingAgent extends NaiveDeadwoodMinimizingAgent {

  // Optimal seems to be around 0.8 to 0.85
  double REDUCTION_WEIGHT = 1.0;

  boolean REMOVE_CARDS_ALREADY_IN_MELDS = true;
  boolean TRY_TO_PREDICT_OPPONENT_MELDS = true;

  public SecondOrderDeadwoodMinimizingAgent(double ReductionWeight) {
    super();
    this.REDUCTION_WEIGHT = ReductionWeight;
  }

  @Override
  public BinaryOperator<ArrayList<Card>> accumulator(ArrayList<Card> unknowns) {
    return (a, b) -> {
      if (valueHand(a, this.my_hand, unknowns) < valueHand(b, this.my_hand, unknowns)) {
        return a;
      } else {
        return b;
      }
    };
  }

  @Override
  public boolean willDrawFaceUpCard(Card card) {
    meldsOneAway(this.my_hand, this.unknownCards());
    return super.willDrawFaceUpCard(card);
  }

  static Hashtable<Card, ArrayList<Card>> meldsOneAway(
      ArrayList<Card> hand, ArrayList<Card> unknowns) {
    ArrayList<ArrayList<Card>> initialMelds =
        flattenMeldSets(GinRummyUtil.cardsToBestMeldSets(hand));
    HashSet<ArrayList<Card>> oneAwayMelds = new HashSet<ArrayList<Card>>(unknowns.size());
    HashSet<Card> newCardsInOneAwayMelds = new HashSet<Card>();

    Hashtable<Card, ArrayList<Card>> table = new Hashtable<Card, ArrayList<Card>>();

    unknowns.stream()
        .forEach(
            (card) -> {
              ArrayList<Card> tmp = new ArrayList<Card>(hand);
              tmp.add(card);
              ArrayList<ArrayList<ArrayList<Card>>> possibleMelds =
                  GinRummyUtil.cardsToBestMeldSets(tmp);

              ArrayList<ArrayList<Card>> additionalMelds = flattenMeldSets(possibleMelds);
              additionalMelds.removeAll(initialMelds);

              oneAwayMelds.addAll(additionalMelds);
              if (!additionalMelds.isEmpty()) {
                newCardsInOneAwayMelds.add(card);
              }

              additionalMelds.forEach(
                  (meld) -> {
                    meld.stream()
                        .filter(
                            (c) -> {
                              return hand.contains(c);
                            })
                        .forEach(
                            (c) -> {
                              // Track existing cards.
                              ArrayList<Card> value;
                              if (!table.containsKey(c)) {
                                value = new ArrayList<Card>();
                              } else {
                                value = table.get(c);
                              }
                              value.add(card);
                              table.put(c, value);
                            });
                  });
            });
    // System.out.println(oneAwayMelds);
    // System.out.println(newCardsInOneAwayMelds);

    HashSet<Card> cardsInHandInOneAwayMelds = new HashSet<Card>();
    oneAwayMelds.forEach(
        (meld) -> {
          cardsInHandInOneAwayMelds.addAll(meld);
        });
    // System.out.println(cardsInHandInOneAwayMelds);
    // System.out.println(table);
    return table;
  }

  Function<ArrayList<Card>, Double> secondOrderApproximation(
      ArrayList<Card> initialHand, ArrayList<Card> unknowns) {
    return (hand) -> {
      int n = unknowns.size();
      Hashtable<Card, ArrayList<Card>> table = meldsOneAway(initialHand, unknowns);
      Double reduction =
          table.entrySet().stream()
              .collect(
                  Collectors.reducing(
                      0.0,
                      (entry) -> {
                        return (double) GinRummyUtil.getDeadwoodPoints(entry.getKey())
                            * ((double) entry.getValue().size() / (double) n);
                      },
                      (a, b) -> {
                        return a + b;
                      }));
      return deadwoodMinusMelds(hand) - (this.REDUCTION_WEIGHT * reduction);
    };
  }

  Function<ArrayList<Card>, Double> secondOrderApproximation2(
      ArrayList<Card> initialHand, ArrayList<Card> unknowns) {
    return (hand) -> {
      int n = unknowns.size();
      Hashtable<Card, ArrayList<Card>> table = meldsOneAway(initialHand, unknowns);
      ArrayList<Card> cardsInMeldsAlready = flattenMeldSet(getBestMeldsWrapper(initialHand));
      Double reduction =
          table.entrySet().stream()
              .filter(
                  (entry) -> {
                    return !cardsInMeldsAlready.contains(entry.getKey());
                  })
              .collect(
                  Collectors.reducing(
                      0.0,
                      (entry) -> {
                        return (double) GinRummyUtil.getDeadwoodPoints(entry.getKey())
                            * ((double) entry.getValue().size() / (double) n);
                      },
                      (a, b) -> {
                        return a + b;
                      }));
      return deadwoodMinusMelds(hand) - (this.REDUCTION_WEIGHT * reduction);
    };
  }

  double valueHand(ArrayList<Card> hand, ArrayList<Card> initialHand, ArrayList<Card> unknowns) {
    double value = deadwoodMinusMelds(hand);
    value -= this.REDUCTION_WEIGHT * approximateSecondOrderReduction(hand, initialHand, unknowns);
    if (this.TRY_TO_PREDICT_OPPONENT_MELDS) {
      value -= approximateOpponentLayoffReduction(hand, unknowns);
    }
    return value;
  }

  double approximateSecondOrderReduction(
      ArrayList<Card> hand, ArrayList<Card> initialHand, ArrayList<Card> unknowns) {
    int n = unknowns.size();
    Hashtable<Card, ArrayList<Card>> table = meldsOneAway(initialHand, unknowns);
    ArrayList<Card> cardsInMeldsAlready = flattenMeldSet(getBestMeldsWrapper(initialHand));
    return table.entrySet().stream()
        .filter(
            (entry) -> {
              return this.REMOVE_CARDS_ALREADY_IN_MELDS
                  && !cardsInMeldsAlready.contains(entry.getKey());
            })
        .collect(
            Collectors.reducing(
                0.0,
                (entry) -> {
                  return (double) GinRummyUtil.getDeadwoodPoints(entry.getKey())
                      * ((double) entry.getValue().size() / (double) n);
                },
                (a, b) -> {
                  return a + b;
                }));
  }

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

  ArrayList<Card> cardsPossiblyInOpponentMelds(ArrayList<Card> unknowns) {
    ArrayList<Card> rv = new ArrayList<Card>();

    ArrayList<Card> cardsInOpponentsMelds =
        flattenMeldSet(getBestMeldsWrapper(this.opponent_hand_known));

    Hashtable<Card, ArrayList<Card>> meldsOneAway =
        meldsOneAway(this.opponent_hand_known, unknowns);
    ArrayList<Card> cardsOneAway =
        meldsOneAway.entrySet().stream()
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
