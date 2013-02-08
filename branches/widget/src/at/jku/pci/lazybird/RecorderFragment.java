package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.Date;
import java.util.Locale;

public class RecorderFragment extends AbstractTabFragment
{
	// Extras
	public static final String EXTRA_FILENAME = "at.jku.pci.lazybird.FILENAME";
	public static final String EXTRA_CLASS = "at.jku.pci.lazybird.CLASS";
	public static final String EXTRA_CLASSES = "at.jku.pci.lazybird.CLASSES";
	public static final String EXTRA_DIRNAME = "at.jku.pci.lazybird.DIRNAME";
	public static final String EXTRA_WAKELOCK = "at.jku.pci.lazybird.WAKELOCK";
	// Intents
	public static final String BCAST_SERVICE_STOPPED = "at.jku.pci.lazybird.REC_SERVICE_STOPPED";
	public static final String BCAST_SERVICE_STARTED = "at.jku.pci.lazybird.REC_SERVICE_STARTED";
	// Constants
	static final String LOGTAG = "RecorderFragment";
	static final boolean LOCAL_LOGV = true;
	/**
	 * Standard extension for ARFF files.
	 * <p> {@value}
	 */
	public static final String EXTENSION = ".arff";
	/**
	 * Format string used for the last values displayed
	 */
	public static final String TRIPLET_FORMAT = "%.1f, %.1f, %.1f";
	/**
	 * Format string used to construct a filename.
	 */
	public static final String FILENAME_FORMAT = "yyyyMMddkkmmss";
	/**
	 * Gets the default title associated with this fragment for use in an {@link ActionBar} tab.
	 * 
	 * @see #getTitle()
	 */
	public static final CharSequence TITLE = "Record";
	// Settings
	/**
	 * Setting: {@link SettingsActivity#KEY_VALUE_UPDATE_SPEED}
	 */
	private static long sValueUpdateDelay;
	/**
	 * Setting: {@link SettingsActivity#KEY_OUTPUT_DIR}
	 * 
	 * @see ARFFRecorderService#getDirname()
	 */
	private static String sOutputDir;
	/**
	 * Setting: {@link SettingsActivity#KEY_USE_WAKELOCK}
	 */
	private static boolean sWakelock;
	
	private SharedPreferences mPrefs;
	
	// Views
	private Switch mSwOnOff;
	private EditText mTxtFilename;
	private Spinner mSpinClass;
	private Button mBtnMakeFilename;
	private Button mBtnArff;
	private TextView mTxtStartTime;
	private TextView mTxtLastValues;
	private TextView mTxtNumValues;
	private TextView mTxtValsPerScond;
	private TextView mLabelStartTime;
	private TextView mLabelNumValues;
	private TextView mLabelLastValues;
	private TextView mLabelValsPerSecond;
	private ImageButton mBtnDelete;

	// Fields
	private int mTextColor;
	private int mDisabledColor = Color.GRAY;
	
	// Handlers
	private ARFFRecorderService mService = null;
	private Handler mHandler = new Handler();
	private Runnable mRunUpdateValues = new Runnable() {
		public void run()
		{
			if(ARFFRecorderService.isRunning())
			{
				updateLastValues();
				mHandler.postDelayed(this, sValueUpdateDelay);
			}
		}
	};
	private LocalBroadcastManager mBroadcastManager;
	private IntentFilter mServiceIntentFilter;
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
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
		return inflater.inflate(R.layout.fragment_recorder, container, false);
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
		updateSettings();
		
		getWidgets(getView());
		
		// If the service is running and we just got created, fill inputs with running data.
		// setting of input enabled and such things are done in onResume.
		if(ARFFRecorderService.isRunning())
		{
			mService = ARFFRecorderService.getInstance();
			// double check for an actual instance, just to be sure
			if(mService != null)
			{
				mTxtFilename.setText(mService.getFilename());
				// Select appropriate class, if possible
				SpinnerAdapter a = mSpinClass.getAdapter();
				for(int j = 0, count = a.getCount(); j < count; j++)
					if(a.getItem(j).equals(mService.getAClass()))
					{
						mSpinClass.setSelection(j);
						break;
					}
			}
		}
	}
	
	/**
	 * Sets the fields for the views of this Fragment and registers listeners and stuff.
	 * 
	 * @param v the View for this Fragment
	 */
	private void getWidgets(View v)
	{
		mTxtFilename = (EditText)v.findViewById(R.id.txtFilename);
		
		mSwOnOff = (Switch)v.findViewById(R.id.swOnOff);
		mSwOnOff.setOnClickListener(onSwOnOffClick);
		// Prevent dragging of the switch, leads to weird behavior when also setting the checked
		// state in code
		mSwOnOff.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event)
			{
				if(event.getAction() == MotionEvent.ACTION_MOVE)
					return true;
				return false;
			}
		});
		mSwOnOff.setOnGenericMotionListener(new OnGenericMotionListener() {
			public boolean onGenericMotion(View v, MotionEvent event)
			{
				if(event.getAction() == MotionEvent.ACTION_MOVE)
					return true;
				return false;
			}
		});
		
		mSpinClass = (Spinner)v.findViewById(R.id.spinClass);
		
		mBtnMakeFilename = (Button)v.findViewById(R.id.btnMakeFilename);
		mBtnMakeFilename.setOnClickListener(onBtnMakeFilenameClick);
		mBtnArff = (Button)v.findViewById(R.id.btnArff);
		mBtnArff.setOnClickListener(onBtnArffClick);
		mBtnDelete = (ImageButton)v.findViewById(R.id.btnDelete);
		mBtnDelete.setOnClickListener(onBtnDeleteClick);
		
		mTxtStartTime = (TextView)v.findViewById(R.id.txtStartTime);
		mTxtLastValues = (TextView)v.findViewById(R.id.txtLastValues);
		mTxtNumValues = (TextView)v.findViewById(R.id.txtNumValues);
		mTxtValsPerScond = (TextView)v.findViewById(R.id.txtValsPerSecond);
		mTextColor = mTxtNumValues.getTextColors().getDefaultColor();
		
		mLabelNumValues = (TextView)v.findViewById(R.id.labelNumValues);
		mLabelLastValues = (TextView)v.findViewById(R.id.labelLastValues);
		mLabelStartTime = (TextView)v.findViewById(R.id.labelStartTime);
		mLabelValsPerSecond = (TextView)v.findViewById(R.id.labelValsPerSecond);
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		
		// stop last value update and unregister service receiver when paused
		mHandler.removeCallbacks(mRunUpdateValues);
		mBroadcastManager.unregisterReceiver(mBroadcastReceiver);
		mTxtNumValues.setTextColor(mDisabledColor);
		mTxtLastValues.setTextColor(mDisabledColor);
		mTxtStartTime.setTextColor(mDisabledColor);
		mTxtValsPerScond.setTextColor(mDisabledColor);
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		
		// check for running service every time the fragment is resumed, since broadcast can't
		// be received while paused or stopped
		mSwOnOff.setChecked(ARFFRecorderService.isRunning());
		setViewStates(ARFFRecorderService.isRunning());
		if(ARFFRecorderService.isRunning())
		{
			// resume last value update and re-register the service receiver, if service is
			// started
			mHandler.post(mRunUpdateValues);
			mBroadcastManager.registerReceiver(mBroadcastReceiver, mServiceIntentFilter);
		}
		else
			mService = null;
	}
	
	@Override
	public CharSequence getTitle()
	{
		if(getActivity() != null)
			return getString(R.string.title_tab_record);
		else
			return TITLE;
	}
	
	/**
	 * Updates the labels with the last values read from the service, if possible.
	 */
	private void updateLastValues()
	{
		if(mService == null)
			return;
		
		final float[] values = mService.getLastValues();
		
		if(values != null)
		{
			mTxtLastValues.setText(String.format((Locale)null, TRIPLET_FORMAT,
				values[0], values[1], values[2]));
			mTxtNumValues.setText(String.format("%,d", mService.getNumValues()));
			long runtime = System.currentTimeMillis() - mService.getStartTime().getTime();
			runtime = runtime / 1000 - (long)ARFFRecorderService.getStartDelay();
			if(runtime > 0)
				mTxtValsPerScond.setText(String.valueOf(mService.getNumValues() / runtime));
		}
	}
	
	/**
	 * Sets all appropriate private fields from the shared preferences.<br>
	 * Also sets static properties of {@link ARFFRecorderService}:
	 * <ul>
	 * <li>{@link ARFFRecorderService#setMaxNumValues(long)}
	 * <li>{@link ARFFRecorderService#setStartDelay(int)}
	 * </ul>
	 */
	private void updateSettings()
	{
		sOutputDir = mPrefs.getString(SettingsActivity.KEY_OUTPUT_DIR, "");
		sWakelock = mPrefs.getBoolean(SettingsActivity.KEY_USE_WAKELOCK, false);
		ARFFRecorderService.setStartDelay(mPrefs.getInt(SettingsActivity.KEY_START_DELAY, 0));
		try
		{
			String s = mPrefs.getString(SettingsActivity.KEY_VALUE_UPDATE_SPEED, "");
			sValueUpdateDelay = Integer.parseInt(s);
		}
		catch(NumberFormatException ex)
		{
			// Should not happen, clean it up anyway
			Log.e(LOGTAG, "Setting " + SettingsActivity.KEY_VALUE_UPDATE_SPEED +
				" is screwed up: " + ex);
			displayWarning(R.string.error, R.string.error_generic, "Parse valueUpdateDelay");
			mPrefs.edit().putString(SettingsActivity.KEY_VALUE_UPDATE_SPEED, "750").apply();
			sValueUpdateDelay = 750;
		}
		try
		{
			String s = mPrefs.getString(SettingsActivity.KEY_MAX_NUM_VALUES, "");
			ARFFRecorderService.setMaxNumValues(Long.parseLong(s));
		}
		catch(NumberFormatException ex)
		{
			// Should not happen, clean it up anyway
			Log.e(LOGTAG, "Setting " + SettingsActivity.KEY_MAX_NUM_VALUES +
				" is screwed up: " + ex);
			displayWarning(R.string.error, R.string.error_generic, "Parse maxNumValues");
			mPrefs.edit().putString(SettingsActivity.KEY_MAX_NUM_VALUES, "10000").apply();
			ARFFRecorderService.setMaxNumValues(10000);
		}
	}
	
	/**
	 * Sets the states of the inputs and labels according to the specified value.
	 * <p>
	 * Inputs are disabled if the service is running, labels get a disabled looking color if the
	 * service is not running. Also the start time is updated and the values of the labels
	 * cleared when appropriate.
	 * 
	 * @param running
	 */
	private void setViewStates(boolean running)
	{
		mTxtFilename.setEnabled(!running);
		mBtnMakeFilename.setEnabled(!running);
		mBtnArff.setEnabled(!running);
		mSpinClass.setEnabled(!running);
		
		final int color = (running ? mTextColor : mDisabledColor);
		mLabelNumValues.setTextColor(color);
		mLabelStartTime.setTextColor(color);
		mLabelLastValues.setTextColor(color);
		mLabelValsPerSecond.setTextColor(color);
		mTxtNumValues.setTextColor(color);
		mTxtStartTime.setTextColor(color);
		mTxtLastValues.setTextColor(color);
		mTxtValsPerScond.setTextColor(color);
		
		if(running)
		{
			setStartTime();
		}
		else
		{
			mTxtNumValues.setText(R.string.nothing);
			mTxtLastValues.setText(R.string.nothing);
			mTxtStartTime.setText(R.string.nothing);
			mTxtValsPerScond.setText(R.string.nothing);
		}
	}
	
	/**
	 * Sets the text of the start time label from the running service instance, if possible.
	 */
	private void setStartTime()
	{
		if(mService != null)
		{
			final Date time = mService.getStartTime();
			mTxtStartTime.setText(DateFormat.getTimeFormat(getActivity()).format(time));
		}
	}
	
	private OnClickListener onBtnDeleteClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			final String filename = mTxtFilename.getText().toString();
			
			if(filename.isEmpty())
				return;
			
			final File f = new File(new File(Environment.getExternalStorageDirectory(),
				sOutputDir), filename);
			int text;
			if(f.isFile())
				text = (f.delete() ? R.string.fileDeleted : R.string.fileNotDeleted);
			else
				text = R.string.fileNotFound;
			Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();
		}
	};
	
	private OnClickListener onBtnMakeFilenameClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			mTxtFilename.setText(DateFormat.format(FILENAME_FORMAT, new Date()) + EXTENSION);
		}
	};
	
	private OnClickListener onBtnArffClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			// Add .arff extension to filename
			String f = mTxtFilename.getText().toString();
			
			if(!f.endsWith(EXTENSION) && !f.isEmpty())
				mTxtFilename.setText(f + EXTENSION);
		}
	};
	
	private OnClickListener onSwOnOffClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			onSwOnOffClick(v);
		}
	};
	
	private void onSwOnOffClick(View v)
	{
		if(mSwOnOff.isChecked())
		{
			// Check for writable external storage
			if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
			{
				Toast.makeText(getActivity(), R.string.error_extstorage,
					Toast.LENGTH_LONG).show();
				mSwOnOff.setChecked(false);
				return;
			}
			
			String filename = mTxtFilename.getText().toString();
			
			if(filename.isEmpty() || filename.contains(File.separator))
			{
				Toast.makeText(getActivity(), R.string.error_filename,
					Toast.LENGTH_SHORT).show();
				mSwOnOff.setChecked(false);
			}
			else
			{
				updateSettings();
				Intent i = new Intent(ARFFRecorderService.ARFF_SERVICE);
				i.putExtra(EXTRA_FILENAME, filename);
				i.putExtra(EXTRA_DIRNAME, sOutputDir);
				i.putExtra(EXTRA_WAKELOCK, sWakelock);
				
				// TODO make classes customizable
				i.putExtra(EXTRA_CLASSES, getResources().getStringArray(R.array.classes));
				i.putExtra(EXTRA_CLASS, mSpinClass.getSelectedItemPosition());
				
				getActivity().startService(i);
				mBroadcastManager.registerReceiver(mBroadcastReceiver, mServiceIntentFilter);
				setViewStates(true);
			}
		}
		else
		{
			getActivity().stopService(new Intent(ARFFRecorderService.ARFF_SERVICE));
		}
	}
	
	/**
	 * Called from the broadcast receiver when the service has started recording.
	 */
	private void onServiceStarted()
	{
		mService = ARFFRecorderService.getInstance();
		mHandler.postDelayed(mRunUpdateValues, 100);
		setStartTime();
	}
	
	/**
	 * Called from the broadcast receiver when the service has stopped. This only happens if
	 * {@link ARFFRecorderService#onDestroy()} is called by the system.
	 */
	private void onServiceStopped()
	{
		mHandler.removeCallbacks(mRunUpdateValues);
		mBroadcastManager.unregisterReceiver(mBroadcastReceiver);
		setViewStates(false);
		mSwOnOff.setChecked(false);
		mService = null;
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
