package siftagent;

import ginrummy.Card;
import java.util.ArrayList;
import java.util.function.BinaryOperator;

public class NaiveDeadwoodMinimizingAgent extends AbstractDeadwoodMinimizingAgent {

  @Override
  public BinaryOperator<ArrayList<Card>> accumulator(ArrayList<Card> unknowns) {
    return pickHandWithLeastDeadwood;
  }

  BinaryOperator<ArrayList<Card>> pickHandWithLeastDeadwood =
      (a, b) -> {
        if (deadwoodMinusMelds(a) < deadwoodMinusMelds(b)) {
          return a;
        } else {
          return b;
        }
      };
}
