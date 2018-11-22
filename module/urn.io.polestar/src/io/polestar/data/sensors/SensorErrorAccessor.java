package io.polestar.data.sensors;

import java.util.List;
import java.util.Map;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFRequestReadOnly;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.urii.ParsedIdentifierImpl;
import org.netkernel.layer0.util.PairList;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

public class SensorErrorAccessor extends StandardAccessorImpl
{
	public SensorErrorAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String id=null;
		INKFRequestReadOnly thisReq=aContext.getThisRequest();
		if (thisReq.argumentExists("id"))
		{	id=thisReq.getArgumentValue("id");
		}
		String durationString=thisReq.getArgumentValue("duration");
		long duration = Long.parseLong(durationString);
		long now=System.currentTimeMillis();
		long start=now-duration;
		
		String action=aContext.getThisRequest().getArgumentValue(ParsedIdentifierImpl.ARG_ACTIVE_TYPE);
		if (action.equals("polestarSensorErrorList"))
		{	onList(id,start,aContext);
		}
		else if (action.equals("polestarSensorErrorInfo"))
		{	onInfo(id,start,now,aContext);
		}
	}
	
	
	static BasicDBObject getQuery(String id, long start, String op)
	{
		BasicDBObject queryO;
		if (id!=null)
		{	//mongodb 2.4 doesn't support $eq
			BasicDBList inO=new BasicDBList();
			inO.add(id);
			BasicDBObject idEqualsO=new BasicDBObject("i", new BasicDBObject("$in",inO));
			BasicDBObject startO=new BasicDBObject("t", new BasicDBObject(op,start));
			BasicDBList listO=new BasicDBList();
			listO.add(idEqualsO);
			listO.add(startO);
			queryO=new BasicDBObject("$and", listO);
		}
		else
		{	queryO=new BasicDBObject("t", new BasicDBObject(op,start));
		}
		return queryO;
	}
	
	/* used by backup */
	static BasicDBObject getQuery(List<String> ids, long start, long end)
	{
		//mongodb 2.4 doesn't support $eq
		BasicDBList inO=new BasicDBList();
		for (String id : ids)
		{	inO.add(id);
		}
		BasicDBObject idEqualsO=new BasicDBObject("i", new BasicDBObject("$in",inO));
		BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",start));
		BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",end));
		BasicDBList listO=new BasicDBList();
		listO.add(idEqualsO);
		listO.add(startO);
		listO.add(endO);
		BasicDBObject queryO=new BasicDBObject("$and", listO);
		return queryO;
	}
	
	
	
	public void onInfo(String id, long start, long now, INKFRequestContext aContext) throws Exception
	{
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		long tt=now-start;
		m.addNode("durationRaw",tt);
		m.addNode("duration", MonitorUtils.formatPeriod(tt));
		
		if (id==null)
		{	
			IHDSReader sensorConfig=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
			for (Object sensorIdRaw : sensorConfig.getValues("/sensors/sensor/id"))
			{
				String sensorId=(String)sensorIdRaw;
				getSensorInfo(sensorId,start,now,m);
			}
		}
		else
		{	getSensorInfo(id,start,now,m);
		}
		
		aContext.createResponseFrom(m.toDocument(false)).setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	private void getSensorInfo(String sensorId, long start, long now, IHDSMutator m) throws Exception
	{
		long tt=now-start;
		DBCollection col=MongoUtils.getCollection("errors");
		BasicDBObject queryO=getQuery(sensorId,start,"$lt");
		DBCursor cursor = col.find(queryO).sort(new BasicDBObject("t",-1)).limit(1);
		int lastLevel=0;
		long lastTimeD=start;
		long lastTime=start;
		String lastMsg=null;
		int errorCount=0;
		
		
		long tp=0;
		
		String lastError=null;
		long lastErrorStart=0;
		long lastErrorEnd=0;
		if (cursor.hasNext())
		{	DBObject previous=cursor.next();
			lastLevel=(Integer)previous.get("l");
			lastMsg=(String)previous.get("m");
			lastTime=(Long)previous.get("t");
		}
		cursor.close();
		
		queryO=getQuery(sensorId,start,"$gte");
		cursor = col.find(queryO);
		while (cursor.hasNext())
		{	DBObject error=cursor.next();
			long t=(Long)error.get("t");
			int l=(Integer)error.get("l");
			long d=t-lastTimeD;
			if (lastLevel>0)
			{	tp+=d;
				lastErrorStart=lastTime;
				lastError=lastMsg;
				errorCount++;
			}
			else
			{	lastErrorEnd=lastTime;
			}
			//tt+=d;
			lastMsg=(String)error.get("m");
			lastLevel=l;
			lastTime=t;
			lastTimeD=t;
		}
		
		long d=now-lastTime;
		if (lastLevel>0)
		{	tp+=d;
			lastErrorStart=lastTime;
			lastError=lastMsg;
			errorCount++;
		}
		else
		{	lastErrorEnd=lastTime;
		}
		//tt+=d;
		
		double r=((double)tp)/((double)tt);
		String currentError;
		long lastErrorDuration;
		if (lastErrorEnd<lastErrorStart)
		{
			currentError=lastError;
			lastErrorDuration=now-lastErrorStart;
		}
		else
		{	currentError=null;
			lastErrorDuration=lastErrorEnd-lastErrorStart;
		}
		long lastErrorCleared=(lastErrorEnd>lastErrorStart)?lastErrorEnd:-1L;
		m.pushNode("sensor")
		.addNode("id", sensorId)
		.addNode("errorPercent", r)
		.addNode("errorCount", errorCount)
		.addNode("currentError",currentError)
		.addNode("lastError",lastError)
		.addNode("lastErrorRaised",MonitorUtils.formatPeriod(now-lastErrorStart))
		.addNode("lastErrorRaisedRaw",lastErrorStart)
		.addNode("lastErrorCleared",lastErrorCleared>=0?MonitorUtils.formatPeriod(now-lastErrorCleared):"not cleared")
		.addNode("lastErrorClearedRaw",lastErrorCleared)
		.addNode("lastErrorDurationRaw",lastErrorDuration)
		.addNode("lastErrorDuration",MonitorUtils.formatPeriod(lastErrorDuration))
		.addNode("errorDurationRaw",tp)
		.addNode("errorDuration", MonitorUtils.formatPeriod(tp))
		.popNode();
		
		
		
	}
	//
	
	public void onList(String id, long start, INKFRequestContext aContext) throws Exception
	{
		DBCollection col=MongoUtils.getCollection("errors");
		BasicDBObject queryO=getQuery(id,start,"$gte");
		
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("errors");
		DBCursor cursor = col.find(queryO);
		while (cursor.hasNext())
		{	DBObject error=cursor.next();
			Long t=(Long)error.get("t");
			int l=(Integer)error.get("l");
			String i=(String)error.get("i");
			String msg=(String)error.get("m");
			m.pushNode("error")
			.addNode("t", t)
			.addNode("i", i)
			.addNode("l", l)
			.addNode("m", msg)
			.popNode();
		}
		INKFResponse respOut=aContext.createResponseFrom(m.toDocument(false));
		respOut.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	//used by data.sensor.SensorQueryAccessor when selecting to get error data
	//returns error level
	public static PairList errorQuery(String sensorId, String fragment, long start, long end, long samplePeriod, MergeAction merge) throws Exception
	{
		int size=(int)((end-start)/samplePeriod);
		PairList result=new PairList(size);

		DBCollection col=MongoUtils.getCollection("errors");
		merge.update(0, 0);
		//initialise with value directly before start
		BasicDBObject queryO=getQuery(sensorId,start,"$lt");
		DBCursor cursorP = col.find(queryO).sort(new BasicDBObject("t",-1)).limit(1);
		if (cursorP.hasNext())
		{
			DBObject previous=cursorP.next();
			merge.update(previous.get("l"), start);
		}
		merge.getValue(start);
	
		//perform query
		BasicDBList inO=new BasicDBList();
		inO.add(sensorId);
		BasicDBObject idEqualsO=new BasicDBObject("i", new BasicDBObject("$in",inO));
		BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",start));
		BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",end));
		BasicDBList listO=new BasicDBList();
		listO.add(idEqualsO);
		listO.add(startO);
		listO.add(endO);
		queryO=new BasicDBObject("$and", listO);
		DBCursor cursor = col.find(queryO);
		
		//iterate through periods
		long lastSampleTime=start;
		DBObject capture=null;
		for (long t=start; t<end; t+=samplePeriod)
		{	long sampleEndTime=t+samplePeriod;
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
				lastSampleTime=time;
				
				if (time<sampleEndTime)
				{
					Object v=capture.get("l");

					//process merge action on sensors
					merge.update(v,time);
					capture=null;
				}
				else
				{	break; //move into next sample
				}
				
				
			} while(true);
			
			Object mv=merge.getValue(sampleEndTime);
			result.put(t,mv);
		}		
		return result;
	}
	
}
