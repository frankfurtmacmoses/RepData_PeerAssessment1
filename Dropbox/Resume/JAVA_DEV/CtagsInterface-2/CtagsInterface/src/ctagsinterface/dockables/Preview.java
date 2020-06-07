package ctagsinterface.dockables;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.JCheckBoxMenuItem;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.Mode;
import org.gjt.sp.jedit.Registers;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.EditBus.EBHandler;
import org.gjt.sp.jedit.buffer.FoldHandler;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.jedit.syntax.ModeProvider;
import org.gjt.sp.jedit.textarea.JEditEmbeddedTextArea;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextArea;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.textarea.Gutter;
import org.gjt.sp.util.ThreadUtilities;

import ctagsinterface.main.CtagsInterfacePlugin;
import ctagsinterface.main.Tag;
import ctagsinterface.options.GeneralOptionPane;
import sidekick.SideKickParsedData;
import sidekick.SideKickParser;
import sidekick.SideKickPlugin;
import errorlist.DefaultErrorSource;

@SuppressWarnings("serial")
public class Preview extends JPanel implements DefaultFocusComponent,
	CaretListener, ListSelectionListener
{
	static public final String MESSAGE = CtagsInterfacePlugin.MESSAGE;
	View view;
	JList tags;
	DefaultListModel tagModel;
	TextArea text;
	boolean first = true;
	String filePath;
	Timer timer;
	Set<JEditTextArea> tracking;
	private JCheckBox wrap;
	private JCheckBox followCaret;
	private JCheckBox previewGutter;
	private JCheckBox collapseFolds;
	private JPanel toolbar;
	private JPanel textPanel;
	private boolean toolbarShown;
	private JSplitPane split;

	public Preview(final View view)
	{
		super(new BorderLayout());
		this.view = view;
		timer = null;
		tracking = new HashSet<JEditTextArea>();
		filePath = null;
		tagModel = new DefaultListModel();
		tags = new JList(tagModel);
		tags.setCellRenderer(new TagListCellRenderer());
		tags.setVisibleRowCount(4);
		tags.addListSelectionListener(this);
		tags.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent me)
			{
				if (me.getButton() == MouseEvent.BUTTON3)
				{
					final int index = tags.locationToIndex(me.getPoint());
					if (index < 0)
						return;
					final Tag t = (Tag) tagModel.get(index);
					JPopupMenu menu = new JPopupMenu();
					JMenuItem jumpAction = new JMenuItem(
						jEdit.getProperty(MESSAGE + "openInEditor"));
					jumpAction.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e)
						{
							jumpToSelectedTag();
						}
					});
					menu.add(jumpAction);
					menu.add(new AbstractAction() {
						public Object getValue(String key)
						{
							if (key.equals(Action.NAME))
								return "Copy absolute path to clipboard";
							return super.getValue(key);
						}
						public void actionPerformed(ActionEvent e)
						{
							Registers.setRegister('$', t.getFile());
						}
					});
					menu.show(view, me.getXOnScreen(), me.getYOnScreen());
					return;
				}
				if (me.getClickCount() < 2 || tags.getSelectedIndex() < 0)
					return;
				jumpToSelectedTag();
			}
		});
		tags.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e)
			{
				if ((e.getKeyCode() == KeyEvent.VK_ENTER))
				{
					e.consume();
					jumpToSelectedTag();
				}
			}
		});
		textPanel = new JPanel();
		textPanel.setLayout(new BorderLayout());
		toolbarShown = false;
		toolbar = new JPanel();
		followCaret = new JCheckBox(jEdit.getProperty(MESSAGE + "followCaret"),
			GeneralOptionPane.getPreviewFollow());
		followCaret.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0)
			{
				GeneralOptionPane.setPreviewFollow(followCaret.isSelected());
				propertiesChanged();
			}
		});
		toolbar.add(followCaret);
		wrap = new JCheckBox(jEdit.getProperty(MESSAGE + "softWrap"),
			GeneralOptionPane.getPreviewWrap());
		wrap.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				GeneralOptionPane.setPreviewWrap(wrap.isSelected());
				propertiesChanged();
			}
		});
		toolbar.add(wrap);
		previewGutter = new JCheckBox(
			jEdit.getProperty(MESSAGE + "previewGutter"),
			GeneralOptionPane.getPreviewGutter());
		previewGutter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				GeneralOptionPane.setPreviewGutter(previewGutter.isSelected());
				propertiesChanged();
			}
		});
		toolbar.add(previewGutter);
		collapseFolds = new JCheckBox(
				jEdit.getProperty(MESSAGE + "previewCollapseFolds"),
				GeneralOptionPane.getPreviewCollapseFolds());
		collapseFolds.setEnabled(previewGutter.isSelected());
		collapseFolds.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				GeneralOptionPane.setPreviewCollapseFolds(
						collapseFolds.isSelected());
				propertiesChanged();
			}
		});
		toolbar.add(collapseFolds);
		text = new PreviewTextArea();
		text.getBuffer().setProperty("folding","explicit");
		textPanel.add(text, BorderLayout.CENTER);
		textPanel.add(text, BorderLayout.CENTER);
		EditPane.initPainter(text.getPainter());
		text.getPainter().addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent me)
			{
				me.consume();
				int caret = text.xyToOffset(me.getX(), me.getY());
				text.setCaretPosition(caret);
				if (me.getClickCount() == 2 && filePath != null)
				{
					CtagsInterfacePlugin.jumpToOffset(Preview.this.view,
						filePath, text.getCaretPosition());
				}
				if (me.getButton() == MouseEvent.BUTTON3)
				{
					text.createPopupMenu(me);
					text.showPopupMenu();
				}
			}
		});
		text.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) &&
					(e.getKeyCode() == KeyEvent.VK_C))
				{
					copyPreviewSelection();
					e.consume();
				}
				if ((e.getKeyCode() == KeyEvent.VK_ENTER))
				{
					e.consume();
					jumpToSelectedTag();
				}
			}
		});
		propertiesChanged();
		text.setMinimumSize(new Dimension(150, 50));
		split = new JSplitPane(getSplitOrientation(),
				new JScrollPane(tags), textPanel);
		split.setOneTouchExpandable(true);
		split.setDividerLocation(100);
		add(split, BorderLayout.CENTER);
		EditBus.addToBus(this);
		this.addHierarchyListener(new HierarchyListener() {
			public void hierarchyChanged(HierarchyEvent e)
			{
				if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) > 0)
					setCaretTracking(Preview.this.view.getTextArea(), false);
			}
		});
		this.addComponentListener(new ComponentListener() {
			public void componentHidden(ComponentEvent arg0)
			{
				updateCaretListenerState();
			}
			public void componentMoved(ComponentEvent arg0)
			{
			}
			public void componentResized(ComponentEvent arg0)
			{
				updateCaretListenerState();
			}
			public void componentShown(ComponentEvent arg0)
			{
				updateCaretListenerState();
			}
		});
	}

	private void jumpToSelectedTag()
	{
		Tag t = (Tag) tagModel.getElementAt(tags.getSelectedIndex());
		CtagsInterfacePlugin.jumpToTag(Preview.this.view, t);
	}

	private void copyPreviewSelection()
	{
		Registers.copy(text, '$');
	}

	private void updateCaretListenerState()
	{
		boolean visible = isVisible() && getWidth() > 0 && getHeight() > 0;
		if (visible)
		{
			if  (followCaret.isSelected())
				setCaretTracking(view.getTextArea(), true);
		}
		else
		{
			Vector<JEditTextArea> textAreas = new Vector<JEditTextArea>();
			textAreas.addAll(tracking);
			Iterator<JEditTextArea> it = textAreas.iterator();
			while (it.hasNext())
				setCaretTracking(it.next(), false);
		}
	}

	private void setCaretTracking(JEditTextArea textArea, boolean track)
	{
		if (track)
			caretUpdate(null);
		if (tracking.contains(textArea) == track)
			return;
		if (track)
		{
			tracking.add(textArea);
			textArea.addCaretListener(this);
		} else
		{
			tracking.remove(textArea);
			textArea.removeCaretListener(this);
		}
	}

	private int getSplitOrientation()
	{
		return GeneralOptionPane.getPreviewVerticalSplit() ?
			JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
	}

	private void propertiesChanged()
	{
		followCaret.setSelected(GeneralOptionPane.getPreviewFollow());
		wrap.setSelected(GeneralOptionPane.getPreviewWrap());
		previewGutter.setSelected(GeneralOptionPane.getPreviewGutter());
		if (split != null)
		{
			split.setOrientation(getSplitOrientation());
			if (GeneralOptionPane.getPreviewToolbar() != toolbarShown)
				toolbarShown = GeneralOptionPane.getPreviewToolbar();
			if (toolbarShown)
				textPanel.add(toolbar, BorderLayout.NORTH);
			else
				textPanel.remove(toolbar);
		}
		wrap.setSelected(GeneralOptionPane.getPreviewWrap());
		String wrapStr;
		if (GeneralOptionPane.getPreviewWrap())
			wrapStr = "soft";
		else
			wrapStr = "none";
		text.getBuffer().setProperty("wrap", wrapStr);
		if (GeneralOptionPane.getPreviewCollapseFolds())
			text.getDisplayManager().expandFolds(1);
		else
			text.getDisplayManager().expandAllFolds();
		text.getGutter().setGutterEnabled(GeneralOptionPane.getPreviewGutter());
		followCaret.setSelected(GeneralOptionPane.getPreviewFollow());
		setCaretTracking(Preview.this.view.getTextArea(), followCaret.isSelected());
		EditPane.initPainter(text.getPainter());
	}

	public void previewTag()
	{
		String name = null;
		try
		{
			name = CtagsInterfacePlugin.getDestinationTag(view);
		}
		catch (Exception e)
		{
			return;
		}
		if (name == null)
			return;
		ThreadUtilities.runInBackground(new QueryTag(name));
	}

	public void caretUpdate(CaretEvent e)
	{
		int delay = GeneralOptionPane.getPreviewDelay();
		if (delay > 0)
		{
			if (timer == null)
			{
				timer = new Timer(delay, new ActionListener() {
					public void actionPerformed(ActionEvent arg0)
					{
						previewTag();
					}
				});
				timer.setRepeats(false);
				timer.start();
			}
			else
				timer.restart();
		}
		else
			previewTag();
	}

	public void valueChanged(ListSelectionEvent e)
	{
		int index = tags.getSelectedIndex();
		if (index < 0)
			return;
		Tag t = (Tag) tagModel.getElementAt(index);
		ThreadUtilities.runInBackground(new PreviewBufferLoader(t));
	}

	public void focusOnDefaultComponent()
	{
		tags.requestFocus();
	}

	static public String getContents(String path)
	{
		StringBuffer contents = new StringBuffer();
		String ret = null;
		BufferedReader input = null;
		try
		{
			input = new BufferedReader(new FileReader(path));
			String line = null;
			while ((line = input.readLine()) != null)
			{
				contents.append(line);
				contents.append(System.getProperty("line.separator"));
			}
			ret = contents.toString();
		}
		catch (IOException ex)
		{
			//ex.printStackTrace();
		}
		finally
		{
			try
			{
				if (input!= null)
					input.close();
			}
			catch (IOException ex)
			{
				//ex.printStackTrace();
			}
		}
		return ret;
	}

	@EBHandler
	public void handlePropertiesChanged(PropertiesChanged msg)
	{
		propertiesChanged();
	}
	@EBHandler
	public void handleViewUpdate(ViewUpdate msg)
	{
		if ((msg.getView() == view) &&
			(msg.getWhat() == ViewUpdate.EDIT_PANE_CHANGED))
		{
			updateCaretListenerState();
		}
	}
	@EBHandler
	public void handleEditPaneUpdate(EditPaneUpdate msg)
	{
		if (msg.getWhat() == EditPaneUpdate.DESTROYED)
		{
			JEditTextArea textArea = msg.getEditPane().getTextArea();
			if (tracking.contains(textArea))
				setCaretTracking(textArea, false);
		}
	}

	private final class TagListCellRenderer extends DefaultListCellRenderer
	{
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus)
		{
			JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index,
				isSelected, cellHasFocus);
			Tag tag = (Tag) tagModel.getElementAt(index);
			l.setText(getText(tag, false));
			l.setToolTipText(getText(tag, true));
			ImageIcon icon = tag.getIcon();
			if (icon != null)
				l.setIcon(icon);
			return l;
		}

		/**
		 *
		 * @param tag
		 * @param full if true, include full pathname (tooltip); if false,
		 *  return short format (text)
		 * @return
		 */
		String getText(Tag tag, boolean full)
		{
			StringBuffer s = new StringBuffer();
			s.append(tag.getName());
			String signature = tag.getExtension("signature");
			if (signature != null && signature.length() > 0)
				s.append(signature);
			s.append("   ");
			int line = tag.getLine();
			if (full)
			{
				s.append(tag.getFile());
				if (line > -1)
					s.append(":" + line);
			}
			else
			{
				File f = new File(tag.getFile());
				s.append(f.getName());
				if (line > -1)
					s.append(":" + line);
				s.append("  (" + MiscUtilities.abbreviate(f.getParent()) + ")");
			}
			return s.toString();
		}
	}

	class PreviewTag implements Runnable
	{
		Vector<Tag> tags;
		public PreviewTag(Vector<Tag> tags)
		{
			this.tags = tags;
		}
		public void run()
		{
			tagModel.clear();
			for (int i = 0; i < tags.size(); i++)
				tagModel.addElement(tags.get(i));
			if (! tags.isEmpty())
				Preview.this.tags.setSelectedIndex(0);
		}
	}
	class QueryTag implements Runnable
	{
		String name;
		public QueryTag(String name)
		{
			this.name = name;
		}
		public void run()
		{
			Vector<Tag> tags = CtagsInterfacePlugin.queryScopedTag(
				Preview.this.view, name);
			if (tags == null)
				return;
			ThreadUtilities.runInDispatchThread(new PreviewTag(tags));
		}
	}
	class PreviewBufferLoader implements Runnable
	{
		Tag tag;
		public PreviewBufferLoader(Tag t)
		{
			tag = t;
		}
		public void run()
		{
			filePath = tag.getFile();
			if (filePath == null)
				return;
			int line = tag.getLine();
			if (line > -1)
			{
				String s = null;
				String folding = null;
				Mode mode = null;
				FoldHandler fh = null;
				//SideKickParsedData data = null;
				File file = new File(filePath);
				String parent = file.getParent();
				Buffer buffer = jEdit.openTemporary(view, parent, filePath, false);
				if (buffer != null)
				{
					if (buffer.isTemporary())
						buffer.setMode();
					mode = buffer.getMode();
					folding = buffer.getFoldHandler().getName();
					s = buffer.getText();
					fh = buffer.getFoldHandler();
				}
				if (s != null)
					ThreadUtilities.runInDispatchThread(
						new PreviewBufferUpdate(s, line, folding, mode, fh));
			}
		}
	}
	class PreviewBufferUpdate implements Runnable
	{
		String s;
		int line;
		String folding;
		Mode mode;
		FoldHandler fh;

		public PreviewBufferUpdate(String s, int line, String folding,
			Mode mode, FoldHandler fh)
		{
			this.s = s;
			this.line = line;
			this.folding = folding;
			this.mode = mode;
			this.fh = fh;
		}
		public void run()
		{
			JEditBuffer buffer = text.getBuffer();
			buffer.setReadOnly(false);
			text.setText(s);
			if (folding != null)
				buffer.setProperty("folding", folding);
			if (mode == null)
				mode = ModeProvider.instance.getMode("text");
			buffer.setMode(mode);
			buffer.invalidateCachedFoldLevels();
			text.scrollTo(line, 0, true);
			text.setCaretPosition(text.getLineStartOffset(line - 1));
			buffer.setReadOnly(true);
		}
	}

	private class PreviewTextArea extends JEditEmbeddedTextArea
	{

		//{{{ PreviewTextArea constructor
		public PreviewTextArea()
		{
			super();
			initGutter();
			setRightClickPopupEnabled(true);
				// allows text to remain selected on right click
		} // }}}

		@Override
		public void createPopupMenu(MouseEvent evt)
		{
			// Create a context menu for the text area
			popup = new JPopupMenu();
			JMenuItem jumpAction = new JMenuItem(
				jEdit.getProperty(MESSAGE + "openInEditor"));
			jumpAction.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e)
				{
					CtagsInterfacePlugin.jumpToOffset(Preview.this.view,
						filePath, getCaretPosition());
				}
			});
			popup.add(jumpAction);
			JMenuItem copyAction = new JMenuItem(
				jEdit.getProperty(MESSAGE + "copyPreviewSelection"));
			copyAction.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e)
				{
					copyPreviewSelection();
				}
			});
			popup.add(copyAction);
			JCheckBoxMenuItem popupFollowCaret = new JCheckBoxMenuItem(
				jEdit.getProperty(MESSAGE + "followCaret"),
				followCaret.isSelected());
			popupFollowCaret.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e)
				{
					followCaret.doClick();
				}
			});
			popup.add(popupFollowCaret);
			JCheckBoxMenuItem popupWrap = new JCheckBoxMenuItem(
				jEdit.getProperty(MESSAGE + "softWrap"),
				wrap.isSelected());
			popupWrap.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e)
				{
					wrap.doClick();
				}
			});
			popup.add(popupWrap);

			JCheckBoxMenuItem popupCollapseFolds = new JCheckBoxMenuItem(
				jEdit.getProperty(MESSAGE + "previewCollapseFolds"),
				collapseFolds.isSelected());
			popupCollapseFolds.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e)
				{
					collapseFolds.doClick();
				}
			});
			popup.add(popupCollapseFolds);
		}

		/**
		 * Copied from org.gjt.sp.jedit.textarea.StandaloneTextArea
		 * Extending StandaloneTextArea instead of using JEditEmbeddedTextArea
		 * caused problems with folding and couldn't see another way of doing it
		 */
		private void initGutter()
	    {
			Gutter gutter = getGutter();
			gutter.setExpanded(jEdit.getBooleanProperty(
				"view.gutter.lineNumbers"));
			int interval = jEdit.getIntegerProperty(
				"view.gutter.highlightInterval",5);
			gutter.setHighlightInterval(interval);
			gutter.setCurrentLineHighlightEnabled(jEdit.getBooleanProperty(
				"view.gutter.highlightCurrentLine"));
			gutter.setStructureHighlightEnabled(jEdit.getBooleanProperty(
				"view.gutter.structureHighlight"));
			gutter.setStructureHighlightColor(
				jEdit.getColorProperty("view.gutter.structureHighlightColor"));
			gutter.setBackground(
				jEdit.getColorProperty("view.gutter.bgColor"));
			gutter.setForeground(
				jEdit.getColorProperty("view.gutter.fgColor"));
			gutter.setHighlightedForeground(
				jEdit.getColorProperty("view.gutter.highlightColor"));
			gutter.setFoldColor(
				jEdit.getColorProperty("view.gutter.foldColor"));
			gutter.setCurrentLineForeground(
				jEdit.getColorProperty("view.gutter.currentLineColor"));
			String alignment = jEdit.getProperty(
				"view.gutter.numberAlignment");
			if ("right".equals(alignment))
				gutter.setLineNumberAlignment(Gutter.RIGHT);
			else if ("center".equals(alignment))
				gutter.setLineNumberAlignment(Gutter.CENTER);
			else // left == default case
				gutter.setLineNumberAlignment(Gutter.LEFT);

			gutter.setFont(jEdit.getFontProperty("view.gutter.font"));

			int width = jEdit.getIntegerProperty(
				"view.gutter.borderWidth",3);
			gutter.setBorder(width,
				jEdit.getColorProperty("view.gutter.focusBorderColor"),
				jEdit.getColorProperty("view.gutter.noFocusBorderColor"),
				painter.getBackground());
		}
 	}
}
