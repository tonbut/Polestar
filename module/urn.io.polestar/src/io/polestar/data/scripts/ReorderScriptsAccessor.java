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

public class ReorderScriptsAccessor extends StandardAccessorImpl
{
	public ReorderScriptsAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String idString=aContext.source("arg:script",String.class);
		Long id=MonitorUtils.fromHexString(idString);
		Integer newPosition=aContext.source("arg:newPosition",Integer.class);
		
		DBCollection col = MongoUtils.getCollection("scripts");
		DBCursor cursor = col.find().sort(new BasicDBObject("order",1));
		try
		{	int n=0;
			while(cursor.hasNext())
			{	DBObject dbo=cursor.next();
				//Integer order=(Integer)dbo.get("order");
				Long id2=(Long)dbo.get("id");
				
				if (id2.equals(id))
				{	dbo.put("order", newPosition);
					//System.out.println(id2+" "+newPosition+" *");
				}
				else
				{	
					if (n==newPosition)
					{	n++;
					}
					//System.out.println(id2+" "+n);
					dbo.put("order", n);
					n++;
				}
					
				col.save(dbo);
			}
		} finally
		{	cursor.close();
		}
		MonitorUtils.cutGoldenThread(aContext, ListScriptsAccessor.GT_SCRIPT_LIST);

	}
}

