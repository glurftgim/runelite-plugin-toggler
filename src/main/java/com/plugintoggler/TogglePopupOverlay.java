package com.plugintoggler;

import java.awt.Color;
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

	private volatile String text;
	private volatile boolean on;
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
		text = message;
		on = enabled;
		hideAt = System.currentTimeMillis() + config.popupSeconds() * 1000L;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.popup())
		{
			return null;
		}

		String shown = text;
		boolean live = shown != null && System.currentTimeMillis() < hideAt;
		boolean sample = !live && client.isKeyPressed(KeyCode.KC_ALT);
		if (!live && !sample)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(sample ? "Plugin Toggler (drag me)" : shown)
			.color(sample ? Color.WHITE : on ? ON : OFF)
			.build());
		return super.render(graphics);
	}
}
