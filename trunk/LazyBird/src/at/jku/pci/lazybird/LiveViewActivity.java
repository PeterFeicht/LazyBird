package at.jku.pci.lazybird;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import at.jku.pci.lazybird.util.UserActivityView;
import at.jku.pervasive.sd12.actclient.ClassLabel;
import at.jku.pervasive.sd12.actclient.CoordinatorClient;
import at.jku.pervasive.sd12.actclient.GuiClient;
import at.jku.pervasive.sd12.actclient.GuiClient.GroupStateListener;
import at.jku.pervasive.sd12.actclient.GuiClient.UserState;
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
	private TextView mLblConnectionLost;
	private ProgressBar mProgressServerUpdate;
	
	// Fields
	private ArrayAdapter<String> mViewUsers;
	private HashMap<String, UserActivityView> mUserViews;
	private GuiClient mClient = null;
	private int mOfflineIndex = 0;
	// Handlers
	private Handler mHandler = new Handler();
	private Runnable mRunUpdateAges = new Runnable() {
		public void run()
		{
			if(mClient == null)
				return;
			
			// When there are no updates from the server in a long time, the displayed ages are
			// incremented until the server sends an update
			for(UserActivityView v : mUserViews.values())
				v.setAge(v.getAge() + AUTO_UPDATE_DELAY);
			sortOffline();
			
			if(mClient.isAlive())
				mHandler.postDelayed(mRunUpdateAges, AUTO_UPDATE_DELAY);
			else
				mLblConnectionLost.setVisibility(View.VISIBLE);
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_live_view);
		
		// Maintain a list of user views to update age and activity
		mUserViews = new HashMap<String, UserActivityView>(20);
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		readSettings();
		
		getWidgets();
		
		// Set up the action bar to show a dropdown list
		final ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		actionBar.setDisplayHomeAsUpEnabled(true);
		// The first element is always for the global view
		mViewUsers = new ArrayAdapter<String>(
			actionBar.getThemedContext(),
			android.R.layout.simple_list_item_1,
			android.R.id.text1);
		mViewUsers.add(getString(R.string.globalView));
		actionBar.setListNavigationCallbacks(mViewUsers, this);
	}
	
	/**
	 * Sets the fields for the views of this activity and registers listeners and stuff.
	 */
	private void getWidgets()
	{
		mUserContainer = (FlowLayout)findViewById(R.id.userContainer);
		
		mChkShowOffline = (CheckBox)findViewById(R.id.chkShowOffline);
		mChkShowOffline.setOnCheckedChangeListener(onChkShowOfflineCheckedChange);
		
		mLblNoUsername = (TextView)findViewById(R.id.lblNoUsername);
		mLblNoUsername.setVisibility(sReportUser.isEmpty() ? View.VISIBLE : View.GONE);
		
		mLblCannotConnect = (TextView)findViewById(R.id.lblCannotConnect);
		
		mLblConnectionLost = (TextView)findViewById(R.id.lblConnectionLost);
		mLblConnectionLost.setOnClickListener(onLblConnectionLostClick);
		
		mProgressServerUpdate = (ProgressBar)findViewById(R.id.progressServerUpdate);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.activity_live_view, menu);
		
		menu.findItem(R.id.liveViewLegend).setOnMenuItemClickListener(onMenuLiveViewLegendClick);
		
		return true;
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		
		mLblCannotConnect.setVisibility(View.GONE);
		
		// If there's no username set, inform the user, otherwise connect
		if(sReportUser.isEmpty())
		{
			mLblNoUsername.setVisibility(View.VISIBLE);
			mChkShowOffline.setEnabled(false);
			mUserContainer.removeAllViews();
			mProgressServerUpdate.setVisibility(View.GONE);
		}
		else
		{
			mLblNoUsername.setVisibility(View.GONE);
			mChkShowOffline.setEnabled(true);
			mProgressServerUpdate.setVisibility(View.VISIBLE);
			connect();
		}
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		
		// Terminate the connection when the activity loses focus
		mHandler.removeCallbacks(mRunUpdateAges);
		if(mClient != null)
			mClient.interrupt();
		mClient = null;
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
		int port = 1;
		
		if(sReportServer.isEmpty())
		{
			// Use default server and port in case something is missing
			host = CoordinatorClient.DEFAULT_SERVER_HOST;
			port += CoordinatorClient.DEFAULT_SERVER_PORT;
		}
		else if(sReportServer.contains(":"))
		{
			// Use specified server and port
			final int idx = sReportServer.indexOf(":");
			host = sReportServer.substring(0, idx);
			try
			{
				port += Integer.parseInt(
					sReportServer.substring(idx + 1, sReportServer.length()));
			}
			catch(NumberFormatException ex)
			{
				port += CoordinatorClient.DEFAULT_SERVER_PORT;
			}
		}
		else
		{
			// Port is missing, use default
			host = sReportServer;
			port += CoordinatorClient.DEFAULT_SERVER_PORT;
		}
		
		// Connect to the server, the GUI port is one above the client port
		if(LOCAL_LOGV) Log.v(LOGTAG, "Connecting to " + host + " on port " + port + "...");
		mClient = new GuiClient(host, port);
		
		mHandler.postDelayed(new Runnable() {
			int timeouts = 0;
			
			@Override
			public void run()
			{
				// In case the connection fails, the thread of the client stops; check
				if(mClient.isAlive())
				{
					mClient.setGroupStateListener(LiveViewActivity.this);
					if(LOCAL_LOGV) Log.v(LOGTAG, "Connected.");
				}
				else
				{
					// Check for a connection for 5 seconds, after that consider it failed
					if(timeouts++ > 20)
					{
						if(LOCAL_LOGV) Log.v(LOGTAG, "Connection failed.");
						mLblCannotConnect.setVisibility(View.VISIBLE);
						mProgressServerUpdate.setVisibility(View.GONE);
						mClient.interrupt();
						mClient = null;
					}
					else
						mHandler.postDelayed(this, 250);
				}
			}
		}, 250);
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
	
	private OnClickListener onLblConnectionLostClick = new OnClickListener() {
		@Override
		public void onClick(View v)
		{
			mLblConnectionLost.setVisibility(View.GONE);
			mProgressServerUpdate.setVisibility(View.VISIBLE);
			connect();
		}
	};
	
	private OnMenuItemClickListener onMenuLiveViewLegendClick = new OnMenuItemClickListener() {
		AlertDialog legend = null;
		
		@Override
		public boolean onMenuItemClick(MenuItem item)
		{
			// Build a legend and show it
			if(legend == null)
			{
				final AlertDialog.Builder b = new AlertDialog.Builder(LiveViewActivity.this);
				final ScrollView sv = new ScrollView(LiveViewActivity.this);
				final LinearLayout l = new LinearLayout(LiveViewActivity.this);
				final float dp = getResources().getDisplayMetrics().density;
				
				b.setTitle(R.string.menu_liveViewLegend);
				b.setPositiveButton(android.R.string.ok, null);
				
				l.setOrientation(LinearLayout.VERTICAL);
				// l.setGravity(Gravity.CENTER_HORIZONTAL);
				l.setPadding((int)(9 * dp), (int)(9 * dp), (int)(9 * dp), (int)(9 * dp));
				l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
				l.setDividerDrawable(getResources().getDrawable(R.drawable.layout_divider));
				final LayoutParams lp =
					new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				lp.gravity = Gravity.CENTER_HORIZONTAL;
				l.setLayoutParams(lp);
				
				UserActivityView v = new UserActivityView(LiveViewActivity.this);
				v.setText("sitting");
				v.setActivity(ClassLabel.sitting);
				l.addView(v);
				v = new UserActivityView(LiveViewActivity.this);
				v.setText("standing");
				v.setActivity(ClassLabel.standing);
				l.addView(v);
				v = new UserActivityView(LiveViewActivity.this);
				v.setText("walking");
				v.setActivity(ClassLabel.walking);
				l.addView(v);
				v = new UserActivityView(LiveViewActivity.this);
				v.setText("null");
				v.setActivity(null);
				l.addView(v);
				v = new UserActivityView(LiveViewActivity.this);
				v.setText(R.string.offline);
				v.setAge(UserActivityView.MAX_AGE + 1);
				l.addView(v);
				
				sv.addView(l);
				b.setView(sv);
				legend = b.create();
			}
			
			legend.show();
			return true;
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
			// Update ages and activities for all users, adding all new users to a list
			UserActivityView v = mUserViews.get(u.getUserId());
			if(v != null)
			{
				v.setAge(u.getUpdateAge());
				v.setActivity(u.getActivity());
			}
			else
				newUsers.add(u);
		}
		
		// Create views for the new users
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
		
		// Hide connecting progress and add new users to dropdown and activity list
		runOnUiThread(new Runnable() {
			@Override
			public void run()
			{
				mProgressServerUpdate.setVisibility(View.GONE);
				
				// Sort users before sorted insertion
				sortOffline();
				
				for(UserActivityView v : newViews)
				{
					final String id = (String)v.getText();
					
					// Add user to dropdown in sorted order
					final int count = mViewUsers.getCount();
					int idx = 1;
					while(idx < count && mViewUsers.getItem(idx).compareTo(id) < 0)
						idx++;
					mViewUsers.insert(id, idx);
					
					// Add user view in sorted order
					final int count2 = mUserContainer.getChildCount();
					idx = 0;
					while(idx < count2 && getUserViewAt(idx).compareTo(v) < 0)
						idx++;
					mUserContainer.addView(v, idx);
				}
			}
		});
		
		mHandler.postDelayed(mRunUpdateAges, AUTO_UPDATE_DELAY);
	}
	
	/**
	 * Sort the user views by offline status, moving offline views to the bottom.
	 * <p>
	 * {@link #mOfflineIndex} has the boundary between the offline and online users, so we only
	 * need to move online users beyond this index to the front and offline users before that to
	 * the end. Since no users are dropped by the server, this is the easiest method.
	 */
	protected void sortOffline()
	{
		final int count = mUserContainer.getChildCount();
		if(count < 2)
			return;
		
		// Move offline users to the bottom
		int j = 0;
		while(j < mOfflineIndex)
		{
			final UserActivityView v = getUserViewAt(j);
			if(v.isOffline())
			{
				int idx = mOfflineIndex;
				while(idx < count && getUserViewAt(idx).compareToText(v) < 0)
					idx++;
				
				mUserContainer.removeViewAt(j);
				mUserContainer.addView(v, idx - 1);
				mOfflineIndex--;
			}
			else
				j++;
		}
		
		// Move online users to the top
		for(j = mOfflineIndex; j < count; j++)
		{
			final UserActivityView v = getUserViewAt(j);
			if(!v.isOffline())
			{
				int idx = 0;
				while(idx < mOfflineIndex && getUserViewAt(idx).compareToText(v) < 0)
					idx++;
				
				mUserContainer.removeViewAt(j);
				mUserContainer.addView(v, idx);
				mOfflineIndex++;
			}
		}
	}
	
	/**
	 * Returns the view at the specified position in {@link #mUserContainer}, cast to a
	 * {@code UserActivityView}.
	 * 
	 * @param position the position at which to get the view from.
	 * @return the view at the specified position or null if the position does not exist within
	 *         the group
	 */
	private UserActivityView getUserViewAt(int position)
	{
		return (UserActivityView)mUserContainer.getChildAt(position);
	}
}
