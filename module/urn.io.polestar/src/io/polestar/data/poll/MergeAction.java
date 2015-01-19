/* Copyright 2015 1060 Research Ltd

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package io.polestar.data.poll;

public abstract class MergeAction
{
	public enum Type
	{	/* basic */
		SAMPLE,
		/* numeric functions */
		AVERAGE, MAX, MIN, DIFF, SUM, RUNNING_TOTAL, ROTATION_360_AVERAGE,
		/* boolean functions */
		BOOLEAN_CHANGE
	}
	
	private String mId;
	private String mName;
	private String mFormat;
	
	public static MergeAction getMergeAction(String aId, String aName, String aFormat, Type aType)
	{	MergeAction result;
		switch(aType)
		{
		case SAMPLE:
			result=new SampleAction();
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
		case SUM:
			result=new SumAction();
			break;
		case RUNNING_TOTAL:
			result=new TotalAction();
			break;
		case ROTATION_360_AVERAGE:
			result=new Rot360AvgAction();
			break;
		case BOOLEAN_CHANGE:
			result=new BooleanChangeAction();
			break;
		default:
			throw new IllegalArgumentException(aType+" not implemented");
		}
		result.init(aId, aName,aFormat);
		return result;
	}
	
	private void init(String aId, String aName, String aFormat)
	{	mId=aId;
		mName=aName;
		mFormat=aFormat;
	}
	
	public String getId()
	{	return mId;
	}
	
	public String getName()
	{	return mName;
	}
	
	public String getFormat()
	{	return mFormat;
	}

	public abstract void update(Object aValue);
	public abstract Object getValue();
	
	/*
	private static class AverageAction extends MergeAction
	{	
		public void update(Object aValue)
		{
		}

		public Object getValue()
		{ return null;
		}
	}
	*/

	private static class SampleAction extends MergeAction
	{	private Object mValue;
		public void update(Object aValue)
		{	mValue=aValue;
		}
		public Object getValue()
		{ return mValue;
		}
	}
	
	private static class AverageAction extends MergeAction
	{	double mValue;
		double mCount;
		public void update(Object aValue)
		{	if (aValue!=null)
			{	double v=Double.parseDouble(aValue.toString());
				mValue+=v;
				mCount++;
			}
		}
		public Object getValue()
		{	double result;
			if (mCount>0)
			{	result=mValue/mCount;
				mCount=mValue=0.0;
			}
			else
			{	result=0.0;
			}
			return result;
		}
	}
	
	private static class MaxAction extends MergeAction
	{	double mValue=Double.NEGATIVE_INFINITY;
		public void update(Object aValue)
		{	if (aValue!=null)
			{	double v=Double.parseDouble(aValue.toString());
				if (v>mValue) mValue=v;
			}
		}
		public Object getValue()
		{	double result;
			if (mValue!=Double.NEGATIVE_INFINITY)
			{	result=mValue;
				mValue=Double.NEGATIVE_INFINITY;
			}
			else
			{	result=0.0;
			}
			return result;
		}
	}
	
	private static class MinAction extends MergeAction
	{	double mValue=Double.MAX_VALUE;
		public void update(Object aValue)
		{	if (aValue!=null)
			{	double v=Double.parseDouble(aValue.toString());
				if (v<mValue) mValue=v;
			}
		}
		public Object getValue()
		{	double result;
			if (mValue!=Double.MAX_VALUE)
			{	result=mValue;
				mValue=Double.MAX_VALUE;
			}
			else
			{	result=0.0;
			}
			return result;
		}
	}
	
	private static class DiffAction extends MergeAction
	{	private double mValue;
		private double mLast;
		public void update(Object aValue)
		{	if (aValue!=null)
			{	double v=Double.parseDouble(aValue.toString());
				if (mLast==0.0)
				{	mLast=v;
				}
				mValue=v;
			}
		}
		public Object getValue()
		{	double v= mValue-mLast;
			mLast=mValue;
			return v;
		}
	}
	
	private static class SumAction extends MergeAction
	{	private double mValue;
		public void update(Object aValue)
		{	if (aValue!=null)
			{	double v=Double.parseDouble(aValue.toString());
				mValue+=v;
			}
		}

		public Object getValue()
		{	double result=mValue;
			mValue=0.0;
			return result;
		}
	}
	
	private static class TotalAction extends MergeAction
	{	private double mValue;
		public void update(Object aValue)
		{	if (aValue!=null)
			{	double v=Double.parseDouble(aValue.toString());
				mValue+=v;
			}
		}

		public Object getValue()
		{	double result=mValue;
			return result;
		}
	}
	
	private static class Rot360AvgAction extends MergeAction
	{	private double mValue=-1; 
		public void update(Object aValue)
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
		public Object getValue()
		{	return mValue;
		}
	}
	
	private static class BooleanChangeAction extends MergeAction
	{	private boolean mHadFalse;
		private boolean mHadTrue;
		private boolean mLast;
		public void update(Object aValue)
		{	boolean v=(Boolean)aValue;
			if (v)
			{	mHadTrue=true;
			}
			else
			{	mHadFalse=true;
			}
		}
		public Object getValue()
		{ 	boolean result;
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
			{	result=false;
			}
			mLast=result;
			mHadFalse=false;
			mHadTrue=false;
			return result;
		}
	}
}
