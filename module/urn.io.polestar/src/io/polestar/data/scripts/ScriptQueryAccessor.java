package io.polestar.data.scripts;

import org.netkernel.layer0.nkf.*;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class ScriptQueryAccessor extends StandardAccessorImpl
{
	public ScriptQueryAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{	if (aContext.getThisRequest().argumentExists("name"))
		{	String name=aContext.source("arg:name",String.class);
			aContext.createResponseFrom(getScriptIdWithName(name));
			MonitorUtils.attachGoldenThread(aContext, ListScriptsAccessor.GT_SCRIPT_LIST);
		}
	}
	
	private String getScriptIdWithName(String aName) throws Exception
	{
		DBCollection col=MongoUtils.getCollection("scripts");
		BasicDBObject query = new BasicDBObject("name", new BasicDBObject("$regex",".*"+aName+".*"));
		DBCursor cursor = col.find(query).limit(1);
		if (cursor.hasNext())
		{	DBObject dbo=cursor.next();
			long id=(Long)dbo.get("id");
			return MonitorUtils.hexString(id);
		}
		throw new NKFException("Script not found","with name="+aName);
	}
}

