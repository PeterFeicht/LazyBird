package at.jku.pci.lazybird.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import at.jku.pci.lazybird.R;
import at.jku.pervasive.sd12.actclient.ClassLabel;
import at.jku.pervasive.sd12.actclient.UserRole;

/**
 * A {@link TextView} with a background and rounded border that has an age bar at the bottom.
 * <p>
 * An age of {@code 0} means a full bar, an age of {@link #MAX_AGE} means the bar is empty.
 * 
 * @author Peter
 */
public class UserActivityView extends TextView implements Comparable<UserActivityView>
{
	// Constants
	public static final String LOGTAG = "UserActivityView";
	public static final boolean LOCAL_LOGV = true;
	
	public static final long MAX_AGE = 10000;
	public static final int AGE_BAR_HEIGHT = 4;
	public static final int AGE_BAR_PADDING = 5;
	public static final int AGE_BAR_OFFSET = 2;
	public static final int AGE_BAR_BG = 0x22000000;
	public static final int AGE_BAR_FG = 0x66000000;
	public static final int CORNER_RADIUS = 6;
	public static final int STROKE_WIDTH = 2;
	
	// Static fields
	protected static Drawable sRoleTransition = null;
	protected static Drawable sRoleSpeaker = null;
	protected static Drawable sRoleListener = null;
	
	// Fields
	private long mAge = 5000;
	private boolean mShowOffline = true;
	private ClassLabel mActivity = null;
	private UserRole mRole = null;
	private int mAgeBarTop;
	private int mAgeBarLeft;
	private int mAgeBarWidth;
	private int mAgeBarHeight = 4;
	private int mAgeBarPadding = 1;
	private int mAgeBarOffset = 1;
	
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
		mAgeBarOffset = (int)(AGE_BAR_OFFSET * dp);
		
		mBackground = new GradientDrawable();
		mBackground.setShape(GradientDrawable.RECTANGLE);
		mBackground.setCornerRadius(CORNER_RADIUS * dp);
		mBackground.setStroke((int)(STROKE_WIDTH * dp), Color.BLACK);
		mBackground.setColor(res.getColor(android.R.color.holo_blue_dark));
		
		setBackgroundDrawable(mBackground);
		setPadding((int)(9 * dp), (int)(6 * dp), (int)(9 * dp), (int)(6 * dp));
		setCompoundDrawablePadding((int)(6 * dp));
		setTextAppearance(context, android.R.style.TextAppearance_Medium);
		
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
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		
		// Parent told us how large to be, make it so
		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		if(heightMode == MeasureSpec.EXACTLY)
			return;
		
		// The value of 1.35 for the text size plus text padding was determined experimentally,
		// I have no idea where it comes from
		final int textSize = (int)(getTextSize() * 1.35f);
		final Drawable[] cd = getCompoundDrawables();
		
		// Check whether compound drawables are larger than text size
		int diff = 0;
		if(cd[0] != null)
			diff = Math.max(diff, cd[0].getBounds().bottom - textSize);
		if(cd[2] != null)
			diff = Math.max(diff, cd[2].getBounds().bottom - textSize);
		
		diff = (mAgeBarHeight + 2 * mAgeBarPadding - mAgeBarOffset) - diff;
		
		// Compound drawables already enlarged the view enough for the age bar to fit, return
		if(diff <= 0)
			return;
		
		// Enlarge the view by the needed age bar space
		int height = getMeasuredHeight() + diff;
		if(heightMode == MeasureSpec.AT_MOST)
			height = Math.min(MeasureSpec.getSize(heightMeasureSpec), height);
		
		setMeasuredDimension(getMeasuredWidthAndState(), height);
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);
		
		mAgeBarLeft = getCompoundPaddingLeft() + mAgeBarPadding;
		mAgeBarTop =
			h - getCompoundPaddingBottom() - mAgeBarHeight - mAgeBarPadding + mAgeBarOffset;
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
		
		updateBackgroundColor();
		if(isOffline())
			setVisibility(mShowOffline ? View.VISIBLE : View.GONE);
		postInvalidate();
	}
	
	private void onRoleChanged()
	{
		Drawable role = null;
		if(mRole != null)
		{
			switch(mRole)
			{
				case listener:
					if(sRoleListener == null)
						sRoleListener = getResources().getDrawable(R.drawable.role_listener);
					role = sRoleListener;
					break;
				case speaker:
					if(sRoleSpeaker == null)
						sRoleSpeaker = getResources().getDrawable(R.drawable.role_speaker);
					role = sRoleSpeaker;
					break;
				default:
					if(sRoleTransition == null)
					{
						sRoleTransition =
								getResources().getDrawable(R.drawable.role_transitional);
					}
					role = sRoleTransition;
			}
		}
		
		final Drawable newRole = role;
		final Drawable[] cd = getCompoundDrawables();
		
		post(new Runnable() {
			@Override
			public void run()
			{
				setCompoundDrawablesWithIntrinsicBounds(newRole, cd[1], cd[2], cd[3]);
			}
		});
	}
	
	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		mAgeBackground.draw(canvas);
		if(!isOffline())
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
	@Override
	public void setBackgroundColor(int argb)
	{
		mBackground.setColor(argb);
		postInvalidate();
	}
	
	/**
	 * Gets the activity class label last set for this view, or {@code null} if none was set.
	 */
	public ClassLabel getActivity()
	{
		return mActivity;
	}
	
	public void setActivity(ClassLabel activity)
	{
		mActivity = activity;
		updateBackgroundColor();
	}
	
	private void updateBackgroundColor()
	{
		int color;
		if(isOffline())
		{
			color = getContext().getResources().getColor(R.color.offlineColor);
		}
		else
		{
			if(mActivity == null)
				color = getContext().getResources().getColor(R.color.nullColor);
			else
			{
				switch(mActivity)
				{
					case sitting:
						color = getContext().getResources().getColor(R.color.sittingColor);
						break;
					case standing:
						color = getContext().getResources().getColor(R.color.standingColor);
						break;
					case walking:
						color = getContext().getResources().getColor(R.color.walkingColor);
						break;
					default:
						color = getContext().getResources().getColor(R.color.nullColor);
						break;
				}
			}
		}
		
		final int newColor = color;
		post(new Runnable() {
			@Override
			public void run()
			{
				setBackgroundColor(newColor);
			}
		});
	}
	
	/**
	 * Gets the role last set for this view, or {@code null} if none was set.
	 */
	public UserRole getRole()
	{
		return mRole;
	}
	
	public void setRole(UserRole role)
	{
		mRole = role;
		onRoleChanged();
	}
	
	@Override
	public int compareTo(UserActivityView another)
	{
		if(this.isOffline() && !another.isOffline())
			return 1;
		if(!this.isOffline() && another.isOffline())
			return -1;
		return compareToText(another);
	}
	
	/**
	 * Compares this object to the specified object to determine their relative order, only
	 * considering their text.
	 * 
	 * @param another the object to compare to this instance.
	 * @return a negative integer if this instance is less than {@code another}; a positive
	 *         integer if this instance is greater than {@code another}; 0 if this instance has
	 *         the same order as {@code another}.
	 */
	public int compareToText(UserActivityView another)
	{
		return ((String)getText()).compareTo((String)another.getText());
	}
}
