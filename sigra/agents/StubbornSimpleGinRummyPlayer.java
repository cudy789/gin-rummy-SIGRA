package sigra.agents;

import java.util.ArrayList;
import java.util.Random;
/*
Same as the SimpleGinRummyAgent but instead we only knock when we get gin.
 */

public class StubbornSimpleGinRummyPlayer implements SimpleGinRummyPlayer {
	@Override
	public ArrayList<ArrayList<Card>> getFinalMelds() {
		if (!opponentKnocked) {
			return null;
		} else {
			return bestMeldSets.isEmpty() ? new ArrayList<ArrayList<Card>>() : bestMeldSets.get(random.nextInt(bestMeldSets.size()));
		}
	}
}
