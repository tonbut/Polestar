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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.layer0.urii.ParsedIdentifierImpl;
import org.netkernel.layer0.util.GoldenThreadExpiryFunction;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import org.netkernel.util.Utils;

import io.polestar.api.IPolestarContext;
import io.polestar.api.IPolestarQuery;
import io.polestar.api.QueryType;
import io.polestar.data.api.PolestarContext;
import io.polestar.data.db.PersistenceFactory;
import io.polestar.data.util.MonitorUtils;

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
	
		IHDSDocument sensorState=PersistenceFactory.getPersistence(aContext).getCurrentSensorState(aContext);
		if (sensorState!=null)
		{
			for (IHDSReader sensor : sensorState.getReader().getNodes("/sensors/sensor"))
			{	String id=(String)sensor.getFirstValue("id");
				try
				{	SensorState ss=new SensorState(sensor);
					mSensorStates.put(id, ss);
				} catch (Exception e) {
					aContext.logRaw(INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
				}
			}
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
	{	IHDSDocument state=getState(aContext);
		PersistenceFactory.getPersistence(aContext).setCurrentSensorState(state, aContext);
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
		else if (action.equals("polestarSensorRegenerate"))
		{	onSensorStateRegenerate(aContext);
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
	{	String toDelete=aContext.source("arg:state",String.class);
		PersistenceFactory.getPersistence(aContext).deleteSensor(toDelete, aContext);
	}
	
	public void onSensorInfo(INKFRequestContext aContext) throws Exception
	{
		IHDSDocument sensorInfo=PersistenceFactory.getPersistence(aContext).getSensorInfo(aContext);
		IHDSReader r=sensorInfo.getReader();
		r.declareKey("byId", "/sensors/sensor", "id");
		INKFResponse resp=aContext.createResponseFrom(r.toDocument());
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
	
	public void onSensorStateRegenerate(INKFRequestContext aContext) throws Exception
	{
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		IPolestarContext pctx=PolestarContext.createContext(aContext,null);
		for (IHDSReader sensorDef : config.getNodes("/sensors/sensor"))
		{
			String id=(String)sensorDef.getFirstValue("id");
			SensorState ss=getSensorState(id,false);
			if (ss==DEFAULT_SENSOR_STATE || ss.getLastUpdated()==0L)
			{
				aContext.logRaw(INKFLocale.LEVEL_INFO, "Regenerating sensor state"+id);
				try
				{
					IPolestarQuery query=pctx.createQuery(id, QueryType.LAST_MODIFIED).setStart(0);
					Long lastModified=(Long)query.execute();
					if (lastModified!=null && lastModified>0)
					{
						Object value=pctx.createQuery(id, QueryType.LAST_VALUE).setStart(0).execute();
						
						IHDSMutator m=HDSFactory.newDocument();
						m.addNode("value",value);
						m.addNode("userError", null);
						m.addNode("valueError", null);
						m.addNode("staleError", null);
						m.addNode("error", null);
						m.addNode("lastModified", lastModified);
						m.addNode("lastUpdated", lastModified);
						ss=new SensorState(m.toDocument(false).getReader());
						mSensorStates.put(id,ss);
					}
				}
				catch (Exception e)
				{	String msg=String.format("Error regenerating sensor state for %s\n%s", id, Utils.throwableToString(e) );
					aContext.logRaw(INKFLocale.LEVEL_WARNING, msg);
				}
			}
		}
		onChanges(aContext);
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
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		long now=System.currentTimeMillis();
		boolean wasReplaced=false;
		
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
			
			if (ss.getLastModified()==updateTime)
			{	mChanges.put(sensorId, sensorId);
			}
				
			if (ss.getErrorLastModified()==updateTime)
			{	mChanges.put(SENSOR_ERROR, SENSOR_ERROR);
				recordError(sensorId,updateTime,aContext);
			}

			wasReplaced = PersistenceFactory.getPersistence(aContext).setSensorValue(sensorId,newValue,updateTime,updateWindow,aContext);
			
		}
		INKFResponse resp=aContext.createResponseFrom(wasReplaced);
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	private void recordError(String aSensorId, long aNow, INKFRequestContext aContext)
	{	
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
		try
		{	PersistenceFactory.getPersistence(aContext).setSensorError(aSensorId,error,level,aNow,aContext);
		}
		catch (Exception e)
		{	try
			{	MonitorUtils.log(aContext,null,INKFLocale.LEVEL_SEVERE, Utils.throwableToString(e));
			}
			catch (Exception e2) {;}
		}
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
