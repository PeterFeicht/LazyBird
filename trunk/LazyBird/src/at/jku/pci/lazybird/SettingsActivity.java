package at.jku.pci.lazybird;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.MenuItem;

/**
 * All settings the user can change, for other stored values see {@link Storage}.
 * 
 * @author Peter
 */
public class SettingsActivity extends Activity
{
	/**
	 * Shared preferences key: {@value}
	 * <p>
	 * The update delay of the last values during recording.
	 */
	public static final String KEY_VALUE_UPDATE_SPEED = "valueUpdateSpeed";
	
	/**
	 * Shared preferences key: {@value}
	 * <p>
	 * The directory to write the recorded files to.
	 */
	public static final String KEY_OUTPUT_DIR = "outputDir";
	
	/**
	 * Shared preferences key: {@value}
	 * <p>
	 * The number of data points after which recording is stopped.
	 */
	public static final String KEY_MAX_NUM_VALUES = "maxNumValues";
	
	/**
	 * Shared preferences key: {@value}
	 * <p>
	 * The time to wait in seconds before starting the recording.
	 */
	public static final String KEY_START_DELAY = "startDelay";
	
	/**
	 * Shared preferences key: {@value}
	 * <p>
	 * The number of folds to perform cross validation with.
	 */
	public static final String KEY_NUM_FOLDS = "numFolds";
	
	public static final String LOGTAG = "SettingsActivity";
	public static final boolean LOCAL_LOGV = true;
	
	public static class SettingsFragment extends PreferenceFragment implements
			OnSharedPreferenceChangeListener
	{
		private ListPreference mValueUpdateSpeed;
		private EditTextPreference mOutputDir;
		private ListPreference mMaxNumValues;
		private NumberPreference mStartDelay;
		private NumberPreference mNumFolds;
		
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "SettingsFragment created.");
			
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences);
			
			mValueUpdateSpeed = (ListPreference)findPreference(KEY_VALUE_UPDATE_SPEED);
			mOutputDir = (EditTextPreference)findPreference(KEY_OUTPUT_DIR);
			mMaxNumValues = (ListPreference)findPreference(KEY_MAX_NUM_VALUES);
			mStartDelay = (NumberPreference)findPreference(KEY_START_DELAY);
			mNumFolds = (NumberPreference)findPreference(KEY_NUM_FOLDS);
			
			// What layout do non-custom preferences use?
			final int layout = mValueUpdateSpeed.getLayoutResource();
			mStartDelay.setLayoutResource(layout);
			mNumFolds.setLayoutResource(layout);
			
			final SharedPreferences p = getPreferenceScreen().getSharedPreferences();
			mOutputDir.setSummary(p.getString(KEY_OUTPUT_DIR, ""));
		}
		
		@Override
		public void onPause()
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "SettingsFragment paused.");
			
			super.onPause();
			getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
		}
		
		@Override
		public void onResume()
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "SettingsFragment resumed.");
			
			super.onResume();
			getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
		}
		
		public void onSharedPreferenceChanged(SharedPreferences p, String key)
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "SharedPreference changed: " + key);
			
			if(key.equals(KEY_VALUE_UPDATE_SPEED))
				mValueUpdateSpeed.setSummary(mValueUpdateSpeed.getEntry());
			else if(key.equals(KEY_OUTPUT_DIR))
				mOutputDir.setSummary(p.getString(KEY_OUTPUT_DIR, ""));
			else if(key.equals(KEY_MAX_NUM_VALUES))
				mMaxNumValues.setSummary(mMaxNumValues.getEntry());
			else if(key.equals(KEY_START_DELAY))
				mStartDelay.setSummary(Integer.toString(p.getInt(KEY_START_DELAY, 0)));
			else if(key.equals(KEY_NUM_FOLDS))
				mNumFolds.setSummary(Integer.toString(p.getInt(KEY_NUM_FOLDS, 4)));
		}
	}
	
	SettingsFragment mSettings;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		if(LOCAL_LOGV) Log.v(LOGTAG, "SettingsActivity created.");
		
		super.onCreate(savedInstanceState);
		if(getActionBar() != null)
			getActionBar().setDisplayHomeAsUpEnabled(true);
		
		mSettings = new SettingsFragment();
		
		getFragmentManager().beginTransaction()
			.replace(android.R.id.content, mSettings)
			.commit();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if(item.getItemId() == android.R.id.home)
			super.onBackPressed();
		return super.onOptionsItemSelected(item);
	}
}
