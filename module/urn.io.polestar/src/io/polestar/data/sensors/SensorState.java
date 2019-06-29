package io.polestar.data.sensors;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;

class SensorState
{
	private Object mValue;
	private long mLastUpdated;
	private long mLastModified;
	private long mErrorLastModified;
	private String mUserError;
	private String mValueError;
	private String mStaleError;
	private long mErrorLastModifiedPublic;
	private String mErrorPublic;
	
	public SensorState()
	{	mValue=null;
		mLastUpdated=0;
		mLastModified=0;
		mErrorLastModified=0;
		mUserError=null;
		mValueError=null;
		mStaleError="No value received";
	}
	
	public SensorState(IHDSReader aState)
	{
		mValue=aState.getFirstValue("value");
		mUserError=(String)aState.getFirstValueOrNull("userError");
		mValueError=(String)aState.getFirstValueOrNull("valueError");
		mStaleError=(String)aState.getFirstValueOrNull("staleError");
		mErrorPublic=(String)aState.getFirstValueOrNull("error");
		mLastUpdated=(Long)aState.getFirstValue("lastUpdated");
		mLastModified=(Long)aState.getFirstValue("lastModified");
		Long errorLastModified=(Long)aState.getFirstValueOrNull("errorLastModified");
		if (errorLastModified==null)
		{	mErrorLastModified=0L;
		}
		Long errorLastModifiedPublic=(Long)aState.getFirstValueOrNull("errorLastModifiedPublic");
		if (errorLastModifiedPublic==null)
		{	mErrorLastModifiedPublic=0L;
		}
	}
	
	public void serializeToHDS(IHDSMutator aMutator, long aNow)
	{	if (mUserError!=null)
		{	aMutator.addNode("userError", mUserError);
		}
		if (mValueError!=null)
		{	aMutator.addNode("valueError", mValueError);
		}
		if (mStaleError!=null)
		{	aMutator.addNode("staleError", mStaleError);
		}
		String error=getError();
		if (error!=null)
		{	aMutator.addNode("error", error);
		}
		aMutator.addNode("value", getValue());
		aMutator.addNode("lastUpdated", getLastUpdated());
		aMutator.addNode("lastModified", getLastModified());
		aMutator.addNode("errorLastModified", mErrorLastModified);
		aMutator.addNode("errorLastModifiedPublic", mErrorLastModifiedPublic);	
	}
	
	private static boolean isDifferent(Object a, Object b)
	{	boolean different=(a==null && b!=null) || (a!=null && b==null) || (a!=null && !a.equals(b));
		return different;
	}
	
	public void setValue(Object aValue, long aNow, IHDSReader sensorDef, INKFRequestContext aContext)
	{	if (isDifferent(aValue,mValue) && aValue!=null)
		{	mValue=aValue;
			if (aNow>mLastModified)
			{	mLastModified=aNow;
				testValue(sensorDef, aNow);
			}
		}
		if (aNow>mLastUpdated)
		{	mLastUpdated=aNow;
			poll(sensorDef,aNow,aContext);
		}
	}

	public void setUserError(String aError, long aNow)
	{	if (isDifferent(aError,mUserError))
		{	mErrorLastModified=aNow;
			//System.out.println("USER "+mUserError+"->"+aError);
			mUserError=aError;
			
		}
	}
	
	public boolean setValueError(String aError, long aNow)
	{	if (isDifferent(aError,mValueError))
		{	mErrorLastModified=aNow;
			//System.out.println("VALUE "+mValueError+"->"+aError);
			mValueError=aError;
			return true;
		}
		return false;
	}
	
	public boolean setStaleError(String aError, long aNow)
	{	if (isDifferent(aError,mStaleError))
		{	mErrorLastModified=aNow;
			//System.out.println("STALE "+mStaleError+"->"+aError);
			mStaleError=aError;
			return true;
		}
		return false;
	}
	
	private void testValue(IHDSReader sensorDef, long aNow)
	{	if (sensorDef!=null)
		{	String error;
		
			error=testForGreaterThan(sensorDef);
			if (error==null)
			{	error=testForLessThan(sensorDef);
			}
			if (error==null)
			{	error=testIfEquals(sensorDef);
			}
			if (error==null)
			{	error=testIfNotEquals(sensorDef);
			}
			setValueError(error, aNow);
		}
	}
	
	public void poll(IHDSReader sensorDef, long aNow, INKFRequestContext aContext)
	{	//boolean result=false;
		if (sensorDef!=null)
		{	
			
			String error;
			error=testForNoReadings(sensorDef,aNow);
			if (error==null)
			{	error=testForNoUpdates(sensorDef,aNow);
			}
			//if (setStaleError(error, aNow)) result=true;
			setStaleError(error, aNow);
			
			Long errorOnlyAfter=(Long)sensorDef.getFirstValueOrNull("errorOnlyAfter");
			if (errorOnlyAfter!=null)
			{	if (mErrorLastModifiedPublic<mErrorLastModified)
				{
					String errorInternal=getErrorInternal();
					if (errorInternal==null)
					{	
						if (isDifferent(errorInternal, mErrorPublic))
						{	mErrorLastModifiedPublic=aNow;
							mErrorPublic=null;
						}
						else
						{	mErrorLastModified=mErrorLastModifiedPublic;
						}
					}
					else
					{	if (aNow-mErrorLastModified>errorOnlyAfter*1000L)
						{	
							if (isDifferent(errorInternal, mErrorPublic))
							{	mErrorLastModifiedPublic=aNow;
								mErrorPublic=errorInternal;
							}
							else
							{	mErrorLastModified=mErrorLastModifiedPublic;
							}
							
						}
						else
						{
						}
					}
				}
			}
			else
			{	String errorInternal=getErrorInternal();
				if (isDifferent(errorInternal, mErrorPublic))
				{	mErrorLastModifiedPublic=mErrorLastModified;
					mErrorPublic=getErrorInternal();
				}
				
			}
		}
	}
	
	private String testForGreaterThan(IHDSReader sensorDef)
	{	String error=null;
		Number errorIfGreaterThan=(Number)sensorDef.getFirstValueOrNull("errorIfGreaterThan");
		if (errorIfGreaterThan!=null && mValue instanceof Number)
		{	Number nn=(Number)mValue;
		
			boolean isError=false;
			String errorText="Value has exceeded "+errorIfGreaterThan;
			Number errorClearIfLessThan=(Number)sensorDef.getFirstValueOrNull("errorClearIfLessThan");
			if (errorClearIfLessThan!=null)
			{	//hysteresis
				if (mValueError!=null && mValueError.equals(errorText))
				{	if (nn.doubleValue()>=errorClearIfLessThan.doubleValue())
					{	isError=true;
					}
				}
				else
				{	if (nn.doubleValue()>errorIfGreaterThan.doubleValue())
					{	isError=true;
					}
				}
			}
			else
			{	if (nn.doubleValue()>errorIfGreaterThan.doubleValue())
				{	isError=true;
				}
			}
			
			if (isError)
			{	error=errorText;
			}
		}
		return error;
	}
	
	private String testForLessThan(IHDSReader sensorDef)
	{	String error=null;
		Number errorIfLessThan=(Number)sensorDef.getFirstValueOrNull("errorIfLessThan");
		if (errorIfLessThan!=null && mValue instanceof Number)
		{	Number nn=(Number)mValue;
		
			boolean isError=false;
			String errorText="Value has fallen below "+errorIfLessThan;
			Number errorClearIfGreaterThan=(Number)sensorDef.getFirstValueOrNull("errorClearIfGreaterThan");
			if (errorClearIfGreaterThan!=null)
			{	//hysteresis
				if (mValueError!=null && mValueError.equals(errorText))
				{	if (nn.doubleValue()<=errorClearIfGreaterThan.doubleValue())
					{	isError=true;
					}
				}
				else
				{	if (nn.doubleValue()<errorIfLessThan.doubleValue())
					{	isError=true;
					}
				}
			}
			else
			{	if (nn.doubleValue()<errorIfLessThan.doubleValue())
				{	isError=true;
				}
			}
			
			if (isError)
			{	error=errorText;
			}
		}
		return error;
	}
	
	private String testIfEquals(IHDSReader sensorDef)
	{	String error=null;
		Object errorIfEquals=sensorDef.getFirstValueOrNull("errorIfEquals");
		if (errorIfEquals!=null && errorIfEquals instanceof Number && mValue instanceof Number)
		{	Number nn=(Number)mValue;
			if (nn.doubleValue()==((Number)errorIfEquals).doubleValue())
			{	error="Value equals "+errorIfEquals;
			}
		}
		if (errorIfEquals!=null && errorIfEquals instanceof Boolean && mValue instanceof Boolean)
		{	Boolean nn=(Boolean)mValue;
			if (nn.equals(errorIfEquals))
			{	error="Value equals "+errorIfEquals;
			}
		}
		return error;
	}
	
	private String testIfNotEquals(IHDSReader sensorDef)
	{	String error=null;
		Object errorIfNotEquals=sensorDef.getFirstValueOrNull("errorIfNotEquals");
		if (errorIfNotEquals!=null && errorIfNotEquals instanceof Number && mValue instanceof Number)
		{	Number nn=(Number)mValue;
			if (nn.doubleValue()!=((Number)errorIfNotEquals).doubleValue())
			{	error="Value doesn't equal "+errorIfNotEquals;
			}
		}
		if (errorIfNotEquals!=null && errorIfNotEquals instanceof Boolean && mValue instanceof Boolean)
		{	Boolean nn=(Boolean)mValue;
			if (!nn.equals(errorIfNotEquals))
			{	error="Value doesn't equal "+errorIfNotEquals;
			}
		}
		return error;
	}
	
	
	
	private String testForNoReadings(IHDSReader sensorDef, long aNow)
	{	String error=null;
		Long errorIfNoReadingsFor=(Long)sensorDef.getFirstValueOrNull("errorIfNoReadingsFor");
		if (errorIfNoReadingsFor!=null)
		{	if (aNow-mLastUpdated>=errorIfNoReadingsFor*1000L)
			{	error="No fresh readings";
			}
		}
		return error;
	}
	
	private String testForNoUpdates(IHDSReader sensorDef, long aNow)
	{	String error=null;
		Long errorIfNotModifiedFor=(Long)sensorDef.getFirstValueOrNull("errorIfNotModifiedFor");
		if (errorIfNotModifiedFor!=null)
		{	if (aNow-mLastModified>=errorIfNotModifiedFor*1000L)
			{	error="Not modified for too long";
			}
		}	
		return error;
	}
	
	public Object getValue()
	{	return mValue;
	}
	public long getLastModified()
	{	return mLastModified;
	}
	public long getLastUpdated()
	{	return mLastUpdated;
	}
	public long getErrorLastModified()
	{	return mErrorLastModifiedPublic;
	}
	public boolean hasError()
	{	return getError()!=null;
	}
	public String getError()
	{	return mErrorPublic;
	}
	private String getErrorInternal()
	{	
		String error=null;
		if (mUserError!=null)
		{	error=mUserError;
		}
		else if (mStaleError!=null)
		{	error=mStaleError;
		}
		else if (mValueError!=null)
		{	error=mValueError;
		}
		return error;
	}
}