package com.plugintoggler;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches RuneLite with the plugin loaded, for development. */
public class PluginTogglerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PluginTogglerPlugin.class);
		RuneLite.main(args);
	}
}
