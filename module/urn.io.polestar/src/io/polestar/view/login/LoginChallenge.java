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

import javax.servlet.http.Cookie;

import org.netkernel.layer0.nkf.*;
import org.netkernel.layer0.representation.IHDSNode;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.util.MonitorUtils;
import io.polestar.view.template.TemplateWrapper;

public class LoginChallenge extends StandardAccessorImpl
{
	
	
	
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String action=aContext.getThisRequest().getArgumentValue("action");
		if (action.equals("login"))
		{	onLogin(aContext);
		}
		else
		{	onLogout(aContext);
		}
	}
	
	private void onLogout(INKFRequestContext aContext) throws Exception
	{
		aContext.delete("session:/username");
		//aContext.delete("session:/persist/rm");
		
		//delete remember me cookie
		aContext.sink("httpResponse:/cookie", new RememberMeCookie("/polestar/"));				
		
		aContext.sink("httpResponse:/redirect", "/polestar/");
		aContext.createResponseFrom("logout successful");
	}
	
	private void onLogin(INKFRequestContext aContext) throws Exception
	{
		IHDSNode params=aContext.source("httpRequest:/params",IHDSNode.class);
		String username=(String)params.getFirstValue("username");
		String password=(String)params.getFirstValue("password");
		boolean remember=params.getFirstNode("remember")!=null;
		//System.out.println(params);
		String statusMessage=null;
		if (username!=null && password!=null)
		{	
			IHDSNode user=MonitorUtils.authenticate(username, password, aContext);
			if (user!=null)
			{	aContext.sink("session:/username", username);
				String role=(String)user.getFirstValue("role");
				aContext.sink("session:/role",role);
				
				if (remember)
				{	//setup remember me cookie
					long duration=7*24*60*60000; // 1week
					String passwordHash = (String)user.getFirstValue("password");
					Cookie c=MonitorUtils.createRememberMeCookie(username, passwordHash, duration, aContext);
					aContext.sink("httpResponse:/cookie", c);
				}
				
				String url=(String)params.getFirstValue("url");
				aContext.sink("httpResponse:/redirect", url);
				aContext.createResponseFrom("login successful");
			}
			else
			{	statusMessage="Login incorrect";
				String hostname = aContext.source("httpRequest:/remote-host",String.class);
				String message="Bad login from ["+hostname+"] with user ["+username+"]";
				MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, message);
			}
		}
		else
		{	statusMessage="Please login to access this service";
		}
		
		if (statusMessage!=null)
		{
			String page=aContext.source("res:/io/polestar/view/login/loginPage.xml",String.class);
			String url=(String)params.getFirstValue("url");
			if (url!=null) page=page.replace("%redirect%", url);
			page=page.replace("%message%", statusMessage);
			INKFResponse resp=aContext.createResponseFrom(page);
			resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
			
		}
	}
	
	private static String generateHashSource(String aHashedPassword, String aRemoteHost)
	{	return aHashedPassword+aRemoteHost;
	}
	
	private static String createRMHash(String aPasswordHash, String aRemoteHost, INKFRequestContext aContext) throws Exception
	{
		INKFRequest req=aContext.createRequest("active:generatePasswordHash");
		req.addArgumentByValue("password",generateHashSource(aPasswordHash,aRemoteHost));
		req.setRepresentationClass(String.class);
		return (String)aContext.issueRequest(req);
	}
		
}
