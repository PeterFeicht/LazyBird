package at.jku.pci.lazybird.features;

import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.UnsupportedAttributeTypeException;
import android.util.Log;

/**
 * Represents a sliding window over a set of {@link Instance} objects with a defined window and
 * jump size. A listener can be registered to be notified when the window jumps as data are
 * added.
 * <p>
 * Static methods are provided to process a fixed set of data instead of incrementally adding
 * data.
 * 
 * @see WindowListener
 * @see #average(Instances, int, int, boolean)
 * @author Peter
 */
public class SlidingWindow implements Iterable<Instance>
{
	/**
	 * Defines the interface that can be registered to be notified of window changes as data are
	 * added to a {@link SlidingWindow}.
	 * 
	 * @author Peter
	 */
	public interface WindowListener
	{
		/**
		 * Called, when the window changes.
		 * 
		 * @param window the {@link SlidingWindow} object raising the event.
		 */
		public void onWindowChanged(SlidingWindow window);
	}
	
	/**
	 * Represents an iterator over a {@link SlidingWindow} object. Note that this implementation
	 * is fail-fast, however the {@code SlidingWindow} iterated over should not be changed while
	 * iterating regardless.
	 * 
	 * @author Peter
	 */
	public class SlidingWindowIterator implements Iterator<Instance>
	{
		private int mExpectedModCount;
		private Iterator<Instance> it;
		
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
		public Instance next()
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
	private enum AttributeOrder
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
	
	private int mModCount = 0;
	private int mWindowSize = 1000;
	private int mJumpSize = 100;
	private LinkedList<Instance> mInstances = new LinkedList<Instance>();
	private WindowListener mListener = null;
	
	/**
	 * Initializes a new instance of the {@link SlidingWindow} class with default window size
	 * (1000ms) and jump size (100ms).
	 */
	public SlidingWindow()
	{
		
	}
	
	/**
	 * Initializes a new instance of the {@link SlidingWindow} class with the specified window
	 * and jump size.
	 * 
	 * @param windowSize the window size in ms, needs to be greater than {@code 1}.
	 * @param jumpSize the jump size in ms, needs to be greater than {@code 1} and less than
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
	 * {@inheritDoc}
	 */
	@Override
	public Iterator<Instance> iterator()
	{
		return new SlidingWindowIterator();
	}
	
	/**
	 * Sets the {@link WindowListener} to be notified of changes to this {@code SlidingWindow}.
	 * 
	 * @param listener the listener to set, or {@code null} to remove the listener.
	 */
	public void setWindowListener(WindowListener listener)
	{
		mListener = listener;
	}
	
	/**
	 * Gets the listener that will be notified of changes to this {@code SlidingWindow}.
	 */
	public WindowListener getWindowListener()
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
	 * Adds an instance of data to this sliding window. The instance needs to have exactly four
	 * values, a class cannot be specified. For more information on the instance format or when a
	 * class is needed, see {@link #average(Instances, int, int, boolean)}.
	 * 
	 * @param i the {@link Instance} to add.
	 * @return {@code true} if the window changed after adding this instance, {@code false}
	 *         otherwise.
	 * @exception NullPointerException if {@code i} is {@code null}.
	 * @exception IllegalArgumentException if {@code i} does not have exactly four values.
	 * @see #hasValidAttributes(Instances)
	 */
	public boolean add(Instance i)
	{
		if(i == null)
			throw new NullPointerException();
		if(i.numValues() != 4)
			throw new IllegalArgumentException();
		
		final double nextJump = mInstances.getFirst().value(0) + mWindowSize;
		
		mInstances.add(i);
		mModCount++;
		if(i.value(0) > nextJump)
		{
			final double cut = i.value(0) - mWindowSize;
			while(mInstances.size() > 0 && mInstances.getFirst().value(0) < cut)
				mInstances.removeFirst();
			if(mListener != null)
				mListener.onWindowChanged(this);
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * This static method takes a fixed set of data and applies a sliding window, averaging the
	 * input data over the window, optionally taking the absolute values. This method should be
	 * used for performance reasons instead of instantiating a {@link SlidingWindow} when the
	 * data doesn't change, and needs to be used when the data contains a class.
	 * <p>
	 * The format of the input data has to match the following conditions:
	 * <ul>
	 * <li>The first attribute is a timestamp.
	 * <li>The next three attributes are numeric values (e.g. acceleration coordinates).
	 * <li>The optional last attribute is the class.
	 * <li>The data is sorted in ascending order by timestamp.
	 * <li>The data only contains one class value (this is not checked, but it leads to undefined
	 * behavior if there is more than one class).
	 * </ul>
	 * If there is a nominal attribute at the last position, it will be set as the class in the
	 * output data set, regardless of whether it is tha class in the input data set.<br>
	 * When these conditions are not met, an exception will be thrown.
	 * <p>
	 * Note that the attributes of the resulting data set are the same as the specified one, they
	 * are not duplicated and changes will affect both data sets.
	 * 
	 * @param data the data to apply the sliding window average to.
	 * @param windowSize the window size in ms, needs to be greater than {@code 1}.
	 * @param jumpSize the jump size in ms, needs to be greater than {@code 1} and less than
	 *        {@code windowSize}.
	 * @param abs {@code true} if the absolute values should be used, {@code false} otherwise.
	 * @return an averaged data set of the input, {@code null} if the input set does not have any
	 *         data points, or an empty data set if there are not enough data points for at least
	 *         one window size.
	 * @exception NullPointerException if {@code data} is {@code null}.
	 * @exception IllegalArgumentException if
	 *            <ul>
	 *            <li>{@code windowSize} is less than {@code 2} <li>{@code jumpSize} is less than
	 *            {@code 1} <li>{@code windowSize} is less than {@code jumpSize}
	 *            </ul>
	 * @exception UnsupportedAttributeTypeException if the input data doesn't meet the
	 *            requirements specified.
	 */
	public static Instances average(Instances data, int windowSize, int jumpSize, boolean abs)
		throws UnsupportedAttributeTypeException
	{
		if(data == null)
			throw new NullPointerException();
		if(windowSize < 2 || jumpSize < 1)
			throw new IllegalArgumentException("Window and jump size need to be positive.");
		if(windowSize < jumpSize)
			throw new IllegalArgumentException("Jump size cannot be larger than window size.");
		
		@SuppressWarnings("unchecked")
		final AttributeOrder attributeOrder = getAttributeOrder(data.enumerateAttributes());
		
		if(attributeOrder == AttributeOrder.INVALID)
			throw new UnsupportedAttributeTypeException();
		if(data.numInstances() == 0)
			return null;
		
		// The approximate number of jumps assuming that the data is sorted by timestamp
		final long time = (long)(data.lastInstance().value(0) - data.firstInstance().value(0));
		final int jumps = (int)(time / jumpSize);
		final Instances outData =
			new Instances(data.relationName() + "_average", getAttributesVector(data), jumps);
		
		// FIXME remove debug stuff
		final long startTime = System.currentTimeMillis();
		Log.v("SlidingWindow.average", "jumps = " + jumps);
		
		if(time / windowSize < 1.0)
			return outData;
		
		final boolean hasClass = (attributeOrder != AttributeOrder.NO_CLASS);
		final LinkedList<Instance> queue = new LinkedList<Instance>();
		@SuppressWarnings("unchecked")
		final Enumeration<Instance> instances = data.enumerateInstances();
		double nextJump = data.firstInstance().value(0) + windowSize;
		
		while(instances.hasMoreElements())
		{
			Instance i = instances.nextElement();
			queue.add(i);
			if(i.value(0) > nextJump)
			{
				nextJump += jumpSize;
				final double cut = i.value(0) - windowSize;
				while(queue.size() > 0 && queue.getFirst().value(0) < cut)
					queue.removeFirst();
				outData.add(mean(queue, hasClass, abs));
			}
		}
		
		Log.v("SlidingWindow.average", System.currentTimeMillis() - startTime +
			"ms, outData count = " + outData.numInstances());
		
		return outData;
	}
	
	private static FastVector getAttributesVector(Instances data)
	{
		@SuppressWarnings("unchecked")
		final Collection<Attribute> attrs = Collections.list(data.enumerateAttributes());
		final FastVector vector = new FastVector(attrs.size());
		
		for(Attribute a: attrs)
		{
			vector.addElement(a);
		}
		
		return vector;
	}
	
	private static Instance mean(Iterable<Instance> i, boolean hasClass, boolean abs)
	{
		final Iterator<Instance> it = i.iterator();
		Instance last = null;
		double x = 0.0, y = 0.0, z = 0.0;
		int numInstances = 0;
		
		if(!it.hasNext())
			return null;
		
		if(abs)
		{
			while(it.hasNext())
			{
				last = it.next();
				numInstances++;
				x += Math.abs(last.value(1));
				y += Math.abs(last.value(2));
				z += Math.abs(last.value(3));
			}
		}
		else
		{
			while(it.hasNext())
			{
				last = it.next();
				numInstances++;
				x += last.value(1);
				y += last.value(2);
				z += last.value(3);
			}
		}
		
		final Instance out = new Instance(hasClass ? 5 : 4);
		out.setValue(0, last.value(0));
		if(hasClass)
			out.setValue(4, last.value(4));
		out.setValue(1, x / numInstances);
		out.setValue(2, y / numInstances);
		out.setValue(3, z / numInstances);
		
		return out;
	}
	
	/**
	 * Determines if a data set has a valid set of attributes and can be supplied to
	 * {@link #average(Instances, int, int, boolean)}.
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
	private static AttributeOrder getAttributeOrder(Enumeration<Attribute> attrs)
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
