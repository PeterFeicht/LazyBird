package at.jku.pci.lazybird;

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
	public final Date time;
	
	/**
	 * The message associated with this log entry.
	 */
	public final String message;
	
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
		this.time = time;
		this.message = msg;
	}
	
	@Override
	public int compareTo(LogEntry another)
	{
		return time.compareTo(another.time);
	}
	
	@Override
	public int hashCode()
	{
		int result = 27;
		result = result * 13 + time.hashCode();
		return result * 13 + message.hashCode();
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
			return time.equals(other.time) && message.equals(other.message);
		}
		return false;
	}
}
