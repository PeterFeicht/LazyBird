package at.jku.pci.lazybird.features;

import java.io.File;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;
import android.os.AsyncTask;
import at.jku.pci.lazybird.features.SlidingWindow.WindowListener;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Represents a task that takes an array of {@link File} and {@link Feature} objects and creates
 * an {@link Instances} data set. Static methods are provided to extract features from a set of
 * {@link Instance} objects.
 * <p>
 * The {@code File} objects need to point to ARFF Files, the {@code Feature} objects need to be a
 * subset of the {@link Feature} enumeration. This class can be used to asynchronously calculate
 * the features of ARFF Files containing acceleration vectors (in 3D space) by transporting both
 * the source filenames and the requested features as well as the resulting data set.
 * <p>
 * A sliding window is applied to the input data, see the {@link SlidingWindow} class for more
 * information.<br>
 * The input ARFF files or {@link Instance} objects need to have the following attributes:
 * <ul>
 * <li>The first attribute is the timestamp.
 * <li>The following three attributes are numeric values (e.g. acceleration coordinates).
 * <li>The optional last attribute is the nominal class.
 * <li>The data is sorted in ascending order by timestamp (when averaging multiple instances in
 * the sliding window, the timestamp of the last value is used).
 * <li>The data only contains one class value per file (this is not checked, but it leads to
 * undefined behavior if there is more than one class).
 * </ul>
 * If multiple files are specified, the output data set will contain the features of every file,
 * extracted separately and then joined in no particular order.
 * <p>
 * If the only feature specified is {@link Feature#RAW}, then no feature extraction is performed
 * on the input files, but all files are merged into one single feature file, if all of them
 * contain the same features. If not all files contain the same features, an exception will be
 * thrown. See also {@link Feature#getAttribute()}.
 * 
 * @see <a href="http://weka.wikispaces.com/ARFF+(stable+version)">ARFF at the WEKA Wiki</a>
 * @author Peter
 */
public class FeatureExtractor
{
	/**
	 * A mask with bits set for all features implemented for extraction by this class.<br>
	 * If a feature is specified for extraction that is not implemented, it will be silently
	 * ignored. However, you can safely consider all features that have corresponding bits set in
	 * this mask to be implemented and generate the expected output.
	 * 
	 * @see Feature#getMask(Feature[])
	 * @see Feature#getBit()
	 */
	public static final int IMPLEMENTED_FEATURES = 0xFF;
	
	private final File[] mFiles;
	private final Feature[] mFeatures;
	private final int mWindowSize;
	private final int mJumpSize;
	private Instances mOutput = null;
	private Feature[] mOutputFeatures = null;
	private boolean mCalculated = false;
	
	/**
	 * Initializes a new instance of the {@link FeatureExtractor} class with the specified
	 * {@link File} and {@link Feature} sets and the specified window and jump sizes for the
	 * sliding window.
	 * 
	 * @param files the files to calculate features for.
	 * @param features the features to calculate, or the single feature {@link Feature#RAW}.
	 * @param windowSize the window size for
	 *        {@link SlidingWindow#slide(Instances, int, int, WindowListener)} in ms.
	 * @param jumpSize the jump size for
	 *        {@link SlidingWindow#slide(Instances, int, int, WindowListener)} in ms.
	 * @exception IllegalArgumentException if either one of {@code files} or {@code features}
	 *            does not have any entries.
	 */
	public FeatureExtractor(File[] files, Feature[] features, int windowSize, int jumpSize)
	{
		if(files == null || features == null)
			throw new NullPointerException();
		if(files.length < 1 || features.length < 1)
			throw new IllegalArgumentException("No empty sets permitted");
		
		mFiles = files;
		mFeatures = features;
		mWindowSize = windowSize;
		mJumpSize = jumpSize;
	}
	
	/**
	 * Gets the array of files assigned to this extractor.<br>
	 * This can be changed before or after the extraction has run, but should not be modified
	 * while the extraction is running or the behavior will be undefined.
	 */
	public File[] getFiles()
	{
		return mFiles;
	}
	
	/**
	 * Gets the array of features assigned to this extractor.<br>
	 * This can be changed before or after the extraction has run, but should not be modified
	 * while the extraction is running or the behavior will be undefined.
	 */
	public Feature[] getFeatures()
	{
		return mFeatures;
	}
	
	/**
	 * Gets the calculated feature set, or {@code null} if there was an error.
	 * 
	 * @exception IllegalStateException if {@link #extract()} has not been called yet.
	 */
	public Instances getOutput()
	{
		if(!mCalculated)
			throw new IllegalStateException();
		else
			return mOutput;
	}
	
	/**
	 * Gets the features in the output data set, or {@code null} if there was an error.
	 * 
	 * @exception IllegalStateException if {@link #extract()} has not been called yet.
	 */
	public Feature[] getOutputFeatures()
	{
		if(!mCalculated)
			throw new IllegalStateException();
		else
			return mOutputFeatures;
	}
	
	/**
	 * Gets the window size for {@link SlidingWindow#slide(Instances, int, int, WindowListener)}
	 * in ms.
	 */
	public int getWindowSize()
	{
		return mWindowSize;
	}
	
	/**
	 * Gets the jump size for {@link SlidingWindow#slide(Instances, int, int, WindowListener)} in
	 * ms.
	 */
	public int getJumpSize()
	{
		return mJumpSize;
	}
	
	/**
	 * Determines whether {@link #extract()} has been called and returned.
	 * 
	 * @return {@code true} if {@code extract()} has been called, {@code false} if it is still
	 *         running or was never called.
	 */
	public boolean hasRun()
	{
		return mCalculated;
	}
	
	/**
	 * Starts the feature extraction by reading files and extracting features. This method can
	 * block the calling thread a long time and should not be run on the UI thread.
	 * 
	 * @exception IllegalStateException if {@link #extract()} was called before.
	 * @see AsyncTask
	 */
	public void extract()
	{
		if(mCalculated)
			throw new IllegalStateException();
		
		// mCalculated = true;
	}
	
	/**
	 * Extracts the features specified in the bit mask from the specified instances.
	 * <p>
	 * For more information on the expected instance format see the {@link FeatureExtractor class
	 * documentation}.
	 * 
	 * @param instances the instances to extract features from.
	 * @param flags a bit mask, as returned by {@link Feature#getMask(Feature[])}.
	 * @return an {@link Instance} with the specified features. The features are in the same
	 *         order that {@link Feature#getFeatures(int)} returns.
	 * @exception IllegalArgumentException if {@code instances} is empty or {@code flags} is
	 *            {@code 0}.
	 */
	public static Instance extractFeatures(Iterable<Instance> instances, int flags)
	{
		if(flags == 0)
			throw new IllegalArgumentException("flags cannot be 0.");
		
		final Feature[] features = Feature.getFeatures(flags);
		final boolean hasMean = (flags & 0x07) != 0;
		final boolean hasVariance = (flags & 0x70) != 0;
		final EnumMap<Feature, Double> values = new EnumMap<Feature, Double>(Feature.class);
		
		final Iterator<Instance> it = instances.iterator();
		if(!it.hasNext())
			throw new IllegalArgumentException("instances cannot be empty.");
		
		if(hasMean)
		{
			final Instance mean = mean(instances);
			if(Feature.X.isSet(flags))
				values.put(Feature.X, mean.value(1));
			if(Feature.Y.isSet(flags))
				values.put(Feature.Y, mean.value(2));
			if(Feature.Z.isSet(flags))
				values.put(Feature.Z, mean.value(3));
		}
		
		if(Feature.MAGNITUDE.isSet(flags) || Feature.VARIANCE_OF_MAGNITUDE.isSet(flags))
		{
			final Iterable<Instance> mag = magnitude(instances);
			
			if(Feature.MAGNITUDE.isSet(flags))
			{
				double sum = 0.0;
				int num = 0;
				for(Instance i: mag)
				{
					num++;
					sum += i.value(1);
				}
				values.put(Feature.MAGNITUDE, sum / num);
			}
			
			if(Feature.VARIANCE_OF_MAGNITUDE.isSet(flags))
				values.put(Feature.VARIANCE_OF_MAGNITUDE, variance(mag, 1).value(1));
		}
		
		if(hasVariance)
		{
			if(Feature.VARIANCE_X.isSet(flags))
				values.put(Feature.VARIANCE_X, variance(instances, 1).value(1));
			if(Feature.VARIANCE_Y.isSet(flags))
				values.put(Feature.VARIANCE_Y, variance(instances, 2).value(1));
			if(Feature.VARIANCE_Z.isSet(flags))
				values.put(Feature.VARIANCE_Z, variance(instances, 3).value(1));
		}
		
		// We need the last instance for the timestamp
		Instance last = null;
		while(it.hasNext())
			last = it.next();
		
		final boolean hasClass = (last.numValues() == 5);
		final Instance out = new Instance(features.length + (hasClass ? 2 : 1));
		out.setValue(0, last.value(0));
		
		for(int j = 0; j < features.length; j++)
			out.setValue(j + 1, values.get(features[j]));
		if(hasClass)
			out.setValue(features.length + 1, last.value(4));
		
		return out;
	}
	
	/**
	 * A streamlined version of {@link #extractFeatures(Iterable, int)} that extracts only the
	 * two features {@link Feature#X} and {@link Feature#VARIANCE_Y}.
	 * 
	 * @param instances instances the instances to extract features from.
	 * @return an {@link Instance} with the two features. The features are in the same order that
	 *         {@link Feature#getFeatures(int)} returns.
	 * @exception IllegalArgumentException if {@code instances} is empty.
	 */
	public static Instance extractFeaturesPE(Iterable<Instance> instances)
	{
		final Iterator<Instance> it = instances.iterator();
		if(!it.hasNext())
			throw new IllegalArgumentException("instances cannot be empty.");
		
		// We need the last instance for the timestamp
		Instance last = null;
		while(it.hasNext())
			last = it.next();
		
		// This is needed to put the features in the right order, if that should change any time.
		final Feature[] features =
			Feature.getFeatures(Feature.X.getBit() | Feature.VARIANCE_Y.getBit());
		final EnumMap<Feature, Double> values = new EnumMap<Feature, Double>(Feature.class);
		
		values.put(Feature.VARIANCE_Y, variance(instances, 1).value(1));
		values.put(Feature.X, mean(instances).value(2));
		
		final boolean hasClass = (last.numValues() == 5);
		final Instance out = new Instance(2 + (hasClass ? 2 : 1));
		out.setValue(0, last.value(0));
		
		for(int j = 0; j < features.length; j++)
			out.setValue(j + 1, values.get(features[j]));
		if(hasClass)
			out.setValue(3, last.value(4));
		
		return out;
	}
	
	/**
	 * Extracts the specified features from the specified instances.<br>
	 * This is a convenience method and {@link #extractFeatures(Iterable, int)} is more
	 * efficient.
	 * <p>
	 * For more information on the expected instance format see the {@link FeatureExtractor class
	 * documentation}.
	 * 
	 * @param instances the instances to extract features from.
	 * @param features an array of {@link Feature} objects. Occurrences of {@link Feature#RAW}
	 *        are ignored.
	 * @return an {@link Instance} with the specified features. The features are in the same
	 *         order that {@link Feature#getMask(Feature[])} returns.
	 * @exception IllegalArgumentException if {@code instances} is empty or {@code features}
	 *            contains no features.
	 */
	public static Instance extractFeatures(Iterable<Instance> instances, Feature[] features)
	{
		return extractFeatures(instances, Feature.getMask(features));
	}
	
	private static Instance variance(Iterable<Instance> instances, int idx)
	{
		Iterator<Instance> it = instances.iterator();
		Instance last = null;
		
		double sum = 0.0;
		int num = 0;
		
		while(it.hasNext())
		{
			last = it.next();
			sum += last.value(idx);
			num++;
		}
		
		final double mean = sum / num;
		double var = 0.0;
		
		while(it.hasNext())
		{
			last = it.next();
			var += (last.value(idx) - mean) * (last.value(idx) - mean);
		}
		
		final boolean hasClass = (last.numValues() == 5) || (last.numValues() == 3);
		final Instance out = new Instance(hasClass ? 3 : 2);
		out.setValue(0, last.value(0));
		if(hasClass)
			out.setValue(2, last.value(last.numValues() == 3 ? 2 : 4));
		
		out.setValue(1, var / num);
		
		return out;
	}
	
	private static Iterable<Instance> magnitude(Iterable<Instance> instances)
	{
		final LinkedList<Instance> out = new LinkedList<Instance>();
		
		for(Instance i: instances)
		{
			final Instance mag = new Instance(i.numValues() - 2);
			mag.setValue(0, i.value(0));
			if(mag.numValues() > 2)
				mag.setValue(2, i.value(4));
			
			final double tmp = mag.value(1) * mag.value(1) + mag.value(2) * mag.value(2);
			mag.setValue(1, Math.sqrt(tmp + mag.value(3) * mag.value(3)));
			out.add(mag);
		}
		
		return out;
	}
	
	/**
	 * Calculates the average of the specified instances.<br>
	 * For the required attributes of an {@code Instance}, see
	 * {@link SlidingWindow#slide(Instances, int, int, WindowListener)}. If the instances contain
	 * a class, it is also set in the output instance, the timestamp is that of the last instance
	 * in the set.
	 * 
	 * @param instances a set of {@link Instance} objects.
	 * @return an averaged {@code Instance}, or {@code null} if {@code instances} is empty.
	 */
	private static Instance mean(Iterable<Instance> instances)
	{
		final Iterator<Instance> it = instances.iterator();
		Instance last = null;
		double x = 0.0, y = 0.0, z = 0.0;
		int num = 0;
		
		if(!it.hasNext())
			return null;
		
		while(it.hasNext())
		{
			last = it.next();
			num++;
			x += last.value(1);
			y += last.value(2);
			z += last.value(3);
		}
		
		final Instance out = new Instance(last.numValues());
		out.setValue(0, last.value(0));
		if(out.numValues() == 5)
			out.setValue(4, last.value(4));
		out.setValue(1, x / num);
		out.setValue(2, y / num);
		out.setValue(3, z / num);
		
		return out;
	}
}
