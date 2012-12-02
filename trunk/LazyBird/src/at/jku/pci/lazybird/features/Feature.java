package at.jku.pci.lazybird.features;

/**
 * Enum with available features for extraction from ARFF Files by {@link TODO}.
 * <p>
 * Note that these only work if the file has a timestamp and exactly three numeric values for the
 * axes. The optional class attribute and timestamp are copied without change.<br>
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
	RAW("Raw", null),
	
	/**
	 * The mean of the X-axis.
	 */
	X("X Mean", "x-mean"),
	
	/**
	 * The mean of the Y-axis.
	 */
	Y("Y Mean", "y-mean"),
	
	/**
	 * The mean of the Z-axis.
	 */
	Z("Z Mean", "z-mean"),
	
	/**
	 * The mean of the absolute values of all three axes' means, that is the magnitude.
	 */
	ABS_MEAN("Magnitude", "mag"),
	
	/**
	 * The variance of all inputs separately.
	 */
	VARIANCE("Variance", "var"),
	
	/**
	 * The variance of the X-axis' mean.
	 */
	VARIANCE_X("Variance X", "x-var"),
	
	/**
	 * The variance of the Y-axis' mean.
	 */
	VARIANCE_Y("Variance Y", "y-var"),
	
	/**
	 * The variance of the Z-axis' mean.
	 */
	VARIANCE_Z("Variance Z", "z-var"),
	
	/**
	 * The variance of the magnitude of all three axes. That is, {@link #ABS_MEAN} and
	 * {@link #VARIANCE} chained.
	 */
	VARIANCE_OF_MAGNITUDE("Variance of the Magnitude", "varmag");
	
	private final String name;
	private final String attribute;
	
	private Feature(String name, String attribute)
	{
		this.name = name;
		this.attribute = attribute;
	}
	
	/**
	 * Gets the name of this feature to be presented to the user.
	 */
	public String getName()
	{
		return name;
	}
	
	/**
	 * Gets the attribute name of this feature to be used in the resulting ARFF file.
	 * <p>
	 * This is also the attribute name used to determine which features should be used when
	 * training from a raw ARFF file and when classifying instances to report.
	 */
	public String getAttribute()
	{
		return attribute;
	}
	
	@Override
	public String toString()
	{
		return name;
	}
}
