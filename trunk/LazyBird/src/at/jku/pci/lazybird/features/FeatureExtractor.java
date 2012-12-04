package at.jku.pci.lazybird.features;

import java.io.File;
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
}
