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
			req.addArgument("template", "res:/io/polestar/view/template/template.xml");
			respIn=aContext.issueRequestForResponse(req);
			INKFResponse respOut=aContext.createResponseFrom(respIn);
			respOut.setMimeType("text/html");
		}
		else
		{	aContext.createResponseFrom(respIn);
		}
	}

}
