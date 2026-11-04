package com.plugintoggler;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * A small text that appears for a few seconds saying what a hotkey turned on or off.
 * Hold Alt to drag it; while Alt is held a sample shows so there is something to grab.
 */
class TogglePopupOverlay extends OverlayPanel
{
	private static final Color ON = new Color(0x5fe08a);
	private static final Color OFF = new Color(0xff6b6b);

	private final Client client;
	private final PluginTogglerConfig config;

	private static final long FADE_IN_MS = 180;
	private static final long FADE_OUT_MS = 450;

	private volatile String text;
	private volatile boolean on;
	private volatile long shownAt;
	private volatile long hideAt;

	@Inject
	TogglePopupOverlay(Client client, PluginTogglerConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	void show(String message, boolean enabled)
	{
		long now = System.currentTimeMillis();
		// a popup that is already up just changes its text; it doesn't fade in all over again
		if (text == null || now >= hideAt)
		{
			shownAt = now;
		}
		text = message;
		on = enabled;
		hideAt = now + Math.max(config.popupSeconds() * 1000L, FADE_IN_MS + FADE_OUT_MS);
	}

	/** 0 to 1: eases in over the first moments and out over the last. */
	private float opacity(long now)
	{
		float in = Math.min(1f, (now - shownAt) / (float) FADE_IN_MS);
		float out = Math.min(1f, (hideAt - now) / (float) FADE_OUT_MS);
		float t = Math.max(0f, Math.min(in, out));
		return t * t * (3f - 2f * t);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.popup())
		{
			return null;
		}

		long now = System.currentTimeMillis();
		String shown = text;
		boolean live = shown != null && now < hideAt;
		boolean sample = !live && client.isKeyPressed(KeyCode.KC_ALT);
		if (!live && !sample)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(sample ? "Plugin Toggler (drag me)" : shown)
			.color(sample ? Color.WHITE : on ? ON : OFF)
			.build());

		Composite before = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, sample ? 1f : opacity(now)));
		try
		{
			return super.render(graphics);
		}
		finally
		{
			graphics.setComposite(before);
		}
	}
}
