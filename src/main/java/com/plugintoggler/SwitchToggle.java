package com.plugintoggler;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JComponent;

/** An ON/OFF switch: a pill with a knob that slides and the word for the current side. Green when on. */
class SwitchToggle extends JComponent
{
	private static final Color ON = new Color(0x3fbf6a);
	private static final Color OFF = new Color(0x5a5a5a);

	private boolean on;

	SwitchToggle(boolean on, Consumer<Boolean> onChange)
	{
		this.on = on;
		Dimension size = new Dimension(54, 22);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				SwitchToggle.this.on = !SwitchToggle.this.on;
				repaint();
				onChange.accept(SwitchToggle.this.on);
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		g2.setColor(on ? ON : OFF);
		g2.fillRoundRect(0, 0, w, h, h, h);

		// the knob sits at one end, the word in the space left over at the other
		int d = h - 6;
		g2.setColor(Color.WHITE);
		g2.fillOval(on ? w - d - 3 : 3, 3, d, d);

		g2.setFont(getFont().deriveFont(Font.BOLD, 10f));
		FontMetrics fm = g2.getFontMetrics();
		String word = on ? "ON" : "OFF";
		int areaX = on ? 0 : h - 2;
		int areaW = w - h + 2;
		g2.setColor(on ? Color.WHITE : new Color(0xcfcfcf));
		g2.drawString(word, areaX + (areaW - fm.stringWidth(word)) / 2,
			(h - fm.getHeight()) / 2 + fm.getAscent());
		g2.dispose();
	}
}
