package io.polestar.data.scriptRuntime;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

public class RuntimeLookupAccessor extends StandardAccessorImpl {

	public RuntimeLookupAccessor()
	{	this.declareThreadSafe();
	}
	
	
	@Override
	public void onSource(INKFRequestContext aContext) throws Exception {
		INKFRequest req = aContext.createRequest("active:spaceAggregateHDS");
		req.setRepresentationClass(IHDSDocument.class);
		req.addArgument("uri", "res:/etc/system/PolestarScriptConfig.xml");
		IHDSDocument runtimeConfigs = (IHDSDocument)aContext.issueRequest(req);

		IHDSMutator resBuilder = HDSFactory.newDocument();

		resBuilder.pushNode("languages");
		for (IHDSReader spaceNode : runtimeConfigs.getReader().getNodes("/spaces/space[config]")) {
			String id = (String) spaceNode.getFirstValue("id");
			String version = (String) spaceNode.getFirstValue("version");
			String name = (String) spaceNode.getFirstValue("config/name");
			String endpoint = (String) spaceNode.getFirstValue("config/endpoint");

			resBuilder.pushNode("language");
			resBuilder.addNode("id", id);
			resBuilder.addNode("version", version);
			resBuilder.addNode("name", name);
			resBuilder.addNode("endpoint", endpoint);
			resBuilder.popNode();
		}

		aContext.createResponseFrom(resBuilder.toDocument(false));
	}
}
