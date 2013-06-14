package at.jku.pci.lazybird;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import java.util.LinkedList;

public class FlowLayout extends ViewGroup
{
	private int mHorizontalSpacing;
	private int mVerticalSpacing;
	private boolean mDistributeHorizontal;
	private LinkedList<Integer> mLineBreaks = new LinkedList<Integer>();
	
	public FlowLayout(Context context)
	{
		super(context);
	}
	
	public FlowLayout(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FlowLayout);
		try
		{
			mHorizontalSpacing = a.getDimensionPixelSize(R.styleable.FlowLayout_horizontalSpacing, 0);
			mVerticalSpacing = a.getDimensionPixelSize(R.styleable.FlowLayout_verticalSpacing, 0);
			mDistributeHorizontal = a.getBoolean(R.styleable.FlowLayout_distributeHorizontal, false);
		}
		finally
		{
			a.recycle();
		}
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		boolean growHeight = (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED);
		mLineBreaks.clear();
		
		int width = 0;
		int height = getPaddingTop();
		
		int currentWidth = getPaddingLeft();
		int currentHeight = 0;
		
		boolean breakLine = false;
		int spacing = 0;
		
		final int count = getChildCount();
		for(int j = 0; j < count; j++)
		{
			View child = getChildAt(j);
			if(child.getVisibility() == View.GONE)
				continue;
			
			LayoutParams lp = (LayoutParams)child.getLayoutParams();
			measureChild(child, widthMeasureSpec, heightMeasureSpec);
			
			spacing = mHorizontalSpacing;
			if(lp.spacing > -1)
				spacing = lp.spacing;
			
			if(growHeight && (breakLine || currentWidth + child.getMeasuredWidth() > widthSize))
			{
				height += currentHeight + mVerticalSpacing;
				currentHeight = 0;
				width = Math.max(width, currentWidth - spacing);
				currentWidth = getPaddingLeft();
				mLineBreaks.add(j);
			}
			
			lp.x = currentWidth;
			lp.y = height;
			
			currentWidth += child.getMeasuredWidth() + spacing;
			currentHeight = Math.max(currentHeight, child.getMeasuredHeight());
			
			breakLine = lp.breakLine;
		}
		
		width = Math.max(width, currentWidth - spacing) + getPaddingRight();
		height += currentHeight + getPaddingBottom();
		
		setMeasuredDimension(View.resolveSize(width, widthMeasureSpec), View.resolveSize(height, heightMeasureSpec));
	}
	
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		final int count = getChildCount();
		
		// If horizontal distribution is enabled, move children to fill the measured width
		if(mDistributeHorizontal && count > 1)
		{
			int lastIdx = 0;
			
			// For every line but the last one, distribute the views equally
			for(Integer line : mLineBreaks)
			{
				int width = 0;
				// Sum up all widths to calculate remaining space
				for(int j = lastIdx; j < line; j++)
				{
					final View v = getChildAt(j);
					if(v.getVisibility() == View.GONE)
						continue;
					width += v.getMeasuredWidth();
				}
				
				width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - width;
				final int space = width / (line - lastIdx - 1);
				
				int nextX = getChildAt(lastIdx + 1).getMeasuredWidth() + space;
				for(int j = lastIdx + 1; j < line; j++)
				{
					final View v = getChildAt(j);
					if(v.getVisibility() == View.GONE)
						continue;
					((LayoutParams)v.getLayoutParams()).x = nextX;
					nextX += v.getMeasuredWidth() + space;
				}
				lastIdx = line;
			}
		}
		
		for(int j = 0; j < count; j++)
		{
			View child = getChildAt(j);
			if(child.getVisibility() == View.GONE)
				continue;
			
			LayoutParams lp = (LayoutParams)child.getLayoutParams();
			child.layout(lp.x, lp.y, lp.x + child.getMeasuredWidth(), lp.y + child.getMeasuredHeight());
		}
	}
	
	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p)
	{
		return p instanceof LayoutParams;
	}
	
	@Override
	protected LayoutParams generateDefaultLayoutParams()
	{
		return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
	}
	
	@Override
	public LayoutParams generateLayoutParams(AttributeSet attrs)
	{
		return new LayoutParams(getContext(), attrs);
	}
	
	@Override
	protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p)
	{
		return new LayoutParams(p.width, p.height);
	}
	
	public static class LayoutParams extends ViewGroup.LayoutParams
	{
		public boolean breakLine = false;
		public int spacing = -1;
		
		int x;
		int y;
		
		@SuppressWarnings("hiding")
		public LayoutParams(int width, int height)
		{
			super(width, height);
		}
		
		public LayoutParams(Context context, AttributeSet attrs)
		{
			super(context, attrs);
			
			TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FlowLayout_LayoutParams);
			try
			{
				spacing = a.getDimensionPixelSize(R.styleable.FlowLayout_LayoutParams_layout_spacing, spacing);
				breakLine = a.getBoolean(R.styleable.FlowLayout_LayoutParams_layout_breakLine, breakLine);
			}
			finally
			{
				a.recycle();
			}
		}
	}
}
