package mh;

import java.io.PrintStream;
import java.util.prefs.Preferences;

import jssc.SerialPort;

/**
 * A container to hold various general terminal settings.
 *
 * @author Martin Hepperle, July 2019
 *
 */
public class TerminalSettings
{
	public final static int ANSI = 100;
	public final static int HP2627A = 2627;
	public final static int HP2648A = 2648;

	// ENQ/ACK protocol?
	protected boolean ENQ_ACK;

	protected int speed;
	// silent or noisy?
	protected boolean Sound;
	// what the [ENTER] key sends
	protected int ENTER;

	// ANSI=100, 2627, 2648
	protected int TerminalID;
	// what is sent in reply to ENQ if not following the ENQ/ACK protocol
	protected String AnswerBack;

	protected int width;
	protected int height;

	protected String PortName;
	
	protected int telnetPort;
	
	protected String telnetHost;
	
	protected boolean graphicsVisible;

	int FontSize;

	public TerminalSettings()
	{
		// default: HP emulation
		// ENQ/ACK protocol?
		ENQ_ACK = true;
        speed = SerialPort.BAUDRATE_9600;
		PortName = "COM1";
		// ENTER key sends CR 0x0D
		ENTER = 13;
		setTerminalID(HP2627A);
		FontSize = 16;
		Sound = true;
		telnetHost = "";
		telnetPort = 23;
		graphicsVisible= false;
	}

	public void setTerminalID ( int id )
	{
		TerminalID = id;

		if (TerminalID == ANSI)
		{
			// ANSI
			// 640 x 480
			AnswerBack = "VT100";
			width = 640;
			height = 480;
		}
		else if (TerminalID == HP2648A)
		{
			// 2648A
			// 720 x 360
			AnswerBack = "2648A";
			width = 720;
			height = 360;
		}
		else
		{
			// HP2627A
			// lo-res 512 x 390
			AnswerBack = "2627A";
			width = 512;
			height = 390;
		}
	}

	public void savePreferences ( Preferences p )
	{
		p.putInt("Font.size", FontSize);
		p.put("Port.name", PortName);
		p.putInt("Port.speed", speed);
		p.putBoolean("Sound", Sound);
		p.putInt("TerminalID", TerminalID);
		p.put("TelnetHost", telnetHost);
		p.putInt("TelnetPort", telnetPort);
		p.putBoolean("GraphicsVisible",graphicsVisible);			
	}

	public void readPreferences ( Preferences p )
	{
		FontSize = p.getInt("Font.size", 12);
		PortName = p.get("Port.name", "COM1");
		speed = p.getInt("Port.speed", SerialPort.BAUDRATE_9600);
		Sound = p.getBoolean("Sound", true); 
		TerminalID = p.getInt("TerminalID", 100);
		telnetHost=p.get("TelnetHost","localhost");
		telnetPort=p.getInt("TelnetPort", 23);
		setTerminalID(TerminalID);
		graphicsVisible=p.getBoolean("GraphicsVisible",false);
	}

	public void dump ( PrintStream fs )
	{
		fs.println("Font size       \t= " + FontSize);
		fs.println("Sound           \t= " + Sound);
		fs.println("Terminal ID     \t= " + TerminalID);
		fs.println("AnswerBack      \t= '" + AnswerBack + "'");
		fs.println("Port            \t= '" + PortName + "'");
		fs.println("Speed           \t= " + speed + " Baud");
		fs.println("ENTER           \t= " + ENTER);
		fs.println("Telnet Host     \t= "+  telnetHost);
		fs.println("Telnet Port     \t= "+  telnetPort);
		fs.println("Graphics visible\t= "+ graphicsVisible);
	}
}
