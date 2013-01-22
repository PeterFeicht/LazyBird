package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class LiveViewActivity extends FragmentActivity implements ActionBar.OnNavigationListener
{
	// Constants
	public static final String LOGTAG = "LiveViewActivity";
	public static final boolean LOCAL_LOGV = true;
	public static final int AUTO_UPDATE_DELAY = 250;
	// Settings
	/**
	 * Setting: {@link SettingsActivity#KEY_REPORT_SERVER}
	 */
	private static String sReportServer = "";
	/**
	 * Setting: {@link SettingsActivity#KEY_REPORT_USER}
	 */
	private static String sReportUser = "";
	
	private SharedPreferences mPrefs;
	
	// Views
	private FlowLayout mUserContainer;
	private CheckBox mChkShowOffline;
	
	// Fields
	private ArrayAdapter<String> mViewUsers;
	// Handlers
	private Handler mHandler = new Handler();
	private Runnable mRunUpdateValues = new Runnable() {
		public void run()
		{
			// TODO Update ages
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_live_view);
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		readSettings();
		
		getWidgets();
		
		// Set up the action bar to show a dropdown list
		final ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		mViewUsers = new ArrayAdapter<String>(
			actionBar.getThemedContext(),
			android.R.layout.simple_list_item_1,
			android.R.id.text1);
		mViewUsers.add(getString(R.string.globalView));
		actionBar.setListNavigationCallbacks(mViewUsers, this);
		// TODO Add users for user dependent view
	}
	
	private void getWidgets()
	{
		mUserContainer = (FlowLayout)findViewById(R.id.userContainer);
		
		mChkShowOffline = (CheckBox)findViewById(R.id.chkShowOffline);
		mChkShowOffline.setOnCheckedChangeListener(onChkShowOfflineCheckedChange);
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
	}
	
	/**
	 * Sets all appropriate private fields from the shared preferences.
	 */
	private void readSettings()
	{
		sReportServer = mPrefs.getString(SettingsActivity.KEY_REPORT_SERVER, "");
		sReportUser = mPrefs.getString(SettingsActivity.KEY_REPORT_USER, "");
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		//getMenuInflater().inflate(R.menu.activity_live_view, menu);
		return true;
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
	
	@Override
	public boolean onNavigationItemSelected(int position, long id)
	{
		// TODO Change user for user dependent view
		if(LOCAL_LOGV) Log.v(LOGTAG, "onNavigationItemSelected: position = " + position);
		return true;
	}
	
	private OnCheckedChangeListener onChkShowOfflineCheckedChange =
		new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				// TODO Show/hide offline users
			}
		};
}
