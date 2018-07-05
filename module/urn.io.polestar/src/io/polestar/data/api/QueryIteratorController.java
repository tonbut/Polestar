package io.polestar.data.api;

import java.util.Arrays;

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
	
	public static IQueryIteratorController getStandardDeviationInstance()
	{	return new IQueryIteratorController()
		{	private double mCount;
			private double mMean;
			private double m2;
		
			public Object getResult()
			{	return Math.sqrt(m2/(mCount-1));
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	
				if (aValue instanceof Number)
				{	double newValue=((Number)aValue).doubleValue();
					mCount++;
					double delta = newValue - mMean;
		            mMean = mMean + delta / mCount;
		            double delta2 = newValue - mMean;
		            m2 = m2 + delta * delta2;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getPercentileInstance(float aPercentile, long aSamplePeriod)
	{	return new IQueryIteratorController()
		{	
			//private List<float> mValues=new ArrayList
			float[] mArray = new float[256];
			int mIndex=0;
		
			public Object getResult()
			{	if (mIndex>0)
				{	float[] a=new float[mIndex];
					System.arraycopy(mArray, 0, a, 0, mIndex);
					Arrays.sort(a);
					float desiredIndex=aPercentile*mIndex;
					
					int di1=(int)desiredIndex;
					if (di1>=mIndex) di1=mIndex-1;
					int di2=di1+1;
					if (di2>=mIndex) di2=mIndex-1;
					float ratio=desiredIndex-(float)(int)desiredIndex;
					float v1=a[di1];
					float v2=a[di2];
					return (v1*(1-ratio)+v2*ratio);
				}
				else
				{	return null;
				}
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	
				if (aValue instanceof Number)
				{	float newValue=((Number)aValue).floatValue();
					
					int c=(int)Math.round((float)aDuration/(float)aSamplePeriod);
					//System.out.println(aDuration+" "+c);
					for (int i=0; i<c; i++)
					{
						if (mIndex<mArray.length)
						{	mArray[mIndex++]=newValue;
						}
						else
						{	float[] a=new float[mArray.length*2];
							System.arraycopy(mArray, 0, a, 0, mArray.length);
							mArray=a;
							mArray[mIndex++]=newValue;
						}
					}
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getMedianDurationInstance()
	{	return new IQueryIteratorController()
		{	long[] mArray = new long[256];
			int mIndex=0;
			
			public Object getResult()
			{	
				if (mIndex>0)
				{	long[] a=new long[mIndex];
					System.arraycopy(mArray, 0, a, 0, mIndex);
					Arrays.sort(a);
					float desiredIndex=mIndex/4;
					long v1=a[(int)desiredIndex];
					return v1;
				}
				else
				{	return null;
				}
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (mIndex<mArray.length)
				{	mArray[mIndex++]=aDuration;
				}
				else
				{	long[] a=new long[mArray.length*2];
					System.arraycopy(mArray, 0, a, 0, mArray.length);
					mArray=a;
					mArray[mIndex++]=aDuration;
				}
				return true;
			}
		};
	}
	
}
