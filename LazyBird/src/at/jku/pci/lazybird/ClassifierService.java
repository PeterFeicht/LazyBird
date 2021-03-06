package at.jku.pci.lazybird;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;
import at.jku.pci.lazybird.features.Feature;
import at.jku.pci.lazybird.features.FeatureExtractor;
import at.jku.pci.lazybird.features.SlidingWindow;
import at.jku.pci.lazybird.features.SlidingWindow.WindowListener;
import at.jku.pci.lazybird.features.TimeInstance;
import at.jku.pci.lazybird.features.UserActivities;
import at.jku.pervasive.sd12.actclient.ClassLabel;
import at.jku.pervasive.sd12.actclient.CoordinatorClient;
import at.jku.pervasive.sd12.actclient.CoordinatorClient.UserState;
import at.jku.pervasive.sd12.actclient.GroupStateListener;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

public class ClassifierService extends Service implements SensorEventListener,
		WindowListener<TimeInstance>, GroupStateListener
{
	private static ClassifierService sInstance = null;
	
	static final String LOGTAG = "ClassifierService";
	static final boolean LOCAL_LOGV = true;
	
	/**
	 * Format string to use for log entries, formatted by {@link SimpleDateFormat} and
	 * {@link String#format(String, Object...)} with one string argument.
	 */
	public static final String LOG_FORMAT = "yyyy-MM-dd HH:mm:ss' - %s'";
	
	/**
	 * Format string to use for short log entries, formatted by {@link SimpleDateFormat} and
	 * {@link String#format(String, Object...)} with one string argument.
	 */
	public static final String LOG_FORMAT_SHORT = "HH:mm' - %s'";
	
	/**
	 * Intent for the service.
	 */
	public static final String CLASSIFIER_SERVICE = "at.jku.pci.lazybird.CLASSIFIER_SERVICE";
	/**
	 * The default server and port to be used when nothing is specified.
	 */
	public static final String DEFAULT_HOST =
			CoordinatorClient.DEFAULT_SERVER_HOST + ":" + CoordinatorClient.DEFAULT_SERVER_PORT;
	/**
	 * The window size of the {@link SlidingWindow} for the user activities.
	 */
	public static final int ACTIVITY_WINDOW_SIZE = 60000;
	/**
	 * The jump size of the {@link SlidingWindow} for the user activities.
	 */
	public static final int ACTIVITY_JUMP_SIZE = 5000;
	// Extras
	public static final String EXTRA_ACTIVITY_NAME = "at.jku.pci.lazybird.ACTIVITY_NAME";
	public static final String EXTRA_LOG_ENTRY = "at.jku.pci.lazybird.LOG_ENTRY";
	
	/**
	 * UID for the ongoing notification.
	 */
	private static final int NOTIFICATION_REPORTING = 64;
	private static final int NOTIFICATION_WRITE_FAIL = 65;
	private static final int NOTIFICATION_CONNECTION_FAIL = 66;
	
	/**
	 * To determine from the main activity whether the service is running or was shut down.
	 */
	private static boolean sRunning = false;
	
	// Manage
	private NotificationManager mNotificationManager;
	private SensorManager mSensorManager;
	private LocalBroadcastManager mBrodcastManager;
	private PendingIntent mNotificationIntent;
	private SimpleDateFormat mDateFormat;
	// TTS
	private boolean mTextToSpeech;
	private TextToSpeech mTtsEngine = null;
	private boolean mTtsInit = false;
	private boolean mTtsRunning = false;
	private HashMap<String, String> mTtsParams;
	// Log
	private boolean mWriteToFile;
	private String mFilename;
	private String mDirname;
	private BufferedWriter mOutfile = null;
	// Report
	private boolean mReport;
	private String mServer;
	private String mUsername;
	private CoordinatorClient mClient = null;
	private boolean mReportEnabled = false;
	Handler mHandler = new Handler();
	private Runnable mRunReportActivity = new Runnable() {
		@Override
		public void run()
		{
			// The server expects an update every few seconds to keep the client online
			reportActivity(getLastActivity());
			mHandler.postDelayed(this, 2000);
		}
	};
	// State
	private WakeLock mWakelock = null;
	private int mLastActivity = -1;
	private int mNewCount = 0;
	private int mNewActivity = -2;
	private Classifier mClassifier;
	private Instances mHeader;
	private int mFeatures;
	private SlidingWindow<TimeInstance> mSlidingWindow;
	private SlidingWindow<UserActivities> mUserSlidingWindow;
	private HashSet<String> mUsersOnline;
	private Date mStartTime;
	
	@Override
	public void onCreate()
	{
		// Only one instance is created by the system (hopefully, the docs don't explicitly state this)
		sInstance = this;
		if(LOCAL_LOGV) Log.v(LOGTAG, "Service created: " + this);
		
		mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		mBrodcastManager = LocalBroadcastManager.getInstance(this);
		mDateFormat = new SimpleDateFormat(LOG_FORMAT, Locale.US);
		sRunning = false;
		
		// Make the pending intent bring the app to the front rather than starting a new activity
		Intent i = new Intent(this, MainActivity.class);
		i.setAction("android.intent.action.MAIN");
		i.addCategory("android.intent.category.LAUNCHER");
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
		mNotificationIntent = PendingIntent.getActivity(this, 0, i, 0);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if(LOCAL_LOGV) Log.v(LOGTAG, "Received start id " + startId + ": " + intent);
		
		// Service is restarted by the system, we don't want that
		if(intent == null)
		{
			stopSelf();
			return START_STICKY;
		}
		
		if(!sRunning)
		{
			sRunning = true;
			mStartTime = new Date();
			mUserSlidingWindow = new SlidingWindow<UserActivities>(ACTIVITY_WINDOW_SIZE,
					ACTIVITY_JUMP_SIZE, new WindowListener<UserActivities>() {
						@Override
						public void onWindowChanged(Iterable<UserActivities> window)
						{
							onUserWindowChanged(window);
						}
					});
			mUsersOnline = new HashSet<String>();
			
			// get information from the intent
			mClassifier = (Classifier)intent.getSerializableExtra(ReportFragment.EXTRA_CLASSIFIER);
			mFeatures = intent.getIntExtra(ReportFragment.EXTRA_FEATURES, 0x21);
			mHeader = buildHeader(mFeatures);
			int windowSize = intent.getIntExtra(ReportFragment.EXTRA_WINDOW, 1000);
			int jumpSize = intent.getIntExtra(ReportFragment.EXTRA_JUMP, 100);
			mSlidingWindow = new SlidingWindow<TimeInstance>(windowSize, jumpSize, this);
			mTextToSpeech = intent.getBooleanExtra(ReportFragment.EXTRA_TTS, false);
			mWriteToFile = intent.getBooleanExtra(ReportFragment.EXTRA_LOG, false);
			mReport = intent.getBooleanExtra(ReportFragment.EXTRA_REPORT, false);
			
			// If the wakelock option is activated, make a wakelock
			if(intent.getBooleanExtra(ReportFragment.EXTRA_WAKELOCK, false))
			{
				final PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
				mWakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOGTAG);
			}
			
			// TTS is possible
			if(mTextToSpeech)
			{
				// TTS is enabled
				if(intent.getBooleanExtra(ReportFragment.EXTRA_TTS_ENABLE, false))
					setTextToSpeech(true);
			}
			
			// Log is possible
			if(mWriteToFile)
			{
				mFilename = intent.getStringExtra(ReportFragment.EXTRA_FILENAME);
				mDirname = intent.getStringExtra(ReportFragment.EXTRA_DIRNAME);
				// Log is enabled
				if(intent.getBooleanExtra(ReportFragment.EXTRA_LOG_ENABLE, false))
					setWriteToFile(true);
				log(getString(R.string.rservice_started));
			}
			
			// Report is possible
			if(mReport)
			{
				mServer = intent.getStringExtra(ReportFragment.EXTRA_REPORT_SERVER);
				mUsername = intent.getStringExtra(ReportFragment.EXTRA_REPORT_USER);
				initCoordinationClient();
				// Report is enabled
				if(intent.getBooleanExtra(ReportFragment.EXTRA_REPORT_ENABLE, false))
					setReportToServer(true);
			}
			
			startReporting();
		}
		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy()
	{
		if(LOCAL_LOGV) Log.v(LOGTAG, "Service destroyed: " + this);
		
		stopForeground(true);
		if(mWakelock != null)
			mWakelock.release();
		if(mSensorManager != null)
			mSensorManager.unregisterListener(this);
		mHandler.removeCallbacks(mRunReportActivity);
		
		try
		{
			if(mOutfile != null)
			{
				log(getString(R.string.rservice_stopped));
				mOutfile.flush();
				mOutfile.close();
			}
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
		finally
		{
			mOutfile = null;
		}
		
		if(mClient != null)
		{
			mClient.interrupt();
			mClient = null;
		}
		
		if(mTtsEngine != null)
		{
			mTtsEngine.stop();
			mTtsEngine.shutdown();
			mTtsEngine = null;
		}
		
		Toast.makeText(this, R.string.rservice_stopped, Toast.LENGTH_SHORT)
			.show();
		sRunning = false;
		sInstance = null;
		mBrodcastManager.sendBroadcast(new Intent(ReportFragment.BCAST_SERVICE_STOPPED));
	}
	
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}
	
	/**
	 * Builds an {@link Instances} object with the specified feature attributes and a class
	 * 
	 * @param flags a mask of selected features, see {@link Feature#getMask(Feature[])}.
	 * @return an {@link Instances} object with attributes for the specified features, a class attribute with
	 *         possible values taken from the {@code classes} array resource, and a capacity of {@code 2}.
	 */
	private Instances buildHeader(int flags)
	{
		final Feature[] features = Feature.getFeatures(flags);
		final String[] classes = getResources().getStringArray(R.array.classes);
		final FastVector attributes = new FastVector(features.length + 1);
		final FastVector values = new FastVector(classes.length);
		
		for(Feature f : features)
			attributes.addElement(new Attribute(f.getName()));
		for(int j = 1; j < classes.length; j++)
			values.addElement(classes[j]);
		attributes.addElement(new Attribute("class", values));
		
		final Instances out = new Instances("header", attributes, 2);
		out.setClassIndex(out.numAttributes() - 1);
		
		return out;
	}
	
	/**
	 * Attempts to set the language of the Text-to-speech engine and kills it on failure.
	 * <p>
	 * TODO maybe add a check for TTS data to the main activity to inform the user.
	 */
	void initTts()
	{
		int lang = mTtsEngine.setLanguage(Locale.ENGLISH);
		if(lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED)
		{
			mTtsEngine.shutdown();
			mTtsEngine = null;
			return;
		}
		
		// Set output stream to music, this is the stream the main activity requests for volume control
		mTtsParams = new HashMap<String, String>();
		mTtsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM, "STREAM_MUSIC");
		mTtsInit = true;
	}
	
	/**
	 * If the client is {@code null} or is not alive any more, initialize it.
	 */
	private void initCoordinationClient()
	{
		// If reporting is not enabled at all, skip user state listening as well
		if(!mReport)
			return;
		
		if(mClient != null && !mClient.isAlive())
			mClient = null;
		
		if(mClient == null)
		{
			String host;
			int port;
			
			if(mUsername == null || mUsername.isEmpty())
				return;
			
			if(mServer == null || mServer.isEmpty())
			{
				// Use default server and port in case something is missing
				host = CoordinatorClient.DEFAULT_SERVER_HOST;
				port = CoordinatorClient.DEFAULT_SERVER_PORT;
			}
			else if(mServer.contains(":"))
			{
				// Use specified server and port
				final int idx = mServer.indexOf(":");
				host = mServer.substring(0, idx);
				try
				{
					port = Integer.parseInt(mServer.substring(idx + 1, mServer.length()));
				}
				catch(NumberFormatException ex)
				{
					port = CoordinatorClient.DEFAULT_SERVER_PORT;
				}
			}
			else
			{
				// Port is missing, use default
				host = mServer;
				port = CoordinatorClient.DEFAULT_SERVER_PORT;
			}
			
			// Start reporting
			mClient = new CoordinatorClient(host, port, mUsername);
			try
			{
				Thread.sleep(1000);
			}
			catch(InterruptedException ex)
			{
				// Why would I be interrupted?
			}
			
			// In case the connection fails, the thread of the client stops; check
			if(mClient.isAlive())
			{
				mClient.addGroupStateListener(this);
			}
			else
			{
				notifyConnectionFail();
				mClient = null;
			}
		}
	}
	
	/**
	 * Gets the last instance of the service created by the system.
	 * 
	 * @return the last instance of the service created, or {@code null} if none was created.
	 */
	public static ClassifierService getInstance()
	{
		return sInstance;
	}
	
	/**
	 * Gets a value indicating whether the service has received a start command and is running.
	 * 
	 * @return {@code true} if the service is running or was killed unexpectedly and {@link #onDestroy()} has
	 *         not been called, {@code false} otherwise.
	 */
	public static boolean isRunning()
	{
		return sRunning;
	}
	
	/**
	 * Gets the last activity as classified by the classifier, or {@code null} if there is none.
	 * 
	 * @return the name of the last activity, or {@code null}.
	 * @see #getClassifier()
	 */
	public String getLastActivity()
	{
		if(mLastActivity < 0)
			return null;
		return mHeader.classAttribute().value(mLastActivity);
	}
	
	/**
	 * Gets a value indicating whether Text-to-speech output is enabled.
	 * 
	 * @return {@code true} if Text-to-speech output is enabled, {@code flase} otherwise.
	 */
	public boolean getTextToSpeech()
	{
		return mTtsEngine != null && mTtsInit && mTtsRunning;
	}
	
	/**
	 * Sets whether Text-to-speech output is enabled. This has no effect if TTS was not enabled in the start
	 * {@code Intent}.
	 * 
	 * @param enable {@code true} if Text-to-speech output should be enabled.
	 */
	public void setTextToSpeech(boolean enable)
	{
		// TTS not enabled
		if(!mTextToSpeech)
			return;
		
		if(mTtsEngine == null)
		{
			if(enable)
			{
				// Initialize and start TTS engine when enabled the first time
				mTtsEngine = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
					@Override
					public void onInit(int status)
					{
						if(status == TextToSpeech.SUCCESS)
							initTts();
					}
				});
				mTtsRunning = true;
			}
		}
		else
		{
			if(enable && !mTtsRunning)
			{
				// Start TTS engine when disabled
				mTtsRunning = true;
			}
			else if(!enable && mTtsRunning)
			{
				// Stop TTS engine when enabled
				mTtsEngine.stop();
				mTtsRunning = false;
			}
		}
	}
	
	/**
	 * Gets a value indicating whether a log is written to the output file.
	 * 
	 * @return {@code true} if the log is written to the output file, {@code false} otherwise.
	 * @see #setWriteToFile(boolean)
	 */
	public boolean getWriteToFile()
	{
		return mOutfile != null;
	}
	
	/**
	 * Sets whether the log should be written to a file. This has no effect if logging to a file was not
	 * enabled in the start {@code Intent}.
	 * <p>
	 * Note that every time this is set from {@code false} to {@code true}, the output file is overwritten.
	 * 
	 * @param write {@code true} to write a log file.
	 */
	public void setWriteToFile(boolean write)
	{
		// Log file not enabled
		if(!mWriteToFile)
			return;
		
		if(mOutfile == null && write)
		{
			// Create ouput file to enable logging
			if(mFilename == null || mFilename.isEmpty() || mDirname == null)
				return;
			
			final File dir = new File(Environment.getExternalStorageDirectory(), mDirname);
			if(!dir.exists())
				dir.mkdir();
			final File file = new File(dir, mFilename);
			
			try
			{
				mOutfile = new BufferedWriter(new FileWriter(file));
				mOutfile.write(getString(R.string.app_name));
				mOutfile.write(' ');
				mOutfile.write(getString(R.string.rservice_label));
				mOutfile.write(" Log\n\n");
			}
			catch(IOException ex)
			{
				notifyWriteFail();
				mOutfile = null;
			}
		}
		else if(mOutfile != null && !write)
		{
			// Close output file to disable logging
			try
			{
				mOutfile.flush();
				mOutfile.close();
			}
			catch(IOException ex)
			{
				notifyWriteFail();
			}
			finally
			{
				mOutfile = null;
			}
		}
	}
	
	/**
	 * Gets the filename the log is written to.
	 * 
	 * @return the filename the log is written to.
	 */
	public String getFilename()
	{
		return mFilename;
	}
	
	/**
	 * Gets the directory the output file is written to. The base directory is always
	 * {@link Environment#getExternalStorageDirectory()}.
	 * 
	 * @return the directory of the output file.
	 * @see #getFilename()
	 */
	public String getDirname()
	{
		return mDirname;
	}
	
	/**
	 * Gets a value indicating whether the current activity is being reported to the server.
	 * 
	 * @return {@code true} if the activity is reported, {@code false} otherwise.
	 * @see #setReportToServer(boolean)
	 * @see #getReportServer()
	 * @see #getReportUsername()
	 */
	public boolean getReportToServer()
	{
		return mClient != null && mReportEnabled;
	}
	
	/**
	 * Sets whether the current activity should be reported to the sever. This has no effect if reporting was
	 * not enabled in the start {@code Intent}.
	 * 
	 * @param report {@code true} to report the current activity.
	 * @see #getReportToServer()
	 * @see #getReportServer()
	 * @see #getReportUsername()
	 */
	public void setReportToServer(boolean report)
	{
		// Reporting not enabled
		if(!mReport)
			return;
		
		// If client died, try to reconnect
		if(mClient != null && !mClient.isAlive())
			initCoordinationClient();
		
		mReportEnabled = report;
		if(report && mClient != null)
			mHandler.post(mRunReportActivity);
		else
			mHandler.removeCallbacks(mRunReportActivity);
	}
	
	/**
	 * Gets the server the current activity is reported to, if enabled.
	 * 
	 * @return the server to report to, or {@code null} if not set.
	 * @see #getReportToServer()
	 * @see #getReportUsername()
	 */
	public String getReportServer()
	{
		return mServer;
	}
	
	/**
	 * Gets the username used to report the current activity, if enabled.
	 * 
	 * @return the username, or {@code null} if not set.
	 * @see #getReportToServer()
	 * @see #getReportServer()
	 */
	public String getReportUsername()
	{
		return mUsername;
	}
	
	/**
	 * Gets the classifier used to classify the current activity.
	 */
	public Classifier getClassifier()
	{
		return mClassifier;
	}
	
	/**
	 * Gets the features used for classifying the current activity.
	 */
	public int getFeatures()
	{
		return mFeatures;
	}
	
	/**
	 * Gets the start time of the service.
	 * 
	 * @return the start time of the service, that is the time at which a start command was received.
	 */
	public Date getStartTime()
	{
		return mStartTime;
	}
	
	/**
	 * Builds the ongoing notification for this service.
	 * 
	 * @return the ongoing notification for this service.
	 */
	private Notification makeOngoingNotification()
	{
		CharSequence text = getText(R.string.rservice_started);
		CharSequence title = getText(R.string.app_name);
		
		Notification n = new Notification.Builder(this)
			.setTicker(text)
			.setContentTitle(title)
			.setContentText(text)
			.setContentIntent(mNotificationIntent)
			.setSmallIcon(R.drawable.ic_stat_rservice)
			.setOngoing(true)
			.setLights(0xFFFFFF00, 1000, 1000)
			.setAutoCancel(false).getNotification();
		
		n.flags |= Notification.FLAG_SHOW_LIGHTS;
		return n;
	}
	
	/**
	 * Builds and shows a notification, to inform the user of an error when writing the output log file.
	 */
	private void notifyWriteFail()
	{
		Resources res = getResources();
		CharSequence ticker = res.getText(R.string.write_fail);
		CharSequence text = res.getText(R.string.write_fail_long);
		CharSequence title = res.getText(R.string.app_name);
		
		Notification n = new Notification.Builder(this)
			.setTicker(ticker)
			.setContentTitle(title)
			.setContentText(text)
			.setSmallIcon(R.drawable.ic_stat_rservice)
			.setAutoCancel(true)
			.setContentIntent(mNotificationIntent).getNotification();
		
		mNotificationManager.notify(NOTIFICATION_WRITE_FAIL, n);
	}
	
	/**
	 * Builds and shows a notification, to inform the user of a failed connection to the reporting server.
	 */
	private void notifyConnectionFail()
	{
		Resources res = getResources();
		CharSequence ticker = res.getText(R.string.conn_fail);
		CharSequence text = res.getText(R.string.conn_fail_long);
		CharSequence title = res.getText(R.string.app_name);
		
		Notification n = new Notification.Builder(this)
			.setTicker(ticker)
			.setContentTitle(title)
			.setContentText(text)
			.setSmallIcon(R.drawable.ic_stat_rservice)
			.setAutoCancel(true)
			.setContentIntent(mNotificationIntent).getNotification();
		
		mNotificationManager.notify(NOTIFICATION_CONNECTION_FAIL, n);
	}
	
	/**
	 * Registers the sensor listener, starts this service as foreground service and sends a broadcast
	 * informing of the start.
	 */
	private void startReporting()
	{
		Sensor s = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);
		
		startForeground(NOTIFICATION_REPORTING, makeOngoingNotification());
		if(mWakelock != null)
			mWakelock.acquire();
		
		mBrodcastManager.sendBroadcast(new Intent(ReportFragment.BCAST_SERVICE_STARTED));
	}
	
	/**
	 * Writes the specified message to the log file, if enabled.
	 * 
	 * @param msg the message to write
	 */
	private void log(String msg)
	{
		if(mOutfile == null)
			return;
		
		try
		{
			mOutfile.write(String.format(mDateFormat.format(new Date()), msg));
			mOutfile.write("\n");
		}
		catch(IOException ex)
		{
			notifyWriteFail();
			mOutfile = null;
		}
	}
	
	/**
	 * Reports the specified activity to the server, if enabled.
	 * 
	 * @param activity the activity to report, has to correspond to a value in the {@link ClassLabel}
	 *        enumeration.
	 */
	void reportActivity(String activity)
	{
		if(mClient != null && mReportEnabled)
		{
			if(mClient.isAlive())
			{
				if(activity == null || activity.isEmpty())
					mClient.setCurrentActivity(null);
				else
					mClient.setCurrentActivity(ClassLabel.parse(activity));
			}
			else
				initCoordinationClient();
		}
	}
	
	/**
	 * Speaks the specified string, if enabled.
	 * 
	 * @param activity the text to speak.
	 */
	private void speak(String activity)
	{
		if(mTtsEngine != null && mTtsInit && mTtsRunning && activity != null)
			mTtsEngine.speak(activity, TextToSpeech.QUEUE_ADD, mTtsParams);
	}
	
	/**
	 * Outputs the specified activity on all enabled channels.
	 * 
	 * @param newActivity the index of the new activity in the {@code class} attribute of {@link #mHeader},
	 *        see {@link #buildHeader(int)}.
	 */
	private void onActivityChanged(int newActivity)
	{
		mLastActivity = newActivity;
		
		final String activity = getLastActivity();
		mBrodcastManager.sendBroadcast(new Intent(ReportFragment.BCAST_NEW_ACTIVITY)
			.putExtra(EXTRA_ACTIVITY_NAME, activity));
		
		log(getString(R.string.log_new_activity, activity));
		
		speak(activity);
		
		reportActivity(activity);
	}
	
	@Override
	public void onSensorChanged(SensorEvent event)
	{
		// Add the new data to the sliding window
		TimeInstance i = new TimeInstance(4, System.currentTimeMillis());
		for(int j = 0; j < 3; j++)
			i.setValue(j + 1, event.values[j]);
		mSlidingWindow.add(i);
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		
	}
	
	@Override
	public void onWindowChanged(Iterable<TimeInstance> window)
	{
		// Extract features and save to a new instance with the right attributes
		Instance i = FeatureExtractor.extractFeatures(window, mFeatures);
		Instance add = new Instance(mHeader.numAttributes());
		for(int j = 1; j < i.numValues(); j++)
			add.setValue(j - 1, i.value(j));
		mHeader.delete();
		mHeader.add(add);
		add.setDataset(mHeader);
		
		try
		{
			// Attempt to classify the instance, do nothing if classification fails
			double tmp = mClassifier.classifyInstance(add);
			if(tmp == Instance.missingValue())
				throw new Exception("not classified.");
			
			// Require a few equal classifications to change activity
			int clazz = (int)tmp;
			if(mLastActivity != clazz)
			{
				if(mNewActivity == clazz)
				{
					if(++mNewCount > 13)
						onActivityChanged(clazz);
				}
				else
				{
					mNewActivity = clazz;
					mNewCount = 0;
				}
			}
			else
				mNewCount = 0;
		}
		catch(Exception ex)
		{
			Log.i(LOGTAG, "Classification failed: " + ex.getMessage());
		}
	}
	
	@Override
	public void groupStateChanged(UserState[] groupState)
	{
		final UserActivities list = new UserActivities(System.currentTimeMillis());
		
		// Maintain a list of online users to avoid scanning all entries when the window changes
		for(UserState u : groupState)
		{
			if(u.getUpdateAge() > 10000)
			{
				mUsersOnline.remove(u.getUserId());
			}
			else
			{
				mUsersOnline.add(u.getUserId());
				list.put(u.getUserId(), u.getActivity());
			}
		}
		
		mUserSlidingWindow.add(list);
	}
	
	public void onUserWindowChanged(Iterable<UserActivities> window)
	{
		
	}
}
