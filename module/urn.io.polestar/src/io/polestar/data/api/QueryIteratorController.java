package io.polestar.data.api;

import io.polestar.api.IPolestarMatcher;

class QueryIteratorController
{
	interface IQueryIteratorController
	{
		/** Accept next value of sensor
		 * @return true when iteration should continue, false to break
		 * @param aValue value of sensor
		 * @param aTimestamp timestamp of sensor value
		 * @param aIndex index of value from beginning
		 * @param aDuration duration of reading since previous or since start/end boundary
		 */
		boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex);
		
		/** Return the result after iteration completes */
		Object getResult();
	}

	
	public static IQueryIteratorController getFirstValueInstance()
	{	return new IQueryIteratorController()
		{	private Object mValue;
		
			public Object getResult()
			{	return mValue;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	mValue=aValue;
				return false;
			}
		};
	}
	
	public static IQueryIteratorController getFirstMatchValueInstance(final IPolestarMatcher matcher)
	{	return new IQueryIteratorController()
		{	private Object mValue;
		
			public Object getResult()
			{	return mValue;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (matcher.matches(aValue, aTimestamp))
				{	mValue=aValue;
					return false;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getFirstMatchTimeInstance(final IPolestarMatcher matcher)
	{	return new IQueryIteratorController()
		{	private Object mValue;
		
			public Object getResult()
			{	return mValue;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (matcher.matches(aValue, aTimestamp))
				{	mValue=aTimestamp;
					return false;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getMatchDurationInstance(final IPolestarMatcher matcher)
	{	return new IQueryIteratorController()
		{	private long mDuration;
		
			public Object getResult()
			{	return mDuration;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (matcher.matches(aValue, aTimestamp))
				{	mDuration+=aDuration;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getTimeAtIndexInstance(final int index)
	{	return new IQueryIteratorController()
		{	private long mTime;
		
			public Object getResult()
			{	return mTime;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (index==aIndex)
				{	mTime=aTimestamp;
					return false;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getAverageInstance()
	{	return new IQueryIteratorController()
		{	private double mTotal;
			private int mCount;
		
			public Object getResult()
			{	return mCount>0?(mTotal/(double)mCount):null;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aValue instanceof Number)
				{	mTotal+=((Number)aValue).doubleValue();
					mCount++;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getMaxInstance()
	{	return new IQueryIteratorController()
		{	private double mMax=Double.MIN_VALUE;
		
			public Object getResult()
			{	return mMax!=Double.MIN_VALUE?mMax:null;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aValue instanceof Number)
				{	double v=((Number)aValue).doubleValue();
					if (v>mMax) mMax=v;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getMinInstance()
	{	return new IQueryIteratorController()
		{	private double mMin=Double.MAX_VALUE;
		
			public Object getResult()
			{	return mMin!=Double.MIN_VALUE?mMin:null;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aValue instanceof Number)
				{	double v=((Number)aValue).doubleValue();
					if (v<mMin) mMin=v;
				}
				return true;
			}
		};
	}
		
	public static IQueryIteratorController getCountInstance()
	{	return new IQueryIteratorController()
		{	private int mCount;
		
			public Object getResult()
			{	return mCount;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	mCount++;
				return true;
			}
		};
	}
}
