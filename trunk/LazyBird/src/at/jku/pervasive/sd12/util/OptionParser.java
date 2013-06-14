package at.jku.pervasive.sd12.util;

import java.util.ArrayList;

/**
 * Parse text into several segments. Segments are separated by one of the separator characters (e.g. ',' or
 * ';'). Whitespace around segments will be removed. Segments can be enclosed in quotes (e.g. "text"). Within
 * a quoted segment it is possible to use separator characters and whitespace at the beginning or end of the
 * segment; a quote character within a quoted section has to be escaped with backslash.
 * <p>
 * Behavior of the parser is fully configurable:
 * <ul>
 * <li>Separator characters can be set; default is comma (",").
 * <li>Quotes can be changed; default is single and double quotes ('\'', '"').
 * <li>Whitespace at the beginning and end of segments can be truncated; on by default
 * <li>A parse error can either be thrown as {@link OptionFormatException} (e.g. when a quote is never closed)
 * or just ignored. If errors are ignored, the parser will continue as best as possible.
 * </ul>
 * 
 * @author Michael Matscheko, 2012
 */
public class OptionParser
{
	public static final Quote SINGLE_QUOTE = new Quote('\'', '\'');
	public static final Quote DOUBLE_QUOTE = new Quote('"', '"');
	
	public static class Quote
	{
		public char begin, end;
		public boolean resolveEscape, includeQuote;
		
		/**
		 * Create a new Quote descriptor.
		 * 
		 * @param begin Character with which the quoted section begins.
		 * @param end Character with which the quoted section ends.
		 * @param resolveEscape Resolve escape sequences (\x).
		 * @param includeQuote Include the quote characters in the output.
		 */
		@SuppressWarnings("hiding")
		public Quote(char begin, char end, boolean resolveEscape, boolean includeQuote)
		{
			this.begin = begin;
			this.end = end;
			this.resolveEscape = resolveEscape;
			this.includeQuote = includeQuote;
		}
		
		/**
		 * Create a new Quote descriptor. Escape sequences will be resolved and the quote characters
		 * truncated.
		 * 
		 * @param begin Character with which the quoted section begins.
		 * @param end Character with which the quoted section ends.
		 */
		@SuppressWarnings("hiding")
		public Quote(char begin, char end)
		{
			this(begin, end, true, false);
		}
	}
	
	public static class OptionFormatException extends RuntimeException
	{
		private static final long serialVersionUID = 2011745750452272442L;
		
		public OptionFormatException(String message)
		{
			super(message);
		}
		
	}
	
	private static final String DEFAULT_SEPARATORS = ",";
	private static final Quote[] DEFAULT_QUOTES = { SINGLE_QUOTE, DOUBLE_QUOTE };
	
	private String mSource;
	private int mPosition = 0;
	private String mDefaultSeparators = DEFAULT_SEPARATORS;
	private Quote[] mQuotes = DEFAULT_QUOTES;
	private boolean mWhiteSpaceIgnored = true;
	private boolean mFormatErrorsIgnored = false;
	
	public OptionParser(String src, String separators, Quote[] quotes)
	{
		if(quotes == null || quotes.length == 0)
			throw new IllegalArgumentException("no quotes specified");
		
		mSource = src;
		mDefaultSeparators = separators;
		mQuotes = quotes;
	}
	
	public OptionParser(String src, String separators)
	{
		mSource = src;
		mDefaultSeparators = separators;
	}
	
	public OptionParser(String src)
	{
		mSource = src;
	}
	
	public String getSeparators()
	{
		return mDefaultSeparators;
	}
	
	public void setSeparators(String separators)
	{
		mDefaultSeparators = separators;
	}
	
	public Quote[] getQuotes()
	{
		return mQuotes;
	}
	
	public void setQuotes(Quote[] quotes)
	{
		mQuotes = quotes;
	}
	
	public boolean isWhiteSpaceIgnored()
	{
		return mWhiteSpaceIgnored;
	}
	
	public void setWhiteSpaceIgnored(boolean whiteSpaceIgnored)
	{
		mWhiteSpaceIgnored = whiteSpaceIgnored;
	}
	
	public boolean isFormatErrorsIgnored()
	{
		return mFormatErrorsIgnored;
	}
	
	public void setFormatErrorsIgnored(boolean formatErrorsIgnored)
	{
		mFormatErrorsIgnored = formatErrorsIgnored;
	}
	
	public String parseNext(String separators)
	{
		if(separators == null || separators.length() == 0)
			throw new IllegalArgumentException("no separators specified");
		if(mSource == null) return null;
		
		StringBuilder result = new StringBuilder();
		int i = mPosition, len = mSource.length();
		
		// return one last empty element if last character in text is separator
		if(len > 0 && i == len && separators.indexOf(mSource.charAt(i - 1)) >= 0)
		{
			mPosition++;
			return "";
		}
		
		if(i >= len)
			return null;
		
		if(mWhiteSpaceIgnored)
		{
			// skip leading whitespace
			while(i < len && Character.isWhitespace(mSource.charAt(i)))
				i++;
		}
		
		if(i < len)
		{
			
			boolean quotedOption = false;
			for(Quote q : mQuotes)
			{
				if(mSource.charAt(i) == q.begin)
				{
					// find quoted section
					quotedOption = true;
					if(q.includeQuote) result.append(mSource.charAt(i));
					i++;
					while(i < len && mSource.charAt(i) != q.end)
					{
						if(q.resolveEscape && mSource.charAt(i) == '\\' && i < len - 1 &&
								(mSource.charAt(i + 1) == q.end || mSource.charAt(i + 1) == '\\'))
						{
							i++;
						}
						result.append(mSource.charAt(i));
						i++;
					}
					if(i >= len)
					{
						if(mFormatErrorsIgnored)
							break;
						throw new OptionFormatException("quote not closed");
					}
					if(q.includeQuote) result.append(mSource.charAt(i));
					i++;
					break;
				}
			}
			
			if(!quotedOption)
			{
				// find normal section (no quotes)
				while(i < len && separators.indexOf(mSource.charAt(i)) < 0)
				{
					result.append(mSource.charAt(i));
					i++;
				}
				if(mWhiteSpaceIgnored)
				{
					// remove trailing whitespace from unquoted section
					int j = result.length() - 1;
					while(j > 0 && Character.isWhitespace(result.charAt(j)))
					{
						result.setLength(j);
						j--;
					}
				}
			}
			else
			{
				if(mWhiteSpaceIgnored)
				{
					// skip trailing whitespace after quote
					while(i < len && separators.indexOf(mSource.charAt(i)) < 0 &&
							Character.isWhitespace(mSource.charAt(i)))
					{
						i++;
					}
				}
			}
			
			// if not at end of text, there has to follow a separator
			if(i < len && separators.indexOf(mSource.charAt(i)) < 0)
			{
				if(!mFormatErrorsIgnored)
					throw new OptionFormatException("separator expected");
			}
			else
				i++;
		}
		
		mPosition = i;
		return result.toString();
	}
	
	public String parseNext()
	{
		return parseNext(mDefaultSeparators);
	}
	
	/**
	 * Split text by one or more separator characters in multiple parts. Parts may be quoted, e.g. in double
	 * quotes, "first part" "second part". If a parse error occurs (e.g. a quote is not closed) a
	 * {@link OptionFormatException} will be thrown.
	 * 
	 * @param src Text to split
	 * @param separators String with separator characters. Throws {@link IllegalArgumentException} if null.
	 * @param quotes List of allowed quotes. Throws {@link IllegalArgumentException} if null.
	 * @return Separated text sections. Never null and never empty. Contains a single empty String ("") if src
	 *         is null or empty.
	 */
	public static String[] split(String src, String separators, Quote[] quotes)
	{
		OptionParser p = new OptionParser(src, separators, quotes);
		ArrayList<String> r = new ArrayList<String>(8);
		String next;
		while((next = p.parseNext()) != null)
		{
			r.add(next);
		}
		if(r.size() == 0)
			r.add("");
		
		return r.toArray(new String[r.size()]);
	}
	
	public static String[] split(String src, String separators)
	{
		return OptionParser.split(src, separators, DEFAULT_QUOTES);
	}
	
	public static String[] split(String src)
	{
		return OptionParser.split(src, DEFAULT_SEPARATORS, DEFAULT_QUOTES);
	}
	
	/**
	 * Split text by one or more separator characters in multiple parts. Parts may be quoted, e.g. in double
	 * quotes, "first part" "second part". Parse errors (e.g. a quote is not closed) will be ignored and the
	 * method will try to continue in the most meaningful way.
	 * 
	 * @param src Text to split
	 * @param separators String with separator characters. Throws {@link IllegalArgumentException} if null.
	 * @param quotes List of allowed quotes. Throws {@link IllegalArgumentException} if null.
	 * @return Separated text sections. Never null and never empty. Contains a single empty String ("") if src
	 *         is null or empty.
	 */
	public static String[] splitYielding(String src, String separators, Quote[] quotes)
	{
		OptionParser p = new OptionParser(src, separators, quotes);
		p.setFormatErrorsIgnored(true);
		ArrayList<String> r = new ArrayList<String>(8);
		String next;
		while((next = p.parseNext()) != null)
		{
			r.add(next);
		}
		if(r.size() == 0)
			r.add("");
		
		return r.toArray(new String[r.size()]);
	}
	
	public static String[] splitYielding(String src, String separators)
	{
		return OptionParser.splitYielding(src, separators, DEFAULT_QUOTES);
	}
	
	public static String[] splitYielding(String src)
	{
		return OptionParser.splitYielding(src, DEFAULT_SEPARATORS, DEFAULT_QUOTES);
	}
}
