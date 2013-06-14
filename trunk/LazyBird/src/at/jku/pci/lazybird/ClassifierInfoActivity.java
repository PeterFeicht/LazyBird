package at.jku.pci.lazybird;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import at.jku.pci.lazybird.features.Feature;
import at.jku.pci.lazybird.util.Storage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class ClassifierInfoActivity extends Activity
{
	// Constants
	static final String LOGTAG = "ClassifierInfoActivity";
	static final boolean LOCAL_LOGV = true;
	// Settings
	/**
	 * Setting: {@link Storage#KEY_CLASSIFIER_FILE}
	 */
	private static String sClassifierFile;
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
	
	private SharedPreferences mPrefsClassifier;
	
	// Views
	private TextView mTxtClassifierType;
	private TextView mTxtTrainDate;
	private TextView mTxtTrainedFeatures;
	private TextView mTxtValidation;
	
	// Fields
	private Date mTrainDate;
	private String mValidation;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_classifier_info);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		
		mPrefsClassifier = Storage.getClassifierPreferences(this);
		readSettings();
		
		getWidgets();
		populate();
	}
	
	/**
	 * Sets the fields for the views of this activity and registers listeners and stuff.
	 */
	private void getWidgets()
	{
		mTxtClassifierType = (TextView)findViewById(R.id.txtClassifierType);
		mTxtTrainDate = (TextView)findViewById(R.id.txtTrainDate);
		mTxtTrainedFeatures = (TextView)findViewById(R.id.txtTrainedFeatures);
		mTxtValidation = (TextView)findViewById(R.id.txtValidation);
	}
	
	/**
	 * Sets all appropriate private (static) fields from the shared preferences.
	 */
	private void readSettings()
	{
		sClassifierFile = mPrefsClassifier.getString(Storage.KEY_CLASSIFIER_FILE, "");
		sTrainInstances = mPrefsClassifier.getInt(Storage.KEY_TRAIN_INSTANCES, 0);
		sTrainedFeatures = mPrefsClassifier.getInt(Storage.KEY_FEATURES, 0);
		sValidationLogFile = mPrefsClassifier.getString(Storage.KEY_VALIDATION_LOG_FILE, "");
		sClassifierType = mPrefsClassifier.getString(Storage.KEY_CLASSIFIER_TYPE, "");
	}
	
	/**
	 * Populates the views with data.
	 */
	private void populate()
	{
		if(sClassifierFile.isEmpty())
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "sClassifierFile is empty.");
			showNoClassifier();
			return;
		}
		
		File file = new File(getFilesDir(), sClassifierFile);
		
		if(file.exists())
		{
			// Classifier file exists, populate views
			long size = file.length();
			String unit = "B";
			if(size > 1024)
			{
				size /= 1024;
				unit = "KB";
			}
			if(size > 1024)
			{
				size /= 1024;
				unit = "MB";
			}
			mTxtClassifierType.setText(
				getString(R.string.txtClassifierType, sClassifierType, size, unit));
			
			mTrainDate = new Date(file.lastModified());
			final String date = SimpleDateFormat.getDateTimeInstance().format(mTrainDate);
			mTxtTrainDate.setText(getString(R.string.txtTrainDate, date, sTrainInstances));
			
			final String features = Arrays.toString(Feature.getFeatures(sTrainedFeatures));
			mTxtTrainedFeatures.setText(getString(R.string.txtTrainedFeatures, features));
			
			// Read validation log
			try
			{
				final InputStream is = openFileInput(sValidationLogFile);
				final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				final StringBuilder sb = new StringBuilder(768);
				String line = reader.readLine();
				
				while(line != null)
				{
					sb.append(line).append("\n");
					line = reader.readLine();
				}
				
				mValidation = sb.toString();
			}
			catch(IOException ex)
			{
				if(LOCAL_LOGV) Log.v(LOGTAG, "Erro reading validation log file.", ex);
				mValidation = getString(R.string.noValidation);
			}
			mTxtValidation.setText(mValidation);
		}
		else
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "sClassifierFile does not exist.");
			showNoClassifier();
		}
	}
	
	/**
	 * Hides all views except the classifier type and sets a message that there is no classifier.
	 */
	private void showNoClassifier()
	{
		// No classifier, hide everything and show message
		mTxtClassifierType.setText(R.string.noClassifier);
		mTxtTrainDate.setVisibility(View.GONE);
		mTxtTrainedFeatures.setVisibility(View.GONE);
		findViewById(R.id.scrollValidation).setVisibility(View.GONE);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case android.R.id.home:
				super.onBackPressed();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
