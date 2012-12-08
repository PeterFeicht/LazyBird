package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import weka.classifiers.Classifier;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;

public class ReportFragment extends Fragment
{
	// Extras
	public static final String EXTRA_CLASSIFIER = "at.jku.pci.lazybird.CLASSIFIER";
	public static final String EXTRA_WINDOW = "at.jku.pci.lazybird.WINDOW";
	public static final String EXTRA_JUMP = "at.jku.pci.lazybird.JUMP";
	public static final String EXTRA_FEATURES = "at.jku.pci.lazybird.FEATURES";
	public static final String EXTRA_TTS = "at.jku.pci.lazybird.TTS";
	public static final String EXTRA_LANGUAGE = "at.jku.pci.lazybird.LANGUAGE";
	public static final String EXTRA_WRITE_LOG = "at.jku.pci.lazybird.WRITE_LOG";
	public static final String EXTRA_FILENAME = "at.jku.pci.lazybird.LOG_FILENAME";
	public static final String EXTRA_DIRNAME = "at.jku.pci.lazybird.LOG_DIRNAME";
	public static final String EXTRA_REPORT = "at.jku.pci.lazybird.REPORT";
	public static final String EXTRA_REPORT_SERVER = "at.jku.pci.lazybird.REPORT_SERVER";
	public static final String EXTRA_REPORT_USER = "at.jku.pci.lazybird.REPORT_USER";
	// Intents
	public static final String BCAST_SERVICE_STOPPED = "at.jku.pci.lazybird.REP_SERVICE_STOPPED";
	public static final String BCAST_SERVICE_STARTED = "at.jku.pci.lazybird.REP_SERVICE_STARTED";
	public static final String BCAST_NEW_ACTIVITY = "at.jku.pci.lazybird.REP_NEW_ACTIVITY";
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
	TextView mLblNoClassifier;
	Switch mSwClassifiy;
	CheckBox mChkTts;
	CheckBox mChkReport;
	ListView mListLog;
	
	private Classifier mClassifier = null;
	// Handlers
	// private ClassifierService mService = null;
	private LocalBroadcastManager mBroadcastManager;
	private IntentFilter mServiceIntentFilter;
	private BroadcastReceiver mServiceReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent)
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "Received broadcast: " + intent);
			
			if(intent.getAction().equals(BCAST_SERVICE_STOPPED))
				onServiceStopped();
			else if(intent.getAction().equals(BCAST_SERVICE_STARTED))
				onServiceStarted();
			else if(intent.getAction().equals(BCAST_NEW_ACTIVITY))
				onNewActivity(intent);
		}
	};
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		Bundle savedInstanceState)
	{
		// Just return the inflated layout, other initializations will be done when the host
		// activity is created
		return inflater.inflate(R.layout.fragment_report, container, false);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		
		mBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
		mServiceIntentFilter = new IntentFilter();
		mServiceIntentFilter.addAction(BCAST_SERVICE_STARTED);
		mServiceIntentFilter.addAction(BCAST_SERVICE_STOPPED);
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mPrefsClassifier = Storage.getClassifierPreferences(getActivity());
		readSettings();
		
		getWidgets(getView());
		checkForClassifier();
		
		// if the service is running and we just got created, fill inputs with running data.
		// setting of input enabled and such things are done in onResume.
		// TODO get classifier from service
	}
	
	/**
	 * Sets the fields for the views of this Fragment and registers listeners and stuff.
	 * 
	 * @param v the View for this Fragment
	 */
	private void getWidgets(View v)
	{
		mLblNoClassifier = (TextView)v.findViewById(R.id.lblNoClassifier);
		
		mSwClassifiy = (Switch)v.findViewById(R.id.swClassify);
		mSwClassifiy.setOnCheckedChangeListener(onSwClassifyCheckedChange);
		
		mChkTts = (CheckBox)v.findViewById(R.id.chkTts);
		mChkTts.setOnCheckedChangeListener(onChkTtsCheckedChange);
		mChkReport = (CheckBox)v.findViewById(R.id.chkReport);
		mChkReport.setOnCheckedChangeListener(onChkReportCheckedChange);
		
		mListLog = (ListView)v.findViewById(R.id.listLog);
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		mBroadcastManager.unregisterReceiver(mServiceReceiver);
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		
		// check for running service every time the fragment is resumed, since broadcast can't
		// be received while paused or stopped
		//mSwClassifiy.setChecked(ClassifierService.isRunning());
		mBroadcastManager.registerReceiver(mServiceReceiver, mServiceIntentFilter);
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
	
	private void setClassifierPresent(boolean present)
	{
		mLblNoClassifier.setVisibility(present ? View.GONE : View.VISIBLE);
		mSwClassifiy.setEnabled(present);
		if(!present)
			mClassifier = null;
	}
	
	private void checkForClassifier()
	{
		readSettings();
		boolean fileSet = (sClassifierFile != null && !sClassifierFile.isEmpty());
		mSwClassifiy.setEnabled(fileSet);
		
		if(fileSet)
		{
			try
			{
				InputStream is = getActivity().openFileInput(sClassifierFile);
				ObjectInputStream ois = new ObjectInputStream(is);
				mClassifier = (Classifier)ois.readObject();
				ois.close();
				setClassifierPresent(true);
			}
			catch(FileNotFoundException ex)
			{
				Log.w(LOGTAG, "Classifier file from preference not found.", ex);
				sClassifierFile = "";
				mPrefsClassifier.edit().putString(Storage.KEY_CLASSIFIER_FILE, "").apply();
				setClassifierPresent(false);
			}
			catch(StreamCorruptedException ex)
			{
				Log.e(LOGTAG, "Serialized classifier is corrupted.", ex);
				sClassifierFile = "";
				mPrefsClassifier.edit().putString(Storage.KEY_CLASSIFIER_FILE, "").apply();
				setClassifierPresent(false);
			}
			catch(IOException ex)
			{
				Toast.makeText(getActivity(), R.string.error_io, Toast.LENGTH_LONG).show();
				Log.e(LOGTAG, "IOException while reading serialized classifier.", ex);
				setClassifierPresent(false);
			}
			catch(ClassNotFoundException ex)
			{
				Log.wtf(LOGTAG, "Class of the serialized classifier not found??", ex);
				throw new InternalError();
			}
		}
		else
		{
			setClassifierPresent(false);
		}
	}
	
	private OnCheckedChangeListener onSwClassifyCheckedChange = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			// TODO Auto-generated method stub
		}
	};
	
	private OnCheckedChangeListener onChkTtsCheckedChange = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			// TODO Auto-generated method stub
			
		}
	};
	
	private OnCheckedChangeListener onChkReportCheckedChange = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			// TODO Auto-generated method stub
			
		}
	};
	
	private void onNewActivity(Intent intent)
	{
		// TODO Auto-generated method stub
		
	}
	
	private void onServiceStarted()
	{
		// TODO Auto-generated method stub
		
	}
	
	private void onServiceStopped()
	{
		// TODO Auto-generated method stub
		
	}
}
