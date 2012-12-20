package at.jku.pci.lazybird;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import weka.classifiers.Classifier;
import weka.core.Instances;

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
	
	/**
	 * Name for the classifier preferences: {@value}
	 */
	public static final String PREFS_CLASSIFIER = "at.jku.pci.lazybird.PREFS_CLASSIFIER";
	
	/**
	 * Shared preference key: {@code int} {@value}
	 * <p>
	 * The flags for all features the current classifier was trained with.
	 */
	public static final String KEY_FEATURES = "classifierFeatures";
	
	/**
	 * Shared preference key: {@code String} {@value}
	 * <p>
	 * Filename of the serialized {@link Classifier} in the internal storage.
	 */
	public static final String KEY_CLASSIFIER_FILE = "classifierFile";
	
	/**
	 * Shared preference key: {@code String} {@value}
	 * <p>
	 * The filename for the serialized {@link Instances} the current classifier was trained with.
	 */
	public static final String KEY_TRAINING_FILE = "classifierTrainingFile";
	
	/**
	 * Shared preference key: {@code String} {@value}
	 * <p>
	 * The number of instances the current classifier was trained with, to avoid deserializing
	 * the whole {@code Instances} just to get the count.
	 */
	public static final String KEY_TRAIN_INSTANCES = "numTrainInstances";
	
	/**
	 * Shared preference key: {@code String} {@value}
	 * <p>
	 * The file name for the log file from validating the current classifier.
	 */
	public static final String KEY_VALIDATION_LOG_FILE = "classifierValidationLogFile";
	
	/**
	 * Shared preference key: {@code String} {@value}
	 * <p>
	 * The type of the current classifier, stored to avoid deserialization when the classifier is
	 * not actually needed.
	 */
	public static final String KEY_CLASSIFIER_TYPE = "classifierType";
	
	/**
	 * Shared preference key: {@code int} {@value}
	 * <p>
	 * The window size for the sliding window used to train the current classifier.
	 */
	public static final String KEY_WINDOW_SIZE = "classifierWindowSize";
	
	/**
	 * Gets the {@link SharedPreferences} for the classifier preferences.<br>
	 * The key is {@link #PREFS_CLASSIFIER}.
	 * 
	 * @param c the {@link Context} to get preferences from.
	 * @return the {@code SharedPreferences} for classifier related settings.
	 */
	public static SharedPreferences getClassifierPreferences(Context c)
	{
		return c.getSharedPreferences(PREFS_CLASSIFIER, Activity.MODE_PRIVATE);
	}
	
	/* ======================================================================================= */
	
	/**
	 * Name for the UI preferences: {@value}
	 */
	public static final String PREFS_UI = "at.jku.pci.lazybird.PREFS_UI";
	
	/**
	 * Shared preference key: {@code boolean} {@value}
	 * <p>
	 * Whether chkTts in ReportFragment was checked.
	 */
	public static final String KEY_CHK_TTS_CHECKED = "chkTts_checked";
	
	/**
	 * Shared preference key: {@code boolean} {@value}
	 * <p>
	 * Whether chkReport in ReportFragment was checked.
	 */
	public static final String KEY_CHK_REPORT_CHECKED = "chkReport_checked";
	
	/**
	 * Gets the {@link SharedPreferences} for the UI preferences.<br>
	 * The key is {@link #PREFS_UI}.
	 * 
	 * @param c the {@link Context} to get preferences from.
	 * @return the {@code SharedPreferences} for UI related settings.
	 */
	public static SharedPreferences getUiPreferences(Context c)
	{
		return c.getSharedPreferences(PREFS_UI, Activity.MODE_PRIVATE);
	}
	
	private Storage()
	{
	}
}
