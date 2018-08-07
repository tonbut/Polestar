package io.polestar.data.sensors;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.util.MultiMap;
import org.netkernel.layer0.util.PairList;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.mod.hds.impl.HDSDocumentImpl;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import org.netkernel.util.Utils;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import io.polestar.data.db.MongoUtils;
import io.polestar.data.sensors.MergeAction.Type;

public class SensorQueryAccessor extends StandardAccessorImpl
{
	public SensorQueryAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		//process input configuration
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader().getFirstNode("query");
		boolean jsonMode=operator.getFirstNodeOrNull("json")!=null;
		DateFormat df;
		String timeFormat=(String)operator.getFirstValueOrNull("timeFormat");
		if (timeFormat==null)
		{	df=new SimpleDateFormat("kk:mm");
		}
		else
		{	df=new SimpleDateFormat(timeFormat);
		}
		long now=System.currentTimeMillis();
		long start=now-1000*60*60*24;
		long end=now;
		long samplePeriod=1000*60*5;
		Long startValue=(Long)operator.getFirstValueOrNull("start");
		if (startValue!=null)
		{	if (startValue<0)
			{	start=now+startValue;
			}
			else
			{	start=startValue;
			}
		}
		Long endValue=(Long)operator.getFirstValueOrNull("end");
		if (endValue!=null)
		{	if (endValue<0)
			{	end=now+endValue;
			}
			else
			{	end=endValue;
			}
		}
		Long samplePeriodValue=(Long)operator.getFirstValueOrNull("samplePeriod");
		if (samplePeriodValue!=null)
		{	samplePeriod=samplePeriodValue;
		}
		
		//long month=1000L*60*60*24*32;
		//start-=month;
		//end-=month;
		//System.out.println("end is "+new Date(end));
		
		//process sensors
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		List<IHDSReader> sensors=operator.getNodes("sensors/sensor");
		
		int c=1;
		PairList resultTuple = new PairList(sensors.size());
		for (IHDSReader sensor : sensors)
		{	String id=(String)sensor.getFirstValue("id");
			String fragment=null;
			int i=id.indexOf('#');
			if (i>=0)
			{	fragment=id.substring(i+1);
				id=id.substring(0, i);
			}
			try
			{
				IHDSReader configNode=config.getFirstNodeOrNull("key('byId','"+id+"')");
				
				String script=(String)configNode.getFirstValueOrNull("script");
				//System.out.println(sensor);
				if (script!=null)
				{	populateSensor(id,script,configNode,start,end,aContext);
				}
				
				String name=(String)sensor.getFirstValueOrNull("name");
				if (name==null)
				{	name="sensor"+(c++);
				}
				String format=(String)configNode.getFirstValueOrNull("format");
				if (format!=null && format.equals("rgb"))
				{	format="%.3f";
				}
				else if (format!=null && !format.contains("%"))
				{	format=null;
				}
				
				String mergeActionString=(String)sensor.getFirstValueOrNull("mergeAction");
				MergeAction mergeAction;
				if (mergeActionString==null)
				{	mergeAction=MergeAction.getMergeAction(name,format,MergeAction.Type.SAMPLE);
				}
				else
				{	Type mergeActionType=Type.valueOf(mergeActionString.toUpperCase());
					mergeAction=MergeAction.getMergeAction(name,format,mergeActionType);
				}
				
				PairList data=getSensorData(id,fragment,start,end,samplePeriod,mergeAction);
				resultTuple.put(mergeAction, data);
			}
			catch (Exception e)
			{
				aContext.logRaw(INKFLocale.LEVEL_WARNING, "Failed to query sensor ["+id+"]:"+Utils.throwableToString(e));
			}
			
		}
		
		if (jsonMode)
		{	generateJSONResponse(resultTuple,df,aContext);
		}
		else
		{	generateHDSResponse(resultTuple,df,aContext);
		}
		
		
	}
	
	private void generateJSONResponse(PairList aResultTuple, DateFormat df, INKFRequestContext aContext) throws Exception
	{
		StringBuilder sb=new StringBuilder(4096);
		sb.append("[ ");
		
		if (aResultTuple.size()!=0)
		{
			//IHDSReader firstSensorReader=((IHDSDocument)aResultTuple.getValue2(0)).getReader();
			//Double cd=(Double)firstSensorReader.getFirstValue("count(/*)");
			//int c=cd.intValue();
			int c=((PairList)aResultTuple.getValue2(0)).size();
			for (int i=0; i<c; i++)
			{
				
				sb.append("[ ");
				
				for (int j=0; j<aResultTuple.size(); j++)
				{
					//IHDSReader r=((IHDSDocument)aResultTuple.getValue2(j)).getReader();
					PairList r=(PairList)aResultTuple.getValue2(j);
					if (j!=0)
					{	sb.append(", ");
					}
					else
					{
						long time=(Long)r.getValue1(i);
						sb.append(time);
						sb.append(",'");
						sb.append(df.format(new Date(time)));
						sb.append("',");
					}
					
					MergeAction ma=(MergeAction)aResultTuple.getValue1(j);
					Object v=r.getValue2(i);
					
					//Object v=ma.getValue();
					if (v==null)
					{	sb.append("null");
					}
					else if (v instanceof Map)
					{	String format=ma.getFormat();
						Map<String,Object> bdo=(Map)v;
						sb.append("{");
						for (Map.Entry<String, Object> entry : bdo.entrySet())
						{	sb.append("\"");
							sb.append(entry.getKey());
							sb.append("\":");
							outputValue(sb,entry.getValue(),format);
							sb.append(",");
						}
						sb.append("}");
					}
					else
					{	String format=ma.getFormat();
						outputValue(sb,v,format);	
					}
				}
				
				sb.append("],\n");
				
			}
		}
		
		sb.append("]");
		//System.out.println(sb.toString());
		INKFResponse respOut=aContext.createResponseFrom(sb.toString());
		respOut.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	private void outputValue(StringBuilder sb, Object v, String format)
	{
		boolean needsQuotes=(v instanceof String);
		if (needsQuotes) sb.append("'");
		if (format==null)
		{	sb.append(v);
		}
		else
		{	String sf;
			try
			{	sf=String.format(format, v);
			} catch (Exception e)
			{	sf=v.toString();
			}
			sb.append(sf);
		}
		
		if (needsQuotes) sb.append("'");
	}
	
	private void generateHDSResponse(PairList aResultTuple, DateFormat df, INKFRequestContext aContext) throws Exception
	{
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("rows");
		if (aResultTuple.size()>0)
		{
			int c=((PairList)aResultTuple.getValue2(0)).size();
			for (int i=0; i<c; i++)
			{
				m.pushNode("row");
				for (int j=0; j<aResultTuple.size(); j++)
				{	PairList r=(PairList)aResultTuple.getValue2(j);
					if (j==0)
					{
						long time=(Long)r.getValue1(i);	
						m.addNode("time", time)
						.addNode("timeString", df.format(new Date(time)));
					}
					
					MergeAction ma=(MergeAction)aResultTuple.getValue1(j);
					Object v=r.getValue2(i);
					
					if (v instanceof BasicDBObject)
					{	BasicDBObject bdo=(BasicDBObject)v;
						m.pushNode(ma.getName());
						for (Map.Entry<String, Object> entry : bdo.entrySet())
						{	m.addNode(entry.getKey(), entry.getValue());
						}
						m.popNode();
					}
					else if (v instanceof Map)
					{	Map<String,Object> bdo=(Map)v;
						m.pushNode(ma.getName());
						for (Map.Entry<String, Object> entry : bdo.entrySet())
						{	m.addNode(entry.getKey(), entry.getValue());
						}
						m.popNode();
					}
					else
					{	m.addNode(ma.getName(), v);
					}
					
					
				}
				m.popNode();
			}

		}
		
		m.popNode();
		
		INKFResponse respOut=aContext.createResponseFrom(m.toDocument(false));
		respOut.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	
	private PairList getSensorData(String sensorId, String fragment, long start, long end, long samplePeriod, MergeAction merge) throws Exception
	{
		int size=(int)((end-start)/samplePeriod);
		PairList result=new PairList(size);

		DBCollection col=MongoUtils.getCollectionForSensor(sensorId);
		
		//initialise with value directly before start
		BasicDBObject startI=new BasicDBObject("t", new BasicDBObject("$lt",start));
		DBCursor cursorP = col.find(startI).sort(new BasicDBObject("t",-1)).limit(1);
		if (cursorP.hasNext())
		{
			DBObject previous=cursorP.next();
			merge.update(previous.get("v"), start);
		}
		merge.getValue(start);
		
		
		//perform query
		BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",start));
		BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",end));
		BasicDBList listO=new BasicDBList();
		listO.add(startO);
		listO.add(endO);
		BasicDBObject queryO=new BasicDBObject("$and", listO);
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
					Object v=capture.get("v");
					if (fragment!=null && v instanceof Map)
					{
						Map m=(Map)v;
						v=m.get(fragment);
					}

					//process merge action on sensors
					merge.update(v,time);
					capture=null;
				}
				else
				{	break; //move into next sample
				}
				
				
			} while(true);
			
			//if no data(/always?) in period then see if we have some dynamic script
			//check if there is a script that provides this value
			//find time ranges that overlap with sample period 
			//then execute these time ranges
			//do this before query?
			//script - sensorid,datum,period
			
			
			Object mv=merge.getValue(sampleEndTime);
			result.put(t,mv);
		}
		return result;
	}
	
	private void populateSensor(String id, String script, IHDSReader aSensorConfig, long start, long end, INKFRequestContext aContext) throws Exception
	{
		//System.out.println("populate "+id);
		IHDSReader scriptNode=aContext.source("res:/md/script/"+script,IHDSDocument.class).getReader();
		//System.out.println(scriptNode);
		long period=Long.parseLong((String)scriptNode.getFirstValue("/script/period"));
		long offset=0;
		// take s0 = start time and subtract period
		// times = offset +k * period
		//s0 - offset / period = n0 (first sample before period)
		long s0=start-period;
		long n1=1+(s0-offset)/period;
		
		//perform query to get any existing data
		DBCollection col=MongoUtils.getCollectionForSensor(id);
		BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",offset+n1*period));
		BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",end));
		BasicDBList listO=new BasicDBList();
		listO.add(startO);
		listO.add(endO);
		BasicDBObject queryO=new BasicDBObject("$and", listO);
		DBCursor cursor = col.find(queryO);
		Set<Long> existingSamples=new HashSet<>();
		while (cursor.hasNext())
		{	DBObject capture=cursor.next();
			Long time=(Long)capture.get("t");
			existingSamples.add(time);
		}
		cursor.close();
		
		
		long n=n1;

		do
		{	long t=offset+n*period;
			boolean existing=existingSamples.contains(t);
			//System.out.println("populate "+id+" "+new Date(t)+" "+existing);
			if (!existing)
			{
				try
				{
					INKFRequest req=aContext.createRequest("active:polestarExecuteScript");
					req.addArgument("script", script);
					req.addArgumentByValue("timestamp", t);
					Object result=aContext.issueRequest(req);
					//System.out.println(result);
					SensorListAccessor.sInstance.updateSensorState(id, result, t, null, aContext);
					SensorListAccessor.storeSensorState(id, result, t, col, aContext);
				}
				catch (Exception e)
				{	String msg="Script Failure";
					SensorListAccessor.sInstance.updateSensorState(id, null, System.currentTimeMillis(), msg, aContext);
					aContext.logRaw(INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
				}
			}
			
			n++;
		} while (offset+n*period<end);
		
	}
	
	
}
