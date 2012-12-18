package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
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
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends FragmentActivity implements ActionBar.TabListener
{
	// Constants
	public static final String LOGTAG = "MainActivity";
	public static final boolean LOCAL_LOGV = true;
	// Settings
	/**
	 * Setting: {@link SettingsActivity#KEY_OUTPUT_DIR}
	 * 
	 * @see ARFFRecorderService#getDirname()
	 */
	private static String sOutputDir;
	/**
	 * Setting: {@link SettingsActivity#KEY_LOG_FILENAME}
	 */
	private static String sLogFilename;
	
	private SharedPreferences mPrefs;
	
	// The PagerAdapter supplies the fragments to be displayed in the ViewPager
	private SectionsPagerAdapter mPagerAdapter;
	private ViewPager mViewPager;
	private MenuItem mMenuReport;
	
	private LocalBroadcastManager mBroadcastManager;
	private IntentFilter mServiceIntentFilter;
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
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
		// When swiping between different sections, select the corresponding tab
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position)
			{
				// Doesn't work when tabs are collapsed to a list (e.g. when in landscape), this
				// is an android bug
				actionBar.setSelectedNavigationItem(position);
			}
		});
		
		// For each of the sections in the app, add a tab to the action bar
		for(int i = 0; i < mPagerAdapter.getCount(); i++)
		{
			actionBar.addTab(
				actionBar.newTab().setText(mPagerAdapter.getPageTitle(i)).setTabListener(this));
		}
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}
	
	private void updateSettings()
	{
		sOutputDir = mPrefs.getString(SettingsActivity.KEY_OUTPUT_DIR, "");
		sLogFilename = mPrefs.getString(SettingsActivity.KEY_LOG_FILENAME, "");
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
		return true;
	}
	
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
			final File logfile = new File(new File(Environment.getExternalStorageDirectory(),
				sOutputDir), sLogFilename);
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
	 * Sets the icon of the report menu item according to the current {@link ClassifierService}
	 * status.
	 */
	private void updateMenuReportIcon()
	{
		if(mMenuReport == null)
			return;
		if(ClassifierService.isRunning())
			mMenuReport.setIcon(R.drawable.ic_action_report_on);
		else
			mMenuReport.setIcon(R.drawable.ic_action_report_off);
	}
	
	/**
	 * Reads text from the raw resource file with the specified ID.
	 * 
	 * @param id the ID of the file to read.
	 * @return the contents of the file as {@code String}, or {@code null} in case of an IO
	 *         error.
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
		}
		catch(IOException ex)
		{
			return null;
		}
		
		return sb.toString();
	}
	
	private class SectionsPagerAdapter extends FragmentPagerAdapter
	{
		AbstractTabFragment[] mTabs;
		
		public SectionsPagerAdapter(FragmentManager fm)
		{
			super(fm);
			mTabs = new AbstractTabFragment[3];
			mTabs[2] = new RecorderFragment();
			mTabs[1] = new TrainFragment();
			mTabs[0] = new ReportFragment();
		}
		
		@Override
		public Fragment getItem(int position)
		{
			return mTabs[position];
		}
		
		@Override
		public int getCount()
		{
			return 3;
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
