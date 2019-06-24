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
package io.polestar.data.sensors;

import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BSONObject;
import org.netkernel.layer0.nkf.*;
import org.netkernel.layer0.representation.ByteArrayRepresentation;
import org.netkernel.layer0.representation.IBinaryStreamRepresentation;
import org.netkernel.layer0.representation.IReadableBinaryStreamRepresentation;
import org.netkernel.layer0.urii.ParsedIdentifierImpl;
import org.netkernel.layer0.util.GoldenThreadExpiryFunction;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import org.netkernel.util.Utils;

import io.polestar.api.IPolestarContext;
import io.polestar.api.QueryType;
import io.polestar.data.api.PolestarContext;
import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

public class SensorListAccessor extends StandardAccessorImpl
{
	private final Map<String,SensorState> mSensorStates=new ConcurrentHashMap<String, SensorState>();
	private final Map<String,String> mChanges=new ConcurrentHashMap<String, String>();
	private final SensorState DEFAULT_SENSOR_STATE;
	private GoldenThreadExpiryFunction mStateUpdateExpiry;
	
	public static final String SENSOR_ERROR="error";
	public static SensorListAccessor sInstance;
	
	public SensorListAccessor()
	{	this.declareThreadSafe();
		DEFAULT_SENSOR_STATE=new SensorState();
		mStateUpdateExpiry=new GoldenThreadExpiryFunction("SensorListAccessor");
		sInstance=this;
	}
	
	public void postCommission(INKFRequestContext aContext) throws Exception
	{	
		MonitorUtils.log(aContext,null,INKFLocale.LEVEL_INFO,"## Polestar Started ##");
	
		BasicDBObject query = new BasicDBObject("id",0);
		DBCollection col=MongoUtils.getCollection("sensorState");
		DBCursor cursor = col.find(query);
		try
		{	if(cursor.hasNext())
			{	DBObject dbo=cursor.next();	
				byte[] hds=(byte[])dbo.get("hds");
				IHDSDocument state=aContext.transrept(new ByteArrayRepresentation(hds), IHDSDocument.class);
				for (IHDSReader sensor : state.getReader().getNodes("/sensors/sensor"))
				{	String id=(String)sensor.getFirstValue("id");
					try
					{	SensorState ss=new SensorState(sensor);
						mSensorStates.put(id, ss);
					} catch (Exception e) {;}
				}
			}
		} finally
		{	cursor.close();
		}
		
	
		//index all sensors
		Set<String> collections=MongoUtils.getDB().getCollectionNames();
		for (String collection : collections)
		{	try
			{	if (collection.startsWith("sensor:"))
				{	
					col=MongoUtils.getCollection(collection);
					List<DBObject> indexes=col.getIndexInfo();
					if (indexes.size()<2)
					{
						MonitorUtils.log(aContext,null,INKFLocale.LEVEL_INFO,"Creating time index for "+collection);
						col.createIndex(new BasicDBObject("t", 1));
					}
				}
			}
			catch (Exception e)
			{	MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
			}
		}
		
		//index errors
		try
		{	col=MongoUtils.getCollection("errors");
			List<DBObject> indexes=col.getIndexInfo();
			//System.out.println(indexes.size()+" indices on errors");
			if (indexes.size()<2)
			{	col.createIndex(new BasicDBObject("t", 1));
				col.createIndex(new BasicDBObject("i", 1));
			}
		}
		catch (Exception e)
		{	MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
		}
		
		
		//run startup scripts
		MonitorUtils.executeTriggeredScripts(Collections.singleton("startup"), true, aContext);
	}
	

	public void preDecommission(INKFRequestContext aContext) throws Exception
	{	
		//run shutdown scripts
		MonitorUtils.executeTriggeredScripts(Collections.singleton("shutdown"), true, aContext);		
		MonitorUtils.log(aContext,null,INKFLocale.LEVEL_INFO,"Monitor Stopped");

		saveSensorState(aContext);
		
	}
	
	private void saveSensorState(INKFRequestContext aContext) throws Exception
	{
		//save current sensor state
		IHDSDocument state=getState(aContext);
		IBinaryStreamRepresentation bs=aContext.transrept(state, IBinaryStreamRepresentation.class);
		ByteArrayOutputStream baos=new ByteArrayOutputStream(4096);
		bs.write(baos);
		baos.flush();
		DBCollection col=MongoUtils.getCollection("sensorState");
		BasicDBObject o=new BasicDBObject("hds",baos.toByteArray());
		o.append("id", 0);
		BasicDBObject set = new BasicDBObject("$set",o);
		BasicDBObject query = new BasicDBObject("id",0);
		WriteResult wr=col.update(query, set, true, false);
	}
	

	public void onSource(INKFRequestContext aContext) throws Exception
	{	String action=aContext.getThisRequest().getArgumentValue(ParsedIdentifierImpl.ARG_ACTIVE_TYPE);
		if (action.equals("polestarSensorConfig"))
		{	onConfig(aContext);
		}
		else if (action.equals("polestarSensorState"))
		{	onState(aContext);
		}
		else if (action.equals("polestarSensorUpdate"))
		{	IHDSReader state=aContext.source("arg:state",IHDSDocument.class).getReader();
			onUpdate(state,aContext);
		}
		else if (action.equals("polestarSensorChanges"))
		{	onChanges(aContext);
		}
		else if (action.equals("polestarSensorReadingCheck"))
		{	onReadingCheck(aContext);
		}
		else if (action.equals("polestarSensorStatePersist"))
		{	saveSensorState(aContext);
		}
		else if (action.equals("polestarSensorInfo"))
		{	onSensorInfo(aContext);
		}
		else if (action.equals("polestarSensorInfoDelete"))
		{	onSensorInfoDelete(aContext);
		}
		else if (action.equals("polestarSensorStateRefresh"))
		{	IHDSReader state=aContext.source("arg:state",IHDSDocument.class).getReader();
			onSensorStateRefresh(state,aContext);
		}
	}
	
	/** look at historical data stored in mongoDB to update cached in memory current state of sensor **/
	public void onSensorStateRefresh(IHDSReader aState,INKFRequestContext aContext) throws Exception
	{
		IPolestarContext pctx=PolestarContext.createContext(aContext,null);
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		
		for (Object sensorId : aState.getValues("/sensors/sensor"))
		{
			IHDSReader sensorDef=config.getFirstNodeOrNull(String.format("key('byId','"+sensorId+"')"));
			if (sensorDef!=null)
			{
				SensorState ss=getSensorState((String)sensorId,true);
				Object lastModified=pctx.createQuery((String)sensorId, QueryType.LAST_MODIFIED).setStart(0L).execute();
				if (lastModified!=null)
				{
					Object lastValue=pctx.createQuery((String)sensorId, QueryType.LAST_VALUE).setStart(0L).execute();
					MonitorUtils.log(aContext,null,INKFLocale.LEVEL_INFO, "Refreshed sensor state for "+sensorId);
					ss.setValue(lastValue, (Long)lastModified, sensorDef, aContext);
				}
			}
		}
	}
	
	public void onSensorInfoDelete(INKFRequestContext aContext) throws Exception
	{
		String toDelete=aContext.source("arg:state",String.class);
		//System.out.println("toDelete "+toDelete);
		DBCollection col=MongoUtils.getCollectionForSensor(toDelete);
		col.remove(new BasicDBObject());
	}
	
	public void onSensorInfo(INKFRequestContext aContext) throws Exception
	{
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		Set<String> collections=MongoUtils.getDB().getCollectionNames();
		for (String collection : collections)
		{
			DBCollection col=MongoUtils.getCollection(collection);
			
			if (collection.startsWith("sensor:"))
			{	
				CommandResult cr=col.getStats();
				Long count=cr.getLong("count");
				Long size=cr.getLong("size");

				long first=-1;
				long last=-1;
				DBCursor cursor;
				
				try
				{
					cursor = col.find().sort(new BasicDBObject("t",1)).limit(1);
					if (cursor.hasNext())
					{	DBObject entry=cursor.next();
						first=(Long)entry.get("t");
					}
					cursor = col.find().sort(new BasicDBObject("t",-1)).limit(1);
					if (cursor.hasNext())
					{	DBObject entry=cursor.next();
						last=(Long)entry.get("t");
					}
				}
				catch (Exception e)
				{
				}
					
				String id=collection.substring(7);
				
				DateFormat df=DateFormat.getDateInstance(DateFormat.SHORT);
				String firstString=first>0?df.format(new Date(first)):"none";
				String lastString=last>0?df.format(new Date(last)):"none";
				long avgSize=count>0?size/count:0L;
				m.pushNode("sensor")
				.addNode("id",id)
				.addNode("count", count)
				.addNode("size", size)
				.addNode("avgSize", avgSize)
				.addNode("first", firstString)
				.addNode("last", lastString)
				.addNode("firstraw", first)
				.addNode("lastraw", last)
				.popNode();

				//System.out.println(collection+" "+count+" "+size+" "+size/count);
			}
		}
		m.declareKey("byId", "/sensors/sensor", "id");
		INKFResponse resp=aContext.createResponseFrom(m.toDocument(false));
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	//check to see if any sensors are not updating
	public void onReadingCheck(INKFRequestContext aContext) throws Exception
	{	long now=System.currentTimeMillis();
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		boolean anyErrorChange=false;
		for (IHDSReader sensorDef : config.getNodes("/sensors/sensor"))
		{	String id=(String)sensorDef.getFirstValue("id");
			SensorState ss=getSensorState(id,false);
			ss.poll(sensorDef, now,aContext);
			if (ss.getErrorLastModified()==now)
			{	mChanges.put(id, id);
				recordError(id,now,aContext);
				anyErrorChange=true;
			}
		}
		if (anyErrorChange)
		{	mChanges.put(SENSOR_ERROR, SENSOR_ERROR);
		}
							
	}
	

	public void onChanges(INKFRequestContext aContext) throws Exception
	{
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		if (mChanges.size()>0)
		{	List<String> changes=new ArrayList<String>(mChanges.keySet());
			mChanges.clear();
			for (String sensor : changes)
			{	m.addNode("sensor", sensor);
			}	
		}
		GoldenThreadExpiryFunction lastExpiry=mStateUpdateExpiry;
		mStateUpdateExpiry=new GoldenThreadExpiryFunction("SensorListAccessor");
		lastExpiry.invalidate();

		INKFResponse resp=aContext.createResponseFrom(m.toDocument(false));
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}

	public void onConfig(INKFRequestContext aContext) throws Exception
	{	IHDSMutator config;
		try
		{	config=aContext.source("res:/md/execute/named/SensorList",IHDSDocument.class).getMutableClone();
			IHDSReader scripts=aContext.source("active:polestarListScripts",IHDSDocument.class).getReader();
			scripts.declareKey("byTarget", "/scripts/script", "target");
			for (IHDSMutator sensorNode : config.getNodes("/sensors/sensor"))
			{	String icon=(String)sensorNode.getFirstValueOrNull("icon");
				if (icon==null)
				{	icon="/polestar/pub/icon/circle-dashed.png";
					sensorNode.createIfNotExists("icon").setValue(icon).resetCursor();
				}
				String sensorId=(String)sensorNode.getFirstValue("id");
				String xpath="key('byTarget','"+sensorId+"')";
				IHDSReader targetScript=scripts.getFirstNodeOrNull(xpath);
				if (targetScript!=null)
				{	String scriptId=(String)targetScript.getFirstValue("id");
					sensorNode.addNode("script", scriptId);
				}
			}
			config.declareKey("byId", "/sensors/sensor", "id");
		}
		catch (NKFException e)
		{	
			if (e.getDeepestId().equals("Script not found"))
			{	MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, "\"SensorList\" script not defined yet");
			}
			else
			{	MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
			}
			config=HDSFactory.newDocument();
			config.pushNode("sensors");
		}
		INKFResponse resp=aContext.createResponseFrom(config.toDocument(false));
	}

	public void onState(INKFRequestContext aContext) throws Exception
	{	//System.out.println("onState");
		IHDSDocument state=getState(aContext);
		INKFResponse resp=aContext.createResponseFrom(state);
		resp.setExpiry(INKFResponse.EXPIRY_MIN_FUNCTION_DEPENDENT,mStateUpdateExpiry);
	}
	
	private IHDSDocument getState(INKFRequestContext aContext) throws Exception
	{	IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		long now=System.currentTimeMillis();
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		for (IHDSReader sensorDef : config.getNodes("/sensors/sensor"))
		{
			String id=(String)sensorDef.getFirstValue("id");
			m.pushNode("sensor");
			m.addNode("id", id);
			SensorState ss=getSensorState(id,false);
			ss.serializeToHDS(m,now);
			m.popNode();
		}
		m.declareKey("byId", "/sensors/sensor", "id");
		return m.toDocument(false);
	}
	
	
	
	public void onUpdate(IHDSReader aState,INKFRequestContext aContext) throws Exception
	{
		boolean result=false;
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		//IHDSReader currentState=aContext.source("active:polestarSensorState",IHDSDocument.class).getReader();
		long now=System.currentTimeMillis();
		
		for (IHDSReader sensorStateNode : aState.getNodes("/sensors/sensor"))
		{	String sensorId=(String)sensorStateNode.getFirstValue("id");
			String exception=(String)sensorStateNode.getFirstValueOrNull("error");
			boolean setException=sensorStateNode.getFirstNodeOrNull("error")!=null;
			Object newValue=sensorStateNode.getFirstValueOrNull("value");
			Long updateTime=(Long)sensorStateNode.getFirstValueOrNull("time");
			Long updateWindow=(Long)sensorStateNode.getFirstValueOrNull("window");
			
			IHDSReader sensorDef=config.getFirstNodeOrNull(String.format("key('byId','"+sensorId+"')"));
			if (sensorDef==null)
			{	throw new NKFException("Sensor not found",sensorId);
			}
			
			if (updateTime==null)
			{	updateTime=now;
			}
			
			SensorState ss=getSensorState(sensorId,true);
			if (ss.getLastModified()<updateTime)
			{		
				if (setException)
				{	ss.setUserError(exception, updateTime);
				}
				ss.setValue(newValue, updateTime, sensorDef,aContext);
			}
			
			//if (updateWindow!=null)
			//{	System.out.println("here"+(ss.getLastModified()==updateTime));	
			//}

			
			
			DBCollection col=MongoUtils.getCollectionForSensor(sensorId);
			if (updateWindow!=null)
			{
				Long existingTime=findExistingSensorValueAtTime(updateTime,updateWindow,col);
				//System.out.println("setting "+sensorId+" to "+newValue+" at "+new Date(updateTime).toString()+" existing="+existingTime);
				if (existingTime==null)
				{	storeSensorState(sensorId,newValue,updateTime,col,aContext);
				}
				else
				{	replaceSensorState(sensorId,newValue,existingTime,col,aContext);
					result=true;
				}
			}
			else
			{	storeSensorState(sensorId,newValue,updateTime,col,aContext);
			}
				
			if (ss.getLastModified()==updateTime)
			{	mChanges.put(sensorId, sensorId);
			}
				
			if (ss.getErrorLastModified()==updateTime)
			{	mChanges.put(SENSOR_ERROR, SENSOR_ERROR);
				recordError(sensorId,updateTime,aContext);
			}
		}
		aContext.createResponseFrom(result).setExpiry(INKFResponse.EXPIRY_ALWAYS);
		
	}
	
	private void recordError(String aSensorId, long aNow, INKFRequestContext aContext)
	{	try
		{	BasicDBObject sensor=new BasicDBObject();
			sensor.append("t", aNow);
			sensor.append("i", aSensorId);
			SensorState ss=mSensorStates.get(aSensorId);
			String error=ss.getError();
			int level;
			if (error==null || error.length()==0)
			{	level=0;
				error=null;
			}
			else
			{	level=3;
			}
			sensor.append("l", level);
			sensor.append("m", error);
			DBCollection col=MongoUtils.getCollection("errors");
			WriteResult wr=col.insert(sensor);
		}
		catch (Exception e)
		{	MonitorUtils.log(aContext,null,INKFLocale.LEVEL_SEVERE, Utils.throwableToString(e));
		}
	}

	
	
	public static void storeSensorState(String aId, Object aValue, long aNow, DBCollection aCol, INKFRequestContext aContext) throws Exception
	{
		if (aValue!=null)
		{	Class c=aValue.getClass();
			if (c==String.class||c==Boolean.class||c==Integer.class||c==Long.class||c==Float.class||c==Double.class||Map.class.isAssignableFrom(c))
			{	
				BasicDBObject sensor=new BasicDBObject();
				sensor.append("t", aNow);
				sensor.append("v", aValue);
				//DBCollection col=MongoUtils.getCollectionForSensor(aId);
				WriteResult wr=aCol.insert(sensor);
			}
			else
			{	String msg=String.format("Unsupported datatype for %s of %s",aId,c.getName());
				MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, msg);
			}
		}
	}
	
	public static void replaceSensorState(String aId, Object aValue, long aTime, DBCollection aCol, INKFRequestContext aContext) throws Exception
	{
		if (aValue!=null)
		{	Class c=aValue.getClass();
			if (c==String.class||c==Boolean.class||c==Integer.class||c==Long.class||c==Float.class||c==Double.class||Map.class.isAssignableFrom(c))
			{	
				BasicDBObject sensor=new BasicDBObject();
				sensor.append("t", aTime);
				sensor.append("v", aValue);
				
				//$eq doesn't work on earlier versions
				//BasicDBObject query=new BasicDBObject("t", new BasicDBObject("$eq",aTime));
				
				BasicDBList inO=new BasicDBList();
				inO.add(aTime);
				BasicDBObject query=new BasicDBObject("t", new BasicDBObject("$in",inO));
				
				//DBCollection col=MongoUtils.getCollectionForSensor(aId);
				WriteResult wr=aCol.update(query, sensor);
			}
			else
			{	String msg=String.format("Unsupported datatype for %s of %s",aId,c.getName());
				MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, msg);
			}
		}
	}
	
	public static Long findExistingSensorValueAtTime(Long aTime, Long aWindow, DBCollection aCol) throws Exception
	{
		long start=aTime-aWindow;
		long end=aTime+aWindow;
		BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",start));
		BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",end));
		BasicDBList listO=new BasicDBList();
		listO.add(startO);
		listO.add(endO);
		BasicDBObject queryO=new BasicDBObject("$and", listO);
		DBCursor cursor = aCol.find(queryO);
		Long existingTime=null;
		if (cursor.hasNext())
		{	DBObject capture=cursor.next();
			if (cursor.hasNext())
			{	throw new NKFException("Multiple sensor values within window");
			}
			existingTime=(Long)capture.get("t");		
		}	
		return existingTime;
	}
	
	
	void updateSensorState(String aId, Object aValue, long aNow, String aException, INKFRequestContext aContext)
	{
		SensorState ss=getSensorState(aId,true);
		ss.setValue(aValue, aNow, null, aContext);
		ss.setUserError(aException, aNow);

	}
	
	private SensorState getSensorState(String aId, boolean aClone)
	{	SensorState result=mSensorStates.get(aId);
		if (result==null)
		{	if (aClone)
			{	result = new SensorState();
				mSensorStates.put(aId, result);
			}
			else
			{	result=DEFAULT_SENSOR_STATE;
			}
		}
		return result;
	}
}
