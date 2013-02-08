package at.jku.pci.lazybird.features;

import java.util.HashMap;
import at.jku.pervasive.sd12.actclient.ClassLabel;
import at.jku.pervasive.sd12.actclient.CoordinatorClient.UserState;

/**
 * Represents a set of users and their activities for a certain point in time. This is a wrapper
 * for {@link HashMap}{@code <}{@link String}{@code ,}{@link ClassLabel}{@code >} that implements
 * {@link Timestamped}.
 * 
 * @author Peter
 * @see UserState
 */
public class UserActivities extends HashMap<String, ClassLabel> implements Timestamped
{
	private static final long serialVersionUID = 3115085448554166870L;
	private final long mTime;
	
	/**
	 * Initializes a new instance of the {@link UserActivities} class for the specified time.
	 * 
	 * @param time a Linux time in milliseconds.
	 */
	public UserActivities(long time)
	{
		mTime = time;
	}
	
	/**
	 * Initializes a new instance of the {@link UserActivities} class for the specified time with
	 * the specified capacity.
	 * 
	 * @param time a Linux time in milliseconds.
	 * @param capacity the initial capacity of this hash map.
	 */
	public UserActivities(long time, int capacity)
	{
		super(capacity);
		mTime = time;
	}
	
	@Override
	public long getTime()
	{
		return mTime;
	}
}
