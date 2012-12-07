package at.jku.pci.lazybird;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import weka.core.Instances;
import weka.core.UnsupportedAttributeTypeException;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;
import at.jku.pci.lazybird.features.Feature;
import at.jku.pci.lazybird.features.FeatureExtractor;

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
	public static final int JUMP_SIZE = 100;
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
	private static String sClassifierFile;
	private static String sTrainingFile;
	private static int sTrainedFeatures;
	
	private SharedPreferences mPrefs;
	private SharedPreferences mPrefsClassifier;
	
	// Fields
	private Button mBtnSelectFile;
	private Button mBtnSelectFeatures;
	private Button mBtnSaveFeatures;
	private Button mBtnTrain;
	private Button mBtnValidate;
	private Spinner mSpinWindowSize;
	private Spinner mSpinClassifier;
	private ProgressBar mProgressTraining;
	private ProgressBar mProgressExtract;
	
	private Drawable mCompoundCheck;
	private Drawable mCompoundUncheck;
	private Drawable mCompoundAlert;
	
	private FileFilter mArffFilter;
	private File[] mFiles = new File[0];
	private Feature[] mFeatures = new Feature[0];
	private Instances mCalculatedFeatures = null;
	
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
		mPrefsClassifier = Storage.getClassifierPreferences(getActivity());
		updateSettings();
		
		getWidgets(getView());
		
		// Get drawables for the select buttons
		mCompoundUncheck =
			getResources().getDrawable(android.R.drawable.checkbox_off_background);
		mCompoundUncheck.setBounds(mBtnSelectFile.getCompoundDrawables()[0].copyBounds());
		mCompoundCheck = getResources().getDrawable(android.R.drawable.checkbox_on_background);
		mCompoundCheck.setBounds(mCompoundUncheck.copyBounds());
		mCompoundAlert = getResources().getDrawable(android.R.drawable.ic_dialog_alert);
		mCompoundAlert.setBounds(mCompoundUncheck.copyBounds());
		
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
		
		mSpinWindowSize = (Spinner)v.findViewById(R.id.spinWindowSize);
		if(mSpinWindowSize.getCount() > 1)
			mSpinWindowSize.setSelection(1);
		mSpinClassifier = (Spinner)v.findViewById(R.id.spinClassifier);
		
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
	
	@Override
	public void onPause()
	{
		super.onPause();
		
		// TODO cancel training or feature extraction
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		// FIXME save selections when rotating
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
		sClassifierFile = mPrefsClassifier.getString(Storage.KEY_CLASSIFIER_FILE, "");
		sTrainingFile = mPrefsClassifier.getString(Storage.KEY_TRAINING_FILE, "");
		sTrainedFeatures = mPrefsClassifier.getInt(Storage.KEY_FEATURES, 0);
	}
	
	/**
	 * Sets the states of the buttons and spinners according to the specified value.
	 * <p>
	 * Inputs are disabled if extraction or training is running.
	 * 
	 * @param running
	 */
	private void setViewStates(boolean extracting)
	{
		mBtnSelectFile.setEnabled(!extracting);
		mBtnSelectFeatures.setEnabled(!extracting);
		mSpinClassifier.setEnabled(!extracting);
		mSpinWindowSize.setEnabled(!extracting);
	}
	
	private void updateTrainEnabled()
	{
		boolean enabled = mFiles.length > 0 && mFeatures.length > 0;
		mBtnTrain.setEnabled(enabled);
		mBtnSaveFeatures.setEnabled(enabled);
	}
	
	private void setValidateVisible(boolean visible)
	{
		if(!visible)
			mCalculatedFeatures = null;
		mBtnValidate.setVisibility(visible ? View.VISIBLE : View.GONE);
	}
	
	private OnClickListener onBtnSelectFileClick = new OnClickListener() {
		File[] allFiles;
		boolean[] selected;
		
		@Override
		public void onClick(View v)
		{
			final String state = Environment.getExternalStorageState();
			if(!state.equals(Environment.MEDIA_MOUNTED) &&
				!state.equals(Environment.MEDIA_MOUNTED_READ_ONLY))
			{
				Toast.makeText(getActivity(), R.string.error_extstorage_read, Toast.LENGTH_LONG)
					.show();
				setLeftDrawable(mBtnSelectFile, mCompoundAlert);
				return;
			}
			
			updateSettings();
			final File dir = new File(Environment.getExternalStorageDirectory(), sOutputDir);
			
			if(!dir.exists() || !dir.isDirectory())
			{
				Toast.makeText(getActivity(), R.string.error_nodir, Toast.LENGTH_LONG).show();
				setLeftDrawable(mBtnSelectFile, mCompoundAlert);
				return;
			}
			
			allFiles = dir.listFiles(mArffFilter);
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
				for(int k = 0; k < mFiles.length; k++)
				{
					if(allFiles[j].equals(mFiles[k]))
					{
						selected[j] = true;
						break;
					}
				}
			}
			
			final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
			b.setTitle(R.string.btnSelectFile);
			
			if(allFiles.length > 0)
			{
				b.setPositiveButton(android.R.string.ok, filesSelectedListener);
				b.setNegativeButton(android.R.string.cancel, null);
				b.setMultiChoiceItems(filenames, selected, checkListener);
			}
			else
			{
				b.setNeutralButton(android.R.string.ok, null);
				b.setMessage(R.string.noFiles);
				setLeftDrawable(mBtnSelectFile, mCompoundUncheck);
			}
			
			b.show();
		}
		
		DialogInterface.OnClickListener filesSelectedListener =
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					if(which != AlertDialog.BUTTON_POSITIVE)
						return;
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
						setLeftDrawable(mBtnSelectFile, mCompoundCheck);
					}
					else
					{
						setLeftDrawable(mBtnSelectFile, mCompoundUncheck);
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
				for(int k = 0; k < mFeatures.length; k++)
				{
					if(mFeatures[k] == allFeatures[j])
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
					setValidateVisible(false);
					
					if(selected[rawIdx])
					{
						mFeatures = new Feature[] { Feature.RAW };
						setLeftDrawable(mBtnSelectFeatures, mCompoundCheck);
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
						setLeftDrawable(mBtnSelectFeatures, mCompoundCheck);
					}
					else
					{
						setLeftDrawable(mBtnSelectFeatures, mCompoundUncheck);
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
			if(mCalculatedFeatures == null)
			{
				int windowSize = Integer.parseInt((String)mSpinWindowSize.getSelectedItem());
				FeatureExtractor fe =
					new FeatureExtractor(mFiles, mFeatures, windowSize, JUMP_SIZE);
				
				(new SaveFeaturesTask()).execute(fe);
			}
			else
				saveFeatures();
		}
	};
	
	private OnClickListener onBtnTrainClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			// TODO implement training
		}
	};
	
	private OnClickListener onBtnValidateClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			// TODO implement validation
			
		}
	};
	
	private void setLeftDrawable(Button btn, Drawable d)
	{
		btn.setCompoundDrawables(d, null, null, null);
	}
	
	private void saveFeatures()
	{
		final EditText e = new EditText(getActivity());
		AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
		b.setTitle(R.string.btnSaveFeatures);
		b.setMessage(R.string.saveFeaturesNote);
		b.setView(e);
		b.setNegativeButton(android.R.string.cancel, null);
		b.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				if(!Environment.getExternalStorageState().equals(
					Environment.MEDIA_MOUNTED))
				{
					Toast.makeText(getActivity(), R.string.error_extstorage,
						Toast.LENGTH_LONG).show();
					return;
				}
				
				updateSettings();
				String name = e.getText().toString();
				if(!name.endsWith(ArffLoader.FILE_EXTENSION))
					name += ArffLoader.FILE_EXTENSION;
				
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
	
	private class SaveFeaturesTask extends AsyncTask<FeatureExtractor, Void, FeatureExtractor>
	{
		private FeatureExtractor mExtractor = null;
		private Exception mException = null;
		
		@Override
		protected FeatureExtractor doInBackground(FeatureExtractor... params)
		{
			mExtractor = params[0];
			
			try
			{
				mExtractor.extract();
			}
			catch(UnsupportedAttributeTypeException ex)
			{
				mException = ex;
				return null;
			}
			catch(IOException ex)
			{
				mException = ex;
				return null;
			}
			
			return mExtractor;
		}
		
		@Override
		protected void onPostExecute(FeatureExtractor result)
		{
			resetViews();
			
			if(result == null)
			{
				if(mException != null)
				{
					AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
					b.setIcon(android.R.drawable.ic_dialog_alert);
					b.setTitle(R.string.error);
					b.setNeutralButton(android.R.string.ok, null);
					
					if(mException instanceof UnsupportedAttributeTypeException)
					{
						b.setMessage(
							getString(R.string.error_attributes, mException.getMessage()));
					}
					else if(mException instanceof FileNotFoundException)
						b.setMessage(R.string.error_nofile);
					else if(mException instanceof IOException)
						b.setMessage(R.string.error_io);
					else
						b.setMessage(R.string.error_generic);
					
					b.show();
				}
			}
			else
			{
				mCalculatedFeatures = result.getOutput();
				
				saveFeatures();
			}
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
		}
		
		@Override
		protected void onCancelled(FeatureExtractor result)
		{
			Toast.makeText(getActivity(), R.string.extractionCancelled, Toast.LENGTH_LONG)
				.show();
			resetViews();
		}
		
		private void resetViews()
		{
			setViewStates(false);
			updateTrainEnabled();
			mProgressExtract.setVisibility(View.GONE);
			mBtnSaveFeatures.setOnClickListener(onBtnSaveFeaturesClick);
			mBtnSaveFeatures.setText(R.string.btnSaveFeatures);
		}
	}
}
