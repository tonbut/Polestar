package io.polestar.data.poll;

import java.util.*;

import org.netkernel.layer0.nkf.*;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.util.MonitorUtils;

public class MediumPollAccessor extends StandardAccessorImpl
{
	public MediumPollAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		//fire all medium poll scripts
		MonitorUtils.executeTriggeredScripts(Collections.singleton("30s"), true, aContext);
	}
}

