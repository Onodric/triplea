package games.strategy.triplea.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.PlayerID;
import games.strategy.engine.data.Resource;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.engine.stats.AbstractStat;
import games.strategy.engine.stats.IStat;
import games.strategy.triplea.Constants;
import games.strategy.triplea.attachments.TechAttachment;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TechAdvance;
import games.strategy.util.Match;

public class ExtendedStats extends StatPanel {
  private static final long serialVersionUID = 2502397606419491543L;
  private IStat[] statsExtended = new IStat[] {};

  public ExtendedStats(final GameData data, final IUIContext uiContext) {
    super(data, uiContext);
  }

  @Override
  protected void initLayout() {
    // no layout necessary
  }

  @Override
  public void setGameData(final GameData data) {
    super.setGameData(data);
  }

  public IStat[] getStatsExtended(final GameData data) {
    if (statsExtended.length == 0) {
      fillExtendedStats(data);
    }
    return statsExtended;
  }

  private void fillExtendedStats(final GameData data) {
    // add other resources, other than PUs and tech tokens
    final List<Resource> resources = data.getResourceList().getResources();
    for (final Resource r : resources) {
      if (r.getName().equals(Constants.PUS) || r.getName().equals(Constants.TECH_TOKENS)) {
        continue;
      } else {
        final GenericResourceStat resourceStat = new GenericResourceStat();
        resourceStat.init(r.getName());
        final List<IStat> statsExtended = new ArrayList<>(Arrays.asList(this.statsExtended));
        statsExtended.add(resourceStat);
        this.statsExtended = statsExtended.toArray(new IStat[statsExtended.size()]);
      }
    }
    // add tech related stuff
    if (games.strategy.triplea.Properties.getTechDevelopment(data)) {
      // add tech tokens
      if (data.getResourceList().getResource(Constants.TECH_TOKENS) != null) {
        final List<IStat> statsExtended = new ArrayList<>(Arrays.asList(this.statsExtended));
        statsExtended.add(new TechTokenStat());
        this.statsExtended = statsExtended.toArray(new IStat[statsExtended.size()]);
      }
      // add number of techs
      final List<IStat> techStatsExtended = new ArrayList<>(Arrays.asList(statsExtended));
      techStatsExtended.add(new TechCountStat());
      statsExtended = techStatsExtended.toArray(new IStat[techStatsExtended.size()]);

      // add individual techs
      for (final TechAdvance ta : TechAdvance.getTechAdvances(gameData)) {
        final GenericTechNameStat techNameStat = new GenericTechNameStat();
        techNameStat.init(ta);
        final List<IStat> statsExtended = new ArrayList<>(Arrays.asList(this.statsExtended));
        statsExtended.add(techNameStat);
        this.statsExtended = statsExtended.toArray(new IStat[statsExtended.size()]);
      }
    }
    // now add actual number of each unit type (holy gumdrops batman, this is going to be long!)
    final Iterator<UnitType> allUnitTypes = data.getUnitTypeList().iterator();
    while (allUnitTypes.hasNext()) {
      final UnitType ut = allUnitTypes.next();
      final GenericUnitNameStat unitNameStat = new GenericUnitNameStat();
      unitNameStat.init(ut);
      final List<IStat> statsExtended = new ArrayList<>(Arrays.asList(this.statsExtended));
      statsExtended.add(unitNameStat);
      this.statsExtended = statsExtended.toArray(new IStat[statsExtended.size()]);
    }
  }

  class TechCountStat extends AbstractStat {
    @Override
    public String getName() {
      return "Techs";
    }

    @Override
    public double getValue(final PlayerID player, final GameData data) {
      int count = 0;
      final TechAttachment ta = TechAttachment.get(player);
      if (ta.getHeavyBomber()) {
        count++;
      }
      if (ta.getLongRangeAir()) {
        count++;
      }
      if (ta.getJetPower()) {
        count++;
      }
      if (ta.getRocket()) {
        count++;
      }
      if (ta.getIndustrialTechnology()) {
        count++;
      }
      if (ta.getSuperSub()) {
        count++;
      }
      if (ta.getDestroyerBombard()) {
        count++;
      }
      if (ta.getImprovedArtillerySupport()) {
        count++;
      }
      if (ta.getParatroopers()) {
        count++;
      }
      if (ta.getIncreasedFactoryProduction()) {
        count++;
      }
      if (ta.getWarBonds()) {
        count++;
      }
      if (ta.getMechanizedInfantry()) {
        count++;
      }
      if (ta.getAARadar()) {
        count++;
      }
      if (ta.getShipyards()) {
        count++;
      }
      for (final boolean value : ta.getGenericTech().values()) {
        if (value) {
          count++;
        }
      }
      return count;
    }
  }

  class GenericResourceStat extends AbstractStat {
    private String m_name = null;

    public void init(final String name) {
      m_name = name;
    }

    @Override
    public String getName() {
      return "Resource: " + m_name;
    }

    @Override
    public double getValue(final PlayerID player, final GameData data) {
      return player.getResources().getQuantity(m_name);
    }
  }

  class GenericTechNameStat extends AbstractStat {
    private TechAdvance m_ta = null;

    public void init(final TechAdvance ta) {
      m_ta = ta;
    }

    @Override
    public String getName() {
      return "TechAdvance: " + m_ta.getName();
    }

    @Override
    public double getValue(final PlayerID player, final GameData data) {
      if (m_ta.hasTech(TechAttachment.get(player))) {
        return 1;
      }
      return 0;
    }
  }

  class GenericUnitNameStat extends AbstractStat {
    private UnitType m_ut = null;

    public void init(final UnitType ut) {
      m_ut = ut;
    }

    @Override
    public String getName() {
      return "UnitType: " + m_ut.getName();
    }

    @Override
    public double getValue(final PlayerID player, final GameData data) {
      int matchCount = 0;
      final Match<Unit> ownedBy = Match.all(Matches.unitIsOwnedBy(player), Matches.unitIsOfType(m_ut));
      for (final Territory place : data.getMap().getTerritories()) {
        matchCount += place.getUnits().countMatches(ownedBy);
      }
      return matchCount;
    }
  }

  class TechTokenStat extends ResourceStat {
    public TechTokenStat() {
      super(gameData.getResourceList().getResource(Constants.TECH_TOKENS));
    }
  }

  public IStat[] getStats() {
    return stats;
  }
}
