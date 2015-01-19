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

