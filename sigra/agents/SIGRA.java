// -*- c-basic-offset: 2; indent-tabs-mode: nil; -*-

package sigra.agents;

import ginrummy.Card;
import ginrummy.GinRummyUtil;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.function.Function;
import java.util.stream.Collectors;
import sigra.agents.util.AbstractDeadwoodMinimizingAgent;
import sigra.agents.util.SiftAgent;
import java.lang.StringBuilder;

public class SIGRA extends AbstractDeadwoodMinimizingAgent {

  // Optimal seems to be around 0.8 to 0.85
  // Set Either to 0.0 to disable.
  double SECOND_ORDER_REDUCTION_WEIGHT = 0.85;
  double OPPONENT_MELDS_REDUCTION_WEIGHT = 1.0;

  // Other Feature Flags
  boolean REMOVE_CARDS_ALREADY_IN_MELDS = true;
  boolean REMOVE_OPPONENT_MELDS_WO_KNOWN_CARD = true;
  boolean REMOVE_OPPONENT_OF_A_KIND_DISCARDED = true;
  boolean REMOVE_OPPONENT_OF_A_KIND_PASSED_OVER = true;

  private String _name = null;

  public String name() {
    if (_name == null) {
      StringBuilder name_builder = new StringBuilder();
      if (STUBBORN) {
        name_builder.append("Stubborn");
      }
      if (SECOND_ORDER_REDUCTION_WEIGHT != 0.0) {
        name_builder.append("Mmd");
      }
      if (OPPONENT_MELDS_REDUCTION_WEIGHT != 0.0) {
        name_builder.append("OpModel");
      }
      if (name_builder.length() == 0) {
        _name = "Simple";
      } else {
        _name = name_builder.toString();
      }
    }
    return _name;
  }

  public SIGRA(boolean stubborn, double SecondOrderWeight, double OppMeldsWeight) {
    this(stubborn);
    this.SECOND_ORDER_REDUCTION_WEIGHT = SecondOrderWeight;
    this.OPPONENT_MELDS_REDUCTION_WEIGHT = OppMeldsWeight;
  }

  protected SIGRA(boolean stubborn) {
    super(stubborn);
  }

  public SIGRA(boolean stubborn, boolean MMD, boolean OppModel) {
    this(stubborn);
    if (!MMD) {
      this.SECOND_ORDER_REDUCTION_WEIGHT = 0.0;
    }
    if (!OppModel) {
      this.OPPONENT_MELDS_REDUCTION_WEIGHT = 0.0;
    }
  }

  @Override
  public Function<ArrayList<Card>, Double> evaluator(ArrayList<Card> unknowns) {
    return (hand) -> {
      return (double) valueHand(hand, unknowns);
    };
  }

  // Compute the score for a given hand, the lower the better
  double valueHand(ArrayList<Card> hand, ArrayList<Card> unknowns) {
    double value = deadwoodMinusMelds(hand);

    if (this.SECOND_ORDER_REDUCTION_WEIGHT != 0.0) {
      value -= this.SECOND_ORDER_REDUCTION_WEIGHT * approximateSecondOrderReduction(hand, unknowns);
    }

    // Enabled only for opponent modeling
    if (this.OPPONENT_MELDS_REDUCTION_WEIGHT != 0.0) {
      value -=
        this.OPPONENT_MELDS_REDUCTION_WEIGHT
        * computeOpponentHandReduction(
                                       hand,
                                       this.opponent_hand_known,
                                       this.opponent_discarded,
                                       this.discard_pile.stream().collect(Collectors.toCollection(ArrayList::new)),
                                       unknowns);
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
  // the higher the better
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

  ////
  // New opponent modeling
  ////

  ArrayList<ArrayList<Card>> meldsOpponentCouldHave(
                                                    ArrayList<Card> hand,
                                                    ArrayList<Card> opponentHand,
                                                    ArrayList<Card> opponentDiscarded,
                                                    ArrayList<Card> discardPile,
                                                    ArrayList<Card> unknowns) {

    ArrayList<Card> passedOver = new ArrayList<Card>(discardPile);
    passedOver.removeAll(opponentDiscarded);

    ArrayList<Card> possibleCards = new ArrayList<Card>(unknowns);
    possibleCards.addAll(opponentHand);
    possibleCards.addAll(hand);

    return GinRummyUtil.cardsToAllMelds(possibleCards).stream()
      .filter(
              (meld) -> {
                return meld.stream()
                  .map(
                       (c) -> {
                         if (hand.contains(c)) {
                           return 1;
                         } else {
                           return 0;
                         }
                       })
                  .reduce(0, Integer::sum)
                  == 1;
              })
      .filter(
              (meld) -> {
                // Determine if any of the cards in `meld` are also known to be in the opponents hand.
                return !(this.REMOVE_OPPONENT_MELDS_WO_KNOWN_CARD
                         && !meld.stream()
                         .map(
                              (card) -> {
                                return opponentHand.contains(card);
                              })
                         .reduce(false, Boolean::logicalOr));
              })
      .filter(
              (meld) -> {
                return !(this.REMOVE_OPPONENT_OF_A_KIND_DISCARDED
                         && isMeldOfAKindAndContainCardInSet(meld, opponentDiscarded));
              })
      .filter(
              (meld) -> {
                return !(this.REMOVE_OPPONENT_OF_A_KIND_PASSED_OVER
                         && isMeldOfAKindAndContainCardInSet(meld, passedOver));
              })
      .collect(Collectors.toCollection(ArrayList::new));
  }

  static boolean areCardsOfSameRank(ArrayList<Card> meld) {
    if (meld.isEmpty()) {
      return false;
    }

    int rank = meld.get(0).rank;

    return meld.stream()
      .map(
           (c) -> {
             return c.rank == rank;
           })
      .reduce(true, Boolean::logicalAnd);
  }

  boolean isMeldOfAKindAndContainCardInSet(ArrayList<Card> meld, ArrayList<Card> set) {
    return areCardsOfSameRank(meld)
      && set.stream()
      .map(
           (c) -> {
             return meld.contains(c);
           })
      .reduce(false, Boolean::logicalOr);
  }
  // the higher the better!
  double computeOpponentHandReduction(
                                      ArrayList<Card> hand,
                                      ArrayList<Card> opponentHand,
                                      ArrayList<Card> opponentDiscarded,
                                      ArrayList<Card> discardPile,
                                      ArrayList<Card> unknowns) {
    return hand.stream()
      .map(
           (card) -> {
             return meldsOpponentCouldHave(
                                           hand, opponentHand, opponentDiscarded, discardPile, unknowns)
               .stream()
               .filter(
                       (meld) -> {
                         return meld.contains(card);
                       })
               .map(
                    (meld) -> {
                      return probabilityOpponentHasMeld(meld, opponentHand, unknowns)
                        * (double) GinRummyUtil.getDeadwoodPoints(card);
                    })
               .reduce(0.0, Double::sum);
           })
      .reduce(0.0, Double::sum);
  }

  double probabilityOpponentHasMeld(
                                    ArrayList<Card> meld, ArrayList<Card> opponentHand, ArrayList<Card> unknowns) {
    return meld.stream()
      .map(
           (c) -> {
             if (opponentHand.contains(c)) {
               return 1.0;
             } else {
               return (double) 1 / (double) unknowns.size();
             }
           })
      .reduce(
              1.0,
              (a, b) -> {
                return a * b;
              });
  }
}
