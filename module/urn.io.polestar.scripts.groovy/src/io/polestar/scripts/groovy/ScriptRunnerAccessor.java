package io.polestar.scripts.groovy;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestReadOnly;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

public class ScriptRunnerAccessor extends StandardAccessorImpl {
	public ScriptRunnerAccessor() {
		declareThreadSafe();
	}

	private final static String SCRIPT_HEAD_GROOVY="context = io.polestar.data.api.PolestarContext.createContext(context,\"%s\");\n";

	@Override
	public void onSource(INKFRequestContext aContext) throws Exception {
		String script = aContext.source("arg:operator", String.class);
		String name = aContext.source("arg:name", String.class);

		script = String.format(SCRIPT_HEAD_GROOVY, name) + script;

		INKFRequest req=aContext.createRequest("active:groovy");
		req.addArgumentByValue("operator", script);
		req.addArgument("state", "arg:state");
		INKFRequestReadOnly thisReq=aContext.getThisRequest();
		for (int i=0; i<thisReq.getArgumentCount(); i++)
		{	String n=thisReq.getArgumentName(i);
			if (!(n.equals("scheme") || n.equals("activeType") || n.equals("script") || n.equals("operator") || n.equals("name")))
			{	req.addArgument(n, thisReq.getArgumentValue(i));
			}
		}

		INKFResponseReadOnly resp=aContext.issueRequestForResponse(req);
		aContext.createResponseFrom(resp);
	}
}
