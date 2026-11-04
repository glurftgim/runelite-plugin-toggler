package com.plugintoggler;

import java.util.ArrayList;
import java.util.List;

/** A named set of plugins with one key. The inherited "plugin" field holds the group's name. */
class Group extends Binding
{
	List<String> plugins = new ArrayList<>();

	Group(String name)
	{
		super(name);
	}
}
