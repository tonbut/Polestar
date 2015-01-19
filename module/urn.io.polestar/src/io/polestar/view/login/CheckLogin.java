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
