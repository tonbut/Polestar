package io.polestar.view.login;

import org.netkernel.layer0.nkf.*;
import org.netkernel.module.standard.endpoint.TransparentOverlayImpl;
import io.polestar.data.util.MonitorUtils;

public class CheckLogin extends TransparentOverlayImpl
{
	public CheckLogin()
	{	declareThreadSafe();
	}
	
	@Override
	public void onRequest(String elementId, INKFRequestContext aContext) throws Exception
	{
		if (!elementId.equals("polestar:loginChallenge") && aContext.getThisRequest().getVerb()==INKFRequestReadOnly.VERB_SOURCE)
		{
			String username=aContext.source("session:/username",String.class);
			if (username==null)
			{	//attempt remember me login - set username if successful
				username=MonitorUtils.onRememberMeLogin(aContext);
			}
			if (username==null)
			{	String url=aContext.source("httpRequest:/url",String.class);
				aContext.sink("httpResponse:/redirect","/polestar/login?url="+url);
				aContext.createResponseFrom("login redirect");
			}
		}
		
		if (!aContext.isResponseSet())
		{	INKFRequest req=aContext.getThisRequest().getIssuableClone();
			INKFResponseReadOnly respIn=aContext.issueRequestForResponse(req);
			aContext.createResponseFrom(respIn);
		}
		
	}

}
