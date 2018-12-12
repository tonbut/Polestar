package io.polestar.data.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import io.polestar.api.IPolestarContext;
import io.polestar.api.IPolestarMatcher;
import io.polestar.api.IPolestarQuery;
import io.polestar.api.IPolestarQueryResultSet;
import io.polestar.api.QueryType;
import io.polestar.data.api.QueryIteratorController.IQueryIteratorController;
import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

public class PolestarQuery2 implements IPolestarQuery
{
	private static final long HOURS24=1000L*60*60*24;
	public static String ERROR_FRAGMENT="_ERROR";
	
	private final String mSensor;
	private final String mFragment;
	private final ICollectionIterator mIterator;
	
	private final QueryType mType;
	private final INKFRequestContext mContext;
	private final PolestarContext mPolestarContext;
	private long mStart, mEnd, mPeriod;
	private Object mParameter;
	private IPolestarMatcher mMatcher;
	private int mPeriodMergeCount;
	private QueryType mPeriodMergeOp=QueryType.AVERAGE;
	private long mTimeMergePeriod;
	
	public PolestarQuery2(String aSensor, String aFragment, QueryType aType, INKFRequestContext aContext, PolestarContext aPolestarContext)
	{	
		mSensor=aSensor;
		mFragment=aFragment;
		mIterator=null;

		mType=aType;
		mContext=aContext;
		mPolestarContext=aPolestarContext;
		long now=System.currentTimeMillis();
		mEnd=now;
		mStart=now-HOURS24;
	}
	
	public PolestarQuery2(ICollectionIterator aIterator, QueryType aType, INKFRequestContext aContext, PolestarContext aPolestarContext)
	{	
		mSensor=null;
		mFragment=null;
		mIterator=aIterator;

		mType=aType;
		mContext=aContext;
		mPolestarContext=aPolestarContext;
		long now=System.currentTimeMillis();
		mEnd=now;
		mStart=now-HOURS24;
	}
	

	@Override
	public IPolestarQuery setStart(long aStartTime)
	{	if (aStartTime<0)
		{	mStart=System.currentTimeMillis()+aStartTime;
		}
		else
		{	mStart=aStartTime;
		}
		return this;
	}

	@Override
	public IPolestarQuery setEnd(long aEndTime)
	{	if (aEndTime<0)
		{	mEnd=System.currentTimeMillis()+aEndTime;
		}
		else
		{	mEnd=aEndTime;
		}
		return this;
	}
	
	@Override
	public IPolestarQuery setResultSetPeriod(long aPeriod) throws NKFException
	{	if (aPeriod>0)
		{	mPeriod=aPeriod;
		}
		else
		{	throw new NKFException("Query result set period must be greater than 0");
		}
		return this;
	}

	@Override
	public IPolestarQuery setQueryParameter(Object aParameter) throws NKFException
	{
		switch(mType) {
		case PERCENTILE:
		case LAST_LESS_THAN_TIME:
		case LAST_GREATER_THAN_TIME:
		case LAST_EQUALS_TIME:
		case FIRST_LESS_THAN_TIME:
		case FIRST_GREATER_THAN_TIME:
		case FIRST_EQUALS_TIME:
		case DURATION_LESS_THAN:
		case DURATION_GREATER_THAN:
		case DURATION_EQUALS:
			mParameter=aParameter;
			break;
		default:
			throw new NKFException("Query Parameter not wanted","For query type "+mType.toString());
		}
		return this;
	}

	@Override
	public IPolestarQuery setQueryMatcher(IPolestarMatcher aMatcher) throws NKFException
	{
		switch(mType) {
		case LAST_MATCH_TIME:
		case FIRST_MATCH_TIME:
		case LAST_MATCH_TIME_RELATIVE:
		case FIRST_MATCH_TIME_RELATIVE:
		case LAST_MATCH_VALUE:
		case FIRST_MATCH_VALUE:
		case DURATION_MATCHES:
			mMatcher=aMatcher;
			break;
		default:
			throw new NKFException("Query Matcher not wanted","For query type "+mType.toString());
		}
		return this;
	}
	
	@Override
	public IPolestarQuery setTimeMerge(int aPeriodCount) throws NKFException
	{	mPeriodMergeCount=aPeriodCount;
		return this;
	}

	@Override
	public IPolestarQuery setTimeMergeOp(QueryType aOp) throws NKFException
	{	mPeriodMergeOp=aOp;
		return this;
	}
	
	@Override
	public IPolestarQuery setTimeMergePeriod(long aPeriod) throws NKFException
	{
		if (aPeriod>0)
		{	mTimeMergePeriod=aPeriod;
		}
		else
		{	throw new NKFException("Query time merge period must be greater than 0");
		}
		return this;
	}
	
	
	private Object getParameter() throws NKFException
	{	if (mParameter==null)
		{	throw new NKFException("Query parameter not defined");
		}
		return mParameter;
	}
	private IPolestarMatcher getMatcher() throws NKFException
	{	if (mMatcher==null)
		{	throw new NKFException("Query matcher not defined");
		}
		return mMatcher;
	}
	

	
	
	private IHDSMutator createHistoricalQueryBase()
	{	IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("query");
		m.addNode("start",mStart); 
		m.addNode("end",mEnd);
		long period;
		if (mPeriod>0)
		{	period=mPeriod;
		}
		else
		{	period=mEnd-mStart;
		}
		m.addNode("samplePeriod", period);
		m.pushNode("sensors"); //list of sensors that we want to get data for
		m.pushNode("sensor").addNode("id",mSensor);
		return m;
	}
	
	private Object executeHistoricalQuery(IHDSDocument aQuery) throws NKFException
	{	INKFRequest req=mContext.createRequest("active:polestarHistoricalQuery");
		req.addArgumentByValue("operator",aQuery);
		req.setRepresentationClass(IHDSDocument.class);
		IHDSDocument rep=(IHDSDocument)mContext.issueRequest(req);
		
		if (mPeriod>0)
		{	return rep.getReader().getValues("/rows/row/sensor1");
		}
		else
		{	return rep.getReader().getFirstValue("/rows/row[1]/sensor1");
		}
	}
	
	/*
	private Object getSensorValue(DBObject aCapture)
	{	Object v=aCapture.get("v");
		if (v instanceof Map && mFragment!=null)
		{	v=((Map)v).get(mFragment);			
		}
		return v;
	}
	
	private void iterateForward(IQueryIteratorController aController) throws NKFException
	{
		try
		{
			DBCollection col=MongoUtils.getCollectionForSensor(mSensor);
			
			//initialise with value directly before start
			BasicDBObject startI=new BasicDBObject("t", new BasicDBObject("$lt",mStart));
			DBCursor cursorP = col.find(startI).sort(new BasicDBObject("t",-1)).limit(1);
			
			Object lastValue=null;
			long lastTime=mStart;
			if (cursorP.hasNext())
			{	DBObject previous=cursorP.next();
				lastValue=previous.get("v");
			}
			
			boolean continueIterating=true;
			BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",mStart));
			BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",mEnd));
			BasicDBList listO=new BasicDBList();
			listO.add(startO);
			listO.add(endO);
			BasicDBObject queryO=new BasicDBObject("$and", listO);
			DBCursor cursor = col.find(queryO);
			
			DBObject capture=null;
			int index=0;
			do
			{	if (cursor.hasNext())
				{	capture=cursor.next();
				}
				else
				{	break;
				}
				long time=(Long)capture.get("t");
				Object value=capture.get("v");
				continueIterating=aController.accept(lastValue, lastTime, time-lastTime, index);
				index++;
				lastTime=time;
				lastValue=value;
			} while (continueIterating);
			
			if (continueIterating)
			{
				aController.accept(lastValue, lastTime, mEnd-lastTime, index);
			}
	
		} catch (Exception e)
		{	throw new NKFException("Unhandled Exception", null, e);
		}
	}
	
	private IPolestarQueryResultSet iterateForwardRS(IQueryIteratorController aController) throws NKFException
	{
		try
		{
			DBCollection col=MongoUtils.getCollectionForSensor(mSensor);
			
			//initialise with value directly before start
			BasicDBObject startI=new BasicDBObject("t", new BasicDBObject("$lt",mStart));
			DBCursor cursorP = col.find(startI).sort(new BasicDBObject("t",-1)).limit(1);
			
			Object lastValue=null;
			long lastTime=mStart;
			if (cursorP.hasNext())
			{	DBObject previous=cursorP.next();
				lastValue=getSensorValue(previous);
			}
			
			BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",mStart));
			BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",mEnd));
			BasicDBList listO=new BasicDBList();
			listO.add(startO);
			listO.add(endO);
			BasicDBObject queryO=new BasicDBObject("$and", listO);
			DBCursor cursor = col.find(queryO);
			int index=0;
			boolean continueIterating=true;
			DBObject capture=null;
			
			List<Long> times=new ArrayList<Long>();
			List<Object> values=new ArrayList<Object>();
			
			for (long t=mStart; t<mEnd; t+=mPeriod)
			{	long sampleEndTime=t+mPeriod;
				do
				{	
					if (capture==null)
					{	if (cursor.hasNext())
						{	capture=cursor.next();
						}
						else
						{	break;
						}
					}
					long time=(Long)capture.get("t");

					if (time<sampleEndTime)
					{
						if (continueIterating)
						{	Object value=getSensorValue(capture);
							continueIterating=aController.accept(lastValue, lastTime, time-lastTime, index);
							index++;
							lastTime=time;
							lastValue=value;
						}
						capture=null;
					}
					else
					{	break; //move into next sample
					}
				} while(true);
				
				if (sampleEndTime>=mEnd && continueIterating)
				{	continueIterating=aController.accept(lastValue, lastTime, mEnd-lastTime, index);
				}
				
				values.add(aController.getResult());
				times.add(t);
				
			}
			return new PolestarQueryResultSet(times,values);

		} catch (Exception e)
		{	throw new NKFException("Unhandled Exception", null, e);
		}
	}
	*/
	
	private static IPolestarQueryResultSet iterateForward(ICollectionIterator aIterator, IQueryIteratorController aController, long aStart, long aEnd, long aPeriod) throws NKFException
	{	Object lastValue=null;
		long lastTime=aStart;
		if (aIterator.next())
		{	lastValue=aIterator.getValue();
		}
		long now=System.currentTimeMillis();

		int index=0;
		boolean continueIterating;
		boolean useCurrent=false;
		
		List<Long> times=new ArrayList<Long>();
		List<Object> values=new ArrayList<Object>();
		long period=(aPeriod==0)?(aEnd-aStart):aPeriod;
		
		for (long t=aStart; t<aEnd; t+=period)
		{	long sampleEndTime=t+period;
			continueIterating=true;
			boolean lastSample=t+period>=aEnd;
			do
			{	if (!useCurrent)
				{	if (!aIterator.next())
					{	
						aController.accept(lastValue, lastTime, sampleEndTime-lastTime, index);
						lastTime=sampleEndTime;
						break;
					}
				}
				long time=aIterator.getTime();
				Object value=aIterator.getValue();
				if (time<sampleEndTime)
				{	if (continueIterating)
					{	long period2=time-lastTime;
						if (period2>0)
						{	continueIterating=aController.accept(lastValue, lastTime, period2, index);
						}
						index++;
						lastTime=time;
						lastValue=value;
					}
					useCurrent=false;
				}
				else
				{	//finish last segment
					aController.accept(lastValue, lastTime, sampleEndTime-lastTime, index);
					useCurrent=true;
					lastTime=sampleEndTime;
					break; //move into next sample
				}
			} while(continueIterating || !lastSample);
			
			if (sampleEndTime>now)
			{	
				values.add(null);
			}
			else
			{	values.add(aController.getResult());
			}
			times.add(t);
			index=0;
		}
		return new PolestarQueryResultSet(times,values);
	}
	
	private static IPolestarQueryResultSet iterateBackward(ICollectionIterator aIterator, IQueryIteratorController aController, long aStart, long aEnd, long aPeriod) throws NKFException
	{	Object lastValue=null;
		long lastTime=aEnd;

		int index=0;
		boolean continueIterating;
		long now=System.currentTimeMillis();
		
		List<Long> times=new ArrayList<Long>();
		List<Object> values=new ArrayList<Object>();
		long period=(aPeriod==0)?(aEnd-aStart):aPeriod;
		
		if (aIterator.next())
		{	lastValue=aIterator.getValue();
			lastTime=aIterator.getTime();
		}
		
		for (long t=aEnd-period; t>=aStart; t-=period)
		{	long sampleEndTime=t+period;
			continueIterating=true;
			boolean lastSample=t-period<aStart;
			
			if (lastTime<=t)
			{	aController.accept(lastValue, t, period, index);
			}
			else
			{	if (lastTime<sampleEndTime)
				{	continueIterating=aController.accept(lastValue, lastTime, sampleEndTime-lastTime, index);
					index++;
				}
			
				while (continueIterating || !lastSample)
				{	if (aIterator.next())
					{	Object value=aIterator.getValue();
						Long time=aIterator.getTime();
						
						if (time>=t)
						{	if (continueIterating)
							{	long period2=lastTime-time;
								if (period2>0)
								{	continueIterating=aController.accept(value, time, period2, index);
									if (!continueIterating && lastSample) break;
								}
							}
							lastTime=time;
							index++;
						}
						else
						{	if (lastTime>t)
							{	if (continueIterating)
								{	aController.accept(value, t, lastTime-t, index);
								}
							}
							lastValue=value;
							lastTime=time;
							break;
						}
					}
					else
					{	break;
					}
				}
			}
			if (sampleEndTime>now)
			{	values.add(null);
			}
			else
			{	values.add(0,aController.getResult());
			}
			times.add(0,t);
			index=0;
		}
		return new PolestarQueryResultSet(times,values);
	}
	
	private ICollectionIterator getForwardIterator(long aStart, long aEnd)
	{
		if (mIterator!=null)
		{	mIterator.reset();
			return mIterator;
		}
		else
		{	if (mFragment!=null && mFragment.equals(ERROR_FRAGMENT))
			{	return MongoCollectionIterator.getErrorForwardIterator(mSensor, aStart, aEnd);
			}
			else
			{	return MongoCollectionIterator.getSensorForwardIterator(mSensor, aStart, aEnd, mFragment);
			}
		}
	}
	
	private ICollectionIterator getBackwardIterator(long aStart, long aEnd) throws NKFException
	{
		if (mIterator!=null)
		{	mIterator.reset();
			return mIterator;
		}
		else
		{	if (mFragment!=null && mFragment.equals(ERROR_FRAGMENT))
			{	return MongoCollectionIterator.getErrorBackwardIterator(mSensor, aStart, aEnd);
			}
			else
			{	return MongoCollectionIterator.getSensorBackwardIterator(mSensor, aStart, aEnd, mFragment);	
			}
		}
	}
	
	private Object onGeneric(IQueryIteratorController aController, boolean aForward) throws NKFException
	{	return onGeneric(aController, aForward, false);
	}

	private Object onGeneric(IQueryIteratorController aController, boolean aForward, boolean aRelativeTime) throws NKFException
	{	
		
		if (mPeriodMergeCount>1)
		{	
			//throw new NKFException("not implemented");
			long mergePeriod=(mTimeMergePeriod>0)?mTimeMergePeriod:(mEnd-mStart);
			List<IPolestarQueryResultSet> mergePeriodResults=new ArrayList<>();
			for (int i=0; i<mPeriodMergeCount; i++)
			{
				long start=mStart-i*mergePeriod;
				long end=mEnd-i*mergePeriod;

				ICollectionIterator it=aForward?getForwardIterator(start,end):getBackwardIterator(start,end);
				IPolestarQueryResultSet rs=aForward?iterateForward(it,aController,start,end,mPeriod):iterateBackward(it,aController,start,end,mPeriod);
				if (aRelativeTime)
				{	rs=createRelativeRS(rs);
				}
				mergePeriodResults.add(rs);
				aController.reset();
			}
			IPolestarQueryResultSet merged=mergePeriods(mergePeriodResults);
			if (mPeriod>0)
			{	return merged;
			}
			else
			{	return merged.getValue(0);
			}
			
		}
		else
		{	
			long start=mStart;
			long end=mEnd;
			
			ICollectionIterator it=aForward?getForwardIterator(start,end):getBackwardIterator(start,end);
			IPolestarQueryResultSet rs=aForward?iterateForward(it,aController,start,end,mPeriod):iterateBackward(it,aController,start,end,mPeriod);
			if (aRelativeTime)
			{	rs=createRelativeRS(rs);
			}
			if (mPeriod>0)
			{	return rs;
			}
			else
			{	return rs.getValue(0);
			}
		}
	}
	
	private IPolestarQueryResultSet mergePeriods(List<IPolestarQueryResultSet> aPeriodResults) throws NKFException
	{

		IPolestarQueryResultSet rs0=aPeriodResults.get(0);
		int rsSize=rs0.size();
		int periods=aPeriodResults.size();
		List<Long> times=new ArrayList<Long>(rsSize);
		List<Object> values=new ArrayList<Object>(rsSize);
		
		for (int rsIndex=0; rsIndex<rsSize; rsIndex++)
		{
			times.add(rs0.getTimestamp(rsIndex));
			
			double total=0;
			int count=0;
			for (int periodIndex=0; periodIndex<periods; periodIndex++)
			{
				Object value=aPeriodResults.get(periodIndex).getValue(rsIndex);
				System.out.println(rsIndex+" "+periodIndex+" "+value);
				
				if (value instanceof Number)
				{
					double v=((Number)value).doubleValue();
					total+=v;
					count++;
				}
			}
			values.add((total/count));
			
		}
		return new PolestarQueryResultSet(times, values);
		
	}
	
	private IPolestarQueryResultSet createRelativeRS(IPolestarQueryResultSet aRS)
	{	int s=aRS.size();
		List<Long> times=new ArrayList<>(s);
		List<Object> values=new ArrayList<>(s);
		for (int i=0; i<s; i++)
		{	Long time=aRS.getTimestamp(i);
			Long value=(Long)aRS.getValue(i);
			value=(value!=null)?(value-time):null;
			times.add(time);
			values.add(value);
		}
		PolestarQueryResultSet rs=new PolestarQueryResultSet(times, values);
		return rs;
	}
	
	private boolean isRelative()
	{	return mType.name().endsWith("_RELATIVE");
	}
	
	@Override
	public Object execute() throws NKFException
	{
		long t0=System.currentTimeMillis();
		try
		{
		switch(mType) {
		case DIFF:
			return onGeneric(QueryIteratorController.getDiffInstance(),true);
		case SUM:
			return onGeneric(QueryIteratorController.getSumInstance(),true);
		case RUNNING_TOTAL:
			return onGeneric(QueryIteratorController.getRunningTotalInstance(),true);
		case AVERAGE:
			return onGeneric(QueryIteratorController.getAverageInstance(),true);
		case MAX:
			return onGeneric(QueryIteratorController.getMaxInstance(),true);
		case MIN:
			return onGeneric(QueryIteratorController.getMinInstance(),true);
		case COUNT:
			return onGeneric(QueryIteratorController.getCountInstance(),true);
		case STDDEV:
			return onGeneric(QueryIteratorController.getStandardDeviationInstance(),true);
		
		case PERCENTILE:
			return onPercentile();
		case SAMPLE:
		case FIRST_VALUE:
			return onGeneric(QueryIteratorController.getFirstValueInstance(),true);
		case LAST_VALUE:
			return onGeneric(QueryIteratorController.getFirstValueInstance(),false);
		case FIRST_MATCH_TIME:
		case FIRST_MATCH_TIME_RELATIVE:
			return onGeneric(QueryIteratorController.getFirstMatchTimeInstance(getMatcher(),true),true,isRelative());
		case LAST_MATCH_TIME:
		case LAST_MATCH_TIME_RELATIVE:
			return onGeneric(QueryIteratorController.getFirstMatchTimeInstance(getMatcher(),false),false,isRelative());
		case FIRST_MATCH_VALUE:
			return onGeneric(QueryIteratorController.getFirstMatchValueInstance(getMatcher()),true);
		case LAST_MATCH_VALUE:
			return onGeneric(QueryIteratorController.getFirstMatchValueInstance(getMatcher()),false);
			
		case FIRST_EQUALS_TIME:
		case FIRST_EQUALS_TIME_RELATIVE:
			return onGeneric(QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getEqualsInstance(getParameter()),true),true,isRelative());
		case LAST_EQUALS_TIME:
		case LAST_EQUALS_TIME_RELATIVE:
			return onGeneric(QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getEqualsInstance(getParameter()),false),false,isRelative());
			
		case LAST_LESS_THAN_TIME:
		case LAST_LESS_THAN_TIME_RELATIVE:
			return onGeneric(QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getLessThanInstance(getParameter()),false),false,isRelative());
		case FIRST_LESS_THAN_TIME:
		case FIRST_LESS_THAN_TIME_RELATIVE:
			return onGeneric(QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getLessThanInstance(getParameter()),true),true,isRelative());
		case LAST_GREATER_THAN_TIME:
		case LAST_GREATER_THAN_TIME_RELATIVE:	
			return onGeneric(QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getGreaterThanInstance(getParameter()),false),false,isRelative());
		case FIRST_GREATER_THAN_TIME:
		case FIRST_GREATER_THAN_TIME_RELATIVE:
			return onGeneric(QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getGreaterThanInstance(getParameter()),true),true,isRelative());
			
		case LAST_MODIFIED:
		case LAST_MODIFIED_RELATIVE:
			return onGeneric(QueryIteratorController.getTimeAtIndexInstance(0),false,isRelative());
		case FIRST_MODIFIED:
		case FIRST_MODIFIED_RELATIVE:
			return onGeneric(QueryIteratorController.getTimeAtIndexInstance(1),true,isRelative());
			
		case DURATION_EQUALS:
			return onGeneric(QueryIteratorController.getMatchDurationInstance(MatcherFactory.getEqualsInstance(getParameter())),true);
		case DURATION_LESS_THAN:
			return onGeneric(QueryIteratorController.getMatchDurationInstance(MatcherFactory.getLessThanInstance(getParameter())),true);
		case DURATION_GREATER_THAN:
			return onGeneric(QueryIteratorController.getMatchDurationInstance(MatcherFactory.getGreaterThanInstance(getParameter())),true);
		case DURATION_MATCHES:
			return onGeneric(QueryIteratorController.getMatchDurationInstance(getMatcher()),true);
			
		case DISCRETE_MOST:
			return onGeneric(QueryIteratorController.getDiscreteMostInstance(),true);
		case POSITIVE_DIFF:
			return onGeneric(QueryIteratorController.getPositiveDiffInstance(),true);
		case BOOLEAN_RISING_EDGE_COUNT:
			return onGeneric(QueryIteratorController.getBooleanEdgeCountInstance(true),true);
		case BOOLEAN_FALLING_EDGE_COUNT:
			return onGeneric(QueryIteratorController.getBooleanEdgeCountInstance(false),true);
		case BOOLEAN_CHANGE:
			return onGeneric(QueryIteratorController.getBooleanChangeInstance(),true);
		case ROTATION_360_AVERAGE:
			return onGeneric(QueryIteratorController.getRotation360Instance(),true);
			
			
		default:
			throw new NKFException("Query not supported yet","For query type "+mType.toString());
		}
		
		}
		finally
		{
			t0=System.currentTimeMillis()-t0;
			if (t0>250)
			{
				String duration=MonitorUtils.formatPeriod(mEnd-mStart);
				try
				{	
					String fragment=mFragment==null?"":("#"+mFragment);
					PolestarContext.createContext(mContext,null)
					.logRaw(INKFLocale.LEVEL_INFO, "Query time="+t0+"ms for "+mSensor+fragment+" "+mType.toString()+" period="+duration);
				}
				catch (Exception e) {;}
			}
		}
		
	}
	

	private Object onPercentile() throws NKFException
	{	Object param=getParameter();
		if (param instanceof Number)
		{	float p=((Number)param).floatValue();
			if (p<0.0 || p>1.0)
			{	throw new NKFException("PERCENTILE parameter must be between 0 and 1");
			}
			
			//get median duration (i.e. sample rate)
			IQueryIteratorController qic1=QueryIteratorController.getMedianDurationInstance();
			//iterateForward(qic1);
			IPolestarQueryResultSet rs=iterateForward(getForwardIterator(mStart,mEnd), qic1,mStart,mEnd,mPeriod);
			//System.out.println("rs="+rs);
			//long medianDuration=(Long)qic1.getResult();
			//System.out.println("median duration="+medianDuration);
			
			IQueryIteratorController qic=QueryIteratorController.getPercentileInstance(p,rs);
			return onGeneric(qic,true);
		}
		else
		{	throw new NKFException("PERCENTILE requires numeric parameter");
		}
	}
	
}
