/* Copyright 2015 1060 Research Ltd

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package io.polestar.data.login;

import org.netkernel.layer0.nkf.*;
import org.netkernel.mod.hds.*;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

public class AuthenticationDataAccessor extends StandardAccessorImpl
{
	public AuthenticationDataAccessor()
	{	this.declareThreadSafe();
		this.declareInhibitCheckForBadExpirationOnMutableResource();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{	IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("authentication");
		DBCollection col=MongoUtils.getCollection("authentication");
		//col.drop(); //uncomment to reset
		DBCursor cursor = col.find();
		try
		{	if (!cursor.hasNext())
			{	cursor.close();
				//col.drop();
				MonitorUtils.log(aContext,null,INKFLocale.LEVEL_INFO,"Initialising authentication to defaults");
				initialiseAuthentication(aContext);
				cursor = col.find();
			}
				
			if (cursor.hasNext())
			{
				DBObject o=cursor.next();
				//System.out.println("query "+o.keySet());
				for (String k : o.keySet())
				{	if (!k.startsWith("_"))
					{	DBObject uo=(DBObject)o.get(k);
						m.pushNode("user");
						m.addNode("username",uo.get("username"));
						m.addNode("password",uo.get("password"));
						Object role=uo.get("role");
						if (role==null) role=uo.get("username");
						m.addNode("role",role);
						m.popNode();
					}
				}
			}
			
		}
		finally
		{	cursor.close();
		}
		INKFResponse resp=aContext.createResponseFrom(m.toDocument(false));
		MonitorUtils.attachGoldenThread(aContext, "gt:auth");

	}

	public void onSink(INKFRequestContext aContext) throws Exception
	{
		IHDSMutator m=aContext.source("res:/md/authentication",IHDSDocument.class).getMutableClone();
		IHDSReader primary=aContext.sourcePrimary(IHDSDocument.class).getReader();
		String username=(String)primary.getFirstValue("/*/username");
		String password=(String)primary.getFirstValue("/*/password");
		//System.out.println(primary);
		IHDSMutator user=m.getFirstNodeOrNull("/authentication/user[username='"+username+"']");
		if (user!=null)
		{	
			INKFRequest req=aContext.createRequest("active:generatePasswordHash");
			req.addArgumentByValue("password",password);
			req.setRepresentationClass(String.class);
			String hash=(String)aContext.issueRequest(req);
			user.setCursor("password").setValue(hash);
			
			innerSink(m, aContext);
			MonitorUtils.cutGoldenThread(aContext, "gt:auth");
		}
		//System.out.println(m);
	}
	
	private void initialiseAuthentication(INKFRequestContext aContext) throws Exception
	{
		INKFRequest req=aContext.createRequest("active:generatePasswordHash");
		req.addArgumentByValue("password", "password");
		String hash=(String)aContext.issueRequest(req);
		
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("authentication");
		m.pushNode("user");
		m.addNode("username", "admin");
		m.addNode("password", hash);
		m.addNode("role", "admin");
		m.popNode();
		m.pushNode("user");
		m.addNode("username", "guest");
		m.addNode("password", hash);
		m.addNode("role", "guest");
		m.popNode();
		
		innerSink(m,aContext);
	}
	
	private boolean innerSink(IHDSMutator aData, INKFRequestContext aContext) throws Exception
	{
		DBCollection col=MongoUtils.getCollection("authentication");
		BasicDBObject query = new BasicDBObject();
		BasicDBList update = new BasicDBList();
		for (IHDSMutator user : aData.getNodes("/authentication/user"))
		{	BasicDBObject userItem = new BasicDBObject();
			userItem.append("username", user.getFirstValue("username"));
			userItem.append("password", user.getFirstValue("password"));
			userItem.append("role", user.getFirstValue("role"));
			update.add(userItem);
		}
		WriteResult wr=col.update(query, update,true,false);
		return wr.getN()>0;
	}

		
	
}
