package io.polestar.data.log;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.data.api.PolestarContext;
import io.polestar.data.db.PersistenceFactory;
import io.polestar.data.util.MonitorUtils;

public class LogCleanupAccessor extends StandardAccessorImpl
{
	public LogCleanupAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		aContext=PolestarContext.createContext(aContext, null);
		long logRetentionPeriod=getLogRetentionPeriod(aContext);
		aContext.logRaw(INKFLocale.LEVEL_INFO,"Cleaning up log items older than "+MonitorUtils.formatPeriod(logRetentionPeriod));
		long time=System.currentTimeMillis()-logRetentionPeriod;
		int deleted=PersistenceFactory.getPersistence(aContext).logRemoveOlderThan(time,aContext);
		aContext.logRaw(INKFLocale.LEVEL_INFO,"Deleted "+deleted+" log entries");
		
	}
	
	private long getLogRetentionPeriod(INKFRequestContext aContext)
	{	long result;
		try
		{	IHDSReader config=aContext.source("res:/md/execute/named/Configuration",IHDSDocument.class).getReader();
			result=(Long)config.getFirstValue("logRetentionPeriod");
		}
		catch (Exception e)
		{	//e.printStackTrace();
			result=1000L*60*60*24*7;
		}
		return result;
	}
}
