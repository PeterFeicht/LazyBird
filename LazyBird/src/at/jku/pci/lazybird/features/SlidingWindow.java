package at.jku.pci.lazybird.features;

import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.UnsupportedAttributeTypeException;
import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Represents a sliding window over a set of {@link Timestamped} objects with a defined window
 * and jump size. A listener can be registered to be notified when the window jumps as data are
 * added.
 * <p>
 * Static methods are provided to process a fixed set of data (in an {@link Instances} object)
 * instead of incrementally adding data.
 * 
 * @see WindowListener
 * @see #slide(Instances, int, int, WindowListener)
 * @see FeatureExtractor
 * @author Peter
 */
public class SlidingWindow<T extends Timestamped> implements Iterable<T>
{
	/**
	 * Defines the interface that can be registered to be notified of window changes as data are
	 * added to a {@link SlidingWindow}.
	 * 
	 * @author Peter
	 */
	public interface WindowListener<U>
	{
		/**
		 * Called, when the window changes.
		 * 
		 * @param window the data of the new window.
		 */
		public void onWindowChanged(Iterable<U> window);
	}
	
	/**
	 * Represents an iterator over a {@link SlidingWindow} object. Note that this implementation
	 * is fail-fast, however the {@code SlidingWindow} iterated over should not be changed while
	 * iterating regardless.
	 * 
	 * @author Peter
	 * @see Iterator
	 */
	public class SlidingWindowIterator implements Iterator<T>
	{
		private final int mExpectedModCount;
		private final Iterator<T> it;
		
		private SlidingWindowIterator()
		{
			mExpectedModCount = mModCount;
			it = mInstances.iterator();
		}
		
		/**
		 * {@inheritDoc}
		 * 
		 * @exception ConcurrentModificationException if the underlying {@link SlidingWindow} has
		 *            been changed since this iterator was created.
		 */
		@Override
		public boolean hasNext()
		{
			if(mExpectedModCount != mModCount)
				throw new ConcurrentModificationException();
			
			return it.hasNext();
		}
		
		/**
		 * {@inheritDoc}
		 * 
		 * @exception ConcurrentModificationException if the underlying {@link SlidingWindow} has
		 *            been changed since this iterator was created.
		 */
		@Override
		public T next()
		{
			if(mExpectedModCount != mModCount)
				throw new ConcurrentModificationException();
			
			return it.next();
		}
		
		/**
		 * Not supported.
		 */
		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Represents an attribute order in a data set. Class here refers to any nominal attribute.
	 * 
	 * @author Peter
	 */
	protected enum AttributeOrder
	{
		/**
		 * There is a class attribute at the last position.
		 */
		HAS_CLASS,
		
		/**
		 * There is no class attribute.
		 */
		NO_CLASS,
		
		/**
		 * The attributes are not valid.
		 */
		INVALID;
	}
	
	private volatile int mModCount = 0;
	private final int mWindowSize;
	private final int mJumpSize;
	private final LinkedList<T> mInstances = new LinkedList<T>();
	private WindowListener<T> mListener = null;
	
	/**
	 * Initializes a new instance of the {@link SlidingWindow} class with default window size
	 * (1000ms) and jump size (100ms).
	 */
	public SlidingWindow()
	{
		this(1000, 100);
	}
	
	/**
	 * Initializes a new instance of the {@link SlidingWindow} class with the specified window
	 * and jump size.
	 * 
	 * @param windowSize the window size in ms, needs to be greater than {@code 1}.
	 * @param jumpSize the jump size in ms, needs to be at least {@code 1} and less than
	 *        {@code windowSize}.
	 * @exception IllegalArgumentException if {@code windowSize} is less than {@code 2},
	 *            {@code jumpSize} is less than {@code 1} or {@code windowSize} is less than
	 *            {@code jumpSize}.
	 */
	public SlidingWindow(int windowSize, int jumpSize)
	{
		if(windowSize < 2 || jumpSize < 1)
			throw new IllegalArgumentException("Window and jump size need to be positive.");
		if(windowSize < jumpSize)
			throw new IllegalArgumentException("Jump size cannot be larger than window size.");
		
		mJumpSize = jumpSize;
		mWindowSize = windowSize;
	}
	
	/**
	 * Initializes a new instance of the {@link SlidingWindow} class with the specified window
	 * size, jump size and listener.
	 * 
	 * @param windowSize the window size in ms, needs to be greater than {@code 1}.
	 * @param jumpSize the jump size in ms, needs to be at least {@code 1} and less than
	 *        {@code windowSize}.
	 * @param listener the {@link WindowListener} to be registered, or {@code null}.
	 * @exception IllegalArgumentException if {@code windowSize} is less than {@code 2},
	 *            {@code jumpSize} is less than {@code 1} or {@code windowSize} is less than
	 *            {@code jumpSize}.
	 */
	public SlidingWindow(int windowSize, int jumpSize, WindowListener<T> listener)
	{
		this(windowSize, jumpSize);
		mListener = listener;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Iterator<T> iterator()
	{
		return new SlidingWindowIterator();
	}
	
	/**
	 * Sets the {@link WindowListener} to be notified of changes to this {@code SlidingWindow}.
	 * 
	 * @param listener the listener to set, or {@code null} to remove the listener.
	 */
	public void setWindowListener(WindowListener<T> listener)
	{
		mListener = listener;
	}
	
	/**
	 * Gets the listener that will be notified of changes to this {@code SlidingWindow}.
	 */
	public WindowListener<T> getWindowListener()
	{
		return mListener;
	}
	
	/**
	 * Gets the window size of this {@code SlidingWindow} in ms.
	 */
	public int getWindowSize()
	{
		return mWindowSize;
	}
	
	/**
	 * Gets the jump size of this {@code SlidingWindow} in ms.
	 */
	public int getJumpSize()
	{
		return mJumpSize;
	}
	
	/**
	 * Removes all instances from this {@code SlidingWindow}, leaving it empty.
	 */
	public void clear()
	{
		mModCount++;
		mInstances.clear();
	}
	
	/**
	 * Adds an instance of data to this sliding window.
	 * 
	 * @param i the {@link Timestamped} object to add.
	 * @return {@code true} if the window changed after adding this instance, {@code false}
	 *         otherwise.
	 * @exception NullPointerException if {@code i} is {@code null}.
	 */
	public boolean add(T i)
	{
		if(i == null)
			throw new NullPointerException();
		
		long nextJump = 0;
		if(!mInstances.isEmpty())
			nextJump = mInstances.getFirst().getTime() + mWindowSize;
		
		mModCount++;
		mInstances.add(i);
		if(i.getTime() > nextJump)
		{
			final double cut = i.getTime() - mWindowSize;
			while(mInstances.size() > 0 && mInstances.getFirst().getTime() < cut)
				mInstances.removeFirst();
			if(mListener != null)
				mListener.onWindowChanged(this);
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * This static method takes a fixed set of data and applies a sliding window, calling a
	 * listener when the window changes.<br>
	 * This method should be used instead of instantiating a {@link SlidingWindow} when the data
	 * is training data that contains a class.
	 * <p>
	 * The format of the input data has to match the following conditions:
	 * <ul>
	 * <li>The first attribute is a timestamp.
	 * <li>The following three attributes are numeric values (e.g. acceleration coordinates).
	 * <li>The optional last attribute is the class.
	 * <li>The data is sorted in ascending order by timestamp.
	 * <li>The data only contains one class value (this is not checked, but it leads to undefined
	 * behavior if there is more than one class).
	 * </ul>
	 * When these conditions are not met, an exception will be thrown.
	 * <p>
	 * The instances in the set given to {@code listener} is associated with the specified
	 * {@link Instances}.
	 * 
	 * @param data the data to apply the sliding window average to.
	 * @param windowSize the window size in ms, needs to be greater than {@code 1}.
	 * @param jumpSize the jump size in ms, needs to be at least {@code 1} and less than
	 *        {@code windowSize}.
	 * @param listener the {@link WindowListener} to be called on window changes.
	 * @exception NullPointerException if {@code data} or {@code listener} is {@code null}.
	 * @exception IllegalArgumentException if
	 *            <ul>
	 *            <li>{@code windowSize} is less than {@code 2} <li>{@code jumpSize} is less than
	 *            {@code 1} <li>{@code windowSize} is less than {@code jumpSize}
	 *            </ul>
	 * @exception UnsupportedAttributeTypeException if the input data doesn't meet the
	 *            requirements specified.
	 */
	public static void slide(Instances data, int windowSize, int jumpSize,
		WindowListener<Instance> listener) throws UnsupportedAttributeTypeException
	{
		if(data == null || listener == null)
			throw new NullPointerException();
		if(windowSize < 2 || jumpSize < 1)
			throw new IllegalArgumentException("Window and jump size need to be positive.");
		if(windowSize < jumpSize)
			throw new IllegalArgumentException("Jump size cannot be larger than window size.");
		if(data.numInstances() < 1)
			return;
		
		@SuppressWarnings("unchecked")
		final AttributeOrder attributeOrder = getAttributeOrder(data.enumerateAttributes());
		if(attributeOrder == AttributeOrder.INVALID)
			throw new UnsupportedAttributeTypeException();
		
		final double time = data.lastInstance().value(0) - data.firstInstance().value(0);
		if(time / windowSize < 1.0)
			return;
		
		final LinkedList<Instance> queue = new LinkedList<Instance>();
		@SuppressWarnings("unchecked")
		final Enumeration<Instance> instances = data.enumerateInstances();
		double nextJump = data.firstInstance().value(0) + windowSize;
		
		while(instances.hasMoreElements())
		{
			final Instance i = instances.nextElement();
			queue.add(i);
			if(i.value(0) > nextJump)
			{
				nextJump += jumpSize;
				final double cut = i.value(0) - windowSize;
				while(queue.size() > 0 && queue.getFirst().value(0) < cut)
					queue.removeFirst();
				listener.onWindowChanged(queue);
			}
		}
		
		return;
	}
	
	/**
	 * Determines if a data set has a valid set of attributes and can be supplied to
	 * {@link #slide(Instances, int, int, WindowListener)}.
	 * 
	 * @param structure the data set to check.
	 * @return {@code true} if the specified data set can be supplied to {@code average} without
	 *         an exception being thrown, {@code false} otherwise.
	 */
	public static boolean hasValidAttributes(Instances structure)
	{
		@SuppressWarnings("unchecked")
		final Enumeration<Attribute> attrs = structure.enumerateAttributes();
		
		return getAttributeOrder(attrs) != AttributeOrder.INVALID;
	}
	
	/**
	 * Determines the order of the attributes in the specified enumeration.
	 * 
	 * @param attrs an {@link Enumeration} of {@link Attribute} objects to check.
	 * @return a value of {@link AttributeOrder} corresponding to the order of the specified
	 *         attributes.
	 */
	protected static AttributeOrder getAttributeOrder(Enumeration<Attribute> attrs)
	{
		if(!attrs.hasMoreElements())
			return AttributeOrder.INVALID;
		
		// First attribute is the timestamp
		if(!attrs.nextElement().isNumeric())
			return AttributeOrder.INVALID;
		
		// Three coordinates are needed
		for(int j = 0; j < 3; j++)
		{
			if(!attrs.hasMoreElements())
				return AttributeOrder.INVALID;
			if(!attrs.nextElement().isNumeric())
				return AttributeOrder.INVALID;
		}
		
		if(!attrs.hasMoreElements())
			return AttributeOrder.NO_CLASS;
		
		if(attrs.nextElement().isNominal())
			return AttributeOrder.HAS_CLASS;
		else
			return AttributeOrder.INVALID;
	}
}
