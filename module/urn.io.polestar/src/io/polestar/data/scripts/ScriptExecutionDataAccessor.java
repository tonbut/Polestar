package io.polestar.data.scripts;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BSONObject;
import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.urii.ParsedIdentifierImpl;
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

import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

public class ScriptExecutionDataAccessor extends StandardAccessorImpl
{
	public static final String COLLECTION_SCRIPT_EXEC="scriptExec";
	
	public ScriptExecutionDataAccessor()
	{	this.declareThreadSafe();
	}
	
	public void postCommission(INKFRequestContext aContext) throws Exception
	{	
		DBCollection col=MongoUtils.getCollection(COLLECTION_SCRIPT_EXEC);
		List<DBObject> indexes=col.getIndexInfo();
		if (indexes.size()==0)
		{	col.createIndex(new BasicDBObject("id", 1));
		}
		//col.remove(new BasicDBObject());
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
		DBCollection col=MongoUtils.getCollection(COLLECTION_SCRIPT_EXEC);
		
		BasicDBObject query=new BasicDBObject();
		BasicDBObject state=new BasicDBObject();
		BasicDBObject set=new BasicDBObject();
		set.append("c", 0);
		set.append("ec",0);
		state.append("$set", set);
		WriteResult wr=col.update(query,state,false,true);
		aContext.logRaw(INKFLocale.LEVEL_INFO, "Script statistics reset");
	}
		
	public void onScriptExecutionStatus(INKFRequestContext aContext) throws Exception
	{
		DBCollection col=MongoUtils.getCollection(COLLECTION_SCRIPT_EXEC);
		//System.out.println(col.getStats());
		//DBCursor cursor = col.find(new BasicDBObject("id",new BasicDBObject("$ne","blah")));
		DBCursor cursor = col.find();
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("scripts");
		while (cursor.hasNext())
		{	DBObject dbo=cursor.next();
			String id=(String)dbo.get("id");
			
			Number c=(Number)dbo.get("c");
			Number ec=(Number)dbo.get("ec");
			if (ec==null) ec=Integer.valueOf(0);
			float errorPercent=ec.floatValue()/c.floatValue();
			Long lastExec=(Long)dbo.get("t");
			Long lastError=(Long)dbo.get("et");
			String error=(String)dbo.get("e");
			
			m.pushNode("script")
			.addNode("id", id)
			.addNode("count", c)
			.addNode("errors", ec)
			.addNode("errorPercent", errorPercent)
			.addNode("lastExecTime", lastExec)
			.addNode("lastErrorTime", lastError)
			.addNode("lastError", error)
			.popNode();
			
		}
		m.declareKey("byId", "/scripts/script", "id");
		aContext.createResponseFrom(m.toDocument(false)).setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	public void onScriptExecutionUpdate(INKFRequestContext aContext) throws Exception
	{
		String id=aContext.source("arg:id",String.class);
		
		long now=System.currentTimeMillis();
		
		DBCollection col=MongoUtils.getCollection(COLLECTION_SCRIPT_EXEC);
		
		BasicDBList inO=new BasicDBList();
		inO.add(id);
		BasicDBObject query=new BasicDBObject("id", new BasicDBObject("$in",inO));
		
		//eq doesn't work in older versions of mongoDB
		//BasicDBObject query=new BasicDBObject("id", new BasicDBObject("$eq",id));
		
		BasicDBObject state=new BasicDBObject();
		state.append("$setOnInsert", new BasicDBObject("id",id));
		
		BasicDBObject set=new BasicDBObject();
		BasicDBObject inc=new BasicDBObject();
		set.append("t", now);
		inc.append("c",1);
		
		String error=null;
		if (aContext.getThisRequest().argumentExists("error"))
		{	error=aContext.source("arg:error",String.class);
		}
		
		if (error==null || error.length()==0)
		{	//set.append("e",null);
		}
		else
		{	set.append("e",error);
			set.append("et",now);
			inc.append("ec",1);
		}

		state.append("$set", set);
		state.append("$inc", inc);
		WriteResult wr=col.update(query,state,true,false);
	}
}
