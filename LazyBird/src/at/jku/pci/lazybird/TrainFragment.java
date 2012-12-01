package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;

public class TrainFragment extends Fragment
{
	// Extras
	public static final String EXTRA_TODO = "at.jku.pci.lazybird.TODO";
	// Intents
	public static final String BCAST_SERVICE_STOPPED = "at.jku.pci.lazybird.TRA_SERVICE_STOPPED";
	public static final String BCAST_SERVICE_STARTED = "at.jku.pci.lazybird.TRA_SERVICE_STARTED";
	// Constants
	public static final String LOGTAG = "ARFFRecorderFragment";
	public static final boolean LOCAL_LOGV = true;
	/**
	 * Gets the default title associated with this fragment for use in an {@link ActionBar} tab.
	 * 
	 * @see #getTitle()
	 */
	public static final CharSequence TITLE = "Train";
	// Settings
	/**
	 * Output directory for recorded files, the base directory is always
	 * {@link Environment#getExternalStorageDirectory()}.
	 * <p>
	 * Setting: {@link SettingsActivity#KEY_OUTPUT_DIR}
	 * 
	 * @see ARFFRecorderService#getDirname()
	 */
	private static String sOutputDir;
	
	private SharedPreferences mPrefs;
	
	// Fields
	private Button mBtnMergeFiles;
	private Button mBtnSelectFile;
	private Button mBtnSelectFeatures;
	private Button mBtnTrain;
	private Spinner mSpinWindowSize;
	private Spinner mSpinClassifier;
	private ProgressBar mProgressTraining;
	
	// Handlers
	// private ARFFRecorderService mService = null;
	private Handler mHandler = new Handler();
	private Runnable mRunProgress = new Runnable() {
		public void run()
		{
			if(ARFFRecorderService.isRunning())
			{
				// TODO update progress if possible, most likely remove
			}
		}
	};
	private LocalBroadcastManager mBroadcastManager;
	private IntentFilter mServiceIntentFilter;
	private BroadcastReceiver mServiceReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent)
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "Received broadcast: " + intent);
			
			if(intent.getAction().equals(BCAST_SERVICE_STOPPED))
				onServiceStopped();
			if(intent.getAction().equals(BCAST_SERVICE_STARTED))
				onServiceStarted();
		}
	};
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		Bundle savedInstanceState)
	{
		// Just return the inflated layout, other initializations will be done when the host
		// activity is created
		return inflater.inflate(R.layout.fragment_train, container, false);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		
		mBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
		mServiceIntentFilter = new IntentFilter();
		mServiceIntentFilter.addAction(BCAST_SERVICE_STARTED);
		mServiceIntentFilter.addAction(BCAST_SERVICE_STOPPED);
		
		PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		updateSettings();
		
		getWidgets(getView());
		
		// if the service is running and we just got created, fill inputs with running data.
		// setting of input enabled and such things are done in onResume
		// if(ARFFRecorderService.isRunning())
		// {
		// mService = ARFFRecorderService.getInstance();
		// // double check for an actual instance, just to be sure
		// if(mService != null)
		// {
		// mTxtFilename.setText(mService.getFilename());
		// // Select appropriate class, if possible
		// SpinnerAdapter a = mSpinClass.getAdapter();
		// for(int j = 0, count = a.getCount(); j < count; j++)
		// if(mService.getAClass().equals(a.getItem(j)))
		// {
		// mSpinClass.setSelection(j);
		// break;
		// }
		// }
		// }
	}
	
	/**
	 * Sets the fields for the views of this Fragment and registers listeners and stuff.
	 * 
	 * @param v the View for this Fragment
	 */
	private void getWidgets(View v)
	{
		mBtnMergeFiles = (Button)v.findViewById(R.id.btnMergeFiles);
		mBtnMergeFiles.setOnClickListener(onBtnMergeFilesClick);
		mBtnSelectFile = (Button)v.findViewById(R.id.btnSelectFile);
		mBtnSelectFile.setOnClickListener(onBtnSelectFileClick);
		mBtnSelectFeatures = (Button)v.findViewById(R.id.btnSelectFeatures);
		mBtnSelectFeatures.setOnClickListener(onBtnSelectFeaturesClick);
		mBtnTrain = (Button)v.findViewById(R.id.btnTrain);
		mBtnTrain.setOnClickListener(onBtnTrainClick);
		mBtnTrain.setEnabled(false);
		
		mSpinWindowSize = (Spinner)v.findViewById(R.id.spinWindowSize);
		if(mSpinWindowSize.getCount() > 1)
			mSpinWindowSize.setSelection(1);
		mSpinClassifier = (Spinner)v.findViewById(R.id.spinClassifier);
		
		mProgressTraining = (ProgressBar)v.findViewById(R.id.progressTraining);
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		
		// TODO maybe remove progress handler
		// stop progress update and unregister service receiver when paused
		mHandler.removeCallbacks(mRunProgress);
		mBroadcastManager.unregisterReceiver(mServiceReceiver);
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		
		// TODO check for running training service
		// check for running training every time the fragment is resumed, since broadcast can't
		// be received while paused or stopped
		// setViewStates(ARFFRecorderService.isRunning());
//		if(ARFFRecorderService.isRunning())
//		{
//			// resume last value update and re-register the service receiver, if service is
//			// started
//			mHandler.post(mRunUpdateValues);
//			mBroadcastManager.registerReceiver(mServiceReceiver, mServiceIntentFilter);
//		}
//		else
//			mService = null;
	}
	
	/**
	 * Gets the title associated with this fragment for use in an {@link ActionBar} tab.
	 * 
	 * @return the title of this fragment.
	 */
	public CharSequence getTitle()
	{
		if(getActivity() != null)
			return getString(R.string.title_tab_record);
		else
			return TITLE;
	}
	
	/**
	 * Sets all appropriate private fields from the shared preferences.
	 */
	private void updateSettings()
	{
		sOutputDir = mPrefs.getString(SettingsActivity.KEY_OUTPUT_DIR, "");
	}
	
	/**
	 * Sets the states of the buttons and spinners according to the specified value.
	 * <p>
	 * Inputs are disabled if the training is running.
	 * 
	 * @param running
	 */
	private void setViewStates(boolean training)
	{
		mBtnMergeFiles.setEnabled(!training);
		mBtnSelectFile.setEnabled(!training);
		mBtnSelectFeatures.setEnabled(!training);
		mSpinClassifier.setEnabled(!training);
		mSpinWindowSize.setEnabled(!training);
		
		mProgressTraining.setVisibility(training ? View.VISIBLE : View.INVISIBLE);
	}
	
	private OnClickListener onBtnMergeFilesClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			// TODO Auto-generated method stub
			
		}
	};
	
	private OnClickListener onBtnSelectFileClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			// TODO Auto-generated method stub
			
		}
	};
	
	private OnClickListener onBtnSelectFeaturesClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			// TODO Auto-generated method stub
			
		}
	};
	
	private OnClickListener onBtnTrainClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			// TODO Auto-generated method stub
			
		}
	};
	
	private void onServiceStopped()
	{
		// TODO Auto-generated method stub
		
	}
	
	private void onServiceStarted()
	{
		// TODO Auto-generated method stub
		
	}
	
	/**
	 * Builds an {@link AlertDialog} with the specified title, message and info and displays it
	 * to the user. The dialog has one OK button and an alert icon.
	 * 
	 * @param title the resource ID to use as a title
	 * @param message the resource ID to use for the message
	 * @param info the info to display below the message, or {@code null}
	 */
	private void displayWarning(int title, int message, CharSequence info)
	{
		AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
		b.setIcon(android.R.drawable.ic_dialog_alert);
		b.setTitle(title);
		b.setNeutralButton(android.R.string.ok, null);
		
		if(info != null)
			b.setMessage(getText(message) + "\n" + getText(R.string.dialogInfo) + " " + info);
		else
			b.setMessage(message);
		
		b.show();
	}
}
