package games.strategy.triplea.delegate;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;

import games.strategy.engine.data.Change;
import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.NamedAttachable;
import games.strategy.engine.data.PlayerID;
import games.strategy.engine.data.ProductionFrontier;
import games.strategy.engine.data.ProductionRule;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.message.IRemote;
import games.strategy.triplea.Constants;
import games.strategy.triplea.TripleAUnit;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.util.BonusIncomeUtils;
import games.strategy.util.IntegerMap;
import games.strategy.util.Match;

/**
 * This delegate is only supposed to be run once, per game, at the start of the game.
 */
public class InitializationDelegate extends BaseTripleADelegate {
  private boolean m_needToInitialize = true;

  /** Creates a new instance of InitializationDelegate. */
  public InitializationDelegate() {}

  @Override
  public void initialize(final String name, final String displayName) {
    m_name = name;
    m_displayName = displayName;
  }

  /**
   * Called before the delegate will run.
   */
  @Override
  public void start() {
    super.start();
    if (m_needToInitialize) {
      init(m_bridge);
      m_needToInitialize = false;
    }
  }

  @Override
  public void end() {
    super.end();
  }

  @Override
  public Serializable saveState() {
    final InitializationExtendedDelegateState state = new InitializationExtendedDelegateState();
    state.superState = super.saveState();
    // add other variables to state here:
    state.m_needToInitialize = this.m_needToInitialize;
    return state;
  }

  @Override
  public void loadState(final Serializable state) {
    final InitializationExtendedDelegateState s = (InitializationExtendedDelegateState) state;
    super.loadState(s.superState);
    this.m_needToInitialize = s.m_needToInitialize;
  }

  @Override
  public boolean delegateCurrentlyRequiresUserInput() {
    return false;
  }

  protected void init(final IDelegateBridge bridge) {
    initDestroyerArtillery(bridge);
    initShipyards(bridge);
    initTwoHitBattleship(bridge);
    initOriginalOwner(bridge);
    initTech(bridge);
    initSkipUnusedBids(bridge.getData());
    initAiStartingBonusIncome(bridge);
    initDeleteAssetsOfDisabledPlayers(bridge);
    initTransportedLandUnits(bridge);
    resetUnitState(bridge);
  }

  /**
   * The initTransportedLandUnits has some side effects, and we need to reset unit state to get rid of them.
   */
  private void resetUnitState(final IDelegateBridge bridge) {
    final Change change = MoveDelegate.getResetUnitStateChange(getData());
    if (!change.isEmpty()) {
      m_bridge.getHistoryWriter().startEvent("Cleaning up unit state.");
      m_bridge.addChange(change);
    }
  }

  /**
   * Want to make sure that all units in the sea that can be transported are
   * marked as being transported by something.
   * We assume that all transportable units in the sea are in a transport, no
   * exceptions.
   */
  private static void initTransportedLandUnits(final IDelegateBridge bridge) {
    final GameData data = bridge.getData();
    // check every territory
    boolean historyItemCreated = false;
    final Iterator<Territory> allTerritories = data.getMap().getTerritories().iterator();
    while (allTerritories.hasNext()) {
      final Territory current = allTerritories.next();
      // only care about water
      if (!current.isWater()) {
        continue;
      }
      final Collection<Unit> units = current.getUnits().getUnits();
      if (units.size() == 0 || !Match.someMatch(units, Matches.UnitIsLand)) {
        continue;
      }
      // map transports, try to fill
      final Collection<Unit> transports = Match.getMatches(units, Matches.UnitIsTransport);
      final Collection<Unit> land = Match.getMatches(units, Matches.UnitIsLand);
      for (final Unit toLoad : land) {
        final UnitAttachment ua = UnitAttachment.get(toLoad.getType());
        final int cost = ua.getTransportCost();
        if (cost == -1) {
          throw new IllegalStateException("Non transportable unit in sea");
        }
        // find the next transport that can hold it
        final Iterator<Unit> transportIter = transports.iterator();
        boolean found = false;
        while (transportIter.hasNext()) {
          final Unit transport = transportIter.next();
          final int capacity = TransportTracker.getAvailableCapacity(transport);
          if (capacity >= cost) {
            if (!historyItemCreated) {
              bridge.getHistoryWriter().startEvent("Initializing Units in Transports");
              historyItemCreated = true;
            }
            try {
              bridge.addChange(TransportTracker.loadTransportChange((TripleAUnit) transport, toLoad));
            } catch (final IllegalStateException e) {
              System.err.println(
                  "You can only edit add transports+units after the initialization delegate of the game is finished.  "
                      + "If this error came up and you have not used Edit Mode to add units + transports, then please "
                      + "report this as a bug:  \r\n" + e.getMessage());
            }
            found = true;
            break;
          }
        }
        if (!found) {
          throw new IllegalStateException(
              "Cannot load all land units in sea transports. " + "Please make sure you have enough transports. "
                  + "You may need to re-order the xml's placement of transports and land units, "
                  + "as the engine will try to fill them in the order they are given.");
        }
      }
    }
  }

  private static void initAiStartingBonusIncome(final IDelegateBridge bridge) {
    bridge.getData().getPlayerList().getPlayers().forEach(
        player -> BonusIncomeUtils.addBonusIncome(player.getResources().getResourcesCopy(), bridge, player));
  }

  private static void initDeleteAssetsOfDisabledPlayers(final IDelegateBridge bridge) {
    final GameData data = bridge.getData();
    if (!games.strategy.triplea.Properties.getDisabledPlayersAssetsDeleted(data)) {
      return;
    }
    for (final PlayerID player : data.getPlayerList().getPlayers()) {
      if (player.isNull() || !player.getIsDisabled()) {
        continue;
      }
      // delete all the stuff they have
      final CompositeChange change = new CompositeChange();
      for (final Resource r : player.getResources().getResourcesCopy().keySet()) {
        final int deleted = player.getResources().getQuantity(r);
        if (deleted != 0) {
          change.add(ChangeFactory.changeResourcesChange(player, r, -deleted));
        }
      }
      final Collection<Unit> heldUnits = player.getUnits().getUnits();
      if (!heldUnits.isEmpty()) {
        change.add(ChangeFactory.removeUnits(player, heldUnits));
      }
      final Match<Unit> owned = Matches.unitIsOwnedBy(player);
      for (final Territory t : data.getMap().getTerritories()) {
        final Collection<Unit> terrUnits = t.getUnits().getMatches(owned);
        if (!terrUnits.isEmpty()) {
          change.add(ChangeFactory.removeUnits(t, terrUnits));
        }
      }
      if (!change.isEmpty()) {
        bridge.getHistoryWriter().startEvent("Remove all resources and units from: " + player.getName());
        bridge.addChange(change);
      }
    }
  }

  private static void initSkipUnusedBids(final GameData data) {
    // we have a lot of bid steps, 12 for pact of steel
    // in multi player this can be time consuming, since each vm
    // must be notified (and have its ui) updated for each step,
    // so remove the bid steps that arent used
    for (final GameStep step : data.getSequence()) {
      if (step.getDelegate() instanceof BidPlaceDelegate || step.getDelegate() instanceof BidPurchaseDelegate) {
        if (!BidPurchaseDelegate.doesPlayerHaveBid(data, step.getPlayerID())) {
          step.setMaxRunCount(0);
        }
      }
    }
  }

  private static void initTech(final IDelegateBridge bridge) {
    final GameData data = bridge.getData();
    final Iterator<PlayerID> players = data.getPlayerList().getPlayers().iterator();
    while (players.hasNext()) {
      final PlayerID player = players.next();
      final Iterator<TechAdvance> advances = TechTracker.getCurrentTechAdvances(player, data).iterator();
      if (advances.hasNext()) {
        bridge.getHistoryWriter().startEvent("Initializing " + player.getName() + " with tech advances");
        while (advances.hasNext()) {
          final TechAdvance advance = advances.next();
          advance.perform(player, bridge);
        }
      }
    }
  }

  private static void initDestroyerArtillery(final IDelegateBridge bridge) {
    final GameData data = bridge.getData();
    final boolean addArtilleryAndDestroyers = games.strategy.triplea.Properties.getUse_Destroyers_And_Artillery(data);
    if (!isWW2V2(data) && addArtilleryAndDestroyers) {
      final CompositeChange change = new CompositeChange();
      final ProductionRule artillery = data.getProductionRuleList().getProductionRule("buyArtillery");
      final ProductionRule destroyer = data.getProductionRuleList().getProductionRule("buyDestroyer");
      final ProductionFrontier frontier = data.getProductionFrontierList().getProductionFrontier("production");
      if (artillery != null && !frontier.getRules().contains(artillery)) {
        change.add(ChangeFactory.addProductionRule(artillery, frontier));
      }
      if (destroyer != null && !frontier.getRules().contains(destroyer)) {
        change.add(ChangeFactory.addProductionRule(destroyer, frontier));
      }
      final ProductionRule artilleryIndustrialTechnology =
          data.getProductionRuleList().getProductionRule("buyArtilleryIndustrialTechnology");
      final ProductionRule destroyerIndustrialTechnology =
          data.getProductionRuleList().getProductionRule("buyDestroyerIndustrialTechnology");
      final ProductionFrontier frontierIndustrialTechnology =
          data.getProductionFrontierList().getProductionFrontier("productionIndustrialTechnology");
      if (artilleryIndustrialTechnology != null
          && !frontierIndustrialTechnology.getRules().contains(artilleryIndustrialTechnology)) {
        change.add(ChangeFactory.addProductionRule(artilleryIndustrialTechnology, frontierIndustrialTechnology));
      }
      if (destroyerIndustrialTechnology != null
          && !frontierIndustrialTechnology.getRules().contains(destroyerIndustrialTechnology)) {
        change.add(ChangeFactory.addProductionRule(destroyerIndustrialTechnology, frontierIndustrialTechnology));
      }
      if (!change.isEmpty()) {
        bridge.getHistoryWriter().startEvent("Adding destroyers and artillery production rules");
        bridge.addChange(change);
      }
    }
  }

  private static void initShipyards(final IDelegateBridge bridge) {
    final GameData data = bridge.getData();
    final boolean useShipyards = games.strategy.triplea.Properties.getUse_Shipyards(data);
    if (useShipyards) {
      final CompositeChange change = new CompositeChange();
      final ProductionFrontier frontierShipyards =
          data.getProductionFrontierList().getProductionFrontier("productionShipyards");
      /*
       * Find the productionRules, if the unit is NOT a sea unit, add it to the ShipYards prod rule.
       */
      final ProductionFrontier frontierNonShipyards =
          data.getProductionFrontierList().getProductionFrontier("production");
      final Collection<ProductionRule> rules = frontierNonShipyards.getRules();
      for (final ProductionRule rule : rules) {
        final String ruleName = rule.getName();
        final IntegerMap<NamedAttachable> ruleResults = rule.getResults();
        final NamedAttachable named = ruleResults.keySet().iterator().next();
        if (!(named instanceof UnitType)) {
          continue;
        }
        final UnitType unit = data.getUnitTypeList().getUnitType(named.getName());
        final boolean isSea = UnitAttachment.get(unit).getIsSea();
        if (!isSea) {
          final ProductionRule prodRule = data.getProductionRuleList().getProductionRule(ruleName);
          change.add(ChangeFactory.addProductionRule(prodRule, frontierShipyards));
        }
      }
      bridge.getHistoryWriter().startEvent("Adding shipyard production rules - land/air units");
      bridge.addChange(change);
    }
  }

  private static boolean isWW2V2(final GameData data) {
    return games.strategy.triplea.Properties.getWW2V2(data);
  }

  private static void initTwoHitBattleship(final IDelegateBridge bridge) {
    final GameData data = bridge.getData();
    final boolean userEnabled = games.strategy.triplea.Properties.getTwoHitBattleships(data);
    final UnitType battleShipUnit = data.getUnitTypeList().getUnitType(Constants.UNIT_TYPE_BATTLESHIP);
    if (battleShipUnit == null) {
      return;
    }
    final UnitAttachment battleShipAttachment = UnitAttachment.get(battleShipUnit);
    final boolean defaultEnabled = battleShipAttachment.getHitPoints() > 1;
    if (userEnabled != defaultEnabled) {
      bridge.getHistoryWriter().startEvent("TwoHitBattleships:" + userEnabled);
      bridge.addChange(ChangeFactory.attachmentPropertyChange(battleShipAttachment, userEnabled ? 2 : 1, "hitPoints"));
    }
  }

  private static void initOriginalOwner(final IDelegateBridge bridge) {
    final GameData data = bridge.getData();
    final CompositeChange changes = new CompositeChange();
    for (final Territory current : data.getMap()) {
      if (!current.getOwner().isNull()) {
        final TerritoryAttachment territoryAttachment = TerritoryAttachment.get(current);
        if (territoryAttachment == null) {
          throw new IllegalStateException("No territory attachment for " + current);
        }
        if (territoryAttachment.getOriginalOwner() == null && current.getOwner() != null) {
          changes.add(OriginalOwnerTracker.addOriginalOwnerChange(current, current.getOwner()));
        }
        final Collection<Unit> factoryAndInfrastructure = current.getUnits().getMatches(Matches.UnitIsInfrastructure);
        changes.add(OriginalOwnerTracker.addOriginalOwnerChange(factoryAndInfrastructure, current.getOwner()));
      } else if (!current.isWater()) {
        final TerritoryAttachment territoryAttachment = TerritoryAttachment.get(current);
        if (territoryAttachment == null) {
          throw new IllegalStateException("No territory attachment for " + current);
        }
      }
    }
    bridge.getHistoryWriter().startEvent("Adding original owners");
    bridge.addChange(changes);
  }

  @Override
  public Class<? extends IRemote> getRemoteType() {
    return null;
  }
}


class InitializationExtendedDelegateState implements Serializable {
  private static final long serialVersionUID = -9000446777655823735L;
  Serializable superState;
  public boolean m_needToInitialize;
}
