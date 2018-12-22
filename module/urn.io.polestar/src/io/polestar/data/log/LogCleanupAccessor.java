package io.polestar.data.log;

import java.util.List;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import org.netkernel.util.Utils;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

import io.polestar.data.api.PolestarContext;
import io.polestar.data.db.MongoUtils;
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
		
		BasicDBObject query=new BasicDBObject("t", new BasicDBObject("$lt",time));
		DBCollection col=MongoUtils.getCollection(LogQueryAccessor.LOG_COLLECTION);
		WriteResult result=col.remove(query);
		int deleted=result.getN();
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
