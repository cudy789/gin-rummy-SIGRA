package siftagent;

import ginrummy.Card;
import java.util.ArrayList;
import java.util.function.Function;

public interface DeadwoodMinimizingAgent {
  /**
   * A funciton which gives a hand a score, given a particular state (unknown cards so far, may need
   * to add more).
   */
  public Function<ArrayList<Card>, Double> evaluator(ArrayList<Card> unknowns);
}
