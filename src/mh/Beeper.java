package mh;

import java.io.IOException;
import java.net.URL;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class Beeper
{
	private AudioFormat m_audioFormat;
	private byte m_buffer[];
	private Clip m_Clip;
	private long msInterval;

	public Beeper(String fileName)
	{
		// preload sample
		m_Clip = null;
		
		try
		{
			URL url = getClass().getResource("rsc/" + fileName);

			AudioInputStream is = AudioSystem.getAudioInputStream(url);
			/*
			 * AudioInputStream is = AudioSystem.getAudioInputStream(new File(
			 * fileName));
			 */
			m_audioFormat = is.getFormat();
			int n = is.available();
			m_buffer = new byte[n];
			is.read(m_buffer);
			is.close();

			m_Clip = AudioSystem.getClip();
			m_Clip.open(m_audioFormat, m_buffer, 0, m_buffer.length);
			msInterval = m_Clip.getMicrosecondLength() / 2000L;
		}
		catch (UnsupportedAudioFileException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (LineUnavailableException e)
		{
			System.err
					.println("*** Error: Cannot access audio output device - no beep possible.");
		}

	}

	protected void close ()
	{
		if (m_Clip.isOpen())
			m_Clip.close();
	}

	/**
	 * 
	 * @param fileName
	 *            the file name with the sound data. Must be either fully
	 *            qualified or the file must reside in the current directory.
	 */
	public void beep ()
	{
		if (m_Clip != null)
		{
			try
			{
				if (m_Clip.isRunning())
					return;

				m_Clip.start();

				// wait for startup of sound
				do
				{
					Thread.sleep(5);
				}
				while (!m_Clip.isRunning());

				// wait until finished
				do
				{
					Thread.sleep(msInterval);
				}
				while (m_Clip.isRunning());

				// rewind for next call
				m_Clip.setFramePosition(0);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}
}
