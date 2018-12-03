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
package io.polestar.data.scripts;

import java.util.concurrent.atomic.AtomicInteger;

import org.netkernel.layer0.nkf.*;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import org.netkernel.request.IRequestScopeLevel;

import io.polestar.data.util.MonitorUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class ExecuteScriptAccessor extends StandardAccessorImpl
{
	public final static int BEAN_CACHE_CLEAR_PERIOD=97;
	private AtomicInteger mBeanCacheClearCountdown=new AtomicInteger(BEAN_CACHE_CLEAR_PERIOD);
	
	public ExecuteScriptAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		//hack - the bean cache seems to cause leaks when combined with groovy
		if (mBeanCacheClearCountdown.decrementAndGet()==0)
		{	mBeanCacheClearCountdown.set(BEAN_CACHE_CLEAR_PERIOD);
			java.beans.Introspector.flushCaches();
		}
		
		//inject script state buffer into scope to capture state sink requests
		IRequestScopeLevel originalScope=aContext.getKernelContext().getRequestScope();
		ScriptStateBuffer ssb=new ScriptStateBuffer(originalScope);
		ssb.onCommissionSpace(aContext.getKernelContext().getKernel());
		aContext.getKernelContext().injectDurableRequestScope(ssb);
		
		//inject groovy space into scope to stop resolving out to data space to find groovy and hence stopping
		//script state buffer being first in line to capture state sink requests
		INKFRequest groovyRequest=aContext.createRequest("active:groovy+operator@dummy");
		aContext.getKernelContext().rescope(groovyRequest);
		
		String resolvedId=aContext.getThisRequest().getResolvedElementId();
		if (resolvedId.equals("polestar:data:scriptExecute"))
		{	onSourceData(aContext);
		}
		else if (resolvedId.equals("polestar:data:scriptExecuteActive"))
		{	onSourceActive(aContext);
		}
		
		//now really sink state if necessary
		aContext.getKernelContext().setRequestScope(originalScope);
		ssb.sinkIfModified(aContext);
	}
	
	public void onSourceActive(INKFRequestContext aContext) throws Exception
	{
		INKFRequestReadOnly thisReq=aContext.getThisRequest();
		String id;
		if (thisReq.argumentExists("name"))
		{	String name=aContext.getThisRequest().getArgumentValue("name");
			INKFRequest req=aContext.createRequest("active:polestarQueryScript");
			req.addArgumentByValue("name", name);
			id=(String)aContext.issueRequest(req);
		}
		else
		{	id=thisReq.getArgumentValue("script");
		}
		try
		{	INKFRequest req=aContext.createRequest("active:groovy");
			req.addArgument("operator", "res:/md/script/"+id+"#script");
			req.addArgument("state","res:/md/script/"+id+"#state");
			for (int i=0; i<thisReq.getArgumentCount(); i++)
			{	String n=thisReq.getArgumentName(i);
				if (!(n.equals("scheme") || n.equals("activeType") || n.equals("script")))
				{	req.addArgument(n, thisReq.getArgumentValue(i));
				}
				//System.out.println(argName);
			}
			INKFResponseReadOnly resp=aContext.issueRequestForResponse(req);
			aContext.createResponseFrom(resp);	
			updateScriptExecutionData(id,null,aContext);
		}
		catch (NKFException e)
		{	//System.out.println(e);
			IHDSReader scriptData=aContext.source("res:/md/script/"+id,IHDSDocument.class).getReader();
			String scriptName=(String)scriptData.getFirstValue("/script/name");
			MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, "Script "+scriptName+" Failed: "+e.getDeepestId()+" "+e.getDeepestMessage());
			updateScriptExecutionData(id,e,aContext);
			throw e;
			
		}
	}

	public void onSourceData(INKFRequestContext aContext) throws Exception
	{	String id;
		if (aContext.getThisRequest().argumentExists("id"))
		{	id=aContext.getThisRequest().getArgumentValue("id");
		}
		else if (aContext.getThisRequest().argumentExists("named"))
		{	String name=aContext.getThisRequest().getArgumentValue("named");
			INKFRequest req=aContext.createRequest("active:polestarQueryScript");
			req.addArgumentByValue("name", name);
			id=(String)aContext.issueRequest(req);
		}
		else throw new NKFException("Malformed execute request");
		
		try
		{	INKFRequest req=aContext.createRequest("active:groovy");
			req.addArgument("operator", "res:/md/script/"+id+"#script");
			req.addArgument("state","res:/md/script/"+id+"#state");
			INKFResponseReadOnly resp=aContext.issueRequestForResponse(req);
			aContext.createResponseFrom(resp);
			updateScriptExecutionData(id,null,aContext);
		}
		catch (NKFException e)
		{	//System.out.println(e);
			IHDSReader scriptData=aContext.source("res:/md/script/"+id,IHDSDocument.class).getReader();
			String scriptName=(String)scriptData.getFirstValue("/script/name");
			MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, "Script "+scriptName+" Failed: "+e.getDeepestId()+" "+e.getDeepestMessage());
			updateScriptExecutionData(id,e,aContext);
			throw e;
			
		}
	}	
	
	private void updateScriptExecutionData(String aId, NKFException aError, INKFRequestContext aContext) throws Exception
	{	
		INKFRequest req=aContext.createRequest("active:polestarScriptExecutionUpdate");
		req.setHeader(INKFRequest.HEADER_EXCLUDE_DEPENDENCIES, true); //don't stop caching
		req.addArgumentByValue("id", aId);
		if (aError!=null)
		{	String errorMsg=aError.getDeepestId()+": "+aError.getDeepestMessage();
			req.addArgumentByValue("error", errorMsg);
		}
		aContext.issueRequest(req);
	}
}

