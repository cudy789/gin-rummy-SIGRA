package siftagent;

import ginrummy.Card;
import java.util.ArrayList;
import java.util.function.BinaryOperator;

public interface DeadwoodMinimizingAgent {
  public BinaryOperator<ArrayList<Card>> accumulator(ArrayList<Card> unknowns);
}
