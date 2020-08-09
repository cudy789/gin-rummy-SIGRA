package siftagent;

import ginrummy.Card;
import ginrummy.GinRummyUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class SecondOrderDeadwoodMinimizingAgent extends NaiveDeadwoodMinimizingAgent {

    double REDUCTION_WEIGHT = 1.0;

    public SecondOrderDeadwoodMinimizingAgent(double ReductionWeight) {
        super();
        this.REDUCTION_WEIGHT = ReductionWeight;
    }

  @Override
  public BinaryOperator<ArrayList<Card>> accumulator(ArrayList<Card> unknowns) {
    return (a, b) -> {
        if (secondOrderApproximation(this.my_hand, unknowns).apply(a) < secondOrderApproximation(this.my_hand, unknowns).apply(b)) {
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

  static Hashtable<Card, ArrayList<Card>> meldsOneAway(ArrayList<Card> hand, ArrayList<Card> unknowns) {
    ArrayList<ArrayList<Card>> initialMelds =
        squishMeldSets(GinRummyUtil.cardsToBestMeldSets(hand));
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

              ArrayList<ArrayList<Card>> additionalMelds = squishMeldSets(possibleMelds);
              additionalMelds.removeAll(initialMelds);

              oneAwayMelds.addAll(additionalMelds);
              if (!additionalMelds.isEmpty()) {
                newCardsInOneAwayMelds.add(card);
              }

              additionalMelds.forEach((meld) -> {
                  meld.stream().filter((c) -> { return hand.contains(c); }).forEach((c) -> {
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
    oneAwayMelds.forEach((meld) -> {
        cardsInHandInOneAwayMelds.addAll(meld);
    });
    // System.out.println(cardsInHandInOneAwayMelds);
    // System.out.println(table);
    return table;
  }

  static ArrayList<ArrayList<Card>> squishMeldSets(ArrayList<ArrayList<ArrayList<Card>>> meldSets) {
    ArrayList<ArrayList<Card>> rv = new ArrayList<ArrayList<Card>>();
    meldSets.forEach(
        (set) -> {
          rv.addAll(set);
        });
    return rv;
  }

  BinaryOperator<ArrayList<Card>> pickHandWithBestSecondOrderApproximation(
      ArrayList<Card> initialHand) {

    return (a, b) -> {
      if (deadwoodMinusMelds(a) < deadwoodMinusMelds(b)) {
        return a;
      } else {
        return b;
      }
    };
  }

  Function<ArrayList<Card>, Double> secondOrderApproximation(ArrayList<Card> initialHand, ArrayList<Card> unknowns) {
    return (hand) -> {
      int n = unknowns.size();
      Hashtable<Card, ArrayList<Card>> table = meldsOneAway(initialHand, unknowns);
      Double reduction = table.entrySet().stream().collect(Collectors.reducing(0.0, (entry) -> {
          return (double) GinRummyUtil.getDeadwoodPoints(entry.getKey()) * ((double) entry.getValue().size()/(double) n);
      }, (a, b) -> { return a + b; }));
      return deadwoodMinusMelds(hand) - (this.REDUCTION_WEIGHT * reduction);
    };
}
}
