package com.plugintoggler;

import net.runelite.client.config.Keybind;

/** One plugin and the key that toggles it. */
class Binding
{
	String plugin;
	int keyCode;
	int modifiers;

	Binding(String plugin)
	{
		this.plugin = plugin;
		this.keyCode = Keybind.NOT_SET.getKeyCode();
		this.modifiers = Keybind.NOT_SET.getModifiers();
	}

	Keybind keybind()
	{
		return new Keybind(keyCode, modifiers);
	}

	void setKeybind(Keybind k)
	{
		keyCode = k.getKeyCode();
		modifiers = k.getModifiers();
	}
}
