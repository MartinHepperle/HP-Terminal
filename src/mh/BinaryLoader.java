package mh;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class BinaryLoader
{

	final static int ESC = 27;
	boolean inEscape = false;
	int inLoader = 0;

	byte ram[] = new byte[64 * 1024];

	public BinaryLoader(InputStream is)
	{
		try
		{
			int num = 0;
			int address = 0;
			int low = Integer.MAX_VALUE;
			int high = Integer.MIN_VALUE;
			int checksum = 0;

			while (is.available() > 0)
			{
				int c = is.read();

				if (inLoader > 0)
				{
					if (inLoader == 1 && (c == 'b' || c == 'c'))
					{
						// ESC & b or ESC & c
						inLoader++;
					}
					else
					{
						// ESC & b ...
						// ESC & c ...
						if (c >= '0' && c <= '7')
						{
							num = num * 8 + c - '0';
						}
						else if (c >= 'a' && c <= 'z')
						{
							switch (c)
							{
								case 'a':
									// address
									address = num;
									checksum = checksum + num;
									num = 0;
									System.out.println("address =" + address);
									if (address < low)
										low = address;
									if (address > high)
										high = address;
									break;
								case 'd':
									// data
									if (address < low)
										low = address;
									if (address > high)
										high = address;
									ram[address++] = (byte) num;
									checksum = checksum + num;
									num = 0;
									break;
								case 'c':
									// checksum
									System.out.println("checksum=" + checksum
											+ " <> num=" + num);

									if (checksum != num)
										System.err.printf("%d != %d\n",
												new Object[] {
														new Integer(checksum),
														new Integer(num) });
									num = 0;
									break;
							}
							// prepare for next
							num = 0;
						}
						else if (c == 'E')
						{
							// terminator, execute
							System.out.println("used address range from " + low
									+ " to " + high);
							System.out.println("execute from address "
									+ address);

							// dump binary code for later disassembly
							DataOutputStream os = new DataOutputStream(
									new FileOutputStream("2648.asm.bin"));

							for (int i = low; i <= high; i++)
								os.write(ram[i]);
							os.close();
						}
						else if (c >= 'A' && c <= 'Z')
						{
							// any other terminator
							inLoader = 0;
						}
					}
				}
				else if (inEscape)
				{
					// ESC &
					if (c == '&')
						inLoader++;

					inEscape = false;
				}
				else if (c == ESC)
				{
					// ESC
					inEscape = true;
				}
			}
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 */
	public static void main ( String[] args )
	{
		try
		{
			BinaryLoader bl = new BinaryLoader(new FileInputStream(
					"G:/HP/2648/pong.log"));
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}

	}

}
