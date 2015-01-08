package io.polestar;

import java.util.*;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;

public class ScriptUtils
{
	private static final Map<String, Object> sBooleanChangeDetectDefaults = new LinkedHashMap<String, Object>();
	private static final Map<String, Object> sAnalogueLevelDetectDefaults = new LinkedHashMap<String, Object>();
	private static final Map<String, Object> sAtMostEveryDefaults = new LinkedHashMap<String, Object>();
	static
	{
		sBooleanChangeDetectDefaults.put("context", null);
		sBooleanChangeDetectDefaults.put("value", null);
		sBooleanChangeDetectDefaults.put("sensorId", "");
		sBooleanChangeDetectDefaults.put("statePath", "value");
		sBooleanChangeDetectDefaults.put("trueHysteresis", 0L);
		sBooleanChangeDetectDefaults.put("falseHysteresis", 0L);
		
		sAnalogueLevelDetectDefaults.put("context", null);
		sAnalogueLevelDetectDefaults.put("value", null);
		sAnalogueLevelDetectDefaults.put("sensorId", "");
		sAnalogueLevelDetectDefaults.put("statePath", "value");
		sAnalogueLevelDetectDefaults.put("trueThreshold", 0.0);
		sAnalogueLevelDetectDefaults.put("falseThreshold", 0.0);

		sAtMostEveryDefaults.put("context", null);
		sAtMostEveryDefaults.put("period", null);
		sAtMostEveryDefaults.put("statePath", "value");
		sAtMostEveryDefaults.put("requireQuiet", false);
	}
	
	
	public static boolean atMostEvery(Map aParams) throws NKFException
	{	Map params=validateNamedParams(aParams,sAtMostEveryDefaults,"atMostEvery");
		boolean result=false;
		
		long period=(Long)params.get("period");
		Object statePath=params.get("statePath");
		INKFRequestContext context=(INKFRequestContext)params.get("context");
		Boolean requireQuiet=(Boolean)params.get("requireQuiet");
		
		IHDSDocument stateDoc=context.source("arg:state",IHDSDocument.class);
		IHDSReader state=stateDoc.getReader();
		Long lastValue=(Long)state.getFirstValueOrNull("/state/"+statePath);
		long now=System.currentTimeMillis();
		if (lastValue!=null)
		{	long diff=now-lastValue;
			if (diff>period)
			{	result=true;
			}
		}
		if (requireQuiet || result)
		{	IHDSMutator m=stateDoc.getMutableClone();
			m.resetCursor().createIfNotExists("state/"+statePath).setValue(now);
			context.sink("arg:state",m.toDocument(false));
		}
		return result;
	}
	
	
	public static boolean analogueLevelChangeDetect(Map aParams) throws NKFException
	{	Map params=validateNamedParams(aParams,sAnalogueLevelDetectDefaults,"analogueLevelChangeDetect");
		boolean result=false;
		
		double value=(Double)params.get("value");
		Object statePath=params.get("statePath");
		INKFRequestContext context=(INKFRequestContext)params.get("context");
		double trueThreshold=(Double)params.get("trueThreshold");
		double falseThreshold=(Double)params.get("falseThreshold");
		boolean invert=trueThreshold<falseThreshold; 
		String sensorId=(String)params.get("sensorId");
		IHDSDocument stateDoc=context.source("arg:state",IHDSDocument.class);
		IHDSReader state=stateDoc.getReader();
		Boolean lastValue=(Boolean)state.getFirstValueOrNull("/state/"+statePath);
		
		boolean update=false;
		boolean triggeredTrue=invert?(value<trueThreshold):(value>trueThreshold);
		boolean triggeredFalse=invert?(value>falseThreshold):(value<falseThreshold);
		if (lastValue!=null)
		{	if ( (triggeredTrue && !lastValue) || (triggeredFalse && lastValue) )
			{	//change occurred
				result=true;
				update=true;
			}
		}
		else
		{	update=true;
		}
		
		if (update)
		{
			IHDSMutator m=stateDoc.getMutableClone();
			m.resetCursor().createIfNotExists("state/"+statePath).setValue(triggeredTrue);
			context.sink("arg:state",m.toDocument(false));

			//update sensor if specified
			if (sensorId.length()>0)
			{
				m=HDSFactory.newDocument();
				m.pushNode("sensors");
				m.pushNode("sensor");
				m.addNode("id",sensorId);
				m.addNode("value", triggeredTrue);
				m.popNode();
				INKFRequest req=context.createRequest("active:polestarSensorUpdate");
				req.addArgumentByValue("state",m.toDocument(false));
				context.issueRequest(req);
			}
		}
			
		return result;
	}
	
	
	public static boolean booleanChangeDetect(Map aParams) throws NKFException
	{	Map params=validateNamedParams(aParams,sBooleanChangeDetectDefaults,"booleanChangeDetect");

		boolean result=false;
		Object value=params.get("value");
		Object statePath=params.get("statePath");
		INKFRequestContext context=(INKFRequestContext)params.get("context");
		Long trueHysteresis=(Long)params.get("trueHysteresis");
		Long falseHysteresis=(Long)params.get("falseHysteresis");
		String sensorId=(String)params.get("sensorId");
		IHDSDocument stateDoc=context.source("arg:state",IHDSDocument.class);
		IHDSReader state=stateDoc.getReader();
		Object lastValue=state.getFirstValueOrNull("/state/"+statePath);
		if (lastValue==null || !lastValue.equals(value))
		{	//change occurred
			//see if we should ignore for now
			long now=System.currentTimeMillis();
			boolean hysteresis=false;
			if (Boolean.TRUE.equals(value) && trueHysteresis>0L)
			{	Long lastChangeTime=(Long)state.getFirstValueOrNull("/state/"+statePath+"/lastModified");
				//System.out.println("trueHysteresis "+(now-lastChangeTime)+" "+params.trueHysteresis);
				if (lastChangeTime!=null && (now-lastChangeTime)<trueHysteresis) hysteresis=true;
			}
			if (Boolean.FALSE.equals(value) && falseHysteresis>0L)
			{	Long lastChangeTime=(Long)state.getFirstValueOrNull("/state/"+statePath+"/lastModified");
				if (lastChangeTime!=null && (now-lastChangeTime)<falseHysteresis) hysteresis=true;
			}
			if (!hysteresis)
			{	result=true;
		
				//update state
				IHDSMutator m=stateDoc.getMutableClone();
				m.resetCursor().createIfNotExists("state/"+statePath).setValue(value);
				m.resetCursor().createIfNotExists("state/"+statePath+"/lastModified").setValue(now);
				context.sink("arg:state",m.toDocument(false));

				//update sensor if specified
				if (sensorId.length()>0)
				{
					m=HDSFactory.newDocument();
					m.pushNode("sensors");
					m.pushNode("sensor");
					m.addNode("id",sensorId);
					m.addNode("value", value);
					m.popNode();
					INKFRequest req=context.createRequest("active:polestarSensorUpdate");
					req.addArgumentByValue("state",m.toDocument(false));
					context.issueRequest(req);
				}
			}
		}
		return result;
	}

	private static Map validateNamedParams(Map<String,Object> aParams, Map<String,Object> aDefaults, String aMethodName)
	{	Map result=new LinkedHashMap();
		for (String key : aParams.keySet())
		{	if (!aDefaults.containsKey(key))
			{	throw new IllegalArgumentException("unexpected "+key+" parameter for "+aMethodName);
			}
		}
		for (Map.Entry<String,Object> entry : aDefaults.entrySet())
		{	String key=entry.getKey();
			Object defaultValue=entry.getValue();
			if (aParams.containsKey(key))
			{	result.put(key,aParams.get(key));
			}
			else
			{	if (defaultValue==null)
				{	throw new IllegalArgumentException("mandatory "+key+" parameter missing for "+aMethodName);
				}
				result.put(key,defaultValue);
			}
		}
		return result;
	}
	
}
