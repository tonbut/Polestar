package io.polestar.data.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.layer0.nkf.impl.NKFAsyncRequestHandleImpl;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;

import io.polestar.api.IPolestarAPI;
import io.polestar.api.IPolestarContext;
import io.polestar.api.IPolestarQuery;
import io.polestar.api.QueryType;
import io.polestar.data.util.MonitorUtils;
import io.polestar.view.sensors.SensorViewAccessor;

public class PolestarContext implements InvocationHandler, IPolestarAPI
{
	private static Constructor sProxyConstructor;
	static
	{	Class[] classes=new Class[]{IPolestarContext.class};
		Class proxyClass = Proxy.getProxyClass(PolestarContext.class.getClassLoader(), classes);
		try
		{	sProxyConstructor = proxyClass.getConstructor(new Class[] { InvocationHandler.class });
		}
		catch (Exception e)
		{	e.printStackTrace();
		}
	}
	
	private final INKFRequestContext mContext;
	private final String mName;
	
	private PolestarContext(INKFRequestContext aContext, String aName)
	{	mContext=aContext;
		mName=aName;
	}
	
	public static IPolestarContext createContext(INKFRequestContext aContext, String aName) throws Exception
	{
		PolestarContext pc=new PolestarContext(aContext,aName);
		IPolestarContext proxy = (IPolestarContext)sProxyConstructor.newInstance(new Object[] {pc} );
		return proxy;
	}
	
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
	{	Class clz=method.getDeclaringClass();
		if (clz.isAssignableFrom(mContext.getClass()))
		{	
			if (method.getName().equals("logRaw"))
			{	//redirect to utility method
				Integer level=(Integer)args[0];
				String message=(String)args[1];
				MonitorUtils.log(mContext, mName, level, message);
				return null;
			}
			else
			{	return method.invoke(mContext, args);
			}
		}
		else
		{	return method.invoke(this, args);
		}
	}
	
	private IHDSReader mSensorState;
	private IHDSReader mScriptState;
	private IHDSReader mSensorConfig;
	
	private IHDSReader getSensorState() throws NKFException
	{	if (mSensorState==null)
		{	mSensorState=mContext.source("active:polestarSensorState",IHDSDocument.class).getReader();
		}
		return mSensorState;
	}
	
	private IHDSReader getSensorDefinitions() throws NKFException
	{	if (mSensorConfig==null)
		{	mSensorConfig=mContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		}
		return mSensorConfig;
	}
	
	private IHDSReader getSensorDefinition(String aSensor) throws NKFException
	{	IHDSReader sensor=getSensorDefinitions().getFirstNodeOrNull("key('byId','"+aSensor+"')");
		if (sensor==null)
		{	throw new NKFException("Unknown Sensor","sensor ["+aSensor+"] does not exist");
		}
		return sensor;
	}
	
	public boolean sensorExists(String aSensorId) throws NKFException
	{	IHDSReader sensor=getSensorState().getFirstNodeOrNull("key('byId','"+aSensorId+"')");
		return sensor!=null;
	}
	
	private IHDSReader getSensorState(String aSensor) throws NKFException
	{	IHDSReader sensor=getSensorState().getFirstNodeOrNull("key('byId','"+aSensor+"')");
		if (sensor==null)
		{	throw new NKFException("Unknown Sensor","sensor ["+aSensor+"] does not exist");
		}
		return sensor;
	}
	

	@Override
	public Object getSensorValue(String aSensorId) throws NKFException
	{	IHDSReader sensorState=getSensorState(aSensorId);
		return sensorState.getFirstValue("value");
	}
	
	@Override
	public String formatSensorValue(String aSensorId) throws NKFException
	{	Object value=getSensorValue(aSensorId);
		IHDSReader sensorDef=getSensorDefinition(aSensorId);
		String format=(String)sensorDef.getFirstValueOrNull("format");
		String s=SensorViewAccessor.getHumanReadable(value, format)[0];
		String units=(String)sensorDef.getFirstValueOrNull("units");
		if (units!=null && units.length()>0)
		{	s=s+" "+units;
		}
		return s;
	}
	

	@Override
	public String getSensorError(String aSensorId) throws NKFException
	{	IHDSReader sensorState=getSensorState(aSensorId);
		return (String)sensorState.getFirstValueOrNull("error");
	}
	
	@Override
	public Long getSensorLastModified(String aSensorId) throws NKFException
	{	IHDSReader sensorState=getSensorState(aSensorId);
		return (Long)sensorState.getFirstValue("lastModified");
	}
	
	@Override
	public Long getSensorLastUpdated(String aSensorId) throws NKFException
	{	IHDSReader sensorState=getSensorState(aSensorId);
		return (Long)sensorState.getFirstValue("lastUpdated");
	}

	@Override
	public void setSensorValue(String aSensorId, Object aValue) throws NKFException
	{	IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		m.pushNode("sensor");
		m.addNode("id",aSensorId);
		m.addNode("value", aValue);
		m.popNode();
		INKFRequest req=mContext.createRequest("active:polestarSensorUpdate");
		req.addArgumentByValue("state",m.toDocument(false));
		mContext.issueRequest(req);
	}
	
	@Override
	public void setSensorValueAtTime(String aSensorId, Object aValue, long aTime) throws NKFException
	{	IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		m.pushNode("sensor");
		m.addNode("id",aSensorId);
		m.addNode("time",aTime);
		m.addNode("value", aValue);
		m.popNode();
		INKFRequest req=mContext.createRequest("active:polestarSensorUpdate");
		req.addArgumentByValue("state",m.toDocument(false));
		mContext.issueRequest(req);
	}
	
	@Override
	public boolean updateSensorValueAtTime(String aSensorId, Object aValue, long aTime, long aWindow) throws NKFException
	{
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		m.pushNode("sensor");
		m.addNode("id",aSensorId);
		m.addNode("time",aTime);
		m.addNode("value", aValue);
		m.addNode("window", aWindow);
		m.popNode();
		INKFRequest req=mContext.createRequest("active:polestarSensorUpdate");
		req.addArgumentByValue("state",m.toDocument(false));
		req.setRepresentationClass(Boolean.class);
		return (Boolean)mContext.issueRequest(req);
	}

	@Override
	public void setSensorError(String aSensorId, String aError) throws NKFException
	{	IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		m.pushNode("sensor");
		m.addNode("id",aSensorId);
		m.addNode("error", aError);
		m.popNode();
		INKFRequest req=mContext.createRequest("active:polestarSensorUpdate");
		req.addArgumentByValue("state",m.toDocument(false));
		mContext.issueRequest(req);
	}
	
	@Override
	public Number incrementSensor(String aSensorId) throws NKFException
	{	Number n=(Number)getSensorValue(aSensorId);
		if (n==null)
		{	n=Long.valueOf(0);
		}
		if (n instanceof Long)
		{	Long v=((Long)n)+1;
			setSensorValue(aSensorId,v);
			return v;
		}
		else if (n instanceof Integer)
		{	Long v=((Long)n)+1;
			setSensorValue(aSensorId,v);
			return v;
		}
		else
		{	throw new NKFException("Current value must be Integer or Long to increment");
		}
	}
	
	private IHDSReader getScriptState() throws NKFException
	{	if (mScriptState==null)
		{	mScriptState=mContext.source("arg:state",IHDSDocument.class).getReader();
		}
		return mScriptState;
	}
	
	@Override
	public Object getScriptState(String aStatePath, Object aDefault) throws NKFException
	{	Object value=getScriptState().getFirstValueOrNull("/state/"+aStatePath);
		if (value==null)
		{	value=aDefault;
		}
		return value;
	}

	@Override
	public void setScriptState(String aStatePath, Object aValue) throws NKFException
	{	IHDSMutator m=getScriptState().toDocument().getMutableClone();
		m.resetCursor().createIfNotExists("state/"+aStatePath).setValue(aValue);
		IHDSDocument modifiedDoc=m.toDocument(false);
		mContext.sink("arg:state",modifiedDoc);
		mScriptState=modifiedDoc.getReader();
	}
	
	private void processCallScriptArguments(INKFRequest aRequest, Object... aArgs) throws NKFException
	{	if (aArgs.length%2==1)
		{	throw new NKFException("Expected matching pairs of name-value arguments to callScript");
		}
		for (int i=0; i<aArgs.length; i+=2)
		{
			Object name=aArgs[i];
			if (!(name instanceof String))
			{	throw new NKFException("Expected argument name to be java.lang.String");
			}
			Object value=aArgs[i+1];
			aRequest.addArgumentByValue((String)name, value);
		}
	}

	@Override
	public Object callScriptById(String aId, Object... aArgs) throws NKFException
	{	INKFRequest req=mContext.createRequest("active:polestarExecuteScript");
		req.addArgument("script",aId);
		processCallScriptArguments(req,aArgs);
		return mContext.issueRequest(req);
	}

	@Override
	public Object callScriptByName(String aName, Object... aArgs) throws NKFException
	{	INKFRequest req=mContext.createRequest("active:polestarExecuteScript");
		req.addArgument("name",aName);
		processCallScriptArguments(req,aArgs);
		return mContext.issueRequest(req);
	}

	@Override
	public IPolestarQuery createQuery(String aSensorId, QueryType aType) throws NKFException
	{	
		getSensorState(aSensorId);
		return new PolestarQuery(aSensorId, aType, mContext, this);
	}

	@Override
	public boolean[] analogueChangeDetect(double value, double falseThreshold, double trueThreshold, String aStatePath)
			throws NKFException
	{
		boolean invert=trueThreshold<falseThreshold; 
		Boolean lastValue=(Boolean)getScriptState(aStatePath, Boolean.FALSE);
		boolean update=false;
		boolean triggeredTrue=invert?(value<trueThreshold):(value>trueThreshold);
		boolean triggeredFalse=invert?(value>falseThreshold):(value<falseThreshold);
		if (lastValue!=null)
		{	if ( (triggeredTrue && !lastValue) || (triggeredFalse && lastValue) )
			{	//change occurred
				update=true;
				lastValue=triggeredTrue;
			}
		}
		else
		{	update=true;
		}
		
		if (update)
		{	setScriptState(aStatePath, lastValue);
		}
		return new boolean[] {lastValue,update};
	}

	@Override
	public boolean[] booleanChangeDetect(boolean value, long falseDelay, long trueDelay, String aStatePath) throws NKFException
	{
		boolean result=false;
		Boolean lastValue=(Boolean)getScriptState(aStatePath, null);
		if (lastValue==null || !lastValue.equals(value))
		{	//change occurred
			//see if we should ignore for now
			long now=System.currentTimeMillis();
			boolean hysteresis=false;
			if (Boolean.TRUE.equals(value) && falseDelay>0L)
			{	Long lastChangeTime=(Long)getScriptState(aStatePath+"/lastModified", null);
				if (lastChangeTime!=null && (now-lastChangeTime)<falseDelay) hysteresis=true;
			}
			if (Boolean.FALSE.equals(value) && trueDelay>0L)
			{	Long lastChangeTime=(Long)getScriptState(aStatePath+"/lastModified", null);
				if (lastChangeTime!=null && (now-lastChangeTime)<trueDelay) hysteresis=true;
			}
			if (!hysteresis)
			{	result=true;
				lastValue=value;
				//update state
				setScriptState(aStatePath, value);
				setScriptState(aStatePath+"/lastModified", now);
			}
		}
		return new boolean[] {lastValue,result};
	}

	@Override
	public boolean atMostEvery(long period, boolean requireQuiet, String aStatePath) throws NKFException
	{
		boolean result=false;
		
		Long lastValue=(Long)getScriptState(aStatePath,null);
		long now=System.currentTimeMillis();
		if (lastValue!=null)
		{	long diff=now-lastValue;
			if (diff>period)
			{	result=true;
			}
		}
		else
		{	requireQuiet=true; // force state update on first access
		}
		if (requireQuiet || result)
		{	setScriptState(aStatePath, now);
		}
		return result;
	}
	
}
