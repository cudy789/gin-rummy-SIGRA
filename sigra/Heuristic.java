// package sigra;
//
// import ginrummy.Card;
// import ginrummy.GinRummyUtil;
//
// import java.util.ArrayList;
// import java.util.HashMap;
//
/// *
//    This class assigns a quantitative value on the 4 different choices we have to make
//    in a gin rummy game.  The agent should use these heuristics to decide between drawing
//    faceup vs. facedown, which card to discard, and when to knock.
//
// */
//
// public class Heuristic {
//
//    private final SiftAgent player;
//    public static double MAXVALUE = 1;
//    public static double MINVALUE = 0;
//    public static int MAXSINGLECARDDEADWOODREDUCTION = 9;
//
//    public Heuristic(SiftAgent player){
//        this.player = player;
//    }
//    /*
//    Factors in deciding when to draw the faceup card:
//        1. Does this card go into a current meld?
//        2. Does it make a new meld?
//        3. We assume this card does not go into an opponents meld (aka they're not bluffing)
//        4. P(this card would make a new meld later)
//        5. Difference in deadwood compared to the least important card
//     Returns a value between MINVALUE-MAXVALUE inclusive, with 0 being do not draw faceup and 1
// being do draw faceup.
//     */
//    public double faceupValue(){
//        double retValue = MINVALUE;
//        // Maximum value a single faceup card could reduce our deadwood by is 20pts
//        Card c = this.player.discard_pile.peek();
//        ArrayList<Card> currentHand = new ArrayList<Card>();
//        for(int i=0; i<SiftAgent.HAND_SIZE; i++){
//            currentHand.add(player.my_hand.get(i));
//        }
////        System.out.println("My hand in heuristic land: " + currentHand);
//        int dist = this.player.my_distances.distance_from_nearest_meld(c);
////        System.out.println("faceup " + c + " distance from nearest meld: " + dist);
//        if (dist == 0){ // 1. faceup card goes into a current new meld, always take it
//            // doesn't matter if drawing from the facedown cards is a good option, we are
// guaranteed to reduce deadwood
//            // even if it doesn't make a new meld and get rid of a lot of points, that's okay
////            System.out.println("faceup card goes into current meld");
//            return MAXVALUE;
//        } else if (dist == 1){ // 2. faceup makes a new meld, always take it
//            // we remove the deadwood from at least 2 cards in our hand, even if it's not a lot of
// points
////            System.out.println("faceup card makes a new meld");
//            return MAXVALUE;
//        }
//        // Now for the actual qualitative to quantitative heuristic magic
//
//        int origPoints = GinRummyUtil.getDeadwoodPoints(currentHand);
//        currentHand.add(c);
//        currentHand.remove(leastImportantCard());
//        int newPoints = GinRummyUtil.getDeadwoodPoints(currentHand);
//        int diff = origPoints - newPoints;
////        System.out.println("diff in heuristic land: " + diff);
//
//        double diffWeight = MINVALUE; // 40% of our total weight
//        double lowCardWeight = MINVALUE; // 20% of our total weight
//        double newMeldLaterWeight = MINVALUE; // 40% of our total weight
//
//        diffWeight = .4 * ((double)(diff + MAXSINGLECARDDEADWOODREDUCTION) / (2 *
// MAXSINGLECARDDEADWOODREDUCTION)); // This formula takes 40% of a value between 0/18->18/18. The
// larger the number, the larger the reduction in deadwood
//        lowCardWeight = .2 * ((double)1/GinRummyUtil.getDeadwoodPoints(c)); // the lower the card,
// the better (even if it doesn't reduce deadwood by much)
//        if (dist == 2){ // only consider this if adding this card would put us at most 1 card away
// from a meld
//            newMeldLaterWeight = .4 * ((double)1/GinRummyUtil.getDeadwoodPoints(c)); // favor
// cards with lower value
//        }
//
//
//        retValue = diffWeight + lowCardWeight + newMeldLaterWeight;
////        System.out.println("Pick faceup " + c + " heuristic: " + retValue);
////        System.out.println("\tdeadwood reduction: " + diffWeight + "\n" +
////                "\tlow card: " + lowCardWeight + "\n" +
////                "\tnew meld later: " + newMeldLaterWeight);
//        return retValue;
//
//    }
//    /*
//    Factors in deciding when to draw the facedown card:
//        * P(card will go into a current meld)
//        * P(card will make a new meld)
//        * P(card will make a new meld later)
//        * P(card will go into an opponent's meld)
//        * P(card will reduce overall deadwood)
//        * Avg expected value of this card
//     Returns a value between MINVALUE-MAXVALUE inclusive, with 0 being do not draw facedown and 1
// being do draw facedown.
//     */
//    public double facedownValue(){
//
//        return .5;
//    }
//    /*
//    Helper for hand importance. Actually calculates the deadwood value of a card in our hand.
//    Factors in deciding the importance of cards:
//        * Does this card go into a current meld?
//        * Did we just draw this card from the discard (faceup) pile?
//        * P(card will make a new meld later)
//        * P(card will go into an opponents meld)
//        * Deadwood value
//     */
//    private double _cardValue(Card c){
//        double retValue = MINVALUE;
//
//        int dist = this.player.my_distances.distance_from_nearest_meld(c);
////        System.out.println(c + " distance from nearest meld: " + dist);
//        if (c.equals(player.picked_up_discard)){ // cannot discard the card we just picked up
//            return MAXVALUE;
//        }
//        else if (dist == 0){ // 1. card goes into a current new meld, don't remove it
//            // doesn't matter if drawing from the facedown cards is a good option, we are
// guaranteed to reduce deadwood
//            // even if it doesn't make a new meld and get rid of a lot of points, that's okay
////            System.out.println("card goes into current meld");
//            return MAXVALUE-.02;
//        } else if (dist <= -1 && dist > -100){ // more than 1 card goes into this meld, if we can
// remove any other card we can get rid of this one
//            return MAXVALUE - .01;
//        }
//        // Now for the actual qualitative to quantitative heuristic magic
//
//        int deadwoodPoints = GinRummyUtil.getDeadwoodPoints(c);
//
//        double deadwoodWeight = MINVALUE; // 40% of our total weight
//        double lowCardWeight = MINVALUE; // 20% of our total weight
//        double newMeldLaterWeight = MINVALUE; // 40% of our total weight
//
//        deadwoodWeight = .4 * ((double) 1/deadwoodPoints); // This formula takes 40% of a value
// between 1/10 -> 1/1. The lower the card, the better
//        lowCardWeight = .2 * ((double)1/GinRummyUtil.getDeadwoodPoints(c)); // the lower the card,
// the better (even if it doesn't reduce deadwood by much)
//        if (dist <= 2){ // only consider this if adding this card would put us at most 1 card away
// from a meld
//            newMeldLaterWeight = .4 * ((double)1/GinRummyUtil.getDeadwoodPoints(c)); // favor
// cards with lower value
//        }
//        retValue = (deadwoodWeight + lowCardWeight + newMeldLaterWeight) * .97; // card does not
// go into a meld, prefer to remove these rather than cards that do go into a meld
////        System.out.println("my hand " + c + " " + retValue + " heuristics:\n" +
////                "\tdeadwoodWeight: " + deadwoodWeight + "\n" +
////                "\tlowCardWeight: " + lowCardWeight + "\n" +
////                "\tnewMeldLaterWeight: " + newMeldLaterWeight);
//
//
//        return retValue;
//    }
//    /*
//    Determining the least important card in our hand.
//    Returns a Map of cards with their corresponding importance value (e.g. [Card, value])
//    The value is between MINVALUE-MAXVALUE inclusive, with 0 being the least important card, and 1
// being the most important card.
//     */
//    public HashMap<Card, Double> handImportance(){
//        HashMap<Card, Double> pairs = new HashMap<>();
//        for (int i=0; i<player.my_hand.size(); i++){
//            pairs.put(player.my_hand.get(i), _cardValue(player.my_hand.get(i)));
//        }
//        return pairs;
//    }
//    /*
//    Deciding when to knock.
//    Current strategy: don't, unless we have gin. Undercutting gives a huge bonus so we don't want
// to go down
//    too early.
//    Returns true if we should knock, false if we shouldn't.
//    NOTE: we must still 'knock' (aka return our melds) when our opponent does in order to score
// our hand,
//    so you will sometimes need to ignore the return value from this.
//     */
//    public boolean knock(){
//        if (GinRummyUtil.getDeadwoodPoints(player.my_hand) == 0){
//            return true;
//        } else{
//            return false;
//        }
//    }
//    /*
//    Helper function that returns the least important card in the hand.
//     */
//    public Card leastImportantCard(){
//        HashMap<Card, Double> pairs = handImportance();
//        double lowestValue = MAXVALUE;
//        Card lowestCard = null;
////        System.out.println("card pairing: " + pairs);
////        System.out.println("my_hand: " + player.my_hand);
//        for (Card c: player.my_hand){
//            if (pairs.get(c) < lowestValue){
//                lowestValue = pairs.get(c);
//                lowestCard = c;
//            }
//        }
////        System.out.println("Here's how I rank my hand: " + pairs);
////        System.out.println("I think " + lowestCard + " is the least important card in the
// hand");
//        return lowestCard;
//    }
// }
