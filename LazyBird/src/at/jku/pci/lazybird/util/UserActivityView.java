package at.jku.pci.lazybird.util;

import android.content.Context;
import android.content.res.TypedArray;
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
 * An age of {@code 0} means a full bar, an age of {@link #getMaxAge()} means the bar is empty.
 * 
 * @author Peter
 */
public class UserActivityView extends TextView implements Comparable<UserActivityView>
{
	// Constants
	static final String LOGTAG = "UserActivityView";
	static final boolean LOCAL_LOGV = true;
	// Default values
	public static final int MAX_AGE = 10000;
	public static final int AGE_BAR_HEIGHT = 4;
	public static final int AGE_BAR_PADDING = 5;
	public static final int AGE_BAR_OFFSET = 2;
	public static final int AGE_BAR_BG = 0x22000000;
	public static final int AGE_BAR_FG = 0x66000000;
	public static final int BG_CORNER_RADIUS = 6;
	public static final int BG_STROKE_WIDTH = 2;
	
	// Static fields
	protected static Drawable sRoleTransition = null;
	protected static Drawable sRoleSpeaker = null;
	protected static Drawable sRoleListener = null;
	
	// Property fields
	private int mMaxAge = MAX_AGE;
	private long mAge = 5000;
	private boolean mShowOffline = true;
	private ClassLabel mActivity = null;
	private UserRole mRole = null;
	private int mAgeBarBackground = AGE_BAR_BG;
	private int mAgeBarForeground = AGE_BAR_FG;
	private int mAgeBarWidth;
	private int mAgeBarHeight;
	private int mAgeBarPadding;
	
	// Fields
	private int mAgeBarTop;
	private int mAgeBarLeft;
	private int mAgeBarOffset;
	
	private final Runnable mRunUpdateBackgroundColor = new Runnable() {
		@Override
		public void run()
		{
			setBackgroundColor(getActivityColor());
		}
	};
	private final Runnable mRunUpdateRoleDrawable = new Runnable() {
		@Override
		public void run()
		{
			final Drawable[] cd = getCompoundDrawables();
			setCompoundDrawablesWithIntrinsicBounds(getRoleDrawable(), cd[1], cd[2], cd[3]);
		}
	};
	private GradientDrawable mBackground;
	private GradientDrawable mAgeBackground;
	private GradientDrawable mAgeBar;
	
	public UserActivityView(Context context)
	{
		this(context, null);
	}
	
	public UserActivityView(Context context, AttributeSet attrs)
	{
		super(context, attrs, R.attr.userActivityViewStyle);
		init(attrs, R.attr.userActivityViewStyle);
	}
	
	public UserActivityView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init(attrs, defStyle);
	}

	private final void init(AttributeSet attrs, int defStyle)
	{
		// Set defaults for values depending on display metrics
		final float dp = getResources().getDisplayMetrics().density;
		mAgeBarHeight = (int)(AGE_BAR_HEIGHT * dp);
		mAgeBarPadding = (int)(AGE_BAR_PADDING * dp);
		mAgeBarOffset = (int)(AGE_BAR_OFFSET * dp);
		
		if(attrs != null)
		{
			final TypedArray a = getContext().obtainStyledAttributes(attrs,
				R.styleable.UserActivityView, defStyle, 0);
			final int count = a.getIndexCount();
			for(int j = 0; j < count; j++)
			{
				int attr = a.getIndex(j);
				switch(attr)
				{
					case R.styleable.UserActivityView_age:
						mAge = a.getInt(attr, 5000);
						break;
					case R.styleable.UserActivityView_maxAge:
						mMaxAge = a.getInt(attr, MAX_AGE);
						break;
					case R.styleable.UserActivityView_showOffline:
						mShowOffline = a.getBoolean(attr, true);
						break;
					case R.styleable.UserActivityView_activity:
						final String activity = a.getString(attr);
						// Background color is set later
						if(activity != null)
							mActivity = ClassLabel.parse(activity);
						break;
					case R.styleable.UserActivityView_role:
						final String role = a.getString(attr);
						// Set compound drawable immediately
						if(role != null)
						{
							mRole = UserRole.parse(role);
							final Drawable[] cd = getCompoundDrawables();
							setCompoundDrawablesWithIntrinsicBounds(getRoleDrawable(),
								cd[1], cd[2], cd[3]);
						}
						break;
					case R.styleable.UserActivityView_ageBarHeight:
						mAgeBarHeight = a.getDimensionPixelSize(attr, mAgeBarHeight);
						break;
					case R.styleable.UserActivityView_ageBarPadding:
						mAgeBarPadding = a.getDimensionPixelSize(attr, mAgeBarPadding);
						break;
					case R.styleable.UserActivityView_ageBarBackgroundColor:
						mAgeBarBackground = a.getColor(attr, AGE_BAR_BG);
						break;
					case R.styleable.UserActivityView_ageBarForegroundColor:
						mAgeBarForeground = a.getColor(attr, AGE_BAR_FG);
						break;
				}
			}
			a.recycle();
		}
		
		mBackground = new GradientDrawable();
		mBackground.setShape(GradientDrawable.RECTANGLE);
		mBackground.setCornerRadius(BG_CORNER_RADIUS * dp);
		mBackground.setStroke((int)(BG_STROKE_WIDTH * dp), Color.BLACK);
		mBackground.setColor(getActivityColor());
		
		setBackgroundDrawable(mBackground);
		setPadding((int)(9 * dp), (int)(6 * dp), (int)(9 * dp), (int)(6 * dp));
		setCompoundDrawablePadding((int)(6 * dp));
		
		mAgeBackground = new GradientDrawable();
		mAgeBackground.setShape(GradientDrawable.RECTANGLE);
		mAgeBackground.setColor(mAgeBarBackground);
		mAgeBackground.setCornerRadius(mAgeBarHeight / 2f);
		
		mAgeBar = new GradientDrawable();
		mAgeBar.setShape(GradientDrawable.RECTANGLE);
		mAgeBar.setColor(mAgeBarForeground);
		mAgeBar.setCornerRadius(mAgeBarHeight / 2f);
		onAgeChanged();
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		
		// Parent told us how large to be, make it so
		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		if(heightMode == MeasureSpec.EXACTLY)
			return;
		
		// The value for the line height was determined experimentally, I have no idea where it
		// comes from
		final int lineHeight = (int)(getTextSize() * 1.36f);
		final Drawable[] cd = getCompoundDrawables();
		
		// Check whether compound drawables are larger than text size
		int diff = 0;
		if(cd[0] != null)
			diff = Math.max(diff, cd[0].getBounds().bottom - lineHeight);
		if(cd[2] != null)
			diff = Math.max(diff, cd[2].getBounds().bottom - lineHeight);
		
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
	
	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		mAgeBackground.draw(canvas);
		if(!isOffline())
			mAgeBar.draw(canvas);
	}
	
	/**
	 * Updates the width of the age bar, the background color and visibility.
	 */
	protected void onAgeChanged()
	{
		int width = (int)((mAgeBarWidth * (mMaxAge - mAge)) / mMaxAge);
		mAgeBar.setBounds(
			mAgeBarLeft,
			mAgeBarTop,
			mAgeBarLeft + (width > 0 ? width : 0),
			mAgeBarTop + mAgeBarHeight);
		
		post(mRunUpdateBackgroundColor);
		if(isOffline())
			setVisibility(mShowOffline ? View.VISIBLE : View.GONE);
		postInvalidate();
	}
	
	/**
	 * Gets the appropriate color for this view's activity and age.
	 */
	protected int getActivityColor()
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
		return color;
	}
	
	/**
	 * Gets the appropriate role drawable for this view's role.
	 */
	protected Drawable getRoleDrawable()
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
		return role;
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
	 * Gets whether this view will be hidden if it is offline.
	 * 
	 * @see #isOffline()
	 */
	public boolean getShowOffline()
	{
		return mShowOffline;
	}
	
	/**
	 * Sets whether this view should be shown even if it is offline.
	 * 
	 * @param showOffline {@code true} to always show the view, {@code false} otherwise.
	 * @see #isOffline()
	 */
	public void setShowOffline(boolean showOffline)
	{
		mShowOffline = showOffline;
		setVisibility((!showOffline && isOffline()) ? View.GONE : View.VISIBLE);
	}
	
	/**
	 * Gets whether the age of this view is greater than {@link #getMaxAge()}.
	 */
	public boolean isOffline()
	{
		return mAge > mMaxAge;
	}
	
	/**
	 * Sets the background color of this view. Note that it is safe to call this method from a
	 * non-UI thread.
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
	
	/**
	 * Sets the activity class label for this view, updating the background color. Note that it
	 * is safe to call this method from a non-UI thread.
	 * 
	 * @param role the new {@link ClassLabel} for this view, or {@code null}.
	 */
	public void setActivity(ClassLabel activity)
	{
		mActivity = activity;
		post(mRunUpdateBackgroundColor);
	}
	
	/**
	 * Gets the role last set for this view, or {@code null} if none was set.
	 */
	public UserRole getRole()
	{
		return mRole;
	}
	
	/**
	 * Sets the role for this view, updating the role image. Note that it is safe to call this
	 * method from a non-UI thread.
	 * 
	 * @param role the new {@link UserRole} for this view, or {@code null}.
	 */
	public void setRole(UserRole role)
	{
		mRole = role;
		post(mRunUpdateRoleDrawable);
	}
	
	/**
	 * Gets the maximum age for this view, that is the age after which the view is considered
	 * offline.
	 */
	public long getMaxAge()
	{
		return mMaxAge;
	}
	
	/**
	 * Sets the maximum age for this view, that is the age after which the view is considered
	 * offline.
	 * 
	 * @param maxAge the maximum age, needs to be positive.
	 */
	public void setMaxAge(int maxAge)
	{
		if(maxAge < 1)
			throw new IllegalArgumentException("maxAge needs to be positive.");
		mMaxAge = maxAge;
	}
	
	/**
	 * Compares this object to the specified object to determine their relative order,
	 * considering their offline status and text. A view that is offline is considered larger
	 * than an online view, so that offline views will be sorted after online views.
	 * 
	 * @param another the object to compare to this instance.
	 * @return a negative integer if this instance is less than {@code another}; a positive
	 *         integer if this instance is greater than {@code another}; 0 if this instance has
	 *         the same order as {@code another}.
	 */
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
