package at.jku.pci.lazybird;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

/**
 * A preference that displays a dialog with a {@link NumberPicker}.
 * 
 * @author Peter
 */
public class NumberPreference extends DialogPreference
{
	public static final String LOGTAG = "NumberPreference";
	public static final boolean LOCAL_LOGV = true;
	
	public static final int DEFAULT_VALUE = 0;
	public static final int DEFAULT_MIN_VALUE = 0;
	public static final int DEFAULT_MAX_VALUE = 100;
	
	protected int mMinValue = DEFAULT_MIN_VALUE;
	protected int mMaxValue = DEFAULT_MAX_VALUE;
	protected int mValue = 0;
	protected NumberPicker mPicker = null;
	
	public NumberPreference(Context context, AttributeSet attrs)
	{
		this(context, attrs, 0);
	}
	
	public NumberPreference(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		setPositiveButtonText(android.R.string.ok);
		setNegativeButtonText(android.R.string.cancel);
		
		// Get min and max value from XML
		final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumberPreference);
		for(int j = 0; j < a.getIndexCount(); j++)
		{
			final int attr = a.getIndex(j);
			switch(attr)
			{
				case R.styleable.NumberPreference_maxValue:
					mMaxValue = a.getInteger(attr, DEFAULT_MAX_VALUE);
					break;
				case R.styleable.NumberPreference_minValue:
					mMinValue = a.getInteger(attr, DEFAULT_MIN_VALUE);
					break;
			}
		}
	}
	
	@Override
	public String toString()
	{
		return Integer.toString(mValue);
	}
	
	@Override
	protected void onBindDialogView(View v)
	{
		super.onBindDialogView(v);
		mPicker = (NumberPicker)v.findViewById(R.id.number_preference_picker);
		mPicker.setMinValue(mMinValue);
		mPicker.setMaxValue(mMaxValue);
		if(mValue > mMaxValue)
			mPicker.setValue(mMaxValue);
		else if(mValue < mMinValue)
			mPicker.setValue(mMinValue);
		else
			mPicker.setValue(mValue);
	}
	
	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		super.onDialogClosed(positiveResult);
		
		if(positiveResult)
		{
			mPicker.clearFocus();
			mValue = mPicker.getValue();
			
			if(callChangeListener(mValue))
			{
				persistInt(mValue);
				setSummary(Integer.toString(mValue));
			}
		}
	}
	
	@Override
	protected Object onGetDefaultValue(TypedArray a, int index)
	{
		return a.getInt(index, 0);
	}
	
	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
	{
		int time = DEFAULT_VALUE;
		
		if(restoreValue)
		{
			time = getPersistedInt(0);
		}
		else
		{
			if(defaultValue != null)
			{
				if(Integer.class.isAssignableFrom(defaultValue.getClass()))
				{
					time = (Integer)defaultValue;
				}
				else if(String.class.isAssignableFrom(defaultValue.getClass()))
				{
					try
					{
						time = Integer.parseInt((String)defaultValue);
					}
					catch(NumberFormatException ex)
					{
						time = 0;
					}
				}
			}
			
			if(shouldPersist()) persistInt(time);
		}
		
		mValue = time;
		setSummary(Integer.toString(mValue));
	}
}
