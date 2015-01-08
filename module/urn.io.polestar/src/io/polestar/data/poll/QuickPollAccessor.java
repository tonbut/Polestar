package io.polestar.data.poll;

import java.util.*;

import org.netkernel.layer0.nkf.*;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.util.MonitorUtils;

public class QuickPollAccessor extends StandardAccessorImpl
{
	public QuickPollAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		IHDSReader changes=aContext.source("active:polestarSensorChanges",IHDSDocument.class).getReader();
		List<String> changedSensors=new ArrayList(changes.getValues("/sensors/sensor"));
		changedSensors.add("1s");
		MonitorUtils.executeTriggeredScripts(changedSensors, false, aContext);
	}
}

