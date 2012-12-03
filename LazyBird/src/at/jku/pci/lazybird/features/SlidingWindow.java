package at.jku.pci.lazybird.features;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import weka.core.Instance;

/**
 * Represents a sliding window over a set of {@link Instance} objects with a defined window and
 * jump size. A listener can be registered to be notified when the window jumps as data are
 * added.
 * <p>
 * Static methods are provided to process a fixed set of data instead of incrementally adding
 * data.
 * 
 * @see WindowListener
 * @see -TODO static methods
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
	 * @param l the listener to set, or {@code null} to remove the listener.
	 */
	public void setWindowListener(WindowListener l)
	{
		mListener = l;
	}
	
	/**
	 * Gets the listener that will be notified of changes to this {@code SlidingWindow}.
	 */
	public WindowListener getWindowListener()
	{
		return mListener;
	}
	
	/**
	 * Sets the window size of this {@code SlidingWindow}.
	 * 
	 * @param windowSize the window size in ms, needs to be at least {@code 2}.
	 * @exception IllegalArgumentException if {@code windowSize} is less than {@code 2}.
	 */
	public void setWindowSize(int windowSize)
	{
		if(windowSize < 2)
			throw new IllegalArgumentException("Window size needs to be at least 2.");
		mModCount++;
		mWindowSize = windowSize;
		// TODO trigger list update
	}
	
	/**
	 * Gets the window size of this {@code SlidingWindow} in ms.
	 */
	public int getWindowSize()
	{
		return mWindowSize;
	}
	
	/**
	 * Sets the jump size of this {@code SlidingWindow}.
	 * 
	 * @param jumpSize the jump size in ms, needs to be at least {@code 1}.
	 * @exception IllegalArgumentException if {@code jumpSize} is less than {@code 1}.
	 */
	public void setJumpSize(int jumpSize)
	{
		if(jumpSize < 1)
			throw new IllegalArgumentException("Jump size needs to be at least 2.");
		mModCount++;
		mJumpSize = jumpSize;
		// TODO trigger list update
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
}
