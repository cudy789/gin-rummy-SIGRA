package siftagent;

import java.util.ArrayList;

import ginrummy.Card;
import ginrummy.GinRummyPlayer;
import ginrummy.GinRummyUtil;
import java.lang.Long;
import java.lang.Integer;

public class SiftAgent implements GinRummyPlayer {
    static final int HAND_SIZE = 10;
    int[][][] straight_cards_avail = new int[Card.NUM_SUITS][Card.NUM_RANKS][HAND_SIZE];

    static final int HAVE_THIS_MELD = 0;
    static final int MELD_IMPOSSIBLE = -1;

    boolean straight_possible(int suit, int start_rank, int length) {
        return ((start_rank + length) < Card.NUM_RANKS) && (length <= HAND_SIZE);
    }

    void init_straight_avail() {
        for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
            for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
                for (int straight_len = 1; straight_len <= HAND_SIZE; straight_len++) {
                    if (straight_possible(suit, rank, straight_len)) {
                        straight_cards_avail[suit][rank][straight_len - 1] = straight_len;
                    } else {
                        straight_cards_avail[suit][rank][straight_len - 1] = MELD_IMPOSSIBLE;
                    }
                }
            }
        }
    }

    static int bitstring_id(long card) {
        return Long.numberOfTrailingZeros(card);
    }
    
    static Card bitstring_card(long card) {
        return Card.getCard(bitstring_id(card));
    }

    static long card_bitstring(Card card) {
        return 1 << card.getId();
    }

    void make_unavail_in_straight(Card card) {
        int suit = card.getSuit();
        int rank = card.getRank();
        for (int straight_len = 1; straight_len <= HAND_SIZE; straight_len++) {
            straight_cards_avail[suit][rank][straight_len - 1] = MELD_IMPOSSIBLE;
            
            for (int other_rank = rank - straight_len;
                 straight_possible(suit, other_rank, straight_len)
                     && (other_rank < rank);
                 other_rank++) {
                straight_cards_avail[suit][rank][straight_len - 1] = MELD_IMPOSSIBLE;
            }
        }
    }

    void make_unavail(long card) {
        Card boxed = bitstring_card(card);
        make_unavail_in_straight(boxed);
    }

    static final long UNKNOWN_CARD = -1;

    int my_score;
    int opponent_score;

    boolean i_play_first;
    int my_number;
    long my_hand;
    long opponent_hand_known;
    long opponent_passed;
    long discard;
    long top_discard = UNKNOWN_CARD;

    static final long FULL_DECK = (1 << Card.NUM_CARDS) - 1;
    
    ArrayList<Long> opponent_melds;

    void reset_for_new_hand() {
        init_straight_avail();
        my_hand = 0;
        opponent_hand_known = 0;
        opponent_passed = 0;
        opponent_melds = null;
        top_discard = UNKNOWN_CARD;
        discard = 0;
    }

    long remaining_cards() {
        return FULL_DECK ^ (my_hand | opponent_hand_known | discard | top_discard);
    }

    double probability_to_draw(long card) {
        long ct = Long.bitCount(remaining_cards());
        return 1.0 / (double)ct;
    }

    static ArrayList<Long> best_melds(long hand) {
        ArrayList<ArrayList<Card>> best_set = null;
        int best_deadwood = Integer.MAX_VALUE;
        ArrayList<Card> hand_arr = GinRummyUtil.bitstringToCards(hand);
        for (ArrayList<ArrayList<Card>> melds : GinRummyUtil.cardsToAllMaximalMeldSets(hand_arr)) {
            int new_deadwood = GinRummyUtil.getDeadwoodPoints(melds, hand_arr);
            if (new_deadwood < best_deadwood) {
                best_deadwood = new_deadwood;
                best_set = melds;
            }
        }
        if (best_set != null) {
            ArrayList<Long> melds = new ArrayList<Long>(best_set.size());
            for (ArrayList<Card> meld : best_set) {
                melds.add(Long.valueOf(GinRummyUtil.cardsToBitstring(meld)));
            }
            return melds;
        } else {
            return null;
        }
    }

    // despite being called `startGame`, this function is called at the start of each new hand.
    @Override
    public void startGame(int playerNum, int startingPlayerNum, Card[] cards) {
        reset_for_new_hand();
        my_number = playerNum;
        i_play_first = my_number == startingPlayerNum;
        my_hand = 0;
        for (Card card : cards) {
            my_hand |= card_bitstring(card);
        }
    }
    @Override
    public boolean willDrawFaceUpCard(Card card) {
        top_discard = card_bitstring(card);
        // TODO: uhhhh...
        return false;
    }
    @Override
    public void reportDraw(int playerNum, Card drawnCard) {
        if (playerNum == my_number) {
            long drawn_card = card_bitstring(drawnCard);
            if (drawn_card == top_discard) {
                top_discard = UNKNOWN_CARD;
            }
            my_hand |= drawn_card;
        } else if (drawnCard != null) {
            long drawn_card = card_bitstring(drawnCard);
            if (drawn_card == top_discard) {
                top_discard = UNKNOWN_CARD;
                opponent_hand_known |= drawn_card;
            }
        } else {
            assert top_discard != UNKNOWN_CARD;
            opponent_passed |= top_discard;
        }
    }
    @Override
    public Card getDiscard() {
        // TODO: uhhhhh...
        long lowest_card = Long.lowestOneBit(my_hand);
        return bitstring_card(lowest_card);
    }
    @Override
    public void reportDiscard(int playerNum, Card discardedCard) {
        long card = card_bitstring(discardedCard);
        if (playerNum == my_number) {
            my_hand ^= card;
        } else {
            opponent_hand_known ^= card;
        }
        discard ^= card;
    }
    @Override
    public ArrayList<ArrayList<Card>> getFinalMelds() {
        if (opponent_melds != null) {
            ArrayList<Long> meld_ints = best_melds(my_hand);
            if (meld_ints == null) { return null ;}
            ArrayList<ArrayList<Card>> melds = new ArrayList<ArrayList<Card>>(meld_ints.size());
            for (Long meld : meld_ints) {
                melds.add(GinRummyUtil.bitstringToCards(meld));
            }
            return melds;
        }
        return null;
    }
    @Override
    public void reportFinalMelds(int playerNum, ArrayList<ArrayList<Card>> melds) {
        if (playerNum != my_number) {
            opponent_melds = new ArrayList<Long>(melds.size());
            for (ArrayList<Card> meld : melds) {
                opponent_melds.add(Long.valueOf(GinRummyUtil.cardsToBitstring(meld)));
            }
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
