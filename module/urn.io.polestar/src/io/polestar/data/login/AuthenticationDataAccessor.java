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
package io.polestar.data.login;

import org.netkernel.layer0.nkf.*;
import org.netkernel.mod.hds.*;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.db.PersistenceFactory;
import io.polestar.data.util.MonitorUtils;

public class AuthenticationDataAccessor extends StandardAccessorImpl
{
	public AuthenticationDataAccessor()
	{	this.declareThreadSafe();
		this.declareInhibitCheckForBadExpirationOnMutableResource();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{	
		IHDSDocument authData=PersistenceFactory.getPersistence(aContext).getAuthentication(aContext);
		INKFResponse resp=aContext.createResponseFrom(authData);
		MonitorUtils.attachGoldenThread(aContext, "gt:auth");
	}

	public void onSink(INKFRequestContext aContext) throws Exception
	{
		IHDSMutator m=aContext.source("res:/md/authentication",IHDSDocument.class).getMutableClone();
		IHDSReader primary=aContext.sourcePrimary(IHDSDocument.class).getReader();
		String username=(String)primary.getFirstValue("/*/username");
		String password=(String)primary.getFirstValue("/*/password");
		IHDSMutator user=m.getFirstNodeOrNull("/authentication/user[username='"+username+"']");
		if (user!=null)
		{	
			INKFRequest req=aContext.createRequest("active:generatePasswordHash");
			req.addArgumentByValue("password",password);
			req.setRepresentationClass(String.class);
			String hash=(String)aContext.issueRequest(req);
			user.setCursor("password").setValue(hash);
			IHDSDocument state=m.toDocument(false);
			PersistenceFactory.getPersistence(aContext).setAuthentication(state, aContext);
			MonitorUtils.cutGoldenThread(aContext, "gt:auth");
		}
	}
}
