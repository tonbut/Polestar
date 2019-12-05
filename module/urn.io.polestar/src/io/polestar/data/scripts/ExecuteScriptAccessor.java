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

import org.netkernel.layer0.meta.IEndpointMeta;
import org.netkernel.layer0.nkf.*;
import org.netkernel.layer0.urii.SimpleIdentifierImpl;
import org.netkernel.mod.hds.*;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import org.netkernel.request.IRequestScopeLevel;

import io.polestar.data.util.MonitorUtils;

import org.netkernel.urii.IIdentifier;
import org.netkernel.urii.IMetaRepresentation;
import org.netkernel.urii.ISpace;
import org.netkernel.urii.IVersion;
import org.netkernel.urii.impl.Version;

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

		doExecuteScript(aContext, id);
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

		doExecuteScript(aContext, id);
	}

	private void doExecuteScript(INKFRequestContext aContext, String id) throws Exception {
		String language = aContext.source("res:/md/script/"+id+"#language", String.class);

		IHDSDocument availableLanguages = aContext.source("active:polestarAvailableLanguages", IHDSDocument.class);
		IHDSReader languageNode = availableLanguages.getReader().getFirstNode("//language[endpoint='" + language + "']");

		IIdentifier spaceId = new SimpleIdentifierImpl((String) languageNode.getFirstValue("id"));
		IVersion spaceVersion = new Version((String) languageNode.getFirstValue("version"));
		ISpace space = aContext.getKernelContext().getKernel().getSpace(spaceId, spaceVersion, spaceVersion);

		try
		{
			INKFRequest metaReq=aContext.createRequest(language);
			metaReq.injectRequestScope(space);
			metaReq.setVerb(INKFRequestReadOnly.VERB_META);
			metaReq.setRepresentationClass(IMetaRepresentation.class);
			IEndpointMeta meta=(IEndpointMeta)aContext.issueRequest(metaReq);

			INKFRequest req = aContext.getKernelContext().createRequestToEndpoint(meta);
			req.injectRequestScope(space);
			req.addArgument("operator", "res:/md/script/"+id+"#script");
			req.addArgument("name", "res:/md/script/"+id+"#name");
			req.addArgument("state", "res:/md/script/"+id+"#state");

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

