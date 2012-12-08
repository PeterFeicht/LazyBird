package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

public class MainActivity extends FragmentActivity implements ActionBar.TabListener
{
	// Constants
	public static final String LOGTAG = "MainActivity";
	public static final boolean LOCAL_LOGV = true;
	
	// The PagerAdapter supplies the fragments to be displayed in the ViewPager
	private SectionsPagerAdapter mPagerAdapter;
	private ViewPager mViewPager;
	
	private MenuItem mMenuReport;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		
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
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.activity_main, menu);
		
		// TODO enable report button in layout
		mMenuReport = menu.findItem(R.id.menu_report);
		mMenuReport.setOnMenuItemClickListener(onMenuReportClick);
		menu.findItem(R.id.menu_settings).setOnMenuItemClickListener(onMenuSettingsClick);
		menu.findItem(R.id.menu_help).setOnMenuItemClickListener(onMenuHelpClick);
		return true;
	}
	
	private OnMenuItemClickListener onMenuSettingsClick = new OnMenuItemClickListener() {
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
			// TODO implement reporting
			return false;
		}
	};
	
	private OnMenuItemClickListener onMenuHelpClick = new OnMenuItemClickListener() {
		@Override
		public boolean onMenuItemClick(MenuItem item)
		{
			final AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
			b.setIcon(android.R.drawable.ic_dialog_info);
			b.setTitle(R.string.menu_help);
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
		// TODO add animation
	}
	
	private class SectionsPagerAdapter extends FragmentPagerAdapter
	{
		RecorderFragment mRecorderFragment;
		TrainFragment mTrainFragment;
		ReportFragment mReportFragment;
		
		public SectionsPagerAdapter(FragmentManager fm)
		{
			super(fm);
			mRecorderFragment = new RecorderFragment();
			mTrainFragment = new TrainFragment();
			mReportFragment = new ReportFragment();
		}
		
		@Override
		public Fragment getItem(int position)
		{
			switch(position)
			{
				case 0:
					return mReportFragment;
				case 1:
					return mTrainFragment;
				case 2:
					return mRecorderFragment;
					
				default:
					return null;
			}
			
		}
		
		@Override
		public int getCount()
		{
			return 3;
		}
		
		@Override
		public CharSequence getPageTitle(int position)
		{
			switch(position)
			{
				case 0:
					return mReportFragment.getTitle();
				case 1:
					return mTrainFragment.getTitle();
				case 2:
					return mRecorderFragment.getTitle();
					
				default:
					return null;
			}
		}
	}
}
