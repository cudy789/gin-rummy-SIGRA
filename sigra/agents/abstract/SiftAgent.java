package sigra.agents;

import ginrummy.Card;
import ginrummy.GinRummyPlayer;
import ginrummy.GinRummyUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class SiftAgent implements GinRummyPlayer {
  static final int HAND_SIZE = 10;

  // Stubborn = Knock only for gin. Otherwise we knock when we drop below 10 deadwood.
  boolean STUBBORN = true;

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
  // cards our opponent explicitly discarded
  ArrayList<Card> opponent_discarded;
  // cards in the discard pile (including the top card after any player discards)
  Stack<Card> discard_pile;

  // list of list of opponent's melds, used when knocking
  ArrayList<ArrayList<Card>> opponent_melds;

  // Card to discard, set in reportDraw.
  Card cardToDiscard;

  // Perhaps a speedup?
  Hashtable<ArrayList<Card>, ArrayList<ArrayList<ArrayList<Card>>>> deadwoodHashTable;
  static int DEADWOOD_HASHTABLE_SIZE = 10_000;

  public SiftAgent(boolean stubborn) {
    this.STUBBORN = stubborn;
  }

  void reset_for_new_hand() {
    my_hand = new ArrayList<Card>();
    opponent_hand_known = new ArrayList<Card>();
    opponent_passed = new ArrayList<Card>();
    opponent_discarded = new ArrayList<Card>();
    opponent_melds = null;
    discard_pile = new Stack<Card>();
    if (deadwoodHashTable == null) {
      deadwoodHashTable =
          new Hashtable<ArrayList<Card>, ArrayList<ArrayList<ArrayList<Card>>>>(
              DEADWOOD_HASHTABLE_SIZE);
    }
    if (deadwoodHashTable.size() > DEADWOOD_HASHTABLE_SIZE) {
      deadwoodHashTable.clear();
    }
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
      opponent_discarded.add(discardedCard);
    }
  }

  @Override
  public ArrayList<ArrayList<Card>> getFinalMelds() {
    if (this.STUBBORN) {
      return this.StubbornFinalMelds();
    } else {
      return this.NotStubbornFinalMelds();
    }
  }

  public ArrayList<ArrayList<Card>> StubbornFinalMelds() {
    // Only knock if we have gin.
    if (this.haveGin() || opponent_melds != null) {
      ArrayList<ArrayList<ArrayList<Card>>> bestMelds = GinRummyUtil.cardsToBestMeldSets(my_hand);
      if (bestMelds.isEmpty()) {
        return new ArrayList<ArrayList<Card>>();
      }
      return bestMelds.get(0);
    }
    return null;
  }

  ArrayList<ArrayList<Card>> NotStubbornFinalMelds() {
    // Check if deadwood of maximal meld is low enough to go out.
    ArrayList<ArrayList<ArrayList<Card>>> bestMeldSets = GinRummyUtil.cardsToBestMeldSets(cards);
    if (!opponentKnocked
        && (bestMeldSets.isEmpty()
            || GinRummyUtil.getDeadwoodPoints(bestMeldSets.get(0), cards)
                > GinRummyUtil.MAX_DEADWOOD)) return null;
    return bestMeldSets.isEmpty()
        ? new ArrayList<ArrayList<Card>>()
        : bestMeldSets.get(random.nextInt(bestMeldSets.size()));
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
    return cardsInMelds(bestMelds.get(0)) == this.HAND_SIZE;
  }

  ArrayList<Card> unknownCards() {
    ArrayList<Card> rv = new ArrayList<Card>(Arrays.asList(Card.allCards));
    rv.removeAll(this.discard_pile);
    rv.removeAll(this.my_hand);
    rv.removeAll(this.opponent_hand_known);
    return rv;
  }

  ArrayList<Card> unknownCardsMinus(ArrayList<Card> cards) {
    ArrayList<Card> rv = this.unknownCards();
    rv.removeAll(cards);
    return rv;
  }

  ArrayList<Card> unknownCardsMinus(Card card) {
    ArrayList<Card> rv = this.unknownCards();
    rv.remove(card);
    return rv;
  }

  Stream<ArrayList<Card>> enumeratePossibleHandsByAddingFrom(ArrayList<Card> cards) {
    return cards.stream() // .parallel()
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

  int deadwoodMinusMelds(ArrayList<Card> hand) {
    ArrayList<ArrayList<ArrayList<Card>>> bestMelds = deadwoodHashTable.get(hand);
    if (bestMelds == null) {
      bestMelds = GinRummyUtil.cardsToBestMeldSets(hand);
      deadwoodHashTable.put(hand, bestMelds);
    }

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

  static int cardsInMelds(ArrayList<ArrayList<Card>> melds) {
    return melds.stream()
        .mapToInt(
            (x) -> {
              return x.size();
            })
        .reduce(
            (a, b) -> {
              return a + b;
            })
        .orElse(0);
  }

  static ArrayList<ArrayList<Card>> getBestMeldsWrapper(ArrayList<Card> cards) {
    ArrayList<ArrayList<ArrayList<Card>>> rv = GinRummyUtil.cardsToBestMeldSets(cards);
    if (rv.isEmpty()) {
      return new ArrayList<ArrayList<Card>>();
    }
    return rv.get(0);
  }

  static ArrayList<Card> flattenMeldSet(ArrayList<ArrayList<Card>> melds) {
    return melds.stream()
        .collect(
            Collectors.reducing(
                new ArrayList<Card>(),
                (x) -> {
                  return x;
                },
                (a, b) -> {
                  ArrayList<Card> rv = new ArrayList<Card>();
                  rv.addAll(a);
                  rv.addAll(b);
                  return rv;
                }));
  }

  static ArrayList<ArrayList<Card>> flattenMeldSets(
      ArrayList<ArrayList<ArrayList<Card>>> meldSets) {
    ArrayList<ArrayList<Card>> rv = new ArrayList<ArrayList<Card>>();
    meldSets.forEach(
        (set) -> {
          rv.addAll(set);
        });
    return rv;
  }

  static boolean willCardMakeOrJoinMeldInHand(ArrayList<Card> hand, Card card) {
    ArrayList<Card> newHand = new ArrayList<Card>(hand);
    newHand.add(card);
    ArrayList<ArrayList<ArrayList<Card>>> n = GinRummyUtil.cardsToBestMeldSets(newHand);
    ArrayList<ArrayList<ArrayList<Card>>> o = GinRummyUtil.cardsToBestMeldSets(hand);
    if (!n.isEmpty() && o.isEmpty()) {
      return true;
    } else if (n.isEmpty() && o.isEmpty()) {
      return false;
    } else if (n.isEmpty() && !o.isEmpty()) {
      System.out.println("Shouldn't get here???");
      return false;
    } else {
      return cardsInMelds(n.get(0)) > cardsInMelds(o.get(0));
    }
  }
}
