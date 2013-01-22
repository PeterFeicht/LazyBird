package at.jku.pci.lazybird.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * A {@link TextView} with a background and rounded border that has an age bar at the bottom.
 * <p>
 * An age of {@code 0} means a full bar, an age of {@link #MAX_AGE} means the bar is empty.
 * 
 * @author Peter
 */
public class UserActivityView extends TextView
{
	public static final String LOGTAG = "UserActivityView";
	public static final boolean LOCAL_LOGV = true;
	
	public static final int MAX_AGE = 10000;
	public static final int AGE_BAR_HEIGHT = 4;
	public static final int AGE_BAR_PADDING = 5;
	public static final int AGE_BAR_BG = 0x22000000;
	public static final int AGE_BAR_FG = 0x66000000;
	public static final int CORNER_RADIUS = 6;
	public static final int STROKE_WIDTH = 2;
	
	private int mAge = 5000;
	private int mAgeBarTop;
	private int mAgeBarLeft;
	private int mAgeBarWidth;
	private int mAgeBarHeight = 4;
	private int mAgeBarPadding = 1;
	
	private GradientDrawable mBackground;
	private GradientDrawable mAgeBackground;
	private GradientDrawable mAgeBar;
	
	public UserActivityView(Context context)
	{
		super(context);
		init(null, 0);
	}
	
	public UserActivityView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init(attrs, 0);
	}
	
	public UserActivityView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init(attrs, defStyle);
	}
	
	private void init(AttributeSet attrs, int defStyle)
	{
		final Resources res = getContext().getResources();
		final float dp = res.getDisplayMetrics().density;
		mAgeBarHeight = (int)(AGE_BAR_HEIGHT * dp);
		mAgeBarPadding = (int)(AGE_BAR_PADDING * dp);
		
		mBackground = new GradientDrawable();
		mBackground.setShape(GradientDrawable.RECTANGLE);
		mBackground.setCornerRadius(CORNER_RADIUS * dp);
		mBackground.setStroke((int)(STROKE_WIDTH * dp), Color.BLACK);
		mBackground.setColor(res.getColor(android.R.color.holo_blue_dark));
		
		setBackgroundDrawable(mBackground);
		setPadding((int)(9 * dp), (int)(6 * dp),
			(int)(9 * dp), (int)((9 + 2 * AGE_BAR_PADDING) * dp));
		setTextAppearance(getContext(), android.R.style.TextAppearance_Large);
		
		mAgeBackground = new GradientDrawable();
		mAgeBackground.setShape(GradientDrawable.RECTANGLE);
		mAgeBackground.setColor(AGE_BAR_BG);
		mAgeBackground.setCornerRadius(mAgeBarHeight / 2f);
		
		mAgeBar = new GradientDrawable();
		mAgeBar.setShape(GradientDrawable.RECTANGLE);
		mAgeBar.setColor(AGE_BAR_FG);
		mAgeBar.setCornerRadius(mAgeBarHeight / 2f);
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);
		
		mAgeBarLeft = getCompoundPaddingLeft() + mAgeBarPadding;
		mAgeBarTop = h - getCompoundPaddingBottom() + mAgeBarPadding;
		mAgeBarWidth =
			w - getCompoundPaddingLeft() - getCompoundPaddingRight() - 2 * mAgeBarPadding;
		
		mAgeBackground.setBounds(
			mAgeBarLeft,
			mAgeBarTop,
			mAgeBarLeft + mAgeBarWidth,
			mAgeBarTop + mAgeBarHeight);
		onAgeChanged();
	}
	
	private void onAgeChanged()
	{
		int width = (mAgeBarWidth * (MAX_AGE - mAge)) / MAX_AGE;
		mAgeBar.setBounds(
			mAgeBarLeft,
			mAgeBarTop,
			mAgeBarLeft + (width > 0 ? width : 0),
			mAgeBarTop + mAgeBarHeight);
		postInvalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		mAgeBackground.draw(canvas);
		mAgeBar.draw(canvas);
	}
	
	/**
	 * Gets the age for this view.
	 */
	public int getAge()
	{
		return mAge;
	}
	
	/**
	 * Sets the age for this view.
	 * 
	 * @param age the age to set, cannot be less than {@code 0}.
	 */
	public void setAge(int age)
	{
		if(age < 0)
			throw new IllegalArgumentException();
		
		mAge = age;
		onAgeChanged();
	}
	
	/**
	 * Sets the background color of this view.
	 */
	public void setBackgroundColor(int argb)
	{
		mBackground.setColor(argb);
		postInvalidate();
	}
}
