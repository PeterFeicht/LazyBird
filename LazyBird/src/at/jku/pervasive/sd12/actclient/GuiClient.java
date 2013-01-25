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
import java.util.Arrays;
import java.util.HashMap;

public class GuiClient extends Thread
{
	public static final Charset NET_CHARSET = Charset.forName("US-ASCII");
	
	private static final Quote[] BRACKETS = { new Quote('(', ')') };
	
	/**
	 * A GroupStateListener will be notified of any user activity changes.
	 */
	public static interface GroupStateListener
	{
		void groupStateChanged(UserState[] groupState);
	}
	
	public static class UserState implements Comparable<UserState>
	{
		private final String userId;
		private long age;
		private ClassLabel activity;
		private UserRole role;
		
		public UserState(long age, String userId, ClassLabel activity, UserRole role)
		{
			this.age = age;
			this.userId = userId;
			this.activity = activity;
			this.role = role;
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
			return age;
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
		
		@Override
		public String toString()
		{
			return age + ":" + userId + ":" + activity + ":" + role;
		}
		
		@Override
		public int compareTo(UserState another)
		{
			return userId.compareTo(another.userId);
		}
	}
	
	private HashMap<String, UserState> mGroupState = new HashMap<String, GuiClient.UserState>();
	private GroupStateListener mListener = null;
	private RoomState mRoomState = null;
	private String mActiveId = "";
	
	private Socket mSocket;
	private BufferedReader mInput;
	private PrintWriter mOutput;
	
	private UserState[] groupStateList;
	
	public GuiClient(String host, int port) throws IOException
	{
		mSocket = new Socket();
		mSocket.connect(new InetSocketAddress(host, port), 5000);
		mInput =
			new BufferedReader(new InputStreamReader(mSocket.getInputStream(), NET_CHARSET));
		mOutput =
			new PrintWriter(new OutputStreamWriter(mSocket.getOutputStream(), NET_CHARSET), true);
	}
	
	public void setActiveUser(String n)
	{
		mActiveId = n;
		if(n == null)
			mOutput.println("");
		else
			mOutput.println(mActiveId);
	}
	
	public String getActiveUser()
	{
		return mActiveId;
	}
	
	@Override
	public void run()
	{
		String line = null;
		while(!interrupted())
		{
			try
			{
				line = mInput.readLine();
				if(line != null)
				{
					String[] users = OptionParser.split(line, ",", BRACKETS);
					synchronized(mGroupState)
					{
						mRoomState = RoomState.parse(users[0]);
						for(int j = 1; j < users.length; j++)
						{
							if(!users[j].isEmpty())
							{
								String[] up = OptionParser.split(users[j]);
								if(up.length != 4)
									throw new Exception("Invalid server response");
								
								final long age = Long.parseLong(up[0]);
								final ClassLabel activity = ClassLabel.parse(up[2]);
								final UserRole role = UserRole.parse(up[3]);
								
								final UserState state = mGroupState.get(up[1]);
								if(state != null)
								{
									state.age = age;
									state.activity = activity;
									state.role = role;
								}
								else
								{
									mGroupState.put(up[1],
										new UserState(age, up[1], activity, role));
								}
							}
						}
					}
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
		if(mSocket.isConnected())
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
	
	protected void notifyGroupStateListener()
	{
		if(mListener != null)
		{
			// if the number of users changed, update sorted array of all users
			if(groupStateList.length != mGroupState.size())
			{
				groupStateList = mGroupState.values().toArray(new UserState[mGroupState.size()]);
				Arrays.sort(groupStateList);
			}
			
			mListener.groupStateChanged(groupStateList);
		}
	}
	
	public RoomState getRoomState()
	{
		return mRoomState;
	}
}
