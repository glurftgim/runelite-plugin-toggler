package com.plugintoggler;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
	private final PluginTogglerPlugin plugin;
	private final JPanel pluginRows = column();
	private final JPanel groupRows = column();

	TogglePanel(PluginTogglerPlugin plugin)
	{
		super(false);
		this.plugin = plugin;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Plugins", tab("Plugin hotkeys", "Pick a plugin to toggle with a key",
			plugin::promptAddBinding, pluginRows));
		tabs.addTab("Groups", tab("Plugin groups", "Make a group of plugins with one key",
			plugin::promptAddGroup, groupRows));
		add(tabs, BorderLayout.CENTER);
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
		fill(pluginRows, bindings.size(), "Click + to add a plugin.");
		for (Binding b : bindings)
		{
			pluginRows.add(pluginRow(b));
			pluginRows.add(Box.createRigidArea(new Dimension(0, 6)));
		}
		fill(groupRows, groups.size(), "Click + to make a group.");
		for (Group g : groups)
		{
			groupRows.add(groupRow(g));
			groupRows.add(Box.createRigidArea(new Dimension(0, 6)));
		}
		for (JPanel p : new JPanel[]{pluginRows, groupRows})
		{
			p.revalidate();
			p.repaint();
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

	private JPanel card()
	{
		JPanel card = new JPanel(new BorderLayout(6, 4));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		return card;
	}

	private JPanel pluginRow(Binding b)
	{
		JPanel row = card();
		JLabel name = new JLabel(b.plugin);
		name.setForeground(Color.WHITE);

		JButton remove = smallButton("x", "Remove");
		remove.addActionListener(e -> plugin.removeBinding(b));

		row.add(name, BorderLayout.NORTH);
		row.add(keyRow(b, remove), BorderLayout.CENTER);
		return capHeight(row);
	}

	private JPanel groupRow(Group g)
	{
		JPanel row = card();
		JLabel name = new JLabel(g.plugin);
		name.setForeground(Color.WHITE);

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
			JButton x = smallButton("-", "Take out of the group");
			x.addActionListener(e -> plugin.removeFromGroup(g, member));
			line.add(l, BorderLayout.CENTER);
			line.add(x, BorderLayout.EAST);
			list.add(line);
		}

		JButton addPlugins = new JButton("+ Add plugins");
		addPlugins.setFocusable(false);
		addPlugins.addActionListener(e -> plugin.promptAddToGroup(g));
		JButton remove = smallButton("x", "Delete group");
		remove.addActionListener(e -> plugin.removeGroup(g));

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(name, BorderLayout.CENTER);
		top.add(remove, BorderLayout.EAST);

		JPanel middle = new JPanel(new BorderLayout(0, 4));
		middle.setOpaque(false);
		middle.add(list, BorderLayout.NORTH);
		JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		addRow.setOpaque(false);
		addRow.add(addPlugins);
		middle.add(addRow, BorderLayout.CENTER);

		row.add(top, BorderLayout.NORTH);
		row.add(middle, BorderLayout.CENTER);
		row.add(keyRow(g, null), BorderLayout.SOUTH);
		return capHeight(row);
	}

	/** The click-then-press key button, with an optional extra button beside it. */
	private JPanel keyRow(Binding b, JButton extra)
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

		JPanel bottom = new JPanel(new BorderLayout(6, 0));
		bottom.setOpaque(false);
		bottom.add(key, BorderLayout.CENTER);
		if (extra != null)
		{
			bottom.add(extra, BorderLayout.EAST);
		}
		return bottom;
	}

	private static JButton smallButton(String text, String tip)
	{
		JButton b = new JButton(text);
		b.setToolTipText(tip);
		b.setFocusable(false);
		b.setMargin(new java.awt.Insets(0, 6, 0, 6));
		return b;
	}

	private static JPanel capHeight(JPanel row)
	{
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static String label(Keybind k)
	{
		return Keybind.NOT_SET.equals(k) ? "Not set" : k.toString();
	}
}
