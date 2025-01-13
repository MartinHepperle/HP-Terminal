package mh;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/**
 * 
 * @author Martin Hepperle
 * 
 */
public class GraphicsScreen extends JPanel implements MouseListener,
		KeyListener, ActionListener, Transferable
{
	private String m_Name;
	private BufferedImage m_Image;
	private int m_backColor;
	private int m_foreColor;
	// can be set by user or can track m_foreColor
	private int m_textColor;
	private boolean m_trackPrimary; // true: m_textColor == m_foreColor, else
									// set by user
	private int m_textSize;
	private int m_drawMode;
	private BasicStroke m_lineStyle;
	private Point m_ptCurrent;
	private boolean m_penDown;

	/** Graphics cursor */
	private boolean m_graphicsCursor;
	// flag for checking whether a mouse or keyboard key was pressed
	private boolean m_clicked;
	// the sampled cursor position
	private Point m_ptCursor;
	// the sampled key code
	private int m_keyCode;

	// 8 colors
	Color colorMap[] = new Color[8];
	boolean isDirty;
	private VectorFont vf;

	private Frame m_ParentFrame;

	public GraphicsScreen(Frame f, String name, int w, int h)
	{
		m_ParentFrame = f;

		m_Name = name;

		m_ParentFrame.setTitle(m_Name + " - Graphics Screen (" + w + "x" + h
				+ ")");

		vf = new VectorFont();

		addMouseListener(this);
		addKeyListener(this);

		JPopupMenu popupMenu = new JPopupMenu();
		addPopup(this, popupMenu);

		JMenuItem mntmCopyBitmap = new JMenuItem("Copy Bitmap");
		mntmCopyBitmap.setMnemonic(KeyEvent.VK_C);
		mntmCopyBitmap.setActionCommand("COPY_BITMAP");
		mntmCopyBitmap.addActionListener(this);
		popupMenu.add(mntmCopyBitmap);

		setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

		setScreenSize(w, h);

		resetDefaults(true);

		f.pack();
		repaint(100);

		// test();
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
		if (cmd.equals("COPY_BITMAP"))
		{
			copyImage();
		}
	}

	/**
	 * Returns an object which represents the data to be transferred.
	 * 
	 * @param flavor
	 *            DataFlavor
	 * @return Object
	 */
	public Object getTransferData ( DataFlavor flavor )
	{
		Object o = null;

		if (flavor.getHumanPresentableName().equals("image/x-java-image"))
		{
			o = m_Image;
		}
		return (o);
	}

	/**
	 * Returns an array of DataFlavor objects indicating the flavors the data
	 * can be provided in.
	 * 
	 * @return DataFlavor[]
	 */
	public DataFlavor[] getTransferDataFlavors ()
	{
		// getHumanPresentableName -> image/x-java-image
		// getSubType -> x-java-image
		// getMimeType -> image/x-java-image; class=java.awt.Image

		DataFlavor df[] = new DataFlavor[1];
		df[0] = new DataFlavor("image/x-java-image; class=java.awt.Image",
				"image/x-java-image");

		return (df);
	}

	/**
	 * Returns whether or not the specified data flavor is supported for this
	 * object
	 * 
	 * @param flavor
	 *            DataFlavor
	 * @return boolean true if DataFalvor is "image/x-java-image".
	 */
	public boolean isDataFlavorSupported ( DataFlavor flavor )
	{
		return (flavor.getMimeType().equals("image/x-java-image"));

	}

	// some tests for paint modes
	private void test ()
	{
		Graphics g = m_Image.getGraphics();
		VectorFont vf = new VectorFont();

		g.setPaintMode();
		g.setColor(Color.GREEN);
		g.fillRect(0, 0, 512, 399);
		g.setColor(Color.RED);
		g.fillRect(0, 0, 256, 399);

		Color cBack = Color.BLACK;
		Color cFore = Color.YELLOW;
		Color cText = Color.MAGENTA;

		// modes 1-3, 6 o.k.

		int mode = 5;

		if (mode == 0)
		{
			// Mode 0: NO EFFECT
		}
		else if (mode == 1)
		{
			// Mode 1: CLEAR
			g.setColor(cBack);
			Polygon p = new Polygon();
			p.addPoint(256 - 128, 195 - 0);
			p.addPoint(256, 195 - 128);
			p.addPoint(256 + 128, 195 - 0);
			p.addPoint(256, 195 + 128);
			g.fillPolygon(p);

			g.setColor(cBack);
			g.setFont(g.getFont().deriveFont(25f));
			g.drawString("DRAWING", 128 + 64, 40);
			g.drawString("MODE " + mode, 144 + 64, 70);
		}
		else if (mode == 2)
		{
			// Mode 2: SET
			g.setColor(cFore);
			Polygon p = new Polygon();
			p.addPoint(256 - 128, 195 - 0);
			p.addPoint(256, 195 - 128);
			p.addPoint(256 + 128, 195 - 0);
			p.addPoint(256, 195 + 128);
			g.fillPolygon(p);

			g.setColor(cText);
			g.setFont(g.getFont().deriveFont(25f));
			g.drawString("DRAWING", 128 + 64, 40);
			g.drawString("MODE " + mode, 144 + 64, 70);
		}
		else if (mode == 3)
		{
			// Mode 3: COMPLEMENT 1
			g.setColor(inverseColor(cFore));
			g.setXORMode(cFore);
			Polygon p = new Polygon();
			p.addPoint(256 - 128, 195 - 0);
			p.addPoint(256, 195 - 128);
			p.addPoint(256 + 128, 195 - 0);
			p.addPoint(256, 195 + 128);
			g.fillPolygon(p);

			g.setColor(inverseColor(cText));
			g.setXORMode(cText);
			g.setFont(g.getFont().deriveFont(25f));
			g.drawString("DRAWING", 128 + 64, 40);
			g.drawString("MODE " + mode, 144 + 64, 70);
		}
		else if (mode == 4)
		{
			// Mode 4: JAM
			g.setColor(cFore);
			Polygon p = new Polygon();
			p.addPoint(256 - 128, 195 - 0);
			p.addPoint(256, 195 - 128);
			p.addPoint(256 + 128, 195 - 0);
			p.addPoint(256, 195 + 128);
			g.fillPolygon(p);

			g.setFont(g.getFont().deriveFont(25f));
			Rectangle2D rc = g.getFontMetrics().getStringBounds("DRAWING", g);
			g.setColor(cBack);
			g.fillRect(128 + 64 + (int) rc.getX(), 40 + (int) rc.getY(),
					(int) rc.getWidth(), (int) rc.getHeight());
			g.setColor(cText);
			g.drawString("DRAWING", 128 + 64, 40);

			rc = g.getFontMetrics().getStringBounds("MODE " + mode, g);
			g.setColor(cBack);
			g.fillRect(144 + 64 + (int) rc.getX(), 70 + (int) rc.getY(),
					(int) rc.getWidth(), (int) rc.getHeight());
			g.setColor(cText);
			g.drawString("MODE " + mode, 144 + 64, 70);
		}
		else if (mode == 5)
		{
			// Mode 5:
			g.setColor(cFore);
			Polygon p = new Polygon();
			p.addPoint(256 - 128, 195 - 0);
			p.addPoint(256, 195 - 128);
			p.addPoint(256 + 128, 195 - 0);
			p.addPoint(256, 195 + 128);
			g.fillPolygon(p);

			g.setFont(g.getFont().deriveFont(25f));
			// Rectangle2D rc = g.getFontMetrics().getStringBounds("DRAWING",
			// g);
			g.setColor(cText);
			// g.fillRect(128 + 64 + (int) rc.getX(), 40 + (int) rc.getY(),
			// (int) rc.getWidth(), (int) rc.getHeight());
			g.setColor(inverseColor(cBack));
			g.setXORMode(inverseColor(cText));
			g.drawString("DRAWING", 128 + 64, 40);

			// Rectangle2D rc = g.getFontMetrics().getStringBounds("MODE " +
			// mode, g);
			// g.setColor(cFore);
			// g.fillRect(144 + 64 + (int) rc.getX(), 70 + (int) rc.getY(),
			// (int) rc.getWidth(), (int) rc.getHeight());
			g.setColor(inverseColor(cBack));
			// g.setXORMode((cText));
			// g.drawString("MODE " + mode, 144 + 64, 70);
			vf.setSize(18, 30);
			vf.drawString(g, "MODE " + mode, 144 + 64, 70);
		}
		else if (mode == 6)
		{
			// Mode 3: COMPLEMENT 2
			g.setColor(cBack);
			g.setXORMode(cFore);
			Polygon p = new Polygon();
			p.addPoint(256 - 128, 195 - 0);
			p.addPoint(256, 195 - 128);
			p.addPoint(256 + 128, 195 - 0);
			p.addPoint(256, 195 + 128);
			g.fillPolygon(p);
			g.setXORMode(cText);
			g.setFont(g.getFont().deriveFont(25f));
			g.drawString("DRAWING", 128 + 64, 40);
			g.drawString("MODE " + mode, 144 + 64, 70);
		}

		g.translate(0, getHeight() - 1);

		g.drawLine(8, -10, 12, -10);
		g.drawLine(10, -8, 10, -12);
		vf.setSize(10, 20);
		vf.drawString(g, "Hello World 1/2 {[]}@Фжмпемно", 10, -10);

		repaint(100);
	}

	public void resetDefaults ( boolean hard )
	{
		m_backColor = 7;
		m_foreColor = 0;
		// default: track m_foreColor
		setTextColor(1);
		setTextSize(1);

		m_ptCurrent = new Point();
		m_penDown = false;
		m_drawMode = 2;
		isDirty = false;
		/** initially at (0,0) */
		m_ptCursor = new Point();
		m_graphicsCursor = false;
		m_clicked = false;

		// HP 2627 default foreground colors
		colorMap[0] = new Color(255, 255, 255); // white
		colorMap[1] = new Color(255, 0, 0); // red
		colorMap[2] = new Color(0, 255, 0); // green
		colorMap[3] = new Color(255, 255, 0); // yellow
		colorMap[4] = new Color(0, 0, 255); // blue
		colorMap[5] = new Color(255, 0, 255); // magenta
		colorMap[6] = new Color(0, 255, 255); // cyan
		colorMap[7] = new Color(0, 0, 0); // black

		setLineStyle(1);

		if (hard)
			clear();
	}

	void setScreenSize ( int w, int h )
	{
		// ANSI: 0,0,640,480
		// 2648: 0,0,719,359
		// 2627: 0,0,511,389
		w = Math.max(Math.min(Math.abs(w), 720), 512); // 512...720
		h = Math.max(Math.min(Math.abs(h), 480), 360); // 360...480

		m_Image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

		Dimension d = new Dimension(w, h);
		setPreferredSize(d);
		setSize(d);

		if (null != m_ParentFrame)
		{
			m_ParentFrame.setTitle(m_Name + " - Graphics Screen (" + w + "x"
					+ h + ")");
			m_ParentFrame.pack();
		}
	}

	/**
	 * Toggle the display of the graphics cursor.
	 * 
	 * @param visible
	 */
	public void showGraphicsCursor ( boolean visible )
	{
		m_graphicsCursor = visible;

		if (m_graphicsCursor)
			setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		else
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}

	/**
	 * 
	 * @return the key code sampled when a waitForClick() state was enabled:
	 *         <ul>
	 *         <li>32...127: key code</li>
	 *         <li>232: left mouse button</li>
	 *         <li>233: right mouse button</li>
	 *         <li>234: middle mouse button</li>
	 *         </ul>
	 */
	public int getKeyCode ()
	{
		return m_keyCode;
	}

	/**
	 * 
	 * @return
	 */
	public Point getCursorPosition ()
	{
		return new Point(m_ptCursor);
	}

	public void setCursorPosition ( int x, int y )
	{
		m_ptCursor.x = x;
		m_ptCursor.y = y;
	}

	public void incrementCursorPosition ( int dx, int dy )
	{
		m_ptCursor.x += dx;
		m_ptCursor.y += dy;
	}

	private Color inverseColor ( Color c )
	{
		return new Color(~c.getRGB());
	}

	public void paint ( Graphics g )
	{
		g.drawImage(m_Image, 0, 0, getWidth(), getHeight(), 0, 0, getWidth(),
				getHeight(), null);
	}

	public void clear ()
	{
		clear(m_backColor);
	}

	public void clear ( int color )
	{
		Graphics g = m_Image.getGraphics();
		g.setColor(colorMap[color % colorMap.length]);
		g.fillRect(0, 0, m_Image.getWidth(), m_Image.getHeight());

		isDirty = false;
		repaint(100);
	}

	/**
	 * 
	 * @return the current position of the pen.
	 */
	public Point getPenPosition ()
	{
		return m_ptCurrent;
	}

	/**
	 * 
	 * @return true if pen is down (after a lineto), false if not (after a
	 *         moveto)
	 */
	public boolean getPenState ()
	{
		return m_penDown;
	}

	/**
	 * Define line style similar to HP 2648A line styles. Does not support user
	 * defined styles.
	 * 
	 * @param n
	 *            the line style:<br>
	 *            1=continuous,<br>
	 *            2,3,4=long-dash-dot,<br>
	 *            5=long-dash,<br>
	 *            6=short-dash,<br>
	 *            7=dotted,<br>
	 *            8=short-dash-dot<br>
	 *            9=triple-dot<br>
	 *            10=dash-dot-dot<br>
	 *            11=dot at start
	 */
	public void setLineStyle ( int n )
	{
		float dash[];
		switch (n)
		{
		case 2:
		case 3:
		case 4: // - . - .
			dash = new float[] { 10.0f, 3.0f, 2.0f, 3.0f };
			break;
		case 5: // -- -- --
			dash = new float[] { 10.0f, 3.0f };
			break;
		case 6: // - - - -
			dash = new float[] { 5.0f, 5.0f };
			break;
		case 7: // .....
			dash = new float[] { 2.0f, 2.0f };
			break;
		case 8: // -.-.
			dash = new float[] { 5.0f, 3.0f, 2.0f, 3.0f };
			break;
		case 9: // ... ...
			dash = new float[] { 2.0f, 2.0f, 2.0f, 2.0f, 2.0f, 6.0f };
			break;
		case 10: // -..-
			dash = new float[] { 9.0f, 3.0f, 2.0f, 3.0f, 2.0f, 3.0f };
			break;
		case 11: // dots only at start
			dash = new float[] { 1.0f, 32000.0f };
			break;
		default:
			// 1=continuous line
			dash = new float[] { 1.0f };
			break;
		}

		m_lineStyle = new BasicStroke(1.0f, BasicStroke.CAP_ROUND,
				BasicStroke.JOIN_ROUND, 1.0f, dash, 0.0f);
	}

	/**
	 * Move the pen to the given point.
	 * 
	 * @param pt
	 *            the point to move to. It becomes the "current point".
	 */
	public void moveto ( Point pt )
	{
		m_ptCurrent.setLocation(pt);
		m_penDown = false;
	}

	/**
	 * Draw a line from the "current point" to the given point.
	 * 
	 * @param pt
	 *            the point to draw to.
	 */
	public void lineto ( Point pt )
	{
		Graphics2D g = (Graphics2D) m_Image.getGraphics();
		g.translate(0, getHeight() - 1);

		g.setStroke(m_lineStyle);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

		if (m_drawMode == 0)
		{
			// Mode 0: NO EFFECT
		}
		else
		{
			if (m_drawMode == 1)
			{
				// Mode 1: CLEAR
				g.setColor(colorMap[m_backColor]);
			}
			else if (m_drawMode == 2)
			{
				// Mode 2: SET
				g.setColor(colorMap[m_foreColor]);
			}
			else if (m_drawMode == 3)
			{
				// Mode 3: COMPLEMENT 1
				// g.setColor(inverseColor(colorMap[m_foreColor]));
				// g.setXORMode(colorMap[m_foreColor]);
				g.setColor(Color.BLACK);
				g.setXORMode(Color.WHITE);
			}
			else if (m_drawMode == 4)
			{
				// Mode 4: JAM
				g.setColor(colorMap[m_foreColor]);
			}
			else
			{
				// Mode 5: COMPLEMENT 2
				g.setColor(colorMap[m_foreColor]);
				g.setXORMode(colorMap[m_backColor]);
			}

			g.drawLine(m_ptCurrent.x, -m_ptCurrent.y, pt.x, -pt.y);
		}

		m_ptCurrent.setLocation(pt);
		m_penDown = true;
		g.dispose();
		isDirty = true;
		this.paint(g);
		repaint(100);
	}

	/**
	 * Fill the given rectangle.
	 * 
	 * @param x
	 *            - the x-position of the upper left corner point.
	 * @param y
	 *            - the y-position of the upper left corner point.
	 * @param width
	 *            - the width of the rectangle
	 * @param height
	 *            - the height of the rectangle
	 */
	public void fillRect ( int x, int y, int width, int height )
	{
		Graphics g = m_Image.getGraphics();
		g.translate(0, getHeight() - 1);

		if (m_drawMode == 0)
		{
			// Mode 0: NO EFFECT
		}
		else
		{
			if (m_drawMode == 2)
			{
				// Mode 2: SET
				g.setColor(colorMap[m_foreColor]);
			}
			else if (m_drawMode == 1)
			{
				// Mode 1: CLEAR
				g.setColor(colorMap[m_backColor]);
			}
			else if (m_drawMode == 3)
			{
				// Mode 3: COMPLEMENT 1
				// g.setColor(inverseColor(colorMap[m_foreColor]));
				// g.setXORMode(colorMap[m_foreColor]);
				g.setColor(Color.BLACK);
				g.setXORMode(Color.WHITE);
			}
			else if (m_drawMode == 4)
			{
				// Mode 4: JAM
				g.setColor(colorMap[m_foreColor]);
			}
			else
			{
				// Mode 5: COMPLEMENT 2
				g.setColor(colorMap[m_foreColor]);
				g.setXORMode(colorMap[m_backColor]);
			}

			g.fillRect(x, -y - height, width, height);
		}
		g.dispose();
		isDirty = true;
		repaint(100);
	}

	public void setDrawMode ( int mode )
	{
		m_drawMode = mode;
	}

	public void drawText ( String s )
	{
		Graphics g = m_Image.getGraphics();

		g.translate(0, getHeight() - 1);

		if (m_drawMode == 0)
		{
			// Mode 0: NO EFFECT
		}
		else
		{
			// if no vector font is used
			// Font f = new Font(Font.MONOSPACED, Font.PLAIN, 9*m_textSize);
			// Font f = new Font("Lucida Sans Typewriter", Font.PLAIN,
			// 9 * m_textSize);
			// g.setFont(f);

			if (m_drawMode == 1)
			{
				// Mode 1: CLEAR
				g.setPaintMode();
				g.setColor(colorMap[m_backColor]);
			}
			else if (m_drawMode == 2)
			{
				// Mode 2: SET
				g.setPaintMode();
				// paint background
				g.setColor(colorMap[m_backColor]);
				Rectangle2D rc = g.getFontMetrics().getStringBounds(s, g);
				g.fillRect(m_ptCurrent.x + (int) rc.getX(), m_ptCurrent.y
						+ (int) rc.getY(), (int) rc.getWidth(),
						(int) rc.getHeight());
				g.setColor(colorMap[m_textColor]);
			}
			else if (m_drawMode == 3)
			{
				// Mode 3: COMPLEMENT 1
				// g.setColor(inverseColor(colorMap[m_foreColor]));
				// g.setXORMode(colorMap[m_foreColor]);
				g.setColor(Color.BLACK);
				g.setXORMode(Color.WHITE);
			}
			else if (m_drawMode == 4)
			{
				// Mode 4: JAM
				g.setPaintMode();
				Rectangle2D rc = g.getFontMetrics().getStringBounds(s, g);
				g.setColor(colorMap[m_backColor]);
				g.fillRect(m_ptCurrent.x + (int) rc.getX(), m_ptCurrent.y
						+ (int) rc.getY(), (int) rc.getWidth(),
						(int) rc.getHeight());
				g.setColor(colorMap[m_textColor]);
			}
			else
			{
				// Mode 5: COMPLEMENT 2
				g.setColor(colorMap[m_textColor]);
				Rectangle2D rc = g.getFontMetrics().getStringBounds(s, g);
				g.fillRect(m_ptCurrent.x + (int) rc.getX(), m_ptCurrent.y
						+ (int) rc.getY(), (int) rc.getWidth(),
						(int) rc.getHeight());
				g.setColor(colorMap[m_textColor]);
				g.setXORMode(colorMap[m_backColor]);
			}

			vf.drawString(g, s, m_ptCurrent.x, -m_ptCurrent.y);
			// g.drawString(s, m_ptCurrent.x, -m_ptCurrent.y - 1);
		}
		g.dispose();
		isDirty = true;
		repaint(100);
	}

	/**
	 * 
	 * @param idxColor
	 *            - if == 0: default: track primary pen
	 */
	public void setTextColor ( int idxColor )
	{
		if (idxColor == 0)
		{
			m_trackPrimary = true;
			m_textColor = m_foreColor;
		}
		else
		{
			m_trackPrimary = false;
			m_textColor = (idxColor - 1) % colorMap.length;
		}
	}

	/**
	 * 
	 * @param size
	 *            - the size of an uppercase letter, e.g. 'A'
	 *            <ul>
	 *            <li>size = 1: 5x7 pixels</li>
	 *            <li>size = 2: 10x14 pixels</li>
	 *            <li>size = 3: 15x21 pixels</li>
	 *            <li>size = 4: 20x28 pixels</li>
	 *            <li>size = 5: 25x35 pixels</li>
	 *            <li>size = 6: 30x42 pixels</li>
	 *            <li>size = 7: 35x49 pixels</li>
	 *            <li>size = 8: 40x56 pixels</li>
	 *            </ul>
	 */
	public void setTextSize ( int size )
	{
		m_textSize = size;
		// translate to pixel width and height /aspect ratio of capital
		// character = 7/5)
		vf.setSize(7 * size * 5 / 7, 7 * size);
	}

	void setTextSlant ( boolean slanted )
	{
		vf.setSlant(slanted);
	}

	/**
	 * Set the text orientation.
	 * 
	 * @param orientation
	 *            the direction of the baseline in degrees (extension of the HP
	 *            specification)
	 * 
	 */
	void setTextOrientation ( int orientation )
	{
		vf.setOrientation(orientation);
	}

	public void setForeColor ( int idxColor )
	{
		// 0 ... 7
		m_foreColor = (idxColor - 1) % colorMap.length;

		if (m_trackPrimary)
		{
			// text pen tracks primary pen
			m_textColor = m_foreColor;
		}
	}

	public void setBackColor ( int idxColor )
	{
		m_backColor = (idxColor - 1) % colorMap.length;
	}

	/**
	 * Save the current image to a file in PNG format. No file is written if the
	 * image is empty.
	 * 
	 * @param fileName
	 *            - the name of the file to write. Any extension is stripped and
	 *            replaced by ".png".
	 * @return - true is a file was written, otherwise false.
	 * @throws IOException
	 *             - if something goes wrong.
	 */
	public boolean saveImage ( String fileName ) throws IOException
	{
		if (isDirty)
		{
			String outFileName = fileName;

			// strip trailing extension (if any)
			int iDot = outFileName.lastIndexOf('.');
			if (iDot > 0)
			{
				outFileName = outFileName.substring(0, iDot);
			}

			outFileName = outFileName + ".png";

			File f = new File(outFileName);
			ImageIO.write(m_Image, "png", f);
		}
		return isDirty;
	}

	/**
	 * Copy the current bitmap image to the system clipboard.
	 */
	private void copyImage ()
	{
		Clipboard theClipboard = Toolkit.getDefaultToolkit()
				.getSystemClipboard();
		theClipboard.setContents(this, null);
	}

	/**
	 * Enables sampling of a mouse click or key press. Use the wasClicked()
	 * method to find out whether a mouse or keyboard key was pressed.
	 */
	public void waitForClick ()
	{
		// reset flag to enable sampling
		m_clicked = false;
		requestFocus();
	}

	/**
	 * 
	 * @return true if a mouse click or key press was registered.
	 */
	public boolean wasClicked ()
	{
		return m_clicked;
	}

	/**
	 * Samples the mouse pointer coordinates when the graphics cursor is
	 * visible. Stores a virtual key code for mouse button. Sets a flag which
	 * can be read with the wasClicked() method.
	 */
	public void mouseClicked ( MouseEvent e )
	{
		handleMousePress(e);
	}

	public void mousePressed ( MouseEvent e )
	{
		handleMousePress(e);
	}

	public void mouseReleased ( MouseEvent e )
	{
		handleMousePress(e);
	}

	public void mouseEntered ( MouseEvent e )
	{
	}

	public void mouseExited ( MouseEvent e )
	{
	}

	/**
	 * Called when a mouse button is pressed. If the graphics cursor is visible
	 * the current cursor position and the character code 232 (left mouse
	 * button) or 233 (right mouse button) or 234 (middle mouse button) are
	 * stored. <br>
	 * These codes are translated by the CP/M GSX system display driver into 32
	 * (' '), 33 ('!') respectively 34 ('"').
	 * 
	 * @param e
	 *            the MouseEvent to test.
	 */
	private void handleMousePress ( MouseEvent e )
	{
		if (m_graphicsCursor)
		{
			m_ptCursor = new Point(e.getPoint());
			if (e.getButton() == MouseEvent.BUTTON1)
				m_keyCode = 232; // left
			else if (e.getButton() == MouseEvent.BUTTON3)
				m_keyCode = 233; // right
			else
				m_keyCode = 234; // middle

			// set flag
			m_clicked = true;
		}
	}

	/**
	 * Samples the current mouse pointer coordinates when the graphics cursor is
	 * visible. Stores the key character code and sets a flag which can be
	 * tested with the wasClicked() method.
	 */
	public void keyTyped ( KeyEvent e )
	{
		handleKeyTyped(e);
	}

	public void keyPressed ( KeyEvent e )
	{
		// you press a key and nothing happens.
	}

	public void keyReleased ( KeyEvent e )
	{
		// you release a key and nothing happens.
	}

	/**
	 * Called when a keyboard key is typed. If the graphics cursor is visible
	 * the current cursor position and the key character code are stored.
	 * 
	 * @param e
	 */
	private void handleKeyTyped ( KeyEvent e )
	{
		if (m_graphicsCursor)
		{
			m_ptCursor = new Point(getMousePosition());
			m_keyCode = e.getKeyChar();
			// set flag
			m_clicked = true;
		}
	}
}
