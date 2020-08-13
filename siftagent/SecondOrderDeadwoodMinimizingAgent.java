package siftagent;

import ginrummy.Card;
import ginrummy.GinRummyUtil;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SecondOrderDeadwoodMinimizingAgent extends NaiveDeadwoodMinimizingAgent {

  // Optimal seems to be around 0.8 to 0.85
  double SECOND_ORDER_REDUCTION_WEIGHT = 0.85;
  double OPPONENT_MELDS_REDUCTION_WEIGHT = 0.45;

  boolean REMOVE_CARDS_ALREADY_IN_MELDS = true;
  boolean TRY_TO_PREDICT_OPPONENT_MELDS = false;

    public SecondOrderDeadwoodMinimizingAgent() {
        super();
    }

  private SecondOrderDeadwoodMinimizingAgent(double SecondOrderWeight) {
    super();
    this.SECOND_ORDER_REDUCTION_WEIGHT = SecondOrderWeight;
    this.TRY_TO_PREDICT_OPPONENT_MELDS = false;
  }

  private SecondOrderDeadwoodMinimizingAgent(double SecondOrderWeight, double OppMeldsWeight) {
    super();
    this.SECOND_ORDER_REDUCTION_WEIGHT = SecondOrderWeight;
    this.TRY_TO_PREDICT_OPPONENT_MELDS = true;
    this.OPPONENT_MELDS_REDUCTION_WEIGHT = OppMeldsWeight;
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

  static ArrayList<ArrayList<Card>> meldsOneAway(ArrayList<Card> hand, ArrayList<Card> unknowns) {
    ArrayList<ArrayList<Card>> initialMelds =
        flattenMeldSets(GinRummyUtil.cardsToBestMeldSets(hand));
    return unknowns.stream()
        .map(
            (card) -> {
              ArrayList<Card> tmp = new ArrayList<Card>(hand);
              tmp.add(card);
              ArrayList<ArrayList<ArrayList<Card>>> possibleMelds =
                  GinRummyUtil.cardsToBestMeldSets(tmp);

              ArrayList<ArrayList<Card>> additionalMelds = flattenMeldSets(possibleMelds);
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

  static Hashtable<Card, ArrayList<Card>> meldsOneAwayTable(
      ArrayList<Card> hand, ArrayList<Card> unknowns) {
    ArrayList<ArrayList<Card>> initialMelds =
        flattenMeldSets(GinRummyUtil.cardsToBestMeldSets(hand));

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

              additionalMelds.forEach(
                  (meld) -> {
                    meld.stream()
                        .filter(
                            (c) -> {
                              return hand.contains(c);
                            })
                        .forEach(
                            (c) -> {
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

    return table;
  }

  double valueHand(ArrayList<Card> hand, ArrayList<Card> initialHand, ArrayList<Card> unknowns) {
    double value = deadwoodMinusMelds(hand);
    value -=
        this.SECOND_ORDER_REDUCTION_WEIGHT
            * approximateSecondOrderReduction(hand, initialHand, unknowns);
    if (this.TRY_TO_PREDICT_OPPONENT_MELDS) {
      value -=
          this.OPPONENT_MELDS_REDUCTION_WEIGHT * approximateOpponentLayoffReduction(hand, unknowns);
    }
    return value;
  }

  double approximateSecondOrderReduction(
      ArrayList<Card> hand, ArrayList<Card> initialHand, ArrayList<Card> unknowns) {
    int n = unknowns.size();
    Hashtable<Card, ArrayList<Card>> table = meldsOneAwayTable(initialHand, unknowns);
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

  double approximateOpponentLayoffReduction2(ArrayList<Card> hand, ArrayList<Card> unknowns) {
    if (this.opponent_hand_known.isEmpty()) {
      // We know noting.
      return 0.0;
    }
    return meldsOneAway(hand, unknowns).stream()
        .map(
            (meld) -> {
              return meld.stream()
                  .filter(
                      (x) -> {
                        return hand.contains(x);
                      })
                  .findFirst()
                  .orElseThrow(null);
            })
        .collect(
            Collectors.reducing(
                0.0,
                (x) -> {
                  return (double) GinRummyUtil.getDeadwoodPoints(x);
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
