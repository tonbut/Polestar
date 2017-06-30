package io.polestar.data.sensors;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


public abstract class MergeAction
{
	private String mName;
	private String mFormat;
	
	/** add sample of value at time aTime **/
	public abstract void update(Object aValue, long aTime);
	/** return value for period ending at aTime **/
	public abstract Object getValue(long aTime);
	
	private void init(String aName, String aFormat)
	{	mName=aName;
		mFormat=aFormat;
	}
	
	public String getName()
	{	return mName;
	}
	
	public String getFormat()
	{	return mFormat;
	}
	
	public enum Type
	{	/* basic */
		SAMPLE, COUNT,
		/* numeric functions */
		AVERAGE, MAX, MIN, DIFF, POSITIVE_DIFF, SUM, RUNNING_TOTAL, ROTATION_360_AVERAGE,
		/* boolean functions */
		BOOLEAN_CHANGE, BOOLEAN_RISING_EDGE_COUNT, BOOLEAN_FALLING_EDGE_COUNT,
		/* map functions */
		AVERAGE_MAP
	}
	
	public static MergeAction getMergeAction(String aName, String aFormat, Type aType)
	{	
		MergeAction result;
		switch(aType)
		{
		case SAMPLE:
			result=new SampleAction();
			break;
		case COUNT:
			result=new CountAction();
			break;
		case AVERAGE:
			result=new AverageAction();
			break;
		case MAX:
			result=new MaxAction();
			break;
		case MIN:
			result=new MinAction();
			break;
		case DIFF:
			result=new DiffAction();
			break;
		case POSITIVE_DIFF:
			result=new PositiveDiffAction();
			break;
		case RUNNING_TOTAL:
			result=new RunningTotalAction();
			break;
		case SUM:
			result=new SumAction();
			break;
		case BOOLEAN_RISING_EDGE_COUNT:
			result=new BooleanRisingEdgeAction();
			break;
		case BOOLEAN_FALLING_EDGE_COUNT:
			result=new BooleanFallingEdgeAction();
			break;
		case BOOLEAN_CHANGE:
			result=new BooleanChangeAction();
			break;
		case ROTATION_360_AVERAGE:
			result=new Rot360AvgAction();
			break;
		case AVERAGE_MAP:
			result=new AverageMapAction();
			break;
		default:
			throw new IllegalArgumentException(aType+" not implemented");
		}
		result.init(aName,aFormat);
		return result;
	}
	
	/** take first value or null **/
	private static class SampleAction extends MergeAction
	{	private Object mValue;
		private boolean mDataPoint;
		
		public void update(Object aValue, long aTime)
		{	if (!mDataPoint && aValue!=null)
			{	mValue=aValue;
			}
			mDataPoint=true;
		}
		public Object getValue(long aTime)
		{	boolean dp=mDataPoint;
			mDataPoint=false;
			return mValue;
		}
	}
	
	/** count number of records in period **/
	private static class CountAction extends MergeAction
	{	private int mCount;
		
		public void update(Object aValue, long aTime)
		{	mCount++;
		}
		public Object getValue(long aTime)
		{	int result=mCount;
			mCount=0;
			return result;
		}
	}
	
	private static class DiffAction extends MergeAction
	{	private double mValue;
		private double mLastValue;
		
		public void update(Object aValue, long aTime)
		{	
			if (aValue instanceof Number)
			{	mValue=((Number)aValue).doubleValue();
			}
		}
		public Object getValue(long aTime)
		{	double result=mValue-mLastValue;
			mLastValue=mValue;
			return result;
		}
	}
	
	private static class PositiveDiffAction extends MergeAction
	{	private double mValue;
		private double mLastValue;
		
		public void update(Object aValue, long aTime)
		{	
			if (aValue instanceof Number)
			{	mValue=((Number)aValue).doubleValue();
			}
		}
		public Object getValue(long aTime)
		{	double result=mValue-mLastValue;
			mLastValue=mValue;
			return result>=0.0?result:0.0;
		}
	}
	
	private static class SumAction extends MergeAction
	{	private double mValue;
		public void update(Object aValue, long aTime)
		{	if (aValue!=null)
			{	double v=Double.parseDouble(aValue.toString());
				mValue+=v;
			}
		}

		public Object getValue(long aTime)
		{	double result=mValue;
			mValue=0.0;
			return result;
		}
	}
	
	private static class RunningTotalAction extends MergeAction
	{	private double mTotal;
		
		public void update(Object aValue, long aTime)
		{	
			if (aValue instanceof Number)
			{	mTotal+=((Number)aValue).doubleValue();
			}
			System.out.println("total="+mTotal);
		}
		public Object getValue(long aTime)
		{	return mTotal;
		}
	}
	
	private static class AverageMapAction extends MergeAction
	{	Map<String,Double> mValue=new LinkedHashMap();
		Map<String,Integer> mCount=new HashMap();
		public void update(Object aValue, long aTime)
		{	
			if (aValue!=null)
			{	Map<String,Object> value=(Map)aValue;
				for (Map.Entry<String,Object> entry : value.entrySet())
				{	String key=entry.getKey();
					
					double v2=Double.parseDouble(entry.getValue().toString());
					Double existingValue=mValue.get(key);
					if (existingValue==null)
					{	mCount.put(key, Integer.valueOf(1));
						mValue.put(key,v2);
					}
					else
					{	mValue.put(key,v2+existingValue);
						mCount.put(key, Integer.valueOf(1+mCount.get(key)));
					}
				}
			}
		}
		public Object getValue(long aTime)
		{	
			for (Map.Entry<String,Double> entry : mValue.entrySet())
			{	String key=entry.getKey();
				Double existingValue=mValue.get(key);
				Integer count=mCount.get(key);
				mValue.put(key,existingValue/(double)count);
			}
			Object result=mValue;

			mValue=new LinkedHashMap();
			mCount.clear();
			return result;
		}
	}
	
	
	
	private static class AverageAction extends MergeAction
	{	private Double mLastValue;
		private double mTotal;
		private long mLastTime;
		private long mPeriodStart;
		
		public void update(Object aValue, long aTime)
		{	
			innerUpdate(aTime);
			if (aValue instanceof Number)
			{	double v=((Number)aValue).doubleValue();
				mLastValue=v;	
			}
			else if (aValue instanceof Boolean)
			{	mLastValue=Boolean.TRUE.equals(aValue)?1.0:0.0;
			}
			else
			{	mLastValue=null;
			}
			mLastTime=aTime;
	
		}
		
		private void innerUpdate(long aTime)
		{	if (mLastValue!=null)
			{	double duration=aTime-mLastTime;
				mTotal+=mLastValue*duration;
			}
		}
		
		public Object getValue(long aTime)
		{	
			innerUpdate(aTime);
			double duration=aTime-mPeriodStart;
			double result=mTotal/duration;
			mTotal=0.0;
			mPeriodStart=aTime;
			mLastTime=aTime;
			return result;
		}
	}
	
	private static class MaxAction extends MergeAction
	{	private Double mMax;
		private Double mLastGood=Double.NaN;
		
		public void update(Object aValue, long aTime)
		{	if (aValue instanceof Number)
			{	double v=((Number)aValue).doubleValue();
				if (mMax==null || mMax<v)
				{	mMax=v;
				}
			}
		}
		
		public Object getValue(long aTime)
		{	Object result;
			if (mMax!=null)
			{	result=mMax;
				mLastGood=mMax;
			}
			else
			{	result=mLastGood;
			}
			mMax=null;
			return result;
		}
	}
	
	private static class MinAction extends MergeAction
	{	private Double mMin;
		private Double mLastGood=Double.NaN;
		
		public void update(Object aValue, long aTime)
		{	if (aValue instanceof Number)
			{	double v=((Number)aValue).doubleValue();
				if (mMin==null || mMin>v)
				{	mMin=v;
				}
			}
		}
		
		public Object getValue(long aTime)
		{	Object result;
			if (mMin!=null)
			{	result=mMin;
				mLastGood=mMin;
			}
			else
			{	result=mLastGood;
			}
			mMin=null;
			return result;
		}
	}
	
	private static class BooleanChangeAction extends MergeAction
	{	private boolean mHadFalse;
		private boolean mHadTrue;
		private boolean mLast;
		private boolean mLastReal;
		public void update(Object aValue, long aTime)
		{	boolean v=(Boolean)aValue;
			if (v)
			{	mHadTrue=true;
			}
			else
			{	mHadFalse=true;
			}
		}
		public Object getValue(long aTime)
		{ 	boolean result;
			if (mHadFalse && !mHadTrue)
			{	result=false;
				mLastReal=false;
			}
			else if (mHadTrue && !mHadFalse)
			{	result=true;
				mLastReal=true;
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
	}
	
	
	private static class BooleanRisingEdgeAction extends MergeAction
	{	
		private Boolean mLast;
		private int mCount;
		
		public void update(Object aValue, long aTime)
		{	
			if (Boolean.TRUE.equals(aValue) && !aValue.equals(mLast)) mCount++;
			if (aValue instanceof Boolean) mLast=(Boolean)aValue;
		}
		public Object getValue(long aTime)
		{	int result=mCount;
			mCount=0;
			return result;
		}
	}
	
	private static class BooleanFallingEdgeAction extends MergeAction
	{	
		private Boolean mLast;
		private int mCount;
		
		public void update(Object aValue, long aTime)
		{	
			if (Boolean.FALSE.equals(aValue) && !aValue.equals(mLast)) mCount++;
			if (aValue instanceof Boolean) mLast=(Boolean)aValue;
		}
		public Object getValue(long aTime)
		{	int result=mCount;
			mCount=0;
			return result;
		}
	}
	
	private static class Rot360AvgAction extends MergeAction
	{	private double mValue=-1; 
		public void update(Object aValue,long aTime)
		{	if (aValue!=null)
			{	double v=Double.parseDouble(aValue.toString());
			
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
		}
		public Object getValue(long aTime)
		{	return mValue;
		}
	}
	
}
