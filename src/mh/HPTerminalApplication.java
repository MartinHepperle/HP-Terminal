package mh;

import jssc.SerialNativeInterface;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import org.apache.commons.net.telnet.EchoOptionHandler;
import org.apache.commons.net.telnet.InvalidTelnetOptionException;
import org.apache.commons.net.telnet.SuppressGAOptionHandler;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.TelnetInputListener;
import org.apache.commons.net.telnet.TerminalTypeOptionHandler;
import org.apache.commons.net.telnet.TelnetNotificationHandler;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.prefs.Preferences;
import java.nio.charset.StandardCharsets;

import javax.swing.JFrame;

/**
 * Terminal emulator main application file
 *
 * @author Martin Hepperle, July 2019
 *
 */
public class HPTerminalApplication implements TelnetNotificationHandler, SerialPortEventListener
{
	static int               DEBUG                = 0;

	final static String      logFileName          = "HPTerminal.log";
	final static String      fileNameHPGL         = "HPTerminal.hpgl";
	BufferedOutputStream     bwLog                = null;
	PrintStream              psHPGL               = null;

	final static String      VERSION_NUMBER       = "0.2";
	final static String      VERSION_DATE         = "February 2025";

	// normally always true
	boolean                  localKeys            = true;

	// [REMOTE*] whether to send characters to the host or use them locally
	boolean                  remoteMode           = false;

	boolean                  logging              = false;

	static String            m_ApplicationKey     = "MHTerminal";
	static final int         BUFLEN               = 4096;
	// control character codes
	static final byte        NUL                  = 0x00;
	static final byte        SOH                  = 0x01;
	static final byte        STX                  = 0x02;
	static final byte        ETX                  = 0x03;
	static final byte        EOT                  = 0x04;
	static final byte        ENQ                  = 0x05;
	static final byte        ACK                  = 0x06;
	static final byte        BEL                  = 0x07;
	static final byte        BS                   = 0x08;
	static final byte        HT                   = 0x09;
	static final byte        LF                   = 0x0A;
	static final byte        VT                   = 0x0B;
	static final byte        FF                   = 0x0C;
	static final byte        CR                   = 0x0D;
	static final byte        SO                   = 0x0E;
	static final byte        SI                   = 0x0F;
	static final byte        DLE                  = 0x10;
	static final int         DC1                  = 0x11;
	static final int         DC2                  = 0x12;
	static final int         DC3                  = 0x13;
	static final int         DC4                  = 0x14;
	static final int         NAK                  = 0x15;
	static final int         SYN                  = 0x16;
	static final int         ETB                  = 0x17;
	static final int         CAN                  = 0x18;
	static final int         EM                   = 0x19;
	static final int         SUB                  = 0x1A;
	static final byte        ESC                  = 0x1B;
	static final int         FS                   = 0x1C;
	static final int         GS                   = 0x1D;
	static final int         RS                   = 0x1E;
	static final int         US                   = 0x1F;
	// first printable character ' '
	static final int         SP                   = 0x20;

	int                      idxImage             = 0;

	boolean                  pendingACK           = false;
	boolean                  m_AlphaActive        = true;

	// modes for Escape sequence parser
	// not in ESC sequence
	final char               MODE_IDLE            = 0;
	// in "ESC" sequence
	final char               MODE_ESC             = 1;
	// in "ESC [" ANSI-sequence
	final char               MODE_ESC_BRACK       = 2;

	// in "ESC *" HP-sequence
	final char               MODE_ESC_ASTERISK    = 3;
	// in "ESC &" HP-sequence
	final char               MODE_ESC_AMPERSAND   = 4;
	// in "Graph text sequence (ends with ESC)
	final char               MODE_ESC_GRAPH_TEXT  = 5;
	// in "Graph label sequence (ends with CR, LF, CR+LF)
	final char               MODE_ESC_GRAPH_LABEL = 6;
	// in "ESC )" HP-sequence
	final char               MODE_ESC_CLOSE_PAREN = 7;
	// waiting for graphics cursor click
	final char               WAIT_FOR_GRAPH_CLICK = 8;

	static final String      ASCII[]              =
		{ "NUL", "SOH", "STX", "ETX", "EOT", "ENQ", "ACK", "BEL", "BS", "HT", "LF",
				"VT", "FF", "CR", "SO", "SI", "DLE", "DC1", "DC2", "DC3", "DC4", "NAK",
				"SYN", "ETB", "CAN", "EM", "SUB", "ESC", "FS", "GS", "RS", "US", " ",
				"!", "\"", "#", "$", "%", "&", "'", "(", ")", "*", "+", ",", "-", ".",
				"/", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ":", ";", "<",
				"=", ">", "?", "@", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
				"K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X",
				"Y", "Z", "[", "\\", "]", "^", "_", "`", "a", "b", "c", "d", "e", "f",
				"g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t",
				"u", "v", "w", "x", "y", "z", "{", "|", "}", "~", "DEL" };

	final boolean            ANSI_TERM            = false;
	// cursor key sequences sent out to mainframe
	final byte               CUP[];
	final byte               CDN[];
	final byte               CRIGHT[];
	final byte               CLEFT[];

	private JFrame           terminalFrame;
	private JFrame           graphicsFrame;
	Beeper                   theBeeper;

	SerialPort               m_Port;
	TelnetClient 			 tc;
	// used to distinguish whether we are in serial or telnet mode
	boolean                  serialMode;
	Thread                   t                    = null;

	// ring buffer for incoming serial data
	int                      ptrRead              = 0;
	int                      ptrWrite             = 0;
	byte                     buffer[]             = new byte[BUFLEN];
	// used for debugging: high water mark
	int                      maxBufferLength      = 0;

	private TerminalScreen   terminalScreen;
	private GraphicsScreen   graphicsScreen;
	private TerminalSettings terminalSettings     = new TerminalSettings();

	// have the following hardware configuration:
	// InhHndShk(G) = OFF,
	// InhDC2(H) = OFF
	final static int         TRANSFER_CHAR        = 0;
	final static int         TRANSFER_BLCK        = 1;
	final static int         TRANSFER_MODY        = 2;
	int                      transferMode         = TRANSFER_CHAR;
	final static int         HS_NONE              = 0;
	final static int         HS_DC1               = 1;
	final static int         HS_DC1_DC2_DC1       = 2;

	// if DC1 is received and toSend != null then send this string
	String                   toSend               = null;

	OutputStream telnetOutputStream				  = null;
	InputStream telnetInputStream  				  = null;      


	/**
	 * Launch the application.
	 *
	 * @param args
	 *           - the command line arguments.
	 */
	public static void main ( final String[] args )
	{

		// VectorFontSimplex p=new VectorFontSimplex();
		// p.dumpASM();

		EventQueue.invokeLater(new Runnable()
		{
			@Override
			public void run ()
			{
				try
				{
					// default values can be overridden by command line
					int terminalID = TerminalSettings.HP2627A;
					String port = null;
					int fontSize = -1;
					int speed = -1;
					int sound = -1;
					int logger = -1;
					String telnetHost = null;
					int telnetPort = -1;

					for ( int i = 0; i < args.length; i++ )
					{
						if ( args[i].toLowerCase().equals("-port") )
						{
							port = args[++i];
						}
						else if (args[i].toLowerCase().equals("-telnethost")) 
						{
							telnetHost = args[++i];
						}
						else if (args[i].toLowerCase().equals("-telnetport") )
						{
							telnetPort = Integer.parseInt(args[++i]);
						}
						else if ( args[i].toLowerCase().equals("-fontsize") )
						{
							fontSize = Integer.parseInt(args[++i]);
						}
						else if ( args[i].toLowerCase().equals("-speed") )
						{
							speed = Integer.parseInt(args[++i]);
						}
						else if ( args[i].toLowerCase().equals("-sound") )
						{
							sound = Integer.parseInt(args[++i]);
						}
						else if ( args[i].toLowerCase().equals("-type") )
						{
							String s = args[++i];
							if ( s.contains("ANSI") )
							{
								terminalID = TerminalSettings.ANSI;
							}
							else if ( s.contains("2627") )
							{
								terminalID = TerminalSettings.HP2627A;
							}
							else if ( s.contains("2648") )
							{
								terminalID = TerminalSettings.HP2648A;
							}
						}
						else if ( args[i].toLowerCase().equals("-logging") )
						{
							logger = Integer.parseInt(args[++i]);
						}
						else if ( args[i].toLowerCase().equals("-debug") )
						{
							DEBUG = Integer.parseInt(args[++i]);
						}
						else
						{
							System.err.println("Unknown parameter '" + args[i] + "'");
							System.err.println("Usage:");
							System.err.println("HPTerminalApplication [-port PORTNAME]"
									+ " [-telnetHost HOST]" + " [-telnetPort PORT]"	 
									+ " [-fontsize FONTSIZE]" + " [-speed BAUDRATE]"
									+ " [-sound {0|1}]"
									+ " [-type {ANSI|HP2627A|HP2648A}]"
									+ " [-logging {0|1}]" + " [-debug {0...}]" + " ");
						}
					}

					// exit, if both port and telnetHost were specified
					if(port != null && telnetHost != null ) {
						System.err.println("The port and telnetHost parameter are mutually exclusive. Program terminated");
						System.exit(1);
					}

					new HPTerminalApplication(
							port, fontSize, speed, sound, logger, terminalID,telnetHost, telnetPort);

				}
				catch ( Exception e )
				{
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 *
	 * @param port
	 *           - the port to open, e.g. "COM1" or "\\.\COM27" under Windows. If this parameter is not null, then
	 *           - HPTerminal uses a serial connection
	 * @param fontSize
	 *           - the size of the font, also defines the window dimensions.
	 * @param speed
	 *           - the line speed, e.g. 9600 baud.
	 * @param sound
	 *           - 0=false, 1=true, negative: use default from properties.
	 * @param logger
	 *           - whether a log file shall be written.
	 * @param terminalID
	 *           - Terminal identification
	 * @param telnetHost
	 * 			- Name of the telnet host. If this parameter is not null, then HPTerminal uses a telnet connection
	 * @param telnetPort
	 *			- Telnet port number
	 */
	public HPTerminalApplication(String port, int fontSize, int speed, int sound,
			int logger, int terminalID, String telnetHost, int telnetPort)
	{
		Preferences p = getPreferences();

		// get settings from last session
		terminalSettings.readPreferences(p);

		// override settings if defined
		if ( fontSize > 0 ) {
			terminalSettings.FontSize = fontSize;
		}

		if ( port != null ) {
			terminalSettings.PortName = port;
			terminalSettings.telnetHost = "";
		}

		if ( speed > 0 ) {
			terminalSettings.speed = speed;
		}
		//
		if ( sound >= 0 ) {
			terminalSettings.Sound = (sound != 0);
		}

		if ( terminalID >= 0 ) {
			terminalSettings.setTerminalID(terminalID);
		}

		if (telnetHost != null ) {
			terminalSettings.telnetHost = telnetHost;
			terminalSettings.PortName = "";
		}

		if(telnetPort > 0) {
			terminalSettings.telnetPort = telnetPort;
		}
		if ( logger > -1 ) {
			logging = true;
		}

		initialize();



		Point pt = new Point(p.getInt("Alpha.x", 100), p.getInt("Alpha.y", 100));
		terminalFrame.setLocation(pt);

		pt = new Point(p.getInt("Graph.x", 50), p.getInt("Graph.y", 50));
		graphicsFrame.setLocation(pt);
		graphicsScreen.setSize(terminalSettings.width, terminalSettings.height);
		terminalScreen.setFontSize(terminalSettings.FontSize);

		// create and preload beep sound
		theBeeper = new Beeper("beep.wav");

		if ( terminalSettings.TerminalID == TerminalSettings.ANSI )
		{
			// ANSI arrow key sequences sent out to mainframe
			CUP = new byte[]
					{ ESC, '[', 'A' };
			CDN = new byte[]
					{ ESC, '[', 'B' };
			CRIGHT = new byte[]
					{ ESC, '[', 'C' };
			CLEFT = new byte[]
					{ ESC, '[', 'D' };
		}
		else
		{
			// HP arrow key sequences sent out to mainframe
			CUP = new byte[]
					{ CTRL('A') };
			CDN = new byte[]
					{ CTRL('B') };
			CRIGHT = new byte[]
					{ CTRL('C') };
			CLEFT = new byte[]
					{ CTRL('D') };
		}

		System.out.println("Running " + getClass().getName() + " Version "
				+ VERSION_NUMBER + " (" + VERSION_DATE + ").");
		System.out.println("Current Settings:");
		System.out.println("Debug level     \t= " + DEBUG);
		System.out.println("Logging         \t= " + logging);
		terminalSettings.dump(System.out);

		if (terminalSettings.PortName != "") {
			serialMode= true;
		} else {
			serialMode= false;
		}
		if(serialMode) {
			m_Port = new SerialPort(terminalSettings.PortName);

			try
			{
				m_Port.openPort();
				m_Port.setParams(terminalSettings.speed, SerialPort.DATABITS_8,
						SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

				// we are ready
				// m_Port.setDTR(true);
				// we are ready to send
				// m_Port.setRTS(true);
				// no automatic flow control
				m_Port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

				m_Port.setEventsMask(SerialPort.MASK_RXCHAR);

				m_Port.addEventListener(this);

				remoteMode = true;

				if ( DEBUG > 0 )
				{
					showLineStatus(System.out);
				}
			}
			catch ( SerialPortException e )
			{
				/*
	         String msg = String.format(
	               "*** Error: Cannot open serial port '%s'.\r\n", new Object[]
	               { terminalSettings.PortName });
				 */
				String msg = String.format(
						"Cannot open serial port '%s'.\r\n",terminalSettings.PortName );
				remoteMode = false;
				System.err.println(msg);
				showMessage(msg);
			}
		} else {

			// create telnet client
			tc = new TelnetClient(terminalSettings.AnswerBack);
			tc.registerNotifHandler(this);
			
			// start telnet reader thread, this allows immediate option negotiation
			tc.setReaderThread(true);

			// Negotiation handlers. Allow defining desired initial setting for local/remote 
			// activation of an option and behavior in case a local/remote activation request 
			// for this option is received.
			// Meaning of the 4 boolean parameters
			// initlocal    == true: a WILL is sent upon connect
			// initremote   == true: a DO is sent upon connect
			// acceptlocal  == true: any DO request is accepted
			// acceptremote == true: any WILL request is accepted
			try { 
				
				//RFC 1091. Terminal type option handler
				tc.addOptionHandler(new TerminalTypeOptionHandler(terminalSettings.AnswerBack, false, 
						false, true, false));
				
				// RFC 857, Telnet Echo Option handler 
				tc.addOptionHandler(new EchoOptionHandler(false, false, false, false));
				
				// RFC 858, supress go ahead option
				tc.addOptionHandler(new SuppressGAOptionHandler(true, true, true, true));
				
			} catch (IOException | InvalidTelnetOptionException e) {
				String msg = "Error adding Option handlers";
				remoteMode = false;
				System.err.println(msg+": "+e.getMessage());
				showMessage(msg);
			}

			// register telnet input listener callback, callback stores received bytes into ring buffer
			tc.registerInputListener(new TelnetInputListener() {

				@Override
				public void telnetInputAvailable() {
					
					try {
						final byte[] buff= new byte[1024];
						int readCount = 0;
						readCount= telnetInputStream.read(buff);
						if(readCount>0) {
							for(int i=0;i<readCount;i++) putByte(buff[i]);
						}
						
						// unfortunately this will not happen!
						if(readCount<0) {
							String msg = "End of telnet stream";
							remoteMode= false;
							terminalScreen.setRemoteMode(remoteMode);
							System.err.println(msg);
							showMessage(msg);
						}
					}	  
					catch (IOException e) {
						String msg = "Error reading from telnet server";
						remoteMode = false;
						terminalScreen.setRemoteMode(remoteMode);
						System.err.println(msg+": "+e.getMessage());
						showMessage(msg);
					}   
				}
			});

			// connect to telnet host
			try {
				tc.connect(terminalSettings.telnetHost, terminalSettings.telnetPort);
				remoteMode= true;
				
			} catch (IOException  e) {
				String msg = String.format(
						"Cannot connect to '%s:%d'", terminalSettings.telnetHost,
						terminalSettings.telnetPort);
				remoteMode = false;
				System.err.println(msg+": "+e.getMessage());
				showMessage(msg);
			} 

			// get telnet input/output streams
			telnetInputStream=tc.getInputStream();
			telnetOutputStream=tc.getOutputStream();
		}
		// terminal thread
		t = new Thread(new Runnable()
		{
			@Override
			public void run ()
			{
				// System.out.println("Terminal thread is running");
				terminalScreen.setRemoteMode(remoteMode);

				if ( logging )
				{
					try
					{
						bwLog = new BufferedOutputStream(
								new FileOutputStream(logFileName));
						bwLog.write(
								("------ HPTerminal I/O log ------\n").getBytes());
					}
					catch ( FileNotFoundException e2 )
					{
						System.err.println(
								"*** Cannot create log file '" + logFileName + "'.");
					}
					catch ( IOException e )
					{
						System.err.println(
								"*** Cannot write to log file '" + logFileName + "'.");
					}
				}

				psHPGL = null;

				if ( DEBUG > 50 )
				{
					// output all vector plot commands to a HPGL file
					try
					{
						psHPGL = new PrintStream(fileNameHPGL);
						psHPGL.print("IN;SP1");
					}
					catch ( FileNotFoundException e1 )
					{
						e1.printStackTrace();
					}
				}

				if ( DEBUG == 97 )
				{
					// specific test

					// show graphics cursor and wait for key
					String s = "&q0L";

					// copy bytes into receive buffer
					for ( int i = 0; i < s.length(); i++ )
					{
						buffer[i] = (byte) s.charAt(i);
					}
					ptrRead = 0;
					ptrWrite = s.length();
				}

				if ( DEBUG == 95 )
				{
					// specific graphics test
					String s = "*pas33,0 133,0 166,95 83,150, 0,95 a40,10 12,91 83,138 153,91 125,10T";
					EscapeSequence es = new EscapeSequence();
					es.append(s);
					handlePlotting(es);
					try
					{
						// wait for key press on console
						System.in.read();
					}
					catch ( IOException e )
					{
						e.printStackTrace();
					}
					System.exit(0);
				}

				if ( DEBUG == 99 )
				{
					// specific graphics test
					String s = "*pia%2%2b%*%3%&%4%\"%4$?%3$=%2$=%1$>%/$?%/%!%.%(%-%*%,%+%,%,%+%,%)%*%(%&%'%\"%'$?%($=%)a%.%4%6%.%6%'a%?%4%6%.a&0%2&-%3&*%4&&%4&#%3&!%2&!%1&\"%/&#%/&%%.&+%-&-%,&.%,&0%+&0%)&-%(&*%'&&%'&#%(&!%)a&9%4&9%'a&2%4' %4a'$%4'$%'a'$%4'1%4a'$%.',%.a'$%''1%'a'6%4'6%'a'6%4'>%'a(&%4'>%'a(&%4(&%%C";
					EscapeSequence es = new EscapeSequence();
					es.append(s);
					handlePlotting(es);
					try
					{
						// wait for key press on console
						System.in.read();
					}
					catch ( IOException e )
					{
						e.printStackTrace();
					}
					System.exit(0);
				}

				// start in idle mode
				char escMode = MODE_IDLE;

				EscapeSequence esc = new EscapeSequence();
				StringBuilder sbGrafText = new StringBuilder();

				pendingACK = false;

				while ( true )
				{
					// this polling loop must work even when nothing comes in
					// from
					// the serial port

					if ( escMode == WAIT_FOR_GRAPH_CLICK )
					{
						if ( graphicsScreen.wasClicked() )
						{
							// returns:
							// "+00360,+00080,000" + CR
							Point pt = graphicsScreen.getCursorPosition();
							int c = graphicsScreen.getKeyCode();
							/*
                     toSend = String.format("+%05d,+%05d,%03d", new Object[]
                     { new Integer(pt.x), new Integer(pt.y), new Integer(c) })
                           + (char) CR; */
							toSend = String.format("+%05d,+%05d,%03d", 
									pt.x, pt.y, c ) + (char) CR;
							// assumption: DC1 was already received and
							// swallowed...
							// force output by injecting a new DC1 and let the
							// loop do its thing
							pushbackByte((byte) DC1);

							escMode = MODE_IDLE;
						}
					}

					if ( inputAvailable() )
					{
						byte b = nextByte();
						int c = b & 0xff;

						if ( c == ENQ )
						{
							if ( terminalSettings.ENQ_ACK )
							{
								// if there is a lot of data in the buffer, we
								// should empty it first, before ACK
								if ( DEBUG > 0 )
								{
									System.out.println("ENQ received.");
								}

								// set a flag that ACK is pending
								pendingACK = true;
							}
							else
							{
								// ANSI: send the Terminal name
								sendString(terminalSettings.AnswerBack + (char) CR);
								if ( DEBUG > 0 )
								{
									System.out.println(
											"Answerback: " + terminalSettings.AnswerBack);
								}
							}

							// no further processing of ENQ
							continue;
						}

						if ( escMode == MODE_ESC_GRAPH_TEXT )
						{
							if ( c == ESC )
							{
								// end of Graphics text mode

								if ( sbGrafText.length() > 0 )
								{
									if ( psHPGL != null )
									{
										psHPGL.println(
												"LB" + sbGrafText.toString() + (char) 3);
									}
									graphicsScreen.drawText(sbGrafText.toString());
									// prepare for next
									sbGrafText.setLength(0);
								}

								// parse this Esc as usual
								escMode = MODE_IDLE;
							}
							else
							{
								// collect characters
								sbGrafText.append((char) c);
								continue;
							}
						}
						else if ( escMode == MODE_ESC_GRAPH_LABEL )
						{
							if ( c == CR || c == LF )
							{
								// end of Graphics label mode

								if ( c == CR && inputAvailable() )
								{
									// swallow trailing LF from CR-LF pair
									if ( peekByte() == LF ) {
										b = nextByte();
									}
								}

								if ( sbGrafText.length() > 0 )
								{
									if ( psHPGL != null )
									{
										psHPGL.println(
												"LB" + sbGrafText.toString() + (char) 3);
									}
									graphicsScreen.drawText(sbGrafText.toString());
									// prepare for next
									sbGrafText.setLength(0);
								}
								if ( DEBUG > 0 ) {
									System.out.println("\nGLABEL(END);");
								}

								// parse this Esc as usual
								escMode = MODE_IDLE;
								continue;
							}
							else
							{
								// collect characters
								sbGrafText.append((char) c);
								if ( DEBUG > 0 ) {
									System.out.print((char) c);
								}
								continue;
							}
						}

						if ( escMode == MODE_ESC )
						{
							// last character was ESC
							esc.append((char) c);

							// test for multi-character sequences first
							switch ( c )
							{
							case '[':
								escMode = MODE_ESC_BRACK;
								break;
							case '*':
								escMode = MODE_ESC_ASTERISK;
								break;
							case '&':
								escMode = MODE_ESC_AMPERSAND;
								break;
							case ')':
								escMode = MODE_ESC_CLOSE_PAREN;
								break;

							default:
								// single character escape sequences
								escMode = handleEscSingle(esc);
								break;
							}
						}
						else if ( escMode == MODE_ESC_BRACK )
						{
							// this sequence is ESC [ ...
							esc.append((char) c);

							// other allowed characters
							// e.g. ESC [ ? 7 h
							// e.g. ESC [ > 1 s HOME DOWN
							// e.g. ESC [ > 0 s HOME UP
							// escMode = (char) c;
							// Vendor special sequences
							// e.g. ESC [ & ... char
							// e.g. ESC [ * ... char
							// escMode = (char) c;

							if ( c >= 0x40 && c <= 0x7E )
							{
								// @A-Z[\]^_`a-z{
								// Anything not part of the escape
								// sequence terminates it. Typically this is a
								// trailing upper- or lowercase character.
								escMode = handleEscBracket(esc);
							}
						} // if (escMode == MODE_ESC_BRACK)
						else if ( escMode == MODE_ESC_ASTERISK )
						{
							// this sequence is ESC * ...

							if ( c == ESC )
							{
								// special case: Esc ends current sequence

								// should not happen, but e.g. AGP sends
								// Esc * m 6 x Esc * n 6 X

								// make character before Esc into a terminator
								c = Character.toUpperCase(esc.getLast());

								// remove last character.
								// will be replaced by uppercase variant below
								esc.removeLast();

								// prepare for next cycle
								pushbackByte(ESC);
							}
							else if ( c == 'l' )
							{ // graphics text label until CR, LF or CR+LF
								escMode = MODE_ESC_GRAPH_LABEL;

								if ( DEBUG > 0 ) {
									System.out.println("GLABEL(START);");
								}
								continue;
							}

							esc.append((char) c);

							// sequence end?

							if ( Character.isUpperCase(c) || c == '@' || c == '^'
									|| c == '\r' || c == '\n' )
							{
								// end of sequence
								if ( DEBUG > 0 )
								{
									dumpEscapeSequence(esc);
								}

								String sEscape = esc.toString().toLowerCase();
								// [0, 1, 2, ... ]
								// ESC * <control> x y z ...
								char control = sEscape.charAt(1);

								switch ( control )
								{
								case 'd':
									// display control
									escMode = handleEscAsteriskD(esc);
									break;
								case 'e':
									// image control
									escMode = MODE_IDLE;
									break;

								case 'm':
									// mode control
									escMode = handleEscAsteriskM(esc);
									break;

								case 'n':
									// graphics text
									escMode = handleEscAsteriskN(esc);
									break;

								case 'p':
									// plot control
									handlePlotting(esc);
									escMode = MODE_IDLE;
									break;

								case 's':
									// ID and equipment requests
									escMode = handleEscAsteriskS(esc);
									break;

								case 't':
									// compatibility mode
									escMode = MODE_IDLE;
									break;

								case 'w':
									// graphics initialization
									escMode = MODE_IDLE;
									break;

								default:
									escMode = MODE_IDLE;
									break;
								}
							}
							// end (escMode == MODE_ESC_ASTERISK)
						}
						else if ( escMode == MODE_ESC_AMPERSAND )
						{
							// this sequence is ESC & ...
							esc.append((char) c);

							if ( Character.isUpperCase(c) || c == '@' || c == '^'
									|| c == '\r' || c == '\n' )
							{
								// end of sequence
								if ( DEBUG > 0 )
								{
									dumpEscapeSequence(esc);
								}

								// ESC & <control> x y z ...^
								esc.setIndex(1);
								char control = esc.parseCharacter();

								switch ( control )
								{
								case 'a':
									// ESC & a ...
									escMode = handleEscAmpersAndA(esc);
									break;

								case 'd':
									// ESC & d ...
									escMode = handleEscAmpersAndD(esc);
									break;

								case 'j':
									switch ( c )
									{
									case '@':
										// Esc & j @ == hide all key labels
										terminalScreen.setKeyLabelsVisible(false);
										break;
									case 'A':
										// Esc & j A == show Modes keys
										terminalScreen.setKeyLabels(
												TerminalScreen.SOFTKEYS_MODE);
										break;
									case 'B':
										// Esc & j B == show User keys
										terminalScreen.setKeyLabels(
												TerminalScreen.SOFTKEYS_USER);
										break;
									case 'C':
										// Esc & j C == clear message and
										// return key label
										break;
									default:
										// replace function key labels...
										break;
									}

									escMode = MODE_IDLE;
									break;

								case 's':
									switch ( c )
									{
									case 'D':
										// Esc [&][s][0][D] 0=line mode
										// Esc [&][s][1][D] 1=page mode
										escMode = MODE_IDLE;
										break;
									}

									break;

								case 'q':
									// ESC & q 0 L == HP: unlock keyboard
									// ESC & q 1 L == HP: lock keyboard
								{
									esc.setIndex(2);
									if ( '0' == esc.parseCharacter() ) {
										terminalScreen.lockKeyboard(false);
									} else {
										terminalScreen.lockKeyboard(true);
									}
								}
								break;

								case '@':
									// ESC & @
									// special handler *** TO BE REMOVED ***
									// error in A990 setup script: should be
									// ESC & d @
									terminalScreen.setAttribute((byte) 0);
									escMode = MODE_IDLE;
									break;

								default:
									// end of sequence
									if ( DEBUG > 0 )
									{
										System.out.print("*** Unknown: ");
										dumpEscapeSequence(esc);
									}
									// done
									escMode = MODE_IDLE;
									break;
								}
							}
							// end (escMode == MODE_ESC_AMPERSAND)
						}
						else if ( escMode == MODE_ESC_CLOSE_PAREN )
						{
							// this sequence is ESC ) ...
							esc.append((char) c);

							// end of sequence
							if ( DEBUG > 0 )
							{
								dumpEscapeSequence(esc);
							}

							esc.setIndex(1);

							int font;

							switch ( esc.parseCharacter() )
							{
							case '@':
							case 'A':
								font = TerminalScreen.CS_ROMAN;
								break;
							case 'B':
							case 'C':
								font = TerminalScreen.CS_LINEDRAW;
								break;
							case 'D':
								font = TerminalScreen.CS_MATH;
								break;
							default:
								font = TerminalScreen.CS_ROMAN;
								break;
							}

							terminalScreen.setAlternateCharset(font);

							// done
							escMode = MODE_IDLE;
						}
						else
						{
							// not inside an "Esc" sequence
							// not inside an "Esc [" sequence
							// not inside an "Esc *" sequence
							// not inside an "Esc &" sequence
							// not inside an "Esc )" sequence

							switch ( c )
							{
							case ESC:
								// start a new escape sequence
								escMode = MODE_ESC;
								esc.reset();
								break;

							case DC1:
								// host prompt
								//
								// - In character mode: host is ready and asks
								// for input (DC1 = XON = 17d = 0x11)
								// - In block mode: reply with DC2 (=18d = 0x12)
								// to indicate block transfer and then wait for
								// another DC1 before sending block.
								// The end of the block is either:
								// - a CR in line mode and
								// - a RS (= 30d = 0x1E) in page mode

								if ( DEBUG > 0 )
								{
									System.out.print('[');
									System.out.print(ASCII[c]);
									System.out.print(']');
								}

								if ( toSend != null )
								{
									// we have been waiting for a DC1
									if ( DEBUG > 0 )
									{
										System.out.print("->Reply: '");
										System.out.println(
												toSend.replace("\r", "[CR]") + "'");
									}
									// send the reply
									sendString(toSend);
									// done
									toSend = null;
								}
								break;

							case BEL:
								if ( terminalSettings.Sound )
								{
									theBeeper.beep();
									if ( DEBUG > 0 )
									{
										System.out.println("[Bell]");
									}
								}
								break;

							default:
								// System.out.print((char) c);
								if ( DEBUG > 0 )
								{
									if ( c < 32 )
									{
										// control character
										System.out.print('[');
										System.out.print(ASCII[c]);
										System.out.print(']');
										if ( c == 10 ) {
											System.out.println();
										}
									}
									else if ( c < 128 )
									{
										// regular ASCII character
										System.out.print(ASCII[c]);
									}
									else
									{
										// bit 7 set: may be inverse or
										// underline on
										// some systems
										System.out.print((char) c);
									}
								}

								if ( m_AlphaActive )
								{
									final boolean CPM_Hack = false;
									if ( CPM_Hack )
									{
										// Special handling
										// for some CP/M systems.
										// Must be removed
										// for general 8-bit data.
										//
										if ( (b & 0x80) == 0x80 )
										{
											// bit 7 set: inverse
											terminalScreen.setAttribute((byte) 7);
											// send only 7 bit character
											b = (byte) (b & 0x7F);
											terminalScreen.putByte(b);
											terminalScreen.setAttribute((byte) 0);
										}
										else
										{
											// pass 7-bit characters through
											terminalScreen.putByte(b);
										}
									}
									else
									{
										terminalScreen.putByte(b);
									}
								}
								break;
							}
						}
					}
					else
					{
						// buffer empty: do some processing

						if ( pendingACK )
						{
							sendByte(ACK);
							pendingACK = false;
						}

						// give piece a chance
						try
						{
							Thread.sleep(10);
						}
						catch ( InterruptedException e )
						{
							e.printStackTrace();
						}
					}
				}
			}

			/**
			 * Handle Escape sequences consisting of a single character after the
			 * Esc character.
			 *
			 * @param esc
			 *           - the escape sequence to decode.
			 * @return - MODE_IDLE
			 */
			private char handleEscSingle ( EscapeSequence esc )
			{
				if ( DEBUG > 0 )
				{
					dumpEscapeSequence(esc);
				}

				// start after 'Esc'
				esc.setIndex(1);

				int c = esc.getLast();

				switch ( c )
				{
				case 'A':
					// ESC A: cursor up
					terminalScreen.moveCursor(-1, 0);
					break;
				case 'B':
					// ESC B == cursor down
					terminalScreen.moveCursor(1, 0);
					break;
				case 'C':
					// ESC C == cursor right
					terminalScreen.moveCursor(0, 1);
					break;
				case 'D':
					// ESC D == cursor left
					terminalScreen.moveCursor(0, -1);
					break;
				case 'E':
					// ESC E == hard reset
					theBeeper.beep();
					terminalScreen.resetDefaults(true);
					graphicsScreen.resetDefaults(true);
					break;
				case 'F':
					// ESC F == cursor home down
					terminalScreen.homeScreenDown();
					break;
				case 'G':
					// ESC G == cursor to left margin
					terminalScreen.setCursorRelScreen(terminalScreen.yCursor,
							terminalScreen.leftMargin);
					break;
				case 'H':
					// ESC H == cursor home up
					terminalScreen.homeScreenUp();
					break;
				case 'J':
					// ESC J == clear from cursor to end
					terminalScreen.clearToEOM();
					break;
				case 'K':
					// ESC K == clear from cursor to end of line
					terminalScreen.clearToEOL();
					break;
				case 'L':
					// ESC L == insert line
					terminalScreen.insertLine();
					break;
				case 'M':
					// ESC M == delete line
					terminalScreen.deleteCurrentLine();
					break;
				case 'P':
					// ESC P == delete character
					terminalScreen.deleteCharsInLine(1);
					break;
				case 'Q':
					// ESC Q == start insert character mode
					terminalScreen.setInsertMode(true);
					break;
				case 'R':
					// ESC R == end insert character mode
					terminalScreen.setInsertMode(false);
					break;
				case 'S':
					// ESC S == roll text (view moves down)
					terminalScreen.scrollScreenDown(1);
					break;
				case 'T':
					// ESC T == roll text down (view moves up)
					terminalScreen.scrollScreenUp(1);
					break;
				case 'X':
					// ESC X == HP: format mode OFF
					break;
				case 'Y':
					// ESC Y == HP: display functions ON
					terminalScreen.setDisplayFunctions(true);
					break;
				case 'Z':
					// ESC Z == HP: display functions OFF
					terminalScreen.setDisplayFunctions(false);
					break;
				case 'a':
					// ESC a: HP: cursor position request in memory
					// reply: ESC & a <col> c <row> R
					// zero-based
					toSend = (char) ESC + "&a" + Dig3(terminalScreen.xCursor)
					+ "c" + Dig3(terminalScreen.yCursor
							+ terminalScreen.getStartRow())
					+ "R" + (char) CR;
					break;
				case 'b':
					// ESC b == HP: unlock keyboard
					terminalScreen.lockKeyboard(false);
					break;
				case 'c':
					// ESC c == HP: lock keyboard }
					terminalScreen.lockKeyboard(true);
					break;
				case 'd':
					// ESC d == HP: host asks for data
					// DC1 handshake
					// terminal sends data block (one line from cursor position
					// in line mode)
					toSend = terminalScreen.getScreenLine(terminalScreen.yCursor,
							terminalScreen.xCursor, TerminalScreen.WIDTH)
					+ (char) CR;
					break;
				case 'e':
					// ESC e == raw binary transmission

					// file name
					// optionally pop up a dialog and ask for name or set file
					// name in settings
					String fileName = "LTape.raw";

					// delay between bytes in [ms]
					long millis = 10;

					sendBinaryFile(fileName, millis);
					break;
				case 'g':
					// ESC g == soft reset
					theBeeper.beep();
					terminalScreen.resetDefaults(false);
					graphicsScreen.resetDefaults(false);
					break;
				case 'h':
					// ESC h: HP: cursor home up
					terminalScreen.homeScreenUp();
					break;
				case 'i':
					// ESC i: HP: backtab
					terminalScreen.setCursorRelScreen(terminalScreen.yCursor,
							terminalScreen.prevTab());
					break;
				case 'l':
					// ESC l: HP: begin memory lock mode
					System.out
					.println("memory lock mode ON: not implemented yet.");
					break;
				case 'm':
					// ESC m: HP: end memory lock mode
					System.out
					.println("memory lock mode OFF: not implemented yet.");
					break;
				case '^':
					// ESC ^: HP: primary status request
					// HP 2627A terminal:
					// Esc [\] [8000020] [CR]
					// [0]=8 - 8KB memory
					// [1]=0 - straps (A-D) = OFF ABCD.EFGH
					// [2]=0 - straps (E-F) = OFF,
					// . . . . InhHndShk(G) = OFF,
					// . . . . InhDC2(H) = OFF
					// [3]=0 - no key locked
					// [4]=0 - no request pending
					// [5]=2 - self test = O.K.,
					// . . . . no datacomm error
					// [6]=0 - no device operations pending
					String primaryStatus = "8000020";
					// String primaryStatus = "8008020";
					toSend = (char) ESC + "\\" + primaryStatus + (char) CR;
					break;
				case '~':
					// ESC ~: HP: secondary status request
					String secondaryStatus = "4506000";
					toSend = (char) ESC + "|" + secondaryStatus + (char) CR;
					break;
				case '@':
					// ESC @: HP: one second delay
					try
					{
						Thread.sleep(1000L);
					}
					catch ( InterruptedException e )
					{
					}
					break;
				case '4':
					// ESC 4: set left margin
					terminalScreen.leftMargin = terminalScreen.xCursor;
					break;
				case '5':
					// ESC 5: set right margin
					terminalScreen.rightMargin = terminalScreen.xCursor;
					break;
				case '7':
					// ESC 7: save cursor and attributes
					terminalScreen.saveCursor();
					break;
				case '8':
					// ESC 8: restore cursor and attributes
					terminalScreen.restoreCursor();
				default:
					System.out.println(
							"*** Unknown Escape sequence [Esc] " + esc.toString());
					break;
				}

				return MODE_IDLE;
			}

			/**
			 * actions:
			 * <ul>
			 * <li>open file</li>
			 * <li>send file via serial</li>
			 * <li>send two NULL bytes</li>
			 * <li>close file</li>
			 * </ul>
			 *
			 * @param fileName
			 *           the name of the file to send.
			 * @param millis
			 *           the delay between bytes in milliseconds.
			 */
			private void sendBinaryFile ( String fileName, long millis )
			{
				if ( DEBUG > 0 )
				{
					System.out.print("ESC e: ");
				}
				System.out.print("Sending raw binary file '" + fileName + "' ");

				BufferedReader br = null;
				try
				{
					int b;

					File theFile = new File(fileName);
					long len = theFile.length();
					long count = 0;
					long step = len / 20;
					long nextDot = 0;

					br = new BufferedReader(new FileReader(fileName));

					while ( (b = br.read()) != -1 )
					{
						if ( count++ == nextDot )
						{
							System.out.print('.');
							nextDot += step;
						}
						sendByte(b);

						// depending on receiver, wait
						try
						{
							Thread.sleep(millis);
						}
						catch ( InterruptedException e )
						{
						}

					}
					// success: two trailing zeros
					b = 0;
					sendByte(b);
					sendByte(b);
					br.close();
					System.out.println(" successful.");
				}
				catch ( FileNotFoundException e )
				{
					e.printStackTrace();
				}
				catch ( IOException e )
				{
					if ( br != null )
					{
						// success: two trailing zeros
						int b = 0xFF;
						sendByte(b);
						sendByte(b);
						try
						{
							br.close();
						}
						catch ( IOException e1 )
						{
						}
						System.out.println(" failed.");
					}
					e.printStackTrace();
				}
			}

			/**
			 * Handle Esc [ ... sequences. These are usually ANSI terminal
			 * sequences which do not interfere with HP sequences.
			 *
			 * @param esc
			 *           - the collected escape sequence.
			 * @return - always MODE_IDLE.
			 */
			private char handleEscBracket ( EscapeSequence esc )
			{
				char escMode = MODE_IDLE;
				int valParam;

				// end of sequence
				if ( DEBUG > 0 )
				{
					dumpEscapeSequence(esc);
				}

				// start after '['
				esc.setIndex(1);

				int c = esc.getLast();

				switch ( c )
				{
				case '@': // ESC [ Pn @
					terminalScreen.insertCharsInLine(esc.parseASCIIInteger(1));
					break;

				case 'A': // ESC [ Pn A - cursor left
					terminalScreen.moveCursor(-esc.parseASCIIInteger(1), 0);
					break;

				case 'B': // ESC [ Pn B - cursor right
					terminalScreen.moveCursor(esc.parseASCIIInteger(1), 0);
					break;

				case 'C': // ESC [ Pn C - cursor down
					terminalScreen.moveCursor(0, esc.parseASCIIInteger(1));
					break;

				case 'D': // ESC [ Pn D - cursor up
					terminalScreen.moveCursor(0, -esc.parseASCIIInteger(1));
					break;

				case 'H':
				case 'f':
					// 0-based
					int row = esc.parseASCIIInteger(0);
					int col = esc.parseASCIIInteger(0);
					terminalScreen.setCursorRelScreen(row, col);
					break;

				case 'J':
					// erase in screen
					valParam = esc.parseASCIIInteger(0);
					if ( valParam == 0 )
					{
						// cursor to end of screen
						// ESC [ J
						// ESC [ 0 J
						terminalScreen.clearToEOS();
					}
					else if ( valParam == 1 )
					{
						// from cursor to start of screen
						// ESC [ 1 J
						terminalScreen.clearToBOS();
					}
					else if ( valParam == 2 )
					{
						// clear complete screen
						// ESC [ 2 J
						terminalScreen.clearScreen();
					}
					break;

				case 'K':
					// erase in line
					valParam = esc.parseASCIIInteger(0);
					if ( valParam == 0 )
					{
						// from cursor to end of line
						// ESC [ K
						// ESC [ 0 K
						terminalScreen.clearToEOL();
					}
					else if ( valParam == 1 )
					{
						// from cursor to start of line
						// ESC [ 1 K
						terminalScreen.clearToBOL();
					}
					else if ( valParam == 2 )
					{
						// entire line
						// ESC [ 2 K
						terminalScreen.clearLine();
					}
					break;

				case 'L':
					// ESC [ 1 L
					// insert blank line,
					// shift current and remaining lines down
					valParam = esc.parseASCIIInteger(1);
					while ( valParam-- > 0 ) {
						terminalScreen.insertLine();
					}
					break;

				case 'M':
					// ESC [ 1 M
					// delete current line,
					// shift remaining lines up
					valParam = esc.parseASCIIInteger(1);
					while ( valParam-- > 0 ) {
						terminalScreen.deleteCurrentLine();
					}
					break;

				case 'P':
					// ESC [ P
					// delete characters at cursor,
					// shift trailing characters on line left
					terminalScreen.deleteCharsInLine(esc.parseASCIIInteger(1));
					break;

				case 'X':
					// ESC [ X
					// clear 1... characters
					// (at max up to end of line)
					terminalScreen.clearChars(esc.parseASCIIInteger(1));
					break;

				case 'm':
					// ESC [ m .......... normal
					// ESC [ 0 m ........ normal
					// ESC [ 1 m ........ highlight
					// ESC [ 5 m ........ underline
					// ESC [ 7 m ........ inverse
					// ESC [ 30...37 m .. foreground color
					// ESC [ 40...47 m .. background color
					terminalScreen.setAttribute((byte) esc.parseASCIIInteger(0));
					break;

				case 'n':
					// ESC [ 6 n ........ cursor position request
					if ( esc.peekCharacter() == '6' )
					{
						// reply: ESC [ <row> ; <col> R
						// zero-based
						sendString((char) ESC + "[" + Dig3(terminalScreen.xCursor)
						+ ";" + Dig3(terminalScreen.yCursor) + "R"
						+ (char) CR);
					}
					break;

				case 'h':
					// e.g. ESC [ ? 7 h
					// enable line wrap
					if ( esc.peekCharacter() == '?' )
					{
						esc.incrementIndex(1);
						if ( esc.peekCharacter() == '7' ) {
							terminalScreen.setLineWrap(true);
						}
					}
					break;

				case 'l':
					// e.g. ESC [ ? 7 l
					// disable line wrap
					if ( esc.peekCharacter() == '?' )
					{
						esc.incrementIndex(1);
						if ( esc.peekCharacter() == '7' ) {
							terminalScreen.setLineWrap(false);
						}
					}
					break;

				case 's':
					// e.g. ESC [ > 1 s HOME DOWN
					// e.g. ESC [ > 0 s HOME UP
					if ( esc.peekCharacter() == '>' )
					{
						esc.incrementIndex(1);
						if ( esc.parseASCIIInteger(0) == 0 ) {
							terminalScreen.homeScreenUp();
						} else {
							terminalScreen.homeScreenDown();
						}
					}
					break;

				default:
					break;
				}

				if ( DEBUG > 0 )
				{
					dumpEscapeSequence(esc);
				}

				return escMode;
			}

			/**
			 * Handle [Esc * d] ... Sequences.
			 *
			 * @param esc
			 * @return MODE_IDLE
			 */
			private char handleEscAsteriskD ( EscapeSequence esc )
			{
				// display control
				char escMode = MODE_IDLE;

				// up to 4 integer parameters
				int number[] = new int[4];

				// start after "*d"
				esc.setIndex(2);

				while ( esc.hasMore() )
				{
					// try to read optional number(s)
					int count = readASCIINumbers(esc, number);

					// read code
					int c = Character.toLowerCase(esc.parseCharacter());

					switch ( c )
					{
					case 'a':
						// Esc * d <pen#> a == Graphics clear

						// optional: write current screen to a file
						try
						{
							// save only non-empty images
							if ( graphicsScreen
									.saveImage("image" + idxImage + ".png") ) {
								idxImage++;
							}
						}
						catch ( IOException e )
						{
							e.printStackTrace();
						}

						if ( count > 0 )
						{
							if ( DEBUG > 0 ) {
								System.out.println("GCLEAR(" + number[0] + ");");
							}
							graphicsScreen.clear(number[0]);
						}
						else
						{
							if ( DEBUG > 0 ) {
								System.out.println("GCLEAR(0);");
							}
							graphicsScreen.clear();
						}
						break;
					case 'b':
						// Esc * d <color#> b == set Graphics memory
						if ( count > 0 )
						{
							if ( DEBUG > 0 ) {
								System.out.println("GSET(" + number[0] + ");");
							}
							graphicsScreen.clear(number[0]);
						}
						else
						{
							if ( DEBUG > 0 ) {
								System.out.println("GSET(7);");
							}
							graphicsScreen.clear(7);
						}
						break;
					case 'c':
						// Esc * d c == Graphics screen ON
						if ( DEBUG > 0 ) {
							System.out.println("GSHOW();");
						}
						graphicsFrame.setVisible(true);
						break;
					case 'd':
						// Esc * d d == Graphics screen OFF
						if ( DEBUG > 0 ) {
							System.out.println("GHIDE();");
						}
						graphicsFrame.setVisible(false);
						break;
					case 'e':
						// Esc * d e == Alpha screen ON
						if ( DEBUG > 0 ) {
							System.out.println("ALPHA(ON);");
						}
						m_AlphaActive = true;
						break;
					case 'f':
						// Esc * d f == Alpha screen OFF
						if ( DEBUG > 0 ) {
							System.out.println("ALPHA(OFF);");
						}
						m_AlphaActive = false;
						break;
					case 'k':
						// Esc * d k == Graphics cursor ON
						graphicsScreen.showGraphicsCursor(true);
						if ( DEBUG > 0 ) {
							System.out.println("GCURSOR(ON);");
						}
						break;
					case 'l':
						// Esc * d l == Graphics cursor OFF
						graphicsScreen.showGraphicsCursor(false);
						if ( DEBUG > 0 ) {
							System.out.println("GCURSOR(OFF);");
						}
						break;
					case 'o':
						// Esc * d <x>,<y> o == position Graphics cursor
						if ( count > 1 )
						{
							graphicsScreen.setCursorPosition(number[0], number[1]);
							if ( DEBUG > 0 ) {
								System.out.println("GCURSOR(" + number[0] + ","
										+ number[1] + ");");
							}
						}
						break;
					case 'p':
						// Esc * d <x>,<y> p == move Graphics cursor
						if ( count > 1 )
						{
							graphicsScreen.incrementCursorPosition(number[0],
									number[1]);
							if ( DEBUG > 0 ) {
								System.out.println("GCURSOR by (" + number[0] + ","
										+ number[1] + ");");
							}
						}
						break;

					case 'q':
						// Esc * d q == Alpha cursor ON
						if ( DEBUG > 0 ) {
							System.out.println("ACURSOR(ON);");
						}
						terminalScreen.setcursorVisible(true);
						break;
					case 'r':
						// Esc * d r == Alpha cursor OFF
						if ( DEBUG > 0 ) {
							System.out.println("ACURSOR(OFF);");
						}
						terminalScreen.setcursorVisible(false);
						break;
					case 's':
						// Esc * d s == Graphics text mode ON
						escMode = MODE_ESC_GRAPH_TEXT;
						// graphicsScreen.setDrawMode(2); // Jam1
						if ( DEBUG > 0 ) {
							System.out.println("GTEXT(ON);");
						}
						break;
					case 't':
						// Esc * d t == Graphics text mode OFF
						if ( DEBUG > 0 ) {
							System.out.println("GTEXT(OFF);");
						}
						break;
					case 'y':
						// Esc * d 0,0,511,389 y == Graphics size in pixel
						// (inclusive)
						// Esc * d 0,0,639,399 y
						// Esc * d lox,loy hix,hiy y
						int w = 1 + (number[2] - number[0]);
						int h = 1 + (number[3] - number[1]);
						graphicsScreen.setScreenSize(w, h);

						if ( DEBUG > 0 ) {
							System.out.println("GSIZE " + esc.toString() + "(" + w
									+ "x" + h + ")");
						}

						break;
					case 'z':
						// Esc * d z == NOP
						break;
					case '\r':
					case '\n':
						// skip
						break;
					default:
						System.out.println("Unknown [Esc * d '" + c + "'].");
						break;
					}

				}

				return escMode;
			}

			/**
			 * Format a 3-digit integer number with leading zeros for cursor
			 * position responses sent from the terminal.
			 *
			 * @param number
			 *           - the number to convert
			 * @return - a 3 character string with leading zeros (if any).
			 */
			private String Dig3 ( int number )
			{
				String s = "000" + Integer.toString(number);
				int i = s.length();
				return s.substring(i - 3);
			}

			/**
			 * Read as many numeric parameters as we can find respectively as many
			 * as fit into number[].
			 *
			 * @param esc
			 *           - the escape sequence to parse.
			 * @param number
			 *           - array to receive the parsed numbers.
			 * @return the number of valid elements in number[].
			 */
			private int readASCIINumbers ( EscapeSequence esc, int[] number )
			{
				int idx = 0;
				for ( int i = 0; i < number.length; i++ )
				{
					number[idx] = esc.parseASCIIInteger();
					// end of valid numbers?
					if ( number[idx] == Integer.MIN_VALUE ) {
						break;
					}
					idx++;
				}
				return idx;
			}

			/**
			 * Handle [Esc * m] ... Sequences.
			 *
			 * @param esc
			 * @return MODE_IDLE
			 */
			private char handleEscAsteriskM ( EscapeSequence esc )
			{
				char escMode = MODE_IDLE;

				// up to 4 integer parameters
				int number[] = new int[4];

				// start after "*m"
				esc.setIndex(2);

				while ( esc.hasMore() )
				{
					// try to read optional number(s)
					int count = readASCIINumbers(esc, number);

					// read code
					int c = Character.toLowerCase(esc.parseCharacter());

					switch ( c )
					{
					case 'a':
						// Esc * m <n> a, <n> in [0...5]
						// Esc * m 3 a set draw mode 3
						int drawMode = 0;
						if ( count > 0 ) {
							drawMode = Math.min(number[0], 5);
						}
						if ( DEBUG > 0 ) {
							System.out.println("DrawMode(" + drawMode + ");");
						}
						graphicsScreen.setDrawMode(drawMode);
						break;

					case 'b':
						// Esc * m 01 b == set line type 01, <n> in [1...11]
						int lineType = 0;
						if ( count > 0 ) {
							lineType = Math.min(number[0], 11);
						}
						graphicsScreen.setLineStyle(lineType);
						if ( DEBUG > 0 ) {
							System.out.println("LT" + lineType + ";");
						}
						break;

					case 'e':
						// Esc * m 0,0,511,287 e = fill rectangle
						// absolute <x1,y1,x2,y2>
						if ( count == 4 )
						{
							// increase by 1 pixel to the right
							int x1 = number[0];
							int y1 = number[1];
							int x2 = number[2];
							int y2 = number[3];
							graphicsScreen.fillRect(x1, y1, x2 - x1, y2 - y1);
							if ( DEBUG > 0 )
							{
								System.out.println("FillRect(" + x1 + "," + y1 + "-"
										+ x2 + "," + y2 + ");");
							}
						}
						break;

					case 'm':
						// Esc * m <n> m == set text size, <n> in [1...8]
						int size = 1;
						if ( count > 0 ) {
							size = Math.min(number[0], 8);
						}
						graphicsScreen.setTextSize(size);
						if ( DEBUG > 0 )
						{
							System.out.println("TextSize(" + size + ");");
						}
						break;

					case 'n':
						// Esc * m 1,2,3,4 n == text orientation
						// 0, 90, 180, 270 degrees
						int orientation = 0;
						if ( count > 0 ) {
							orientation = (Math.min(number[0], 4) - 1) * 90;
						}
						graphicsScreen.setTextOrientation(orientation);
						if ( DEBUG > 0 )
						{
							System.out.println(
									"TextOrientaton(" + orientation + " deg);");
						}
						break;

					case 'o':
						// Esc * m o == text slant ON
						graphicsScreen.setTextSlant(true);
						if ( DEBUG > 0 )
						{
							System.out.println("TextSlant(ON);");
						}
						break;

					case 'p':
						// Esc * m p == text slant OFF
						graphicsScreen.setTextSlant(false);
						if ( DEBUG > 0 )
						{
							System.out.println("TextSlant(OFF);");
						}
						break;

					case 'r':
						// Esc * m r == set Graphics defaults
						if ( DEBUG > 0 )
						{
							System.out.println("GraphicsDefaults();");
						}
						break;

					case 'x':
						// Esc * m 0 x == set primary pen 0
						// Esc * m 7 x == set primary pen 7
						int pen = number[0];
						if ( pen == Integer.MIN_VALUE ) {
							pen = 0;
						}
						pen++; // 1-based
						graphicsScreen.setForeColor(pen);
						if ( DEBUG > 0 )
						{
							System.out.println("SP" + pen + ";");
						}
						break;
					case 'Z':
						break;
					}
				}

				return escMode;
			}

			/**
			 * Handle Esc [*][n] ... Sequences.
			 *
			 * @param esc
			 * @return MODE_IDLE
			 */
			private char handleEscAsteriskN ( EscapeSequence esc )
			{
				char escMode = MODE_IDLE;

				int c = esc.getLast();
				esc.setIndex(2);

				switch ( c )
				{
				case 'X':
					// graphics text pen
					// Esc * n 0 x == set graphics text pen 0
					// Esc * n x == set graphics text pen to track primary pen
					int pen = esc.parseASCIIInteger(-1);
					pen++; // 1-based, 0=default==track primary pen

					// @TODO check
					graphicsScreen.setTextColor(pen);

					if ( DEBUG > 0 )
					{
						System.out.println("TextPen " + pen + ";");
					}
					break;
				}

				return escMode;
			}

			/**
			 * Handle [Esc * s] ... Sequences.
			 *
			 * @param esc
			 * @return MODE_IDLE
			 */
			private char handleEscAsteriskS ( EscapeSequence esc )
			{
				Point pt;
				int n;
				char escMode = MODE_IDLE;

				int c = esc.getLast();
				esc.setIndex(2);

				if ( c == '^' )
				{
					int code = esc.parseASCIIInteger();

					switch ( code )
					{
					case 1:
						// Esc * s 1 ^ == read terminal ID
						// returns: 5 character field
						// "2627A" + CR
						toSend = terminalSettings.AnswerBack + (char) CR;

						if ( DEBUG > 0 )
						{
							System.out.println(
									"Answerback: " + terminalSettings.AnswerBack);
						}
						break;

					case 2:
						// Esc * s 2 ^ == read graphics pen position
						// returns:
						// "+00360,+00080,0" + CR
						pt = graphicsScreen.getPenPosition();
						n = graphicsScreen.getPenState() ? 1 : 0;
						/*
                     toSend = String.format("+%05d,+%05d,%1d", new Object[]
                     { new Integer(pt.x), new Integer(pt.y), new Integer(n) })
                           + (char) CR;
						 */
						toSend = String.format("+%05d,+%05d,%1d", pt.x,pt.y,n) + (char) CR;

						if ( DEBUG > 0 )
						{
							System.out.println("Pen position: " + toSend);
						}
						break;

					case 3:
						// Esc * s 3 ^ == read current graphics cursor position
						// immediately returns:
						// "+00360,+00080" + CR
						pt = graphicsScreen.getCursorPosition();
						/*
                     toSend = String.format("+%05d,+%05d", new Object[]
                     { new Integer(pt.x), new Integer(pt.y) }) + (char) CR;
						 */
						toSend = String.format("+%05d,+%05d", pt.x,pt.y) + (char) CR;

						if ( DEBUG > 0 )
						{
							System.out.println("Cursor position: " + toSend);
						}
						break;

					case 4:
						// Esc * s 4 ^ == read graphics cursor position with
						// wait for mouse click or key press
						graphicsScreen.waitForClick();
						escMode = WAIT_FOR_GRAPH_CLICK;
						if ( DEBUG > 0 )
						{
							System.out.println("Waiting for cursor click");
						}
						break;

					case 5:
						// Esc * s 5 ^ == read display size
						// returns:
						// "+00000,+00000,+00639,+00399,00002.,00002." + CR
						Dimension d = graphicsScreen.getSize();
						// dots per millimeter
						char dpmm = (terminalSettings.TerminalID == TerminalSettings.HP2627A)
								? '2'
										: '3';
						/*
                     toSend = "+00000,+00000,"
                           + String.format("+%05d,+%05d", new Object[]
                     { new Integer(d.width - 1), new Integer(d.height - 1) })
                           + ",0000" + dpmm + ".,0000" + dpmm + '.' + (char) CR;
						 */
						toSend = "+00000,+00000,"
								+ String.format("+%05d,+%05d", (d.width-1), (d.height-1)) 
								+ ",0000" + dpmm + ".,0000" + dpmm + '.' + (char) CR;
						if ( DEBUG > 0 )
						{
							System.out.println(
									"Answerback: " + terminalSettings.AnswerBack);
						}
						break;

					case 8:
						// Esc * s 8 ^ == read zoom status
						// returns:
						// "001.,0" + CR

						toSend = "001.,0" + (char) CR;

						if ( DEBUG > 0 )
						{
							System.out.println(
									"Answerback: " + terminalSettings.AnswerBack);
						}
						break;

					default:
						sendString("0" + (char) CR);
						if ( DEBUG > 0 )
						{
							System.out.println("*** Unimplemented Esc *s " + code);
						}
						break;
					}
				}

				return escMode;
			}

			/**
			 * Handle [Esc & a] ... Sequences.
			 *
			 * @param escMode
			 * @param esc
			 *           - the complete Escape sequence (sans Esc )
			 * @param c
			 *           - current (last) character of sequence
			 * @return
			 */
			private char handleEscAmpersAndA ( EscapeSequence esc )
			{
				char escMode = MODE_IDLE;

				// Esc & a 12 r 04 c == row and column
				// Esc & a 54 c == column only
				// Esc & a 5 r == row only

				// up to 4 integer parameters
				int number[] = new int[1];

				// start after "&a"
				esc.setIndex(2);

				// start with current position in screen system
				int col = terminalScreen.xCursor;
				int row = terminalScreen.yCursor;
				// relative to memory
				row += terminalScreen.getStartRow();

				while ( esc.hasMore() )
				{
					// check sign of optional number
					char sign = esc.peekCharacter();

					// read number
					int count = readASCIINumbers(esc, number);

					// read code
					int c = Character.toLowerCase(esc.parseCharacter());

					switch ( c )
					{
					case 'r':
						// row rel. memory
						if ( count != 0 )
						{
							if ( sign == '-' || sign == '+' )
							{ // relative to current position

								row = row + number[0];
							}
							else
							{
								row = number[0];
							}
						}
						break;

					case 'y':
						// row rel. screen
						if ( count != 0 )
						{
							if ( sign == '-' || sign == '+' )
							{ // relative to current position
								row = row + number[0];
							}
							else
							{
								row = number[0] + terminalScreen.getStartRow();
							}
						}
						break;

					case 'c':
						// column
						if ( count != 0 )
						{
							if ( sign == '-' || sign == '+' )
							{ // relative to current position
								col = col + number[0];
							}
							else
							{
								col = number[0];
							}
						}
						break;
					}
				}

				terminalScreen.setCursorRelMemory(row, col);

				return escMode;
			}

			/**
			 * Handle [Esc & d] ... Sequences.
			 *
			 * @param escMode
			 * @param esc
			 *           - the complete Escape sequence (sans Esc )
			 * @param c
			 *           - current (last) character of sequence
			 * @return
			 */
			private char handleEscAmpersAndD ( EscapeSequence esc )
			{
				char escMode = MODE_IDLE;
				// Esc & d @ == end enhancement
				// Esc & d B == inverse
				// Esc & d C == inverse+blink
				// Esc & d J

				int c = esc.getLast();

				switch ( c )
				{
				case '@':
					terminalScreen.setAttribute((byte) 0);
					break;
				case 'B':
					terminalScreen.setAttribute((byte) 7);
					break;
				case 'C':
					terminalScreen.setAttribute((byte) 7); // inverse
					break;
				case 'D':
				case 'E':
					terminalScreen.setAttribute((byte) 5); // underline
					break;
				case 'F':
				case 'G':
					terminalScreen.setAttribute((byte) 7); // inverse
					terminalScreen.setAttribute((byte) 5); // underline
					break;
				case 'J':
					terminalScreen.setAttribute((byte) 7);
					break;
				}

				return escMode;
			}

			/**
			 * Esc * p plotting
			 */
			// pen state
			final static int PEN_UP            = 0;
			final static int PEN_DN            = 1;
			// move mode
			final static int MOVE_ABS          = 0;
			final static int MOVE_INC          = 1;
			final static int MOVE_REL          = 2;
			// number format
			final static int FORM_ASCII        = 0;
			final static int FORM_BINARY       = 1;
			final static int FORM_BINARY_SHORT = 2;

			private int handlePlotting ( EscapeSequence esc )
			{
				char escMode = MODE_IDLE;
				// Esc * p a == lift pen
				// Esc * p b == lower pen
				// Esc * p e == set origin for relocatable plotting
				// coordinate mode:
				// Esc * p f == ASCII, absolute (default)
				// Esc * p g == ASCII, incremental
				// Esc * p h == ASCII, relocatable
				// Esc * p i == Binary, absolute
				// Esc * p j == Binary, short, incr.
				// Esc * p k == Binary, incremental
				// Esc * p l == Binary, relocatable
				//
				// Esc * p s == start polygon fill area
				// Esc * p a == close polygon/begin new polygon
				// Esc * p t == terminate polygon fill area
				// terminator
				// Esc * p z == NOP

				// start parsing
				esc.setIndex(2);

				// defaults
				int penState = PEN_UP;
				int moveMode = MOVE_ABS;
				int numberForm = FORM_ASCII;
				boolean fillPoly = false;   // see p. 5-15 HP 2627A Color Graphics Terminal Reference Manual

				// coordinate index [0,1]
				int idx = 0;
				Point pt = new Point();
				Point ptCurrent = new Point();
				// relocatable origin
				Point ptOrigin = new Point();

				while ( esc.hasMore() )
				{
					char cNext = Character.toLowerCase(esc.peekCharacter());

					int increment = 1;

					switch ( cNext )
					{
					case 'a':
						penState = PEN_UP;
						if ( psHPGL != null )
						{
							psHPGL.print(";\nPU");
						}
						if ( fillPoly )
						{
							// TODO: close current polygon segment and start a new
							// segment (e.g. for alternating fills)
						}
						break;

					case 'b':
						penState = PEN_DN;
						if ( psHPGL != null )
						{
							psHPGL.print(";\nPD");
						}
						break;

					case 'c':
						break;

					case 'd':
						// Esc * p d == plot a point at current pen position
						if ( psHPGL != null )
						{
							psHPGL.print(";PD;PU");
						}
						break;

					case 'e':
						// Esc * p e == set origin for relocatable plotting
						ptOrigin.x = ptCurrent.x;
						ptOrigin.y = ptCurrent.y;
						break;

					case 'f':
						// Esc * p f == ASCII, absolute
						numberForm = FORM_ASCII;
						moveMode = MOVE_ABS;
						if ( psHPGL != null )
						{
							psHPGL.print(";\nPA");
						}
						break;

					case 'g':
						// Esc * p g == ASCII, incremental
						numberForm = FORM_ASCII;
						moveMode = MOVE_INC;
						if ( psHPGL != null )
						{
							psHPGL.print(";\nPR");
						}
						break;

					case 'h':
						// Esc * p h == ASCII, relocatable
						numberForm = FORM_ASCII;
						moveMode = MOVE_REL;
						if ( psHPGL != null )
						{
							psHPGL.print(";\nPA");
						}
						break;

					case 'i':
						// Esc * p i == Binary, absolute
						numberForm = FORM_BINARY;
						moveMode = MOVE_ABS;
						if ( psHPGL != null )
						{
							psHPGL.print(";\nPA");
						}
						break;

					case 'j':
						// Esc * p j == Binary, short, incr.
						numberForm = FORM_BINARY_SHORT;
						moveMode = MOVE_INC;
						if ( DEBUG > 0 )
						{
							psHPGL.print(";\nPR");
						}
						break;

					case 'k':
						// Esc * p k - Binary, incremental
						numberForm = FORM_BINARY;
						moveMode = MOVE_INC;
						if ( psHPGL != null )
						{
							psHPGL.print(";\nPR");
						}
						break;

					case 'l':
						// Esc * p l == Binary, relocatable
						numberForm = FORM_BINARY;
						moveMode = MOVE_REL;
						if ( psHPGL != null )
						{
							psHPGL.print(";\nPA");
						}
						break;

					case 's':
						// Esc * p s == begin polygon area fill
						// up to 149 points should be collected into0 a polygon and
						// filled when
						// an 't' closes the polygon or an upper case letter
						// terminates the sequence
						// The polygon shall be filled with current drawing mode,
						// area pattern, area boundary color, and pen.
						// TODO: set a state flag to start collecting points into a
						// polygon.
						fillPoly = true;

						break;

					case 't':
						// Esc * p t == close polygon area fill
						// up to 149 points should be collected into0 a polygon and
						// filled when
						// an 't' closes the polygon or an upper case letter
						// terminates the sequence
						// The polygon shall be filled with current drawing mode,
						// area pattern, area boundary color, and pen.
						// TODO: reset a state flag
						if ( fillPoly )
						{
							// TODO: plot polygon in polygon list, reset polygon
							// list
						}

						fillPoly = false;
						break;

					case 'z':
						// NOP, may end the sequence
						break;

					default:
						if ( numberForm == FORM_ASCII )
						{
							if ( cNext == ',' || cNext == ' ' )
							{
								// skip separator
								break;
							}
						}

						// no character, must be a number
						increment = 0;
						break;
					}

					if ( increment == 1 )
					{
						esc.incrementIndex(increment);
					}
					else
					{
						if ( cNext < 32 )
						{
							warning(1, "incorrect character in Esc * p sequence.");
							break;
						}
						// parse number or separator
						int value = 0;

						if ( numberForm == FORM_ASCII )
						{
							// get a signed integer
							value = esc.parseASCIIInteger();
							if ( value == Integer.MIN_VALUE )
							{
								// error
								warning(1, "Cannot parse number.");
								break;
							}
						}
						else if ( numberForm == FORM_BINARY )
						{
							if ( moveMode == MOVE_ABS )
							{
								// get a 2-byte unsigned integer
								value = esc.parseBinaryWord();
							}
							else if ( moveMode == MOVE_INC || moveMode == MOVE_REL )
							{
								// get a 3-byte unsigned integer
								// - convert later after error test
								value = esc.parseBinaryTriple();
							}

							if ( value == Integer.MIN_VALUE )
							{
								// error
								warning(1, "Cannot parse number.");
								break;
							}

							if ( moveMode == MOVE_INC || moveMode == MOVE_REL )
							{
								// 3-byte unsigned -> signed
								if ( value > 16383 ) {
									value = value - 32768;
								}
							}
						}
						else if ( numberForm == FORM_BINARY_SHORT )
						{
							// get a 1-byte unsigned integer
							// - convert later after error test
							value = esc.parseBinaryByte();

							if ( value == Integer.MIN_VALUE )
							{
								// error
								warning(1, "Cannot parse number.");
								break;
							}

							// unsigned -> signed
							if ( value > 16 ) {
								value = value - 32;
							}
						}

						if ( idx == 0 )
						{
							pt.x = value;
							idx++;
						}
						else
						{
							pt.y = value;

							if ( moveMode == MOVE_INC )
							{
								ptCurrent.x += pt.x;
								ptCurrent.y += pt.y;
							}
							else if ( moveMode == MOVE_REL )
							{
								ptCurrent.x = ptOrigin.x + pt.x;
								ptCurrent.y = ptOrigin.y + pt.y;
							}
							else
							{
								ptCurrent.x = pt.x;
								ptCurrent.y = pt.y;
							}

							if ( fillPoly )
							{
								// TODO: append ptCurrent to polygon list
							}
							else
							{
								// execute as normal moveto/lineto
							}

							if ( penState == PEN_UP )
							{
								// moveto
								graphicsScreen.moveto(ptCurrent);
								penState = PEN_DN;
								if ( psHPGL != null )
								{
									psHPGL.print("," + ptCurrent.x + "," + ptCurrent.y
											+ ";\nPD");
								}
							}
							else
							{
								// lineto
								if ( psHPGL != null )
								{
									psHPGL.print("," + ptCurrent.x + "," + ptCurrent.y);
								}
								graphicsScreen.lineto(ptCurrent);
							}
							idx = 0;
						}
					}
				}

				if ( fillPoly )
				{
					// TODO: plot any remaining polygon in polygon list, reset
					// polygon list
				}

				return escMode;
			}

			private void dumpEscapeSequence ( EscapeSequence esc )
			{
				System.out.print("Esc " + esc.toString() + "   ==   HEX( ");

				byte buff[] = esc.toString().getBytes();
				System.out.print(Integer.toHexString(buff[0]));
				for ( int i = 1; i < buff.length; i++ ) {
					System.out.print(", " + Integer.toHexString(buff[i]));
				}
				System.out.println(" )");
			}
		});

		// go!
		t.start();
		terminalFrame.setVisible(true);
		graphicsFrame.setVisible(terminalSettings.graphicsVisible);
	}

	/**
	 * Show the status of the incoming CTS and DSR lines.
	 */
	private void showLineStatus ( PrintStream ps )
	{
		try
		{
			ps.println("Serial Port Settings:");

			ps.println("Port               \t= '" + m_Port.getPortName() + "'");
			ps.println(
					"Input Buffer Bytes \t= " + m_Port.getInputBufferBytesCount());
			ps.println(
					"Output Buffer Bytes\t= " + m_Port.getOutputBufferBytesCount());

			ps.println("Library Version    \t= "
					+ SerialNativeInterface.getLibraryVersion());

			ps.print("Operating System    \t= ");
			int os = SerialNativeInterface.getOsType();
			String osName = "unknown";
			switch ( os )
			{
			case SerialNativeInterface.OS_WINDOWS:
				osName = "Windows";
				break;
			case SerialNativeInterface.OS_LINUX:
				osName = "Linux";
				break;
			case SerialNativeInterface.OS_MAC_OS_X:
				osName = "MacOs X";
				break;
			case SerialNativeInterface.OS_SOLARIS:
				osName = "Solaris";
				break;
			}
			ps.println("'" + osName + "'");

			int state[] = m_Port.getLinesStatus();
			ps.println("CTS Status         \t= " + state[0]);
			ps.println("DSR Status         \t= " + state[1]);
		}
		catch ( SerialPortException e )
		{
			e.printStackTrace();
		}
	}

	/**
	 * Shows the given message on the terminal screen.
	 *
	 * @param msg
	 *           the message to display. May be terminated by CL,LF to move
	 *           cursor to the start of the next line.
	 */
	private void showMessage ( String msg )
	{
		char s[] = msg.toCharArray();
		for (char element : s) {
			putByte((byte) element);
		}
	}

	/**
	 * Get the user preference
	 *
	 * @return Preferences
	 */
	public static Preferences getPreferences ()
	{
		return (Preferences.userRoot().node(m_ApplicationKey));
	}

	/**
	 * Delete the user preferences so that the application will start with
	 * defaults when started the next time.
	 */
	public static void resetPreferences ()
	{
		try
		{
			Preferences.userRoot().node(m_ApplicationKey).clear();
		}
		catch ( Exception e )
		{
			// might be thrown in case of being an Applet
		}
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize ()
	{
		terminalFrame = new JFrame();
		terminalFrame.addWindowListener(new WindowAdapter()
		{
			public void windowClosing ( WindowEvent e )
			{
				if(remoteMode && (!serialMode)) {
					// shut down connection gracefully
					try {
						tc.setReaderThread(false);
						tc.unregisterInputListener();
						tc.disconnect();

						telnetInputStream.close();
						telnetOutputStream.close();
					}
					catch (IOException e2) {
						System.out.println("error on disconnect: "+e2.getMessage());
					}
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
				if ( psHPGL != null )
				{
					psHPGL.print(";SP0;");
					psHPGL.close();
				}

				if ( logging )
				{
					try
					{
						bwLog.close();
						bwLog = null;
					}
					catch ( IOException e1 )
					{
					}
				}

				theBeeper.close();

				// save settings for tomorrow
				Preferences p = getPreferences();
				Point pt = terminalFrame.getLocation();
				// window position on screen
				p.putInt("Alpha.x", pt.x);
				p.putInt("Alpha.y", pt.y);
				pt = graphicsFrame.getLocation();
				// window position on screen
				p.putInt("Graph.x", pt.x);
				p.putInt("Graph.y", pt.y);

				terminalSettings.savePreferences(p);
			}
		});

		terminalFrame.setFocusTraversalKeysEnabled(false);

		// This is important to capture the TAB key !
		terminalFrame.getContentPane().setFocusTraversalKeysEnabled(false);

		terminalFrame.addKeyListener(new KeyAdapter()
		{
			public void keyPressed ( KeyEvent e )
			{
				if ( terminalScreen.isKeyboardLocked() )
				{
					// special function to unlock keyboard
					if ( e.getKeyCode() == KeyEvent.VK_SCROLL_LOCK ) {
						terminalScreen.lockKeyboard(false);
					}
					return;
				}

				int c = e.getKeyCode();
				boolean control = e.isControlDown();
				boolean shift = e.isShiftDown();

				if ( DEBUG > 1 )
				{
					System.out.println("Key pressed: '" + (char) c + "' = 0x"
							+ Integer.toHexString(c) + ", Control:" + control
							+ " Shift:" + shift);
				}

				// for VT-100 special numpad handling
				// KeyEvent.VK_NUMPAD0
				// ...
				// KeyEvent.VK_NUMPAD9
				// KeyEvent.VK_KP_DOWN
				// KeyEvent.VK_KP_LEFT
				// KeyEvent.VK_KP_RIGHT
				// KeyEvent.VK_KP_UP
				// KeyEvent.KEY_LOCATION_NUMPAD
				// KeyEvent.VK_PAGE_DOWN
				// KeyEvent.VK_PAGE_UP
				// KeyEvent.VK_INSERT
				// KeyEvent.VK_DELETE

				// handle all special keys, which do not send a KeyTyped message
				// cursor keys into ANSI sequences
				try
				{
					switch ( c )
					{
					case KeyEvent.VK_UP:
						if ( control )
						{
							terminalScreen.scrollScreenUp(1); // local action
						}
						else
						{
							if ( localKeys ) {
								terminalScreen.moveCursor(-1, 0);
							} else {
								sendBytes(CUP);
							}
						}
						break;

					case KeyEvent.VK_DOWN:
						if ( control )
						{
							terminalScreen.scrollScreenDown(1); // local action
						}
						else
						{
							if ( localKeys ) {
								terminalScreen.moveCursor(1, 0); // local action
							} else {
								sendBytes(CDN);
							}
						}
						break;

					case KeyEvent.VK_LEFT:
						if ( localKeys ) {
							terminalScreen.moveCursor(0, -1); // local action
						} else {
							sendBytes(CLEFT);
						}
						break;

					case KeyEvent.VK_RIGHT:
						if ( localKeys ) {
							terminalScreen.moveCursor(0, 1); // local action
						} else {
							sendBytes(CRIGHT);
						}
						break;

					case KeyEvent.VK_PAGE_UP:
						terminalScreen.pageScreenUp(); // local action
						break;

					case KeyEvent.VK_PAGE_DOWN:
						terminalScreen.pageScreenDown(); // local action
						break;

					case KeyEvent.VK_HOME:
						terminalScreen.homeScreenUp(); // local action
						break;

					case KeyEvent.VK_END:
						terminalScreen.homeScreenDown(); // local action
						break;

					case KeyEvent.VK_INSERT:
						if ( control ) {
							terminalScreen.insertLine(); // local action
						} else if ( localKeys )
						{
							terminalScreen.toggleInsertMode(); // local action
						}
						break;

					case KeyEvent.VK_DELETE:
						if ( control ) {
							terminalScreen.deleteCurrentLine(); // local action
						} else if ( localKeys )
						{
							terminalScreen.deleteCharsInLine(1); // local action
						}
						break;

					case KeyEvent.VK_F1:
					case KeyEvent.VK_F2:
					case KeyEvent.VK_F3:
					case KeyEvent.VK_F4:
					case KeyEvent.VK_F5:
					case KeyEvent.VK_F6:
					case KeyEvent.VK_F7:
					case KeyEvent.VK_F8:
					case KeyEvent.VK_F9:
					case KeyEvent.VK_F10:
						handleFunctionKey(c, shift);
						break;

					case KeyEvent.VK_SCROLL_LOCK:
						// toggle locked keyboard
						terminalScreen
						.lockKeyboard(!terminalScreen.isKeyboardLocked());
						break;

					case KeyEvent.VK_PAUSE:
						// break
						if(!serialMode) break;
						System.out.print("Sending 250 ms BREAK");
						m_Port.sendBreak(250);
						try
						{
							Thread.sleep(300);
						}
						catch ( InterruptedException ex )
						{
							ex.printStackTrace();
						}
						System.out.println("... Done.");

						break;
					}
				}
				catch ( SerialPortException ex )
				{
					System.err.println(ex);
				}
			}

			public void keyTyped ( KeyEvent e )
			{
				if ( terminalScreen.isKeyboardLocked() ) {
					return;
				}

				char c = e.getKeyChar();
				boolean control = e.isControlDown();
				boolean shift = e.isShiftDown();

				if ( DEBUG > 1 )
				{
					System.out.println("Key typed: '" + c + "' = 0x"
							+ Integer.toHexString(c) + ", Control:" + control
							+ " Shift:" + shift);
				}

				// translate ENTER (10) -> CR (13)
				if ( c == KeyEvent.VK_ENTER )
				{
					sendByte((byte) terminalSettings.ENTER);
				}
				else if ( c == KeyEvent.VK_DELETE )
				{
					if ( !localKeys ) {
						sendByte((byte) c);
					}
				}
				else
				{
					sendByte((byte) c);
				}
			}

			public void keyReleased ( KeyEvent e )
			{
				// nothing happens here
			}
		});

		terminalFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		terminalScreen = new TerminalScreen(terminalFrame, this,
				terminalSettings.AnswerBack);
		terminalScreen.setFocusTraversalKeysEnabled(false);
		terminalFrame.getContentPane().add(terminalScreen, BorderLayout.CENTER);
		terminalFrame.pack();

		graphicsFrame = new JFrame();
		graphicsFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		graphicsFrame.setResizable(false);

		graphicsScreen = new GraphicsScreen(graphicsFrame,
				terminalSettings.AnswerBack, terminalSettings.width,
				terminalSettings.height);

		graphicsFrame.getContentPane().add(graphicsScreen, BorderLayout.CENTER);
		graphicsFrame.pack();

		{
			String fileName = "icnAlpha.png";

			java.awt.Toolkit tk = java.awt.Toolkit.getDefaultToolkit();
			// image file is in mh/rsc/...
			Image imgIcon = tk
					.createImage(getClass().getResource("rsc/" + fileName));
			terminalFrame.setIconImage(imgIcon);
		}

		{
			String fileName = "icnGraph.png";

			java.awt.Toolkit tk = java.awt.Toolkit.getDefaultToolkit();
			// from resource in path mh/rsc or .jar file
			Image imgIcon = tk
					.createImage(getClass().getResource("rsc/" + fileName));
			/*
			 * java.awt.Toolkit tk = java.awt.Toolkit.getDefaultToolkit(); Image
			 * imgIcon = tk.createImage(fileName);
			 */

			graphicsFrame.setIconImage(imgIcon);
		}

	}

	/**
	 * Function key handler
	 * @param c
	 *           The key code VK_F1 ... VK_F8.
	 * @param shift
	 *           Whether the SHIFT key is pressed.
	 */
	public void handleFunctionKey ( int c, boolean shift )
	{
		if ( c >= KeyEvent.VK_F1 && c <= KeyEvent.VK_F8 )
		{
			// HP key labels are organized in two rows of 8 each
			String label = terminalScreen.getKeyLabel(
					shift ? SoftKeys.ROW_TOP : SoftKeys.ROW_BOT, c - KeyEvent.VK_F1);
			System.out.println("F-key: '" + label + "'");
		}
		else
		{
			// F9 ... F12 are extra function keys,
			// not found on HP hardware.
			if(c == KeyEvent.VK_F9) {
				terminalScreen.toggleKeyLabels();
			} else if (c == KeyEvent.VK_F10) {
				terminalSettings.graphicsVisible= ! terminalSettings.graphicsVisible;
				if (terminalSettings.graphicsVisible)  {
					graphicsFrame.setVisible(true);
				} else {
					graphicsFrame.setVisible(false);
				}
			}
		}
	}

	/**
	 * Append one byte read from the host to the ring buffer.
	 *
	 * @param b
	 *           - the byte to append.
	 */
	private void putByte ( byte b )
	{
		buffer[ptrWrite] = b;
		ptrWrite++;
		//System.out.println("put byte "+b+" "+(char)b);
		if ( ptrWrite == BUFLEN ) {
			ptrWrite = 0;
		}

		if ( ptrWrite == ptrRead )
		{
			warning(1,
					"*** Error: Buffer overrun (" + ptrRead + "==" + ptrWrite + ")");
		}
	}

	/**
	 * Test whether there is anything in the ring buffer.
	 *
	 * @return true if there is data in the buffer.
	 */
	private boolean inputAvailable ()
	{
		/**
		 * <pre>
		 *  <-------  BUFLEN = 32   ------->
		 * {....12345678901234567890........}
		 *  0   4                   24     31
		 *      ^                   ^
		 *      ptrRead             ptrWrite
		 * len = (ptrWrite - ptrRead)
		 *     = (24 - 4) = 20
		 *
		 * {67890............123456789012345}
		 *  0    5           17            31
		 *       ^           ^
		 *       ptrWrite    ptrRead
		 * len = BUFLEN + (ptrWrite - ptrRead)
		 *     = 32     + (5 - 17) = 20
		 *
		 *
		 * len = (ptrWrite - ptrRead)
		 * if len < 0
		 *     len += BUFLEN
		 * </pre>
		 */
		return ptrWrite != ptrRead;
	}

	/**
	 * @return The currently used ring buffer length.
	 */
	int getBufferLength ()
	{
		int len = (ptrWrite - ptrRead);
		if ( len < 0 ) {
			len += BUFLEN;
		}
		return len;
	}

	/**
	 * Fetch the next byte from the ring buffer and remove it from the buffer.
	 * You should test first with {@link #inputAvailable()} whether there is
	 * anything in the buffer.
	 *
	 * @return The next byte from the ring buffer.
	 */
	private byte nextByte ()
	{
		byte b = buffer[ptrRead];

		ptrRead++;

		if ( ptrRead == BUFLEN ) {
			ptrRead = 0;
		}

		return b;
	}

	/**
	 * Preview the next byte from the ring buffer. It is not removed from the
	 * buffer. You should test first with {@link #inputAvailable()} whether there
	 * is anything in the buffer.
	 *
	 * @return The next byte from the ring buffer.
	 */
	private byte peekByte ()
	{
		return buffer[ptrRead];
	}

	/**
	 * Push a byte back into the ring buffer to be read by a following
	 * nextByte().
	 *
	 * @param b
	 *           - the value to be placed into the buffer.
	 */
	private void pushbackByte ( byte b )
	{
		ptrRead--;

		if ( ptrRead < 0 ) {
			ptrRead = BUFLEN - 1;
		}

		buffer[ptrRead] = b;
	}

	/**
	 * Dump the current contents of the ring buffer to System.out.
	 */

	private void dumpBuffer ()
	{
		StringBuilder sb = new StringBuilder();

		sb.append("Buffer(");
		sb.append(ptrRead);
		sb.append(',');
		sb.append(ptrWrite);
		sb.append(")={");

		int i = ptrRead;

		while ( i != ptrWrite )
		{
			char c = (char) buffer[i];

			if ( c < 128 )
			{
				sb.append('[');
				sb.append(ASCII[c]);
				sb.append(']');
			}
			else
			{
				sb.append("[0x");
				sb.append(Integer.toHexString(c & 0xFF));
				sb.append(']');
			}

			if ( ++i == BUFLEN ) {
				i = 0;
			}
		}
		sb.append('}');

		maxBufferLength = Math.max(maxBufferLength, getBufferLength());
		sb.append(" [max. length = " + maxBufferLength + "]");

		System.out.println(sb.toString());
	}

	void warning ( int code, String msg )
	{
		if ( code < 1 ) {
			System.err.println("*** Warning: " + msg);
		} else {
			System.err.println("*** Error: " + msg);
		}
	}

	/**
	 * Send the given string to the host.
	 *
	 * @param s
	 *           - The ASCII character string to be sent.
	 * @return The number of characters sent.
	 */
	int sendString ( String s )
	{
		int ret = 0;
		byte b[]= s.getBytes(StandardCharsets.US_ASCII);

		if ( remoteMode )
		{
			try
			{
				// serial transmit
				if(serialMode) {
					if ( m_Port.writeString(s) )
						ret = s.length();
					
				// telnet transmit
				} else {
					for(int i=0;i<b.length;i++) telnetOutputStream.write(b[i]);
					telnetOutputStream.flush(); 	
					ret= b.length;
				}
				if ( logging )
				{
					bwLog.write(("      {sent: '" + s.replace("\r", "[CR]") + "'}\n")
							.getBytes());
				}

				if ( DEBUG > 1 )
				{
					System.out.println("-> host '" + s.replace("\r", "[CR]") + "'");
				}
			}
			catch ( SerialPortException | IOException e )
			{
				String msg;
				if (e instanceof SerialPortException) {
					msg = "Error writing to serial port";
				} else {
					msg = "Error writing to telnet server";
				}
				remoteMode = false;
				terminalScreen.setRemoteMode(remoteMode);
				System.err.println(msg+": "+e.getMessage());
				showMessage(msg);
			}
		}
		else
		{
			// local
			terminalScreen.putString(s);
		}

		return ret;
	}

	/**
	 * Send the given byte to the host.
	 *
	 * @param theByte
	 *           - The byte to send.
	 * @return The number of bytes sent.
	 */
	int sendByte ( int theByte )
	{
		int ret = 0;

		if ( remoteMode )
		{
			try
			{
				// serial transmit
				if(serialMode) {
					m_Port.writeByte((byte) (theByte & 0xFF));
					
				// telnet transmit
				} else {
					telnetOutputStream.write((int) (theByte & 0xFF));
					telnetOutputStream.flush();
				}
				ret = 1;

				if ( logging )
				{
					int c = theByte & 0xFF;
					if ( c > 31 ) {
						bwLog.write(("      {sent '" + (char) c + "'}\n").getBytes());
					} else {
						bwLog.write(
								("      {sent 0x" + Integer.toHexString(theByte & 0xFF)
								+ "}\n").getBytes());
					}
				}

				if ( DEBUG > 1 )
				{
					System.out.println(
							"-> host 0x" + Integer.toHexString((byte) (theByte & 0xFF))
							+ " = '" + (char) theByte + "'");
				}
			}

			catch ( SerialPortException | IOException e )
			{
				String msg;
				if (e instanceof SerialPortException) {
					msg = "Error writing to serial port";
				} else {
					msg = "Error writing to telnet server";
				}
				remoteMode = false;
				terminalScreen.setRemoteMode(remoteMode);
				System.err.println(msg+": "+e.getMessage());
				showMessage(msg);
			}
		}
		else
		{
			// local
			terminalScreen.putByte((byte) theByte);
		}

		return ret;
	}

	/**
	 * Send the given bytes to the host.
	 *
	 * @param b
	 *           - An array of bytes to be sent.
	 * @return The number of bytes sent.
	 */
	int sendBytes ( byte b[] )
	{
		int ret = 0;

		if ( remoteMode )
		{
			try
			{
				// serial transmit
				if(serialMode) {
					if ( m_Port.writeBytes(b) )
						ret = b.length;
				} else {
					// telnet transmit
					for(int i=0; i < b.length;i++) telnetOutputStream.write(b[i] &0xFF); 
					telnetOutputStream.flush();
					ret= b.length;
				}


				if ( logging )
				{
					bwLog.write("      {sent: ".getBytes());

					for ( int i = 0; i < b.length; i++ )
					{
						if ( i > 0 ) {
							bwLog.write(", ".getBytes());
						}

						bwLog.write(("0x" + Integer.toHexString((byte) (b[i] & 0xFF)))
								.getBytes());
					}

					bwLog.write("}\n".getBytes());
				}

				if ( DEBUG > 1 )
				{
					System.out.print("-> host ");
					for ( int i = 0; i < b.length; i++ )
					{
						if ( i > 0 ) {
							System.out.print(", ");
						}
						System.out.print(
								"0x" + Integer.toHexString((byte) (b[i] & 0xFF)));
					}
					System.out.println();
				}
			}
			catch ( SerialPortException | IOException e )
			{
				String msg;
				if (e instanceof SerialPortException) {
					msg = "Error writing to serial port";
				} else {
					msg = "Error writing to telnet server";
				}
				remoteMode = false;
				terminalScreen.setRemoteMode(remoteMode);
				System.err.println(msg+": "+e.getMessage());
				showMessage(msg);
			}
		}
		else
		{
			// local
			terminalScreen.putBytes(b);
		}

		return ret;
	}

	/**
	 * Returns the code of the character key pressed with the Control key. The
	 * Control key modifies the raw character code by subtracting 64 from it.
	 *
	 * @param c
	 *           the character code to be modified.
	 *
	 * @return the character code with the Control key depressed.
	 */
	public static byte CTRL ( char c )
	{
		return (byte) (c - 64);
	}

	/**
	 * Called when new data arrives at the serial port
	 *
	 * Copies the data to the ring buffer for later processing.
	 */
	public void serialEvent ( SerialPortEvent serialPortEvent )
	{
		if ( serialPortEvent.isRXCHAR() )
		{
			int count = serialPortEvent.getEventValue();

			if ( count > 0 )
			{
				try
				{
					// append new data to ring buffer
					byte b[] = m_Port.readBytes(count);

					for (byte element : b) {
						putByte(element);
					}

					if ( DEBUG > 2 )
					{
						dumpBuffer();
					}

					if ( logging )
					{
						bwLog.write(b);
					}
				}
				catch ( SerialPortException e )
				{
					e.printStackTrace();
				}
				catch ( IOException e )
				{
					e.printStackTrace();
				}
			}
		}
	}
	/**
	 * Implemented method for TelnetNotificationHandler
	 * 
	 * Shows received negotiation requests, if DEBUG > 0
	 **/
	public void receivedNegotiation(final int negotiation_code, final int option_code) {
		String command;
		switch (negotiation_code) {
		case TelnetNotificationHandler.RECEIVED_DO:
			command = "DO";
			break;
		case TelnetNotificationHandler.RECEIVED_DONT:
			command = "DONT";
			break;
		case TelnetNotificationHandler.RECEIVED_WILL:
			command = "WILL";
			break;
		case TelnetNotificationHandler.RECEIVED_WONT:
			command = "WONT";
			break;
		case TelnetNotificationHandler.RECEIVED_COMMAND:
			command = "COMMAND";
			break;
		default:
			command = Integer.toString(negotiation_code); // Should not happen
			break;
		}
		if(DEBUG >0) {
			System.out.println("Received " + command + " for option code " + option_code);
		}
	}
/*
	private int getOption ( InputStream is, final String[] options )
			throws IOException
	{
		int b;
		b = is.read();
		System.out.println(
				Integer.toString(b) + " (" + (b < 40 ? options[b] : "?") + ")");
		return b;
	}
*/
}
