package io.polestar.data.db;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.urii.ParsedIdentifierImpl;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

public class DataMigrationAccessor extends StandardAccessorImpl
{
	public DataMigrationAccessor()
	{
		this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String type=aContext.getThisRequest().getArgumentValue("action");
		System.out.println(type);
		if (type.equals("import"))
		{	onImport(aContext);
		}
		else if (type.equals("cleanup"))
		{	onCleanup(aContext);
		}
	}
	
	public void onCleanup(INKFRequestContext aContext) throws Exception
	{
		DBCollection col=MongoUtils.getCollection("capture");
		col.drop();
	}
	
	public void onImport(INKFRequestContext aContext) throws Exception
	{
		DBCollection col=MongoUtils.getCollection("capture");
		DBCursor cursor = col.find();
		
		
		long rows=0;
		long sensors=0;
		
		
		Map<String,DBCollection> sensorIds=new HashMap();
		Map<String,Object> lastValues=new HashMap();
		Map<String,AtomicLong> countRead=new HashMap();
		Map<String,AtomicLong> countWrite=new HashMap();
		Map<String,Long> oldest=new HashMap();
		Map<String,Long> newest=new HashMap();
		
		while (cursor.hasNext())
		{	DBObject capture=cursor.next();
			BasicDBList list=(BasicDBList)capture.get("sensors");
			sensors+=list.size();
			Long time=(Long)capture.get("time");
			//if (rows==0) oldestTime=time;
			//newestTime=time;
			for (int i=0; i<list.size(); i++)
			{
				DBObject sensor=(DBObject)list.get(i);
				String id=(String)sensor.get("id");
				Object value=sensor.get("value");
				
				AtomicLong c=countRead.get(id);
				if (c==null) countRead.put(id, new AtomicLong(1));
				else c.incrementAndGet();
				
				Object lastValue=lastValues.get(id);
				if (lastValue==null || !lastValue.equals(value))
				{
					lastValues.put(id, value);
				
					DBCollection colOut=sensorIds.get(id);
					if (colOut==null)
					{
						oldest.put(id, time);
						colOut=MongoUtils.getCollectionForSensor(id);
						//delete existing
						WriteResult wr=colOut.remove(new BasicDBObject());
						System.out.println("deleted "+wr.getN()+" existing from "+id);
						
						sensorIds.put(id, colOut);
					}
					newest.put(id, time);
					c=countWrite.get(id);
					if (c==null) countWrite.put(id, new AtomicLong(1));
					else c.incrementAndGet();
					BasicDBObject record=new BasicDBObject();
					record.append("t", time);
					record.append("v", value);
					WriteResult wr=colOut.insert(record);
				}
				//System.out.print(id+" ");
			}
			//System.out.println();
			
			//long time=(Long)capture.get("time");
			rows++;
			
			if (rows%5000==0)
			{	System.out.println("Migrate progress: rows="+rows+" sensors="+sensors);
			}
			//if (rows>10000) break;
		}
		//System.out.println(sensorIds.size()+" "+sensorIds);
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("migrate");
		for (String id : countRead.keySet())
		{
			m.pushNode("sensor");
			m.addNode("id", id);
			m.addNode("start", new Date(oldest.get(id)).toString());
			m.addNode("end", new Date(newest.get(id)).toString());
			long read=countRead.get(id).get();
			long write=countWrite.get(id).get();
			m.addNode("read", read);
			m.addNode("write", write);
			m.addNode("ratio", ((double)write)/((double)read));
			m.popNode();
		}
		INKFResponse respOut=aContext.createResponseFrom(m.toDocument(false));
		respOut.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
}
