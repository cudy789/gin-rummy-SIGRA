package siftagent;

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

//    static int bitstring_id(long card) {
//        return Long.numberOfTrailingZeros(card);
//    }
    
//    static Card bitstring_card(long card) {
//        return Card.getCard(bitstring_id(card));
//    }

//    static long card_bitstring(Card card) {
//        return 1 << card.getId();
//    }

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

//    void make_unavail(long card) {
//        Card boxed = bitstring_card(card);
//        make_unavail_in_straight(boxed);
//    }

//    static final long UNKNOWN_CARD = -1;

    int my_score;
    int opponent_score;

    boolean i_play_first;
    int my_number;
//    long my_hand;
//    long opponent_hand_known;
//    long opponent_passed;
//    long discard;
//    long top_discard = UNKNOWN_CARD;

    // cards in our hand
    ArrayList<Card> my_hand;
    // known cards in opponent's hand
    ArrayList<Card> opponent_hand_known;
    // cards that we discarded and our opponent didn't pick up
    ArrayList<Card> opponent_passed;
    // cards in the discard pile (including the top card after any player discards)
    Stack<Card> discard_pile;

//    static final long FULL_DECK = (1 << Card.NUM_CARDS) - 1;
    
//    ArrayList<Long> opponent_melds;

    // list of list of opponent's melds, used when knocking
    ArrayList<ArrayList<Card>> opponent_melds;

    void reset_for_new_hand() {
        init_straight_avail();
        my_hand = new ArrayList<Card>();
        opponent_hand_known = new ArrayList<Card>();
        opponent_passed = new ArrayList<Card>();
        opponent_melds = null;
        discard_pile = new Stack<Card>();
    }

    ArrayList<Card> remaining_cards() {
        ArrayList<Card> rem_cards = new ArrayList<Card>();
        for (Card c : Card.allCards){
            if (!my_hand.contains(c) && !opponent_hand_known.contains(c) && !discard_pile.contains(c)){
                rem_cards.add(c);
            }
        }
        return rem_cards;
    }

    double probability_to_draw(long card) {
        return 1.0 / (double)remaining_cards().size();
    }

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
        discard_pile.push(card);
        // TODO: uhhhh... never draw the faceup card
        return false;
    }
    @Override
    public void reportDraw(int playerNum, Card drawnCard) {
        // we pushed the faceup card onto the discard_pile in wilLDrawFaceUpCard
        if(discard_pile.size() > 0 && drawnCard != null && drawnCard.equals(discard_pile.peek())){ // if the drawn card is the top of the discard, remove it. we'll add it to the appropriate hand later
            discard_pile.pop();
        } else if (playerNum != my_number){ // it's not our turn and our opponent passed on the top card
            opponent_passed.add(drawnCard);
        }
        if (playerNum == my_number) {
            my_hand.add(drawnCard);
        } else {
            opponent_hand_known.add(drawnCard);
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
        if (playerNum != my_number){
            opponent_hand_known.remove(discardedCard); // try to remove card from opponent's known hand
        }
    }
    @Override
    public ArrayList<ArrayList<Card>> getFinalMelds() {
        if (opponent_melds != null) { // we never initiate a knock, so we only report melds if the other player knocks
            ArrayList<ArrayList<ArrayList<Card>>> best_melds = GinRummyUtil.cardsToBestMeldSets(my_hand);
            if (best_melds.size() == 0){
                return new ArrayList<ArrayList<Card>>();
            }
            return best_melds.get(0); // the simpleginrummyplayer grabs a random set of melds from here, does it matter which one we choose???
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
