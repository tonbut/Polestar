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
package io.polestar.data.util;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.Cookie;

import org.netkernel.layer0.nkf.*;
import org.netkernel.layer0.representation.IHDSNode;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import io.polestar.view.login.RememberMeCookie;

public class MonitorUtils
{
	public static String hexString(long aLong)
	{	return String.format("%016X",aLong);
	}
	
	public static long fromHexString(String aHex)
	{	return new BigInteger(aHex, 16).longValue();
	}
	
	public static void cutGoldenThread(INKFRequestContext aHelper, String... aIds) throws Exception
	{	INKFRequest gtReq = aHelper.createRequest("active:cutGoldenThread");
		for (String id : aIds)
		{	gtReq.addArgument("id", id);
		}
		aHelper.issueRequest(gtReq);
	}
	
	public static void attachGoldenThread(INKFRequestContext aHelper, String... aIds) throws Exception
	{	INKFRequest gtReq = aHelper.createRequest("active:attachGoldenThread");
		for (String id : aIds)
		{	gtReq.addArgument("id", id);
		}
		aHelper.issueRequest(gtReq);
	}
	
	
	public static boolean isLoggedIn(INKFRequestContext aContext)
	{	boolean result=false;
		try
		{	String username=aContext.source("session:/username",String.class);
			if (username!=null)
			{	result=true;
			}
			else
			{	username=onRememberMeLogin(aContext);
				if (username!=null)
				{	result=true;
				}
			}
		}
		catch (Exception e) {;}
		return result;
	}
	
	public static void assertAdmin(INKFRequestContext aContext) throws NKFException
	{	try
		{	String role=aContext.source("session:/role",String.class);
			if (role!=null && role.equals("admin"))
			{	return;
			}
		}
		catch (Exception e) {;}
		throw new NKFException("Not Authorized");
	}
	
	
	public static String onRememberMeLogin(INKFRequestContext aContext) throws Exception
	{	String result=null;
		Cookie c=aContext.source("httpRequest:/cookie/"+RememberMeCookie.NAME,Cookie.class);
		if (c!=null)
		{	RememberMeCookie rmc=new RememberMeCookie(c, aContext);
			result=rmc.getUsername();
			IHDSNode user=getUserDetails(result, aContext);
			if (user!=null)
			{	String password=(String)user.getFirstValue("password");
				String hostname=aContext.source("httpRequest:/remote-host",String.class);
				if (rmc.isValid(password,hostname,aContext))
				{	//valid remember me login
					aContext.sink("session:/username",result);
					String role=(String)user.getFirstValue("role");
					aContext.sink("session:/role",role);
				}
				else
				{	result=null;
				}
			}
			else
			{	result=null;
			}
		}	
		return result;
	}
	
	public static IHDSNode getUserDetails(String aUsername, INKFRequestContext aContext) throws Exception
	{	IHDSNode result=null;
		IHDSNode auth=aContext.source("res:/md/authentication",IHDSNode.class);
		for (IHDSNode user: auth.getNodes("/authentication/user"))
		{	String authName=(String)user.getFirstValue("username");
			if (aUsername.equals(authName))
			{	result=user;
			}
		}
		return result;
	}
	
	
	/** Format given period as a human readable string
	 * @param aPeriod
	 * @param doSeconds if true display seconds
	 * @return
	 */
	public static String formatPeriod(long aPeriod)
	{
		long period=aPeriod;
	    long second=1000;
	    long minute=second*60;
	    long hour=minute*60;
	    long day=hour*24;
	    long week=day*7;
	    long year=day*365;
	    String message="";
	    long years=period/year;
	    do
	    {	boolean already=false;
		    if (years>0)
		    {   message+=years;
		        message+=(years==1)?" year":" years";
		        period-=years*year;
		        already=true;
		    }
		    long weeks=period/week;
		    if (weeks>0 || years>0)
		    {   if (already) message+=", ";
		    	message+=weeks;
		        message+=(weeks==1)?" week":" weeks";
		        period-=weeks*week;
		        if (years>0) break;
		        already=true;
		    }
		    long days=period/day;
		    if (days>0 || weeks>0 || years>0)
		    {   if (already) message+=", ";
		    	message+=days;
		        message+=(days==1)?" day":" days";
		        period-=days*day;
		        if (weeks>0) break;
		        already=true;
		    }
		    long hours=period/hour;
		    if (hours>0 || days>0 || weeks>0 || years>0)
		    {   if (already) message+=", ";
		    	message+=hours;
		        message+=(hours==1)?" hour":" hours";
		        period-=hours*hour;
		        if (days>0) break;
		        already=true;
		    }
		    long minutes=period/minute;
		    if (minutes>0 || hours>0 || days>0 || weeks>0 || years>0)
		    {   if (already) message+=", ";
		    	message+=minutes;
		        message+=(minutes==1)?" minute":" minutes";
		        period-=minutes*minute;
		        if (hours>0) break;
		        already=true;
		    }
		    {   if (already) message+=", ";
		        long seconds=period/second;
		        {   message+=seconds;
		            message+=(seconds==1)?" second":" seconds";
		        }
		    }
	    } while (false);
	    return message;
	}
	
	public static void executeTriggeredScripts(Collection<String> aSensors, boolean aJoin, INKFRequestContext aContext) throws Exception
	{	Set<String> triggeredScripts = new HashSet<String>();
		IHDSReader triggers=aContext.source("active:polestarScriptTriggers",IHDSDocument.class).getReader();

		for (Object o : aSensors)
		{	String changedSensor=(String)o;
			for (Object o2 : triggers.getValues(String.format("key('byId','%s')/scripts/script",changedSensor)))
			{	String triggeredScript=(String)o2;
			triggeredScripts.add(triggeredScript);
			}
		}
		
		List<INKFAsyncRequestHandle> handles=new ArrayList<INKFAsyncRequestHandle>(triggeredScripts.size());
		for (String script : triggeredScripts)
		{	INKFRequest ereq=aContext.createRequest("res:/md/execute/"+script);
			handles.add(aContext.issueAsyncRequest(ereq));
		}
		if (aJoin)
		{	for (INKFAsyncRequestHandle handle : handles)
			{	try
				{	handle.join();
				}
				catch (NKFException e)
				{	//ignore
				}
			}
		}
	}
	

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		long l=-4236037447797807431L;
		System.out.println(l);
		String hex=hexString(l);
		System.out.println(hex);
		long l2 = fromHexString(hex);
		System.out.println(l2);

	}

}
