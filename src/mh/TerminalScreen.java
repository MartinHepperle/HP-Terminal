package mh;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JPanel;

import com.sun.glass.events.KeyEvent;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JPopupMenu;
import java.awt.Component;
import javax.swing.JMenuItem;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * A terminal screen panel.
 * 
 * @author Martin Hepperle, July 2019
 * 
 */
public class TerminalScreen extends JPanel implements MouseListener,
		ActionListener
{
	// the size of one screen
	public static int WIDTH = 80;
	public static int HEIGHT = 24;
	// number of pages each with WIDTH x HEIGHT characters.
	private int PAGES = 4;
	// additional lines below the actual screen (HP terminals use them for
	// function keys and status display)
	private int EXTRALINES = 3;

	// we have two character sets
	static private final int CS_PRIMARY = 0;
	static private final int CS_ALTERNATE = 1;
	private int currentCharSet;
	// set primary character set by ESC
	private int primaryCharSet = CS_ROMAN;
	private int alternateCharSet = CS_ROMAN;

	// primary and alternate character set can be one of
	static final int CS_ROMAN = 0;
	static final int CS_LINEDRAW = 1;
	static final int CS_MATH = 2;

	static final int NUL = 0x00;
	static final int BS = 0x08;
	static final int HT = 0x09;
	static final int LF = 0x0A;
	static final int VT = 0x0B;
	static final int FF = 0x0C;
	static final int CR = 0x0D;

	Frame theParent;
	HPTerminalApplication theApp;
	int xCursor; // 0...WIDTH-1
	int yCursor; // 0...HEIGHT-1
	int dx;// cell width
	int dy; // cell height
	int descent;
	int borderWidth = 16;
	private Font theFont;
	protected Color foreColor;
	protected Color backColor;
	// TAB stops: 1== set, 0== free
	byte tabStop[] = new byte[WIDTH];
	// screen memory
	char screen[] = new char[HEIGHT * WIDTH * PAGES];
	int attributes[] = new int[HEIGHT * WIDTH * PAGES];
	// CCCCC.U.V.I = bits 7...0
	final static int ATTRIB_INTENSE_MASK = 0x0001;
	final static int ATTRIB_INVERSE_MASK = 0x0002;
	final static int ATTRIB_UNDERLINE_MASK = 0x0004;
	final static int ATTRIB_COLOR_MASK = (0x1F << 3); // 0...31 color
	final static int ATTRIB_EMPTY_MASK = 0xFFFF;

	int currentAttribute = 0;

	// 8 colors
	// Color colorMap[] = { Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW,
	// Color.BLUE, Color.MAGENTA, Color.CYAN, Color.WHITE };
	// adapted
	Color colorMap[] = { new Color(0x00, 0x10, 0x00), Color.RED,
			new Color(0x00, 0xEE, 0x00), Color.YELLOW, Color.BLUE,
			Color.MAGENTA, Color.CYAN, Color.WHITE };

	int leftMargin;
	int rightMargin;
	int savedX;
	int savedY;
	int savedAttribute;

	// the starting index defines the index of the character shown in the upper
	// left corner of the current view.
	// It must be in the range [0...(PAGES-1)*WIDTH*HEIGHT].
	int idxStart = 0; // starting index of current view

	static final int SOFTKEYS_MODE = 1;
	static final int SOFTKEYS_USER = 2;
	private int softKeyMode; // SOFTKEYS_MODE or SOFTKEYS_USER
	SoftKeys softKeysSystem;
	SoftKeys softKeysUser;

	private boolean keyLabelVisible;

	private boolean cursorVisible;
	private boolean cursorBlink;
	private boolean paintCursorOnly;

	private boolean m_displayFunctions;

	private boolean wrapLines;
	private boolean m_insertMode;
	private boolean m_keyboardLocked;

	// The bitmapped font
	// It is scaled by a factor of 2 to accomplish half pixel resolution.
	private final static int H_CELL = 30;
	private final static int W_CELL = 16;
	private Image imgFont;

	/**
	 * Create the screen panel.
	 * 
	 * @param f
	 *            The parent JFrame.
	 * @param name
	 *            The name to show in the title bar caption.
	 */
	public TerminalScreen(Frame f, HPTerminalApplication a, String name)
	{
		theParent = f;
		theApp = a;

		theParent.setTitle(name + " - Text Screen");

		loadFontImage();

		resetDefaults(true);

		addMouseListener(this);

		JPopupMenu popupMenu = new JPopupMenu();
		addPopup(this, popupMenu);

		JMenuItem mntmCopyText = new JMenuItem("Copy Text");
		mntmCopyText.setMnemonic(KeyEvent.VK_T);
		mntmCopyText.setActionCommand("COPY_TEXT");
		mntmCopyText.addActionListener(this);
		popupMenu.add(mntmCopyText);

		JMenuItem mntmCopyHtml = new JMenuItem("Copy HTML");
		mntmCopyHtml.setMnemonic(KeyEvent.VK_H);
		mntmCopyHtml.setActionCommand("COPY_HTML");
		mntmCopyHtml.addActionListener(this);
		mntmCopyHtml.setEnabled(false);
		popupMenu.add(mntmCopyHtml);

		JMenuItem mntmCopyBitmap = new JMenuItem("Copy Bitmap");
		mntmCopyBitmap.setMnemonic(KeyEvent.VK_B);
		mntmCopyBitmap.setActionCommand("COPY_BITMAP");
		mntmCopyBitmap.addActionListener(this);
		mntmCopyBitmap.setEnabled(false);
		popupMenu.add(mntmCopyBitmap);

		CursorBlinker theBlinker = new CursorBlinker();
		Timer timer = new Timer(true);
		timer.scheduleAtFixedRate(theBlinker, 0, 1000);
	}

	public void resetDefaults ( boolean hard )
	{
		// default font
		setFontSize(12);

		// select default colors
		// light green
		foreColor = colorMap[2];
		// dark green
		backColor = colorMap[0];

		// cursor to upper left of view
		xCursor = 0;
		yCursor = 0;

		leftMargin = 0;
		rightMargin = WIDTH - 1;

		currentAttribute = 0;
		wrapLines = true;

		keyLabelVisible = true;
		softKeyMode = SOFTKEYS_MODE;
		softKeysSystem = new SoftKeys();
		softKeysSystem.setButtonText(SoftKeys.ROW_TOP, SoftKeys.F_8, " Config");
		softKeysSystem.setButtonText(SoftKeys.ROW_BOT, SoftKeys.F_8, "  Keys");

		softKeysUser = new SoftKeys();
		softKeysUser.setButtonText(SoftKeys.ROW_TOP, SoftKeys.F_1, "   f1");
		softKeysUser.setButtonText(SoftKeys.ROW_TOP, SoftKeys.F_2, "   f2");
		softKeysUser.setButtonText(SoftKeys.ROW_TOP, SoftKeys.F_3, "   f3");
		softKeysUser.setButtonText(SoftKeys.ROW_TOP, SoftKeys.F_4, "   f4");
		softKeysUser.setButtonText(SoftKeys.ROW_TOP, SoftKeys.F_5, "   f5");
		softKeysUser.setButtonText(SoftKeys.ROW_TOP, SoftKeys.F_6, "   f6");
		softKeysUser.setButtonText(SoftKeys.ROW_TOP, SoftKeys.F_7, "   f7");
		softKeysUser.setButtonText(SoftKeys.ROW_TOP, SoftKeys.F_8, "   f8");

		cursorVisible = true;
		cursorBlink = true;
		paintCursorOnly = false;

		m_insertMode = false;
		m_keyboardLocked = false;
		m_displayFunctions = true;

		saveCursor();

		if (hard)
			clearMemory();

		setDefaultTabs(8);
	}

	public void test ( Graphics g )
	{
		resetDefaults(true);

		g.setColor(backColor);
		g.setXORMode(foreColor);

		currentCharSet = CS_ROMAN;

		yCursor = 5 * dy;
		for (int c = 32; c < 128; c++)
		{
			if (c % 32 == 0)
			{
				yCursor += dy;
				xCursor = 32;
			}
			drawChar(g, (char) c, xCursor, yCursor);
			xCursor += dx;
		}

		yCursor += dy;
		currentCharSet = CS_LINEDRAW;
		for (int c = 32; c < 128; c++)
		{
			if (c % 32 == 0)
			{
				yCursor += dy;
				xCursor = 32;
			}
			drawChar(g, (char) c, xCursor, yCursor);
			xCursor += dx;
		}

		yCursor += dy;
		currentCharSet = CS_MATH;
		for (int c = 32; c < 128; c++)
		{
			if (c % 32 == 0)
			{
				yCursor += dy;
				xCursor = 32;
			}
			drawChar(g, (char) c, xCursor, yCursor);
			xCursor += dx;
		}

		yCursor += dy;
		yCursor += dy;

		currentCharSet = CS_ROMAN;

		String str = String.format("Memory: %d pages",
				new Object[] { new Integer(PAGES) });

		drawString(g, str, 32, yCursor);
		yCursor += dy;

		// cursor to upper left of view
		xCursor = 0;
		yCursor = 0;

		repaint(100);
	}

	private void loadFontImage ()
	{
		String fileName = "combinedalpha-16x30.png";
		/*
		 * java.awt.Toolkit tk = java.awt.Toolkit.getDefaultToolkit(); imgFont =
		 * tk.createImage(fileName);
		 */
		java.awt.Toolkit tk = java.awt.Toolkit.getDefaultToolkit();
		// image file is in mh/rsc/...
		imgFont = tk.createImage(getClass().getResource("rsc/" + fileName));

	}

	/**
	 * Select one of the two available character sets.
	 * 
	 * @param idxSet
	 *            - either CS_PRIMARY or CS_ALTERNATE.
	 */
	public void selectCharset ( int idxSet )
	{
		if (idxSet == CS_ALTERNATE)
			currentCharSet = alternateCharSet;
		else
			currentCharSet = primaryCharSet;
	}

	/**
	 * Define the font to be used for the primary character set.
	 * 
	 * @param charSet
	 *            - either CS_ROMAN, CS_LINEDRAW or CS_MATH.
	 */
	public void setPrimaryCharset ( int charSet )
	{
		primaryCharSet = charSet;
	}

	/**
	 * Define the font to be used for the alternate character set.
	 * 
	 * @param charSet
	 *            either - CS_ROMAN, CS_LINEDRAW or CS_MATH.
	 */
	public void setAlternateCharset ( int charSet )
	{
		alternateCharSet = charSet;
	}

	/**
	 * Set a new font size and size window accordingly.
	 * 
	 * @param size
	 *            The new font size in pixels.
	 */
	public void setFontSize ( int size )
	{
		theFont = loadFont("HPTerminal.ttf", size);

		/*
		 * Using a TrueType font
		 */
		/*
		 * Create a temporary graphics context for determining font dimensions
		 * etc.
		 * 
		 * BufferedImage biPaper = new BufferedImage(8, 8,
		 * BufferedImage.TYPE_BYTE_INDEXED); Graphics g = biPaper.getGraphics();
		 * FontMetrics theMetrics = g.getFontMetrics(theFont); dx =
		 * theMetrics.charWidth(32); dy = theMetrics.getHeight() + 2; descent =
		 * theMetrics.getDescent();
		 */

		/*
		 * Using a bitmapped font
		 */

		/*
		 * The bitmap font nominal size = 12 pt
		 */
		dx = W_CELL / 2 * size / 12;
		dy = H_CELL / 2 * size / 12;
		descent = 4 * size / 15;

		Dimension d = new Dimension(dx * WIDTH + 2 * borderWidth, dy
				* (HEIGHT + EXTRALINES) + 2 * descent + 2 * borderWidth);

		setPreferredSize(d);
		setSize(d);

		theParent.pack();
		repaint(100);
	}

	/**
	 * Load a TrueType font and return a scaled instance.
	 * 
	 * @param fontFileName
	 *            The name of the font file.
	 * @param size
	 *            The desired size of the font.
	 * @return An instance of the font or a PLAIN, MONOSPACED font if the
	 *         TrueType font file cannot be found.
	 */
	Font loadFont ( String fontFileName, int size )
	{
		// default font
		Font theFont = new Font(Font.MONOSPACED, Font.PLAIN, size);

		try
		{
			/*
			 * theFont = Font.createFont(Font.TRUETYPE_FONT, new
			 * File(fontFileName));
			 */
			// from resource in path or .jar file
			URL url = getClass().getResource("rsc/" + fontFileName);
			InputStream is = url.openStream();
			theFont = Font.createFont(Font.TRUETYPE_FONT, is);
			is.close();
			// apply size
			theFont = theFont.deriveFont(Font.PLAIN, (float) size);
		}
		catch (FontFormatException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return theFont;
	}

	public void setDisplayFunctions ( boolean onoff )
	{
		m_displayFunctions = onoff;
	}

	/**
	 * Create a new Color from the inverted RGB components of the given color.
	 * 
	 * @param c
	 *            the Color to invert
	 * @return a new Color with the R, G, B components inverted.
	 */
	private Color createInvertedColor ( Color c )
	{
		return new Color(255 - c.getRed(), 255 - c.getGreen(),
				255 - c.getBlue());
	}

	/**
	 * Clear all TAB stops.
	 */
	public void clearAllTabs ()
	{
		for (int i = 0; i < WIDTH; i++)
			tabStop[i] = 0;
	}

	/**
	 * Distribute TAB stops in regular intervals.
	 * 
	 * @param step
	 *            The horizontal position increment from TAB to TAB. <br>
	 * 
	 *            Example: TAB step = 5 - a TAB in in every 5th column.
	 * 
	 *            <pre>
	 *            1   5   9   13  17  21   screen coordinates
	 *            |   |   |   |   |   | 
	 *            123456789012345678901234567890
	 *            |   |   |   |   |   |  
	 *            0   4   8   12  16  20   array index
	 * </pre>
	 */
	public void setDefaultTabs ( int step )
	{
		clearAllTabs();
		for (int i = 0; i < WIDTH; i += (step - 1))
			tabStop[i] = 1;
	}

	/**
	 * Set a TAB stop at the current cursor position.
	 */
	public void setTab ()
	{
		tabStop[xCursor] = 1;
	}

	/**
	 * Clear any TAB stop at the current cursor position.
	 */
	public void clearTab ()
	{
		tabStop[xCursor] = 0;
	}

	/**
	 * Set a TAB stop at the given cursor position.
	 * 
	 * @param x
	 *            The horizontal position where a TAB stop is set [1...WIDTH].
	 */
	public void setTab ( int x )
	{
		if (x <= WIDTH && x > 0)
			tabStop[x - 1] = 1;
	}

	/**
	 * Clear any TAB stop at the given cursor position.
	 * 
	 * @param x
	 *            The horizontal position where a TAB stop is removed
	 *            [1...WIDTH].
	 */
	public void clearTab ( int x )
	{
		if (x <= WIDTH && x > 0)
			tabStop[x - 1] = 0;
	}

	/**
	 * 
	 * @return The next TAB position in [1...WIDTH].<br>
	 *         If there is no following TAB stop the current position is
	 *         returned,
	 */
	public int nextTab ()
	{
		// start one column after current position
		int i = xCursor + 1;

		for (; i < WIDTH; i++)
			if (tabStop[i] == 1)
				break;

		// no tab found: stay where we are
		if (i == WIDTH)
			i = xCursor;

		return i;
	}

	/**
	 * 
	 * @return The previous TAB position in [1...WIDTH].<br>
	 *         If there is no preceding TAB stop the current position is
	 *         returned,
	 */
	public int prevTab ()
	{
		// start one column before current position
		int i = xCursor - 1;

		for (; i >= 0; i--)
			if (tabStop[i] == 1)
				break;

		// no tab found: stay where we are
		if (i < 0)
			i = xCursor;

		return i;
	}

	public void saveCursor ()
	{
		savedX = xCursor;
		savedY = yCursor;
		savedAttribute = currentAttribute;
	}

	public void restoreCursor ()
	{
		xCursor = savedX;
		yCursor = savedY;
		currentAttribute = savedAttribute;
	}

	/**
	 * Define the behavior of the cursor at the end of a line.
	 * 
	 * @param enable
	 *            if true, the cursor wraps to the next line. The screen scrolls
	 *            up if the end of the view is reached and the cursor is not yet
	 *            at the end of the screen memory.
	 */
	public void setLineWrap ( boolean enable )
	{
		wrapLines = enable;
	}

	/**
	 * Clear the visible screen. The view is filled with blank characters.
	 */
	public void clearScreen ()
	{
		clear(idxStart, idxStart + HEIGHT * WIDTH - 1);
	}

	/**
	 * Clear the complete screen memory. The page memory is filled with blank
	 * characters.
	 */
	public void clearMemory ()
	{
		clear(idxBOM(), idxEOM());
	}

	/**
	 * Fill a region of memory by filling with Space characters. Also resets the
	 * attribute byte for each cell.
	 * 
	 * @param idxFirst
	 *            index of first character to clear.
	 * @param idxLast
	 *            index of last character to clear.
	 */
	public void clear ( int idxFirst, int idxLast )
	{
		for (int i = idxFirst; i <= idxLast; i++)
		{
			screen[i] = ' ';
			attributes[i] = ATTRIB_EMPTY_MASK;
		}

		repaint(10);
	}

	/**
	 * Set the character insert mode. If true, subsequent characters are
	 * inserted, otherwise they overwrite.
	 * 
	 * @param onoff
	 *            True if the insert mode shall be activated, otherwise False.
	 */
	public void setInsertMode ( boolean onoff )
	{
		m_insertMode = onoff;
		repaint(100);
	}

	/**
	 * Toggle the character insert mode flag.
	 */
	public void toggleInsertMode ()
	{
		setInsertMode(!m_insertMode);
		repaint(100);
	}

	/**
	 * Set the keyboard locked flag.
	 * 
	 * @param yesno
	 *            True if the keyboard shall be locked, False otherwise..
	 */
	void lockKeyboard ( boolean yesno )
	{
		m_keyboardLocked = yesno;
		repaint(100);
	}

	/**
	 * @return True if the keyboard is currently locked, False if not.
	 */
	boolean isKeyboardLocked ()
	{
		return m_keyboardLocked;
	}

	/**
	 * Set the cursor visibility flag.
	 * 
	 * @param yesno
	 *            True if the cursor shall be shown, False otherwise..
	 */
	void setcursorVisible ( boolean yesno )
	{
		cursorVisible = yesno;
		repaint(100);
	}

	/**
	 * Insert one or more blank characters at the current position. All
	 * following characters up to the end of the line are shifted right by one.
	 * The last characters at the end of the line are lost. The cursor position
	 * is not updated
	 * 
	 * @param count
	 *            The number of blanks to insert.
	 */
	public void insertCharsInLine ( int count )
	{
		while (count-- > 0)
		{
			for (int i = idxEOL(); i > idxCursor(); i--)
			{
				screen[i] = screen[i - 1];
				attributes[i] = attributes[i - 1];
			}
			screen[idxCursor()] = ' ';
			attributes[idxCursor()] = currentAttribute;
		}
		repaint(10);
	}

	/**
	 * Delete one or more characters at the current position and shift the rest
	 * of the line to the left. Blank character(s) are inserted at the end of
	 * the line.
	 * 
	 * @param count
	 *            The number of characters to delete.
	 */
	public void deleteCharsInLine ( int count )
	{
		while (count-- > 0)
		{
			for (int i = idxCursor(); i < idxEOL(); i++)
			{
				screen[i] = screen[i + 1];
				attributes[i] = attributes[i + 1];
			}
			// end of line moves 1 column to the left
			screen[idxEOL()] = ' ';
			attributes[idxEOL()] = ATTRIB_EMPTY_MASK;
		}
		repaint(10);
	}

	/**
	 * @return The index of the first character in the current view.
	 */
	private int idxBOS ()
	{
		return idxStart;
	}

	/**
	 * @return The index of the first character in memory.
	 */
	private int idxBOM ()
	{
		return 0;
	}

	/**
	 * @return The index of the last character at end of current view.
	 */
	private int idxEOS ()
	{
		return idxStart + WIDTH * HEIGHT - 1;
	}

	/**
	 * @return The index of the last character at end of memory.
	 */
	private int idxEOM ()
	{
		return WIDTH * HEIGHT * PAGES - 1;
	}

	/**
	 * @return The index of the current cursor position.
	 */
	private int idxCursor ()
	{
		return idxBOL() + xCursor;
	}

	/**
	 * @return The index of the first character on current line.
	 */
	private int idxBOL ()
	{
		return idxStart + yCursor * WIDTH;
	}

	/**
	 * @return The index of the last character on current line.
	 */
	private int idxEOL ()
	{
		return idxStart + (yCursor + 1) * WIDTH - 1;
	}

	/**
	 * 
	 * @return - the starting row of the screen relative to memory.
	 */
	public int getStartRow ()
	{
		return idxStart / WIDTH;
	}

	/**
	 * Return the text contained in a line of the current screen view.
	 * 
	 * @param row
	 *            the screen row [0...HEIGHT-1]
	 * @param offset
	 *            the starting column [0...WIDTH-1]
	 * @param count
	 *            the number of characters to copy [1...WIDTH-offset]
	 * 
	 * @return The text contained in the given part of the line. If the number
	 *         of defined characters in the line is shorter than offset+count
	 *         only the defined length is returned.
	 */
	String getScreenLine ( int row, int offset, int count )
	{
		return getMemoryLine(row + getStartRow(), offset, count);
	}

	/**
	 * Return a line from global screen memory.
	 * 
	 * @param row
	 *            the memory row [0...HEIGHT*PAGES-1]
	 * @param offset
	 *            the starting column [0...WIDTH-1]
	 * @param count
	 *            the number of columns to copy [1...WIDTH-offset]
	 * @return a string with the characters starting at offset to the end of the
	 *         line. If the number of defined characters in the line is shorter
	 *         than offset+count only the defined length is returned.
	 */
	String getMemoryLine ( int row, int offset, int count )
	{
		if (row < 0)
			row = 0; // first row
		else if (row >= HEIGHT * PAGES)
			row = HEIGHT - 1; // last row

		if (offset < 0)
			offset = 0; // first character
		else if (offset >= WIDTH)
			offset = WIDTH - 1; // last character

		// now clip count to line length
		if (offset + count > WIDTH)
			count = WIDTH - offset;

		// check line for used length
		int idxStart = row * WIDTH + offset;
		for (int idx = idxStart; idx < idxStart + count; idx++)
		{
			if (attributes[idx] == ATTRIB_EMPTY_MASK)
			{
				count = idx - idxStart;
				break;
			}
		}

		return String.copyValueOf(screen, row * WIDTH + offset, count);
	}

	/**
	 * Erase from the cursor to the start of the current screen.
	 */
	public void clearToBOS ()
	{
		clear(idxBOS(), idxCursor());
	}

	/**
	 * Erase from the cursor to the end of the memory.
	 */
	public void clearToEOM ()
	{
		clear(idxCursor(), idxEOM());
	}

	/**
	 * Erase from the cursor to the end of the current screen.
	 */
	public void clearToEOS ()
	{
		clear(idxCursor(), idxEOS());
	}

	/**
	 * Erase from the cursor position (inclusive) to the end of line.
	 */
	public void clearToEOL ()
	{
		clear(idxCursor(), idxEOL());
	}

	/**
	 * Erase from the cursor position (inclusive) to the start of line.
	 */
	public void clearToBOL ()
	{
		clear(idxBOL(), idxCursor());
	}

	/**
	 * Erase one or more characters starting at the cursor position. Does not
	 * erase beyond the end of the line.
	 * 
	 * @param count
	 *            The number of characters to erase.
	 */
	public void clearChars ( int count )
	{
		int idxLast = idxCursor() + count - 1;
		if (idxLast > idxEOL())
			idxLast = idxEOL();
		else if (idxLast <= idxCursor())
			idxLast = idxCursor();

		clear(idxCursor(), idxLast);
	}

	/**
	 * Erase the entire line
	 */
	public void clearLine ()
	{
		clear(idxBOL(), idxEOL());
	}

	/**
	 * Insert a new blank line at the current cursor row position and scroll the
	 * current and all following lines down. The last line at the end of memory
	 * is lost.
	 */
	public void insertLine ()
	{
		/**
		 * <pre>
		 * 1          1 
		 * 2          2
		 * 3  x,y     new
		 * 4          3
		 * ...        ...
		 * 24         23
		 * </pre>
		 */

		// copy lines down from the end to the line below the current line
		for (int i = idxEOM(); i >= idxBOL() + WIDTH; i--)
		{
			screen[i] = screen[i - WIDTH];
			attributes[i] = attributes[i - WIDTH];
		}

		// clear new current line
		clear(idxBOL(), idxEOL());

		repaint(10);
	}

	/**
	 * Delete the current line and scroll the following lines up. A new empty
	 * line will appended to the bottom of the memory buffer.
	 */
	public void deleteCurrentLine ()
	{
		/**
		 * <pre>
		 * 1          1
		 * 2          2  >
		 * 3  x,y     4  > line 3 is deleted
		 * 4          5
		 * ...        24
		 * 24         new
		 * </pre>
		 */

		// copy up
		for (int i = idxBOL(); i <= idxEOM() - WIDTH; i++)
		{
			screen[i] = screen[i + WIDTH];
			attributes[i] = attributes[i + WIDTH];
		}

		// clear last (new) line
		clear(idxEOM() - WIDTH + 1, idxEOM());

		repaint(10);
	}

	/**
	 * Move the cursor to the upper left of the screen .
	 */
	public void homeScreenUp ()
	{
		xCursor = 0;
		yCursor = 0;
		// view starts at
		idxStart = idxBOM();
		repaint(10);
	}

	/**
	 * Move the cursor to the lower left of the screen .
	 */
	public void homeScreenDown ()
	{
		xCursor = 0;
		yCursor = HEIGHT - 1;
		repaint(10);
	}

	/**
	 * Shift the viewport up by one line so that the paper moves one line down.
	 * 
	 * @param rows
	 *            The number of rows to scroll up.
	 */
	public void scrollScreenUp ( int rows )
	{
		idxStart -= WIDTH * rows; // one line
		clipViewToMemory();
		repaint(10);
	}

	/**
	 * Shift the viewport down by one line so that the paper moves one line up.
	 * 
	 * @param rows
	 *            The number of rows to scroll down.
	 */
	public void scrollScreenDown ( int rows )
	{
		idxStart += WIDTH * rows; // one line
		clipViewToMemory();
		repaint(10);
	}

	/**
	 * Shift the viewport up by one page so that the paper moves down. There is
	 * an overlap of of one line (the top line becomes the new bottom line).
	 */
	public void pageScreenUp ()
	{
		idxStart -= WIDTH * (HEIGHT - 1); // one screen
		clipViewToMemory();
		repaint(10);
	}

	/**
	 * Shift the viewport down by one page so that the paper moves up. There is
	 * an overlap of of one line (the bottom line becomes the new top line).
	 */
	public void pageScreenDown ()
	{
		idxStart += WIDTH * (HEIGHT - 1); // one screen
		clipViewToMemory();
		repaint(10);
	}

	/**
	 * Shift the viewport down by one line by shifting the memory up. Delete the
	 * first line in memory and move the following lines up. Clear the new last
	 * line.
	 */
	public void scrollMemoryDown ()
	{
		// copy up
		for (int i = idxBOM(); i <= idxEOM() - WIDTH; i++)
		{
			screen[i] = screen[i + WIDTH];
			attributes[i] = attributes[i + WIDTH];
		}

		// clear new bottom line
		clear(idxEOM() - WIDTH + 1, idxEOM());

		repaint(10);
	}

	/**
	 * Set the cursor position relative to the current view.
	 * 
	 * @param row
	 *            row of cursor cell [0...HEIGHT-1]
	 * @param col
	 *            column of cursor cell [0...WIDTH-1]
	 */
	public void setCursorRelScreen ( int row, int col )
	{
		xCursor = col;
		yCursor = row;
		clipCursorToScreen();
		repaint(10);
	}

	/**
	 * Set the cursor position relative to memory.
	 * 
	 * @param row
	 *            row of cursor cell [0...MEMORY_ROWS-1]
	 * @param col
	 *            column of cursor cell [0...WIDTH-1]
	 */
	public void setCursorRelMemory ( int row, int col )
	{
		xCursor = col;
		yCursor = row - idxStart / WIDTH;

		if (yCursor < 0)
		{
			// scroll view up so that row is at top
			scrollScreenUp(-yCursor);
		}
		else if (yCursor >= HEIGHT)
		{
			// scroll view down so that row is at bottom
			scrollScreenDown(yCursor - HEIGHT + 2);
		}
		clipCursorToScreen();
		repaint(10);
	}

	/**
	 * Move the cursor position by increment.
	 * 
	 * @param deltaRow
	 *            row movement +=down, -=up
	 * @param deltaCol
	 *            column movement, +=right, -=left
	 */
	public void moveCursor ( int deltaRow, int deltaCol )
	{
		xCursor += deltaCol;
		yCursor += deltaRow;

		if (xCursor < 0)
		{
			// wrap at left edge
			xCursor = WIDTH - 1;
			yCursor--;
		}
		else if (xCursor >= WIDTH)
		{
			// wrap at right edge
			xCursor = 0;
			yCursor++;
		}

		// wrap on bottom or top
		if (yCursor < 0)
			yCursor = HEIGHT - 1;
		else if (yCursor >= HEIGHT)
			yCursor = 0;

		// always show cursor when moving
		cursorBlink = true;
		repaint(10);
	}

	/**
	 * Make sure that the cursor is inside screen bounds.
	 * <p>
	 * x must be within [1...WIDTH].<br>
	 * y must be within [1...HEIGHT].
	 */
	private void clipCursorToScreen ()
	{
		if (xCursor < 0)
			xCursor = 0;
		else if (xCursor >= WIDTH)
			xCursor = WIDTH - 1;

		if (yCursor < 0)
			yCursor = 0;
		else if (yCursor >= HEIGHT)
			yCursor = HEIGHT - 1;
	}

	/**
	 * Make sure that the current viewport is completely inside the memory
	 * bounds.
	 * <p>
	 * idxStart must be [0...(PAGES-1)*WIDTH*HEIGHT].
	 */
	private void clipViewToMemory ()
	{
		if (idxStart < 0)
			idxStart = 0;
		else if (idxStart > (PAGES - 1) * WIDTH * HEIGHT)
			idxStart = (PAGES - 1) * WIDTH * HEIGHT;
	}

	/**
	 * Set the attribute byte for the following characters. This attribute will
	 * be used in all subsequent calls to putChar().
	 * 
	 * @param a
	 *            0 == normal<br>
	 *            1 == highlight<br>
	 *            7 == inverse<br>
	 *            30...37 == foreground color<br>
	 *            40...47 == background color<br>
	 */
	public void setAttribute ( byte a )
	{
		// attribute
		// CCCCC.UVI = bits 7...0
		// byte ATTRIB_INTENSE_MASK = (byte) 0x01;
		// byte ATTRIB_INVERSE_MASK = (byte) 0x02;
		// byte ATTRIB_UNDERLINE_MASK = (byte) 0x04;
		// byte ATTRIB_COLOR_MASK = (byte) (0x1F << 3); // 0...31

		if (a == 0)
		{
			// clear
			currentAttribute = a;
		}
		else if (a == 1)
		{
			// add intense bit
			currentAttribute |= ATTRIB_INTENSE_MASK;
		}
		else if (a == 7)
		{
			// add inverse bit
			currentAttribute |= ATTRIB_INVERSE_MASK;
		}
		else if (a == 5) // UNDERLINE
		{
			// add underline bit
			currentAttribute |= ATTRIB_UNDERLINE_MASK;
		}
		else if (a >= 30 && a <= 37)
		{
			// we have 5 bits for back and fore color = 31 combinations
			// translate 30...37 -> 0...7
			// 11111000 = ATTRIB_COLOR_MASK
			// 00000111 = ~ATTRIB_COLOR_MASK
			currentAttribute = (byte) ((currentAttribute & ~ATTRIB_COLOR_MASK) | ((a - 30) << 3));
		}
		else if (a >= 40 && a <= 47)
		{
			// translate 40...47 -> 8...15
			currentAttribute = (byte) ((currentAttribute & ~ATTRIB_COLOR_MASK) | ((a - 40 + 8) << 3));
		}

		repaint(10);
	}

	public void putString ( String s )
	{
		char c[] = s.toCharArray();

		for (int i = 0; i < c.length; i++)
			putByte((byte) c[i]);
	}

	public void putBytes ( byte b[] )
	{
		for (int i = 0; i < b.length; i++)
			putByte(b[i]);
	}

	public void putByte ( byte b )
	{
		if (b == LF || b == VT || b == FF)
		{
			yCursor++;

			if (yCursor >= HEIGHT) // below last line
			{
				// stay in last row
				yCursor = HEIGHT - 1;

				if (idxBOL() < idxEOM() - WIDTH + 1)
				{
					// not at end of memory
					// scroll screen window down
					scrollScreenDown(1);
					// clear uncovered new line at bottom
					clearLine();
				}
				else
				{
					// at last line in memory
					// delete first line in memory
					// scroll memory up and clear last line
					scrollMemoryDown();
				}
			}
		}
		else if (b == CR)
		{
			xCursor = 0;
			// also reset any attribute
			currentAttribute = 0;
		}
		else if (b == BS)
		{
			xCursor--;

			if (xCursor < 0)
				xCursor = 0;
		}
		else if (b == HT)
		{
			// TAB to next TAB stop
			int xNewCursor = nextTab();

			if (m_insertMode)
			{
				// insert empty space
				insertCharsInLine(xNewCursor - xCursor);
			}
			xCursor = xNewCursor;
		}
		else if (b == NUL)
		{
			// skip NULL characters
		}
		else
		{
			// control character?
			if (b < 32 && !m_displayFunctions)
				return;

			int idx = idxCursor();

			if (m_insertMode)
			{
				// empty space for new character
				insertCharsInLine(1);
			}

			screen[idx] = (char) b;
			attributes[idx] = currentAttribute;
			xCursor++;

			if (wrapLines)
			{
				// if line wrap is enabled
				if (xCursor >= WIDTH)
				{
					// send CR character
					putByte((byte) 13);
					// send LF character (will scroll display)
					putByte((byte) 10);
				}
			}
			else
			{
				// stay in last column of current line
				if (xCursor >= WIDTH)
					xCursor = WIDTH - 1;
			}
		}

		repaint(10);
	}

	public boolean isKeyLabelsVisible ()
	{
		return keyLabelVisible;
	}

	public void setKeyLabelsVisible ( boolean visible )
	{
		keyLabelVisible = visible;
	}

	/**
	 * 
	 * @param which
	 *            - SOFTKEYS_MODE or SOFTKEYS_USER
	 */
	public void setKeyLabels ( int which )
	{
		softKeyMode = which;
		repaint(100);
	}
	public void toggleKeyLabels ()
	{
		if (softKeyMode == SOFTKEYS_MODE)
		softKeyMode = SOFTKEYS_USER;
		else
			softKeyMode = SOFTKEYS_MODE;
			repaint(100);
	}

	public String getKeyLabel ( int row, int col )
	{
		if (softKeyMode == SOFTKEYS_MODE)
			return softKeysSystem.getButtonText(row, col);
		else
			return softKeysUser.getButtonText(row, col);
	}

	/**
	 * Draw a dark background and a lighter tube area with slightly curved
	 * edges. The actual terminal output goes into a rectangular area.
	 * 
	 * @param g1
	 *            The context to draw on.
	 */
	public void redrawScreen ( Graphics g1 )
	{
		Rectangle rc = getBounds();

		if (rc.width < 2 * borderWidth || rc.height < 2 * borderWidth)
			return;

		Graphics2D g = (Graphics2D) g1;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		Stroke s = new BasicStroke(1.0f, BasicStroke.CAP_ROUND,
				BasicStroke.JOIN_ROUND);
		g.setStroke(s);

		g.setPaintMode();

		// fill entire drawing area darker than defined background color
		g.setColor(backColor.darker().darker().darker());
		g.fillRect(rc.x, rc.y, rc.width, rc.height);

		/**
		 * <pre>
		 *  (rc.x,rc.y)
		 *      +------o------------------------------+
		 *      |      .         bw            .      |
		 *      |      .         v             .      |
		 *      +      +-----------------------o      |
		 *      |      |                       |      |
		 *      |<-bw->|                       |<-bw->|
		 *      |      |                       |      |
		 *      |      |                       |      |
		 *      +      o-----------------------+      |
		 *      |      .         ^             .      |
		 *      |      .         bw            .      |
		 *      +------------------------------o------+
		 * </pre>
		 */

		// fill tube area slightly brighter than defined background color
		g.setColor(backColor.brighter());

		// fill center rectangle
		g.fillRect(rc.x + borderWidth, rc.y + borderWidth, rc.width - 2
				* borderWidth, rc.height - 2 * borderWidth);

		// top arc
		g.fillArc(rc.x + borderWidth, rc.y, rc.width - 2 * borderWidth,
				2 * borderWidth, 0, 180);
		// bottom arc
		g.fillArc(rc.x + borderWidth, rc.y + rc.height - 2 * borderWidth,
				rc.width - 2 * borderWidth, 2 * borderWidth, 180, 180);
		// left arc
		g.fillArc(rc.x, rc.y + borderWidth, 2 * borderWidth, rc.height - 2
				* borderWidth, 90, 180);
		// right arc
		g.fillArc(rc.x + rc.width - 2 * borderWidth, rc.y + borderWidth,
				2 * borderWidth, rc.height - 2 * borderWidth, 270, 180);

		// move origin to upper left corner of to rectangular center region
		g.translate(borderWidth, borderWidth);

		if (!paintCursorOnly)
		{
			g.setFont(theFont);

			// start of currently visible view
			int idx = idxStart;
			Color cFore = foreColor;
			Color cBack = backColor;

			for (int row = 0; row < HEIGHT; row++)
			{
				int currAttribute = -1;

				for (int col = 0; col < WIDTH; col++)
				{
					if (attributes[idx] != ATTRIB_EMPTY_MASK)
					{
						if (attributes[idx] != currAttribute)
						{
							// change only if attribute is different
							currAttribute = attributes[idx];

							// attribute
							// CCCCC.UVI = bits 7...0
							// byte ATTRIB_INTENSE_MASK = (byte) 0x01;
							// byte ATTRIB_INVERSE_MASK = (byte) 0x02;
							// byte ATTRIB_UNDERLINE_MASK = (byte) 0x04;
							// byte ATTRIB_COLOR_MASK = (byte) (0x1F << 3); //
							// 0...31
							// color

							if (currAttribute == 0)
							{
								cFore = foreColor;
								cBack = backColor;
							}
							else if ((currAttribute & ATTRIB_INTENSE_MASK) == ATTRIB_INTENSE_MASK)
							{
								// brighten current foreground color (BGRA)
								cFore = new Color(
										foreColor.getRGB() | 0xF0F0F000);
								cBack = backColor;
							}
							else if ((currAttribute & ATTRIB_INVERSE_MASK) == ATTRIB_INVERSE_MASK)
							{
								cBack = foreColor;
								cFore = backColor;
							}
							else if ((currAttribute & ATTRIB_COLOR_MASK) != 0)
							{
								// 5 color bits
								int color = (currAttribute & ATTRIB_COLOR_MASK) >> 4;

								if (color < 8)
								{
									// 0...7
									// foreground color
									cFore = colorMap[color];
								}
								else
								{
									color -= 8;
									if (color < 8)
									{
										// 0...7
										// background color
										cBack = colorMap[color];
									}
								}
							}
						}

						// draw one character at a time

						if (cBack != backColor)
						{
							// fill background with cBack color
							g.setPaintMode();
							g.setColor(cBack);
							g.fillRect(col * dx, row * dy + descent - 2, dx, dy);

							// paint text in cBack color over background
							g.setColor(cFore);
							g.setPaintMode();
						}
						else
						{
							// paint text in cFore color over background
							g.setColor(cFore);
							g.setXORMode(cFore);
						}

						// using the TrueType font
						// g.setColor(cFore);
						// g.drawChars(screen, idx, 1, col * dx, (row + 1) *
						// dy);

						// using the bitmapped font
						drawChar(g, screen[idx], col * dx, (row + 1) * dy);

						if ((currAttribute & ATTRIB_UNDERLINE_MASK) == ATTRIB_UNDERLINE_MASK)
							drawChar(g, '_', col * dx, (row + 1) * dy);
					}

					idx++;
				}

			}

			if (EXTRALINES > 0)
			{
				g.setColor(cFore);
				g.setXORMode(cFore);

				// output cursor position relative to view in 1-based R/C values
				// R C in line 25
				String str = String.format("%02d %02d", new Object[] {
						new Integer(yCursor + 1), new Integer(xCursor + 1) });

				drawString(g, str, (WIDTH - str.length()) * dx / 2,
						(HEIGHT + 2) * dy);

				// message line)
				/*
				 * int globalRow = (idxBOL() - idxBOM()) / WIDTH + 1;
				 * 
				 * str = String.format("%2d %2d", new Object[] { new
				 * Integer(globalRow), new Integer(x) });
				 * 
				 * c = str.toCharArray();
				 * 
				 * col = (WIDTH - c.length)*dx / 2; row = HEIGHT + 3;
				 * 
				 * for (int i = 0; i < c.length; i++) { g.drawChars(c, i, 1,
				 * col, (row + 1) * dy); col+=dx; }
				 */

				// in Java 1.6 we must use
				Calendar c = Calendar.getInstance();
				str = String.format("%02d:%02d",
						new Object[] {
								new Integer(c.get(Calendar.HOUR_OF_DAY)),
								new Integer(c.get(Calendar.MINUTE)) });

				// in Java 1.8 we can use
				// ZonedDateTime t = ZonedDateTime.now();
				// str = String.format(
				// "%02d:%02d",
				// new Object[] { new Integer(t.getHours()),
				// new Integer(t.getMinutes()) });

				drawString(g, str, (WIDTH - str.length()) * dx / 2,
						(HEIGHT + 3) * dy);

				// indicator for insert mode
				if (m_insertMode)
				{
					// centered
					drawString(g, "Ins", (WIDTH - 3) * dx / 2, (HEIGHT + 4)
							* dy);
				}
				// indicator for Keyboard locked
				if (m_keyboardLocked)
				{
					// columns 2-12
					drawString(g, "Kbd Locked", dx, (HEIGHT + 4) * dy + dy / 3);
				}

				if (keyLabelVisible)
				{
					if (softKeyMode == SOFTKEYS_MODE)
					{
						// terminal modes
						softKeysSystem.paint(g, this);
					}
					else if (softKeyMode == SOFTKEYS_USER)
					{
						softKeysUser.paint(g, this);
					}

				}
				// back to foreground color for cursor plotting
				g.setColor(cFore);
			}

			// test(g);
		}

		if (cursorVisible && cursorBlink)
		{
			// show cursor
			g.setXORMode(backColor);
			g.fillRect(xCursor * dx, yCursor * dy + descent + 1, dx, dy);
		}

		// restore
		g.translate(-borderWidth, -borderWidth);
	}

	private void drawChar ( Graphics gDest, char c, int x, int y )
	{
		// bitmap file with transparent background
		// three rows of 30 pixels height
		// each character is 16 x 30 pixels (W_CELL x H_CELL)
		// which corresponds to 8 x 15 with half pixel shift

		// source position in font image
		int sx = (int) c * W_CELL;
		int sy = 0;

		// select proper row
		if (currentCharSet == CS_ROMAN)
			sy = 0;
		else if (currentCharSet == CS_LINEDRAW)
			sy = H_CELL;
		else if (currentCharSet == CS_MATH)
			sy = 2 * H_CELL;

		gDest.drawImage(imgFont, x, y - 12, x + dx, y - 12 + dy, sx, sy, sx
				+ W_CELL, sy + H_CELL, this);
	}

	protected void drawString ( Graphics g, String s, int x, int y )
	{
		char c[] = s.toCharArray();

		for (int i = 0; i < c.length; i++)
		{
			drawChar(g, c[i], x, y);
			x += dx;
		}
	}

	public void paint ( Graphics g )
	{
		redrawScreen(g);
	}

	/**
	 * Neglects attributes.
	 * 
	 * @return the text content of the terminal memory buffer. In Wndows each
	 *         line is terminated by a '\n', in all other operating systems the
	 *         sequence '\n\r' is used.
	 */
	public String getText ()
	{
		String lineEnd;

		if (System.getProperty("os.name").startsWith("Windows"))
		{
			lineEnd = "\n";
		}
		else
		{
			lineEnd = "\r\n";
		}

		StringBuilder sb = new StringBuilder();

		int idx = 0;
		for (int row = 0; row < HEIGHT; row++)
		{
			// int currAttribute = -1;

			for (int col = 0; col < WIDTH; col++)
			{
				if (attributes[idx] != ATTRIB_EMPTY_MASK)
				{
					sb.append(screen[idx]);
				}
				idx++;
			}

			sb.append(lineEnd);
		}
		return sb.toString();
	}

	public void mouseClicked ( MouseEvent e )
	{
		// move to display area
		e.translatePoint(-borderWidth, -borderWidth);
		// will return [0...7] if a key was clicked, otherwise -1
		int softKey = softKeysSystem.hitTest(e.getPoint(), this);

		if (softKey >= 0)
		{
			boolean shift = e.isShiftDown();
			theApp.handleFunctionKey(softKey + KeyEvent.VK_F1, shift);
		}
	}

	public void mousePressed ( MouseEvent e )
	{
		// TODO Auto-generated method stub

	}

	public void mouseReleased ( MouseEvent e )
	{
		// TODO Auto-generated method stub

	}

	public void mouseEntered ( MouseEvent e )
	{
		// TODO Auto-generated method stub

	}

	public void mouseExited ( MouseEvent e )
	{
		// TODO Auto-generated method stub

	}

	private static void addPopup ( Component component, final JPopupMenu popup )
	{
		component.addMouseListener(new MouseAdapter()
		{
			public void mousePressed ( MouseEvent e )
			{
				if (e.isPopupTrigger())
				{
					showMenu(e);
				}
			}

			public void mouseReleased ( MouseEvent e )
			{
				if (e.isPopupTrigger())
				{
					showMenu(e);
				}
			}

			private void showMenu ( MouseEvent e )
			{
				popup.show(e.getComponent(), e.getX(), e.getY());
			}
		});
	}

	public void actionPerformed ( ActionEvent e )
	{
		String cmd = e.getActionCommand();
		if (cmd.equals("COPY_TEXT"))
		{
			String s = getText();
			Clipboard theClipboard = Toolkit.getDefaultToolkit()
					.getSystemClipboard();
			theClipboard.setContents(new StringSelection(s), null);
		}
	}

	private class CursorBlinker extends TimerTask
	{

		public void run ()
		{
			// repaint cursor only.
			cursorBlink = !cursorBlink;

			// paintCursorOnly = true;
			repaint(100);
			paintCursorOnly = false;
		}
	}
}
