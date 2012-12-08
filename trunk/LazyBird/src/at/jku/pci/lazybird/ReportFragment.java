package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import weka.classifiers.Classifier;

public class ReportFragment extends Fragment
{
	// Intents
	public static final String BCAST_SERVICE_STOPPED = "at.jku.pci.lazybird.REP_SERVICE_STOPPED";
	public static final String BCAST_SERVICE_STARTED = "at.jku.pci.lazybird.REP_SERVICE_STARTED";
	// Constants
	public static final String LOGTAG = "ReportFragment";
	public static final boolean LOCAL_LOGV = true;
	/**
	 * Standard extension for log files.
	 * <p> {@value}
	 */
	public static final String EXTENSION = ".log";
	/**
	 * Gets the default title associated with this fragment for use in an {@link ActionBar} tab.
	 * 
	 * @see #getTitle()
	 */
	public static final CharSequence TITLE = "Report";
	// Settings
	/**
	 * TODO Setting: {@link SettingsActivity#KEY_VALUE_UPDATE_SPEED}
	 */
	private static long sValueUpdateDelay;
	/**
	 * Output directory for log files, the base directory is always
	 * {@link Environment#getExternalStorageDirectory()}.
	 * <p>
	 * Setting: {@link SettingsActivity#KEY_OUTPUT_DIR}
	 * 
	 * @see ARFFRecorderService#getDirname()
	 */
	private static String sOutputDir;
	private static String sClassifierFile;
	private static int sTrainedFeatures;
	
	private SharedPreferences mPrefs;
	private SharedPreferences mPrefsClassifier;
	
	// Fields
	Switch mSwClassifiy;
	CheckBox mChkTts;
	CheckBox mChkReport;
	TextView mTxtLog;
	
	private Classifier mClassifier = null;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		Bundle savedInstanceState)
	{
		// Just return the inflated layout, other initializations will be done when the host
		// activity is created
		return inflater.inflate(R.layout.fragment_report, container, false);
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mPrefsClassifier = Storage.getClassifierPreferences(getActivity());
		readSettings();
		
		getWidgets(getView());
	}
	
	/**
	 * Sets the fields for the views of this Fragment and registers listeners and stuff.
	 * 
	 * @param v the View for this Fragment
	 */
	private void getWidgets(View v)
	{
		// TODO disable everything and enable in code
		mSwClassifiy = (Switch)v.findViewById(R.id.swClassify);
		mSwClassifiy.setOnCheckedChangeListener(onSwClassifyCheckedChange);
		
		mChkTts = (CheckBox)v.findViewById(R.id.chkTts);
		mChkTts.setOnCheckedChangeListener(onChkTtsCheckedChange);
		mChkReport = (CheckBox)v.findViewById(R.id.chkReport);
		mChkReport.setOnCheckedChangeListener(onChkReportCheckedChange);
		
		mTxtLog = (TextView)v.findViewById(R.id.txtLog);
	}
	
	/**
	 * Gets the title associated with this fragment for use in an {@link ActionBar} tab.
	 * 
	 * @return the title of this fragment.
	 */
	public CharSequence getTitle()
	{
		if(getActivity() != null)
			return getString(R.string.title_tab_report);
		else
			return TITLE;
	}
	
	/**
	 * Sets all appropriate private fields from the shared preferences.
	 */
	private void readSettings()
	{
		sOutputDir = mPrefs.getString(SettingsActivity.KEY_OUTPUT_DIR, "");
		sClassifierFile = mPrefsClassifier.getString(Storage.KEY_CLASSIFIER_FILE, "");
		sTrainedFeatures = mPrefsClassifier.getInt(Storage.KEY_FEATURES, 0);
	}
	
	private OnCheckedChangeListener onSwClassifyCheckedChange =
		new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				// TODO Auto-generated method stub
				
			}
		};
	
	private OnCheckedChangeListener onChkTtsCheckedChange =
		new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				// TODO Auto-generated method stub
				
			}
		};
	
	private OnCheckedChangeListener onChkReportCheckedChange =
		new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				// TODO Auto-generated method stub
				
			}
		};
}
