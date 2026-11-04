package com.plugintoggler;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/** A small flat icon button (arrow, cross, minus) that lights up when the mouse is over it. */
class IconButton extends JComponent
{
	enum Kind
	{
		UP, DOWN, CLOSE, MINUS
	}

	private static final Color IDLE = new Color(0x8f8f8f);
	private static final Color HOVER = Color.WHITE;
	private static final Color DANGER = new Color(0xff6b6b);

	private final Kind kind;
	private boolean hover;

	IconButton(Kind kind, String tip, Runnable action)
	{
		this.kind = kind;
		Dimension size = new Dimension(16, 16);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		setToolTipText(tip);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = false;
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		int cx = getWidth() / 2;
		int cy = getHeight() / 2;
		boolean danger = kind == Kind.CLOSE || kind == Kind.MINUS;
		g2.setColor(hover ? (danger ? DANGER : HOVER) : IDLE);
		g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		switch (kind)
		{
			case UP:
				g2.drawLine(cx - 3, cy + 1, cx, cy - 2);
				g2.drawLine(cx, cy - 2, cx + 3, cy + 1);
				break;
			case DOWN:
				g2.drawLine(cx - 3, cy - 1, cx, cy + 2);
				g2.drawLine(cx, cy + 2, cx + 3, cy - 1);
				break;
			case CLOSE:
				g2.drawLine(cx - 3, cy - 3, cx + 3, cy + 3);
				g2.drawLine(cx + 3, cy - 3, cx - 3, cy + 3);
				break;
			default:
				g2.drawLine(cx - 3, cy, cx + 3, cy);
				break;
		}
		g2.dispose();
	}
}
