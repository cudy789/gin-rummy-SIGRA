package siftagent;

import java.lang.reflect.Array;
import java.util.ArrayList;

import ginrummy.Card;
import ginrummy.GinRummyPlayer;
import ginrummy.GinRummyUtil;
import java.lang.Long;
import java.lang.Integer;
import java.util.Arrays;
import java.util.Stack;
import java.util.ArrayList;

public class SiftAgent implements GinRummyPlayer {
    static final int HAND_SIZE = 10;

    // For each `suit` x `rank` x `length`, this matrix stores the distance we are from a straight with that
    // `length` in that `suit` starting with that `rank`, that is, the number of cards we must draw to
    // complete it. Straights which are impossible because they contain a discarded card are marked with the
    // sentinel `MELD_IMPOSSIBLE`, and straights which we hold in are hand are marked with zero, conveninetly
    // aliased `HAVE_THIS_MELD`.
    int[][][] straight_distances = new int[Card.NUM_SUITS][Card.NUM_RANKS][HAND_SIZE];

    // For each `rank` x `n`, this matrix stores the distance we are from an `n`-of-a-kind match of that
    // `rank`.
    //
    // TODO: figure out some way to represent the number of available cards which complete a given
    // 3-of-a-kind. If we have `2C` and `2S`, `match_distances[2 - 1][3 - 1]` is 1 as long as either `2H` or
    // `2D` or both are still in the deck.
    int[][] match_distances = new int[Card.NUM_RANKS][Card.NUM_SUITS];

    // The distance (i.e. number of required cards) to complete a meld which is entierly in our hand is zero.
    static final int HAVE_THIS_MELD = 0;
    // Melds which cannot be completed because they contain cards we've seen discarded are maked with this
    // sentinel.
    static final int MELD_IMPOSSIBLE = -1;
    // The smallest melds that are actually worth points (or rather, not worth points) have 3 cards.
    static final int SMALLEST_MELD = 3;

    // Some straights are impossible because there just aren't that many cards. For example, you can't build a
    // straight of 11 cards because you only get 10 cards in hand, and you can't build a 5-card straight
    // starting with a 10 because there isn't a fifth card after the king.
    boolean straight_possible(int start_rank, int length) {
        return ((start_rank + length) < Card.NUM_RANKS) && (length <= HAND_SIZE);
    }

    // Before you've seen any cards, you're `n` away from each `n`-of-a-kind.
    void init_match_distances() {
        for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
            for (int match_size = 1; match_size < Card.NUM_SUITS; match_size++) {
                match_distances[rank][match_size - 1] = match_size;
            }
        }
    }

    // Before you've seen any cards, you're `n` away from each run-of-`n`, except that some of them are
    // impossible.
    void init_straight_distances() {
        for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
            for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                for (int straight_len = 1; straight_len <= HAND_SIZE; straight_len++) {
                    // initialize any impossible straights as such, so we don't try to build them
                    straight_distances[suit][rank][straight_len - 1]
                        = straight_possible(rank, straight_len) ? straight_len : MELD_IMPOSSIBLE;
                }
            }
        }
    }

    // Mark each of the straights containing `card` as being impossible.
    void make_unavail_in_straight(Card card) {
        int my_rank = card.getRank();
        int suit = card.getSuit();
        for (int straight_len = 1; straight_len <= HAND_SIZE; straight_len++) {
            for (int rank = my_rank;
                 (rank > my_rank - straight_len) && (rank >= 0);
                 rank--) {
                straight_distances[suit][rank][straight_len - 1] = MELD_IMPOSSIBLE;
            }
        }
    }

    // Mark the matche requiring `card` as being impossible.
    //
    // Note that at any given time, exactly one match depends on a given card: if this is the first card of
    // that rank, the 4-of-a-kind is now impossible, but the 3-of-a-kind remains available. If this is the
    // second, the 3-of-a-kind is now impossible, but the 2-of-a-kind is available (although useless).
    void make_unavail_in_match(Card card) {
        int rank = card.getRank();
        boolean next_possible = false;
        for (int match_size = Card.NUM_SUITS;
             (!next_possible) && (match_size > 0);
             match_size--) {
            if (match_distances[rank][match_size - 1] != MELD_IMPOSSIBLE) {
                // If the 4-of-a-kind of `rank` is still possible, mark it impossible. Otherwise, proceed to
                // the 3-of-a-kind, and so forth.
                next_possible = true;
                match_distances[rank][match_size - 1] = MELD_IMPOSSIBLE;
            }
        }
    }

    // Returns the number of cards not in our hand required to make any straight containing `card`.
    //
    // See the comment on `distance_from_nearest_meld` for some nuance regarding what that value means.
    int distance_from_nearest_straight(Card card) {
        int min = Integer.MAX_VALUE;
        int suit = card.getSuit();
        int my_rank = card.getRank();

        // you only have to check all three of the three-card melds
        // containing this card, because it will be at least as close
        // to each of them as to each 4-card meld
        for (int rank = my_rank;
             (rank > 0) && ((my_rank - rank) <= SMALLEST_MELD);
             rank--) {
            int this_meld = straight_distances[suit][rank][SMALLEST_MELD - 1];
            if (this_meld != MELD_IMPOSSIBLE) {
                min = Integer.min(this_meld, min);
            }
        }
        return min;
    }

    // Returns the number of cards not in our hand, required to make any match containing `card`.
    //
    // See the comment on `distance_from_nearest_meld` for some nuance regarding what that value means.
    int distance_from_nearest_match(Card card) {
        int rank = card.getRank();
        // you only have to check the 3-card match, because it will be
        // at least as close as the 4-card meld.
        int this_meld = match_distances[rank][SMALLEST_MELD - 1];
        if (this_meld == MELD_IMPOSSIBLE) {
            return Integer.MAX_VALUE;
        } else {
            return this_meld;
        }
    }

    // Returns the number of cards not in our hand required to make a meld containing `card`.
    //
    // This means slightly different things depending on whether `card` is already in our hand or not.
    //
    // If `card` is already in our hand, then zero means it is part of a complete mmeld; one means that we
    // need to draw one card to make the nearest meld, and so forth.
    //
    // If `card` is not in our hand, then zero means that we already have a complete meld in our hand to which
    // `card` can be added; one means that `card` completes a meld in our hand; two means that we would need
    // another card in addition to `card` to complete a meld, and so forth.
    int distance_from_nearest_meld(Card card) {
        return Integer.min(distance_from_nearest_match(card), distance_from_nearest_straight(card));
    }

    // True if `card` completes a meld in our hand.
    //
    // This should be called on cards we intend to draw which are not yet in our hand.
    boolean makes_a_meld(Card card) {
        int distance = distance_from_nearest_meld(card);
        if ((distance == 0) || (distance == 1)) {
            return true;
        } else {
            return false;
        }
    }

    // Mark each of the melds containing `card` as being impossible because that card is in the discard pile.
    void make_unavail(Card card) {
        make_unavail_in_straight(card);
        make_unavail_in_match(card);
    }

    // Reduce the distance of each straight containing `card` by one because we have drawn it.
    void make_straights_nearer(Card card) {
        int suit = card.getSuit();
        int my_rank = card.getRank();
        for (int rank = card.getRank(),
                 length_to_reach = 1;
             rank > 0;
             rank--, length_to_reach++) {
            for (int straight_length = length_to_reach;
                 ((rank + straight_length) < Card.NUM_RANKS) && (straight_length <= HAND_SIZE);
                 straight_length++) {
                if (straight_distances[suit][rank][straight_length - 1] != MELD_IMPOSSIBLE) {
                    straight_distances[suit][rank][straight_length - 1]--;
                }
            }
        }
    }

    // Reduce the distance of each match containing `card` because we have drawn it.
    void make_matches_nearer(Card card) {
        int rank = card.getRank();
        for (int match_size = 1;
             match_size < Card.NUM_SUITS;
             match_size++) {
            if (match_distances[rank][match_size - 1] != MELD_IMPOSSIBLE) {
                match_distances[rank][match_size - 1]--;
            }
        }
    }

    // Reduce the distance of each meld containing `card` because we have drawn it.
    void make_nearer(Card card) {
        make_matches_nearer(card);
        make_straights_nearer(card);
    }

    boolean i_play_first;
    int my_number;

    // cards in our hand
    ArrayList<Card> my_hand = new ArrayList<Card>(HAND_SIZE);
    // known cards in opponent's hand
    ArrayList<Card> opponent_hand_known = new ArrayList<Card>(HAND_SIZE);
    // cards that we discarded and our opponent didn't pick up
    ArrayList<Card> opponent_passed = new ArrayList<Card>(Card.NUM_CARDS);
    // cards in the discard pile (including the top card after any player discards)
    Stack<Card> discard_pile = new Stack<Card>();

    // list of list of opponent's melds, used when knocking
    ArrayList<ArrayList<Card>> opponent_melds;

    void reset_for_new_hand() {
        init_straight_distances();
        init_match_distances();
        my_hand.clear();
        opponent_hand_known.clear();
        opponent_passed.clear();
        opponent_melds = null;
        discard_pile.clear();
    }

    int num_remaining_cards() {
        return Card.NUM_CARDS - my_hand.size() - opponent_hand_known.size() - discard_pile.size();
    }

    double probability_to_draw(Card card) {
        if (discard_pile.contains(card) || opponent_hand_known.contains(card)) {
            return 0.0;
        }
        return 1.0 / (double)num_remaining_cards();
    }

    // Returns the set of melds in our hand which minimizes deadwood in our hand, ignoring the possibility of
    // laying off cards on our opponent's melds.
    //
    // TODO: consider the possibility of laying off cards on our opponent's melds.
    static ArrayList<ArrayList<Card>> best_melds(ArrayList<Card> hand) {
        ArrayList<ArrayList<Card>> best_set = null;
        int best_deadwood = Integer.MAX_VALUE;
        for (ArrayList<ArrayList<Card>> melds : GinRummyUtil.cardsToAllMaximalMeldSets(hand)) {
            int new_deadwood = GinRummyUtil.getDeadwoodPoints(melds, hand);
            if (new_deadwood < best_deadwood) {
                best_deadwood = new_deadwood;
                best_set = melds;
            }
        }
        return best_set;
    }

    // despite being called `startGame`, this function is called at the start of each new hand.
    @Override
    public void startGame(int playerNum, int startingPlayerNum, Card[] cards) {
        reset_for_new_hand();
        my_number = playerNum;
        i_play_first = my_number == startingPlayerNum;
        my_hand = new ArrayList<Card>(Arrays.asList(cards));
    }
    // Confusingly, this method is also used to report the initial face-up card at the start of a game.
    @Override
    public boolean willDrawFaceUpCard(Card card) {
        if (discard_pile.size() == 0) { // this is the initial face-up card
            discard_pile.push(card);
            if (!i_play_first) { // and we go second, so this card is gone forever
                make_unavail(card);
                return false;
            }
        }
        boolean will_draw = makes_a_meld(card);
        if (!will_draw) {
            // if we don't draw this card, it's gone for good.
            make_unavail(card);
        }
        return will_draw;
    }

    // if the drawn card is the top of the discard, remove it. we'll add it to the appropriate hand later
    void undiscard(Card card) {
        if ((discard_pile.size() > 0)
            && (card == discard_pile.peek())){
            discard_pile.pop();
        }
    }

    @Override
    public void reportDraw(int playerNum, Card drawnCard) {
        if (drawnCard != null) {
            undiscard(drawnCard);
        }

        if (playerNum == my_number) {
            make_nearer(drawnCard);
            my_hand.add(drawnCard);
        } else if (drawnCard != null) {
            opponent_hand_known.add(drawnCard);
        } else if (discard_pile.size() > 0) {
            opponent_passed.add(discard_pile.peek());
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

        if (playerNum == my_number) {
            // cards we discard are gone for good; we can never draw them.
            make_unavail(discardedCard);
        } else {
            // try to remove card from opponent's known hand, since they no longer have it
            opponent_hand_known.remove(discardedCard);
            opponent_passed.add(discardedCard);
        }
    }
    @Override
    public ArrayList<ArrayList<Card>> getFinalMelds() {
         // we never initiate a knock, so we only report melds if the other player knocks
        if (opponent_melds != null) {
            ArrayList<ArrayList<Card>> best_melds = best_melds(my_hand);
            if (best_melds == null){
                return new ArrayList<ArrayList<Card>>();
            }
            return best_melds;
        }
        return null;
    }
    @Override
    public void reportFinalMelds(int playerNum, ArrayList<ArrayList<Card>> melds) {
        if (playerNum != my_number) {
            opponent_melds = melds;
        }
    }

    // There isn't actually any useful way to use the information reported by this method, as we have no way
    // of knowing whether a hand is part of a new game or not, that is, whether points carry over between
    // hands.
    @Override
    public void reportScores(int[] scores) {}
    @Override
    public void reportLayoff(int playerNum, Card layoffCard, ArrayList<Card> opponentMeld) {}
    @Override
    public void reportFinalHand(int playerNum, ArrayList<Card> hand) {}
}
