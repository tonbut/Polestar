package io.polestar.data.scripts;

import org.netkernel.layer0.nkf.*;
import org.netkernel.layer0.util.Utils;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class ListScriptsAccessor extends StandardAccessorImpl
{
	public static String GT_SCRIPT_LIST="gt:script:all";
	
	public ListScriptsAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		DBCollection coll = MongoUtils.getCollection("scripts");
		DBCursor cursor = coll.find().sort(new BasicDBObject("order",1));		
		
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("scripts");
		try
		{	while(cursor.hasNext())
			{	DBObject dbo=cursor.next();
				
				Long id=(Long)dbo.get("id");
				//if (id==null) continue;
				m.pushNode("script");
				m.addNode("id",MonitorUtils.hexString(id));
				m.addNode("order",dbo.get("order"));
				m.addNode("name",dbo.get("name"));
				
				String triggers=(String)dbo.get("triggers");
				m.pushNode("triggers",triggers);
				if (triggers!=null)
				{	String[] triggerList=Utils.splitString(triggers, ", ");
					for (String trigger : triggerList)
					{	m.addNode("trigger",trigger);
					}
				}
				m.popNode();

				String keywords=(String)dbo.get("keywords");
				m.pushNode("keywords",keywords);
				if (keywords!=null)
				{	String[] keywordList=Utils.splitString(keywords, ", ");
					for (String keyword : keywordList)
					{	m.addNode("keyword",keyword);
					}
				}
				m.popNode();

				
				m.popNode();
			}
		} finally
		{	cursor.close();
		}
		INKFResponse resp=aContext.createResponseFrom(m.toDocument(false));
		//resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		MonitorUtils.attachGoldenThread(aContext, GT_SCRIPT_LIST);
	}
}

