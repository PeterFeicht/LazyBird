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

public class SettingsActivity extends Activity
{
	public static final String LOGTAG = "SettingsActivity";
	public static final boolean LOCAL_LOGV = true;
	
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
	
	public static class SettingsFragment extends PreferenceFragment implements
			OnSharedPreferenceChangeListener
	{
		private ListPreference mValueUpdateSpeed;
		private EditTextPreference mOutputDir;
		private ListPreference mMaxNumValues;
		private NumberPreference mStartDelay;
		
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
			
			// What layout do non-custom preferences use?
			mStartDelay.setLayoutResource(mValueUpdateSpeed.getLayoutResource());
			
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
