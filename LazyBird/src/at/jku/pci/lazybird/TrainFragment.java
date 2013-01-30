package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import at.jku.pci.lazybird.features.Feature;
import at.jku.pci.lazybird.features.FeatureExtractor;
import at.jku.pci.lazybird.util.Storage;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.J48;
import weka.core.Instances;
import weka.core.UnsupportedAttributeTypeException;
import weka.core.Utils;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import weka.core.converters.SerializedInstancesSaver;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class TrainFragment extends AbstractTabFragment
{
	/**
	 * Represents an entry in the list of selectable classifiers.
	 * 
	 * @author Peter
	 */
	private static class ClassifierEntry
	{
		public final Class<? extends Classifier> type;
		
		public ClassifierEntry(Class<? extends Classifier> type)
		{
			this.type = type;
		}
		
		@Override
		public String toString()
		{
			return type.getSimpleName();
		}
	}
	
	// Constants
	static final String LOGTAG = "TrainFragment";
	static final boolean LOCAL_LOGV = true;
	/**
	 * Bundle key for the selected files.
	 */
	public static final String STATE_FILES = "at.jku.pci.lazybird.FILES";
	/**
	 * Bundle key for the selected features.
	 */
	public static final String STATE_FEATURES = "at.jku.pci.lazybird.FEATURES";
	
	/**
	 * The default title associated with this fragment for use in an {@link ActionBar} tab.
	 * 
	 * @see #getTitle()
	 */
	public static final CharSequence TITLE = "Train";
	/**
	 * Jump size for the sliding window, maybe make this a setting someday.
	 */
	public static final int JUMP_SIZE = 100;
	// Settings
	/**
	 * Setting: {@link SettingsActivity#KEY_OUTPUT_DIR}
	 * 
	 * @see ClassifierService#getDirname()
	 */
	private static String sOutputDir;
	/**
	 * Setting {@link SettingsActivity#KEY_NUM_FOLDS}
	 */
	private static int sNumFolds;
	/**
	 * Setting: {@link Storage#KEY_CLASSIFIER_FILE}
	 */
	private static String sClassifierFile;
	/**
	 * Setting {@link Storage#KEY_TRAINING_FILE}
	 */
	private static String sTrainingFile;
	/**
	 * Setting: {@link Storage#KEY_TRAIN_INSTANCES}
	 */
	private static int sTrainInstances;
	/**
	 * Setting: {@link Storage#KEY_FEATURES}
	 */
	private static int sTrainedFeatures;
	/**
	 * Setting: {@link Storage#KEY_VALIDATION_LOG_FILE}
	 */
	private static String sValidationLogFile;
	/**
	 * Setting: {@link Storage#KEY_CLASSIFIER_TYPE}
	 */
	private static String sClassifierType;
	
	private SharedPreferences mPrefs;
	private SharedPreferences mPrefsClassifier;
	
	// Views
	private Button mBtnSelectFile;
	private Button mBtnSelectFeatures;
	private Button mBtnSaveFeatures;
	private Button mBtnTrain;
	private Button mBtnValidate;
	private Spinner mSpinWindowSize;
	private Spinner mSpinClassifier;
	private ProgressBar mProgressTraining;
	private ProgressBar mProgressExtract;
	private TextView mTxtTrainStatus;
	
	// Fields
	private final int mCompoundCheck = android.R.drawable.checkbox_on_background;
	private final int mCompoundUncheck = android.R.drawable.checkbox_off_background;
	private Drawable mCompoundAlert;
	
	private List<ClassifierEntry> mClassifiers;
	private FileFilter mArffFilter;
	private File[] mFiles = new File[0];
	private Feature[] mFeatures = new Feature[0];
	private Instances mCalculatedFeatures = null;
	private Classifier mClassifier = null;
	private String mEvaluation = null;
	@SuppressWarnings("rawtypes")
	private AsyncTask mTask = null;
	
	// Handlers
	private LocalBroadcastManager mBroadcastManager;
	private IntentFilter mServiceIntentFilter;
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent)
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "Received broadcast: " + intent);
			
			// Disable or enable train button when report service is started or stopped
			if(intent.getAction().equals(ReportFragment.BCAST_SERVICE_STARTED) ||
				intent.getAction().equals(ReportFragment.BCAST_SERVICE_STOPPED))
			{
				updateTrainEnabled();
			}
		}
	};
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		Bundle savedInstanceState)
	{
		if(LOCAL_LOGV) Log.v(LOGTAG, "View created.");
		// Just return the inflated layout, other initializations will be done when the host
		// activity is created
		return inflater.inflate(R.layout.fragment_train, container, false);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		if(LOCAL_LOGV) Log.v(LOGTAG, "Activity created.");
		
		mBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
		mServiceIntentFilter = new IntentFilter();
		mServiceIntentFilter.addAction(ReportFragment.BCAST_SERVICE_STARTED);
		mServiceIntentFilter.addAction(ReportFragment.BCAST_SERVICE_STOPPED);
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mPrefsClassifier = Storage.getClassifierPreferences(getActivity());
		readSettings();
		
		getWidgets(getView());
		
		final Drawable check =
			getResources().getDrawable(android.R.drawable.checkbox_off_background);
		mCompoundAlert = getResources().getDrawable(android.R.drawable.ic_dialog_alert);
		mCompoundAlert.setBounds(0, 0, check.getIntrinsicWidth(), check.getIntrinsicHeight());
		
		mArffFilter = new FileFilter() {
			@Override
			public boolean accept(File pathname)
			{
				if(!pathname.isFile())
					return false;
				return pathname.getName().endsWith(RecorderFragment.EXTENSION);
			}
		};
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
		mBtnValidate = (Button)v.findViewById(R.id.btnValidate);
		mBtnValidate.setOnClickListener(onBtnValidateClick);
		
		mTxtTrainStatus = (TextView)v.findViewById(R.id.txtTrainStatus);
		
		mSpinWindowSize = (Spinner)v.findViewById(R.id.spinWindowSize);
		if(mSpinWindowSize.getCount() > 1)
			mSpinWindowSize.setSelection(1);
		mSpinClassifier = (Spinner)v.findViewById(R.id.spinClassifier);
		initClassifiers();
		
		OnItemSelectedListener hideValidateListener = new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
			{
				setValidateVisible(false);
			}
			
			@Override
			public void onNothingSelected(AdapterView<?> parent)
			{
				setValidateVisible(false);
			}
		};
		
		mSpinClassifier.setOnItemSelectedListener(hideValidateListener);
		mSpinWindowSize.setOnItemSelectedListener(hideValidateListener);
		
		mProgressTraining = (ProgressBar)v.findViewById(R.id.progressTraining);
		mProgressExtract = (ProgressBar)v.findViewById(R.id.progressExtract);
	}
	
	/**
	 * Populates the classifier type spinner.
	 */
	private void initClassifiers()
	{
		mClassifiers = new ArrayList<ClassifierEntry>(4);
		mClassifiers.add(new ClassifierEntry(IBk.class));
		mClassifiers.add(new ClassifierEntry(NaiveBayes.class));
		mClassifiers.add(new ClassifierEntry(J48.class));
		
		ArrayAdapter<ClassifierEntry> adapter = new ArrayAdapter<ClassifierEntry>(
			getActivity(), android.R.layout.simple_spinner_item, mClassifiers);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinClassifier.setAdapter(adapter);
		mSpinClassifier.setSelection(0);
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		mBroadcastManager.unregisterReceiver(mBroadcastReceiver);
	}
	
	@Override
	public void onStop()
	{
		super.onStop();
		
		// Stop any running task when activity stops
		if(mTask != null)
			mTask.cancel(true);
		mTask = null;
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		mBroadcastManager.registerReceiver(mBroadcastReceiver, mServiceIntentFilter);
		updateTrainEnabled();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		
		// Save selected files and features, we wouldn't expect to lose that
		outState.putSerializable(STATE_FILES, mFiles);
		outState.putInt(STATE_FEATURES, Feature.getMask(mFeatures));
	}
	
	@Override
	public void onViewStateRestored(Bundle savedInstanceState)
	{
		super.onViewStateRestored(savedInstanceState);
		if(LOCAL_LOGV)
			Log.v(LOGTAG, "onViewStateRestored: savedInstanceState = " + savedInstanceState);
		
		if(savedInstanceState != null)
		{
			// Restore selected files and features, if possible
			Object[] files = (Object[])savedInstanceState.getSerializable(STATE_FILES);
			if(files != null)
			{
				try
				{
					mFiles = Arrays.copyOf(files, files.length, File[].class);
					if(LOCAL_LOGV) Log.v(LOGTAG, "onViewStateRestored: Files restored.");
				}
				catch(ArrayStoreException ex)
				{
					mFiles = new File[0];
					Log.w(LOGTAG, "Saved state for mFiles is of wrong class.", ex);
				}
			}
			else
				mFiles = new File[0];
			
			int features = savedInstanceState.getInt(STATE_FEATURES, 0);
			if(features != 0)
			{
				mFeatures = Feature.getFeatures(features);
				if(LOCAL_LOGV) Log.v(LOGTAG, "onViewStateRestored: Features restored.");
			}
			updateTrainEnabled();
			updateCheckButtons();
		}
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
	 * Sets all appropriate private (static) fields from the shared preferences.
	 */
	private void readSettings()
	{
		sOutputDir = mPrefs.getString(SettingsActivity.KEY_OUTPUT_DIR, "");
		sNumFolds = mPrefs.getInt(SettingsActivity.KEY_NUM_FOLDS, 4);
		sClassifierFile = mPrefsClassifier.getString(Storage.KEY_CLASSIFIER_FILE, "");
		sTrainingFile = mPrefsClassifier.getString(Storage.KEY_TRAINING_FILE, "");
		sTrainedFeatures = mPrefsClassifier.getInt(Storage.KEY_FEATURES, 0);
		sValidationLogFile = mPrefsClassifier.getString(Storage.KEY_VALIDATION_LOG_FILE, "");
		sClassifierType = mPrefsClassifier.getString(Storage.KEY_CLASSIFIER_TYPE, "");
		sTrainInstances = mPrefsClassifier.getInt(Storage.KEY_TRAIN_INSTANCES, 0);
	}
	
	/**
	 * Writes the settings for classifier file and type, training file and trained features.
	 */
	private void writeSettings()
	{
		mPrefsClassifier.edit()
			.putString(Storage.KEY_CLASSIFIER_FILE, sClassifierFile)
			.putString(Storage.KEY_TRAINING_FILE, sTrainingFile)
			.putInt(Storage.KEY_FEATURES, sTrainedFeatures)
			.putString(Storage.KEY_CLASSIFIER_TYPE, mClassifier.getClass().getSimpleName())
			.putString(Storage.KEY_VALIDATION_LOG_FILE, sValidationLogFile)
			.putString(Storage.KEY_CLASSIFIER_TYPE, sClassifierType)
			.putInt(Storage.KEY_TRAIN_INSTANCES, sTrainInstances)
			.apply();
	}
	
	/**
	 * Sets the states of the buttons and spinners according to the specified value.
	 * <p>
	 * Inputs are disabled if extraction or training is running.
	 */
	private void setViewStates(boolean extracting)
	{
		mBtnSelectFile.setEnabled(!extracting);
		mBtnSelectFeatures.setEnabled(!extracting);
		mSpinClassifier.setEnabled(!extracting);
		mSpinWindowSize.setEnabled(!extracting);
	}
	
	/**
	 * Enables or disables the train and extract features buttons according to the selected
	 * files, features, spinner items and report service state.
	 */
	private void updateTrainEnabled()
	{
		boolean enabled = mFiles.length > 0 &&
			mFeatures.length > 0 &&
			mSpinClassifier.getSelectedItem() != null &&
			mSpinWindowSize.getSelectedItem() != null &&
			!ClassifierService.isRunning();
		
		mBtnTrain.setEnabled(enabled);
		mBtnSaveFeatures.setEnabled(enabled);
	}
	
	/**
	 * Sets the compound drawables of the buttons depending on whether files or features are
	 * selected.
	 */
	private void updateCheckButtons()
	{
		mBtnSelectFile.setCompoundDrawablesWithIntrinsicBounds(
			(mFiles.length > 0) ? mCompoundCheck : mCompoundUncheck, 0, 0, 0);
		mBtnSelectFeatures.setCompoundDrawablesWithIntrinsicBounds(
			(mFeatures.length > 0) ? mCompoundCheck : mCompoundUncheck, 0, 0, 0);
	}
	
	/**
	 * Shows or hides the validate button.
	 * 
	 * @param visible if {@code true} the button will be shown, otherwise it will be hidden and
	 *        the calculated features and evaluation reset.
	 */
	private void setValidateVisible(boolean visible)
	{
		if(!visible)
		{
			mCalculatedFeatures = null;
			mEvaluation = null;
		}
		mBtnValidate.setVisibility(visible ? View.VISIBLE : View.GONE);
	}
	
	private OnClickListener onBtnSelectFileClick = new OnClickListener() {
		File[] allFiles;
		boolean[] selected;
		
		@Override
		public void onClick(View v)
		{
			// Check for readable external storage
			final String state = Environment.getExternalStorageState();
			if(!state.equals(Environment.MEDIA_MOUNTED) &&
				!state.equals(Environment.MEDIA_MOUNTED_READ_ONLY))
			{
				Toast.makeText(getActivity(), R.string.error_extstorage_read, Toast.LENGTH_LONG)
					.show();
				mBtnSelectFile.setCompoundDrawables(mCompoundAlert, null, null, null);
				return;
			}
			
			readSettings();
			final File dir = new File(Environment.getExternalStorageDirectory(), sOutputDir);
			if(!dir.exists() || !dir.isDirectory())
			{
				Toast.makeText(getActivity(), R.string.error_nodir, Toast.LENGTH_LONG).show();
				mBtnSelectFile.setCompoundDrawables(mCompoundAlert, null, null, null);
				return;
			}
			allFiles = dir.listFiles(mArffFilter);
			
			// Populate list of names and look for selected files
			String[] filenames = new String[allFiles.length];
			selected = new boolean[allFiles.length];
			for(int j = 0; j < allFiles.length; j++)
			{
				filenames[j] = allFiles[j].getName();
				for(int k = 0; k < mFiles.length; k++)
				{
					if(allFiles[j].equals(mFiles[k]))
					{
						selected[j] = true;
						break;
					}
				}
			}
			
			// Show selection or no-files dialog
			final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
			b.setTitle(R.string.btnSelectFile);
			
			if(allFiles.length > 0)
			{
				b.setPositiveButton(android.R.string.ok, filesSelectedListener);
				b.setNegativeButton(android.R.string.cancel, null);
				b.setMultiChoiceItems(filenames, selected, new OnMultiChoiceClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which, boolean isChecked)
					{
						selected[which] = isChecked;
					}
				});
			}
			else
			{
				b.setNeutralButton(android.R.string.ok, null);
				b.setMessage(R.string.noFiles);
				mFiles = new File[0];
				updateCheckButtons();
			}
			
			b.show();
		}
		
		DialogInterface.OnClickListener filesSelectedListener =
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					// Kill everything when new files are selected
					setValidateVisible(false);
					
					// User clicked OK, count the number of selected files
					int numSelected = 0;
					for(int j = 0; j < selected.length; j++)
					{
						if(selected[j])
							numSelected++;
					}
					
					// Save selected files in field and set button checkbox
					mFiles = new File[numSelected];
					if(numSelected > 0)
					{
						int idx = 0;
						for(int j = 0; j < selected.length; j++)
						{
							if(selected[j])
								mFiles[idx++] = allFiles[j].getAbsoluteFile();
						}
					}
					updateCheckButtons();
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
			
			// Populate the selected features array with the saved features
			for(int j = 0; j < selected.length; j++)
			{
				selected[j] = false;
				for(int k = 0; k < mFeatures.length; k++)
				{
					if(mFeatures[k] == allFeatures[j])
					{
						selected[j] = true;
						break;
					}
				}
			}
			
			// This listener removes feature selections when RAW is selected and removes the RAW
			// selection when any other feature is selected
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
					setValidateVisible(false);
					
					// Special case when raw is selected
					if(selected[rawIdx])
					{
						mFeatures = new Feature[] { Feature.RAW };
						updateCheckButtons();
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
					mFeatures = new Feature[numSelected];
					if(numSelected > 0)
					{
						int idx = 0;
						for(int j = 0; j < selected.length; j++)
						{
							if(selected[j])
								mFeatures[idx++] = allFeatures[j];
						}
					}
					updateCheckButtons();
					updateTrainEnabled();
				}
			};
		
		private void init()
		{
			// Get feature names an save index of RAW feature (should always be 0 anyway)
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
			// If features have already been calculated just save them, otherwise calculate them
			if(mCalculatedFeatures == null)
			{
				int windowSize = Integer.parseInt((String)mSpinWindowSize.getSelectedItem());
				FeatureExtractor fe =
					new FeatureExtractor(mFiles, mFeatures, windowSize, JUMP_SIZE);
				
				SaveFeaturesTask t = new SaveFeaturesTask();
				mTask = t;
				t.execute(fe);
			}
			else
				saveFeatures();
		}
	};
	
	private OnClickListener onBtnTrainClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			// Warn the user that training a new classifier will replace the current one
			final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
			b.setTitle(R.string.trainQuestion);
			b.setMessage(R.string.trainNote);
			b.setNegativeButton(R.string.no, null);
			b.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					// User wants to train, so train
					int windowSize = Integer.parseInt((String)mSpinWindowSize.getSelectedItem());
					FeatureExtractor fe =
						new FeatureExtractor(mFiles, mFeatures, windowSize, JUMP_SIZE);
					
					TrainClassifierTask t = new TrainClassifierTask();
					mTask = t;
					t.execute(fe);
				}
			});
			b.show();
		}
	};
	
	private OnClickListener onBtnValidateClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			// If the classifier has been evaluated show results, otherwise evaluate
			if(mEvaluation == null)
			{
				readSettings();
				final Bundle bu = new Bundle(4);
				bu.putInt(ValidateClassifierTask.KEY_NUM_FOLDS, sNumFolds);
				bu.putSerializable(ValidateClassifierTask.KEY_INSTANCES, mCalculatedFeatures);
				bu.putSerializable(ValidateClassifierTask.KEY_CLASSIFIER, mClassifier);
				
				// Warn the user that this may take some time
				final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
				b.setTitle(R.string.validationQuestion);
				b.setMessage(R.string.validateNote);
				b.setNegativeButton(R.string.no, null);
				b.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						// User wants to validate, obey minion!
						ValidateClassifierTask t = new ValidateClassifierTask();
						mTask = t;
						t.execute(bu);
					}
				});
				b.show();
			}
			else
				showEvaluation();
		}
	};
	
	/**
	 * Shows a dialog informing the user that an error occurred while performing a task.
	 * 
	 * @param ex the type of the {@link Exception} determines the message shown.
	 */
	private void showExceptionDialog(Exception ex)
	{
		AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
		b.setIcon(android.R.drawable.ic_dialog_alert);
		b.setTitle(R.string.error);
		b.setNeutralButton(android.R.string.ok, null);
		
		if(ex instanceof UnsupportedAttributeTypeException)
		{
			b.setMessage(
				getString(R.string.error_attributes, ex.getMessage()));
		}
		else if(ex instanceof FileNotFoundException)
			b.setMessage(R.string.error_nofile);
		else if(ex instanceof IOException)
			b.setMessage(R.string.error_io);
		else
			b.setMessage(R.string.error_generic);
		
		b.show();
	}
	
	/**
	 * Displays a dialog asking for a filename and writes the calculated features to this file.
	 */
	private void saveFeatures()
	{
		final EditText txt = new EditText(getActivity());
		final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
		b.setTitle(R.string.saveFeaturesQuestion);
		b.setMessage(R.string.saveFeaturesNote);
		b.setView(txt);
		b.setNegativeButton(android.R.string.cancel, null);
		b.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				// Check for writable external storage
				if(!Environment.getExternalStorageState().equals(
					Environment.MEDIA_MOUNTED))
				{
					Toast.makeText(getActivity(), R.string.error_extstorage,
						Toast.LENGTH_LONG).show();
					return;
				}
				
				readSettings();
				String name = txt.getText().toString();
				if(!name.endsWith(ArffLoader.FILE_EXTENSION))
					name += ArffLoader.FILE_EXTENSION;
				
				// Save file
				File out = new File(new File(Environment.getExternalStorageDirectory(),
					sOutputDir), name);
				if(out.exists())
					out.delete();
				ArffSaver saver = new ArffSaver();
				try
				{
					saver.setFile(out);
					saver.setInstances(mCalculatedFeatures);
					saver.writeBatch();
					Toast.makeText(getActivity(), R.string.fileSaved, Toast.LENGTH_LONG)
						.show();
				}
				catch(IOException ex)
				{
					Toast.makeText(getActivity(), R.string.error_io, Toast.LENGTH_LONG)
						.show();
				}
			}
		});
		b.show();
	}
	
	/**
	 * Extracts data from the evaluation of the classifier and shows it in a dialog.
	 */
	private void showEvaluation()
	{
		// Create a TextView with small monospace font
		final TextView txt = new TextView(getActivity());
		txt.setText(mEvaluation);
		txt.setTypeface(Typeface.MONOSPACE);
		txt.setTextSize(12f);
		txt.setTextColor(getResources().getColorStateList(android.R.color.primary_text_light));
		// Let the TextView scroll horizontally and vertically. Doesn't look very nice,
		// but it shouldn't bee needed much
		txt.setHorizontallyScrolling(true);
		txt.setMovementMethod(ScrollingMovementMethod.getInstance());
		// Set 12dp of padding left and right
		final int pixels = (int)(Resources.getSystem().getDisplayMetrics().density * 12);
		txt.setPadding(pixels, 0, pixels, 0);
		
		AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
		b.setTitle(R.string.validationResults);
		b.setNeutralButton(android.R.string.ok, null);
		b.setMessage(mClassifier.getClass().getSimpleName());
		b.setView(txt);
		b.show();
	}
	
	/**
	 * Builds a summary string for the specified evaluation.
	 * 
	 * @param e the {@link Evaluation} to summarize.
	 * @return a summary string, or {@code null} in case of an error.
	 */
	public String getEvaluationSummary(Evaluation e)
	{
		final StringBuilder sb = new StringBuilder();
		
		try
		{
			final int width = 12;
			final int after = 4;
			
			sb.append("=== Summary ===\n");
			sb.append(sNumFolds).append(" fold cross validation.\n");
			sb.append("Correctly Classified Instances\n");
			sb.append(Utils.doubleToString(e.correct(), width, after) + "    " +
				Utils.doubleToString(e.pctCorrect(), width, after) + " %\n");
			sb.append("Incorrectly Classified Instances\n");
			sb.append(Utils.doubleToString(e.incorrect(), width, after) + "    " +
				Utils.doubleToString(e.pctIncorrect(), width, after) + " %\n");
			
			sb.append("\nKappa statistic\n");
			sb.append(Utils.doubleToString(e.kappa(), width, after) + "\n");
			sb.append("Mean absolute error\n");
			sb.append(Utils.doubleToString(e.meanAbsoluteError(), width, after) + "\n");
			sb.append("Root mean squared error\n");
			sb.append(Utils.doubleToString(e.rootMeanSquaredError(), width, after) + "\n");
			sb.append("Relative absolute error\n");
			sb.append(Utils.doubleToString(e.relativeAbsoluteError(), width, after) + " %\n");
			sb.append("Root relative squared error\n");
			sb.append(Utils.doubleToString(e.rootRelativeSquaredError(), width, after) + " %\n");
			
			if(Utils.gr(e.unclassified(), 0))
			{
				sb.append("UnClassified Instances\n");
				sb.append(Utils.doubleToString(e.unclassified(), width, after) + "    " +
					Utils.doubleToString(e.pctUnclassified(), width, after) + " %\n");
			}
			sb.append("Total Number of Instances\n");
			sb.append(Utils.doubleToString(e.numInstances(), width, after));
			
			sb.append(e.toMatrixString("\n\nConfusion Matrix"));
		}
		catch(Exception ex)
		{
			return null;
		}
		
		return sb.toString();
	}
	
	/**
	 * {@link AsyncTask} that extracts the selected features from the selected files, merges and
	 * saves them.
	 * 
	 * @author Peter
	 */
	private class SaveFeaturesTask extends AsyncTask<FeatureExtractor, Void, FeatureExtractor>
	{
		private Exception mException = null;
		private int mOrientation;
		
		@Override
		protected FeatureExtractor doInBackground(FeatureExtractor... params)
		{
			try
			{
				params[0].extract();
				return params[0];
			}
			catch(Exception ex)
			{
				// Save exception to report it to the user
				ex.printStackTrace();
				mException = ex;
				return null;
			}
		}
		
		@Override
		protected void onPostExecute(FeatureExtractor result)
		{
			// Fragment was detached before the task completed, do nothing
			if(getActivity() == null)
				return;
			
			if(result == null)
			{
				if(mException != null)
					showExceptionDialog(mException);
				mCalculatedFeatures = null;
			}
			else
			{
				mCalculatedFeatures = result.getOutput();
				
				saveFeatures();
			}
			resetViews();
		}
		
		@Override
		protected void onPreExecute()
		{
			setViewStates(true);
			mBtnTrain.setEnabled(false);
			mProgressExtract.setVisibility(View.VISIBLE);
			mBtnSaveFeatures.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v)
				{
					SaveFeaturesTask.this.cancel(true);
				}
			});
			mBtnSaveFeatures.setText(android.R.string.cancel);
			
			// Don't let the screen rotate while a task is running
			mOrientation = getActivity().getRequestedOrientation();
			int currentOrientation = getResources().getConfiguration().orientation;
			getActivity().setRequestedOrientation(currentOrientation);
		}
		
		@Override
		protected void onCancelled(FeatureExtractor result)
		{
			if(getActivity() != null)
			{
				Toast.makeText(getActivity(), R.string.extractionCancelled, Toast.LENGTH_LONG)
					.show();
				resetViews();
			}
		}
		
		private void resetViews()
		{
			setViewStates(false);
			updateTrainEnabled();
			mProgressExtract.setVisibility(View.GONE);
			mBtnSaveFeatures.setOnClickListener(onBtnSaveFeaturesClick);
			mBtnSaveFeatures.setText(R.string.btnSaveFeatures);
			mTask = null;
			// Reset the requested orientation to the one before the task start
			getActivity().setRequestedOrientation(mOrientation);
		}
	}
	
	private class TrainClassifierTask extends AsyncTask<FeatureExtractor, Instances, Classifier>
	{
		private Exception mException = null;
		private int mOutputFeatures = 0;
		private ClassifierEntry mType = null;
		private int mOrientation;
		
		@Override
		protected Classifier doInBackground(FeatureExtractor... params)
		{
			// Save filenames of old serialized classifier and training data
			final String oldClassifierFile = sClassifierFile;
			final String oldTrainingFile = sTrainingFile;
			final String oldValidationLogFile = sValidationLogFile;
			
			try
			{
				FeatureExtractor fe = params[0];
				fe.extract();
				mOutputFeatures = fe.getOutputFeatures();
				
				if(isCancelled())
					return null;
				publishProgress(fe.getOutput());
				
				// TODO maybe set options for classifiers
				Classifier out = mType.type.newInstance();
				
				out.buildClassifier(fe.getOutput());
				
				if(isCancelled())
					return null;
				
				// Make new filenames and save info
				sClassifierFile = String.format("classifier-%X%s", out.hashCode(), ".bin");
				sTrainingFile = String.format("trainfile-%X%s",
					out.hashCode(), Instances.SERIALIZED_OBJ_FILE_EXTENSION + "z");
				sValidationLogFile = "";
				sClassifierType = mType.toString();
				sTrainInstances = fe.getNumInputInstances();
				
				// Serialize the classifier to internal storage
				OutputStream os =
					getActivity().openFileOutput(sClassifierFile, Context.MODE_PRIVATE);
				final ObjectOutputStream oos = new ObjectOutputStream(os);
				oos.writeObject(out);
				oos.close();
				
				if(isCancelled())
					return null;
				
				// Serialize training data to internal storage
				os = getActivity().openFileOutput(sTrainingFile, Context.MODE_PRIVATE);
				final SerializedInstancesSaver saver = new SerializedInstancesSaver();
				saver.setDestination(new GZIPOutputStream(os));
				saver.setInstances(fe.getOutput());
				saver.writeBatch();
				// writeBatch() closes the stream
				
				// Delete old files
				if(!oldClassifierFile.equals(sClassifierFile))
					getActivity().deleteFile(oldClassifierFile);
				if(!oldTrainingFile.equals(sTrainingFile))
					getActivity().deleteFile(oldTrainingFile);
				if(!oldValidationLogFile.isEmpty())
					getActivity().deleteFile(oldValidationLogFile);
				
				return out;
			}
			catch(Exception ex)
			{
				if(getActivity() != null)
				{
					// In case of an exception after one of the new files was saved, remove it
					if(!sClassifierFile.equals(oldClassifierFile))
						getActivity().deleteFile(sClassifierFile);
					if(!sTrainingFile.equals(oldTrainingFile))
						getActivity().deleteFile(sTrainingFile);
				}
				
				// Save exception to report it to the user
				ex.printStackTrace();
				mException = ex;
				return null;
			}
		}
		
		@Override
		protected void onProgressUpdate(Instances... values)
		{
			// Save calculated features and update status text
			mCalculatedFeatures = values[0];
			sTrainedFeatures = mOutputFeatures;
			mTxtTrainStatus.setText(R.string.statusTrain);
		}
		
		@Override
		protected void onPostExecute(Classifier result)
		{
			// Fragment was detached before the task completed, do nothing
			if(getActivity() == null)
				return;
			
			mClassifier = result;
			
			if(result == null)
			{
				// Restore old classifier and training data filenames in case of an error
				readSettings();
				
				if(mException != null)
					showExceptionDialog(mException);
			}
			else
			{
				// Activate the validate button and inform the user
				setValidateVisible(true);
				
				final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
				b.setTitle(R.string.trainingFinished);
				b.setMessage(R.string.trainingFinished_long);
				b.setNeutralButton(android.R.string.ok, null);
				b.show();
				
				writeSettings();
				mBroadcastManager.sendBroadcast(new Intent(ReportFragment.BCAST_NEW_CLASSIFIER));
			}
			resetViews();
		}
		
		@Override
		protected void onPreExecute()
		{
			if(ClassifierService.isRunning())
				cancel(true);
			
			// Disable inputs and change button to cancel
			setViewStates(true);
			setValidateVisible(false);
			mBtnSaveFeatures.setEnabled(false);
			mProgressTraining.setVisibility(View.VISIBLE);
			mBtnTrain.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v)
				{
					mTxtTrainStatus.setText(R.string.statusCancel);
					TrainClassifierTask.this.cancel(true);
				}
			});
			mBtnTrain.setText(android.R.string.cancel);
			
			// Set status text and show progress bar
			mTxtTrainStatus.setText(R.string.statusExtract);
			mTxtTrainStatus.setVisibility(View.VISIBLE);
			mType = (ClassifierEntry)mSpinClassifier.getSelectedItem();
			
			// Don't let the screen rotate while a task is running
			mOrientation = getActivity().getRequestedOrientation();
			int currentOrientation = getResources().getConfiguration().orientation;
			getActivity().setRequestedOrientation(currentOrientation);
		}
		
		@Override
		protected void onCancelled(Classifier result)
		{
			if(getActivity() != null)
			{
				Toast.makeText(getActivity(), R.string.trainingCancelled, Toast.LENGTH_LONG)
					.show();
				resetViews();
			}
		}
		
		private void resetViews()
		{
			setViewStates(false);
			updateTrainEnabled();
			mTxtTrainStatus.setVisibility(View.INVISIBLE);
			mProgressTraining.setVisibility(View.INVISIBLE);
			mBtnTrain.setOnClickListener(onBtnTrainClick);
			mBtnTrain.setText(R.string.btnTrain);
			mTask = null;
			// Reset the requested orientation to the one before the task start
			getActivity().setRequestedOrientation(mOrientation);
		}
	}
	
	private class ValidateClassifierTask extends AsyncTask<Bundle, Void, String>
	{
		public static final String KEY_NUM_FOLDS = "numFolds";
		public static final String KEY_INSTANCES = "instances";
		public static final String KEY_CLASSIFIER = "classifier";
		
		private Exception mException = null;
		private int mOrientation;
		
		@Override
		protected String doInBackground(Bundle... params)
		{
			try
			{
				// Extract classifier, feature data and number of folds from the bundle
				final Bundle b = params[0];
				final Classifier classifier = (Classifier)b.getSerializable(KEY_CLASSIFIER);
				final Instances instances = (Instances)b.getSerializable(KEY_INSTANCES);
				final int numFolds = b.getInt(KEY_NUM_FOLDS);
				
				// Prepare data for cross-validation
				instances.stratify(numFolds);
				Evaluation eval = new Evaluation(instances);
				
				// Validate
				for(int fold = 0; fold < numFolds; fold++)
				{
					if(isCancelled())
						return null;
					
					Instances train = instances.trainCV(numFolds, fold);
					Instances test = instances.testCV(numFolds, fold);
					Classifier run = Classifier.makeCopy(classifier);
					
					eval.setPriors(train);
					run.buildClassifier(train);
					for(int j = 0; j < test.numInstances(); j++)
						eval.evaluateModelOnceAndRecordPrediction(run, test.instance(j));
				}
				
				final String summary = getEvaluationSummary(eval);
				
				// There is no old validation log file since it is deleted when a classifier is
				// trained
				sValidationLogFile =
					String.format("validation-%X%s", classifier.hashCode(), ".txt");
				OutputStreamWriter out = new OutputStreamWriter(
					getActivity().openFileOutput(sValidationLogFile, Activity.MODE_PRIVATE));
				out.write(summary);
				out.close();
				
				return summary;
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
				mException = ex;
				return null;
			}
		}
		
		@Override
		protected void onPreExecute()
		{
			setViewStates(true);
			mBtnSaveFeatures.setEnabled(false);
			mBtnTrain.setEnabled(false);
			
			// Show progress bar and change button to cancel
			mTxtTrainStatus.setText(R.string.statusValidate);
			mTxtTrainStatus.setVisibility(View.VISIBLE);
			mProgressTraining.setVisibility(View.VISIBLE);
			mBtnValidate.setText(android.R.string.cancel);
			mBtnValidate.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v)
				{
					ValidateClassifierTask.this.cancel(true);
				}
			});
			
			// Don't let the screen rotate while a task is running
			mOrientation = getActivity().getRequestedOrientation();
			int currentOrientation = getResources().getConfiguration().orientation;
			getActivity().setRequestedOrientation(currentOrientation);
		}
		
		@Override
		protected void onPostExecute(String result)
		{
			// Fragment was detached before the task completed, do nothing
			if(getActivity() == null)
				return;
			
			mEvaluation = result;
			
			// If something went wrong inform the user, otherwise show results
			if(result == null)
			{
				if(mException != null)
					showExceptionDialog(mException);
			}
			else
			{
				writeSettings();
				showEvaluation();
			}
			resetViews();
		}
		
		@Override
		protected void onCancelled(String result)
		{
			if(getActivity() != null)
			{
				Toast.makeText(getActivity(), R.string.validationCancelled, Toast.LENGTH_LONG)
					.show();
				resetViews();
			}
		}
		
		private void resetViews()
		{
			setViewStates(false);
			updateTrainEnabled();
			
			mTxtTrainStatus.setVisibility(View.INVISIBLE);
			mProgressTraining.setVisibility(View.INVISIBLE);
			
			mBtnValidate.setText(R.string.btnValidate);
			mBtnValidate.setOnClickListener(onBtnValidateClick);
			mTask = null;
			// Reset the requested orientation to the one before the task start
			getActivity().setRequestedOrientation(mOrientation);
		}
	}
}
