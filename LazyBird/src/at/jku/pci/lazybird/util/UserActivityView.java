package at.jku.pci.lazybird.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
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
	// Constants
	public static final String LOGTAG = "UserActivityView";
	public static final boolean LOCAL_LOGV = true;
	
	public static final long MAX_AGE = 10000;
	public static final int AGE_BAR_HEIGHT = 4;
	public static final int AGE_BAR_PADDING = 5;
	public static final int AGE_BAR_BG = 0x22000000;
	public static final int AGE_BAR_FG = 0x66000000;
	public static final int CORNER_RADIUS = 6;
	public static final int STROKE_WIDTH = 2;
	
	// Fields
	private long mAge = 5000;
	private boolean mShowOffline = true;
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
		init(context);
	}
	
	public UserActivityView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init(context);
	}

	private void init(Context context)
	{
		final Resources res = context.getResources();
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
		setTextAppearance(context, android.R.style.TextAppearance_Large);
		
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
		int width = (int)((mAgeBarWidth * (MAX_AGE - mAge)) / MAX_AGE);
		mAgeBar.setBounds(
			mAgeBarLeft,
			mAgeBarTop,
			mAgeBarLeft + (width > 0 ? width : 0),
			mAgeBarTop + mAgeBarHeight);
		
		if(isOffline())
			setVisibility(mShowOffline ? View.VISIBLE : View.GONE);
		else
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
	public long getAge()
	{
		return mAge;
	}
	
	/**
	 * Sets the age for this view.
	 * 
	 * @param age the age to set, cannot be less than {@code 0}.
	 */
	public void setAge(long age)
	{
		if(age < 0)
			throw new IllegalArgumentException();
		
		mAge = age;
		onAgeChanged();
	}
	
	/**
	 * Gets whether this view will be hidden if the age is greater than {@link #MAX_AGE}.
	 */
	public boolean getShowOffline()
	{
		return mShowOffline;
	}
	
	/**
	 * Sets whether this view should be shown even if the age is greater than {@link #MAX_AGE}.
	 * 
	 * @param showOffline {@code true} to always show the view, {@code false} to hide it, if the
	 *        age is greater than {@link #MAX_AGE}.
	 */
	public void setShowOffline(boolean showOffline)
	{
		mShowOffline = showOffline;
		setVisibility((!showOffline && isOffline()) ? View.GONE : View.VISIBLE);
	}
	
	/**
	 * Gets whether the age of this view is greater than {@link #MAX_AGE}.
	 */
	public boolean isOffline()
	{
		return mAge > MAX_AGE;
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
