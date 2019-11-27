package io.polestar.data.sensors;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFRequestReadOnly;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.urii.ParsedIdentifierImpl;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.data.db.PersistenceFactory;
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
		/*if (action.equals("polestarSensorErrorList"))
		{	onList(id,start,aContext);
		}*/
		if (action.equals("polestarSensorErrorInfo"))
		{	onInfo(id,start,now,aContext);
		}
	}
	
	
	public void onInfo(String id, long start, long now, INKFRequestContext aContext) throws Exception
	{
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		long tt=now-start;
		m.addNode("durationRaw",tt);
		m.addNode("duration", MonitorUtils.formatPeriod(tt));
		
		IHDSMutator sensorErrorInfo=PersistenceFactory.getPersistence(aContext)
				.getSensorErrorSummary(id,start,aContext).getMutableClone();
		sensorErrorInfo.setCursor("/sensor");
		
		long lastErrorRaisedRaw=(Long)sensorErrorInfo.getFirstValue("/sensor/lastErrorRaisedRaw");
		sensorErrorInfo.addNode("lastErrorRaised", MonitorUtils.formatPeriod(now-lastErrorRaisedRaw));
		
		long lastErrorClearedRaw=(Long)sensorErrorInfo.getFirstValue("/sensor/lastErrorClearedRaw");
		sensorErrorInfo.addNode("lastErrorCleared", lastErrorClearedRaw>=0?MonitorUtils.formatPeriod(now-lastErrorClearedRaw):"not cleared");
		
		long lastErrorDurationRaw=(Long)sensorErrorInfo.getFirstValue("/sensor/lastErrorDurationRaw");
		sensorErrorInfo.addNode("lastErrorDuration", MonitorUtils.formatPeriod(lastErrorDurationRaw));
		
		long errorDurationRaw=(Long)sensorErrorInfo.getFirstValue("/sensor/errorDurationRaw");
		sensorErrorInfo.addNode("errorDuration", MonitorUtils.formatPeriod(errorDurationRaw));
		
		
		m.appendChildren(sensorErrorInfo);
		
		/*
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
		*/
		aContext.createResponseFrom(m.toDocument(false)).setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}

}
