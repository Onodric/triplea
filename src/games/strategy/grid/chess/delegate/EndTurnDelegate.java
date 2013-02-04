package games.strategy.grid.chess.delegate;

import games.strategy.common.delegate.AbstractDelegate;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.PlayerID;
import games.strategy.engine.message.IRemote;
import games.strategy.grid.ui.display.IGridGameDisplay;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class EndTurnDelegate extends AbstractDelegate
{
	/**
	 * Called before the delegate will run.
	 */
	@Override
	public void start()
	{
		super.start();
		final PlayerID winner = checkForWinner();
		if (winner != null)
		{
			signalGameOver(winner.getName() + " wins!");
		}
	}
	
	@Override
	public void end()
	{
		super.end();
	}
	
	@Override
	public Serializable saveState()
	{
		return null;
	}
	
	@Override
	public void loadState(final Serializable state)
	{
	}
	
	public boolean stuffToDoInThisDelegate()
	{
		return false;
	}
	
	/**
	 * Notify all players that the game is over.
	 * 
	 * @param status
	 *            the "game over" text to be displayed to each user.
	 */
	private void signalGameOver(final String status)
	{
		// If the game is over, we need to be able to alert all UIs to that fact.
		// The display object can send a message to all UIs.
		final IGridGameDisplay display = (IGridGameDisplay) m_bridge.getDisplayChannelBroadcaster();
		display.setStatus(status);
		display.setGameOver();
		m_bridge.stopGameSequence();
	}
	
	/**
	 * Check to see if anyone has won the game.
	 * 
	 * @return the player who has won, or <code>null</code> if there is no winner yet
	 */
	private PlayerID checkForWinner()
	{
		if (doWeWin(m_player, getData(), 1))
			return m_player;
		return null;
	}
	
	public static boolean doWeWin(final PlayerID player, final GameData data, final int testForCheckTurnsAhead)
	{
		if (player == null)
			throw new IllegalArgumentException("Checking for winner can not have null player");
		final Collection<PlayerID> enemies = new ArrayList<PlayerID>(data.getPlayerList().getPlayers());
		enemies.remove(player);
		final Iterator<PlayerID> iter = enemies.iterator();
		while (iter.hasNext())
		{
			final PlayerID e = iter.next();
			if (PlayDelegate.getKingTerritories(e, data).isEmpty() || !PlayDelegate.canWeMakeAValidMoveThatIsNotPuttingUsInCheck(e, data, testForCheckTurnsAhead))
				iter.remove();
		}
		if (enemies.isEmpty())
			return true;
		return false;
	}
	
	/**
	 * If this class implements an interface which inherits from IRemote, returns the class of that interface.
	 * Otherwise, returns null.
	 */
	@Override
	public Class<? extends IRemote> getRemoteType()
	{
		return null;
	}
}