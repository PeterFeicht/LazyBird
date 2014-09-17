package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import at.jku.pci.lazybird.util.Storage;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import weka.core.converters.SerializedInstancesLoader;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

public class MainActivity extends FragmentActivity implements ActionBar.TabListener
{
	// Constants
	static final String LOGTAG = "MainActivity";
	static final boolean LOCAL_LOGV = true;
	// Settings
	/**
	 * Setting: {@link SettingsActivity#KEY_OUTPUT_DIR}
	 * 
	 * @see ARFFRecorderService#getDirname()
	 */
	static String sOutputDir;
	/**
	 * Setting: {@link SettingsActivity#KEY_LOG_FILENAME}
	 */
	static String sLogFilename;
	/**
	 * Setting {@link Storage#KEY_TRAINING_FILE}
	 */
	static String sTrainingFile;
	
	private SharedPreferences mPrefs;
	
	// The PagerAdapter supplies the fragments to be displayed in the ViewPager
	SectionsPagerAdapter mPagerAdapter;
	ViewPager mViewPager;
	MenuItem mMenuReport;
	
	private LocalBroadcastManager mBroadcastManager;
	private IntentFilter mServiceIntentFilter;
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "Received broadcast: " + intent);
			
			// Update the icon of the report button on the action bar when the service starts
			if(intent.getAction().equals(ReportFragment.BCAST_SERVICE_STOPPED) ||
					intent.getAction().equals(ReportFragment.BCAST_SERVICE_STARTED))
			{
				updateMenuReportIcon();
			}
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		updateSettings();
		
		mBroadcastManager = LocalBroadcastManager.getInstance(this);
		mServiceIntentFilter = new IntentFilter();
		mServiceIntentFilter.addAction(ReportFragment.BCAST_SERVICE_STARTED);
		mServiceIntentFilter.addAction(ReportFragment.BCAST_SERVICE_STOPPED);
		
		// Set up the action bar
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		
		mPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
		mViewPager = (ViewPager)findViewById(R.id.pager);
		mViewPager.setAdapter(mPagerAdapter);
		mViewPager.setOffscreenPageLimit(2);
		// When swiping between different sections, select the corresponding tab
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position)
			{
				// Doesn't work when tabs are collapsed to a list (when in landscape), this is an Android bug
				actionBar.setSelectedNavigationItem(position);
			}
		});
		
		// For each of the sections in the app, add a tab to the action bar
		for(int i = 0; i < mPagerAdapter.getCount(); i++)
		{
			actionBar.addTab(actionBar.newTab().setText(mPagerAdapter.getPageTitle(i)).setTabListener(this));
		}
		
		// TTS outputs on the music stream so we want to control that one
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}
	
	protected void updateSettings()
	{
		sOutputDir = mPrefs.getString(SettingsActivity.KEY_OUTPUT_DIR, "");
		sLogFilename = mPrefs.getString(SettingsActivity.KEY_LOG_FILENAME, "");
		sTrainingFile = Storage.getClassifierPreferences(this).getString(Storage.KEY_TRAINING_FILE, "");
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		mBroadcastManager.unregisterReceiver(mBroadcastReceiver);
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		updateMenuReportIcon();
		mBroadcastManager.registerReceiver(mBroadcastReceiver, mServiceIntentFilter);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.activity_main, menu);
		
		mMenuReport = menu.findItem(R.id.menu_report);
		mMenuReport.setOnMenuItemClickListener(onMenuReportClick);
		updateMenuReportIcon();
		menu.findItem(R.id.menu_settings).setOnMenuItemClickListener(onMenuSettingsClick);
		menu.findItem(R.id.menu_help).setOnMenuItemClickListener(onMenuHelpClick);
		menu.findItem(R.id.menu_showlog).setOnMenuItemClickListener(onMenuShowlogClick);
		menu.findItem(R.id.menu_about).setOnMenuItemClickListener(onMenuAboutClick);
		menu.findItem(R.id.menu_showClassifierInfo).setOnMenuItemClickListener(onMenuShowClassifierInfo);
		menu.findItem(R.id.menu_saveTrainingData).setOnMenuItemClickListener(onMenuSaveTrainingDataClick);
		menu.findItem(R.id.menu_liveView).setOnMenuItemClickListener(onMenuLiveViewClick);
		return true;
	}
	
	private OnMenuItemClickListener onMenuSaveTrainingDataClick = new OnMenuItemClickListener() {
		@Override
		public boolean onMenuItemClick(MenuItem item)
		{
			saveTrainData();
			return true;
		}
	};
	
	private OnMenuItemClickListener onMenuShowClassifierInfo = new OnMenuItemClickListener() {
		@Override
		public boolean onMenuItemClick(MenuItem item)
		{
			startActivity(new Intent(MainActivity.this, ClassifierInfoActivity.class));
			return true;
		}
	};
	
	private OnMenuItemClickListener onMenuLiveViewClick = new OnMenuItemClickListener() {
		@Override
		public boolean onMenuItemClick(MenuItem item)
		{
			startActivity(new Intent(MainActivity.this, LiveViewActivity.class));
			return true;
		}
	};
	
	private OnMenuItemClickListener onMenuAboutClick = new OnMenuItemClickListener() {
		@Override
		public boolean onMenuItemClick(MenuItem item)
		{
			AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
			b.setTitle(R.string.menu_about);
			b.setIcon(R.drawable.ic_launcher);
			
			CharSequence text = readRawTextFile(R.raw.about);
			if(text == null)
				text = "Oops!";
			text = Html.fromHtml((String)text);
			
			b.setMessage(text);
			b.setPositiveButton(android.R.string.ok, null);
			b.show();
			return true;
		}
	};
	
	private OnMenuItemClickListener onMenuShowlogClick = new OnMenuItemClickListener() {
		@Override
		public boolean onMenuItemClick(MenuItem item)
		{
			updateSettings();
			
			if(sLogFilename.isEmpty())
				return true;
			final File logfile = new File(
					new File(Environment.getExternalStorageDirectory(), sOutputDir), sLogFilename);
			if(logfile.isFile())
			{
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setDataAndType(Uri.fromFile(logfile), "text/plain");
				startActivity(i);
			}
			else
			{
				Toast.makeText(MainActivity.this, R.string.fileNotFound, Toast.LENGTH_SHORT)
					.show();
			}
			return true;
		}
	};
	
	private OnMenuItemClickListener onMenuSettingsClick = new OnMenuItemClickListener() {
		@Override
		public boolean onMenuItemClick(MenuItem item)
		{
			startActivity(new Intent(MainActivity.this, SettingsActivity.class));
			return true;
		}
	};
	
	private OnMenuItemClickListener onMenuReportClick = new OnMenuItemClickListener() {
		@Override
		public boolean onMenuItemClick(MenuItem item)
		{
			if(ClassifierService.isRunning())
				mPagerAdapter.getReportFragment().stopService();
			else
				mPagerAdapter.getReportFragment().startService();
			
			return true;
		}
	};
	
	private OnMenuItemClickListener onMenuHelpClick = new OnMenuItemClickListener() {
		@Override
		public boolean onMenuItemClick(MenuItem item)
		{
			// Display an appropriate help message for the current tab
			final AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
			b.setNeutralButton(android.R.string.ok, null);
			
			switch(getActionBar().getSelectedNavigationIndex())
			{
				case 0:
					b.setMessage(R.string.helpReport);
					break;
				case 1:
					b.setMessage(R.string.helpTrain);
					break;
				case 2:
					b.setMessage(R.string.helpRecorder);
					break;
				
				default:
					b.setMessage("Oops");
					break;
			}
			
			b.show();
			return true;
		}
	};
	
	@Override
	public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction)
	{
		// When the given tab is selected, switch to the corresponding page in the ViewPager
		mViewPager.setCurrentItem(tab.getPosition());
	}
	
	@Override
	public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction)
	{
	}
	
	@Override
	public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction)
	{
	}
	
	/**
	 * Sets the icon of the report menu item according to the current {@link ClassifierService} status.
	 */
	void updateMenuReportIcon()
	{
		if(mMenuReport == null)
			return;
		if(ClassifierService.isRunning())
		{
			mMenuReport.setIcon(R.drawable.ic_action_report_on);
			mMenuReport.setTitle(R.string.menu_report_on);
		}
		else
		{
			mMenuReport.setIcon(R.drawable.ic_action_report_off);
			mMenuReport.setTitle(R.string.menu_report_off);
		}
	}
	
	/**
	 * Displays a dialog asking for a filename and writes the calculated features to this file.
	 */
	void saveTrainData()
	{
		final EditText txt = new EditText(this);
		final AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setTitle(R.string.enterFilename);
		b.setMessage(R.string.saveTrainingData);
		b.setView(txt);
		b.setNegativeButton(android.R.string.cancel, null);
		b.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				// Check for writable external storage
				if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
				{
					Toast.makeText(MainActivity.this, R.string.error_extstorage, Toast.LENGTH_SHORT)
						.show();
					return;
				}
				
				updateSettings();
				String name = txt.getText().toString();
				if(!name.endsWith(ArffLoader.FILE_EXTENSION))
					name += ArffLoader.FILE_EXTENSION;
				
				File out = new File(new File(Environment.getExternalStorageDirectory(), sOutputDir), name);
				if(out.exists())
					out.delete();
				
				final ArffSaver saver = new ArffSaver();
				try
				{
					// Read training data from internal storage, decompressing it if necessary
					final InputStream is = openFileInput(sTrainingFile);
					final SerializedInstancesLoader loader = new SerializedInstancesLoader();
					if(sTrainingFile.endsWith("z"))
						loader.setSource(new GZIPInputStream(is));
					else
						loader.setSource(is);
					
					// Save instances, output stream is closed by writeBatch()
					final Instances i = loader.getDataSet();
					is.close();
					saver.setFile(out);
					saver.setInstances(i);
					saver.writeBatch();
					Toast.makeText(MainActivity.this, R.string.fileSaved, Toast.LENGTH_SHORT)
						.show();
				}
				catch(IOException ex)
				{
					Toast.makeText(MainActivity.this, R.string.error_io, Toast.LENGTH_LONG)
						.show();
				}
			}
		});
		b.show();
	}
	
	/**
	 * Reads text from the raw resource file with the specified ID.
	 * 
	 * @param id the ID of the file to read.
	 * @return the contents of the file as {@code String}, or {@code null} in case of an IO error.
	 * @exception NotFoundException if the resource ID is not found.
	 */
	public String readRawTextFile(int id)
	{
		InputStream is = getResources().openRawResource(id);
		BufferedReader r = new BufferedReader(new InputStreamReader(is));
		
		StringBuilder sb = new StringBuilder();
		try
		{
			String line = r.readLine();
			while(line != null)
			{
				sb.append(line);
				sb.append("\n");
				line = r.readLine();
			}
			r.close();
		}
		catch(IOException ex)
		{
			try
			{
				r.close();
			}
			catch(IOException foo)
			{
				// Program failed to fail, what do we do now?
			}
			return null;
		}
		
		return sb.toString();
	}
	
	private class SectionsPagerAdapter extends FragmentPagerAdapter
	{
		private static final int TAB_COUNT = 3;
		AbstractTabFragment[] mTabs;
		
		public SectionsPagerAdapter(FragmentManager fm)
		{
			super(fm);
			
			// Fill array with newly created Fragments, see instantiateItem
			mTabs = new AbstractTabFragment[TAB_COUNT];
			mTabs[2] = new RecorderFragment();
			mTabs[1] = new TrainFragment();
			mTabs[0] = new ReportFragment();
		}
		
		@Override
		public Object instantiateItem(ViewGroup container, int position)
		{
			// When no cached Fragment is present, the super call returns the item returned by getItem. When
			// a cached Fragment is present, this is returned and stored in the array to avoid working with
			// the newly created but detached Fragments.
			AbstractTabFragment tab = (AbstractTabFragment)super.instantiateItem(container, position);
			mTabs[position] = tab;
			return tab;
		}
		
		@Override
		public Fragment getItem(int position)
		{
			return mTabs[position];
		}
		
		@Override
		public int getCount()
		{
			return TAB_COUNT;
		}
		
		@Override
		public CharSequence getPageTitle(int position)
		{
			return mTabs[position].getTitle();
		}
		
		public ReportFragment getReportFragment()
		{
			return (ReportFragment)mTabs[0];
		}
	}
}
