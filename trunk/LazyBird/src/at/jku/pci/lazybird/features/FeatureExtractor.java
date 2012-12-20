package at.jku.pci.lazybird.features;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.UnsupportedAttributeTypeException;
import weka.core.converters.ArffLoader.ArffReader;
import android.os.AsyncTask;
import at.jku.pci.lazybird.features.SlidingWindow.WindowListener;

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
 * <li>The last attribute is the nominal class.
 * <li>The data is sorted in ascending order by timestamp (when averaging multiple instances in
 * the sliding window, the timestamp of the last value is used).
 * <li>The data only contains one class value per file (this is not checked for performance
 * reasons, but it leads to undefined behavior if there is more than one class).
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
	
	public static final String LOGTAG = "FeatureExtractor";
	
	private final File[] mFiles;
	private final Feature[] mFeatures;
	private final int mWindowSize;
	private final int mJumpSize;
	private int mInputInstances;
	private int mOutputFeatures;
	private Instances mOutput = null;
	private boolean mCalculated = false;
	
	/**
	 * Initializes a new instance of the {@link FeatureExtractor} class with the specified
	 * {@link File} and {@link Feature} sets and the specified window and jump sizes for the
	 * sliding window.
	 * 
	 * @param files the files to calculate features for.
	 * @param features the features to calculate, or the single feature {@link Feature#RAW}. If
	 *        any other features than {@link Feature#RAW} are specified, it is ignored.
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
		// Needed to filter RAW and get right order.
		mOutputFeatures = Feature.getMask(features);
		mWindowSize = windowSize;
		mJumpSize = jumpSize;
	}
	
	/**
	 * Gets the array of files assigned to this extractor.<br>
	 * The contents should not be changed.
	 */
	public File[] getFiles()
	{
		return mFiles;
	}
	
	/**
	 * Gets the array of features assigned to this extractor.<br>
	 * The contents should not be changed.
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
	 * When extracting features, gets the number of input instances used. When merging already
	 * extracted features, gets the number of feature instances.
	 */
	public int getNumInputInstances()
	{
		return mInputInstances;
	}
	
	/**
	 * Gets the mask for the features in the output data set.<br>
	 * All occurrences of {@link Feature#RAW} are ignored, if other features are specified.
	 * 
	 * @exception IllegalStateException if {@link #extract()} has not been called yet.
	 * @see #FeatureExtractor(File[], Feature[], int, int)
	 */
	public int getOutputFeatures()
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
	 * Determines whether {@link #extract()} has been called and returned.<br>
	 * Note that this class is not thread safe and {@code extract} may be running when this
	 * method is called.
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
	 * block the calling thread a long time and should not be run on the UI thread.<br>
	 * Note that calls to this method are not synchronized and multiple calls my lead to
	 * undefined behavior.
	 * 
	 * @exception IllegalStateException if {@link #extract()} was called before.
	 * @exception FileNotFoundException if a file from the specified list does not exist.
	 * @exception IOException if another file related error occurred.
	 * @exception UnsupportedAttributeTypeException if the attributes in one of the input files
	 *            are of the wrong format, see the {@link FeatureExtractor class documentation}.
	 * @see AsyncTask
	 */
	public void extract() throws IOException, UnsupportedAttributeTypeException
	{
		if(mCalculated)
			throw new IllegalStateException();
		
		// If RAW was specified, merge input files and return
		if(mOutputFeatures == 0)
		{
			mergeFiles();
			return;
		}
		
		// WindowListener to add extracted features to the output Instances
		final WindowListener windowListener = new WindowListener() {
			@Override
			public void onWindowChanged(Iterable<Instance> window)
			{
				final Instance raw = extractFeatures(window);
				final Instance features = new Instance(mOutput.numAttributes());
				features.setDataset(mOutput);
				features.setClassValue(raw.value(raw.numValues() - 1));
				
				for(int j = 0; j < mOutput.numAttributes() - 1; j++)
					features.setValue(j, raw.value(j + 1));
				
				mOutput.add(features);
			}
		};
		
		for(File f : mFiles)
		{
			final BufferedReader reader = new BufferedReader(new FileReader(f));
			final ArffReader arff = new ArffReader(reader);
			final Instances input = arff.getData();
			
			// For static feature extraction, files need to have timestamp, class and coordinates
			if(input.numAttributes() == 5)
				input.setClassIndex(input.numAttributes() - 1);
			else
				throw new UnsupportedAttributeTypeException(f.toString());
			
			// Initialize output Instances object from data of the first file
			if(mOutput == null)
				mOutput = initOutput(input);
			
			try
			{
				SlidingWindow.slide(input, mWindowSize, mJumpSize, windowListener);
				mInputInstances += input.numInstances();
			}
			catch(UnsupportedAttributeTypeException ex)
			{
				throw new UnsupportedAttributeTypeException(f.toString());
			}
			
			reader.close();
		}
		
		mCalculated = true;
	}
	
	/**
	 * Just merges all input files into a single {@link Instances} data set. Also checks that all
	 * input files have the same features.
	 */
	private void mergeFiles() throws IOException, UnsupportedAttributeTypeException
	{
		HashSet<String> inFeatures = new HashSet<String>();
		
		for(File f : mFiles)
		{
			// Read the next input file
			final BufferedReader reader = new BufferedReader(new FileReader(f));
			final ArffReader arff = new ArffReader(reader);
			final Instances input = arff.getData();
			input.setClassIndex(input.numAttributes() - 1);
			
			if(mOutput == null)
			{
				// Initialize output and fill our HashSet with the feature names
				mOutput = initOutput(input);
				for(Feature f2 : Feature.getFeatures(mOutputFeatures))
					inFeatures.add(f2.getAttribute());
			}
			
			if(input.numAttributes() != mOutput.numAttributes())
				throw new UnsupportedAttributeTypeException(f.toString());
			
			// Check if all input attributes are wanted
			for(int j = 0; j < input.numAttributes() - 1; j++)
			{
				final Attribute a = input.attribute(j);
				if(!a.isNumeric() || !inFeatures.contains(a.name()))
					throw new UnsupportedAttributeTypeException(f.toString());
			}
			
			// Simply add all instances to the output
			@SuppressWarnings("unchecked")
			Enumeration<Instance> instances = input.enumerateInstances();
			while(instances.hasMoreElements())
			{
				final Instance i = new Instance(instances.nextElement());
				i.setDataset(mOutput);
				mOutput.add(i);
			}
			
			mInputInstances += input.numInstances();
			reader.close();
		}
		
		mCalculated = true;
	}
	
	/**
	 * Initializes an output set from the specified instances and sets {@link #mOutputFeatures}
	 * if necessary.
	 */
	private Instances initOutput(Instances input) throws UnsupportedAttributeTypeException
	{
		final FastVector attributes = new FastVector(mFeatures.length + 1);
		double cap = mFiles.length;
		
		if(mOutputFeatures != 0)
		{
			// Features have been specified, no extraction from input file necessary
			for(Feature f : Feature.getFeatures(mOutputFeatures))
				attributes.addElement(new Attribute(f.getAttribute()));
			
			attributes.addElement(
				new Attribute("class", getValueVector(input.classAttribute())));
			
			// Make an educated guess for the needed capacity of the output set
			cap *= (input.lastInstance().value(0) - input.firstInstance().value(0)) / mJumpSize;
		}
		else
		{
			// Extract output features from input file
			final Map<String, Feature> map = Feature.getAttributes();
			@SuppressWarnings("unchecked")
			final Enumeration<Attribute> inAttributes = input.enumerateAttributes();
			
			while(inAttributes.hasMoreElements())
			{
				final Attribute a = inAttributes.nextElement();
				if(a.isNumeric())
				{
					// If name matches celebrate, otherwise go berserk
					if(map.containsKey(a.name()))
					{
						attributes.addElement(a);
						mOutputFeatures |= map.get(a.name()).getBit();
					}
					else
						throw new UnsupportedAttributeTypeException();
				}
				else if(a.isNominal())
				{
					// Class attribute has to be the last one
					if(inAttributes.hasMoreElements())
						throw new UnsupportedAttributeTypeException();
					
					attributes.addElement(new Attribute("class", getValueVector(a)));
				}
				else
					throw new UnsupportedAttributeTypeException();
			}
			
			// Make an educated guess for the needed capacity of the output set
			cap *= input.numInstances();
		}
		
		Instances out = new Instances("lazybird-train-" + System.currentTimeMillis(),
			attributes, (int)cap);
		out.setClassIndex(out.numAttributes() - 1);
		
		return out;
	}
	
	/**
	 * Gets a {@link FastVector} with all possible values of the specified nominal attribute.
	 * 
	 * @param a the {@link Attribute} to get values from
	 * @return a {@link FastVector} with all possible {@link String} values of {@code a}.
	 */
	private FastVector getValueVector(Attribute a)
	{
		@SuppressWarnings("unchecked")
		final Enumeration<String> names = a.enumerateValues();
		final FastVector values = new FastVector();
		while(names.hasMoreElements())
			values.addElement(names.nextElement());
		return values;
	}
	
	/**
	 * Call {@link #extractFeatures(Iterable, int)} with our output features.
	 */
	private Instance extractFeatures(Iterable<Instance> instances)
	{
		return extractFeatures(instances, mOutputFeatures);
	}
	
	/**
	 * Extracts the features specified in the bit mask from the specified instances.
	 * <p>
	 * For more information on the expected instance format see the {@link FeatureExtractor class
	 * documentation}. In the case of this method however, the class attribute is optional.
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
		
		// The output features need to be in the right order, which depends on the definition of
		// the Feature class. To be independent, put all features in a map and get them in the
		// right order
		final EnumMap<Feature, Double> values = new EnumMap<Feature, Double>(Feature.class);
		
		// Check for empty input set
		final Iterator<Instance> it = instances.iterator();
		if(!it.hasNext())
			throw new IllegalArgumentException("instances cannot be empty.");
		
		// All means are extracted at once, so if any one is needed, do it
		if((flags & 0x07) != 0)
		{
			final Instance mean = mean(instances);
			if(Feature.X.isSet(flags))
				values.put(Feature.X, mean.value(1));
			if(Feature.Y.isSet(flags))
				values.put(Feature.Y, mean.value(2));
			if(Feature.Z.isSet(flags))
				values.put(Feature.Z, mean.value(3));
		}
		
		// Magnitude and Variance of the Magnitude both need the magnitude
		if(Feature.MAGNITUDE.isSet(flags) || Feature.VARIANCE_OF_MAGNITUDE.isSet(flags))
		{
			final Iterable<Instance> mag = magnitude(instances);
			
			if(Feature.MAGNITUDE.isSet(flags))
			{
				double sum = 0.0;
				int num = 0;
				for(Instance i : mag)
				{
					num++;
					sum += i.value(1);
				}
				values.put(Feature.MAGNITUDE, sum / num);
			}
			
			if(Feature.VARIANCE_OF_MAGNITUDE.isSet(flags))
				values.put(Feature.VARIANCE_OF_MAGNITUDE, variance(mag, 1).value(1));
		}
		
		// Extract variance features
		if(Feature.VARIANCE_X.isSet(flags))
			values.put(Feature.VARIANCE_X, variance(instances, 1).value(1));
		if(Feature.VARIANCE_Y.isSet(flags))
			values.put(Feature.VARIANCE_Y, variance(instances, 2).value(1));
		if(Feature.VARIANCE_Z.isSet(flags))
			values.put(Feature.VARIANCE_Z, variance(instances, 3).value(1));
		
		// We need the last instance for the timestamp
		Instance last = null;
		while(it.hasNext())
			last = it.next();
		
		// Put all features in an Instance and return it
		final boolean hasClass = (last.numValues() == 5);
		final Feature[] features = Feature.getFeatures(flags);
		final Instance out = new Instance(features.length + (hasClass ? 2 : 1));
		out.setValue(0, last.value(0));
		
		for(int j = 0; j < features.length; j++)
			out.setValue(j + 1, values.get(features[j]));
		if(hasClass)
			out.setValue(features.length + 1, last.value(4));
		
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
	
	/**
	 * Calculates the variance of the attribute with the specified index.
	 * 
	 * @param instances the set of instances to calculate the variance for.
	 * @param idx the index of the needed attribute.
	 * @return an {@link Instance} with the timestamp of the last input instance and the variance
	 *         of the specified index. The class is retained, if present.
	 */
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
		
		it = instances.iterator();
		while(it.hasNext())
		{
			last = it.next();
			var += (last.value(idx) - mean) * (last.value(idx) - mean);
		}
		
		// Input instances can have a number of attributes:
		// * 3: timestamp, coordinate and class
		// * 4: timestamp and three coordinates
		// * 5: timestamp, three coordinates and a class
		final boolean hasClass = (last.numValues() == 5) || (last.numValues() == 3);
		final Instance out = new Instance(hasClass ? 3 : 2);
		out.setValue(0, last.value(0));
		if(hasClass)
			out.setValue(2, last.value(last.numValues() == 3 ? 2 : 4));
		
		out.setValue(1, var / num);
		
		return out;
	}
	
	/**
	 * Calculates the magnitude of the specified instances.
	 * 
	 * @param instances the set of instances to calculate the magnitude for.
	 * @return an {@link Instance} with the timestamp of the last input instance and the
	 *         magnitude. The class is retained, if present.
	 */
	private static Iterable<Instance> magnitude(Iterable<Instance> instances)
	{
		final LinkedList<Instance> out = new LinkedList<Instance>();
		
		for(Instance i : instances)
		{
			final Instance mag = new Instance(i.numValues() - 2);
			mag.setValue(0, i.value(0));
			if(mag.numValues() > 2)
				mag.setValue(2, i.value(4));
			
			final double tmp = i.value(1) * i.value(1) + i.value(2) * i.value(2);
			mag.setValue(1, Math.sqrt(tmp + i.value(3) * i.value(3)));
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
