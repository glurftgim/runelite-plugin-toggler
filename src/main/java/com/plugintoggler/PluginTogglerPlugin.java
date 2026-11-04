package com.plugintoggler;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets you pick any installed plugin, or a group of plugins, and give it a hotkey that turns it
 * on and off. It only reacts to your key presses; it never plays the game for you.
 */
@PluginDescriptor(
	name = "RuneLite Plugin Toggler",
	description = "Turn any plugin, or a group of plugins, on and off with a hotkey, like Entity Hider",
	tags = {"hotkey", "keybind", "toggle", "plugin", "group", "entity hider"}
)
public class PluginTogglerPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(PluginTogglerPlugin.class);
	private static final String CONFIG_GROUP = "plugintoggler";
	private static final String BINDINGS_KEY = "bindings";
	private static final String GROUPS_KEY = "groups";

	@Inject
	private Gson gson;

	@Inject
	private KeyManager keyManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	private final List<Binding> bindings = new ArrayList<>();
	private final List<Group> groups = new ArrayList<>();
	private TogglePanel panel;
	private NavigationButton navButton;

	private final KeyListener keyListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			for (Binding b : bindings)
			{
				if (pressed(b, e))
				{
					String name = b.plugin;
					SwingUtilities.invokeLater(() -> toggleNames(List.of(name)));
					return;
				}
			}
			for (Group g : groups)
			{
				if (pressed(g, e))
				{
					List<String> members = new ArrayList<>(g.plugins);
					SwingUtilities.invokeLater(() -> toggleNames(members));
					return;
				}
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};

	private static boolean pressed(Binding b, KeyEvent e)
	{
		Keybind k = b.keybind();
		if (Keybind.NOT_SET.equals(k) || !k.matches(e))
		{
			return false;
		}
		e.consume();
		return true;
	}

	@Override
	protected void startUp()
	{
		load();
		panel = new TogglePanel(this);
		panel.rebuild(bindings, groups);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("RuneLite Plugin Toggler")
			.icon(ImageUtil.resizeImage(icon, 16, 16))
			.priority(9)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		keyManager.registerKeyListener(keyListener);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(keyListener);
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
	}

	/** Names of installed plugins that can be toggled, minus the ones in {@code exclude}. */
	private Set<String> availableNames(Collection<String> exclude)
	{
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (Plugin p : pluginManager.getPlugins())
		{
			PluginDescriptor d = p.getClass().getAnnotation(PluginDescriptor.class);
			if (d == null || d.hidden() || p == this || exclude.contains(p.getName()))
			{
				continue;
			}
			names.add(p.getName());
		}
		return names;
	}

	// ---- single plugins ----

	void promptAddBinding()
	{
		List<String> taken = new ArrayList<>();
		bindings.forEach(b -> taken.add(b.plugin));
		Set<String> names = availableNames(taken);
		if (names.isEmpty())
		{
			return;
		}
		Object choice = JOptionPane.showInputDialog(panel, "Which plugin?", "Add plugin hotkey",
			JOptionPane.PLAIN_MESSAGE, null, names.toArray(), names.iterator().next());
		if (choice != null)
		{
			bindings.add(new Binding((String) choice));
			changed();
		}
	}

	void removeBinding(Binding b)
	{
		bindings.remove(b);
		changed();
	}

	// ---- groups ----

	void promptAddGroup()
	{
		String name = JOptionPane.showInputDialog(panel, "Name for the group:", "New plugin group",
			JOptionPane.PLAIN_MESSAGE);
		if (name == null || name.trim().isEmpty())
		{
			return;
		}
		Group g = new Group(name.trim());
		groups.add(g);
		changed();
		promptAddToGroup(g);
	}

	void promptAddToGroup(Group g)
	{
		Set<String> names = availableNames(g.plugins);
		if (names.isEmpty())
		{
			return;
		}
		JList<String> list = new JList<>(names.toArray(new String[0]));
		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(260, 260));
		int result = JOptionPane.showConfirmDialog(panel, scroll, "Add plugins to " + g.plugin,
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result == JOptionPane.OK_OPTION && !list.getSelectedValuesList().isEmpty())
		{
			g.plugins.addAll(list.getSelectedValuesList());
			changed();
		}
	}

	void removeFromGroup(Group g, String member)
	{
		g.plugins.remove(member);
		changed();
	}

	void removeGroup(Group g)
	{
		groups.remove(g);
		changed();
	}

	// ---- saving ----

	private void changed()
	{
		saveAll();
		if (panel != null)
		{
			panel.rebuild(bindings, groups);
		}
	}

	void saveAll()
	{
		configManager.setConfiguration(CONFIG_GROUP, BINDINGS_KEY, gson.toJson(bindings));
		configManager.setConfiguration(CONFIG_GROUP, GROUPS_KEY, gson.toJson(groups));
	}

	private void load()
	{
		bindings.clear();
		groups.clear();
		try
		{
			String json = configManager.getConfiguration(CONFIG_GROUP, BINDINGS_KEY);
			if (json != null && !json.isEmpty())
			{
				List<Binding> loaded = gson.fromJson(json, new TypeToken<List<Binding>>()
				{
				}.getType());
				if (loaded != null)
				{
					bindings.addAll(loaded);
				}
			}
			json = configManager.getConfiguration(CONFIG_GROUP, GROUPS_KEY);
			if (json != null && !json.isEmpty())
			{
				List<Group> loaded = gson.fromJson(json, new TypeToken<List<Group>>()
				{
				}.getType());
				if (loaded != null)
				{
					groups.addAll(loaded);
				}
			}
		}
		catch (Exception e)
		{
			log.warn("Could not read saved hotkeys", e);
		}
	}

	// ---- toggling ----

	/**
	 * Flips the named plugins together: if any of them is on they all go off, otherwise they
	 * all go on. For a single plugin that is a plain on/off toggle.
	 */
	private void toggleNames(List<String> names)
	{
		List<Plugin> targets = new ArrayList<>();
		for (String name : names)
		{
			Plugin p = pluginManager.getPlugins().stream()
				.filter(x -> x.getName().equals(name))
				.findFirst()
				.orElse(null);
			if (p == null)
			{
				log.warn("Plugin not found: {}", name);
			}
			else
			{
				targets.add(p);
			}
		}
		if (targets.isEmpty())
		{
			return;
		}

		// Starting or stopping a plugin makes RuneLite's plugin list grab focus for its
		// search box. Remember where focus was (the game) and put it back afterwards.
		Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

		boolean enable = targets.stream().noneMatch(pluginManager::isPluginEnabled);
		for (Plugin target : targets)
		{
			if (pluginManager.isPluginEnabled(target) == enable)
			{
				continue;
			}
			try
			{
				pluginManager.setPluginEnabled(target, enable);
				if (enable)
				{
					pluginManager.startPlugin(target);
				}
				else
				{
					pluginManager.stopPlugin(target);
				}
			}
			catch (Exception e)
			{
				log.warn("Could not toggle {}", target.getName(), e);
			}
		}

		if (focused != null)
		{
			focused.requestFocusInWindow();
			SwingUtilities.invokeLater(focused::requestFocusInWindow);
		}
	}
}
