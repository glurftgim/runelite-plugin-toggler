package com.plugintoggler;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
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

	/** The text form of everything, used by Export and Import. */
	static class Backup
	{
		List<Binding> bindings;
		List<Group> groups;
	}

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

	@Inject
	private PluginTogglerConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TogglePopupOverlay popupOverlay;

	@Provides
	PluginTogglerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PluginTogglerConfig.class);
	}

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
			Binding b = pressedEntry(e);
			if (b == null)
			{
				return;
			}
			e.consume();
			List<String> names = namesOf(b);
			String label = labelOf(b);
			boolean sessionOnly = b.sessionOnly;
			Component source = e.getComponent();
			SwingUtilities.invokeLater(() -> toggleNames(names, label, sessionOnly, source));
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};

	private static boolean matches(Binding b, KeyEvent e)
	{
		Keybind k = b.keybind();
		return !b.paused && !Keybind.NOT_SET.equals(k) && k.matches(e);
	}

	/** The entry whose key this is, if any. Single plugins win over groups. */
	private Binding pressedEntry(KeyEvent e)
	{
		for (Binding b : bindings)
		{
			if (matches(b, e))
			{
				return b;
			}
		}
		for (Group g : groups)
		{
			if (matches(g, e))
			{
				return g;
			}
		}
		return null;
	}

	private static List<String> namesOf(Binding b)
	{
		return b instanceof Group ? new ArrayList<>(((Group) b).plugins) : List.of(b.plugin);
	}

	/** The group's name, or null for a single plugin. */
	private static String labelOf(Binding b)
	{
		return b instanceof Group ? b.plugin : null;
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
		overlayManager.add(popupOverlay);
		keyManager.registerKeyListener(keyListener);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(keyListener);
		overlayManager.remove(popupOverlay);
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.refreshStates();
			}
		});
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

	private Plugin find(String name)
	{
		return pluginManager.getPlugins().stream()
			.filter(x -> x.getName().equals(name))
			.findFirst()
			.orElse(null);
	}

	// ---- editing the lists (all called from the panel, on the Swing thread) ----

	void promptAddBinding()
	{
		List<String> taken = new ArrayList<>();
		bindings.forEach(b -> taken.add(b.plugin));
		Set<String> names = availableNames(taken);
		if (names.isEmpty())
		{
			return;
		}
		List<String> picked = PluginPicker.pick(panel, "Add plugin hotkey", names, false);
		if (!picked.isEmpty())
		{
			bindings.add(new Binding(picked.get(0)));
			changed();
		}
	}

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
		List<String> picked = PluginPicker.pick(panel, "Add plugins to " + g.plugin, names, true);
		if (!picked.isEmpty())
		{
			g.plugins.addAll(picked);
			changed();
		}
	}

	void removeFromGroup(Group g, String member)
	{
		g.plugins.remove(member);
		changed();
	}

	void removeEntry(Binding b)
	{
		if (b instanceof Group)
		{
			groups.remove(b);
		}
		else
		{
			bindings.remove(b);
		}
		changed();
	}

	void moveEntry(Binding b, int delta)
	{
		List<? extends Binding> list = b instanceof Group ? groups : bindings;
		int from = list.indexOf(b);
		int to = from + delta;
		if (from < 0 || to < 0 || to >= list.size())
		{
			return;
		}
		Collections.swap(list, from, to);
		changed();
	}

	void setSessionOnly(Binding b, boolean sessionOnly)
	{
		b.sessionOnly = sessionOnly;
		saveAll();
	}

	/**
	 * The ON/OFF switch on a row. ON turns the plugin(s) on and lets the key work; OFF turns them
	 * off and makes the key do nothing.
	 */
	void setEntryOn(Binding b, boolean on)
	{
		b.paused = !on;
		saveAll();
		toggleNames(namesOf(b), labelOf(b), b.sessionOnly, on, null);
		changed();
	}

	/** 1 all on, 0 all off, 2 some on, -1 none of its plugins are installed. */
	int stateOf(Binding b)
	{
		int on = 0;
		int total = 0;
		for (String name : namesOf(b))
		{
			Plugin p = find(name);
			if (p == null)
			{
				continue;
			}
			total++;
			if (pluginManager.isPluginActive(p))
			{
				on++;
			}
		}
		return total == 0 ? -1 : on == 0 ? 0 : on == total ? 1 : 2;
	}

	// ---- export and import ----

	void exportDialog()
	{
		Backup backup = new Backup();
		backup.bindings = bindings;
		backup.groups = groups;
		JTextArea area = new JTextArea(gson.toJson(backup), 10, 28);
		area.setLineWrap(true);
		area.setEditable(false);
		area.selectAll();
		JPanel body = new JPanel(new BorderLayout(0, 6));
		body.add(new JLabel("Copy this text (Ctrl+C) and keep it somewhere safe."), BorderLayout.NORTH);
		body.add(new JScrollPane(area), BorderLayout.CENTER);
		JOptionPane.showMessageDialog(panel, body, "Export hotkeys", JOptionPane.PLAIN_MESSAGE);
	}

	void importDialog()
	{
		JTextArea area = new JTextArea(10, 28);
		area.setLineWrap(true);
		JPanel body = new JPanel(new BorderLayout(0, 6));
		body.add(new JLabel("<html>Paste exported text below.<br>This replaces your current hotkeys and groups.</html>"),
			BorderLayout.NORTH);
		body.add(new JScrollPane(area), BorderLayout.CENTER);
		int result = JOptionPane.showConfirmDialog(panel, body, "Import hotkeys", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
		{
			return;
		}

		try
		{
			Backup backup = gson.fromJson(area.getText().trim(), Backup.class);
			if (backup == null)
			{
				throw new IllegalArgumentException("empty");
			}
			List<Binding> newBindings = new ArrayList<>();
			if (backup.bindings != null)
			{
				for (Binding b : backup.bindings)
				{
					if (b != null && b.plugin != null)
					{
						newBindings.add(b);
					}
				}
			}
			List<Group> newGroups = new ArrayList<>();
			if (backup.groups != null)
			{
				for (Group g : backup.groups)
				{
					if (g != null && g.plugin != null)
					{
						if (g.plugins == null)
						{
							g.plugins = new ArrayList<>();
						}
						newGroups.add(g);
					}
				}
			}
			bindings.clear();
			bindings.addAll(newBindings);
			groups.clear();
			groups.addAll(newGroups);
			changed();
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(panel, "That doesn't look like exported hotkeys.", "Import failed",
				JOptionPane.ERROR_MESSAGE);
		}
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
					for (Group g : loaded)
					{
						if (g.plugins == null)
						{
							g.plugins = new ArrayList<>();
						}
					}
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
	 * all go on. For a single plugin that is a plain on/off toggle. Unless {@code sessionOnly}, the
	 * result is saved in RuneLite so it is the same next session; with it, plugins are only stopped
	 * or started for now (so one switched off in the plugin list can't be turned on this way).
	 */
	private void toggleNames(List<String> names, String groupName, boolean sessionOnly, Component source)
	{
		toggleNames(names, groupName, sessionOnly, null, source);
	}

	/** As above, but with {@code forced} set the plugins are all put in that state instead of flipped. */
	private void toggleNames(List<String> names, String groupName, boolean sessionOnly, Boolean forced,
		Component source)
	{
		List<Plugin> targets = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		List<String> failed = new ArrayList<>();
		List<String> blocked = new ArrayList<>();
		for (String name : names)
		{
			Plugin p = find(name);
			if (p == null)
			{
				log.warn("Plugin not found: {}", name);
				missing.add(name);
			}
			else
			{
				targets.add(p);
			}
		}
		if (targets.isEmpty())
		{
			say(groupName == null ? "couldn't find " + String.join(", ", missing)
				: groupName + " group has no plugins to toggle.", null, false);
			return;
		}

		boolean enable = forced != null ? forced : targets.stream().noneMatch(pluginManager::isPluginActive);
		List<String> changed = new ArrayList<>();
		for (Plugin target : targets)
		{
			if (pluginManager.isPluginActive(target) == enable)
			{
				continue;
			}
			if (sessionOnly && enable && !pluginManager.isPluginEnabled(target))
			{
				// RuneLite won't start a plugin that is switched off in its plugin list
				blocked.add(target.getName());
				continue;
			}
			if (setEnabled(target, enable, sessionOnly))
			{
				changed.add(target.getName());
			}
			else
			{
				failed.add(target.getName());
			}
		}

		String state = enable ? "on" : "off";
		String forNow = sessionOnly ? " (this session only)" : "";
		StringBuilder msg = new StringBuilder();
		if (groupName == null)
		{
			if (!changed.isEmpty())
			{
				msg.append(changed.get(0)).append(" turned ").append(state).append(forNow).append(".");
			}
		}
		else
		{
			msg.append(groupName).append(" group turned ").append(state).append(forNow);
			msg.append(changed.isEmpty() ? "." : ": " + String.join(", ", changed) + ".");
		}
		if (!blocked.isEmpty())
		{
			msg.append(" ").append(String.join(", ", blocked))
				.append(" is switched off in the plugin list, so it can't be turned on for this session only.");
		}
		if (!failed.isEmpty())
		{
			msg.append(" Couldn't toggle ").append(String.join(", ", failed)).append(".");
		}
		if (!missing.isEmpty())
		{
			msg.append(" Couldn't find ").append(String.join(", ", missing)).append(".");
		}
		// short text for the on-screen popup: "Entity Hider ON" or "Raiding group OFF (3)"
		String popupText = null;
		if (!changed.isEmpty())
		{
			String word = enable ? "ON" : "OFF";
			popupText = groupName == null ? changed.get(0) + " " + word
				: groupName + " group " + word + " (" + changed.size() + ")";
		}
		say(msg.toString().trim(), popupText, enable);
		refocus(source);
	}

	private boolean setEnabled(Plugin target, boolean enable, boolean sessionOnly)
	{
		try
		{
			if (!sessionOnly)
			{
				pluginManager.setPluginEnabled(target, enable);
			}
			if (enable)
			{
				pluginManager.startPlugin(target);
			}
			else
			{
				pluginManager.stopPlugin(target);
			}
			return true;
		}
		catch (Exception e)
		{
			log.warn("Could not toggle {}", target.getName(), e);
			return false;
		}
	}

	/**
	 * Starting or stopping a plugin makes RuneLite's plugin list grab focus for its search box.
	 * Hand focus back to where the key press came from (the game).
	 */
	private static void refocus(Component source)
	{
		if (source != null)
		{
			source.requestFocusInWindow();
			SwingUtilities.invokeLater(source::requestFocusInWindow);
		}
	}

	/** Tells the player what happened: in the game chat and/or in the on-screen popup, per the settings. */
	private void say(String chatText, String popupText, boolean enabled)
	{
		if (popupText != null && config.popup())
		{
			popupOverlay.show(popupText, enabled);
		}
		if (chatText.isEmpty() || !config.chatMessages())
		{
			return;
		}
		clientThread.invokeLater(() -> chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage("Plugin Toggler: " + chatText)
			.build()));
	}
}
