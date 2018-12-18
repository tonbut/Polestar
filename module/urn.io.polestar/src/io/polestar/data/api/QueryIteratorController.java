package io.polestar.data.api;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.polestar.api.IPolestarMatcher;
import io.polestar.api.IPolestarQueryResultSet;

public class QueryIteratorController
{
	public abstract static class IQueryIteratorController
	{
		/** Accept next value of sensor
		 * @return true when iteration should continue, false to break
		 * @param aValue value of sensor
		 * @param aTimestamp timestamp of sensor value
		 * @param aIndex index of value from beginning
		 * @param aDuration duration of reading since previous or since start/end boundary
		 */
		abstract boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex);
		
		/** Return the result after iteration completes and clear ready for reuse */
		abstract Object getResult();
		
		/** full reset between query periods*/
		void reset()
		{
		}
	}

	
	public static IQueryIteratorController getDiscreteMostInstance()
	{
		return new IQueryIteratorController()
		{	private Map<Object,Long> mDurations=new HashMap<>();
		
			public Object getResult()
			{
				Object chosen=null;
				long longestDuration=-1;
				for (Map.Entry<Object, Long> e : mDurations.entrySet())
				{
					if (e.getValue()>longestDuration)
					{	chosen=e.getKey();
					}
				}
				mDurations.clear();
				return chosen;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aValue!=null)
				{	Long existing=mDurations.get(aValue);
					if (existing==null)
					{	existing=aDuration;
					}
					else
					{	existing=existing+aDuration;
					}
					mDurations.put(aValue, existing);
				}
				return true;
			}
		};
		
	}
	
	
	public static IQueryIteratorController getFirstValueInstance()
	{	return new IQueryIteratorController()
		{	private Object mValue;
		
			public Object getResult()
			{	Object value=mValue;
				mValue=null;
				//System.out.println("FV= "+value);
				return value;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	
				//System.out.println("FV: "+aValue+" "+aTimestamp+">"+(aTimestamp+aDuration)+" "+aIndex);
				if (mValue==null && aDuration>0)
				{	mValue=aValue;
				}
				return false;
			}
		};
	}
	
	public static IQueryIteratorController getFirstMatchValueInstance(final IPolestarMatcher matcher)
	{	return new IQueryIteratorController()
		{	private Object mValue;
		
			public Object getResult()
			{	Object value=mValue;
				mValue=null;
				return value;
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
	
	public static IQueryIteratorController getFirstMatchTimeInstance(final IPolestarMatcher matcher, final boolean aStartOrEnd)
	{	return new IQueryIteratorController()
		{	private Object mValue;
		
			public Object getResult()
			{	Object value=mValue;
				mValue=null;
				return value;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (matcher.matches(aValue, aTimestamp))
				{	mValue=aStartOrEnd?aTimestamp:aTimestamp+aDuration-1;
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
			{	long duration=mDuration;
				mDuration=0;
				return duration;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (matcher.matches(aValue, aTimestamp))
				{	mDuration+=aDuration;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getTimeAtIndexInstance(final int index, final boolean start)
	{	return new IQueryIteratorController()
		{	private Long mTime;
		
			public Object getResult()
			{	Long time=mTime;
				mTime=null;
				return time;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (index==aIndex)
				{	if (start)
					{	mTime=aTimestamp;
					}
					else
					{	mTime=aTimestamp+aDuration;
					}
					return false;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getAverageInstance()
	{	return new IQueryIteratorController()
		{	private double mTotal;
			private long mDuration;
		
			public Object getResult()
			{	Object result= mDuration>0?(mTotal/(double)mDuration):null;
				mTotal=0;
				mDuration=0;
				return result;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aValue instanceof Number)
				{	mTotal+=((Number)aValue).doubleValue()*aDuration;
					mDuration+=aDuration;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getMaxInstance()
	{	return new IQueryIteratorController()
		{	private double mMax=Double.NEGATIVE_INFINITY;
		
			public Object getResult()
			{	Object result= mMax!=Double.NEGATIVE_INFINITY?mMax:null;
				mMax=Double.NEGATIVE_INFINITY;
				return result;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	
				if (aValue instanceof Number)
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
			{	Object result= mMin!=Double.MIN_VALUE?mMin:null;
				mMin=Double.MAX_VALUE;
				return result;
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
	
	public static IQueryIteratorController getDiffInstance()
	{	return new IQueryIteratorController()
		{	private Double mLast=null;
			private double mValue=0;
		
			public Object getResult()
			{	Object result= (mLast!=null)?mValue-mLast:0.0;
				mLast=mValue;
				return result;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aValue instanceof Number)
				{	double v=((Number)aValue).doubleValue();
					if (mLast==null)
					{	mLast=v;
					}
					mValue=v;
				}
				return true;
			}
		};
	}
	
	/** might only work with forward iterator! */
	public static IQueryIteratorController getRateOfChangeInstance()
	{	return new IQueryIteratorController()
		{	private Double mLast=null;
			private double mValue=0;
			private long mStartTime;
			private long mEndTime;
		
			public Object getResult()
			{	Object result;
				if (mLast!=null)
				{	double diff=mValue-mLast;
					double duration=(double)(mEndTime-mStartTime);
					result=(diff*60000)/duration;
				}
				else
				{	result=null;
				}
				mLast=null;
				return result;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aValue instanceof Number)
				{	double v=((Number)aValue).doubleValue();
					if (mLast==null)
					{	mLast=v;
						mStartTime=aTimestamp;
					}
					mValue=v;
					mEndTime=aTimestamp+aDuration;
				}
				return true;
			}
		};
	}
	
	
	public static IQueryIteratorController getSumInstance()
	{	return new IQueryIteratorController()
		{	private double mTotal=0;
		
			public Object getResult()
			{	Object result=mTotal;
				mTotal=0;
				return result;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aIndex>0 && aValue instanceof Number)
				{	double v=((Number)aValue).doubleValue();
					mTotal+=v;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getRunningTotalInstance()
	{	return new IQueryIteratorController()
		{	private double mTotal=0;
		
			public Object getResult()
			{	return mTotal;
			}
			
			public void reset()
			{	mTotal=0;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aIndex>0 && aValue instanceof Number)
				{	double v=((Number)aValue).doubleValue();
					mTotal+=v;
				}
				return true;
			}
		};
	}
		
	public static IQueryIteratorController getCountInstance()
	{	return new IQueryIteratorController()
		{	private int mCount;
		
			public Object getResult()
			{	Object result=mCount;
				mCount=0;
				return result;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aIndex>0) mCount++;
				//System.out.println("COUNT: "+aValue+" "+aTimestamp+">"+(aTimestamp+aDuration)+" "+aIndex);
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
			{	Object result=Math.sqrt(m2/(mCount-1));
				mCount=0;
				mMean=0;
				m2=0;
				return result;
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
	
	public static IQueryIteratorController getPercentileInstance(float aPercentile, IPolestarQueryResultSet aSamplePeriods)
	{	return new IQueryIteratorController()
		{	
			int mSamplePeriodIndex=0;
			Float mSamplePeriod;
		
			//private List<float> mValues=new ArrayList
			float[] mArray = new float[256];
			int mIndex=0;
		
			public Object getResult()
			{	Object result=null;
				if (mIndex>0)
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
					result= (v1*(1-ratio)+v2*ratio);
				}
				mIndex=0;
				mArray = new float[256];
				mSamplePeriodIndex++;
				mSamplePeriod=null;
				return result;

			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	
				if (mSamplePeriod==null)
				{
					Long samplePeriod=(Long)aSamplePeriods.getValue(mSamplePeriodIndex);
					mSamplePeriod=samplePeriod.floatValue();
				}
				
				if (aValue instanceof Number)
				{	float newValue=((Number)aValue).floatValue();
					
					int c=(int)Math.round((float)aDuration/mSamplePeriod);
					if (c>255) c=255;
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
	
	/*used by percentile to get median sample period. This is need as input to calculation */
	public static IQueryIteratorController getMedianDurationInstance()
	{	return new IQueryIteratorController()
		{	long[] mArray = new long[256];
			int mIndex=0;
			
			public Object getResult()
			{	Object result=null;
				if (mIndex>0)
				{	long[] a=new long[mIndex];
					System.arraycopy(mArray, 0, a, 0, mIndex);
					Arrays.sort(a);
					float desiredIndex=mIndex/4;
					long v1=a[(int)desiredIndex];
					result=v1;
				}
				mIndex=0;
				mArray = new long[256];
				//System.out.println("MedianDuration="+result);
				return result;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	
				if (mIndex<mArray.length)
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
	
	public static IQueryIteratorController getPositiveDiffInstance()
	{
		return new IQueryIteratorController()
		{
			Double first=null;
			Double last=null;
			
			public Object getResult()
			{	if (first!=null)
				{	Double value=last-first;
					first=last;
					if (value>0)
					{	return value;
					}
				}
				return 0;
			}
			
			public void reset()
			{	first=last=null;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aValue instanceof Number)
				{	double v=((Number)aValue).doubleValue();
					if (first==null)
					{	first=last=v;
					}
					else
					{	last=v;
					}
				}
				return true;
			}
		};
	}
		
	public static IQueryIteratorController getBooleanEdgeCountInstance(final boolean aRising)
	{
		return new IQueryIteratorController()
		{
			int mCount;
			Boolean mLast;
			
			public Object getResult()
			{	int result=mCount;
				mCount=0;
				return result;
			}
			
			public void reset()
			{	mLast=null;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	
				if (aValue!=null && aValue instanceof Boolean)
				{	Boolean b=(Boolean)aValue;
					if (aRising==b && (mLast==null || aRising!=mLast))
					{	mCount++;
					}
					mLast=b;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getBooleanChangeInstance()
	{
		return new IQueryIteratorController()
		{
			private boolean mHadFalse;
			private boolean mHadTrue;
			private boolean mLast;
			private boolean mLastReal;
			
			public Object getResult()
			{	
				boolean result;
				if (mHadFalse && !mHadTrue)
				{	result=false;
				}
				else if (mHadTrue && !mHadFalse)
				{	result=true;
				}
				else if (mHadTrue && mHadFalse)
				{	result=!mLast;
				}
				else
				{	result=mLastReal;
				}
				mLast=result;
				mHadFalse=false;
				mHadTrue=false;
				return result;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	if (aValue!=null && aValue instanceof Boolean)
				{	boolean v=(Boolean)aValue;
					if (v)
					{	mHadTrue=true;
					}
					else
					{	mHadFalse=true;
					}
					mLastReal=v;
				}
				return true;
			}
		};
	}
	
	public static IQueryIteratorController getRotation360Instance()
	{
		return new IQueryIteratorController()
		{
			private double mValue=-1; 
			
			public Object getResult()
			{	return mValue;
			}
			
			public boolean accept(Object aValue, long aTimestamp, long aDuration, int aIndex)
			{	
				if (aValue!=null && aValue instanceof Number)
				{	double v=((Number)aValue).doubleValue();
				
					if  (mValue==-1)
					{	mValue=v;
					}
					else
					{	double diff=v-mValue;
						double nv;
						if (diff>180)
						{	nv=v-360;
						}
						else if (diff<-180)
						{	nv=v+360;
						}
						else
						{	nv=v;
						}
						mValue=mValue*0.75+nv*0.25;
					}
				}
				return true;
			}
		};
	}
	
}
