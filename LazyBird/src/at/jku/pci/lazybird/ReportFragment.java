package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import at.jku.pci.lazybird.util.LogListAdapter;
import at.jku.pci.lazybird.util.Storage;
import weka.classifiers.Classifier;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;

public class ReportFragment extends AbstractTabFragment
{
	// Extras
	public static final String EXTRA_CLASSIFIER = "at.jku.pci.lazybird.CLASSIFIER";
	public static final String EXTRA_WINDOW = "at.jku.pci.lazybird.WINDOW";
	public static final String EXTRA_JUMP = "at.jku.pci.lazybird.JUMP";
	public static final String EXTRA_FEATURES = "at.jku.pci.lazybird.FEATURES";
	public static final String EXTRA_TTS = "at.jku.pci.lazybird.TTS";
	public static final String EXTRA_TTS_ENABLE = "at.jku.pci.lazybird.TTS_ENABLE";
	public static final String EXTRA_LOG = "at.jku.pci.lazybird.LOG";
	public static final String EXTRA_LOG_ENABLE = "at.jku.pci.lazybird.LOG_ENABLE";
	public static final String EXTRA_FILENAME = "at.jku.pci.lazybird.LOG_FILENAME";
	public static final String EXTRA_DIRNAME = "at.jku.pci.lazybird.LOG_DIRNAME";
	public static final String EXTRA_REPORT = "at.jku.pci.lazybird.REPORT";
	public static final String EXTRA_REPORT_ENABLE = "at.jku.pci.lazybird.REPORT_ENABLE";
	public static final String EXTRA_REPORT_SERVER = "at.jku.pci.lazybird.REPORT_SERVER";
	public static final String EXTRA_REPORT_USER = "at.jku.pci.lazybird.REPORT_USER";
	public static final String EXTRA_WAKELOCK = "at.jku.pci.lazybird.WAKELOCK";
	// Intents
	public static final String BCAST_SERVICE_STOPPED = "at.jku.pci.lazybird.REP_SERVICE_STOPPED";
	public static final String BCAST_SERVICE_STARTED = "at.jku.pci.lazybird.REP_SERVICE_STARTED";
	public static final String BCAST_NEW_ACTIVITY = "at.jku.pci.lazybird.REP_NEW_ACTIVITY";
	public static final String BCAST_NEW_CLASSIFIER = "at.jku.pci.lazybird.NEW_CLASSIFIER";
	// Constants
	static final String LOGTAG = "ReportFragment";
	static final boolean LOCAL_LOGV = true;
	public static final String STATE_CLASSIFIER = "at.jku.pci.lazybird.STATE_CLASSIFIER";
	public static final String STATE_LOG = "at.jku.pci.lazybird.STATE_LOG";
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
	 * Setting: {@link SettingsActivity#KEY_OUTPUT_DIR}
	 * 
	 * @see ARFFRecorderService#getDirname()
	 */
	private static String sOutputDir = "";
	/**
	 * Setting: {@link Storage#KEY_CLASSIFIER_FILE}
	 */
	private static String sClassifierFile = "";
	/**
	 * Setting: {@link Storage#KEY_FEATURES}
	 */
	private static int sTrainedFeatures;
	/**
	 * Setting {@link Storage#KEY_WINDOW_SIZE}
	 */
	private static int sWindowSize;
	/**
	 * Setting that's not actually a setting, see {@link TrainFragment#JUMP_SIZE}
	 */
	private static int sJumpSize;
	/**
	 * Setting: {@link SettingsActivity#KEY_REPORT_SERVER}
	 */
	private static String sReportServer = "";
	/**
	 * Setting: {@link SettingsActivity#KEY_REPORT_USER}
	 */
	private static String sReportUser = "";
	/**
	 * Setting: {@link SettingsActivity#KEY_WRITE_LOG}
	 */
	private static boolean sWriteLog;
	/**
	 * Setting: {@link SettingsActivity#KEY_LOG_FILENAME}
	 */
	private static String sLogFilename = "";
	/**
	 * Setting: {@link SettingsActivity#KEY_USE_WAKELOCK}
	 */
	private static boolean sWakelock;
	
	private SharedPreferences mPrefs;
	private SharedPreferences mPrefsClassifier;
	
	// Views
	private TextView mLblNoClassifier;
	private Switch mSwClassifiy;
	private ProgressBar mProgressSerialize;
	private CheckBox mChkTts;
	private CheckBox mChkReport;
	private ListView mListLog;
	
	// Fields
	private Classifier mClassifier = null;
	private LogListAdapter mLogAdapter;
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
			else if(intent.getAction().equals(BCAST_NEW_CLASSIFIER))
				checkForClassifier();
		}
	};
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		Bundle savedInstanceState)
	{
		if(LOCAL_LOGV) Log.v(LOGTAG, "View created.");
		// Just return the inflated layout, other initializations will be done when the host
		// activity is created
		return inflater.inflate(R.layout.fragment_report, container, false);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		if(LOCAL_LOGV) Log.v(LOGTAG, "Activity created.");
		
		mBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
		mServiceIntentFilter = new IntentFilter();
		mServiceIntentFilter.addAction(BCAST_SERVICE_STARTED);
		mServiceIntentFilter.addAction(BCAST_SERVICE_STOPPED);
		mServiceIntentFilter.addAction(BCAST_NEW_ACTIVITY);
		mServiceIntentFilter.addAction(BCAST_NEW_CLASSIFIER);
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mPrefsClassifier = Storage.getClassifierPreferences(getActivity());
		readSettings();
		
		getWidgets(getView());
		
		// If the service is running and we just got created, get working classifier.
		// Setting of input enabled and such things are done in onResume.
		if(ClassifierService.isRunning())
		{
			mService = ClassifierService.getInstance();
			// double check for an actual instance, just to be sure
			if(mService != null)
			{
				mClassifier = mService.getClassifier();
				setClassifierPresent(true);
				mChkReport.setChecked(mService.getReportToServer());
				mChkTts.setChecked(mService.getTextToSpeech());
			}
			// Restore the log if the service is running
			if(savedInstanceState != null)
			{
				final LogListAdapter log =
					(LogListAdapter)savedInstanceState.getSerializable(STATE_LOG);
				if(log != null)
				{
					// The context can't be serialized so we need to set it to the new one
					log.setContext(getActivity());
					mLogAdapter = log;
					mListLog.setAdapter(log);
				}
			}
		}
		else if(savedInstanceState != null)
		{
			// Restore the classifier if the service isn't running
			mClassifier = (Classifier)savedInstanceState.getSerializable(STATE_CLASSIFIER);
			if(mClassifier == null)
				checkForClassifier();
			else
				setClassifierPresent(true);
		}
		else
		{
			// When freshly starting the app, restore previous selection of output methods
			final SharedPreferences uiPrefs = Storage.getUiPreferences(getActivity());
			mChkReport.setChecked(uiPrefs.getBoolean(Storage.KEY_CHK_REPORT_CHECKED, false));
			mChkTts.setChecked(uiPrefs.getBoolean(Storage.KEY_CHK_TTS_CHECKED, false));
			checkForClassifier();
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		// Save the classifier to avoid deserializing it when the screen rotates, doesn't seem to
		// do any good though
		outState.putSerializable(STATE_CLASSIFIER, mClassifier);
		// Save the log, will be restored in case the service is running
		outState.putSerializable(STATE_LOG, mLogAdapter);
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
		mSwClassifiy.setEnabled(false);
		mProgressSerialize = (ProgressBar)v.findViewById(R.id.progressSerialize);
		
		mChkTts = (CheckBox)v.findViewById(R.id.chkTts);
		mChkTts.setOnCheckedChangeListener(onChkTtsCheckedChange);
		mChkReport = (CheckBox)v.findViewById(R.id.chkReport);
		mChkReport.setOnCheckedChangeListener(onChkReportCheckedChange);
		mChkReport.setEnabled(!sReportUser.isEmpty());
		
		mLogAdapter = new LogListAdapter(getActivity());
		mListLog = (ListView)v.findViewById(R.id.listLog);
		mListLog.setAdapter(mLogAdapter);
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
		mBroadcastManager.registerReceiver(mBroadcastReceiver, mServiceIntentFilter);
		if(!ClassifierService.isRunning())
		{
			mService = null;

			sReportUser = mPrefs.getString(SettingsActivity.KEY_REPORT_USER, "");
			if(sReportUser.isEmpty())
				mChkReport.setChecked(false);
			mChkReport.setEnabled(!sReportUser.isEmpty());
		}
	}
	
	@Override
	public void onStop()
	{
		super.onStop();
		// Save selected output methods
		Storage.getUiPreferences(getActivity()).edit()
			.putBoolean(Storage.KEY_CHK_REPORT_CHECKED, mChkReport.isChecked())
			.putBoolean(Storage.KEY_CHK_TTS_CHECKED, mChkTts.isChecked())
			.apply();
	}
	
	@Override
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
		sJumpSize = TrainFragment.JUMP_SIZE;
		sReportServer = mPrefs.getString(SettingsActivity.KEY_REPORT_SERVER, "");
		sReportUser = mPrefs.getString(SettingsActivity.KEY_REPORT_USER, "");
		sWriteLog = mPrefs.getBoolean(SettingsActivity.KEY_WRITE_LOG, false);
		sLogFilename = mPrefs.getString(SettingsActivity.KEY_LOG_FILENAME, "");
		sWakelock = mPrefs.getBoolean(SettingsActivity.KEY_USE_WAKELOCK, false);
	}
	
	/**
	 * Enable or disable the report switch and set visibility of {@link #mLblNoClassifier}.
	 */
	private void setClassifierPresent(boolean present)
	{
		mLblNoClassifier.setVisibility(present ? View.GONE : View.VISIBLE);
		mSwClassifiy.setEnabled(present);
		if(!present)
			mClassifier = null;
	}
	
	/**
	 * Checks static variable {@link #sClassifierFile} for a filename and deserializes the
	 * classifier if there is one. Does nothing if the {@link ClassifierService} is running.
	 */
	private void checkForClassifier()
	{
		if(LOCAL_LOGV) Log.v(LOGTAG, "checkForClassifier called");
		if(ClassifierService.isRunning())
			return;
		
		readSettings();
		
		if(!sClassifierFile.isEmpty())
			new CheckForClassifierTask().execute();
		else
			setClassifierPresent(false);
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
		// Log the new activity
		String activity = intent.getStringExtra(ClassifierService.EXTRA_ACTIVITY_NAME);
		if(activity != null)
			mLogAdapter.add(getString(R.string.log_new_activity, activity));
	}
	
	private void onServiceStarted()
	{
		// Get service instance and log event
		mService = ClassifierService.getInstance();
		mSwClassifiy.setChecked(true);
		mLogAdapter.add(getString(R.string.rservice_started));
	}
	
	private void onServiceStopped()
	{
		mSwClassifiy.setChecked(false);
		mLogAdapter.add(getString(R.string.rservice_stopped));
		mService = null;
	}
	
	/**
	 * Determines whether the report service can be started.
	 * 
	 * @return {@code true} if the service can be started and is not running, {@code false}
	 *         otherwise.
	 */
	public boolean canStart()
	{
		return !ClassifierService.isRunning() && mClassifier != null;
	}
	
	/**
	 * Starts the service. If {@link #canStart()} returns {@code false}, does nothing.
	 */
	public void startService()
	{
		if(!canStart())
			return;
		
		readSettings();
		Intent i = new Intent(ClassifierService.CLASSIFIER_SERVICE);
		i.putExtra(EXTRA_CLASSIFIER, mClassifier);
		i.putExtra(EXTRA_WINDOW, sWindowSize);
		i.putExtra(EXTRA_JUMP, sJumpSize);
		i.putExtra(EXTRA_FEATURES, sTrainedFeatures);
		i.putExtra(EXTRA_WAKELOCK, sWakelock);
		
		// This is a little complicated, the first value determines whether the feature should be
		// enabled at all, the second one specifies whether it's actually activated
		i.putExtra(EXTRA_TTS, true);
		i.putExtra(EXTRA_TTS_ENABLE, mChkTts.isChecked());
		
		i.putExtra(EXTRA_LOG, true);
		i.putExtra(EXTRA_LOG_ENABLE, sWriteLog);
		i.putExtra(EXTRA_FILENAME, sLogFilename);
		i.putExtra(EXTRA_DIRNAME, sOutputDir);
		
		i.putExtra(EXTRA_REPORT, !sReportUser.isEmpty());
		i.putExtra(EXTRA_REPORT_ENABLE, mChkReport.isChecked());
		i.putExtra(EXTRA_REPORT_SERVER, sReportServer);
		i.putExtra(EXTRA_REPORT_USER, sReportUser);
		
		getActivity().startService(i);
	}
	
	/**
	 * Stops the running service.
	 */
	public void stopService()
	{
		getActivity().stopService(new Intent(ClassifierService.CLASSIFIER_SERVICE));
	}
	
	/**
	 * Attempts to deserialize the classifier from the file in the settings, see
	 * {@link Storage#KEY_CLASSIFIER_FILE}.
	 * 
	 * @author Peter
	 */
	private class CheckForClassifierTask extends AsyncTask<Void, Void, Classifier>
	{
		@Override
		protected Classifier doInBackground(Void... params)
		{
			try
			{
				InputStream is = getActivity().openFileInput(sClassifierFile);
				ObjectInputStream ois = new ObjectInputStream(is);
				Classifier out = (Classifier)ois.readObject();
				ois.close();
				return out;
			}
			catch(FileNotFoundException ex)
			{
				Log.e(LOGTAG, "Classifier file from preference not found.", ex);
				sClassifierFile = "";
				mPrefsClassifier.edit().putString(Storage.KEY_CLASSIFIER_FILE, "").apply();
				return null;
			}
			catch(StreamCorruptedException ex)
			{
				Log.e(LOGTAG, "Serialized classifier is corrupted.", ex);
				sClassifierFile = "";
				mPrefsClassifier.edit().putString(Storage.KEY_CLASSIFIER_FILE, "").apply();
				return null;
			}
			catch(IOException ex)
			{
				Toast.makeText(getActivity(), R.string.error_io, Toast.LENGTH_LONG).show();
				Log.e(LOGTAG, "IOException while reading serialized classifier.", ex);
				return null;
			}
			catch(ClassNotFoundException ex)
			{
				Log.wtf(LOGTAG, "Class of the serialized classifier not found??", ex);
				throw new InternalError();
			}
		}
		
		@Override
		protected void onPreExecute()
		{
			mProgressSerialize.setVisibility(View.VISIBLE);
		}
		
		@Override
		protected void onPostExecute(Classifier result)
		{
			setClassifierPresent(result != null);
			mClassifier = result;
			mProgressSerialize.setVisibility(View.GONE);
			if(LOCAL_LOGV) Log.v(LOGTAG, "CheckForClassifierTask finished");
		}
	}
}
