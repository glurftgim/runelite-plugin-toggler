package com.plugintoggler;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Side panel with two tabs: single plugins, and groups of plugins. Each gets a key. */
class TogglePanel extends PluginPanel
{
	private static final Color ON = new Color(0x5fe08a);
	private static final Color OFF = new Color(0xff6b6b);
	private static final Color MIXED = new Color(0xf5c542);
	private static final String REMEMBER = "Remember across sessions";
	private static final String SESSION_ONLY = "This session only";

	private final PluginTogglerPlugin plugin;
	private final JPanel pluginRows = column();
	private final JPanel groupRows = column();
	/** One refresher per visible row, run whenever a plugin starts or stops. */
	private final List<Runnable> stateUpdaters = new ArrayList<>();

	TogglePanel(PluginTogglerPlugin plugin)
	{
		super(false);
		this.plugin = plugin;
		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Plugins", tab("Plugin hotkeys", "Pick a plugin to toggle with a key",
			plugin::promptAddBinding, pluginRows));
		tabs.addTab("Groups", tab("Plugin groups", "Make a group of plugins with one key",
			plugin::promptAddGroup, groupRows));
		add(tabs, BorderLayout.CENTER);

		JButton export = new JButton("Export");
		export.setToolTipText("Copy your hotkeys and groups as text");
		export.setFocusable(false);
		export.addActionListener(e -> plugin.exportDialog());
		JButton importer = new JButton("Import");
		importer.setToolTipText("Paste exported text to replace your hotkeys and groups");
		importer.setFocusable(false);
		importer.addActionListener(e -> plugin.importDialog());
		JPanel bar = new JPanel(new GridLayout(1, 2, 6, 0));
		bar.setOpaque(false);
		bar.add(export);
		bar.add(importer);
		add(bar, BorderLayout.SOUTH);
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		return p;
	}

	private static JPanel tab(String titleText, String addTip, Runnable onAdd, JPanel rows)
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		JLabel title = new JLabel(titleText);
		title.setForeground(Color.WHITE);
		JButton add = new JButton("+");
		add.setToolTipText(addTip);
		add.setFocusable(false);
		add.addActionListener(e -> onAdd.run());
		header.add(title, BorderLayout.WEST);
		header.add(add, BorderLayout.EAST);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(rows, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(north);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		JPanel tab = new JPanel(new BorderLayout(0, 8));
		tab.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
		tab.setOpaque(false);
		tab.add(header, BorderLayout.NORTH);
		tab.add(scroll, BorderLayout.CENTER);
		return tab;
	}

	void rebuild(List<Binding> bindings, List<Group> groups)
	{
		stateUpdaters.clear();
		fill(pluginRows, bindings.size(), "Click + to add a plugin.");
		for (Binding b : bindings)
		{
			pluginRows.add(entryCard(b, null));
			pluginRows.add(Box.createRigidArea(new Dimension(0, 6)));
		}
		fill(groupRows, groups.size(), "Click + to make a group.");
		for (Group g : groups)
		{
			groupRows.add(entryCard(g, memberList(g)));
			groupRows.add(Box.createRigidArea(new Dimension(0, 6)));
		}
		for (JPanel p : new JPanel[]{pluginRows, groupRows})
		{
			p.revalidate();
			p.repaint();
		}
	}

	/** Re-reads which plugins are on, to recolor the dots. Called when any plugin starts or stops. */
	void refreshStates()
	{
		for (Runnable r : new ArrayList<>(stateUpdaters))
		{
			r.run();
		}
	}

	private static void fill(JPanel rows, int count, String emptyHint)
	{
		rows.removeAll();
		if (count == 0)
		{
			JLabel hint = new JLabel(emptyHint);
			hint.setForeground(Color.LIGHT_GRAY);
			rows.add(hint);
		}
	}

	/** One card: dot and name with move/delete buttons, the middle part, the key, and the options. */
	private JPanel entryCard(Binding b, JComponent middle)
	{
		JPanel card = new JPanel(new BorderLayout(6, 6));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

		JLabel dot = new JLabel("●");
		Runnable update = () ->
		{
			int state = plugin.stateOf(b);
			dot.setForeground(state == 1 ? ON : state == 0 ? OFF : state == 2 ? MIXED : Color.GRAY);
			dot.setToolTipText(state == 1 ? "On" : state == 0 ? "Off" : state == 2 ? "Some are on" : "Not installed");
		};
		stateUpdaters.add(update);
		update.run();

		JLabel name = new JLabel(b.plugin);
		name.setForeground(b.paused ? Color.GRAY : Color.WHITE);
		name.setToolTipText(b.paused ? "Off: its key does nothing" : null);

		JPanel title = new JPanel(new BorderLayout(6, 0));
		title.setOpaque(false);
		title.add(dot, BorderLayout.WEST);
		title.add(name, BorderLayout.CENTER);

		JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		tools.setOpaque(false);
		tools.add(new IconButton(IconButton.Kind.UP, "Move up", () -> plugin.moveEntry(b, -1)));
		tools.add(new IconButton(IconButton.Kind.DOWN, "Move down", () -> plugin.moveEntry(b, 1)));
		tools.add(new IconButton(IconButton.Kind.CLOSE, b instanceof Group ? "Delete group" : "Remove",
			() -> plugin.removeEntry(b)));

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		top.add(title, BorderLayout.CENTER);
		top.add(tools, BorderLayout.EAST);

		JPanel body = column();
		if (middle != null)
		{
			body.add(left(middle));
			body.add(Box.createRigidArea(new Dimension(0, 6)));
		}
		body.add(left(keyRow(b)));
		body.add(Box.createRigidArea(new Dimension(0, 4)));
		body.add(left(options(b)));

		card.add(top, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private static <T extends JComponent> T left(T c)
	{
		c.setAlignmentX(LEFT_ALIGNMENT);
		return c;
	}

	private JPanel memberList(Group g)
	{
		JPanel middle = new JPanel(new BorderLayout(0, 4));
		middle.setOpaque(false);

		JPanel list = column();
		if (g.plugins.isEmpty())
		{
			JLabel none = new JLabel("No plugins yet");
			none.setForeground(Color.LIGHT_GRAY);
			list.add(none);
		}
		for (String member : g.plugins)
		{
			JPanel line = new JPanel(new BorderLayout(4, 0));
			line.setOpaque(false);
			JLabel l = new JLabel(member);
			l.setForeground(Color.LIGHT_GRAY);
			line.add(l, BorderLayout.CENTER);
			line.add(new IconButton(IconButton.Kind.MINUS, "Take out of the group",
				() -> plugin.removeFromGroup(g, member)), BorderLayout.EAST);
			list.add(line);
		}

		JButton addPlugins = new JButton("+ Add plugins");
		addPlugins.setFocusable(false);
		addPlugins.addActionListener(e -> plugin.promptAddToGroup(g));
		JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		addRow.setOpaque(false);
		addRow.add(addPlugins);

		middle.add(list, BorderLayout.NORTH);
		middle.add(addRow, BorderLayout.CENTER);
		return middle;
	}

	/** The click-then-press key button, with a button beside it to toggle with the mouse. */
	private JPanel keyRow(Binding b)
	{
		JButton key = new JButton(label(b.keybind()));
		key.setToolTipText("Click, then press a key. Esc clears it.");
		key.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				key.setText("Press a key...");
				key.requestFocusInWindow();
			}
		});
		key.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				int code = e.getKeyCode();
				if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL
					|| code == KeyEvent.VK_ALT || code == KeyEvent.VK_META)
				{
					return;
				}
				b.setKeybind(code == KeyEvent.VK_ESCAPE ? Keybind.NOT_SET : new Keybind(e));
				key.setText(label(b.keybind()));
				plugin.saveAll();
				e.consume();
			}
		});

		// ON turns the plugin on and enables the key; OFF turns the plugin off and disables the key
		SwitchToggle power = new SwitchToggle(!b.paused, on -> plugin.setEntryOn(b, on));
		power.setToolTipText("<html>ON: turns it on, and its key works.<br>OFF: turns it off, and its key does nothing.</html>");

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(key, BorderLayout.CENTER);
		row.add(power, BorderLayout.EAST);
		return row;
	}

	/** Whether the on/off state is kept after a restart or only for this session. */
	private JPanel options(Binding b)
	{
		JComboBox<String> mode = new JComboBox<>(new String[]{REMEMBER, SESSION_ONLY});
		mode.setSelectedItem(b.sessionOnly ? SESSION_ONLY : REMEMBER);
		mode.setFocusable(false);
		mode.setToolTipText("<html>" + REMEMBER + ": keeps its on/off state after a restart.<br>"
			+ SESSION_ONLY + ": reverts after a restart.</html>");
		mode.addActionListener(e -> plugin.setSessionOnly(b, SESSION_ONLY.equals(mode.getSelectedItem())));

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(mode, BorderLayout.CENTER);
		return row;
	}

	private static String label(Keybind k)
	{
		return Keybind.NOT_SET.equals(k) ? "Not set" : k.toString();
	}
}
