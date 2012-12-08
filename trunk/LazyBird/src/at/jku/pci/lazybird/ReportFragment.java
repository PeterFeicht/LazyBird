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
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;
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
	public static final String EXTRA_TTS_ENABLE = "at.jku.pci.lazybird.TTS_ENABLE";
	public static final String EXTRA_LANGUAGE = "at.jku.pci.lazybird.LANGUAGE";
	public static final String EXTRA_LOG = "at.jku.pci.lazybird.LOG";
	public static final String EXTRA_LOG_ENABLE = "at.jku.pci.lazybird.LOG_ENABLE";
	public static final String EXTRA_FILENAME = "at.jku.pci.lazybird.LOG_FILENAME";
	public static final String EXTRA_DIRNAME = "at.jku.pci.lazybird.LOG_DIRNAME";
	public static final String EXTRA_REPORT = "at.jku.pci.lazybird.REPORT";
	public static final String EXTRA_REPORT_ENABLE = "at.jku.pci.lazybird.REPORT_ENABLE";
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
	private static int sWindowSize;
	private static int sJumpSize;
	private static String sReportServer;
	private static String sReportUser;
	private static boolean sWriteLog;
	private static String sLogFilename;
	
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
	private ClassifierService mService = null;
	private LocalBroadcastManager mBroadcastManager;
	private IntentFilter mServiceIntentFilter;
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
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
		mServiceIntentFilter.addAction(BCAST_NEW_ACTIVITY);
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mPrefsClassifier = Storage.getClassifierPreferences(getActivity());
		readSettings();
		
		getWidgets(getView());
		
		// if the service is running and we just got created, get working classifier.
		// setting of input enabled and such things are done in onResume.
		if(ClassifierService.isRunning())
		{
			mService = ClassifierService.getInstance();
			// double check for an actual instance, just to be sure
			if(mService != null)
			{
				mClassifier = mService.getClassifier();
				mLblNoClassifier.setVisibility(View.GONE);
			}
		}
		else
			checkForClassifier();
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
		mBroadcastManager.unregisterReceiver(mBroadcastReceiver);
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		
		// check for running service every time the fragment is resumed, since broadcast can't
		// be received while paused or stopped
		mSwClassifiy.setChecked(ClassifierService.isRunning());
		if(ClassifierService.isRunning())
			mBroadcastManager.registerReceiver(mBroadcastReceiver, mServiceIntentFilter);
		else
			mService = null;
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
		sWindowSize = mPrefsClassifier.getInt(Storage.KEY_WINDOW_SIZE, 1000);
		sJumpSize = 100;
		sReportServer = mPrefs.getString(SettingsActivity.KEY_REPORT_SERVER, "");
		sReportUser = mPrefs.getString(SettingsActivity.KEY_REPORT_USER, "");
		sWriteLog = mPrefs.getBoolean(SettingsActivity.KEY_WRITE_LOG, false);
		sLogFilename = mPrefs.getString(SettingsActivity.KEY_LOG_FILENAME, "");
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
			if(isChecked)
				startService();
			else
				stopService();
		}
	};
	
	private OnCheckedChangeListener onChkTtsCheckedChange = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			if(ClassifierService.isRunning() && mService != null)
				mService.setTextToSpeech(isChecked);
		}
	};
	
	private OnCheckedChangeListener onChkReportCheckedChange = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		{
			if(ClassifierService.isRunning() && mService != null)
				mService.setReportToServer(isChecked);
		}
	};
	
	private void onNewActivity(Intent intent)
	{
		// TODO Auto-generated method stub
		
	}
	
	private void onServiceStarted()
	{
		mService = ClassifierService.getInstance();
		mSwClassifiy.setChecked(true);
		// TODO log
	}
	
	private void onServiceStopped()
	{
		mBroadcastManager.unregisterReceiver(mBroadcastReceiver);
		mSwClassifiy.setChecked(false);
		// TODO log
	}
	
	public void startService()
	{
		readSettings();
		Intent i = new Intent(ClassifierService.CLASSIFIER_SERVICE);
		i.putExtra(EXTRA_CLASSIFIER, mClassifier);
		i.putExtra(EXTRA_WINDOW, sWindowSize);
		i.putExtra(EXTRA_JUMP, sJumpSize);
		i.putExtra(EXTRA_FEATURES, sTrainedFeatures);
		
		i.putExtra(EXTRA_TTS, false);
		i.putExtra(EXTRA_TTS_ENABLE, mChkTts.isChecked());
		// Language
		
		i.putExtra(EXTRA_LOG, true);
		i.putExtra(EXTRA_LOG_ENABLE, sWriteLog);
		i.putExtra(EXTRA_FILENAME, sLogFilename);
		i.putExtra(EXTRA_DIRNAME, sOutputDir);
		
		i.putExtra(EXTRA_REPORT, true);
		i.putExtra(EXTRA_REPORT_ENABLE, mChkReport.isChecked());
		i.putExtra(EXTRA_REPORT_SERVER, sReportServer);
		i.putExtra(EXTRA_REPORT_USER, sReportUser);
		
		getActivity().startService(i);
		mBroadcastManager.registerReceiver(mBroadcastReceiver, mServiceIntentFilter);
	}
	
	public void stopService()
	{
		getActivity().stopService(new Intent(ClassifierService.CLASSIFIER_SERVICE));
	}
}
