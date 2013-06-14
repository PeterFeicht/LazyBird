package at.jku.pci.lazybird.features;

import weka.core.Instance;

/**
 * A wrapper for {@link Instance} that implements {@link Timestamped}. The first value is used for the
 * timestamp.
 * 
 * @author Peter
 */
public class TimeInstance extends Instance implements Timestamped
{
	private static final long serialVersionUID = 4762755102097775605L;
	
	/**
	 * Initializes a new instance of the {@link TimeInstance} class wit the specified number of values.
	 * 
	 * @param numValues the number of values, including the timestamp.
	 * @exception IllegalArgumentException if {@code numValues} is less than {@code 1}.
	 * @see Instance#Instance(int)
	 */
	public TimeInstance(int numValues)
	{
		super(numValues);
		
		if(numValues < 1)
			throw new IllegalArgumentException();
	}
	
	/**
	 * Initializes a new instance of the {@link TimeInstance} class wit the specified number of values and
	 * sets the timestamp to the specified value.
	 * 
	 * @param numValues the number of values, including the timestamp.
	 * @param timestamp a Linux time in milliseconds.
	 * @exception IllegalArgumentException if {@code numValues} is less than {@code 1}.
	 * @see Instance#Instance(int)
	 */
	public TimeInstance(int numValues, long timestamp)
	{
		this(numValues);
		setTime(timestamp);
	}
	
	@Override
	public long getTime()
	{
		return (long)value(0);
	}
	
	/**
	 * Sets the timestamp for this instance. That is, sets the first value of this instance.
	 * 
	 * @param time a Linux time in milliseconds.
	 * @see #getTime()
	 */
	public void setTime(long time)
	{
		setValue(0, time);
	}
}
