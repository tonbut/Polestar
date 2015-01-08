package io.polestar.view.template;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

public class IndexPageAccessor extends StandardAccessorImpl
{
	public IndexPageAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		try
		{	INKFResponseReadOnly respIn=aContext.sourceForResponse("res:/md/execute/named/Homepage");
			aContext.createResponseFrom(respIn);
		}
		catch (Exception e)
		{	
			INKFResponseReadOnly respIn=aContext.sourceForResponse("res:/io/polestar/view/template/DummyIndexPage.xml");
			INKFResponse resp=aContext.createResponseFrom(respIn);
			resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
		}
	}
}
