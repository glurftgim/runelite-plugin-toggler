package com.plugintoggler;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("plugintoggler")
public interface PluginTogglerConfig extends Config
{
	@ConfigItem(
		keyName = "chatMessages",
		name = "Chat message",
		description = "Say in the game chat which plugin or group a hotkey turned on or off",
		position = 1
	)
	default boolean chatMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "popup",
		name = "On-screen popup",
		description = "Show a small text on screen for a few seconds saying what a hotkey turned on or off. "
			+ "Hold Alt to drag it.",
		position = 2
	)
	default boolean popup()
	{
		return true;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "popupSeconds",
		name = "Popup seconds",
		description = "How long the on-screen popup stays up",
		position = 3
	)
	default int popupSeconds()
	{
		return 3;
	}
}
