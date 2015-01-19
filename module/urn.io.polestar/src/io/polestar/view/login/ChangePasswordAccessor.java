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

import io.polestar.data.util.MonitorUtils;
import io.polestar.view.template.TemplateWrapper;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.representation.IHDSNode;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

public class ChangePasswordAccessor extends StandardAccessorImpl
{	public void onSource(INKFRequestContext aContext) throws Exception
	{
		MonitorUtils.assertAdmin(aContext);
		IHDSNode params=aContext.source("httpRequest:/params",IHDSNode.class);
		if (params.getChildren().length==0)
		{
			Object page=aContext.source("res:/io/polestar/view/login/passwordPage.xml");
			INKFResponse resp=aContext.createResponseFrom(page);
			resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
			resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
		}
		else
		{
			String password1=(String)params.getFirstValue("password1");
			String password2=(String)params.getFirstValue("password2");
			boolean equal=password1.equals(password2) && password1.length()>=8;
			if (equal)
			{	
				String username=(String)params.getFirstValue("username");
				//String username=aContext.source("session:/username",String.class);
				IHDSMutator m=HDSFactory.newDocument();
				m.pushNode("changePassword").addNode("username", username).addNode("password", password1);
				aContext.sink("res:/md/authentication", m.toDocument(false));
			}
			
			String good="<div class='container'><div class='alert alert-success'>Password successfully changed</div></div>";
			String bad="<div class='container'><div class='alert alert-warning'>Passwords do not match or are too short</div></div>";
			String body=equal?good:bad;
			INKFResponse resp=aContext.createResponseFrom(body);
			resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
			resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
		}
	}
}
