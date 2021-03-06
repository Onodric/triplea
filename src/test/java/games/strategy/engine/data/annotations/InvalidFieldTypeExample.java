package games.strategy.engine.data.annotations;

import games.strategy.engine.data.Attachable;
import games.strategy.engine.data.DefaultAttachment;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameParseException;
import games.strategy.engine.data.UnitType;
import games.strategy.util.IntegerMap;

/**
 * Class with an invalid return for an adder (the return type must be an integerMap).
 */
public class InvalidFieldTypeExample extends DefaultAttachment {
  private static final long serialVersionUID = 6465866180845982327L;

  protected InvalidFieldTypeExample(final String name, final Attachable attachable, final GameData gameData) {
    super(name, attachable, gameData);
  }

  @SuppressWarnings("unused")
  // this should be an integermap, since that is what we are returning. should cause test to fail.
  private String m_givesMovement;

  @GameProperty(xmlProperty = true, gameProperty = true, adds = true)
  public void setGivesMovement(final String value) {}

  public void resetGivesMovement() {}

  public void clearMovement() {}

  public IntegerMap<UnitType> getGivesMovement() {
    return new IntegerMap<>();
  }

  @Override
  public void validate(final GameData data) throws GameParseException {}
}
