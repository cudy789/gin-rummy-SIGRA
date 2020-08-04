package siftagent;

import ginrummy.Card;
import ginrummy.GinRummyPlayer;
import ginrummy.GinRummyUtil;

import java.util.ArrayList;

/*
    This class assigns a quantitative value on the 4 different choices we have to make
    in a gin rummy game.  The agent should use these heuristics to decide between drawing
    faceup vs. facedown, which card to discard, and when to knock.

 */

public class Heuristic {

    private SiftAgent player;
    public static double MAXVALUE = 1;
    public static double MINVALUE = 0;
    public static int MAXSINGLECARDDEADWOODREDUCTION = 9;

    public Heuristic(SiftAgent player){
        this.player = player;
    }
    /*
    Factors in deciding when to draw the faceup card:
        1. Does this card go into a current meld?
        2. Does it make a new meld?
        3. We assume this card does not go into an opponents meld (aka they're not bluffing)
        4. P(this card would make a new meld later)
        5. Difference in deadwood compared to the least important card
     Returns a value between MINVALUE-MAXVALUE inclusive, with 0 being do not draw faceup and 1 being do draw faceup.
     */
    public double faceupValue(){
        double retValue = MINVALUE;
        // Maximum value a single faceup card could reduce our deadwood by is 20pts
        Card c = this.player.discard_pile.peek();
        ArrayList<Card> currentHand = new ArrayList<Card>();
        for(int i=0; i<SiftAgent.HAND_SIZE; i++){
            currentHand.add(player.my_hand.get(i));
        }
        int dist = this.player.distance_from_nearest_meld(c);
        if (dist == 0){ // 1. faceup card goes into a current new meld, always take it
            // doesn't matter if drawing from the facedown cards is a good option, we are guaranteed to reduce deadwood
            // even if it doesn't make a new meld and get rid of a lot of points, that's okay
//            System.out.println("faceup card goes into current meld");
            return MAXVALUE;
        } else if (dist == 1){ // 2. faceup makes a new meld, always take it
            // we remove the deadwood from at least 2 cards in our hand, even if it's not a lot of points
//            System.out.println("faceup card makes a new meld");
            return MAXVALUE;
        }
        // Now for the actual qualitative to quantitative heuristic magic

        int origPoints = GinRummyUtil.getDeadwoodPoints(currentHand);
        currentHand.add(c);
        currentHand.remove(_leastImportantCard());
        int newPoints = GinRummyUtil.getDeadwoodPoints(currentHand);
        int diff = origPoints - newPoints;
        System.out.println("diff in heuristic land: " + diff);

        double diffWeight = MINVALUE; // 40% of our total weight
        double lowCardWeight = MINVALUE; // 20% of our total weight
        double newMeldLaterWeight = MINVALUE; // 40% of our total weight

        diffWeight = .4 * ((double)(diff + MAXSINGLECARDDEADWOODREDUCTION) / (2 * MAXSINGLECARDDEADWOODREDUCTION)); // This formula takes 40% of a value between 0/18->18/18. The larger the number, the larger the reduction in deadwood
        lowCardWeight = .2 * ((double)1/c.getRank()); // the lower the card, the better (even if it doesn't reduce deadwood by much)
        if (dist == 2){ // only consider this if adding this card would put us at most 1 card away from a meld
            newMeldLaterWeight = .4 * ((double)1/c.getRank()); // favor cards with lower value
        }


        retValue = diffWeight + lowCardWeight + newMeldLaterWeight;
        System.out.println("Pick faceup card heuristic: " + retValue);
        System.out.println("\tdeadwood reduction: " + diffWeight + "\n" +
                "\tlow card: " + lowCardWeight + "\n" +
                "\tnew meld later: " + newMeldLaterWeight);
        return retValue;

    }
    /*
    Factors in deciding when to draw the facedown card:
        * P(card will go into a current meld)
        * P(card will make a new meld)
        * P(card will make a new meld later)
        * P(card will go into an opponent's meld)
        * P(card will reduce overall deadwood)
        * Avg expected value of this card
     Returns a value between MINVALUE-MAXVALUE inclusive, with 0 being do not draw facedown and 1 being do draw facedown.
     */
    public double facedownValue(){
        return 0;
    }
    private double _cardValue(Card c){
        double retValue = MINVALUE;
        // Maximum value a single card could increase our deadwood by is 20pts
//        Card c = this.player.discard_pile.peek();
        ArrayList<Card> currentHand = new ArrayList<Card>();
        for(int i=0; i<SiftAgent.HAND_SIZE; i++){
            currentHand.add(player.my_hand.get(i));
        }
        System.out.println("my hand in heuristic land: " + currentHand);
        int dist = this.player.distance_from_nearest_meld(c);
        if (c.equals(player.picked_up_discard)){ // cannot discard the card we just picked up
            return MAXVALUE;
        }
        else if (dist == 0){ // 1. card goes into a current new meld, don't remove it
            // doesn't matter if drawing from the facedown cards is a good option, we are guaranteed to reduce deadwood
            // even if it doesn't make a new meld and get rid of a lot of points, that's okay
            System.out.println("card goes into current meld");
            return MAXVALUE-.02;
        } else if (dist <= -1){ // more than 1 card goes into this meld, if we can remove any other card we can get rid of this one
            return MAXVALUE - .01;

        }
        return 0;
    }
    /*
    Determining the least important card in our hand.
    Factors in deciding the importance of cards:
        * Does this card go into a current meld?
        * Did we just draw this card from the discard (faceup) pile?
        * P(card will make a new meld later)
        * P(card will go into an opponents meld)
        * Deadwood value
    Returns a list of cards with their corresponding importance value (e.g. [Card, value])
    The value is between MINVALUE-MAXVALUE inclusive, with 0 being the least important card, and 1 being the most important card.
     */
    public double[] handImportance(){
        double[] importance = {0,0,0,0,0,0,0,0,0,0};


        return importance;
    }
    /*
    Deciding when to knock.
    Current strategy: don't, unless we have gin. Undercutting gives a huge bonus so we don't want to go down
    too early.
    Returns true if we should knock, false if we shouldn't.
    NOTE: we must still 'knock' (aka return our melds) when our opponent does in order to score our hand,
    so you will sometimes need to ignore the return value from this.
     */
    public boolean knock(){
        return false;
    }
    /*
    Helper function that returns the least important card in the hand.
     */
    private Card _leastImportantCard(){
        double[] importance = handImportance();
        double lowestValue = MAXVALUE;
        Card lowestCard = null;
        for (int i=0; i<SiftAgent.HAND_SIZE; i++){
            if (importance[i] < lowestValue){
                lowestValue = importance[i];
                lowestCard = player.my_hand.get(i);
            }
        }
        System.out.println("I think " + lowestCard + " is the least important card in the hand");
        return lowestCard;
    }
}
