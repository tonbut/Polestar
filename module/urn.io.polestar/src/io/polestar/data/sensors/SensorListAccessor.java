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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

import com.mongodb.BasicDBObject;
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
	
	public SensorListAccessor()
	{	this.declareThreadSafe();
		DEFAULT_SENSOR_STATE=new SensorState();
		mStateUpdateExpiry=new GoldenThreadExpiryFunction("SensorListAccessor");
	}
	
	public void postCommission(INKFRequestContext aContext) throws Exception
	{	aContext.logRaw(INKFLocale.LEVEL_INFO,"Monitor Started");
	
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
					updateSensorState(id, sensorDef, ss.getValue(), error);
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
		for (IHDSMutator sensorNode : config.getNodes("/sensors/sensor"))
		{	String icon=(String)sensorNode.getFirstValueOrNull("icon");
			if (icon==null)
			{	icon="/polestar/pub/icon/circle-dashed.png";
				sensorNode.createIfNotExists("icon").setValue(icon).resetCursor();
			}
		}
		config.declareKey("byId", "/sensors/sensor", "id");
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
					if (nn.doubleValue()>errorIfGreaterThan.doubleValue())
					{	exception="Value exceeds "+errorIfGreaterThan;
					}
				}
				Number errorIfLessThan=(Number)sensorDef.getFirstValueOrNull("errorIfLessThan");
				if (errorIfLessThan!=null && newValue instanceof Number)
				{	Number nn=(Number)newValue;
					if (nn.doubleValue()<errorIfLessThan.doubleValue())
					{	exception="Value below "+errorIfLessThan;
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
			
			updateSensorState(sensorId,sensorDef,newValue,exception);
		}
	}
	
	private void updateSensorState(String aId, IHDSReader aSensorDef, Object aValue, String aException)
	{	SensorState existing=mSensorStates.get(aId);
		long now=System.currentTimeMillis();
		if (aException==null)
		{	Object oldValue=null;
			if (existing!=null)
			{	oldValue=existing.getValue();
			}
			
			boolean valueChanged=(aValue==null && oldValue!=null)
					|| (oldValue==null && aValue!=null)
					|| (oldValue!=null && aValue!=null && !oldValue.equals(aValue));
			
			long lastModified=valueChanged?now:existing.getLastModified();
			SensorState ss=new SensorState(aValue, now, lastModified, null);
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
		{	
			Object oldValue=null;
			if (existing!=null)
			{	oldValue=existing.getError();
			}
			boolean valueChanged=(aException==null && oldValue!=null)
					|| (oldValue==null && aException!=null)
					|| (oldValue!=null && aException!=null && !oldValue.equals(aException));
			
			long lastModified=valueChanged?now:existing.getLastModified();
			SensorState ss=new SensorState(aValue, now, lastModified, aException);
			mSensorStates.put(aId, ss);
			if (valueChanged && existing!=null)
			{	mChanges.put(aId,aId);
			}
			
			boolean newError=(oldValue==null);
			if (newError)
			{	mChanges.put(SENSOR_ERROR, SENSOR_ERROR);
			}
		}
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
