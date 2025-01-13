package mh;

/**
 * 
 * @author ea55
 * 
 *         This class collects elements of an escape sequence and offers methods
 *         for parsing the sequence.
 */
public class EscapeSequence
{
	private StringBuilder m_sb;
	private int m_idx;

	/**
	 * Create a new object to hold an escape sequence.
	 */
	public EscapeSequence()
	{
		m_sb = new StringBuilder();
		m_idx = 0;
	}

	/**
	 * Append a character to the current sequence.
	 * 
	 * @param c
	 *            the character to append.
	 */
	public void append ( char c )
	{
		m_sb.append(c);
	}

	/**
	 * Append a string to the current sequence.
	 * 
	 * @param s
	 *            the string to append.
	 */
	public void append ( String s )
	{
		m_sb.append(s);
	}

	/**
	 * Prepare for a new escape sequence.
	 * 
	 * Resets the parse pointer and truncates the escape character string.
	 */
	public void reset ()
	{
		m_sb.setLength(0);
		m_idx = 0;
	}

	/**
	 * Set the current parsing pointer to the given index. Prepares for parsing
	 * at the given index.
	 * 
	 * @param i
	 *            - the index into the escape sequence
	 *            [0...length(EscapeSequence) - 1].
	 */
	public void setIndex ( int i )
	{
		if (i >= 0 && i < m_sb.length())
			m_idx = i;
	}

	/**
	 * Increment the parse pointer by the given number of characters. Nothing
	 * happens if the result would point behind the end of the EscapeSequence.
	 * 
	 * @param i
	 *            the number of characters to skip.
	 */
	public void incrementIndex ( int i )
	{
		if (m_idx + i <= m_sb.length())
			m_idx = m_idx + i;
	}

	/**
	 * Decrement the parse pointer by the given number of characters. Nothing
	 * happens if the result would point before the start of the EscapeSequence.
	 * 
	 * @param i
	 *            the number of characters to go back.
	 */
	public void decrementIndex ( int i )
	{
		if (m_idx >= i)
			m_idx = m_idx - i;
	}

	/**
	 * Return a string with this escape sequence.
	 */
	public String toString ()
	{
		return m_sb.toString();
	}

	/**
	 * Test whether parsing has reached the end of the sequence.
	 * 
	 * @return true if there are more characters in this sequence.
	 */
	public boolean hasMore ()
	{
		return m_idx < m_sb.length();
	}

	/**
	 * Tests whether the escape sequence starts with the given string.
	 * 
	 * @param s
	 *            the string to look for.
	 * @return true if the escape sequence starts with the given string.
	 */
	public boolean startsWith ( String s )
	{
		boolean ret = false;

		if (s.length() <= m_sb.length())
		{
			ret = true;
			for (int i = 0; i < s.length(); i++)
			{
				if (m_sb.charAt(i) != s.charAt(i))
				{
					ret = false;
					break;
				}
			}
		}
		return ret;
	}

	/**
	 * Tests whether the escape sequence ends with the given string.
	 * 
	 * @param s
	 *            the string to look for.
	 * @return true if the escape sequence ends with the given string.
	 */
	public boolean endsWith ( String s )
	{
		boolean ret = false;

		if (s.length() <= m_sb.length())
		{
			ret = true;

			for (int i = m_sb.length() - s.length(); i < m_sb.length(); i++)
			{
				if (m_sb.charAt(i) != s.charAt(i))
				{
					ret = false;
					break;
				}
			}
		}

		return ret;
	}

	/**
	 * 
	 * @return The last character in this Escape sequence.
	 */
	int getLast ()
	{
		int ret = -1;

		if (m_sb.length() > 0)
			ret = m_sb.charAt(m_sb.length() - 1);

		return ret;
	}

	/**
	 * Remove the last character from this sequence.
	 */
	void removeLast ()
	{
		if (m_sb.length() > 0)
		{
			m_sb.setLength(m_sb.length() - 1);
		}
	}

	/**
	 * Parses a decimal number with an optional leading '+' or '-' sign.
	 * Advances the parse pointer only if a valid number has been parsed. If the
	 * number ends with a space, a comma, or a semicolon, this separator is
	 * skipped too so that the pointer points to the next number (if any).
	 * 
	 * @return decimal number, if no valid number was found, Integer.MIN_VALUE
	 *         is returned.
	 */
	public int parseASCIIInteger ()
	{
		boolean first = true;
		int sign = 1;
		int number = Integer.MIN_VALUE;

		while (m_idx < m_sb.length())
		{
			char c = m_sb.charAt(m_idx);

			if ('0' <= c && c <= '9')
			{
				// digit
				number = number * 10 + (c - '0');
				first = false;
			}
			else if (c == ' ')
			{
				// break when space is reached
				if (!first)
				{
					m_idx++;
					break; // stop
				}
				// else: skip leading spaces
			}
			else if (c == ',')
			{
				m_idx++;
				break; // stop
			}
			else if (c == ';')
			{
				m_idx++;
				break; // stop
			}
			else if (c == '+')
			{
				if (first)
					sign = 1;
				else
					break; // not a leading sign
				first = false;
			}
			else if (c == '-')
			{
				if (first)
					sign = -1;
				else
					break; // not a leading sign
				first = false;
			}
			else
			{
				// not a number, ',' , ';' or ' ': stop
				break;
			}
			m_idx++;
		}

		return (sign > 0) ? number : -number;
	}

	/**
	 * Parses a decimal number with an optional leading '+' or '-' sign. If no
	 * valid number is found the given default value is returned.
	 * 
	 * 
	 * @param def
	 *            default value to return.
	 * @return decimal number, if no valid number was found, the value of 'def'
	 *         is returned.
	 * 
	 * @see #parseASCIIInteger()
	 */
	public int parseASCIIInteger ( int def )
	{
		int number = parseASCIIInteger();
		if (number == Integer.MIN_VALUE)
			number = def;

		return number;
	}

	/**
	 * Advances the parse pointer only if THREE valid bytes have been parsed.
	 * 
	 * @return an unsigned decimal number built from two 5-bit parts,
	 *         Integer.MIN_VALUE if no valid number was found.
	 */
	public int parseBinaryTriple ()
	{
		int number = Integer.MIN_VALUE;

		if (m_idx + 2 < m_sb.length())
		{
			char hi = m_sb.charAt(m_idx);
			char mi = m_sb.charAt(m_idx + 1);
			char lo = m_sb.charAt(m_idx + 2);
			if ((' ' <= hi && hi <= '?') && (' ' <= mi && mi <= '?')
					&& (' ' <= lo && lo <= '?'))
			{
				number = ((((hi & 0x1F) << 5) | (mi & 0x1F)) << 5)
						| (lo & 0x1F);
				m_idx += 3;
			}
		}

		return number;
	}

	/**
	 * Advances the parse pointer only if TWO valid bytes have been parsed.
	 * 
	 * @return an unsigned decimal number built from two 5-bit parts,
	 *         Integer.MIN_VALUE if no valid number was found.
	 */
	public int parseBinaryWord ()
	{
		int number = Integer.MIN_VALUE;

		if (m_idx + 1 < m_sb.length())
		{
			char hi = m_sb.charAt(m_idx);
			char lo = m_sb.charAt(m_idx + 1);
			if ((' ' <= hi && hi <= '?') && (' ' <= lo && lo <= '?'))
			{
				number = ((hi & 0x1F) << 5) | (lo & 0x1F);
				m_idx += 2;
			}
		}

		return number;
	}

	/**
	 * Advances the parse pointer only if ONE valid byte has been parsed.
	 * 
	 * @return an unsigned decimal number from a 5-bit byte, Integer.MIN_VALUE
	 *         if no valid number was found.
	 */
	public int parseBinaryByte ()
	{
		int number = Integer.MIN_VALUE;

		if (m_idx < m_sb.length())
		{
			char num = m_sb.charAt(m_idx);
			if (' ' <= num && num <= '?')
			{
				number = num & 0x1F;
				m_idx++;
			}
		}

		return number;
	}

	/**
	 * Return the next character in the sequence. Advances the parse pointer
	 * only if a character has been parsed.
	 * 
	 * @return the next character or 0 if the end of the sequence has been
	 *         reached.
	 */
	public char parseCharacter ()
	{
		if (m_idx < m_sb.length())
			return m_sb.charAt(m_idx++);
		else
			return 0;
	}

	/**
	 * Return the next character in the sequence. Does not advance the parse
	 * pointer.
	 * 
	 * @return - the next available character or 0 if the end of the sequence
	 *         has been reached.
	 */
	public char peekCharacter ()
	{
		if (m_idx < m_sb.length())
			return m_sb.charAt(m_idx);
		else
			return 0;
	}
}
