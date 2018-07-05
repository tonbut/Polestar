package io.polestar.data.api;

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
import io.polestar.api.QueryType;
import io.polestar.data.api.QueryIteratorController.IQueryIteratorController;
import io.polestar.data.db.MongoUtils;

public class PolestarQuery implements IPolestarQuery
{
	private static final long HOURS24=1000L*60*60*24;
	//private static final long RELATIVE_TIME_THRESHOLD=HOURS24*365*10;
	
	private final String mSensor;
	private final QueryType mType;
	private final INKFRequestContext mContext;
	private final PolestarContext mPolestarContext;
	private long mStart, mEnd;
	private Object mParameter;
	private IPolestarMatcher mMatcher;
	
	public PolestarQuery(String aSensor, QueryType aType, INKFRequestContext aContext, PolestarContext aPolestarContext)
	{	mSensor=aSensor;
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
		m.addNode("samplePeriod", mEnd-mStart);
		m.pushNode("sensors"); //list of sensors that we want to get data for
		m.pushNode("sensor").addNode("id",mSensor);
		return m;
	}
	
	private Object executeHistoricalQuery(IHDSDocument aQuery) throws NKFException
	{	INKFRequest req=mContext.createRequest("active:polestarHistoricalQuery");
		req.addArgumentByValue("operator",aQuery);
		req.setRepresentationClass(IHDSDocument.class);
		IHDSDocument rep=(IHDSDocument)mContext.issueRequest(req);
		return rep.getReader().getFirstValue("/rows/row[1]/sensor1");
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
	
	private void iterateBackward(IQueryIteratorController aController) throws NKFException
	{
		try 
		{
			DBCollection col=MongoUtils.getCollectionForSensor(mSensor);
			
			BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",mStart));
			BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",mEnd));
			BasicDBList listO=new BasicDBList();
			listO.add(startO);
			listO.add(endO);
			BasicDBObject queryO=new BasicDBObject("$and", listO);
			DBCursor cursor = col.find(queryO).sort(new BasicDBObject("t",-1));
			
			boolean continueIterating=true;
			DBObject capture=null;
			int index=0;
			long lastTime=mEnd;
			do
			{	if (cursor.hasNext())
				{	capture=cursor.next();
				}
				else
				{	break;
				}
				long time=(Long)capture.get("t");
				Object value=capture.get("v");
				continueIterating=aController.accept(value, time, lastTime-time, index);
				index++;
				lastTime=time;
			} while (continueIterating);
			
			if (continueIterating)
			{
				BasicDBObject startI=new BasicDBObject("t", new BasicDBObject("$lt",mStart));
				DBCursor cursorP = col.find(startI).sort(new BasicDBObject("t",-1)).limit(1);
				if (cursorP.hasNext())
				{	DBObject previous=cursorP.next();
					Object value=previous.get("v");
					continueIterating=aController.accept(value, mStart, lastTime-mStart, index);
				}
				else
				{	continueIterating=aController.accept(null, mStart, lastTime-mStart, index);
				}
				
			}
		
		} catch (Exception e)
		{	throw new NKFException("Unhandled Exception", null, e);
		}
	}
	
	
	@Override
	public Object execute() throws NKFException
	{
		switch(mType) {
		case DIFF:
			return onDiff();
		case SUM:
			return onSum();
		case AVERAGE:
			return onAverage();
		case MAX:
			return onMax();
		case MIN:
			return onMin();
		case COUNT:
			return onCount();
		case FIRST_VALUE:
			return onFirstValue();
		case LAST_VALUE:
			return onLastValue();
		case FIRST_MATCH_TIME:
			return onFirstMatchTime();
		case LAST_MATCH_TIME:
			return onLastMatchTime();
		case FIRST_MATCH_VALUE:
			return onFirstMatchValue();
		case LAST_MATCH_VALUE:
			return onLastMatchValue();
		case FIRST_EQUALS_TIME:
			return onFirstEqualsTime();
		case LAST_EQUALS_TIME:
			return onLastEqualsTime();
		case LAST_LESS_THAN_TIME:
			return onLastLessThanTime();
		case FIRST_LESS_THAN_TIME:
			return onFirstLessThanTime();
		case LAST_GREATER_THAN_TIME:
			return onLastGreaterThanTime();
		case FIRST_GREATER_THAN_TIME:
			return onFirstGreaterThanTime();
		case LAST_MODIFIED:
			return onLastModified();
		case FIRST_MODIFIED:
			return onFirstModified();
		case DURATION_EQUALS:
			return onDurationEquals();
		case DURATION_LESS_THAN:
			return onDurationLessThan();
		case DURATION_GREATER_THAN:
			return onDurationGreaterThan();
		case DURATION_MATCHES:
			return onDurationMatches();
			
		default:
			throw new NKFException("Query supported yet","For query type "+mType.toString());
		}
	}
	
	
	private Object onDiff() throws NKFException
	{	IHDSMutator m=createHistoricalQueryBase();
		m.addNode("mergeAction","diff").popNode();
		return executeHistoricalQuery(m.toDocument(false));
	}
	
	private Object onSum() throws NKFException
	{	IHDSMutator m=createHistoricalQueryBase();
		m.addNode("mergeAction","sum").popNode();
		return executeHistoricalQuery(m.toDocument(false));
	}
	
	private Object onAverage() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getAverageInstance();
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onMax() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getMaxInstance();
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onMin() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getMinInstance();
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onCount() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getCountInstance();
		iterateForward(qic);
		return qic.getResult();
	}
	
	
	
	
	private Object onFirstValue() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstValueInstance();
		iterateForward(qic);
		return qic.getResult();
	}
	
	private Object onLastValue() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstValueInstance();
		iterateBackward(qic);
		return qic.getResult();
	}
	
	private Object onFirstMatchTime() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstMatchTimeInstance(getMatcher());
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onLastMatchTime() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstMatchTimeInstance(getMatcher());
		iterateBackward(qic);
		return qic.getResult();
	}
	
	private Object onFirstMatchValue() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstMatchValueInstance(getMatcher());
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onLastMatchValue() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstMatchValueInstance(getMatcher());
		iterateBackward(qic);
		return qic.getResult();
	}
	
	private Object onFirstEqualsTime() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getEqualsInstance(getParameter()));
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onLastEqualsTime() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getEqualsInstance(getParameter()));
		iterateBackward(qic);
		return qic.getResult();
	}
	
	private Object onFirstLessThanTime() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getLessThanInstance(getParameter()));
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onLastLessThanTime() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getLessThanInstance(getParameter()));
		iterateBackward(qic);
		return qic.getResult();
	}
	private Object onFirstGreaterThanTime() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getGreaterThanInstance(getParameter()));
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onLastGreaterThanTime() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getFirstMatchTimeInstance(MatcherFactory.getGreaterThanInstance(getParameter()));
		iterateBackward(qic);
		return qic.getResult();
	}
	
	private Object onFirstModified() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getTimeAtIndexInstance(1);
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onLastModified() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getTimeAtIndexInstance(0);
		iterateBackward(qic);
		return qic.getResult();
	}
	
	private Object onDurationEquals() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getMatchDurationInstance(MatcherFactory.getEqualsInstance(getParameter()));
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onDurationLessThan() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getMatchDurationInstance(MatcherFactory.getLessThanInstance(getParameter()));
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onDurationGreaterThan() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getMatchDurationInstance(MatcherFactory.getGreaterThanInstance(getParameter()));
		iterateForward(qic);
		return qic.getResult();
	}
	private Object onDurationMatches() throws NKFException
	{	IQueryIteratorController qic=QueryIteratorController.getMatchDurationInstance(getMatcher());
		iterateForward(qic);
		return qic.getResult();
	}

	
	
}
