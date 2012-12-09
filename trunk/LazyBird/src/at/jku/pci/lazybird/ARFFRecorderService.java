package at.jku.pci.lazybird;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
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
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

public class ARFFRecorderService extends Service implements SensorEventListener
{
	private static ARFFRecorderService sInstance = null;
	
	public static final String LOGTAG = "ARFFRecorderService";
	public static final boolean LOCAL_LOGV = true;
	/**
	 * Format string used to format the acceleration values written to the output file.
	 */
	private static final String DATA_FORMAT = "%d,%.2f,%.2f,%.2f";
	private static final String DATE_FORMAT = "yyyy-MM-dd kk:mm";
	
	/**
	 * The attribute specification for the output ARFF file.<br>
	 * Note that a class may also be specified, which is not part of this specification.
	 */
	public static final String ATTRIBUTE_STRING =
		"@ATTRIBUTE timestamp        NUMERIC\n" +
			"@ATTRIBUTE accelerationx    NUMERIC\n" +
			"@ATTRIBUTE accelerationy    NUMERIC\n" +
			"@ATTRIBUTE accelerationz    NUMERIC\n";
	
	/**
	 * UID for the ongoing notification.
	 */
	private static final int NOTIFICATION_RECORDING = 16;
	
	/**
	 * UID for the too many values notification.
	 */
	private static final int NOTIFICATION_TOO_MAY_VALUES = 17;
	
	/**
	 * UID for the waiting notification.
	 */
	private static final int NOTIFICATION_WAITING = 18;
	
	/**
	 * Intent for the service.
	 */
	public static final String ARFF_SERVICE = "at.jku.pci.lazybird.ARFF_SERVICE";
	
	/**
	 * Maximum number of values after which recording is stopped automatically.
	 * <p>
	 * Setting {@link SettingsActivity#KEY_VALUE_UPDATE_SPEED}
	 */
	private static long sMaxNumValues = 10000;
	
	/**
	 * To determine from the main activity whether the service is running or was shut down.
	 */
	private static boolean sRunning = false;
	
	/**
	 * The number of seconds to wait after receiving the start command before recording.
	 * <p>
	 * Setting {@link SettingsActivity#KEY_START_DELAY}
	 */
	private static int sStartDelay = 0;
	
	private final long mTimeOffset = System.currentTimeMillis() - System.nanoTime() / 1000000;
	private final IBinder mBinder = new LocalBinder();
	private NotificationManager mNotificationManager;
	private SensorManager mSensorManager;
	private PendingIntent mNotificationIntent;
	private WaitingTimer mWaitingTimer;
	private long mNumValues;
	private float[] mLastValues;
	private BufferedWriter mOutfile;
	private String mFilename;
	private String mDirname;
	private String mClass;
	private Date mStartTime;
	
	@Override
	public void onCreate()
	{
		// Only one instance is created by the system
		// (hopefully, the docs don't explicitly state this)
		sInstance = this;
		if(LOCAL_LOGV) Log.v(LOGTAG, "Service created: " + this);
		
		mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
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
			
			// get information from the intent
			mFilename = intent.getStringExtra(RecorderFragment.EXTRA_FILENAME);
			mDirname = intent.getStringExtra(RecorderFragment.EXTRA_DIRNAME);
			final int clazz = intent.getIntExtra(RecorderFragment.EXTRA_CLASS, 0);
			final String[] classes = intent.getStringArrayExtra(RecorderFragment.EXTRA_CLASSES);
			
			if(mFilename == null)
			{
				stopSelf();
				return 0;
			}
			
			final File directory = new File(Environment.getExternalStorageDirectory(), mDirname);
			if(!directory.exists())
				directory.mkdir();
			
			// create ARFF-file and write header and data
			final File file = new File(directory, mFilename);
			
			try
			{
				mOutfile = new BufferedWriter(new FileWriter(file, false));
				
				// write the file header
				mOutfile.write("% Group: Feichtinger, Hager\n% Date: ");
				mOutfile.write(DateFormat.format(DATE_FORMAT, new Date()).toString());
				mOutfile.write(String.format(
					(Locale)null, "\n\n@RELATION lazybird-%d\n\n", System.currentTimeMillis()));
				
				mOutfile.write(ATTRIBUTE_STRING);
				
				if(clazz != 0 && classes != null)
				{
					mOutfile.write("@ATTRIBUTE class            ");
					mOutfile.write(getClassesString(classes) + "\n");
					mClass = classes[clazz];
				}
				
				mOutfile.write("\n@DATA\n");
			}
			catch(IOException e)
			{
				e.printStackTrace();
				stopSelf();
				return 0;
			}
			
			if(sStartDelay != 0)
				mWaitingTimer = (new WaitingTimer(sStartDelay)).start();
			else
				startRecording();
		}
		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy()
	{
		if(LOCAL_LOGV) Log.v(LOGTAG, "Service destroyed: " + this);
		
		stopForeground(true);
		if(mSensorManager != null)
			mSensorManager.unregisterListener(this);
		if(mWaitingTimer != null)
			mWaitingTimer.stop();
		// We don't want to cancel the notification telling the user why we stopped, only those
		// that aren't needed any more.
		mNotificationManager.cancel(NOTIFICATION_WAITING);
		
		try
		{
			if(mOutfile != null)
			{
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
		
		Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show();
		sRunning = false;
		sInstance = null;
		LocalBroadcastManager.getInstance(this).sendBroadcast(
			new Intent(RecorderFragment.BCAST_SERVICE_STOPPED));
	}
	
	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}
	
	public class LocalBinder extends Binder
	{
		ARFFRecorderService getService()
		{
			return ARFFRecorderService.this;
		}
	}
	
	/**
	 * Gets the last instance of the service created by the system.
	 * 
	 * @return the last instance of the service created, or {@code null} if none was created.
	 */
	public static ARFFRecorderService getInstance()
	{
		return sInstance;
	}
	
	/**
	 * Gets a value indicating whether the service has received a start command and is running.
	 * <p>
	 * Note that this does not mean the service is recording, see {@link #setStartDelay(int)}.
	 * 
	 * @return {@code true} if the service is running or was killed unexpectedly and
	 *         {@link #onDestroy()} has not been called, {@code false} otherwise.
	 */
	public static boolean isRunning()
	{
		return sRunning;
	}
	
	/**
	 * Gets the maximum number of values after which recording is stopped automatically.
	 * 
	 * @return the maximum number of values.
	 */
	public static long getMaxNumValues()
	{
		return sMaxNumValues;
	}
	
	/**
	 * Sets the maximum number of values after which recording is stopped automatically.
	 * 
	 * @param maxNumValues the new maximum number, or a value less than {@code 1} to set
	 *        {@link Long#MAX_VALUE}.
	 */
	public static void setMaxNumValues(long maxNumValues)
	{
		if(maxNumValues < 1)
			ARFFRecorderService.sMaxNumValues = Long.MAX_VALUE;
		else
			ARFFRecorderService.sMaxNumValues = maxNumValues;
	}
	
	/**
	 * Gets the time to wait after receiving the start command before recording.
	 * 
	 * @return the number of seconds to wait before recording.
	 */
	public static int getStartDelay()
	{
		return sStartDelay;
	}
	
	/**
	 * Sets the time to wait after receiving the start command before recording.
	 * 
	 * @param startDelay the number of seconds to wait before recording.
	 * @exception IllegalArgumentException if {@code startDelay} is less than {@code 0}.
	 */
	public static void setStartDelay(int startDelay)
	{
		if(startDelay < 0)
			throw new IllegalArgumentException("startDelay cannot be less than 0.");
		sStartDelay = startDelay;
	}
	
	/**
	 * Gets the last set of values reported by the sensor.
	 * 
	 * @return the last values reported, or {@code null} if there has been no update yet.
	 */
	public float[] getLastValues()
	{
		return mLastValues;
	}
	
	/**
	 * Gets the number of values already recorded.
	 * 
	 * @return the number of values recorded.
	 */
	public long getNumValues()
	{
		return mNumValues;
	}
	
	/**
	 * Gets the filename the data is written to.
	 * 
	 * @return the filename the data is written to.
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
	 * Gets the class of data points.
	 * 
	 * @return the class, or {@code null} if none is set.
	 */
	public String getAClass()
	{
		return mClass;
	}
	
	/**
	 * Gets the start time of the service.
	 * <p>
	 * If a start delay other than {@code 0} is set, the actual start time of the recording will
	 * be different from the service start time.
	 * 
	 * @return the start time of the service, that is the time at which a start command was
	 *         received.
	 * @see #setStartDelay(int)
	 */
	public Date getStartTime()
	{
		return mStartTime;
	}
	
	/**
	 * Constructs a string representation of the specified array.
	 * 
	 * @param c the array to construct the array string from.
	 * @return a representation of the array in the form of <code>{ c[0], c[1], ... }</code>.
	 */
	private String getClassesString(String[] c)
	{
		if(c.length < 2)
			throw new IllegalArgumentException("classes array has too few entries!");
		
		StringBuilder sb = (new StringBuilder()).append("{ ").append(c[1]);
		for(int j = 2; j < c.length; j++)
			sb.append(", " + c[j]);
		
		return sb.append(" }").toString();
	}
	
	/**
	 * Builds the ongoing notification for this service.
	 * 
	 * @return the ongoing notification for this service.
	 */
	private Notification makeOngoingNotification()
	{
		CharSequence text = getText(R.string.service_started);
		CharSequence title = getText(R.string.app_name);
		
		Notification n = new Notification.Builder(this)
			.setTicker(text)
			.setContentTitle(title)
			.setContentText(text)
			.setContentIntent(mNotificationIntent)
			.setSmallIcon(R.drawable.ic_stat_service)
			.setOngoing(true)
			.setLights(0xFFFFFF00, 1000, 1000)
			.setAutoCancel(false).getNotification();
		
		n.flags |= Notification.FLAG_SHOW_LIGHTS;
		return n;
	}
	
	/**
	 * Creates or updates a notification, announcing that the service is waiting to start
	 * recording.
	 * 
	 * @param seconds the number of seconds until recording starts.
	 * @see #setStartDelay(int)
	 */
	private void notifyWaiting(int seconds)
	{
		Resources res = getResources();
		CharSequence ticker = res.getText(R.string.service_waiting_ticker);
		CharSequence title = res.getText(R.string.app_name);
		CharSequence text = getResources().getString(R.string.service_waiting, seconds);
		
		Notification n = new Notification.Builder(this)
			.setTicker(ticker)
			.setContentTitle(title)
			.setContentText(text)
			.setSmallIcon(R.drawable.ic_stat_service)
			.setAutoCancel(false)
			.setOngoing(true)
			.setContentIntent(mNotificationIntent).getNotification();
		
		mNotificationManager.notify(NOTIFICATION_WAITING, n);
	}
	
	/**
	 * Builds and shows a notification, announcing that the maximum number of values has been
	 * reached and the service stopped.
	 */
	private void notifyLimit()
	{
		Resources res = getResources();
		CharSequence ticker = res.getText(R.string.too_many_values);
		CharSequence text = res.getString(R.string.too_many_values_long, sMaxNumValues);
		CharSequence title = res.getText(R.string.app_name);
		
		Notification n = new Notification.Builder(this)
			.setTicker(ticker)
			.setContentTitle(title)
			.setContentText(text)
			.setSmallIcon(R.drawable.ic_stat_service)
			.setAutoCancel(true)
			.setDefaults(Notification.DEFAULT_VIBRATE)
			.setContentIntent(mNotificationIntent).getNotification();
		
		mNotificationManager.notify(NOTIFICATION_TOO_MAY_VALUES, n);
	}
	
	private void startRecording()
	{
		Sensor s = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);
		
		mNotificationManager.cancel(NOTIFICATION_WAITING);
		startForeground(NOTIFICATION_RECORDING, makeOngoingNotification());
		
		LocalBroadcastManager.getInstance(this).sendBroadcast(
			new Intent(RecorderFragment.BCAST_SERVICE_STARTED));
	}
	
	public void onSensorChanged(SensorEvent event)
	{
		final Long timestamp = event.timestamp / 1000000 + mTimeOffset;
		mNumValues++;
		
		// shouldn't happen, since outfile is only closed in onDestroy
		if(mOutfile == null)
		{
			mSensorManager.unregisterListener(this);
		}
		else
		{
			mLastValues = event.values;
			try
			{
				mOutfile.write(String.format((Locale)null, DATA_FORMAT, timestamp,
					event.values[0], event.values[1], event.values[2]));
				if(mClass != null)
					mOutfile.write("," + mClass);
				mOutfile.write("\n");
			}
			catch(IOException ex)
			{
				ex.printStackTrace();
				mSensorManager.unregisterListener(this);
				stopSelf();
			}
		}
		
		if(mNumValues > sMaxNumValues)
		{
			mSensorManager.unregisterListener(this);
			notifyLimit();
			stopSelf();
		}
	}
	
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}
	
	private class WaitingTimer implements Runnable
	{
		private int mSeconds;
		private final Handler mHandler = new Handler();
		
		public WaitingTimer(int seconds)
		{
			mSeconds = seconds;
		}
		
		public WaitingTimer stop()
		{
			mHandler.removeCallbacks(this);
			return this;
		}
		
		public WaitingTimer start()
		{
			notifyWaiting(mSeconds);
			mHandler.postDelayed(this, 1000);
			return this;
		}
		
		public void run()
		{
			mSeconds--;
			
			if(mSeconds > 0)
				start();
			else
				startRecording();
		}
	}
}
