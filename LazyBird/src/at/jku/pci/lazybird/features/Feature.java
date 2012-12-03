package at.jku.pci.lazybird.features;

/**
 * Enum with available features for extraction from ARFF Files by {@link TODO}.
 * <p>
 * Note that these only work if the file has a timestamp and exactly three numeric values for the
 * axes. The optional class attribute and the timestamp are copied without change.<br>
 * Also, a sliding window is always applied to the data point values (the data is always a mean).
 * 
 * @author Peter
 */
public enum Feature
{
	/**
	 * Don't extract features, use raw data points. This can be used when features have already
	 * been extracted and only the classifier should be trained.
	 * <p>
	 * When using this, it should be the only feature and the source ARFF file needs to have
	 * appropriate attribute names corresponding to the names of the features.
	 * 
	 * @see #getAttribute()
	 */
	RAW("Raw", null, 0),
	
	/**
	 * The mean of the X-axis.
	 */
	X("X Mean", "x-mean", 0x01),
	
	/**
	 * The mean of the Y-axis.
	 */
	Y("Y Mean", "y-mean", 0x02),
	
	/**
	 * The mean of the Z-axis.
	 */
	Z("Z Mean", "z-mean", 0x04),
	
	/**
	 * The mean of the absolute values of all three axes' means, that is the magnitude.
	 */
	ABS_MEAN("Magnitude", "mag", 0x08),
	
	/**
	 * The variance of all inputs separately.
	 */
	VARIANCE("Variance", "var", 0x10),
	
	/**
	 * The variance of the X-axis' mean.
	 */
	VARIANCE_X("Variance X", "x-var", 0x20),
	
	/**
	 * The variance of the Y-axis' mean.
	 */
	VARIANCE_Y("Variance Y", "y-var", 0x40),
	
	/**
	 * The variance of the Z-axis' mean.
	 */
	VARIANCE_Z("Variance Z", "z-var", 0x80),
	
	/**
	 * The variance of the magnitude of all three axes. That is, {@link #ABS_MEAN} and
	 * {@link #VARIANCE} chained.
	 */
	VARIANCE_OF_MAGNITUDE("Variance of the Magnitude", "varmag", 0x0100);
	
	private final String mName;
	private final String mAttribute;
	private int mBit;
	
	private Feature(String name, String attribute, int bit)
	{
		mName = name;
		mAttribute = attribute;
		mBit = bit;
	}
	
	/**
	 * Gets the name of this feature to be presented to the user.
	 */
	public String getName()
	{
		return mName;
	}
	
	/**
	 * Gets the attribute name of this feature to be used in the resulting ARFF file.
	 * <p>
	 * This is also the attribute name used to determine which features should be used when
	 * training from a raw ARFF file and when classifying instances to report.<br>
	 * Note that the attribute value of {@link #RAW} is {@code null}, all others are not
	 * {@code null}.
	 */
	public String getAttribute()
	{
		return mAttribute;
	}
	
	/**
	 * Gets the bit of this feature, used in a bit mask to combine multiple features together.
	 */
	public int getBit()
	{
		return mBit;
	}
	
	@Override
	public String toString()
	{
		return mName;
	}
}
