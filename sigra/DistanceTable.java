//package sigra;
//
//import ginrummy.Card;
//import ginrummy.GinRummyUtil;
//
//import java.util.ArrayList;
//
//
///*
//    This data structure allows us to store our distance from any given meld in the game.
// */
//public class DistanceTable {
//
//    private SiftAgent player;
//
//    public DistanceTable(SiftAgent player){
//        this.player = player;
//        init_match_distances();
//        init_straight_distances();
//    }
//
//    public void reset_tables(){
//        init_straight_distances();
//        init_match_distances();
//    }
//
//    public void show_straight_tables(){
//        for(int i=0; i<Card.NUM_SUITS; i++){
//            System.out.println("Suit " + Card.suitNames[i]);
//            System.out.printf("\t  | ");
//            for(int j=0; j<SiftAgent.HAND_SIZE; j++){
//                System.out.printf("%s ", j+1);
//            }
//            System.out.println("\n-----------------------------");
////            System.out.println();
//            for(int j=0; j<Card.NUM_RANKS; j++){
//                System.out.printf("\t%s | ", Card.rankNames[j]);
//                for(int k=0; k<SiftAgent.HAND_SIZE; k++){
//                    System.out.printf("%d ", straight_distances[i][j][k]);
//                }
//                System.out.println();
//            }
//        }
//    }
//    public void show_match_tables(){
//        System.out.printf("\n  | ");
//        for(int i=0; i<Card.NUM_SUITS; i++){
//            System.out.printf("%s ", Card.suitNames[i]);
//        }
//        System.out.println("\n-----------------------------");
//        for(int i=0; i<Card.NUM_RANKS; i++){
//            System.out.printf("%s | ", Card.rankNames[i]);
//            for(int j=0; j<Card.NUM_SUITS; j++){
//                System.out.printf("%d ", match_distances[i][j]);
//            }
//            System.out.println();
//        }
//    }
//
//    // For each `suit` x `rank` x `length`, this matrix stores the distance we are from a straight with that
//    // `length` in that `suit` starting with that `rank`, that is, the number of cards we must draw to
//    // complete it. Straights which are impossible because they contain a discarded card are marked with the
//    // sentinel `MELD_IMPOSSIBLE`, and straights which we hold in are hand are marked with zero, conveninetly
//    // aliased `HAVE_THIS_MELD`.
//    private final int[][][] straight_distances = new int[Card.NUM_SUITS][Card.NUM_RANKS][SiftAgent.HAND_SIZE];
//
//    // For each `rank` x `n`, this matrix stores the distance we are from an `n`-of-a-kind match of that
//    // `rank`.
//    //
//    // TODO: figure out some way to represent the number of available cards which complete a given
//    // 3-of-a-kind. If we have `2C` and `2S`, `match_distances[2 - 1][3 - 1]` is 1 as long as either `2H` or
//    // `2D` or both are still in the deck.
//    private final int[][] match_distances = new int[Card.NUM_RANKS][Card.NUM_SUITS];
//
//    // The distance (i.e. number of required cards) to complete a meld which is entierly in our hand is zero.
//    public static final int HAVE_THIS_MELD = 0;
//    // Melds which cannot be completed because they contain cards we've seen discarded are maked with this
//    // sentinel.
//    public static final int MELD_IMPOSSIBLE = -100;
//    // The smallest melds that are actually worth points (or rather, not worth points) have 3 cards.
//    public static final int SMALLEST_MELD = 3;
//
//    // Some straights are impossible because there just aren't that many cards. For example, you can't build a
//    // straight of 11 cards because you only get 10 cards in hand, and you can't build a 5-card straight
//    // starting with a 10 because there isn't a fifth card after the king.
//    private boolean straight_possible(int start_rank, int length) {
//        return ((start_rank + length) < Card.NUM_RANKS) && (length <= SiftAgent.HAND_SIZE);
//    }
//
//    // Before you've seen any cards, you're `n` away from each `n`-of-a-kind.
//    private void init_match_distances() {
//        for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
//            for (int match_size = 0; match_size < Card.NUM_SUITS; match_size++) {
//                match_distances[rank][match_size] = 1;
//            }
//        }
//    }
//
//    // Before you've seen any cards, you're `n` away from each run-of-`n`, except that some of them are
//    // impossible.
//    private void init_straight_distances() {
//        for (int suit = 0; suit < Card.NUM_SUITS; suit++) {
//            for (int rank = 0; rank < Card.NUM_RANKS; rank++) {
//                for (int straight_len = 1; straight_len <= SiftAgent.HAND_SIZE; straight_len++) {
//                    // initialize any impossible straights as such, so we don't try to build them
//                    straight_distances[suit][rank][straight_len - 1]
//                            = straight_possible(rank, straight_len) ? straight_len : MELD_IMPOSSIBLE;
//                }
//            }
//        }
//    }
//
//    // Mark each of the straights containing `card` as being impossible.
//    public void make_unavail_in_straight(Card card) {
//        int my_rank = card.getRank();
//        int suit = card.getSuit();
//        for (int straight_len = 1; straight_len <= SiftAgent.HAND_SIZE; straight_len++) {
//            for (int rank = my_rank;
//                 (rank > my_rank - straight_len) && (rank >= 0);
//                 rank--) {
//                straight_distances[suit][rank][straight_len - 1] = MELD_IMPOSSIBLE;
//            }
//        }
//    }
//
//    // Mark the match requiring `card` as being impossible.
//    //
//    // Note that at any given time, exactly one match depends on a given card: if this is the first card of
//    // that rank, the 4-of-a-kind is now impossible, but the 3-of-a-kind remains available. If this is the
//    // second, the 3-of-a-kind is now impossible, but the 2-of-a-kind is available (although useless).
//    public void make_unavail_in_match(Card card) {
//        int rank = card.getRank();
//        int suit = card.getSuit();
//
//        match_distances[rank][suit] = MELD_IMPOSSIBLE;
//
////        boolean next_possible = false;
////        for (int match_size = Card.NUM_SUITS;
////             (!next_possible) && (match_size > 0);
////             match_size--) {
////            if (match_distances[rank][match_size - 1] != MELD_IMPOSSIBLE) {
////                // If the 4-of-a-kind of `rank` is still possible, mark it impossible. Otherwise, proceed to
////                // the 3-of-a-kind, and so forth.
////                next_possible = true;
////                match_distances[rank][match_size - 1] = MELD_IMPOSSIBLE;
////            }
////        }
//    }
//
//    // Returns the number of cards not in our hand required to make any straight containing `card`.
//    //
//    // See the comment on `distance_from_nearest_meld` for some nuance regarding what that value means.
//    public int distance_from_nearest_straight(Card card) {
//        int min = Integer.MAX_VALUE;
//        int suit = card.getSuit();
//        int my_rank = card.getRank();
//
//        // you only have to check all three of the three-card melds
//        // containing this card, because it will be at least as close
//        // to each of them as to each 4-card meld
//        for (int rank = my_rank;
//             (rank >= 0) && ((my_rank - rank) <= SMALLEST_MELD);
//             rank--) {
//            int this_meld = straight_distances[suit][rank][SMALLEST_MELD - 1];
//            if (this_meld != MELD_IMPOSSIBLE) {
//                min = Integer.min(this_meld, min);
//            }
//        }
//        return min;
//    }
//
//    // Returns the number of cards not in our hand, required to make any match containing `card`.
//    //
//    // See the comment on `distance_from_nearest_meld` for some nuance regarding what that value means.
//    public int distance_from_nearest_match(Card card) {
//        int rank = card.getRank();
//        // you only have to check the 3-card match, because it will be
//        // at least as close as the 4-card meld.
//        int dist = 0;
//        int numImpossible = 0;
//        for(int i=0; i<Card.NUM_SUITS; i++){
//            if (match_distances[rank][i] == MELD_IMPOSSIBLE){
//                numImpossible +=1;
//                if(numImpossible > 1){ // you can still get a 3 of a kind with 1 card marked impossible
//                    return Integer.MAX_VALUE;
//                }
//            } else{
//                dist += match_distances[rank][i];
//            }
//        }
//        if (dist > 0) dist -=1; // for the 3 card match, dist will be -1 if you have 4 of a kind in your hand!
//        return dist; // return the current distance from a meld with the cards in our hand
//    }
//
//    // Returns the number of cards not in our hand required to make a meld containing `card`.
//    //
//    // This means slightly different things depending on whether `card` is already in our hand or not.
//    //
//    // If `card` is already in our hand, then zero means it is part of a complete meld; one means that we
//    // need to draw one card to make the nearest meld, and so forth.
//    //
//    // If `card` is not in our hand, then zero means that we already have a complete meld in our hand to which
//    // `card` can be added; one means that `card` completes a meld in our hand; two means that we would need
//    // another card in addition to `card` to complete a meld, and so forth.
//    public int distance_from_nearest_meld(Card card) {
//        return Integer.min(distance_from_nearest_match(card), distance_from_nearest_straight(card));
//    }
//
//    // True if `card` is part of a meld in our hand.
//    //
//    // This should be called on cards which are already in our hand.
//    public boolean in_meld(Card card) {
//        return distance_from_nearest_meld(card) == 0;
//    }
//
//    // True if `card` completes a meld in our hand.
//    //
//    // This should be called on cards we intend to draw which are not yet in our hand.
//    public boolean makes_a_meld(Card card) {
//        int distance = distance_from_nearest_meld(card);
//        if ((distance == 0) || (distance == 1)) {
//            return true;
//        } else {
//            return false;
//        }
//    }
//
//    void optimize_melds_in_table(){
//        for (Card c: player.my_hand) {
//            System.out.println("does " + c + " have 2 different kinds of melds? " + distance_from_nearest_match(c) + "==" + distance_from_nearest_straight(c) + "?? " + (distance_from_nearest_match(c) == 0 && distance_from_nearest_straight(c) == 0));
//            if (distance_from_nearest_match(c) == 0 && distance_from_nearest_straight(c) == 0) {// card is in an unresolved straight and meld
//                System.out.println(c + " has 2 different kinds of melds, sorting out the situation");
////                ArrayList<ArrayList<ArrayList<Card>>> setOfMelds = GinRummyUtil.cardsToBestMeldSets(player.my_hand);
//                ArrayList<ArrayList<Card>> bestMelds = SiftAgent.get_best_melds(player.my_hand);
//                ArrayList<Card> c_meld = new ArrayList<>();
//                for (ArrayList<Card> meld : bestMelds) {
//                    for (int i=0; i<meld.size(); i++){
//                        if (meld.get(i).toString().equals(c.toString())){
//                            c_meld = meld;
//                        }
//                    }
//                }
//                System.out.println("Best melds: " + bestMelds);
//                System.out.println("Containing meld: " + c_meld);
//                if (SiftAgent.is_straight_meld(c_meld)) { // if the best meld for it to be in is a straight, then remove it from the matches
//                    make_matches_further(c);
//                    System.out.println("putting " + c + " into a straight instead of a match");
//                } else {
//                    make_straights_further(c); // if the best meld is a match, remove it from the straights
//                    System.out.println("putting " + c + " into a match instead of a straight");
//                }
//            }
//        }
//    }
//
//
//
//    // Mark each of the melds containing `card` as being impossible because that card is in the discard pile.
//    public void make_unavail(Card card) {
//        make_unavail_in_straight(card);
//        make_unavail_in_match(card);
//    }
//
//
//    // Reduce the distance of each straight containing `card` by one because we have drawn it.
//    public void make_straights_nearer(Card card) {
//        int suit = card.getSuit();
//        int my_rank = card.getRank();
//        for (int rank = card.getRank(),
//             length_to_reach = 1;
//             rank >= 0;
//             rank--, length_to_reach++) {
//            for (int straight_length = length_to_reach;
//                 ((rank + straight_length) < Card.NUM_RANKS) && (straight_length <= SiftAgent.HAND_SIZE);
//                 straight_length++) {
//                if (straight_distances[suit][rank][straight_length - 1] != MELD_IMPOSSIBLE) {
//                    straight_distances[suit][rank][straight_length - 1]--;
//                }
//            }
//        }
//    }
//
//    // Reduce the distance of each match containing `card` because we have drawn it.
//    public void make_matches_nearer(Card card) {
//        int rank = card.getRank();
//        int suit = card.getSuit();
//        if (match_distances[rank][suit] != MELD_IMPOSSIBLE){
//            match_distances[rank][suit] = match_distances[rank][suit] - 1;
//        }
//
////        for (int match_size = 1;
////             match_size < Card.NUM_SUITS;
////             match_size++) {
////            if (match_distances[rank][match_size - 1] != MELD_IMPOSSIBLE) {
////                match_distances[rank][match_size - 1]--;
////            }
////        }
//    }
//    // Increase the distance of each straight containing `card` by one, used on a card that was in a straight but now in a match
//    public void make_straights_further(Card card) {
//        int suit = card.getSuit();
//        int my_rank = card.getRank();
//        for (int rank = card.getRank(),
//             length_to_reach = 1;
//             rank >= 0;
//             rank--, length_to_reach++) {
//            for (int straight_length = length_to_reach;
//                 ((rank + straight_length) < Card.NUM_RANKS) && (straight_length <= SiftAgent.HAND_SIZE);
//                 straight_length++) {
//                if (straight_distances[suit][rank][straight_length - 1] != MELD_IMPOSSIBLE) {
//                    straight_distances[suit][rank][straight_length - 1]++;
//                }
//            }
//        }
//    }
//
//    // Increase the distance of each match containing `card`, used on a card that was in a match but now in a straight
//    public void make_matches_further(Card card) {
//        int rank = card.getRank();
//        int suit = card.getSuit();
//        if (match_distances[rank][suit] != MELD_IMPOSSIBLE){
//            match_distances[rank][suit] = match_distances[rank][suit] + 1;
//        }
////        for (int match_size = 1;
////             match_size < Card.NUM_SUITS;
////             match_size++) {
////            if (match_distances[rank][match_size - 1] != MELD_IMPOSSIBLE) {
////                match_distances[rank][match_size - 1]++;
////            }
////        }
//    }
//
//    // Reduce the distance of each meld containing `card` because we have drawn it.
//    public void make_nearer(Card card) {
//        make_matches_nearer(card);
//        make_straights_nearer(card);
////        optimize_melds_in_table();
//    }
//    // Increase the distance of each meld containing `card`
//    public void make_further(Card card) {
//        make_matches_further(card);
//        make_straights_further(card);
//    }
//}
