package io.polestar.data.poll;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

public class PollingStateAccessor extends StandardAccessorImpl
{
	public PollingStateAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{	IHDSDocument pollingState=PeriodPollAccessor.getPollingState();
		INKFResponse resp=aContext.createResponseFrom(pollingState);
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
}
