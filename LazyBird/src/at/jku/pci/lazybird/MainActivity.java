package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewPager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.TextView;

public class MainActivity extends FragmentActivity implements ActionBar.TabListener
{
	// Constants
	public static final String LOGTAG = "MainActivity";
	public static final boolean LOCAL_LOGV = true;
	
	// The PagerAdapter supplies the fragments to be displayed in the ViewPager
	private SectionsPagerAdapter mPagerAdapter;
	private ViewPager mViewPager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
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
		menu.findItem(R.id.menu_report).setOnMenuItemClickListener(onMenuReportClick);
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
			// TODO Auto-generated method stub
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
				case 1:
					b.setMessage(R.string.helpTrain);
					break;
				case 2:
					b.setMessage(R.string.helpRecorder);
					break;
				
				default:
					b.setMessage("");
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
	
	public class SectionsPagerAdapter extends FragmentPagerAdapter
	{
		private RecorderFragment mRecorderFragment;
		private TrainFragment mTrainFragment;
		
		public SectionsPagerAdapter(FragmentManager fm)
		{
			super(fm);
			mRecorderFragment = new RecorderFragment();
			mTrainFragment = new TrainFragment();
		}
		
		@Override
		public Fragment getItem(int position)
		{
			switch(position)
			{
				case 1:
					return mTrainFragment;
				case 2:
					return mRecorderFragment;
					
				default:
					Fragment fragment = new DummySectionFragment();
					Bundle args = new Bundle();
					args.putInt(DummySectionFragment.ARG_SECTION_NUMBER, position + 1);
					fragment.setArguments(args);
					return fragment;
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
				case 1:
					return mTrainFragment.getTitle();
				case 2:
					return mRecorderFragment.getTitle();
					
				default:
					return Integer.toString(position + 1);
			}
		}
	}
	
	/**
	 * A dummy fragment representing a section of the app, but that simply displays dummy text.
	 */
	public static class DummySectionFragment extends Fragment
	{
		/**
		 * The fragment argument representing the section number for this fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";
		
		public DummySectionFragment()
		{
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState)
		{
			// Create a new TextView and set its text to the fragment's section number argument
			// value
			TextView textView = new TextView(getActivity());
			textView.setGravity(Gravity.CENTER);
			textView.setText(Integer.toString(getArguments().getInt(ARG_SECTION_NUMBER)));
			return textView;
		}
	}
	
}