package io.polestar.data.poll;

import java.util.*;

import org.netkernel.layer0.nkf.*;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

public class SlowPollAccessor extends StandardAccessorImpl
{
	private Map<String,Object> mLastStored=Collections.EMPTY_MAP;
	public SlowPollAccessor()
	{	declareThreadSafe();
	}
	
	public void postCommission(INKFRequestContext aContext) throws Exception
	{	
		DBCollection col=MongoUtils.getCollection("capture");
		//if (indices.size()==0)
		{
			col.createIndex(new BasicDBObject("timestamp", 1));
			//col.dropIndex(new BasicDBObject("name", 1));
		}
		//List<DBObject> indices=col.getIndexInfo();
		//System.out.println(indices);
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{	//System.out.println("SlowPollAccessor");
		
		//detect errors for unchanging sensors 
		aContext.source("active:polestarSensorReadingCheck");
	
		//fire all slow poll scripts
		MonitorUtils.executeTriggeredScripts(Collections.singleton("5m"), true, aContext);
		
		//store sensor state
		IHDSReader sensors=aContext.source("active:polestarSensorState",IHDSDocument.class).getReader();
		//System.out.println(sensors);
		BasicDBList sensorsList = new BasicDBList();
		HashMap<String,Object> lastStored=new HashMap<String,Object>();
		long now=System.currentTimeMillis();
		final long FIVE_MINS=60000*5;
		for (IHDSReader sensorNode : sensors.getNodes("/sensors/sensor"))
		{	
			String id=(String)sensorNode.getFirstValue("id");
			Object value=sensorNode.getFirstValue("value");
			if (value!=null)
			{	Class c=value.getClass();
				if (c==String.class||c==Boolean.class||c==Integer.class||c==Long.class||c==Float.class||c==Double.class)
				{	
					if (c==Boolean.class)
					{	long lastModified=(Long)sensorNode.getFirstValue("lastModified");
						if (now-lastModified<FIVE_MINS)
						{	Object lastValue=mLastStored.get(id);
							if (value.equals(lastValue))
							{	//give a change in value if we have had one even if we have missed it
								value=value.equals(Boolean.TRUE)?Boolean.FALSE:Boolean.TRUE;
							}
						}
					}
					
					BasicDBObject sensor=new BasicDBObject();
					sensor.append("id", id);
					sensor.append("value", value);
					sensorsList.add(sensor);
					lastStored.put(id, value);
				}
				else
				{	String msg=String.format("Unsupported datatype for %s of %s",id,c.getName());
					aContext.logRaw(INKFLocale.LEVEL_WARNING, msg);
				}
			}
		}
		mLastStored=lastStored;
		BasicDBObject record=new BasicDBObject();
		record.append("time", System.currentTimeMillis());
		record.append("sensors", sensorsList);
		DBCollection col=MongoUtils.getCollection("capture");
		WriteResult wr=col.insert(record);
		
		//fire all capture scripts
		MonitorUtils.executeTriggeredScripts(Collections.singleton("capture"), true, aContext);
	}
}

