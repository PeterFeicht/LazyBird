package at.jku.pci.lazybird.util;

import java.io.Serializable;
import java.util.Date;

/**
 * Represents a single log entry with a time and message.
 * 
 * @author Peter
 */
public class LogEntry implements Comparable<LogEntry>, Serializable
{
	private static final long serialVersionUID = -8913365085816311032L;
	
	/**
	 * The time of this log entry.
	 */
	public final Date mTime;
	
	/**
	 * The message associated with this log entry.
	 */
	public final String mMessage;
	
	/**
	 * Initializes a new instance of the {@link LogEntry} class with the specified message and
	 * the current time.
	 * 
	 * @param msg the log message.
	 * @exception NullPointerException if {@code msg} is {@code null}.
	 */
	public LogEntry(String msg)
	{
		this(new Date(), msg);
	}
	
	/**
	 * Initializes a new instance of the {@link LogEntry} class with the specified time and
	 * message.
	 * 
	 * @param time the time.
	 * @param msg the log message.
	 * @exception NullPointerException if {@code time} or {@code msg} is {@code null}.
	 */
	public LogEntry(Date time, String msg)
	{
		if(time == null || msg == null)
			throw new NullPointerException();
		mTime = time;
		mMessage = msg;
	}
	
	@Override
	public int compareTo(LogEntry another)
	{
		return mTime.compareTo(another.mTime);
	}
	
	@Override
	public int hashCode()
	{
		int result = 27;
		result = result * 13 + mTime.hashCode();
		return result * 13 + mMessage.hashCode();
	}
	
	@Override
	public boolean equals(Object o)
	{
		if(o == this)
			return true;
		if(o == null)
			return false;
		if(o instanceof LogEntry)
		{
			LogEntry other = (LogEntry)o;
			return mTime.equals(other.mTime) && mMessage.equals(other.mMessage);
		}
		return false;
	}
}
