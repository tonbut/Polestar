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

import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

public class SensorListAccessor extends StandardAccessorImpl
{
	private final Map<String,SensorState> mSensorStates=new ConcurrentHashMap<String, SensorListAccessor.SensorState>();
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
	{	aContext.logRaw(INKFLocale.LEVEL_INFO,"## Polestar Started ##");
	
		BasicDBObject query = new BasicDBObject("id",0);
		DBCollection col=MongoUtils.getCollection("sensorState");
		DBCursor cursor = col.find(query);
		try
		{	if(cursor.hasNext())
			{	DBObject dbo=cursor.next();	
				byte[] hds=(byte[])dbo.get("hds");
				IHDSDocument state=aContext.transrept(new ByteArrayRepresentation(hds), IHDSDocument.class);
				setState(state,aContext);
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
						aContext.logRaw(INKFLocale.LEVEL_INFO, "Creating time index for "+collection);
						col.createIndex(new BasicDBObject("t", 1));
					}
				}
			}
			catch (Exception e)
			{	aContext.logRaw(INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
			}
		}
		
		
		//run startup scripts
		MonitorUtils.executeTriggeredScripts(Collections.singleton("startup"), true, aContext);
	}
	
	public void preDecommission(INKFRequestContext aContext) throws Exception
	{	
		//run shutdown scripts
		MonitorUtils.executeTriggeredScripts(Collections.singleton("shutdown"), true, aContext);		
		aContext.logRaw(INKFLocale.LEVEL_INFO,"Monitor Stopped");
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
				
				String id=collection.substring(7);
				
				DateFormat df=DateFormat.getDateInstance(DateFormat.SHORT);
				String firstString=first>0?df.format(new Date(first)):"none";
				String lastString=first>0?df.format(new Date(last)):"none";
				long avgSize=count>0?size/count:0L;
				m.pushNode("sensor")
				.addNode("id",id)
				.addNode("count", count)
				.addNode("size", size)
				.addNode("avgSize", avgSize)
				.addNode("first", firstString)
				.addNode("last", lastString)
				.popNode();

				//System.out.println(collection+" "+count+" "+size+" "+size/count);
			}
		}
		INKFResponse resp=aContext.createResponseFrom(m.toDocument(false));
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	//check to see if any sensors are not updating
	public void onReadingCheck(INKFRequestContext aContext) throws Exception
	{	long now=System.currentTimeMillis();
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		for (IHDSReader sensorDef : config.getNodes("/sensors/sensor"))
		{	String id=(String)sensorDef.getFirstValue("id");
			Long errorIfNoReadingsFor=(Long)sensorDef.getFirstValueOrNull("errorIfNoReadingsFor");
			if (errorIfNoReadingsFor!=null)
			{	SensorState ss=getSensorState(id);
				long lastUpdated=ss.getLastUpdated();
				if (now-lastUpdated>=errorIfNoReadingsFor*1000L && ss.getError()==null)
				{	String error="No fresh readings";
					updateSensorState(id, ss.getValue(), now, error);
				}
			}
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
	{	IHDSMutator config=aContext.source("res:/md/execute/named/SensorList",IHDSDocument.class).getMutableClone();
		IHDSReader scripts=aContext.source("active:polestarListScripts",IHDSDocument.class).getReader();
		//System.out.println(scripts);
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
			{	//System.out.println(targetScript.getFirstValue("name")+" "+sensorId);
				String scriptId=(String)targetScript.getFirstValue("id");
				sensorNode.addNode("script", scriptId);
			}
			//System.out.println(targetScript+" "+sensorId+" "+xpath);
		}
		config.declareKey("byId", "/sensors/sensor", "id");
		//System.out.println(config);
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
			SensorState ss=getSensorState(id);
			long lastUpdated=ss.getLastUpdated();
			if (ss.hasError())
			{	m.addNode("error", ss.getError());
			}
			
			m.addNode("lastUpdated", lastUpdated);
			m.addNode("lastModified", ss.getLastModified());
			m.addNode("value", ss.getValue());		
			m.popNode();
		}
		m.declareKey("byId", "/sensors/sensor", "id");
		return m.toDocument(false);
	}
	
	private void setState(IHDSDocument aState, INKFRequestContext aContext) throws Exception
	{	for (IHDSReader sensor : aState.getReader().getNodes("/sensors/sensor"))
		{	String id=(String)sensor.getFirstValue("id");
			Object value=sensor.getFirstValue("value");
			String error=(String)sensor.getFirstValueOrNull("error");
			Long lastUpdated=(Long)sensor.getFirstValue("lastUpdated");
			Long lastModified=(Long)sensor.getFirstValue("lastModified");
			SensorState ss=new SensorState(value, lastUpdated, lastModified, error);
			mSensorStates.put(id, ss);
		}
	}
	
	public void onUpdate(IHDSReader aState,INKFRequestContext aContext) throws Exception
	{
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		IHDSReader currentState=aContext.source("active:polestarSensorState",IHDSDocument.class).getReader();
		long now=System.currentTimeMillis();
		
		for (IHDSReader sensorStateNode : aState.getNodes("/sensors/sensor"))
		{	String sensorId=(String)sensorStateNode.getFirstValue("id");
			String exception=(String)sensorStateNode.getFirstValueOrNull("error");
			Object newValue=sensorStateNode.getFirstValueOrNull("value");
			
			IHDSReader sensorDef=config.getFirstNodeOrNull(String.format("key('byId','"+sensorId+"')"));
			if (sensorDef==null)
			{	throw new NKFException("Sensor not found",sensorId);
			}
			
			if (exception==null && newValue!=null)
			{	//now test value if necessary
				Number errorIfGreaterThan=(Number)sensorDef.getFirstValueOrNull("errorIfGreaterThan");
				if (errorIfGreaterThan!=null && newValue instanceof Number)
				{	Number nn=(Number)newValue;
				
					boolean isError=false;
					String errorText="Value has exceeded "+errorIfGreaterThan;
					Number errorClearIfLessThan=(Number)sensorDef.getFirstValueOrNull("errorClearIfLessThan");
					if (errorClearIfLessThan!=null)
					{	//hysteresis
						String currentError=(String)currentState.getFirstValueOrNull("key('byId','"+sensorId+"')/error");
						if (currentError!=null && currentError.equals(errorText))
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
					{	exception=errorText;
					}
				}
				Number errorIfLessThan=(Number)sensorDef.getFirstValueOrNull("errorIfLessThan");
				if (errorIfLessThan!=null && newValue instanceof Number)
				{	Number nn=(Number)newValue;
					boolean isError=false;
					String errorText="Value has fallen below "+errorIfLessThan;
				
					Number errorClearIfGreaterThan=(Number)sensorDef.getFirstValueOrNull("errorClearIfGreaterThan");
					if (errorClearIfGreaterThan!=null)
					{	//hysteresis
						String currentError=(String)currentState.getFirstValueOrNull("key('byId','"+sensorId+"')/error");
						if (currentError!=null && currentError.equals(errorText))
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
					{	exception=errorText;
					}
				}
				
				Object errorIfEquals=sensorDef.getFirstValueOrNull("errorIfEquals");
				if (errorIfEquals!=null && errorIfEquals instanceof Number && newValue instanceof Number)
				{	Number nn=(Number)newValue;
					if (nn.doubleValue()==((Number)errorIfEquals).doubleValue())
					{	exception="Value equals "+errorIfEquals;
					}
				}
				if (errorIfEquals!=null && errorIfEquals instanceof Boolean && newValue instanceof Boolean)
				{	Boolean nn=(Boolean)newValue;
					if (nn.equals(errorIfEquals))
					{	exception="Value equals "+errorIfEquals;
					}
				}
				
				Object errorIfNotEquals=sensorDef.getFirstValueOrNull("errorIfNotEquals");
				if (errorIfNotEquals!=null && errorIfNotEquals instanceof Number && newValue instanceof Number)
				{	Number nn=(Number)newValue;
					if (nn.doubleValue()!=((Number)errorIfNotEquals).doubleValue())
					{	exception="Value doesn't equal "+errorIfNotEquals;
					}
				}
				if (errorIfNotEquals!=null && errorIfNotEquals instanceof Boolean && newValue instanceof Boolean)
				{	Boolean nn=(Boolean)newValue;
					if (nn.equals(errorIfNotEquals))
					{	exception="Value doesn't equal "+errorIfNotEquals;
					}
				}
			}
			
			boolean valueChanged=updateSensorState(sensorId,newValue,now,exception);
			if (valueChanged)
			{	DBCollection col=MongoUtils.getCollectionForSensor(sensorId);
				storeSensorState(sensorId,newValue,now,col,aContext);
			}
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
				aContext.logRaw(INKFLocale.LEVEL_WARNING, msg);
			}
		}
	}
	
	boolean updateSensorState(String aId, Object aValue, long aNow, String aException)
	{	SensorState existing=mSensorStates.get(aId);
		boolean valueChanged;
		if (aException==null)
		{	
			if (aNow>existing.getLastModified() || existing.getError()!=null)
			{
				Object oldValue=null;
				if (existing!=null)
				{	oldValue=existing.getValue();
				}
				
				valueChanged=(aValue==null && oldValue!=null)
						|| (oldValue==null && aValue!=null)
						|| (oldValue!=null && aValue!=null && !oldValue.equals(aValue));
				
				long lastModified=valueChanged?aNow:existing.getLastModified();
				SensorState ss=new SensorState(aValue, aNow, lastModified, null);
				mSensorStates.put(aId, ss);
				if (valueChanged && existing!=null)
				{	mChanges.put(aId,aId);
				}
				
				if (existing!=null && existing.hasError())
				{	//error is now cleared on sensor
					mChanges.put(SENSOR_ERROR, SENSOR_ERROR);
				}
			}
			else
			{	valueChanged=false;
			}
		}
		else
		{	
			Object oldValue=null;
			if (existing!=null)
			{	oldValue=existing.getError();
			}
			valueChanged=(aException==null && oldValue!=null)
					|| (oldValue==null && aException!=null)
					|| (oldValue!=null && aException!=null && !oldValue.equals(aException));
			
			long lastModified=valueChanged?aNow:existing.getLastModified();
			SensorState ss=new SensorState(aValue, aNow, lastModified, aException);
			mSensorStates.put(aId, ss);
			if (valueChanged && existing!=null)
			{	mChanges.put(aId,aId);
			}
			
			boolean newError=(oldValue==null);
			if (newError)
			{	mChanges.put(SENSOR_ERROR, SENSOR_ERROR);
			}
		}
		return valueChanged;
	}
	
	
	private SensorState getSensorState(String aId)
	{	SensorState result=mSensorStates.get(aId);
		if (result==null)
		{	result=DEFAULT_SENSOR_STATE;
		}
		return result;
	}
	
	static class SensorState
	{
		private final Object mValue;
		private final long mLastUpdated;
		private final long mLastModified;
		private final String mError;
		
		public SensorState()
		{	mValue=null;
			mLastUpdated=0;
			mLastModified=0;
			mError="No value received";
		}
		
		public SensorState(Object aValue, long aLastUpdated, long aLastModified, String aError)
		{	mValue=aValue;
			mLastUpdated=aLastUpdated;
			mLastModified=aLastModified;
			mError=aError;
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
		public boolean hasError()
		{	return mError!=null;
		}
		public String getError()
		{	return mError;
		}
	}
}
