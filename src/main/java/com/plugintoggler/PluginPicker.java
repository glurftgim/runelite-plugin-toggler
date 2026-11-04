package com.plugintoggler;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A dialog with a search box and a list of plugin names to pick from. */
final class PluginPicker
{
	private PluginPicker()
	{
	}

	/** Returns the picked names, or an empty list if cancelled. With {@code multi} false at most one. */
	static List<String> pick(Component parent, String title, Collection<String> names, boolean multi)
	{
		List<String> all = new ArrayList<>(names);
		DefaultListModel<String> model = new DefaultListModel<>();
		JList<String> list = new JList<>(model);
		list.setSelectionMode(multi ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
			: ListSelectionModel.SINGLE_SELECTION);
		JTextField field = new JTextField();
		Set<String> chosen = new LinkedHashSet<>();
		boolean[] updating = {false};

		Runnable refill = () ->
		{
			updating[0] = true;
			model.clear();
			String q = field.getText().trim().toLowerCase(Locale.ROOT);
			for (String n : all)
			{
				if (q.isEmpty() || n.toLowerCase(Locale.ROOT).contains(q))
				{
					model.addElement(n);
				}
			}
			List<Integer> keep = new ArrayList<>();
			for (int i = 0; i < model.size(); i++)
			{
				if (chosen.contains(model.get(i)))
				{
					keep.add(i);
				}
			}
			list.setSelectedIndices(keep.stream().mapToInt(Integer::intValue).toArray());
			updating[0] = false;
		};
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refill.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refill.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refill.run();
			}
		});
		// remember picks made while a filter hides the rest of the list
		list.addListSelectionListener(e ->
		{
			if (updating[0] || e.getValueIsAdjusting())
			{
				return;
			}
			for (int i = 0; i < model.size(); i++)
			{
				if (list.isSelectedIndex(i))
				{
					chosen.add(model.get(i));
				}
				else
				{
					chosen.remove(model.get(i));
				}
			}
		});
		// put the cursor in the search box as soon as the dialog opens
		field.addAncestorListener(new AncestorListener()
		{
			@Override
			public void ancestorAdded(AncestorEvent event)
			{
				field.requestFocusInWindow();
			}

			@Override
			public void ancestorRemoved(AncestorEvent event)
			{
			}

			@Override
			public void ancestorMoved(AncestorEvent event)
			{
			}
		});
		refill.run();

		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(260, 260));
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.add(field, BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);
		panel.add(new JLabel(multi ? "Search, then pick one or more (Ctrl-click)." : "Search, then pick one."),
			BorderLayout.SOUTH);

		int result = JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
		{
			return new ArrayList<>();
		}
		if (multi)
		{
			return new ArrayList<>(chosen);
		}
		String one = list.getSelectedValue();
		List<String> out = new ArrayList<>();
		if (one != null)
		{
			out.add(one);
		}
		return out;
	}
}
