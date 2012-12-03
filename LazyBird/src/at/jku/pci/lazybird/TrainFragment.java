package at.jku.pci.lazybird;

import java.io.File;
import java.io.FileFilter;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;
import at.jku.pci.lazybird.features.Feature;

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
	private Button mBtnSelectFile;
	private Button mBtnSelectFeatures;
	private Button mBtnSaveFeatures;
	private Button mBtnTrain;
	private Spinner mSpinWindowSize;
	private Spinner mSpinClassifier;
	private ProgressBar mProgressTraining;
	
	private Drawable compoundCheck;
	private Drawable compoundUncheck;
	private Drawable compoundAlert;
	
	private FileFilter arffFilter;
	private File[] files = new File[0];
	private Feature[] features = new Feature[0];
	
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
		
		PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		updateSettings();
		
		getWidgets(getView());
		
		// Get drawables for the select buttons
		compoundUncheck = getResources().getDrawable(android.R.drawable.checkbox_off_background);
		compoundUncheck.setBounds(mBtnSelectFile.getCompoundDrawables()[0].copyBounds());
		compoundCheck = getResources().getDrawable(android.R.drawable.checkbox_on_background);
		compoundCheck.setBounds(compoundUncheck.copyBounds());
		compoundAlert = getResources().getDrawable(android.R.drawable.ic_dialog_alert);
		compoundAlert.setBounds(compoundUncheck.copyBounds());
		
		arffFilter = new FileFilter() {
			@Override
			public boolean accept(File pathname)
			{
				if(!pathname.isFile())
					return false;
				return pathname.getName().endsWith(RecorderFragment.EXTENSION);
			}
		};
		
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
		mBtnSelectFile = (Button)v.findViewById(R.id.btnSelectFile);
		mBtnSelectFile.setOnClickListener(onBtnSelectFileClick);
		mBtnSelectFeatures = (Button)v.findViewById(R.id.btnSelectFeatures);
		mBtnSelectFeatures.setOnClickListener(onBtnSelectFeaturesClick);
		mBtnSaveFeatures = (Button)v.findViewById(R.id.btnSaveFeatures);
		mBtnSaveFeatures.setOnClickListener(onBtnSaveFeaturesClick);
		mBtnSaveFeatures.setEnabled(false);
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
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		
		// TODO check for running training service
		// check for running training every time the fragment is resumed, since broadcast can't
		// be received while paused or stopped
		// setViewStates(ARFFRecorderService.isRunning());
		// if(ARFFRecorderService.isRunning())
		// {
		// // resume last value update and re-register the service receiver, if service is
		// // started
		// mHandler.post(mRunUpdateValues);
		// mBroadcastManager.registerReceiver(mServiceReceiver, mServiceIntentFilter);
		// }
		// else
		// mService = null;
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
		mBtnSelectFile.setEnabled(!training);
		mBtnSelectFeatures.setEnabled(!training);
		mSpinClassifier.setEnabled(!training);
		mSpinWindowSize.setEnabled(!training);
		
		mProgressTraining.setVisibility(training ? View.VISIBLE : View.INVISIBLE);
	}
	
	private void updateTrainEnabled()
	{
		boolean enabled = files.length > 0 && features.length > 0;
		mBtnTrain.setEnabled(enabled);
		mBtnSaveFeatures.setEnabled(enabled);
	}
	
	private OnClickListener onBtnSelectFileClick = new OnClickListener() {
		File[] allFiles;
		boolean[] selected;
		
		@Override
		public void onClick(View v)
		{
			final String readonly = Environment.MEDIA_MOUNTED_READ_ONLY;
			if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) &&
				!Environment.getExternalStorageState().equals(readonly))
			{
				Toast.makeText(getActivity(), R.string.error_extstorage_read, Toast.LENGTH_LONG)
					.show();
				setLeftDrawable(mBtnSelectFile, compoundAlert);
				return;
			}
			
			updateSettings();
			final File dir = new File(Environment.getExternalStorageDirectory(), sOutputDir);
			
			if(!dir.exists() || !dir.isDirectory())
			{
				Toast.makeText(getActivity(), R.string.error_nodir, Toast.LENGTH_LONG).show();
				setLeftDrawable(mBtnSelectFile, compoundAlert);
				return;
			}
			
			allFiles = dir.listFiles(arffFilter);
			final OnMultiChoiceClickListener checkListener = new OnMultiChoiceClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked)
				{
					selected[which] = isChecked;
				}
			};
			
			String[] filenames = new String[allFiles.length];
			selected = new boolean[allFiles.length];
			for(int j = 0; j < allFiles.length; j++)
			{
				filenames[j] = allFiles[j].getName();
				for(int k = 0; k < files.length; k++)
				{
					if(allFiles[j].equals(files[k]))
					{
						selected[j] = true;
						break;
					}
				}
			}
			
			final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
			b.setTitle(R.string.btnSelectFile);
			b.setPositiveButton(android.R.string.ok, filesSelectedListener);
			b.setNegativeButton(android.R.string.cancel, null);
			b.setMultiChoiceItems(filenames, selected, checkListener);
			b.show();
		}
		
		DialogInterface.OnClickListener filesSelectedListener =
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					if(which != AlertDialog.BUTTON_POSITIVE)
						return;
					
					// User clicked OK, count the number of selected files
					int numSelected = 0;
					for(int j = 0; j < selected.length; j++)
					{
						if(selected[j])
							numSelected++;
					}
					
					// Save selected files in field and set button checkbox
					files = new File[numSelected];
					if(numSelected > 0)
					{
						int idx = 0;
						for(int j = 0; j < selected.length; j++)
						{
							if(selected[j])
								files[idx++] = allFiles[j].getAbsoluteFile();
						}
						setLeftDrawable(mBtnSelectFile, compoundCheck);
					}
					else
					{
						setLeftDrawable(mBtnSelectFile, compoundUncheck);
					}
					updateTrainEnabled();
				}
			};
	};
	
	private OnClickListener onBtnSelectFeaturesClick = new OnClickListener() {
		final Feature[] allFeatures = Feature.values();
		String[] featureNames = null;
		int rawIdx;
		boolean[] selected;
		
		@Override
		public void onClick(View v)
		{
			if(featureNames == null)
				init();
			
			for(int j = 0; j < selected.length; j++)
			{
				selected[j] = false;
				for(int k = 0; k < features.length; k++)
				{
					if(features[k] == allFeatures[j])
					{
						selected[j] = true;
						break;
					}
				}
			}
			
			final OnMultiChoiceClickListener checkListener = new OnMultiChoiceClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked)
				{
					final ListView list = ((AlertDialog)dialog).getListView();
					selected[which] = isChecked;
					
					if(isChecked)
					{
						// If raw feature was selected, clear all other selections, otherwise
						// clear raw feature selection
						if(which == rawIdx)
						{
							for(int j = 0; j < selected.length; j++)
							{
								if(selected[j])
								{
									list.setItemChecked(j, false);
									selected[j] = false;
								}
							}
							selected[rawIdx] = true;
							list.setItemChecked(rawIdx, true);
						}
						else
						{
							list.setItemChecked(rawIdx, false);
							selected[rawIdx] = false;
						}
					}
				}
			};
			
			final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
			b.setTitle(R.string.btnSelectFeatures);
			b.setPositiveButton(android.R.string.ok, featuresSelectedListener);
			b.setNegativeButton(android.R.string.cancel, null);
			b.setMultiChoiceItems(featureNames, selected, checkListener);
			b.show();
		}
		
		DialogInterface.OnClickListener featuresSelectedListener =
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					if(which != AlertDialog.BUTTON_POSITIVE)
						return;
					
					if(selected[rawIdx])
					{
						features = new Feature[] { Feature.RAW };
						setLeftDrawable(mBtnSelectFeatures, compoundCheck);
						updateTrainEnabled();
						
						return;
					}
					
					// User clicked OK, count the number of selected features
					int numSelected = 0;
					for(int j = 0; j < selected.length; j++)
					{
						if(selected[j])
							numSelected++;
					}
					
					// Save selected features in field and set button checkbox
					features = new Feature[numSelected];
					if(numSelected > 0)
					{
						int idx = 0;
						for(int j = 0; j < selected.length; j++)
						{
							if(selected[j])
								features[idx++] = allFeatures[j];
						}
						setLeftDrawable(mBtnSelectFeatures, compoundCheck);
					}
					else
					{
						setLeftDrawable(mBtnSelectFeatures, compoundUncheck);
					}
					updateTrainEnabled();
				}
			};
			
			private void init()
			{
				featureNames = new String[allFeatures.length];
				selected = new boolean[allFeatures.length];
				
				for(int j = 0; j < allFeatures.length; j++)
				{
					featureNames[j] = allFeatures[j].getName();
					if(allFeatures[j] == Feature.RAW)
						rawIdx = j;
				}
			}
	};
	
	private OnClickListener onBtnSaveFeaturesClick = new OnClickListener() {
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
			
		}
	};
	
	private void setLeftDrawable(Button btn, Drawable d)
	{
		btn.setCompoundDrawables(d, null, null, null);
	}
}
