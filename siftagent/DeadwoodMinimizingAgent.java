package siftagent;

import ginrummy.Card;
import java.util.ArrayList;
import java.util.function.Function;

public interface DeadwoodMinimizingAgent {
  public Function<ArrayList<Card>, Double> evaluator(ArrayList<Card> unknowns);
}
