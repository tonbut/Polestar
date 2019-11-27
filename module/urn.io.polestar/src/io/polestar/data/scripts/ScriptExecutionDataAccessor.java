package io.polestar.data.scripts;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.urii.ParsedIdentifierImpl;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import org.netkernel.util.Utils;

import io.polestar.data.db.PersistenceFactory;
import io.polestar.data.util.MonitorUtils;

public class ScriptExecutionDataAccessor extends StandardAccessorImpl
{
	public static final String COLLECTION_SCRIPT_EXEC="scriptExec";
	
	public ScriptExecutionDataAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String type=aContext.getThisRequest().getArgumentValue(ParsedIdentifierImpl.ARG_ACTIVE_TYPE);
		if (type.equals("polestarScriptExecutionUpdate"))
		{	try
			{	onScriptExecutionUpdate(aContext);
			} catch (Exception e)
			{	System.out.println(Utils.throwableToString(e));
			}
		}
		else if (type.equals("polestarScriptExecutionStatus"))
		{	onScriptExecutionStatus(aContext);
		}
		else if (type.equals("polestarScriptExecutionReset"))
		{	onScriptExecutionReset(aContext);
		}
	}
	
	public void onScriptExecutionReset(INKFRequestContext aContext) throws Exception
	{
		PersistenceFactory.getPersistence(aContext).resetScriptStats(aContext);
		MonitorUtils.log(aContext,null,INKFLocale.LEVEL_INFO, "Script statistics reset");
	}
		
	public void onScriptExecutionStatus(INKFRequestContext aContext) throws Exception
	{
		IHDSDocument stats=PersistenceFactory.getPersistence(aContext).getScriptStats(aContext);
		IHDSReader r=stats.getReader();
		r.declareKey("byId", "/scripts/script", "id");
		INKFResponse resp=aContext.createResponseFrom(r.toDocument());
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	public void onScriptExecutionUpdate(INKFRequestContext aContext) throws Exception
	{
		String idString=aContext.source("arg:id",String.class);
		long id=MonitorUtils.fromHexString(idString);
		boolean isEdit=aContext.getThisRequest().argumentExists("edit");
		String error=null;
		if (aContext.getThisRequest().argumentExists("error"))
		{	error=aContext.source("arg:error",String.class);
		}
		long now=System.currentTimeMillis();
		PersistenceFactory.getPersistence(aContext).updateScriptStats(id,isEdit,error,now);
	}
}
