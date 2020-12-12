// -*- c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8; -*-
package ginrummy;

import java.util.ArrayList;
import java.util.Random;
import java.util.Stack;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Runnable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * A class for modeling a game of Gin Rummy
 * 
 * @author Todd W. Neller
 * @version 1.0

 Copyright (C) 2020 Todd Neller

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 Information about the GNU General Public License is available online at:
 http://www.gnu.org/licenses/
 To receive a copy of the GNU General Public License, write to the Free
 Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 02111-1307, USA.

*/
public class GinRummyGame implements Runnable {
	private final PrintStream out;

	private final int GAME_RESULT_DRAW = -1;

	// number of times a hand may be repeated
	private final int ALLOWED_REPEATS = 25;

	private final int NUM_GAMES = 500;

	/**
	 * Random number generator
	 */
	private static final Random RANDOM = new Random();
	
	/**
	 * Hand size (before and after turn). After draw and before discard there is one extra card.
	 */
	private static final int HAND_SIZE = 10;
	
	/**
	 * Whether or not to print information during game play
	 */
	private boolean playVerbose = false;
	
	/**
	 * Two Gin Rummy players numbered according to their array index.
	 */
	private final GinRummyPlayer[] players;
        
	/**
	 * Set whether or not there is to be printed output during gameplay.
	 * @param playVerbose whether or not there is to be printed output during gameplay
	 */
	public void setPlayVerbose(final boolean _playVerbose) {
		playVerbose = _playVerbose;
	}

	public GinRummyGame(final GinRummyPlayer player0, final GinRummyPlayer player1) {
		out = System.out;
		players = new GinRummyPlayer[] {player0, player1};
	}
	/**
	 * Create a GinRummyGame with two given players
	 * @param player0 Player 0
	 * @param player1 Player 1
	 */
	public GinRummyGame(final GinRummyPlayer player0, final GinRummyPlayer player1, OutputStream outstream) {
		out = new PrintStream(outstream);
		players = new GinRummyPlayer[] {player0, player1};
	}

	/**
	 * Play a game of Gin Rummy and return the winning player number 0 or 1.
	 * @return the winning player number 0 or 1
	 */
	@SuppressWarnings("unchecked")
	public int play() {
		int repeats = 0;
		final int[] scores = new int[2];
		final ArrayList<ArrayList<Card>> hands = new ArrayList<ArrayList<Card>>();
		hands.add(new ArrayList<Card>());
		hands.add(new ArrayList<Card>());
		int startingPlayer = RANDOM.nextInt(2);
		
		while (scores[0] < GinRummyUtil.GOAL_SCORE && scores[1] < GinRummyUtil.GOAL_SCORE) { // while game not over
			int currentPlayer = startingPlayer;
			int opponent = (currentPlayer == 0) ? 1 : 0;
			
			// get shuffled deck and deal cards
			final Stack<Card> deck = Card.getShuffle(RANDOM.nextInt());
			hands.get(0).clear();
			hands.get(1).clear();
			for (int i = 0; i < 2 * HAND_SIZE; i++)
				hands.get(i % 2).add(deck.pop());
			for (int i = 0; i < 2; i++) {
				final Card[] handArr = new Card[HAND_SIZE];
				hands.get(i).toArray(handArr);
				players[i].startGame(i, startingPlayer, handArr); 
				if (playVerbose)
					out.printf("Player %d is dealt %s.\n", i, hands.get(i));
			}
			if (playVerbose)
				out.printf("Player %d starts.\n", startingPlayer);
			final Stack<Card> discards = new Stack<Card>();
			discards.push(deck.pop());
			if (playVerbose)
				out.printf("The initial face up card is %s.\n", discards.peek());
			final Card firstFaceUpCard = discards.peek();
			int turnsTaken = 0;
			ArrayList<ArrayList<Card>> knockMelds = null;
			while (deck.size() > 2) { // while the deck has more than two cards remaining, play round
				// DRAW
				boolean drawFaceUp = false;
				final Card faceUpCard = discards.peek();
				// offer draw face-up iff not 3rd turn with first face up card (decline automatically in that case) 
				if (!(turnsTaken == 2 && faceUpCard == firstFaceUpCard)) { // both players declined and 1st player must draw face down
					drawFaceUp = players[currentPlayer].willDrawFaceUpCard(faceUpCard);
					if (playVerbose && !drawFaceUp && faceUpCard == firstFaceUpCard && turnsTaken < 2)
						out.printf("Player %d declines %s.\n", currentPlayer, firstFaceUpCard);
				}
				if (!(!drawFaceUp && turnsTaken < 2 && faceUpCard == firstFaceUpCard)) { // continue with turn if not initial declined option
					final Card drawCard = drawFaceUp ? discards.pop() : deck.pop();
					for (int i = 0; i < 2; i++) 
						players[i].reportDraw(currentPlayer, (i == currentPlayer || drawFaceUp) ? drawCard : null);
					if (playVerbose)
						out.printf("Player %d draws %s.\n", currentPlayer, drawCard);
					hands.get(currentPlayer).add(drawCard);

					// DISCARD
					final Card discardCard = players[currentPlayer].getDiscard();
					if (!hands.get(currentPlayer).contains(discardCard) || discardCard == faceUpCard) {
						if (playVerbose)
							out.printf("Player %d discards %s illegally and forfeits.\n", currentPlayer, discardCard);
                                                else
							out.printf("!!! Illegal discard by player %d !!!\n", currentPlayer);
						return opponent;
					}
					hands.get(currentPlayer).remove(discardCard);
					for (int i = 0; i < 2; i++) 
						players[i].reportDiscard(currentPlayer, discardCard);
					if (playVerbose)
						out.printf("Player %d discards %s.\n", currentPlayer, discardCard);
					discards.push(discardCard);
					if (playVerbose) {
						final ArrayList<Card> unmeldedCards = (ArrayList<Card>) hands.get(currentPlayer).clone();
						final ArrayList<ArrayList<ArrayList<Card>>> bestMelds = GinRummyUtil.cardsToBestMeldSets(unmeldedCards);
						if (bestMelds.isEmpty()) 
							out.printf("Player %d has %s with %d deadwood.\n", currentPlayer, unmeldedCards, GinRummyUtil.getDeadwoodPoints(unmeldedCards));
						else {
							final ArrayList<ArrayList<Card>> melds = bestMelds.get(0);
							for (final ArrayList<Card> meld : melds)
								for (final Card card : meld)
									unmeldedCards.remove(card);
							melds.add(unmeldedCards);
							out.printf("Player %d has %s with %d deadwood.\n", currentPlayer, melds, GinRummyUtil.getDeadwoodPoints(unmeldedCards));
						}
					}
						
					// CHECK FOR KNOCK 
					knockMelds = players[currentPlayer].getFinalMelds();
					if (knockMelds != null)
						break; // player knocked; end of round
				}

				turnsTaken++;
				currentPlayer = (currentPlayer == 0) ? 1 : 0;
				opponent = (currentPlayer == 0) ? 1 : 0;
			}
			
			if (knockMelds != null) { // round didn't end due to non-knocking and 2 cards remaining in draw pile
				// check legality of knocking meld
				final long handBitstring = GinRummyUtil.cardsToBitstring(hands.get(currentPlayer));
				long unmelded = handBitstring;
				for (final ArrayList<Card> meld : knockMelds) {
					final long meldBitstring = GinRummyUtil.cardsToBitstring(meld);
					if (!GinRummyUtil.getAllMeldBitstrings().contains(meldBitstring) // non-meld ...
					    || (meldBitstring & unmelded) != meldBitstring) { // ... or meld not in hand
						if (playVerbose)
							out.printf("Player %d melds %s illegally and forfeits.\n", currentPlayer, knockMelds);
                                                else
							out.printf("!!! Illegal meld by player %d !!!\n", currentPlayer);
						return opponent;
					}
					unmelded &= ~meldBitstring; // remove successfully melded cards from 
				}
				// compute knocking deadwood
				final int knockingDeadwood = GinRummyUtil.getDeadwoodPoints(knockMelds, hands.get(currentPlayer));
				if (knockingDeadwood > GinRummyUtil.MAX_DEADWOOD) {
					if (playVerbose)
						out.printf("Player %d melds %s with greater than %d deadwood and forfeits.\n", currentPlayer, knockMelds, knockingDeadwood);
                                        else
						out.printf("!!! Illegal knock by player %d !!!\n", currentPlayer);
					return opponent;
				}
				
				ArrayList<ArrayList<Card>> meldsCopy = new ArrayList<ArrayList<Card>>();
				for (final ArrayList<Card> meld : knockMelds)
					meldsCopy.add((ArrayList<Card>) meld.clone());
				for (int i = 0; i < 2; i++) 
					players[i].reportFinalMelds(currentPlayer, meldsCopy);
				if (playVerbose)
					if (knockingDeadwood > 0) 
						out.printf("Player %d melds %s with %d deadwood from %s.\n", currentPlayer, knockMelds, knockingDeadwood, GinRummyUtil.bitstringToCards(unmelded));
					else
						out.printf("Player %d goes gin with melds %s.\n", currentPlayer, knockMelds);

				// get opponent meld
				final ArrayList<ArrayList<Card>> opponentMelds = players[opponent].getFinalMelds();
				meldsCopy = new ArrayList<ArrayList<Card>>();
				for (final ArrayList<Card> meld : opponentMelds)
					meldsCopy.add((ArrayList<Card>) meld.clone());
				for (int i = 0; i < 2; i++) 
					players[i].reportFinalMelds(opponent, meldsCopy);
				
				// check legality of opponent meld
				final long opponentHandBitstring = GinRummyUtil.cardsToBitstring(hands.get(opponent));
				long opponentUnmelded = opponentHandBitstring;
				for (final ArrayList<Card> meld : opponentMelds) {
					final long meldBitstring = GinRummyUtil.cardsToBitstring(meld);
					if (!GinRummyUtil.getAllMeldBitstrings().contains(meldBitstring) // non-meld ...
					    || (meldBitstring & opponentUnmelded) != meldBitstring) { // ... or meld not in hand
						if (playVerbose)
							out.printf("Player %d melds %s illegally and forfeits.\n", opponent, opponentMelds);
                                                else
							out.printf("!!! Illegal meld by player %d !!!\n", opponent);
						return currentPlayer;
					}
					opponentUnmelded &= ~meldBitstring; // remove successfully melded cards from 
				}
				if (playVerbose)
					out.printf("Player %d melds %s.\n", opponent, opponentMelds);

				// lay off on knocking meld (if not gin)
				final ArrayList<Card> unmeldedCards = GinRummyUtil.bitstringToCards(opponentUnmelded);
				if (knockingDeadwood > 0) { // knocking player didn't go gin
					boolean cardWasLaidOff;
					do { // attempt to lay each card off
						cardWasLaidOff = false;
						Card layOffCard = null;
						ArrayList<Card> layOffMeld = null;
						for (final Card card : unmeldedCards) {
							for (final ArrayList<Card> meld : knockMelds) {
								final ArrayList<Card> newMeld = (ArrayList<Card>) meld.clone();
								newMeld.add(card);
								final long newMeldBitstring = GinRummyUtil.cardsToBitstring(newMeld);
								if (GinRummyUtil.getAllMeldBitstrings().contains(newMeldBitstring)) {
									layOffCard = card;
									layOffMeld = meld;
									break;
								}
							}
							if (layOffCard != null) {
								if (playVerbose)
									out.printf("Player %d lays off %s on %s.\n", opponent, layOffCard, layOffMeld);
								for (int i = 0; i < 2; i++) 
									players[i].reportLayoff(opponent, layOffCard, (ArrayList<Card>) layOffMeld.clone());
								unmeldedCards.remove(layOffCard);
								layOffMeld.add(layOffCard);
								cardWasLaidOff = true;
								break;
							}
								
						}
					} while (cardWasLaidOff);
				}
				int opponentDeadwood = 0;
				for (final Card card : unmeldedCards)
					opponentDeadwood += GinRummyUtil.getDeadwoodPoints(card);
				if (playVerbose)
					out.printf("Player %d has %d deadwood with %s\n", opponent, opponentDeadwood, unmeldedCards); 

				// compare deadwood and compute new scores
				if (knockingDeadwood == 0) { // gin round win
					scores[currentPlayer] += GinRummyUtil.GIN_BONUS + opponentDeadwood;
					if (playVerbose)
						out.printf("Player %d scores the gin bonus of %d plus opponent deadwood %d for %d total points.\n", currentPlayer, GinRummyUtil.GIN_BONUS, opponentDeadwood, GinRummyUtil.GIN_BONUS + opponentDeadwood);
                                        else
						out.print(currentPlayer);
				}
				else if (knockingDeadwood < opponentDeadwood) { // non-gin round win
					scores[currentPlayer] += opponentDeadwood - knockingDeadwood;
					if (playVerbose)
						out.printf("Player %d scores the deadwood difference of %d.\n", currentPlayer, opponentDeadwood - knockingDeadwood);
                                        else
						out.print(currentPlayer);
				}
				else { // undercut win for opponent
					scores[opponent] += GinRummyUtil.UNDERCUT_BONUS + knockingDeadwood - opponentDeadwood;
					if (playVerbose)
						out.printf("Player %d undercuts and scores the undercut bonus of %d plus deadwood difference of %d for %d total points.\n", opponent, GinRummyUtil.UNDERCUT_BONUS, knockingDeadwood - opponentDeadwood, GinRummyUtil.UNDERCUT_BONUS + knockingDeadwood - opponentDeadwood);
                                        else
						out.print(opponent);
				}
				startingPlayer = (startingPlayer == 0) ? 1 : 0; // starting player alternates
			} else { // If the round ends due to a two card draw pile with no knocking, the round is cancelled.
				repeats += 1;
				if (playVerbose) {
					out.println("The draw pile was reduced to two cards without knocking, so the hand is cancelled.");
				} else {
					out.print("x");
				}
				if (repeats > ALLOWED_REPEATS) {
					if (playVerbose) {
						out.println("Maximum number of repeats exceeded.");
					} else {
						out.println(" -> x");
					}
					return -1;
				}
			}
			
			// report final hands
			for (int i = 0; i < 2; i++)
				for (int j = 0; j < 2; j++)
					players[i].reportFinalHand(j, (ArrayList<Card>) hands.get(j).clone());

			// score reporting
			if (playVerbose) 
				out.printf("Player\tScore\n0\t%d\n1\t%d\n", scores[0], scores[1]);
			for (int i = 0; i < 2; i++) 
				players[i].reportScores(scores.clone());
		}
		if (playVerbose)
			out.printf("Player %s wins.\n", scores[0] > scores[1] ? 0 : 1);
                else
			out.printf(" -> %d\n", scores[0] > scores[1] ? 0 : 1);
		return scores[0] >= GinRummyUtil.GOAL_SCORE ? 0 : 1;
	}

	public void run() {
		out.println("Running tournament between " + players[0].getClass().getName() + " and " + players[1].getClass().getName());

		int numDraws = 0;
		int p0Wins = 0;
		int p1Wins = 0;
		final long startMs = System.currentTimeMillis();
		for (int i = 0; i < NUM_GAMES; i++) {
			int p = play();
			if (p == GAME_RESULT_DRAW) {
				numDraws += 1;
			} else if (p == 1) {
				p1Wins += 1;
			} else {
				p0Wins += 1;
			}
		}
		final long totalMs = System.currentTimeMillis() - startMs;
		double p0WinPct = (double) p0Wins / (double) NUM_GAMES;
		double p1WinPct = (double) p1Wins / (double) NUM_GAMES;
		double drawPct = (double) numDraws / (double) NUM_GAMES;
		out.println(p0WinPct + " win rate (" + p0Wins + "/" + NUM_GAMES + ") for " + players[0].getClass().getName());
		out.println(p1WinPct + " win rate (" + p1Wins + "/" + NUM_GAMES + ") for " + players[1].getClass().getName());
		out.println(drawPct + " draws (" + numDraws + "/" + NUM_GAMES + ")");
	}

	static final String[] playerPool = new String[] {
		"sigra.agents.SimpleGinRummyPlayer",
		"sigra.agents.StubbornSimpleGinRummyPlayer",
		"sigra.agents.QuickKnockingSecondOrderDeadwoodMinimizingAgent",
		"sigra.agents.NoOpponentModelingSecondOrderDeadwoodMinimizingAgent",
		"sigra.agents.SecondOrderDeadwoodMinimizingAgent",
		"sigra.agents.StubbornOpModel",
		"sigra.agents.OpModelOnlyAgent",
		"sigra.agents.MMDOnlyAgent"
	};
	
	public static void main(final String[] args) {
		try {
			Files.createDirectories(Paths.get("results/"));
		} catch (Exception e) { System.err.println(e); }

		ExecutorCompletionService<Path> executor = new ExecutorCompletionService<>(Executors.newWorkStealingPool());

		int tourney_ct = 0;

		for (String p1 : playerPool) {
			for (String p2 : playerPool) {
				try {
					GinRummyPlayer p0i = (GinRummyPlayer)Class.forName(p1).newInstance();
					GinRummyPlayer p1i = (GinRummyPlayer)Class.forName(p2).newInstance();
					Path outpath = Paths.get("results/" + p1 + "-vs-" + p2 + "-results.txt");
					OutputStream out = Files.newOutputStream(outpath);
					GinRummyGame tourney = new GinRummyGame(p0i, p1i, out);
					executor.submit(tourney, outpath);
					tourney_ct++;
				} catch (Exception e) {
					System.err.println("Error starting tournament: " + e);
				}
			}
		}
		
		while (tourney_ct != 0) {
			try {
				Future<Path> tourney = executor.take();
				Path results = tourney.get();
				
				System.out.println("Tournament " + results + " finished");
				tourney_ct--;
			} catch (Exception e) {
				System.err.println("Error getting tournament results: " + e);
				continue;
			}
		}
	}
}
