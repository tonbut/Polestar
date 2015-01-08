package io.polestar.data.scripts;

import java.util.Random;

import org.netkernel.layer0.nkf.*;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

import com.mongodb.*;

public class ScriptDataAccessor extends StandardAccessorImpl
{
	public ScriptDataAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String idString=aContext.getThisRequest().getArgumentValue("id");
		Long id=MonitorUtils.fromHexString(idString);
		String fragment=null;
		if (aContext.getThisRequest().argumentExists("fragment"))
		{	fragment=aContext.getThisRequest().getArgumentValue("fragment");
		}
		
		BasicDBObject query = new BasicDBObject("id", id);
		DBCollection col=MongoUtils.getCollection("scripts");
		DBCursor cursor = col.find(query);
		try
		{	if(cursor.hasNext())
			{	DBObject dbo=cursor.next();
			
				if (fragment==null)
				{	IHDSMutator m=HDSFactory.newDocument();
					m.pushNode("script");
					m.addNode("id",idString);
					m.addNode("name",(String)dbo.get("name"));
					m.addNode("script",(String)dbo.get("script"));
					m.addNode("state",(String)dbo.get("state"));
					m.addNode("triggers",(String)dbo.get("triggers"));
					m.addNode("keywords",(String)dbo.get("keywords"));
					m.addNode("public",dbo.get("public"));
					m.popNode();
					INKFResponse resp=aContext.createResponseFrom(m.toDocument(false));
					//resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
					MonitorUtils.attachGoldenThread(aContext, "gt:script:"+id, "gt:script:"+id+":state");
					return;
				}
				else
				{	Object rep=null;
					if (fragment.equals("script"))
					{	rep=dbo.get("script");
						MonitorUtils.attachGoldenThread(aContext, "gt:script:"+id);
					}
					else if (fragment.equals("state"))
					{	rep=dbo.get("state");
						MonitorUtils.attachGoldenThread(aContext, "gt:script:"+id+":state");
					}
					INKFResponse resp=aContext.createResponseFrom(rep);
					//resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
					return;
				}
		   }
		} finally
		{	cursor.close();
		}
		throw new NKFException("Script not found","with id="+idString);
	}

	public void onSink(INKFRequestContext aContext) throws Exception
	{
		String idString=aContext.getThisRequest().getArgumentValue("id");
		Long id=MonitorUtils.fromHexString(idString);
		String fragment=null;
		if (aContext.getThisRequest().argumentExists("fragment"))
		{	fragment=aContext.getThisRequest().getArgumentValue("fragment");
		}
		
		BasicDBObject query = new BasicDBObject("id", id);
		DBCollection col=MongoUtils.getCollection("scripts");
		
		BasicDBObject update = new BasicDBObject();
		boolean needsUpdate=false;

		if (fragment==null)
		{	IHDSReader primary=aContext.sourcePrimary(IHDSDocument.class).getReader().getFirstNode("*");
			Object name=primary.getFirstValueOrNull("name");	
			if (name!=null)
			{	update.append("name", name);
				MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id, ListScriptsAccessor.GT_SCRIPT_LIST);
				needsUpdate=true;
			}
			Object triggers=primary.getFirstValueOrNull("triggers");	
			if (triggers!=null)
			{	update.append("triggers", triggers);
				MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id, ListScriptsAccessor.GT_SCRIPT_LIST);
				needsUpdate=true;
			}
			Object keywords=primary.getFirstValueOrNull("keywords");	
			if (keywords!=null)
			{	update.append("keywords", keywords);
				MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id, ListScriptsAccessor.GT_SCRIPT_LIST);
				needsUpdate=true;
			}
			Object isPublic=primary.getFirstValueOrNull("public");	
			if (isPublic!=null)
			{	update.append("public", isPublic);
				MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id);
				needsUpdate=true;
			}
			Object script=primary.getFirstValueOrNull("script");
			if (script!=null)
			{	update.append("script", script);
				MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id);
				needsUpdate=true;
			}
			Object state=primary.getFirstValueOrNull("state");
			if (state!=null)
			{	update.append("state", state);
				MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id+":state");
				needsUpdate=true;
			}
		}
		else
		{	if (fragment.equals("state"))
			{	Object state=aContext.sourcePrimary(String.class);
				update.append("state", state);
				MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id+":state");
				needsUpdate=true;
			}
		}
		
		if (needsUpdate)
		{	BasicDBObject set = new BasicDBObject("$set",update);
			WriteResult wr=col.update(query, set);
		}
	}

	public void onExists(INKFRequestContext aContext) throws Exception
	{	String idString=aContext.getThisRequest().getArgumentValue("id");
		Long id=MonitorUtils.fromHexString(idString);
		BasicDBObject query = new BasicDBObject("id", id);
		DBCollection col=MongoUtils.getCollection("scripts");
		DBCursor cursor = col.find(query);
		boolean exists=false;
		try
		{	exists=cursor.hasNext();
		} finally
		{	cursor.close();
		}
		aContext.createResponseFrom(exists).setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}

	public void onNew(INKFRequestContext aContext) throws Exception
	{	long id;
		String idString;
		if (aContext.getThisRequest().argumentExists("id"))
		{	idString=aContext.getThisRequest().getArgumentValue("id");
			id=MonitorUtils.fromHexString(idString);
		}
		else
		{	Random r=new Random();
			id=r.nextLong();
			idString=MonitorUtils.hexString(id);
		}
		BasicDBObject doc = new BasicDBObject("name", "Script "+idString);
		doc.append("id", id);
		doc.append("triggers", "");
		doc.append("keywords", "");
		doc.append("script", "");
		doc.append("state", "<state/>");
		doc.append("public", "private");
		
		DBCollection col=MongoUtils.getCollection("scripts");
		long size=col.getCount();
		doc.append("order", (int)size);
		
		col.insert(doc);
		aContext.createResponseFrom("res:/polestar/data/script/"+idString);
		MonitorUtils.cutGoldenThread(aContext, ListScriptsAccessor.GT_SCRIPT_LIST);
	}

	public void onDelete(INKFRequestContext aContext) throws Exception
	{
		String idString=aContext.getThisRequest().getArgumentValue("id");
		Long id=MonitorUtils.fromHexString(idString);
		BasicDBObject query = new BasicDBObject("id", id);
		DBCollection col=MongoUtils.getCollection("scripts");
		WriteResult wr=col.remove(query);
		boolean result=wr.getN()>0;
		aContext.createResponseFrom(result);
		MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id, ListScriptsAccessor.GT_SCRIPT_LIST);
	}
	
	
}
