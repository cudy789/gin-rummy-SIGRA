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
    int[][][] straight_distances = new int[Card.NUM_SUITS][Card.NUM_RANKS][HAND_SIZE];
    int[][] match_distances = new int[Card.NUM_RANKS][Card.NUM_SUITS];

    static final int HAVE_THIS_MELD = 0;
    static final int MELD_IMPOSSIBLE = -1;
    static final int SMALLEST_MELD = 3;

    void init_match_distances() {
        for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
            for (int match_size = 1; match_size < Card.NUM_SUITS; match_size++) {
                match_distances[rank][match_size - 1] = match_size;
            }
        }
    }

    void init_straight_distances() {
        for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
            for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                for (int straight_len = 1; straight_len <= HAND_SIZE; straight_len++) {
                    straight_distances[suit][rank][straight_len - 1] = straight_len;
                }
            }
        }
    }

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

    void make_unavail_in_match(Card card) {
        int rank = card.getRank();
        boolean next_possible = false;
        for (int match_size = Card.NUM_SUITS;
             (!next_possible) && (match_size > 0);
             match_size--) {
            if (match_distances[rank][match_size - 1] != MELD_IMPOSSIBLE) {
                next_possible = true;
                match_distances[rank][match_size - 1] = MELD_IMPOSSIBLE;
            }
        }
    }

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

    boolean makes_a_meld(Card card) {
        int distance = Integer.min(distance_from_nearest_match(card), distance_from_nearest_straight(card));
        if ((distance == 0) || (distance == 1)) {
            return true;
        } else {
            return false;
        }
    }

    void make_unavail(Card card) {
        make_unavail_in_straight(card);
        make_unavail_in_match(card);
    }

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

    void make_nearer(Card card) {
        make_matches_nearer(card);
        make_straights_nearer(card);
    }

    int my_score;
    int opponent_score;

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

    double probability_to_draw(long card) {
        return 1.0 / (double)num_remaining_cards();
    }

    // returns the set of melds in our hand which minimizes deadwood in our hand.
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
    @Override
    public boolean willDrawFaceUpCard(Card card) {
        if (discard_pile.size() == 0) {
            discard_pile.push(card);
        }
        return makes_a_meld(card);
    }

    // if the drawn card is the top of the discard, remove it. we'll
    // add it to the appropriate hand later
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
        make_unavail(discardedCard);
        discard_pile.push(discardedCard);
        if (playerNum != my_number){
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
    @Override
    public void reportScores(int[] scores) {
        my_score += scores[my_number];
        opponent_score += scores[(my_number + 1) % 2];
    }
    @Override
    public void reportLayoff(int playerNum, Card layoffCard, ArrayList<Card> opponentMeld) {}
    @Override
    public void reportFinalHand(int playerNum, ArrayList<Card> hand) {}
}
