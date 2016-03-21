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
package io.polestar.view.template;

import org.netkernel.layer0.meta.IPrototypeParameterMeta;
import org.netkernel.layer0.meta.impl.PrototypeParameterMetaImpl;
import org.netkernel.layer0.nkf.*;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.IStandardOverlay;
import org.netkernel.module.standard.endpoint.TransparentOverlayImpl;
import org.netkernel.urii.ISpace;

public class TemplateWrapper extends TransparentOverlayImpl
{
	public static String HEADER_WRAP="polestar_wrap";
	
	public static IPrototypeParameterMeta[] sPrototypeParameters = new IPrototypeParameterMeta[]
    {	new PrototypeParameterMetaImpl(IStandardOverlay.PARAM_SPACE,ISpace.class,IPrototypeParameterMeta.NO_DEFAULT)
	};
	
	public TemplateWrapper()
	{	declareParameters(sPrototypeParameters);
		declareThreadSafe();
	}

	public void onRequest(String elementId, INKFRequestContext aContext) throws Exception
	{
		INKFRequest req=aContext.getThisRequest().getIssuableClone();
		INKFResponseReadOnly respIn=aContext.issueRequestForResponse(req);
		
		if (respIn.hasHeader(HEADER_WRAP))
		{	
			String title=null, subtitle=null, icon=null;
			try
			{	IHDSReader config=aContext.source("res:/md/execute/named/Configuration",IHDSDocument.class).getReader();
				title=(String)config.getFirstValueOrNull("title");
				subtitle=(String)config.getFirstValueOrNull("subtitle");
				icon=(String)config.getFirstValueOrNull("icon");
				aContext.createResponseFrom(respIn);
			}
			catch (Exception e)
			{	//e.printStackTrace();
			}
			if (title==null) title="Polestar IoT Hub";
			if (subtitle==null) subtitle="powered by NetKernel";
			if (icon==null) icon="/polestar/pub/icon/polestar.png";
			
			req=aContext.createRequest("active:xrl2");
			req.addArgumentFromResponse("content",respIn);
			req.addArgumentByValue("title", title);
			req.addArgumentByValue("subtitle", subtitle);
			req.addArgumentByValue("icon", icon);
			req.addArgument("template", "active:polestarTemplate");
			respIn=aContext.issueRequestForResponse(req);
			INKFResponse respOut=aContext.createResponseFrom(respIn);
			respOut.setMimeType("text/html");
		}
		else
		{	aContext.createResponseFrom(respIn);
		}
	}

}
