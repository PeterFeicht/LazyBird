package at.jku.pci.lazybird.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;
import at.jku.pci.lazybird.R;
import java.io.Serializable;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * A {@link ListAdapter} with a queue that produces log-like element views and supports unbounded
 * addition of elements.
 * <p>
 * Note that the {@code Context} of this Adapter is not preserved when serializing it, you have
 * to call {@link #setContext(Context)} after deserialization to use it again.
 * 
 * @author Peter
 */
public class LogListAdapter extends BaseAdapter implements Serializable
{
	private static final long serialVersionUID = -6786673932748896170L;
	
	/**
	 * The default initial capacity when nothing is specified.
	 */
	public static final int DEFAULT_CAPACITY = 100;
	
	private final int mInitialCapacity;
	private List<LogEntry> mElements;
	private transient Context mContext = null;
	private DateFormat mDateFormat;
	
	/**
	 * Initializes a new instance of the {@link LogListAdapter} class with the default minimum
	 * capacity for the specified context.
	 * 
	 * @param c the context for this adapter.
	 */
	public LogListAdapter(Context c)
	{
		this(c, DEFAULT_CAPACITY);
	}
	
	/**
	 * Initializes a new instance of the {@link LogListAdapter} class with the specified minimum
	 * capacity for the specified context.
	 * 
	 * @param c the context for this adapter.
	 * @param capacity the minimum capacity.
	 * @exception IllegalArgumentException if {@code capacity} is less than {@code 1}.
	 */
	public LogListAdapter(Context c, int capacity)
	{
		if(capacity < 1)
			throw new IllegalArgumentException();
		if(c == null)
			throw new NullPointerException();
		
		mInitialCapacity = capacity;
		mElements = new ArrayList<LogEntry>(capacity);
		mContext = c;
		mDateFormat = android.text.format.DateFormat.getTimeFormat(mContext);
	}
	
	/**
	 * Sets the context to use when creating views. This has to be called after deserialization
	 * so the adapter can be used again.
	 * 
	 * @param c the new {@link Context}.
	 * @exception NullPointerException if {@code c} is {@code null}.
	 */
	public void setContext(Context c)
	{
		if(c == null)
			throw new NullPointerException();
		mContext = c;
		mDateFormat = android.text.format.DateFormat.getTimeFormat(mContext);
	}
	
	@Override
	public int getCount()
	{
		return mElements.size();
	}
	
	@Override
	public LogEntry getItem(int position)
	{
		return mElements.get(position);
	}
	
	@Override
	public long getItemId(int position)
	{
		return mElements.get(position).hashCode();
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		View view = convertView;
		if(view != null)
		{
			TextView time = (TextView)view.findViewById(R.id.logTime);
			TextView msg = (TextView)view.findViewById(R.id.logMessage);
			
			if(time == null || msg == null)
				return createView(position, parent);
			
			time.setText(mDateFormat.format(mElements.get(position).time));
			msg.setText(mElements.get(position).message);
			return view;
		}
		else
			return createView(position, parent);
	}
	
	private View createView(int position, ViewGroup parent)
	{
		if(mContext == null)
			return null;
		LayoutInflater inflater =
			(LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = inflater.inflate(R.layout.log_entry, parent, false);
		TextView time = (TextView)v.findViewById(R.id.logTime);
		TextView msg = (TextView)v.findViewById(R.id.logMessage);
		if(time == null || msg == null)
			throw new InternalError("Layout of LogEntry corrupted!");
		time.setText(mDateFormat.format(mElements.get(position).time));
		msg.setText(mElements.get(position).message);
		return v;
	}
	
	@Override
	public boolean hasStableIds()
	{
		return true;
	}
	
	/**
	 * Constructs a new {@link LogEntry} with the specified message and current time and adds it
	 * to this adapter.
	 * 
	 * @param msg the message for the log entry.
	 * @return {@code true} if the number of elements has exceeded the initial capacity, you can
	 *         use this to determine if the adapter should be trimmed, see {@link #trim()}.
	 */
	public boolean add(String msg)
	{
		return add(new LogEntry(msg));
	}
	
	/**
	 * Constructs a new {@link LogEntry} with the specified time and message and adds it to this
	 * adapter.
	 * 
	 * @param time the time for the log entry.
	 * @param msg the message for the log entry.
	 * @return {@code true} if the number of elements has exceeded the initial capacity, you can
	 *         use this to determine if the adapter should be trimmed, see {@link #trim()}.
	 */
	public boolean add(Date time, String msg)
	{
		return add(new LogEntry(time, msg));
	}
	
	/**
	 * Adds the specified log entry to this adapter.
	 * 
	 * @param entry the {@link LogEntry} to add.
	 * @return {@code true} if the number of elements has exceeded the initial capacity, you can
	 *         use this to determine if the adapter should be trimmed, see {@link #trim()}.
	 */
	public boolean add(LogEntry entry)
	{
		mElements.add(entry);
		notifyDataSetChanged();
		return mElements.size() > mInitialCapacity;
	}
	
	/**
	 * Resizes the backing list of this adapter to the initial capacity, removing the oldest
	 * elements (i.e. the ones inserted first).
	 * 
	 * @see #getInitialCapacity()
	 */
	public void trim()
	{
		if(mElements.size() > mInitialCapacity)
		{
			final int size = mElements.size();
			List<LogEntry> l = new ArrayList<LogEntry>(mInitialCapacity);
			for(LogEntry e : mElements.subList(size - mInitialCapacity, size))
				l.add(e);
			mElements = l;
			notifyDataSetChanged();
		}
	}
	
	/**
	 * Removes all elements from this adapter, leaving it empty.
	 */
	public void clear()
	{
		mElements.clear();
		notifyDataSetChanged();
	}
	
	/**
	 * 
	 */
	public void sort()
	{
		Collections.sort(mElements);
		notifyDataSetChanged();
	}
	
	/**
	 * Gets the initial capacity of this adapter.
	 * 
	 * @see #trim()
	 */
	public int getInitialCapacity()
	{
		return mInitialCapacity;
	}
}
