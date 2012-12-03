package at.jku.pci.lazybird.features;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import weka.core.Instance;

/**
 * Represents a sliding window over a set of {@link Instance} objects with a defined window- and
 * jump-size. A listener can be registered to be notified when the window jumps as data are
 * added.
 * <p>
 * Static methods are provided to process a fixed set of data instead of incrementally adding
 * data.
 * 
 * @see WindowListener
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
	 * is fail-fast, however the object iterated over should not be changed while iterating
	 * regardless.
	 * 
	 * @author Peter
	 */
	public class SlidingWindowIterator implements Iterator<Instance>
	{
		private int expectedModCount;
		
		private SlidingWindowIterator()
		{
			expectedModCount = modCount;
		}
		
		/**
		 * {@inheritDoc}
		 * @return
		 */
		@Override
		public boolean hasNext()
		{
			if(expectedModCount != modCount)
				throw new ConcurrentModificationException();
			
			// TODO implement
			return false;
		}
		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public Instance next()
		{
			if(expectedModCount != modCount)
				throw new ConcurrentModificationException();
			
			// TODO implement
			return null;
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
	
	private int modCount = 0;
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Iterator<Instance> iterator()
	{
		return new SlidingWindowIterator();
	}
	
}
