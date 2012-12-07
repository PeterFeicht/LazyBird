package at.jku.pci.lazybird;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.J48;
import weka.core.Instances;
import weka.core.UnsupportedAttributeTypeException;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import weka.core.converters.SerializedInstancesSaver;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
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

public class TrainFragment extends Fragment
{
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
	public static final String LOGTAG = "TrainFragment";
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
	private TextView mTxtTrainStatus;
	
	private Drawable mCompoundCheck;
	private Drawable mCompoundUncheck;
	private Drawable mCompoundAlert;
	
	private List<ClassifierEntry> mClassifiers;
	private FileFilter mArffFilter;
	private File[] mFiles = new File[0];
	private Feature[] mFeatures = new Feature[0];
	private Instances mCalculatedFeatures = null;
	private Classifier mClassifier = null;
	@SuppressWarnings("rawtypes")
	private AsyncTask mTask = null;
	
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
		readSettings();
		
		initClassifiers();
		
		getWidgets(getView());
		
		// Get drawables for the select buttons, bounds are from a button in XML
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
		
		mTxtTrainStatus = (TextView)v.findViewById(R.id.txtTrainStatus);
		
		mSpinWindowSize = (Spinner)v.findViewById(R.id.spinWindowSize);
		if(mSpinWindowSize.getCount() > 1)
			mSpinWindowSize.setSelection(1);
		mSpinClassifier = (Spinner)v.findViewById(R.id.spinClassifier);
		
		ArrayAdapter<ClassifierEntry> adapter = new ArrayAdapter<TrainFragment.ClassifierEntry>(
			getActivity(), android.R.layout.simple_spinner_item, mClassifiers);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinClassifier.setAdapter(adapter);
		mSpinClassifier.setSelection(0);
		
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
	
	private void initClassifiers()
	{
		mClassifiers = new ArrayList<TrainFragment.ClassifierEntry>(4);
		mClassifiers.add(new ClassifierEntry(IBk.class));
		mClassifiers.add(new ClassifierEntry(NaiveBayes.class));
		mClassifiers.add(new ClassifierEntry(J48.class));
	}
	
	@Override
	public void onStop()
	{
		super.onStop();
		
		if(mTask != null)
			mTask.cancel(true);
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
	private void readSettings()
	{
		sOutputDir = mPrefs.getString(SettingsActivity.KEY_OUTPUT_DIR, "");
		sClassifierFile = mPrefsClassifier.getString(Storage.KEY_CLASSIFIER_FILE, "");
		sTrainingFile = mPrefsClassifier.getString(Storage.KEY_TRAINING_FILE, "");
		sTrainedFeatures = mPrefsClassifier.getInt(Storage.KEY_FEATURES, 0);
	}
	
	/**
	 * Writes the settings for classifier file and type, training file and trained features.
	 */
	private void writeSettings()
	{
		Editor e = mPrefsClassifier.edit();
		e.putString(Storage.KEY_CLASSIFIER_FILE, sClassifierFile);
		e.putString(Storage.KEY_TRAINING_FILE, sTrainingFile);
		e.putInt(Storage.KEY_FEATURES, sTrainedFeatures);
		e.putString(Storage.KEY_CLASSIFIER_TYPE, mClassifier.getClass().getSimpleName());
		e.apply();
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
			
			readSettings();
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
			int windowSize = Integer.parseInt((String)mSpinWindowSize.getSelectedItem());
			FeatureExtractor fe = new FeatureExtractor(mFiles, mFeatures, windowSize, JUMP_SIZE);
			
			TrainClassifierTask t = new TrainClassifierTask();
			mTask = t;
			t.execute(fe);
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
	
	private void saveFeatures()
	{
		final EditText e = new EditText(getActivity());
		final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
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
				
				readSettings();
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
		private Exception mException = null;
		
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
				mException = ex;
				return null;
			}
		}
		
		@Override
		protected void onPostExecute(FeatureExtractor result)
		{
			resetViews();
			
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
			mTask = null;
		}
	}
	
	private class TrainClassifierTask extends AsyncTask<FeatureExtractor, Instances, Classifier>
	{
		private Exception mException = null;
		private int mOutputFeatures = 0;
		private ClassifierEntry mType = null;
		
		@Override
		protected Classifier doInBackground(FeatureExtractor... params)
		{
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
				
				final String oldClassifierFile = sClassifierFile;
				final String oldTrainingFile = sTrainingFile;
				sClassifierFile = String.format("classifier-%X%s", out.hashCode(), ".bin");
				sTrainingFile = String.format("trainfile-%X%s",
					out.hashCode(), Instances.SERIALIZED_OBJ_FILE_EXTENSION);
				
				OutputStream os =
					getActivity().openFileOutput(sClassifierFile, Context.MODE_PRIVATE);
				final ObjectOutputStream oos = new ObjectOutputStream(os);
				oos.writeObject(out);
				oos.close();
				
				if(isCancelled())
					return null;
				
				os = getActivity().openFileOutput(sTrainingFile, Context.MODE_PRIVATE);
				final SerializedInstancesSaver saver = new SerializedInstancesSaver();
				saver.setDestination(os);
				saver.setInstances(fe.getOutput());
				saver.writeBatch();
				// saver.writeBatch() closes the stream
				
				if(!oldClassifierFile.equals(sClassifierFile))
					(new File(getActivity().getFilesDir(), oldClassifierFile)).delete();
				if(!oldTrainingFile.equals(sTrainingFile))
					(new File(getActivity().getFilesDir(), oldTrainingFile)).delete();
				
				return out;
			}
			catch(Exception ex)
			{
				mException = ex;
				return null;
			}
		}
		
		@Override
		protected void onProgressUpdate(Instances... values)
		{
			mCalculatedFeatures = values[0];
			sTrainedFeatures = mOutputFeatures;
			mTxtTrainStatus.setText(R.string.statusTrain);
		}
		
		@Override
		protected void onPostExecute(Classifier result)
		{
			resetViews();
			mClassifier = result;
			
			if(result == null)
			{
				readSettings();
				
				if(mException != null)
					showExceptionDialog(mException);
			}
			else
			{
				setValidateVisible(true);
				
				final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
				b.setTitle(R.string.trainingFinished);
				b.setMessage(R.string.trainingFinished_long);
				b.setNeutralButton(android.R.string.ok, null);
				b.show();
				
				writeSettings();
			}
		}
		
		@Override
		protected void onPreExecute()
		{
			// TODO stop reporting
			
			setViewStates(true);
			mBtnSaveFeatures.setEnabled(false);
			mProgressTraining.setVisibility(View.VISIBLE);
			mBtnTrain.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v)
				{
					TrainClassifierTask.this.cancel(true);
				}
			});
			mBtnTrain.setText(android.R.string.cancel);
			
			mTxtTrainStatus.setText(R.string.statusExtract);
			mTxtTrainStatus.setVisibility(View.VISIBLE);
			
			mType = (ClassifierEntry)mSpinClassifier.getSelectedItem();
		}
		
		@Override
		protected void onCancelled(Classifier result)
		{
			Toast.makeText(getActivity(), R.string.trainingCancelled, Toast.LENGTH_LONG)
				.show();
			resetViews();
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
		}
	}
}
