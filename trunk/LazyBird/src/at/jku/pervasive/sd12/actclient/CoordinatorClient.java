package at.jku.pervasive.sd12.actclient;

import at.jku.pervasive.sd12.util.OptionParser;
import at.jku.pervasive.sd12.util.OptionParser.Quote;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

/**
 * A CoordinatorClient handles interaction with an activity coordination server. It will immediately activate
 * the client thread upon object instantiation and connect to the designated coordination server. To terminate
 * the client, call {@linkplain #interrupt()}.
 * 
 * @author matsch, 2012
 */
public class CoordinatorClient extends Thread
{
	public static final String DEFAULT_SERVER_HOST = "netadmin.soft.uni-linz.ac.at";
	public static final int DEFAULT_SERVER_PORT = 8891;
	public static final Charset NET_CHARSET = Charset.forName("US-ASCII");
	
	/**
	 * This class represents the state of a single user, mainly the current activity.
	 */
	public class UserState implements Comparable<UserState>
	{
		private final String userId;
		long updateAge;
		ClassLabel activity;
		
		@SuppressWarnings("hiding")
		public UserState(String userId)
		{
			this.userId = userId;
			updateAge = -1;
			activity = null;
		}
		
		/**
		 * The users ID.
		 * 
		 * @return String
		 */
		public String getUserId()
		{
			return userId;
		}
		
		/**
		 * Time since the users activity was last updated in ms.
		 * 
		 * @return long
		 */
		public long getUpdateAge()
		{
			return updateAge;
		}
		
		/**
		 * The users last known activity.
		 * 
		 * @return ClassLabel
		 */
		public ClassLabel getActivity()
		{
			return activity;
		}
		
		/**
		 * Tell the server the user role that was determined for this user.
		 * 
		 * @param role the new user role (UserRole)
		 */
		public void setRole(UserRole role)
		{
			synchronized(mOutputQueue)
			{
				mOutputQueue.add("U:" + userId + ":" + role);
			}
			// notify client thread that new output is available
			lock.release();
		}
		
		@Override
		public String toString()
		{
			return userId + ":" + activity + "-" + updateAge;
		}
		
		@Override
		public int compareTo(UserState another)
		{
			return userId.compareTo(another.userId);
		}
		
	}
	
	static final Quote[] BRACKETS = { new Quote('(', ')') };
	
	private final String mClientId;
	private final String mHost;
	private final int mPort;
	
	private Socket mSocket;
	BufferedReader mIn;
	private PrintWriter mOut;
	private Thread mInputThread;
	Semaphore lock;
	
	Deque<String> mOutputQueue;
	HashMap<String, UserState> mGroupState;
	private UserState[] mGroupStateList;
	private ArrayList<GroupStateListener> mGroupStateListeners;
	
	/**
	 * Create a new CoordinatorClient.
	 * 
	 * @param host server host name
	 * @param port server port
	 * @param clientId the ID of this client
	 */
	public CoordinatorClient(String host, int port, String clientId)
	{
		mHost = host;
		mPort = port;
		mClientId = clientId;
		
		mOutputQueue = new LinkedList<String>();
		mGroupState = new HashMap<String, UserState>();
		mGroupStateList = new UserState[0];
		mGroupStateListeners = new ArrayList<GroupStateListener>();
		mInputThread = null;
		lock = new Semaphore(1);
		
		start();
	}
	
	/**
	 * Create a new CoordinatorClient with default server and host.
	 * 
	 * @param clientId the ID of this client
	 */
	public CoordinatorClient(String clientId)
	{
		this(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT, clientId);
	}
	
	/**
	 * Update the current activity and notify the server. This method will return immediately, the server
	 * update will be performed by the client thread.
	 * 
	 * @param label the new activity (class label), null for null-class
	 */
	public void setCurrentActivity(ClassLabel label)
	{
		synchronized(mOutputQueue)
		{
			mOutputQueue.add(String.valueOf(label));
		}
		// notify client thread that new output is available
		lock.release();
	}
	
	/**
	 * Update the current room state and notify the server.
	 * 
	 * @param state the new room state (RoomState)
	 */
	public void setRoomState(RoomState state)
	{
		synchronized(mOutputQueue)
		{
			mOutputQueue.add("R:" + state);
		}
		// notify client thread that new output is available
		lock.release();
	}
	
	/**
	 * Add a new listener, that will be notified about any user activity changes. The listeners will be
	 * notified whenever an activity changes, but at most every 100 ms and at least every 5000 ms.
	 * 
	 * @param groupStateListener The listener to add.
	 */
	public void addGroupStateListener(GroupStateListener groupStateListener)
	{
		mGroupStateListeners.add(groupStateListener);
	}
	
	/**
	 * Remove a group state listener.
	 * 
	 * @param groupStateListener The listener to remove.
	 */
	public void removeGroupStateListener(GroupStateListener groupStateListener)
	{
		mGroupStateListeners.remove(groupStateListener);
	}
	
	protected void notifyGroupStateListeners()
	{
		// if the number of users changed, update sorted array of all users
		if(mGroupStateList.length != mGroupState.size())
		{
			ArrayList<UserState> gsa = new ArrayList<UserState>(mGroupState.values());
			Collections.sort(gsa);
			mGroupStateList = gsa.toArray(new UserState[gsa.size()]);
		}
		
		// notify listeners
		for(GroupStateListener l : mGroupStateListeners)
			l.groupStateChanged(mGroupStateList);
	}
	
	@Override
	public void run()
	{
		try
		{
			// open connection
			mSocket = new Socket();
			mSocket.connect(new InetSocketAddress(mHost, mPort), 5000);
			mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream(), NET_CHARSET));
			mOut = new PrintWriter(new OutputStreamWriter(mSocket.getOutputStream(), NET_CHARSET), true);
			
			// send our client id to the server
			mOut.println(mClientId);
			String response = mIn.readLine();
			System.out.println("server says: " + response);
			if(!response.equals("accepted")) throw new IOException("invalid client id");
			
			// create input thread
			mInputThread = new Thread() {
				@Override
				public void run()
				{
					String line;
					while(!Thread.interrupted())
					{
						try
						{
							line = mIn.readLine();
							if(line != null)
							{
								String[] users = OptionParser.split(line, ",", BRACKETS);
								for(String user : users)
									updateUser(user);
								
								notifyGroupStateListeners();
							}
							else
							{
								// this never happens with my java runtime ...
								interrupt();
							}
						}
						catch(Exception e)
						{
							if(e.getMessage() == null)
								e.printStackTrace(System.out);
							else
								System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
							interrupt();
						}
					}
				}
				
				private void updateUser(String user) throws IOException
				{
					if(user.length() != 0)
					{
						String[] up = OptionParser.split(user);
						if(up.length != 3)
							throw new IOException("invalid server response");
						String usId = up[1];
						UserState us = mGroupState.get(usId);
						if(us == null)
						{
							// add new user state object
							us = new UserState(usId);
							mGroupState.put(usId, us);
						}
						// update user state
						us.updateAge = Long.parseLong(up[0]);
						us.activity = ClassLabel.parse(up[2]);
					}
				}
				
			};
			mInputThread.start();
			
			// send current activity to server until this thread is stopped
			while(!Thread.interrupted())
			{
				// wait for an activity change
				lock.acquire();
				
				// send current activity to server
				synchronized(mOutputQueue)
				{
					String line;
					while((line = mOutputQueue.poll()) != null)
						mOut.println(line);
				}
			}
		}
		catch(IOException e)
		{
			System.out.println("connection failed: " + e.getMessage());
		}
		catch(InterruptedException e)
		{
			// thread is interrupted by a call to interrupt, which we consider to be the "correct" way to
			// terminate the client. do nothing.
		}
		
		if(mInputThread != null)
			mInputThread.interrupt();
		if(!mSocket.isClosed())
		{
			try
			{
				mSocket.close();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public boolean isConnected()
	{
		if(mInputThread != null)
			return isAlive() && mInputThread.isAlive();
		return isAlive();
	}
}
