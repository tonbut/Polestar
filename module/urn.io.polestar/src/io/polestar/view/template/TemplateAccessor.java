package io.polestar.view.template;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

public class TemplateAccessor extends StandardAccessorImpl
{
	public TemplateAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		IHDSReader state=aContext.source("active:polestarSensorState",IHDSDocument.class).getReader();
		Double errors=(Double)state.getFirstValue("count(/sensors/sensor[error])");
		
		Object rep;
		if (errors==0.0)
		{	rep=aContext.source("res:/io/polestar/view/template/template.xml");
		}
		else
		{	rep=aContext.source("res:/io/polestar/view/template/templateError.xml");
		}
		aContext.createResponseFrom(rep);
	}
}
