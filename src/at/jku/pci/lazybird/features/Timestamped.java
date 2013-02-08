package at.jku.pci.lazybird.features;

import java.util.Date;

/**
 * Defines a method to get a timestamp for an object.
 * 
 * @author Peter
 */
public interface Timestamped
{
	/**
	 * Gets the timestamp associated with this object.
	 * 
	 * @see Date
	 */
	public long getTime();
}
