package at.jku.pci.lazybird;

/**
 * Provides keys and default values for shared preferences that cannot be changed by the user.
 * 
 * @see SettingsActivity
 * @author Peter
 */
public final class Storage
{
	public static final String LOGTAG = "Storage";
	public static final boolean LOCAL_LOGV = true;
	
	public static final String PREFS_CLASSIFIER = "at.jku.pci.lazybird.PREFS_CLASSIFIER";
	
	/**
	 * Shared preference key: {@value}
	 * <p>
	 * The flags for all preferences the current classifier was trained with.
	 */
	public static final String KEY_FEATURES = "classifierFeatures";
	
	/**
	 * Shared preference key: {@value}
	 * <p>
	 * The file name for the serialized current classifier object.
	 */
	public static final String KEY_CLASSIFIER_FILE = "classifierFile";
	
	/**
	 * Shared preference key: {@value}
	 * <p>
	 * The file name for the data the current classifier was trained with.
	 */
	public static final String KEY_TRAINING_FILE = "classifierTrainingFile";
	
	/**
	 * Shared preference key: {@value}
	 * <p>
	 * The file name for the log file from training the current classifier.
	 */
	public static final String KEY_TRAINING_LOG_FILE = "classifierTrainingLogFile";
	
	/**
	 * Shared preference key: {@value}
	 * <p>
	 * The type of the current classifier.
	 */
	public static final String KEY_CLASSIFIER_TYPE = "classifierType";
	
	/**
	 * Shared preference key: {@value}
	 * <p>
	 * The window size for the sliding window used to train the current classifier.
	 */
	public static final String KEY_WINDOW_SIZE = "classifierWindowSize";
	
	private Storage()
	{
	}
}
