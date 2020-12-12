package sigra.agents;

import ginrummy.Card;
import java.util.ArrayList;
import java.util.function.Function;

public class NaiveDeadwoodMinimizingAgent extends AbstractDeadwoodMinimizingAgent {
  @Override
  public Function<ArrayList<Card>, Double> evaluator(ArrayList<Card> unknowns) {
    return (hand) -> {
      return (double) deadwoodMinusMelds(hand);
    };
  }
}
