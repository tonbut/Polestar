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

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

public class LogQueryAccessor extends StandardAccessorImpl
{
	public static final String LOG_COLLECTION="log";
	public LogQueryAccessor()
	{	this.declareThreadSafe();
	}
	
	public void postCommission(INKFRequestContext aContext) throws Exception
	{	
		//index log
		try
		{	DBCollection col=MongoUtils.getCollection(LOG_COLLECTION);
			List<DBObject> indexes=col.getIndexInfo();
			if (indexes.size()<2)
			{	col.createIndex(new BasicDBObject("t", 1));
			}
		}
		catch (Exception e)
		{	MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
		}
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader();
		Integer start=(Integer)operator.getFirstValue("start");
		Integer length=(Integer)operator.getFirstValue("length");
		String search=(String)operator.getFirstValue("search");
		DBCollection col=MongoUtils.getCollection(LOG_COLLECTION);
		DBCursor cursor=col.find();
		int len=cursor.length();
		int filterLen=len;
		
		if (search==null || search.length()==0)
		{
			cursor=col.find().sort(new BasicDBObject("t",-1));
			cursor=cursor.skip(start).limit(length);
		}
		else
		{
			BasicDBObject filter=new BasicDBObject("m", new BasicDBObject("$regex",search));
			cursor=col.find(filter).sort(new BasicDBObject("t",-1));
			filterLen=cursor.length();
			cursor=col.find(filter).sort(new BasicDBObject("t",-1));
			cursor=cursor.skip(start).limit(length);
			
		}
		
		IHDSMutator m=HDSFactory.newDocument();
		m.addNode("length", len);
		m.addNode("filterLength", filterLen);
		while (cursor.hasNext())
		{
			DBObject item=cursor.next();
			m.pushNode("entry")
				.addNode("time", item.get("t"))
				.addNode("level", item.get("l"))
				.addNode("origin", item.get("o"))
				.addNode("msg", item.get("m"))
				.popNode();
		}
		
		aContext.createResponseFrom(m.toDocument(false)).setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
}
