package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ProgressBar;
import android.widget.TextView;
import at.jku.pci.lazybird.util.UserActivityView;
import at.jku.pervasive.sd12.actclient.CoordinatorClient;
import at.jku.pervasive.sd12.actclient.CoordinatorClient.UserState;
import at.jku.pervasive.sd12.actclient.GroupStateListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class LiveViewActivity extends Activity implements ActionBar.OnNavigationListener,
		GroupStateListener
{
	// Constants
	public static final String LOGTAG = "LiveViewActivity";
	public static final boolean LOCAL_LOGV = true;
	public static final int AUTO_UPDATE_DELAY = 500;
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
	private TextView mLblNoUsername;
	private TextView mLblCannotConnect;
	private ProgressBar mProgressServerUpdate;
	
	// Fields
	private ArrayAdapter<String> mViewUsers;
	private HashMap<String, UserActivityView> mUserViews;
	private CoordinatorClient mClient = null;
	// Handlers
	private Handler mHandler = new Handler();
	private Runnable mRunUpdateAges = new Runnable() {
		public void run()
		{
			// When there are no updates from the server in a long time, the displayed ages are
			// incremented until the server sends an update
			for(UserActivityView v : mUserViews.values())
				v.setAge(v.getAge() + AUTO_UPDATE_DELAY);
			// TODO check connection
			mHandler.postDelayed(mRunUpdateAges, AUTO_UPDATE_DELAY);
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_live_view);
		mUserViews = new HashMap<String, UserActivityView>(20);
		
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
	}
	
	private void getWidgets()
	{
		mUserContainer = (FlowLayout)findViewById(R.id.userContainer);
		
		mChkShowOffline = (CheckBox)findViewById(R.id.chkShowOffline);
		mChkShowOffline.setOnCheckedChangeListener(onChkShowOfflineCheckedChange);
		
		mLblNoUsername = (TextView)findViewById(R.id.lblNoUsername);
		mLblNoUsername.setVisibility(sReportUser.isEmpty() ? View.VISIBLE : View.GONE);
		
		mLblCannotConnect = (TextView)findViewById(R.id.lblCannotConnect);
		
		mProgressServerUpdate = (ProgressBar)findViewById(R.id.progressServerUpdate);
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		
		mLblCannotConnect.setVisibility(View.GONE);
		
		if(sReportUser.isEmpty())
		{
			mLblNoUsername.setVisibility(View.VISIBLE);
			mChkShowOffline.setEnabled(false);
			mUserContainer.removeAllViews();
		}
		else
		{
			mLblNoUsername.setVisibility(View.GONE);
			mChkShowOffline.setEnabled(true);
			
			connect();
		}
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		
		if(mClient != null)
			mClient.interrupt();
		mClient = null;
		mHandler.removeCallbacks(mRunUpdateAges);
	}
	
	/**
	 * Sets all appropriate private fields from the shared preferences.
	 */
	private void readSettings()
	{
		sReportServer = mPrefs.getString(SettingsActivity.KEY_REPORT_SERVER, "");
		sReportUser = mPrefs.getString(SettingsActivity.KEY_REPORT_USER, "");
	}
	
	/**
	 * Tries to connect to the server, setting lblCannotConnect to visible if that fails.
	 */
	private void connect()
	{
		String host;
		int port;
		
		if(sReportServer.isEmpty())
		{
			// Use default server and port in case something is missing
			host = CoordinatorClient.DEFAULT_SERVER_HOST;
			port = CoordinatorClient.DEFAULT_SERVER_PORT;
		}
		else if(sReportServer.contains(":"))
		{
			// Use specified server and port
			final int idx = sReportServer.indexOf(":");
			host = sReportServer.substring(0, idx);
			try
			{
				port = Integer.parseInt(
					sReportServer.substring(idx + 1, sReportServer.length()));
			}
			catch(NumberFormatException ex)
			{
				port = CoordinatorClient.DEFAULT_SERVER_PORT;
			}
		}
		else
		{
			// Port is missing, use default
			host = sReportServer;
			port = CoordinatorClient.DEFAULT_SERVER_PORT;
		}
		
		// Start reporting
		if(LOCAL_LOGV) Log.v(LOGTAG, "Connecting to server...");
		mClient = new CoordinatorClient(host, port, sReportUser);
		try
		{
			Thread.sleep(1000);
		}
		catch(InterruptedException ex)
		{
		}
		
		// In case the connection fails, the thread of the client stops; check
		if(mClient.isAlive())
		{
			mClient.addGroupStateListener(this);
			if(LOCAL_LOGV) Log.v(LOGTAG, "Connected.");
		}
		else
		{
			if(LOCAL_LOGV) Log.v(LOGTAG, "Connection failed.");
			mLblCannotConnect.setVisibility(View.VISIBLE);
			mClient = null;
		}
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
				for(UserActivityView v : mUserViews.values())
					v.setShowOffline(mChkShowOffline.isChecked());
			}
		};
	
	@Override
	public void groupStateChanged(UserState[] groupState)
	{
		if(groupState == null)
			return;
		
		mHandler.removeCallbacks(mRunUpdateAges);
		
		// We don't need to remove users from our list, since the server doesn't drop users
		final LinkedList<UserState> newUsers = new LinkedList<UserState>();
		for(UserState u : groupState)
		{
			UserActivityView v = mUserViews.get(u.getUserId());
			if(v != null)
			{
				v.setAge(u.getUpdateAge());
				v.setActivity(u.getActivity());
			}
			else
				newUsers.add(u);
		}
		
		// Create views for the new users and add them to our list
		final ArrayList<UserActivityView> newViews =
			new ArrayList<UserActivityView>(newUsers.size());
		for(UserState u : newUsers)
		{
			final UserActivityView v = new UserActivityView(this);
			v.setText(u.getUserId());
			v.setAge(u.getUpdateAge());
			v.setActivity(u.getActivity());
			v.setShowOffline(mChkShowOffline.isChecked());
			mUserViews.put(u.getUserId(), v);
			newViews.add(v);
		}
		
		runOnUiThread(new Runnable() {
			@Override
			public void run()
			{
				mProgressServerUpdate.setVisibility(View.GONE);
				for(UserActivityView v : newViews)
				{
					mUserContainer.addView(v);
					final String id = (String)v.getText();
					final int count = mViewUsers.getCount();
					// Add user to dropdown in sorted order
					int idx = 1;
					while(idx < count && mViewUsers.getItem(idx).compareTo(id) < 0)
						idx++;
					mViewUsers.insert(id, idx);
				}
				// TODO sort users
			}
		});
		
		// maybe remove offline users from view user list
		mHandler.postDelayed(mRunUpdateAges, AUTO_UPDATE_DELAY);
	}
}
