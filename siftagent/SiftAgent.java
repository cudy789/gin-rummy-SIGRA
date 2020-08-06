package siftagent;

import java.lang.reflect.Array;
import java.util.ArrayList;

import ginrummy.Card;
import ginrummy.GinRummyPlayer;
import ginrummy.GinRummyUtil;

import java.lang.Integer;
import java.util.Stack;


public class SiftAgent implements GinRummyPlayer {
    static final int HAND_SIZE = 10;

    public boolean i_play_first;
    public int my_number;
    public Card picked_up_discard = null;

    // assuming the cards are in order lowest to highest...
    public static boolean is_straight_meld(ArrayList<Card> l){
        if (l.size() < 3 || l.size() > 10){
            return false;
        }
        for (int i=0; i<l.size(); i++){
            if (i+1 < l.size()){
                if (!(l.get(i).suit == l.get(i+1).suit)){ // transitive property
//                    System.out.println("suits don't match");
                    return false;
                }
                if (!(l.get(i+1).rank == l.get(i).rank + 1)){
//                    System.out.println("not sequential");
                    return false;
                }
            }
        }

        return true;

    }
    public static boolean is_match_meld(ArrayList<Card> l){
        if (l.size() < 3 || l.size() > 4){
            return false;
        }
        for (int i=0; i<l.size(); i++){
            if (i+1 < l.size()){
                if (!(l.get(i).suit == l.get(i+1).suit)){
                    return false;
                }
                if (!(l.get(i).rank == l.get(i+1).rank)){
                    return false;
                }

            }
        }
        return true;
    }

    // cards in our hand
    public ArrayList<Card> my_hand = new ArrayList<Card>(HAND_SIZE);
    // known cards in opponent's hand
    public ArrayList<Card> opponent_hand_known = new ArrayList<Card>(HAND_SIZE);
    // cards that we discarded and our opponent didn't pick up
    public ArrayList<Card> opponent_passed = new ArrayList<Card>(Card.NUM_CARDS);
    // cards in the discard pile (including the top card after any player discards)
    public Stack<Card> discard_pile = new Stack<Card>();

    // list of list of opponent's melds, used when knocking
    public ArrayList<ArrayList<Card>> opponent_melds;

    private final Heuristic my_heuristic = new Heuristic(this);
    public final DistanceTable my_distances = new DistanceTable(this);


    void reset_for_new_hand() {
        my_distances.reset_tables();
//        my_distances.show_straight_tables();
        my_hand.clear();
        opponent_hand_known.clear();
        opponent_passed.clear();
        opponent_melds = null;
        discard_pile.clear();
    }

    int num_remaining_cards() {
        return Card.NUM_CARDS - my_hand.size() - opponent_hand_known.size() - discard_pile.size();
    }



    public double probability_to_draw(Card card) {
        if (discard_pile.contains(card) || opponent_hand_known.contains(card)) {
            return 0.0;
        }
        return 1.0 / (double)num_remaining_cards();
    }
    // TODO sum of distance_from_meld * (1/num_cards--)
    public double probability_to_get_meld(Card card){
        return 0.0;
    }

    // Returns the set of melds in our hand which minimizes deadwood in our hand, ignoring the possibility of
    // laying off cards on our opponent's melds.
    //
    // TODO: consider the possibility of laying off cards on our opponent's melds.
    public static ArrayList<ArrayList<Card>> get_best_melds(ArrayList<Card> hand) {
        ArrayList<ArrayList<Card>> best_set = null;
        int best_deadwood = Integer.MAX_VALUE;
        for (ArrayList<ArrayList<Card>> melds : GinRummyUtil.cardsToBestMeldSets(hand)) {
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
        for (Card card : cards) {
            my_hand.add(card);
            my_distances.make_nearer(card);
        }
//        System.out.println("Starting game with hand " + my_hand);
//        my_distances.show_straight_tables();

//        reset_for_new_hand();
//        my_number = playerNum;
//        my_distances.show_straight_tables();
//        my_distances.show_match_tables();
//
//        ArrayList<Card> cardsToAdd = new ArrayList<Card>();
//
//        cardsToAdd.add(new Card(0, 0));
//        cardsToAdd.add(new Card(1, 0));
//        cardsToAdd.add(new Card(2, 0));
//        cardsToAdd.add(new Card(0, 1));
//        cardsToAdd.add(new Card(0, 2));
//        cardsToAdd.add(new Card(1, 3));
//        cardsToAdd.add(new Card(10, 0));
//        cardsToAdd.add(new Card(7, 2));
//        cardsToAdd.add(new Card(3, 0));
//        cardsToAdd.add(new Card(4, 1));
//
//        System.out.println("will be adding these cards to my hand: " + cardsToAdd);
//
//        for (Card c: cardsToAdd){
//            System.out.println("adding " + c + " to my hand, here's the straight tables: ");
//            my_hand.add(c);
//            my_distances.make_nearer(c);
//            my_distances.show_straight_tables();
//            my_distances.show_match_tables();
//        }
//
//        System.out.println("My beginning hand: " + my_hand);
//        my_distances.show_straight_tables();
//        my_distances.show_match_tables();
    }
    // Confusingly, this method is also used to report the initial face-up card at the start of a game.
    @Override
    public boolean willDrawFaceUpCard(Card card) {
//        discard_pile.push(card);
        if (discard_pile.size() == 0) { // this is the initial face-up card
            discard_pile.push(card);
            if (!i_play_first) { // and we go second, so this card is gone forever
                my_distances.make_unavail(card);
                return false;
            }
        }
        if (my_heuristic.faceupValue() > my_heuristic.facedownValue()){
            picked_up_discard = card;
            return true;
        } else{
            my_distances.make_unavail(card);
            return false;
        }

    }

    // if the drawn card is the top of the discard, remove it. we'll add it to the appropriate hand later
    void undiscard(Card card) {
        if ((discard_pile.size() > 0) && (card.equals(discard_pile.peek()))){
            discard_pile.pop();
        }
    }

    @Override
    public void reportDraw(int playerNum, Card drawnCard) {
        if (drawnCard != null) {
            undiscard(drawnCard); // will remove this card from the discard if it's there
        }

        if (playerNum == my_number) {
//            System.out.println("My hand: " + my_hand);
//            System.out.println("Drew " + drawnCard);
            my_distances.make_nearer(drawnCard);
//            my_distances.show_straight_tables();
            my_hand.add(drawnCard);
        } else if (drawnCard != null) {
            opponent_hand_known.add(drawnCard);
//            make_unavail(drawnCard); // TODO we don't know if we'll ever see this card again... we should decrement instead of making unavail probably. also we need to keep track of opponents melds in another data structure
        } else if (discard_pile.size() > 0) {
            opponent_passed.add(drawnCard);
        }
    }

    // Discard the card from our hand which is not part of a meld and worth the most deadwood points.
    //
    @Override
    public Card getDiscard() {
//        Card to_discard = null;
//        int max_deadwood = Integer.MIN_VALUE;
//        for (Card card : my_hand) {
//            if (in_meld(card)) {
//                continue;
//            }
//            int this_deadwood = GinRummyUtil.getDeadwoodPoints(card);
//            if (this_deadwood > max_deadwood) {
//                to_discard = card;
//                max_deadwood = this_deadwood;
//            }
//        }
//        my_hand.remove(to_discard);
        Card discard = my_heuristic.leastImportantCard();
        my_hand.remove(discard);
        return discard;
    }
    @Override
    public void reportDiscard(int playerNum, Card discardedCard) {
        discard_pile.push(discardedCard);

        if (playerNum == my_number) {
            // cards we discard are gone for good; we can never draw them.
//            System.out.println("Discarded " + discardedCard);
            my_distances.make_unavail(discardedCard);
        } else {
            // try to remove card from opponent's known hand, since they no longer have it
            opponent_hand_known.remove(discardedCard);
            opponent_passed.add(discardedCard);
        }
    }
    @Override
    public ArrayList<ArrayList<Card>> getFinalMelds() {
        ArrayList<ArrayList<ArrayList<Card>>> melds = GinRummyUtil.cardsToBestMeldSets(my_hand);
        ArrayList<ArrayList<Card>> best_melds;
        if (melds.size() > 0){
            best_melds = melds.get(0);
        } else{
            best_melds = new ArrayList<ArrayList<Card>>();
        }
        if (opponent_melds != null) {
            // opponent has knocked, so we need to report our melds
            return best_melds;
        } else if (my_heuristic.knock()) {
            // we have gin! go out
            return best_melds;
        } else {
            return null;
        }
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
