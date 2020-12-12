package sigra.agents;

import ginrummy.Card;
import ginrummy.GinRummyUtil;
import java.util.ArrayList;

/*
Same as the SimpleGinRummyAgent but instead we only knock when we get gin.
 */

public class StubbornSimpleGinRummyPlayer extends SimpleGinRummyPlayer {
  @Override
  public ArrayList<ArrayList<Card>> getFinalMelds() {
    if (!opponentKnocked) {
      return null;
    } else {
      ArrayList<ArrayList<ArrayList<Card>>> bestMeldSets = GinRummyUtil.cardsToBestMeldSets(cards);

      return bestMeldSets.isEmpty()
          ? new ArrayList<ArrayList<Card>>()
          : bestMeldSets.get(random.nextInt(bestMeldSets.size()));
    }
  }
}
