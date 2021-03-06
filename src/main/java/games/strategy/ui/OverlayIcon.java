package games.strategy.ui;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;

/**
 * Make one icon from two.
 */
public class OverlayIcon implements Icon {
  private final Icon m_back;
  private final Icon m_front;
  private final int m_x_offset;
  private final int m_y_offset;

  /**
   * Create a composite icon by overlaying the front icon over the back icon.
   *
   * @param back
   *        back icon
   * @param front
   *        front icon
   * @param x
   *        , y position of front icon relative to back icon.
   */
  public OverlayIcon(final Icon back, final Icon front, final int x, final int y) {
    m_back = back;
    m_front = front;
    m_x_offset = x;
    m_y_offset = y;
  }

  @Override
  public int getIconHeight() {
    return m_back.getIconHeight() > (m_front.getIconHeight() + m_y_offset) ? m_back.getIconHeight()
        : (m_front.getIconHeight() + m_y_offset);
  }

  @Override
  public int getIconWidth() {
    return m_back.getIconWidth() > (m_front.getIconWidth() + m_x_offset) ? m_back.getIconWidth()
        : (m_front.getIconWidth() + m_x_offset);
  }

  @Override
  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    m_back.paintIcon(c, g, x, y);
    m_front.paintIcon(c, g, x + m_x_offset, y + m_y_offset);
  }
}
