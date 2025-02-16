package mh;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;

/**
 * row 1 [shift f1] [shift f2] [shift f3] [shift f4] - [5] [6] [7] [8]<br>
 * row 0 [norml f1] [norml f2] [norml f3] [norml f4] - [5] [6] [7] [8]<br>
 * column....0..........1..........2..........3.........4...5...6...7<br>
 *
 * @author ea55
 *
 */
public class SoftKeys
{
	final static int ROW_TOP = 0;
	final static int ROW_BOT = 1;
	final static int F_1 = 0;
	final static int F_2 = 1;
	final static int F_3 = 2;
	final static int F_4 = 3;
	final static int F_5 = 4;
	final static int F_6 = 5;
	final static int F_7 = 6;
	final static int F_8 = 7;

	String buttonLabel[];
	String buttonCommand[];

	public SoftKeys()
	{
		buttonLabel = new String[8 * 2];
		buttonCommand = new String[8 * 2];
		clear();
	}

	public void clear ()
	{
		for (int i = 0; i < 16; i++)
		{
			buttonLabel[i] = "";
			buttonCommand[i] = "";
		}
	}

	/**
	 *
	 * @param row
	 *            [0...1]
	 * @param col
	 *            [0...7]
	 * @return the caption of the soft key
	 */
	public String getButtonText ( int row, int col )
	{
		return buttonLabel[col + row * 8];
	}

	/**
	 * Copy up to eight characters into the soft key caption
	 *
	 * @param row
	 *            [0...1]
	 * @param col
	 *            [0...7]
	 * @param text
	 *            - the string to be shown
	 */
	public void setButtonText ( int row, int col, String text )
	{
		if (text.length() > 8) {
			buttonLabel[col + row * 8] = text.substring(0, 8);
		} else {
			buttonLabel[col + row * 8] = text;
		}
	}

	public void setButtonCommand ( int row, int col, String command )
	{
		buttonCommand[col + row * 8] = command;
	}

	/**
	 *
	 * @param pt
	 *            the point which is to be tested, usually the current mouse
	 *            position.
	 * @param theScreen
	 *            the terminal screen with soft key labels.
	 * @return -1 if no label was hit, otherwise [0...7] for [f1...f8].
	 */
	public int hitTest ( Point pt, TerminalScreen theScreen )
	{
		int retVal = -1;
		int dx = theScreen.dx;
		int dy = theScreen.dy;
		int hMargin = dx / 4; // 1/4
		int hSpacing = dx / 2;
		int width = 8 * dx + 2 * hMargin; // 802
		int height = 2 * dy + 4;
		int yButtonBar = (TerminalScreen.HEIGHT + 1) * dy;

		if (pt.y >= yButtonBar && pt.y <= yButtonBar + height)
		{
			// vertical match: inside the button band

			// check for horizontal match

			// test the left four buttons
			int xButton = 0;
			for (int i = 0; i < 4; i++)
			{
				if (pt.x >= xButton && pt.x <= xButton + width)
				{
					retVal = i;
					break;
				}
				xButton += (width + hSpacing);
			}

			if (retVal == -1)
			{
				// no hit yet: test the right four buttons
				xButton = TerminalScreen.WIDTH * dx + hSpacing - 4
						* (width + hSpacing);
				for (int i = 0; i < 4; i++)
				{
					if (pt.x >= xButton && pt.x <= xButton + width)
					{
						retVal = i + 4;
						break;
					}
					xButton += (width + hSpacing);
				}
			}
		}
		return retVal;
	}

	public void paint ( Graphics g, TerminalScreen theScreen )
	{
		g.setPaintMode();
		int dx = theScreen.dx;
		int dy = theScreen.dy;
		Color cFore = theScreen.foreColor;
		Color cBack = theScreen.backColor;

		int hMargin = dx / 4; // 1/4
		int hSpacing = dx / 2;
		int width = 8 * dx + 2 * hMargin; // 802
		int height = 2 * dy + 4;
		int yButtonBar = (TerminalScreen.HEIGHT + 1) * dy;

		for (int i = 0; i < 4; i++)
		{
			int xButton = i * (width + hSpacing);
			g.setColor(cFore);
			g.fillRect(xButton, yButtonBar, width, height);
			g.setColor(cBack);
			// top row
			theScreen.drawString(g, buttonLabel[i], xButton + hMargin,
					yButtonBar + dy);
			// bottom row
			theScreen.drawString(g, buttonLabel[i + 8], xButton + hMargin,
					yButtonBar + 2 * dy);
		}

		for (int i = 0; i < 4; i++)
		{
			int xButton = TerminalScreen.WIDTH * dx + hSpacing + (i - 4)
					* (width + hSpacing);
			g.setColor(cFore);
			g.fillRect(xButton, yButtonBar, width, height);
			g.setColor(cBack);
			// top row
			theScreen.drawString(g, buttonLabel[i + 4], xButton + hMargin,
					yButtonBar + dy);
			// bottom row
			theScreen.drawString(g, buttonLabel[i + 4 + 8], xButton + hMargin,
					yButtonBar + 2 * dy);
		}
	}
}
