package games.strategy.triplea.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.PlayerID;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import games.strategy.util.CountDownLatchHandler;
import games.strategy.util.EventThreadJOptionPane;
import games.strategy.util.Match;
import games.strategy.util.Tuple;

/**
 * For choosing territories and units for them, during RandomStartDelegate.
 */
public class PickTerritoryAndUnitsPanel extends ActionPanel {
  private static final long serialVersionUID = -2672163347536778594L;
  private final TripleAFrame parent;
  private final JLabel actionLabel = new JLabel();
  private JButton doneButton = null;
  private JButton selectTerritoryButton = null;
  private JButton selectUnitsButton = null;
  private Territory pickedTerritory = null;
  private Set<Unit> pickedUnits = new HashSet<>();
  private List<Territory> territoryChoices = null;
  private List<Unit> unitChoices = null;
  private int unitsPerPick = 1;
  private Action currentAction = null;
  private Territory currentHighlightedTerritory = null;

  public PickTerritoryAndUnitsPanel(final GameData data, final MapPanel map, final TripleAFrame parent) {
    super(data, map);
    this.parent = parent;
  }

  @Override
  public String toString() {
    return "Pick Territory and Units";
  }

  @Override
  public void display(final PlayerID id) {
    super.display(id);
    pickedTerritory = null;
    pickedUnits = new HashSet<>();
    currentAction = null;
    currentHighlightedTerritory = null;
    SwingUtilities.invokeLater(() -> {
      removeAll();
      actionLabel.setText(id.getName() + " Pick Territory and Units");
      add(actionLabel);
      selectTerritoryButton = new JButton(SelectTerritoryAction);
      add(selectTerritoryButton);
      selectUnitsButton = new JButton(SelectUnitsAction);
      add(selectUnitsButton);
      doneButton = new JButton(DoneAction);
      add(doneButton);
      SwingUtilities.invokeLater(() -> selectTerritoryButton.requestFocusInWindow());
    });
  }

  Tuple<Territory, Set<Unit>> waitForPickTerritoryAndUnits(final List<Territory> territoryChoices,
      final List<Unit> unitChoices, final int unitsPerPick) {
    this.territoryChoices = territoryChoices;
    this.unitChoices = unitChoices;
    this.unitsPerPick = unitsPerPick;
    if (currentHighlightedTerritory != null) {
      getMap().clearTerritoryOverlay(currentHighlightedTerritory);
      currentHighlightedTerritory = null;
    }
    if (territoryChoices.size() == 1) {
      pickedTerritory = territoryChoices.get(0);
      currentHighlightedTerritory = pickedTerritory;
      getMap().setTerritoryOverlay(currentHighlightedTerritory, Color.WHITE, 200);
    }
    SwingUtilities.invokeLater(() -> {
      if (territoryChoices.size() > 1) {
        SelectTerritoryAction.actionPerformed(null);
      } else if (unitChoices.size() > 1) {
        SelectUnitsAction.actionPerformed(null);
      }
    });
    waitForRelease();
    return Tuple.of(this.pickedTerritory, this.pickedUnits);
  }

  private void setWidgetActivation() {
    SwingUtilities.invokeLater(() -> {
      if (!getActive()) {
        // current turn belongs to remote player or AI player
        DoneAction.setEnabled(false);
        SelectUnitsAction.setEnabled(false);
        SelectTerritoryAction.setEnabled(false);
      } else {
        DoneAction.setEnabled(currentAction == null);
        SelectUnitsAction.setEnabled(currentAction == null);
        SelectTerritoryAction.setEnabled(currentAction == null);
      }
    });
  }

  private final Action DoneAction = new AbstractAction("Done") {
    private static final long serialVersionUID = -2376988913511268803L;

    @Override
    public void actionPerformed(final ActionEvent event) {
      currentAction = DoneAction;
      setWidgetActivation();
      if (pickedTerritory == null || !territoryChoices.contains(pickedTerritory)) {
        EventThreadJOptionPane.showMessageDialog(parent, "Must Pick An Unowned Territory",
            "Must Pick An Unowned Territory", JOptionPane.WARNING_MESSAGE, new CountDownLatchHandler(true));
        currentAction = null;
        if (currentHighlightedTerritory != null) {
          getMap().clearTerritoryOverlay(currentHighlightedTerritory);
        }
        currentHighlightedTerritory = null;
        pickedTerritory = null;
        setWidgetActivation();
        return;
      }
      if (!pickedUnits.isEmpty() && !unitChoices.containsAll(pickedUnits)) {
        EventThreadJOptionPane.showMessageDialog(parent, "Invalid Units?!?", "Invalid Units?!?",
            JOptionPane.WARNING_MESSAGE, new CountDownLatchHandler(true));
        currentAction = null;
        pickedUnits.clear();
        setWidgetActivation();
        return;
      }
      if (pickedUnits.size() > Math.max(0, unitsPerPick)) {
        EventThreadJOptionPane.showMessageDialog(parent, "Too Many Units?!?", "Too Many Units?!?",
            JOptionPane.WARNING_MESSAGE, new CountDownLatchHandler(true));
        currentAction = null;
        pickedUnits.clear();
        setWidgetActivation();
        return;
      }
      if (pickedUnits.size() < unitsPerPick) {
        if (unitChoices.size() < unitsPerPick) {
          // if we have fewer units than the number we are supposed to pick, set it to all
          pickedUnits.addAll(unitChoices);
        } else if (Match.allMatch(unitChoices, Matches.unitIsOfType(unitChoices.get(0).getType()))) {
          // if we have only 1 unit type, set it to that
          pickedUnits.clear();
          pickedUnits.addAll(Match.getNMatches(unitChoices, unitsPerPick, Match.always()));
        } else {
          EventThreadJOptionPane.showMessageDialog(parent, "Must Choose Units For This Territory",
              "Must Choose Units For This Territory", JOptionPane.WARNING_MESSAGE, new CountDownLatchHandler(true));
          currentAction = null;
          setWidgetActivation();
          return;
        }
      }
      currentAction = null;
      if (currentHighlightedTerritory != null) {
        getMap().clearTerritoryOverlay(currentHighlightedTerritory);
      }
      currentHighlightedTerritory = null;
      setWidgetActivation();
      release();
    }
  };
  private final Action SelectUnitsAction = new AbstractAction("Select Units") {
    private static final long serialVersionUID = 4745335350716395600L;

    @Override
    public void actionPerformed(final ActionEvent event) {
      currentAction = SelectUnitsAction;
      setWidgetActivation();
      final UnitChooser unitChooser = new UnitChooser(unitChoices, Collections.emptyMap(),
          getData(), false, getMap().getUIContext());
      unitChooser.setMaxAndShowMaxButton(unitsPerPick);
      if (JOptionPane.OK_OPTION == EventThreadJOptionPane.showConfirmDialog(parent, unitChooser, "Select Units",
          JOptionPane.OK_CANCEL_OPTION, new CountDownLatchHandler(true))) {
        pickedUnits.clear();
        pickedUnits.addAll(unitChooser.getSelected());
      }
      currentAction = null;
      setWidgetActivation();
    }
  };
  private final Action SelectTerritoryAction = new AbstractAction("Select Territory") {
    private static final long serialVersionUID = -8003634505955439651L;

    @Override
    public void actionPerformed(final ActionEvent event) {
      currentAction = SelectTerritoryAction;
      setWidgetActivation();
      getMap().addMapSelectionListener(MAP_SELECTION_LISTENER);
    }
  };
  private final MapSelectionListener MAP_SELECTION_LISTENER = new DefaultMapSelectionListener() {
    @Override
    public void territorySelected(final Territory territory, final MouseDetails md) {
      if (territory == null) {
        return;
      }
      if (currentAction == SelectTerritoryAction) {
        if (territory == null || !territoryChoices.contains(territory)) {
          EventThreadJOptionPane.showMessageDialog(parent,
              "Must Pick An Unowned Territory (will have a white highlight)", "Must Pick An Unowned Territory",
              JOptionPane.WARNING_MESSAGE, new CountDownLatchHandler(true));
          return;
        }
        pickedTerritory = territory;
        SwingUtilities.invokeLater(() -> {
          getMap().removeMapSelectionListener(MAP_SELECTION_LISTENER);
          currentAction = null;
          setWidgetActivation();
        });
      } else {
        System.err.println("Should not be able to select a territory outside of the SelectTerritoryAction.");
      }
    }

    @Override
    public void mouseMoved(final Territory territory, final MouseDetails md) {
      if (!getActive()) {
        System.err.println("Should not be able to select a territory when inactive.");
        return;
      }
      if (territory != null) {
        // highlight territory
        if (currentAction == SelectTerritoryAction) {
          if (currentHighlightedTerritory != territory) {
            if (currentHighlightedTerritory != null) {
              getMap().clearTerritoryOverlay(currentHighlightedTerritory);
            }
            currentHighlightedTerritory = territory;
            if (territoryChoices.contains(currentHighlightedTerritory)) {
              getMap().setTerritoryOverlay(currentHighlightedTerritory, Color.WHITE, 200);
            } else {
              getMap().setTerritoryOverlay(currentHighlightedTerritory, Color.RED, 200);
            }
            getMap().repaint();
          }
        }
      }
    }
  };
}
