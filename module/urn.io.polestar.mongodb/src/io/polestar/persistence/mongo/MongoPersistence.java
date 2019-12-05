package io.polestar.persistence.mongo;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.layer0.representation.ByteArrayRepresentation;
import org.netkernel.layer0.representation.IBinaryStreamRepresentation;
import org.netkernel.layer0.util.Utils;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;

import io.polestar.data.api.ICollectionIterator;
import io.polestar.data.db.IPolestarPersistence;
import io.polestar.data.util.MonitorUtils;

public class MongoPersistence implements IPolestarPersistence
{
	public static final String LOG_COLLECTION="log";
	public static final String AUTH_COLLECTION="authentication";
	public static final String SCRIPT_COLLECTION="scripts";
	public static final String SCRIPT_EXEC_COLLECTION="scriptExec";
	public static final String SENSOR_ERRORS_COLLECTION="errors";
	public static final String SENSOR_COLLECTION_PREFIX="sensor:";
	public static final String SENSOR_STATE_COLLECTION="sensorState";
	
	
	public static final String ERROR_ID_MONGO_PERSISTENCE="MongoDB Persistence Layer Error";
	public static final String DBNAME="homemonitor";
	private MongoClient mMongoClient;
	
	DB getDB()
	{	return mMongoClient.getDB(DBNAME);
	}
	
	DBCollection getCollection(String aName) throws UnknownHostException
	{	return getDB().getCollection(aName);
	}
	
	DBCollection getCollectionForSensor(String aSensorId) throws UnknownHostException
	{	return getCollection(SENSOR_COLLECTION_PREFIX+aSensorId);
	}
	
	
	public MongoPersistence()
	{	System.out.println(Thread.currentThread().getContextClassLoader());
		mMongoClient = new MongoClient( "localhost" , 27017 );
	}
	
	@Override
	public void prime(INKFRequestContext aContext) throws NKFException
	{
		String msg="Priming MongoPersistence";
		aContext.logRaw(INKFLocale.LEVEL_INFO, msg);
		
		try
		{	
			DBCollection col;
			List<DBObject> indexes;
			
			/* prime log index */
			col=getCollection(LOG_COLLECTION);
			indexes=col.getIndexInfo();
			if (indexes.size()<2)
			{	col.createIndex(new BasicDBObject("t", 1));
			}
			
			/* prime script exec index */
			col=getCollection(SCRIPT_EXEC_COLLECTION);
			indexes=col.getIndexInfo();
			if (indexes.size()==0)
			{	col.createIndex(new BasicDBObject("id", 1));
			}
			
			/* prime authentication defaults */
			col=getCollection(AUTH_COLLECTION);
			DBCursor cursor = col.find();
			if (!cursor.hasNext())
			{	cursor.close();
				aContext.logRaw(INKFLocale.LEVEL_INFO,"Initialising authentication to defaults");
				initialiseAuthentication(aContext);
			}
			
			//index all sensors
			Set<String> collections=getDB().getCollectionNames();
			for (String collection : collections)
			{	try
				{	if (collection.startsWith("sensor:"))
					{	col=getCollection(collection);
						indexes=col.getIndexInfo();
						if (indexes.size()<2)
						{
							aContext.logRaw(INKFLocale.LEVEL_INFO, "Creating time index for "+collection);
							col.createIndex(new BasicDBObject("t", 1));
						}
					}
				}
				catch (Exception e)
				{	aContext.logRaw(INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
				}
			}
			
			//index errors
			try
			{	col=getCollection("errors");
				indexes=col.getIndexInfo();
				//System.out.println(indexes.size()+" indices on errors");
				if (indexes.size()<2)
				{	col.createIndex(new BasicDBObject("t", 1));
					col.createIndex(new BasicDBObject("i", 1));
				}
			}
			catch (Exception e)
			{	aContext.logRaw(INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
			}
			

		}
		catch (Exception e)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,e);
		}
	}
	
	@Override
	public void log(String aOrigin, int aLevel, String aMessage, INKFRequestContext aContext)
	{
		try
		{	DBCollection log=getCollection("log");
			long now=System.currentTimeMillis();			
			BasicDBObject sensor=new BasicDBObject();
			sensor.append("t", now);
			sensor.append("l", aLevel);
			sensor.append("o", aOrigin);
			sensor.append("m", aMessage);
			WriteResult wr=log.insert(sensor);
		
		}
		catch (UnknownHostException uhe)
		{	aContext.logRaw(INKFLocale.LEVEL_SEVERE, "Can't write to log: "+uhe.getMessage());
		}
	}
	
	@Override
	public IHDSDocument logQuery(long start, long length, String search, INKFRequestContext aContext) throws NKFException
	{
		try
		{
			DBCollection col=getCollection(LOG_COLLECTION);
			DBCursor cursor=col.find();
			int len=cursor.length();
			int filterLen=len;
			
			if (search==null || search.length()==0)
			{
				cursor=col.find().sort(new BasicDBObject("t",-1));
				cursor=cursor.skip((int)start).limit((int)length);
			}
			else
			{	cursor.close();
				BasicDBObject filter=new BasicDBObject("m", new BasicDBObject("$regex",search));
				cursor=col.find(filter).sort(new BasicDBObject("t",-1));
				filterLen=cursor.length();
				cursor=col.find(filter).sort(new BasicDBObject("t",-1));
				cursor=cursor.skip((int)start).limit((int)length);
				
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
			cursor.close();
			return m.toDocument(false);
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public int logRemoveOlderThan(long aTime, INKFRequestContext aContext) throws NKFException
	{
		try
		{	BasicDBObject query=new BasicDBObject("t", new BasicDBObject("$lt",aTime));
			DBCollection col=getCollection(LOG_COLLECTION);
			WriteResult result=col.remove(query);
			return result.getN();
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
		
	}
	
	
	
	
	
	
	
	
	
	@Override
	public IHDSDocument getAuthentication(INKFRequestContext aContext) throws NKFException
	{	try
		{	DBCollection col=getCollection(AUTH_COLLECTION);
			DBCursor cursor = col.find();
			DBObject o=cursor.next();
			IHDSMutator m=HDSFactory.newDocument();
			m.pushNode("authentication");
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
			return m.toDocument(false);
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public void setAuthentication(IHDSDocument aState, INKFRequestContext aContext) throws NKFException
	{	try
		{	DBCollection col=getCollection(AUTH_COLLECTION);
			BasicDBObject query = new BasicDBObject();
			BasicDBList update = new BasicDBList();
			for (IHDSReader user : aState.getReader().getNodes("/authentication/user"))
			{	BasicDBObject userItem = new BasicDBObject();
				userItem.append("username", user.getFirstValue("username"));
				userItem.append("password", user.getFirstValue("password"));
				userItem.append("role", user.getFirstValue("role"));
				update.add(userItem);
			}
			WriteResult wr=col.update(query, update,true,false);
			//return wr.getN()>0;
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
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
		
		setAuthentication(m.toDocument(false),aContext);
	}
	
	
	
	
	
	
	@Override
	public IHDSDocument getScriptList(INKFRequestContext aContext) throws NKFException
	{
		try
		{	
			DBCollection coll = getCollection(SCRIPT_COLLECTION);
			DBCursor cursor = coll.find().sort(new BasicDBObject("order",1));		
			
			IHDSMutator m=HDSFactory.newDocument();
			m.pushNode("scripts");
			try
			{	while(cursor.hasNext())
				{	DBObject dbo=cursor.next();
					
					Long id=(Long)dbo.get("id");
					//if (id==null) continue;
					m.pushNode("script");
					m.addNode("id",MDBUtils.hexString(id));
					m.addNode("order",dbo.get("order"));
					m.addNode("name",dbo.get("name"));
					m.addNode("target",dbo.get("target"));
					m.addNode("period",dbo.get("period"));
					m.addNode("public",dbo.get("public"));
					
					String triggers=(String)dbo.get("triggers");
					m.pushNode("triggers",triggers);
					if (triggers!=null)
					{	String[] triggerList=Utils.splitString(triggers, ", ");
						for (String trigger : triggerList)
						{	m.addNode("trigger",trigger);
						}
					}
					
					String period=(String)dbo.get("period");
					if (period!=null && period.length()>0 && !period.equals("None"))
					{
						long p=Long.parseLong(period);
						String ts=MDBUtils.formatPeriod(p);
						m.addNode("trigger",ts);
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
			return m.toDocument(false);
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public void setScriptPosition(long aScriptId, int aNewPosition, INKFRequestContext aContext) throws NKFException
	{
		try
		{
			DBCollection col = getCollection(SCRIPT_COLLECTION);
			DBCursor cursor = col.find().sort(new BasicDBObject("order",1));
			try
			{	int n=0;
				while(cursor.hasNext())
				{	DBObject dbo=cursor.next();
					Long id2=(Long)dbo.get("id");
					
					if (id2.equals(aScriptId))
					{	dbo.put("order", aNewPosition);
					}
					else
					{	
						if (n==aNewPosition)
						{	n++;
						}
						dbo.put("order", n);
						n++;
					}
						
					col.save(dbo);
				}
			} finally
			{	cursor.close();
			}
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}	
	}
	
	@Override
	public IHDSDocument getScript(long aScriptId, INKFRequestContext aContext) throws NKFException
	{
		try
		{
			DBCollection col=getCollection(SCRIPT_COLLECTION);
			BasicDBObject query = new BasicDBObject("id", aScriptId);
			DBCursor cursor = col.find(query);
			try
			{
				if(cursor.hasNext())
				{	DBObject dbo=cursor.next();
				
					IHDSMutator m=HDSFactory.newDocument();
					m.pushNode("script");
					m.addNode("id",MDBUtils.hexString(aScriptId));
					m.addNode("name",(String)dbo.get("name"));
					m.addNode("script",(String)dbo.get("script"));
					m.addNode("state",(String)dbo.get("state"));
					m.addNode("triggers",(String)dbo.get("triggers"));
					m.addNode("period",(String)dbo.get("period"));
					m.addNode("target",(String)dbo.get("target"));
					m.addNode("keywords",(String)dbo.get("keywords"));
					m.addNode("language",(String)dbo.get("language"));
					m.addNode("public",dbo.get("public"));
					m.popNode();
					return m.toDocument(false);
				}
				throw new NKFException("Script not found","with id="+aScriptId);
			}
			finally
			{	cursor.close();
			}
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public void setScript(long aScriptId, IHDSDocument aSaveState, INKFRequestContext aContext) throws NKFException
	{
		try
		{
			BasicDBObject query = new BasicDBObject("id", aScriptId);
			DBCollection col=getCollection("scripts");
			
			BasicDBObject update = new BasicDBObject();
			boolean needsUpdate=false;

			IHDSReader primary=aSaveState.getReader().getFirstNode("*");
			Object name=primary.getFirstValueOrNull("name");	
			if (name!=null)
			{	update.append("name", name);
				needsUpdate=true;
			}
			Object triggers=primary.getFirstValueOrNull("triggers");	
			if (triggers!=null)
			{	update.append("triggers", triggers);
				needsUpdate=true;
			}
			Object period=primary.getFirstValueOrNull("period");	
			if (period!=null)
			{	update.append("period", period);
				needsUpdate=true;
			}
			Object target=primary.getFirstValueOrNull("target");	
			if (target!=null)
			{	update.append("target", target);
				needsUpdate=true;
			}
			Object keywords=primary.getFirstValueOrNull("keywords");	
			if (keywords!=null)
			{	update.append("keywords", keywords);
				needsUpdate=true;
			}
			Object language=primary.getFirstValueOrNull("language");
			if (language!=null)
			{	update.append("language", language);
				needsUpdate=true;
			}
			Object isPublic=primary.getFirstValueOrNull("public");	
			if (isPublic!=null)
			{	update.append("public", isPublic);
				needsUpdate=true;
			}
			Object script=primary.getFirstValueOrNull("script");
			if (script!=null)
			{	update.append("script", script);
				needsUpdate=true;
			}
			Object state=primary.getFirstValueOrNull("state");
			if (state!=null)
			{	update.append("state", state);
				needsUpdate=true;
			}
			
			if (needsUpdate)
			{	BasicDBObject set = new BasicDBObject("$set",update);
				WriteResult wr=col.update(query, set);
			}
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public void addScript(IHDSDocument aNewState, INKFRequestContext aContext) throws NKFException
	{
		try
		{
			IHDSReader r=aNewState.getReader().getFirstNode("/script");
			BasicDBObject doc = new BasicDBObject("name", r.getFirstValue("name"));
			doc.append("id", r.getFirstValue("id"));
			doc.append("triggers", r.getFirstValue("triggers"));
			doc.append("keywords", r.getFirstValue("keywords"));
			doc.append("script", r.getFirstValue("script"));
			doc.append("state", r.getFirstValue("state"));
			doc.append("public", r.getFirstValue("public"));
			doc.append("order", r.getFirstValue("order"));
			
			DBCollection col=getCollection(SCRIPT_COLLECTION);
			col.insert(doc);
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public boolean deleteScript(long aScriptId, INKFRequestContext aContext) throws NKFException
	{
		try
		{	BasicDBObject query = new BasicDBObject("id", aScriptId);
			DBCollection col=getCollection(SCRIPT_COLLECTION);
			WriteResult wr=col.remove(query);
			return wr.getN()>0;
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public Long findScriptWithName(String aName, INKFRequestContext aContext) throws NKFException
	{
		try
		{	DBCollection col=getCollection(SCRIPT_COLLECTION);
			BasicDBObject query = new BasicDBObject("name", new BasicDBObject("$regex",".*"+aName+".*"));
			DBCursor cursor = col.find(query).limit(1);
			if (cursor.hasNext())
			{	DBObject dbo=cursor.next();
				return (Long)dbo.get("id");
			}
			return null;
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public void resetScriptStats(INKFRequestContext aContext) throws NKFException
	{
		try
		{	DBCollection col=getCollection(SCRIPT_EXEC_COLLECTION);
			BasicDBObject query=new BasicDBObject();
			BasicDBObject state=new BasicDBObject();
			BasicDBObject set=new BasicDBObject();
			set.append("c", 0);
			set.append("ec",0);
			state.append("$set", set);
			WriteResult wr=col.update(query,state,false,true);
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public IHDSDocument getScriptStats(INKFRequestContext aContext) throws NKFException
	{
		try
		{	
			DBCollection col=getCollection(SCRIPT_EXEC_COLLECTION);
			DBCursor cursor = col.find();
			IHDSMutator m=HDSFactory.newDocument();
			m.pushNode("scripts");
			while (cursor.hasNext())
			{	DBObject dbo=cursor.next();
				String id=(String)dbo.get("id");
				
				Number c=(Number)dbo.get("c");
				if (c==null) c=Integer.valueOf(0);
				Number ec=(Number)dbo.get("ec");
				if (ec==null) ec=Integer.valueOf(0);
				float errorPercent=ec.floatValue()/c.floatValue();
				Long lastExec=(Long)dbo.get("t");
				Long lastError=(Long)dbo.get("et");
				String error=(String)dbo.get("e");
				Long lastEdited=(Long)dbo.get("ed");
				
				m.pushNode("script")
				.addNode("id", id)
				.addNode("count", c)
				.addNode("errors", ec)
				.addNode("errorPercent", errorPercent)
				.addNode("lastExecTime", lastExec)
				.addNode("lastErrorTime", lastError)
				.addNode("lastError", error)
				.addNode("lastEdited", lastEdited)
				.popNode();
			}
			return m.toDocument(false);
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public void updateScriptStats(long aScriptId, boolean aIsEdit, String aError, long aTime, INKFRequestContext aContext) throws NKFException
	{
		try
		{	
			String hexId=MDBUtils.hexString(aScriptId);
			DBCollection col=getCollection(SCRIPT_EXEC_COLLECTION);
			
			BasicDBList inO=new BasicDBList();
			inO.add(hexId);
			BasicDBObject query=new BasicDBObject("id", new BasicDBObject("$in",inO));
			
			//eq doesn't work in older versions of mongoDB
			//BasicDBObject query=new BasicDBObject("id", new BasicDBObject("$eq",id));
			
			BasicDBObject state=new BasicDBObject();
			state.append("$setOnInsert", new BasicDBObject("id",hexId));
			
			BasicDBObject set=new BasicDBObject();
			BasicDBObject inc=new BasicDBObject();
			
			if (aIsEdit)
			{	set.append("ed",aTime);
				state.append("$set", set);
			}
			else
			{	//execution
				set.append("t", aTime);
				inc.append("c",1);
				
				if (aError==null || aError.length()==0)
				{	//set.append("e",null);
				}
				else
				{	set.append("e",aError);
					set.append("et",aTime);
					inc.append("ec",1);
				}
				state.append("$set", set);
				state.append("$inc", inc);
			}
			WriteResult wr=col.update(query,state,true,false);
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
		
	}
	
	@Override
	public IHDSDocument getCurrentSensorState(INKFRequestContext aContext) throws NKFException
	{
		try
		{	BasicDBObject query = new BasicDBObject("id",0);
			DBCollection col=getCollection(SENSOR_STATE_COLLECTION);
			DBCursor cursor = col.find(query);
			try
			{	if(cursor.hasNext())
				{	DBObject dbo=cursor.next();	
					byte[] hds=(byte[])dbo.get("hds");
					IHDSDocument state=aContext.transrept(new ByteArrayRepresentation(hds), IHDSDocument.class);
					return state;
				}
			} finally
			{	cursor.close();
			}
			return null;
		}
		catch (UnknownHostException uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public void setCurrentSensorState(IHDSDocument aNewState, INKFRequestContext aContext) throws NKFException
	{
		try
		{	IBinaryStreamRepresentation bs=aContext.transrept(aNewState, IBinaryStreamRepresentation.class);
			ByteArrayOutputStream baos=new ByteArrayOutputStream(4096);
			bs.write(baos);
			baos.flush();
			DBCollection col=getCollection(SENSOR_STATE_COLLECTION);
			BasicDBObject o=new BasicDBObject("hds",baos.toByteArray());
			o.append("id", 0);
			BasicDBObject set = new BasicDBObject("$set",o);
			BasicDBObject query = new BasicDBObject("id",0);
			WriteResult wr=col.update(query, set, true, false);
		}
		catch (Exception uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public void deleteSensor(String aSensorName, INKFRequestContext aContext) throws NKFException
	{	try
		{	DBCollection col=getCollectionForSensor(aSensorName);
			col.remove(new BasicDBObject());
		}
		catch (Exception uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public IHDSDocument getSensorInfo(INKFRequestContext aContext) throws NKFException
	{
		try
		{	
			IHDSMutator m=HDSFactory.newDocument();
			m.pushNode("sensors");
			Set<String> collections=getDB().getCollectionNames();
			for (String collection : collections)
			{
				DBCollection col=getCollection(collection);
				
				if (collection.startsWith("sensor:"))
				{	
					CommandResult cr=col.getStats();
					Long count=cr.getLong("count");
					Long size=cr.getLong("size");

					long first=-1;
					long last=-1;
					DBCursor cursor;
					
					try
					{
						cursor = col.find().sort(new BasicDBObject("t",1)).limit(1);
						if (cursor.hasNext())
						{	DBObject entry=cursor.next();
							first=(Long)entry.get("t");
						}
						cursor = col.find().sort(new BasicDBObject("t",-1)).limit(1);
						if (cursor.hasNext())
						{	DBObject entry=cursor.next();
							last=(Long)entry.get("t");
						}
					}
					catch (Exception e)
					{
					}
						
					String id=collection.substring(7);
					
					DateFormat df=DateFormat.getDateInstance(DateFormat.SHORT);
					String firstString=first>0?df.format(new Date(first)):"none";
					String lastString=last>0?df.format(new Date(last)):"none";
					long avgSize=count>0?size/count:0L;
					m.pushNode("sensor")
					.addNode("id",id)
					.addNode("count", count)
					.addNode("size", size)
					.addNode("avgSize", avgSize)
					.addNode("first", firstString)
					.addNode("last", lastString)
					.addNode("firstraw", first)
					.addNode("lastraw", last)
					.popNode();
				}
			}
			return m.toDocument(false);
		}
		catch (Exception uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
		
	}
	
	@Override
	public boolean setSensorValue(String aSensorId, Object aNewValue, long aUpdateTime, Long aUpdateWindow, INKFRequestContext aContext)  throws NKFException
	{
		try
		{	
			boolean result=false;
			DBCollection col=getCollectionForSensor(aSensorId);
			if (aUpdateWindow!=null)
			{
				Long existingTime=findExistingSensorValueAtTime(aUpdateTime,aUpdateWindow,col);
				if (existingTime==null)
				{	storeSensorState(aSensorId,aNewValue,aUpdateTime,col,aContext);
				}
				else
				{	replaceSensorState(aSensorId,aNewValue,existingTime,col,aContext);
					result=true;
				}
			}
			else
			{	storeSensorState(aSensorId,aNewValue,aUpdateTime,col,aContext);
			}
			return result;
		}
		catch (Exception uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
		
	}
	
	private static Long findExistingSensorValueAtTime(Long aTime, Long aWindow, DBCollection aCol) throws Exception
	{
		long start=aTime-aWindow;
		long end=aTime+aWindow;
		BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",start));
		BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",end));
		BasicDBList listO=new BasicDBList();
		listO.add(startO);
		listO.add(endO);
		BasicDBObject queryO=new BasicDBObject("$and", listO);
		DBCursor cursor = aCol.find(queryO);
		Long existingTime=null;
		if (cursor.hasNext())
		{	DBObject capture=cursor.next();
			if (cursor.hasNext())
			{	throw new NKFException("Multiple sensor values within window");
			}
			existingTime=(Long)capture.get("t");		
		}	
		return existingTime;
	}
	
	
	private static void storeSensorState(String aId, Object aValue, long aNow, DBCollection aCol, INKFRequestContext aContext) throws Exception
	{
		if (aValue!=null)
		{	Class c=aValue.getClass();
			if (c==String.class||c==Boolean.class||c==Integer.class||c==Long.class||c==Float.class||c==Double.class||Map.class.isAssignableFrom(c))
			{	
				BasicDBObject sensor=new BasicDBObject();
				sensor.append("t", aNow);
				sensor.append("v", aValue);
				//DBCollection col=MongoUtils.getCollectionForSensor(aId);
				WriteResult wr=aCol.insert(sensor);
			}
			else
			{	String msg=String.format("Unsupported datatype for %s of %s",aId,c.getName());
				aContext.logRaw(INKFLocale.LEVEL_WARNING, msg);
			}
		}
	}
	
	private static void replaceSensorState(String aId, Object aValue, long aTime, DBCollection aCol, INKFRequestContext aContext) throws Exception
	{
		if (aValue!=null)
		{	Class c=aValue.getClass();
			if (c==String.class||c==Boolean.class||c==Integer.class||c==Long.class||c==Float.class||c==Double.class||Map.class.isAssignableFrom(c))
			{	
				BasicDBObject sensor=new BasicDBObject();
				sensor.append("t", aTime);
				sensor.append("v", aValue);
				
				//$eq doesn't work on earlier versions
				//BasicDBObject query=new BasicDBObject("t", new BasicDBObject("$eq",aTime));
				
				BasicDBList inO=new BasicDBList();
				inO.add(aTime);
				BasicDBObject query=new BasicDBObject("t", new BasicDBObject("$in",inO));
				
				//DBCollection col=MongoUtils.getCollectionForSensor(aId);
				WriteResult wr=aCol.update(query, sensor);
			}
			else
			{	String msg=String.format("Unsupported datatype for %s of %s",aId,c.getName());
				aContext.logRaw(INKFLocale.LEVEL_WARNING, msg);
			}
		}
	}
	
	@Override
	public void setSensorError(String aSensorId, String aError, int aLevel, long aTime, INKFRequestContext aContext) throws NKFException
	{
		try
		{	BasicDBObject sensor=new BasicDBObject();
			sensor.append("t", aTime);
			sensor.append("i", aSensorId);
			sensor.append("l", aLevel);
			sensor.append("m", aError);
			DBCollection col=getCollection("errors");
			WriteResult wr=col.insert(sensor);
		}
		catch (Exception uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public IHDSDocument getSensorErrorSummary(String aSensorId, long aStartTime, INKFRequestContext aContext) throws NKFException
	{
		try
		{	long now=System.currentTimeMillis();
			long tt=now-aStartTime;
			DBCollection col=getCollection(SENSOR_ERRORS_COLLECTION);
			BasicDBObject queryO=createErrorQuery(aSensorId,aStartTime,"$lt");
			DBCursor cursor = col.find(queryO).sort(new BasicDBObject("t",-1)).limit(1);
			int lastLevel=0;
			long lastTimeD=aStartTime;
			long lastTime=aStartTime;
			String lastMsg=null;
			int errorCount=0;
			
			
			long tp=0;
			
			String lastError=null;
			long lastErrorStart=0;
			long lastErrorEnd=0;
			if (cursor.hasNext())
			{	DBObject previous=cursor.next();
				lastLevel=(Integer)previous.get("l");
				lastMsg=(String)previous.get("m");
				lastTime=(Long)previous.get("t");
			}
			cursor.close();
			
			queryO=createErrorQuery(aSensorId,aStartTime,"$gte");
			cursor = col.find(queryO);
			while (cursor.hasNext())
			{	DBObject error=cursor.next();
				long t=(Long)error.get("t");
				int l=(Integer)error.get("l");
				long d=t-lastTimeD;
				if (lastLevel>0)
				{	tp+=d;
					lastErrorStart=lastTime;
					lastError=lastMsg;
					errorCount++;
				}
				else
				{	lastErrorEnd=lastTime;
				}
				//tt+=d;
				lastMsg=(String)error.get("m");
				lastLevel=l;
				lastTime=t;
				lastTimeD=t;
			}
			
			long d=now-lastTime;
			if (lastLevel>0)
			{	tp+=d;
				lastErrorStart=lastTime;
				lastError=lastMsg;
				errorCount++;
			}
			else
			{	lastErrorEnd=lastTime;
			}
			
			double r=((double)tp)/((double)tt);
			String currentError;
			long lastErrorDuration;
			if (lastErrorEnd<lastErrorStart)
			{
				currentError=lastError;
				lastErrorDuration=now-lastErrorStart;
			}
			else
			{	currentError=null;
				lastErrorDuration=lastErrorEnd-lastErrorStart;
			}
			long lastErrorCleared=(lastErrorEnd>lastErrorStart)?lastErrorEnd:-1L;
			
			IHDSMutator m=HDSFactory.newDocument();
			m.pushNode("sensor")
			.addNode("id", aSensorId)
			.addNode("errorPercent", r)
			.addNode("errorCount", errorCount)
			.addNode("currentError",currentError)
			.addNode("lastError",lastError)
			//.addNode("lastErrorRaised",MonitorUtils.formatPeriod(now-lastErrorStart))
			.addNode("lastErrorRaisedRaw",lastErrorStart)
			//.addNode("lastErrorCleared",lastErrorCleared>=0?MonitorUtils.formatPeriod(now-lastErrorCleared):"not cleared")
			.addNode("lastErrorClearedRaw",lastErrorCleared)
			.addNode("lastErrorDurationRaw",lastErrorDuration)
			//.addNode("lastErrorDuration",MonitorUtils.formatPeriod(lastErrorDuration))
			.addNode("errorDurationRaw",tp)
			//.addNode("errorDuration", MonitorUtils.formatPeriod(tp))
			.popNode();
			return m.toDocument(false);
			
		}
		catch (Exception uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	private static BasicDBObject createErrorQuery(String id, long start, String op)
	{
		BasicDBObject queryO;
		if (id!=null)
		{	//mongodb 2.4 doesn't support $eq
			BasicDBList inO=new BasicDBList();
			inO.add(id);
			BasicDBObject idEqualsO=new BasicDBObject("i", new BasicDBObject("$in",inO));
			BasicDBObject startO=new BasicDBObject("t", new BasicDBObject(op,start));
			BasicDBList listO=new BasicDBList();
			listO.add(idEqualsO);
			listO.add(startO);
			queryO=new BasicDBObject("$and", listO);
		}
		else
		{	queryO=new BasicDBObject("t", new BasicDBObject(op,start));
		}
		return queryO;
	}
	
	
	
	@Override
	public ICollectionIterator getSensorForwardIterator(String aSensorId, long aStart, long aEnd, String aFragment, INKFRequestContext aContext)  throws NKFException
	{	return MongoCollectionIterator.getSensorForwardIterator(aSensorId, aStart, aEnd, aFragment,this);
	}
	
	@Override
	public ICollectionIterator getErrorForwardIterator(String aSensorId, long aStart, long aEnd, INKFRequestContext aContext) throws NKFException
	{	return MongoCollectionIterator.getErrorForwardIterator(aSensorId, aStart, aEnd,this);
	}

	@Override
	public ICollectionIterator getSensorBackwardIterator(String aSensorId, long aStart, long aEnd, String aFragment, INKFRequestContext aContext)  throws NKFException
	{	return MongoCollectionIterator.getSensorBackwardIterator(aSensorId, aStart, aEnd, aFragment,this);
	}
	
	@Override
	public ICollectionIterator getErrorBackwardIterator(String aSensorId, long aStart, long aEnd, INKFRequestContext aContext) throws NKFException
	{	return MongoCollectionIterator.getErrorBackwardIterator(aSensorId, aStart, aEnd,this);
	}


	private String mStatusMessage="";
	private long mProgressTotal;
	private long mProgressNow;
	public static final String BACKUP_ERROR_KEY="_ERRORS_";
	
	@Override
	public IHDSDocument getSensorBackupRestoreStatus()
	{
		IHDSMutator m=HDSFactory.newDocument();
		m.addNode("msg", mStatusMessage)
			.addNode("progress", mProgressNow)
			.addNode("progressTotal", mProgressTotal);
		return m.toDocument(false);
	}
	@Override
	public IHDSDocument getSensorBackupInfo(long aStart, long aEnd, List<String> aSensorIds, INKFRequestContext aContext) throws NKFException
	{
		try
		{
			IHDSReader info=getSensorInfo(aContext).getReader();
			info.declareKey("byId", "/sensors/sensor", "id");
			IHDSMutator m=HDSFactory.newDocument();
			m.pushNode("backupInfo");
			long totalCount=0;
			long totalSize=0;
	
			for (String sensorId : aSensorIds)
			{
				DBObject query=MDBUtils.getQuery(aStart,aEnd);
				DBCursor cursor=getCollectionForSensor(sensorId).find(query);
				long count=cursor.count();
				IHDSReader r=info.getFirstNode("key('byId','"+sensorId+"')");
				long avgSize=(Long)r.getFirstValue("avgSize");
				long size=avgSize*count;
				//System.out.println(sensorId+" -> "+count);
				m.pushNode("sensor")
					.addNode("id", sensorId)
					.addNode("count", count)
					.addNode("size",size)
					.popNode();
				totalCount+=count;
				totalSize+=size;
			}
			
			DBObject errorQuery=MDBUtils.getQuery(aSensorIds, aStart, aEnd);
			DBCursor cursor=getCollection(SENSOR_ERRORS_COLLECTION).find(errorQuery);
			long errorCount=cursor.count();
			totalCount+=errorCount;
			long size=errorCount*256; //guess
			totalSize+=size;
			m.pushNode("sensor")
			.addNode("id", BACKUP_ERROR_KEY)
			.addNode("count", errorCount)
			.addNode("size",size) 
			.popNode();
			
			m.pushNode("totals")
				.addNode("count",totalCount)
				.addNode("size",totalSize)
				.popNode();
			
			mProgressTotal=totalCount;
			return m.toDocument(false);
		}
		catch (Exception uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	@Override
	public File sensorBackup(long aStart, long aEnd, List<String> aSensorIds, INKFRequestContext aContext) throws NKFException
	{
		FileOutputStream fos=null;
		File f=null;
		try
		{
			mProgressNow=0;
			mStatusMessage="";
			
			f=File.createTempFile("polestar_export", ".bin");
			fos=new FileOutputStream(f);
			GZIPOutputStream zos=new GZIPOutputStream(fos,1024);
			Writer w=new OutputStreamWriter(zos, "UTF-8");
			Pattern p=Pattern.compile("\"_id.*} ,");
			Matcher m=p.matcher("");
			
			for (String sensorId : aSensorIds)
			{
				DBObject query=MDBUtils.getQuery(aStart,aEnd);
				DBCursor cursor=getCollectionForSensor(sensorId).find(query);
	
				w.write("#");
				w.write(sensorId);
				w.write("\n");
				while (cursor.hasNext())
				{	DBObject capture=cursor.next();
					Long time=(Long)capture.get("t");
					Object v=capture.get("v");
					String json=JSON.serialize(capture);
					m.reset(json);
					json=m.replaceFirst("");
					w.write(json);
					w.write("\n");	
					mProgressNow++;
				}
			}
			
			//backup errors
			DBObject errorQuery=MDBUtils.getQuery(aSensorIds, aStart, aEnd);
			DBCursor cursor=getCollection("errors").find(errorQuery);
			w.write("#"+BACKUP_ERROR_KEY+"\n");
			while (cursor.hasNext())
			{	
				DBObject capture=cursor.next();
				String json=JSON.serialize(capture);
				m.reset(json);
				json=m.replaceFirst("");
				w.write(json);
				w.write("\n");
				mProgressNow++;
			}
	
			w.flush();
			zos.finish();
			fos.flush();
			fos.close();
			
			mStatusMessage="Backup successful";
			mProgressNow=mProgressTotal;
			return f;
		}
		catch (Exception e)
		{	
			mStatusMessage=e.getClass().getName()+": "+e.getMessage();
			mProgressNow=mProgressTotal;
			try
			{	if (fos!=null) fos.close();
			} catch (IOException e2) {;}
			if (f!=null) f.delete();
			throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,e);
		}	
		
	}
	
	@Override
	public IHDSDocument getSensorRestoreInfo(File aRestoreFile, INKFRequestContext aContext) throws NKFException
	{
		try
		{
			FileInputStream fis=new FileInputStream(aRestoreFile);
			GZIPInputStream zis=new GZIPInputStream(fis,1024);
			Reader r=new InputStreamReader(zis, "UTF-8");
			BufferedReader br=new BufferedReader(r);
			
			String line;
			long count=0;
			long total=0;
			long oldest=Long.MAX_VALUE;
			long newest=0L;
			String sensorId=null;
			IHDSMutator m=HDSFactory.newDocument();
			while ((line=br.readLine())!=null)
			{
				if (line.startsWith("#"))
				{	
					if (sensorId!=null)
					{
						m.pushNode("sensor")
							.addNode("id", sensorId)
							.addNode("count", count)
							.addNode("overlap", checkSensorDataOverlap(sensorId,oldest,newest))
							.popNode();
					}
					sensorId=line.substring(1);
					count=0;
				}
				else
				{	
					try
					{	DBObject capture=(DBObject)JSON.parse(line);
						long ts=(Long)capture.get("t");
						if (ts>newest) newest=ts;
						if (ts<oldest) oldest=ts;
					}
					catch (Exception e) {;}
					count++;
					total++;
				}
			}
			
			if (sensorId!=null)
			{	//output error state - which should be last
				m.pushNode("sensor")
					.addNode("id", sensorId)
					.addNode("count", count)
					.popNode();
				
				m.addNode("oldest", oldest)
					.addNode("newest", newest)
					.addNode("count", total);
				
			}
			
			mProgressTotal=total;
			fis.close();
			return m.toDocument(false);
		}
		catch (Exception uhe)
		{	throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,uhe);
		}
	}
	
	private boolean checkSensorDataOverlap(String aSensorId,long aStart, long aEnd) throws Exception
	{
		DBObject query=MDBUtils.getQuery(aStart,aEnd);
		DBCursor cursor=getCollectionForSensor(aSensorId).find(query);
		int count=cursor.count();
		return count>0;
	}
	
	@Override
	public void sensorRestore(File aRestoreFile, String aMode, long aStart, long aEnd, List<String> aSensors, INKFRequestContext aContext) throws NKFException
	{
		try
		{
			mProgressNow=0;
			mStatusMessage="";
		
			if (!aMode.equals("Replace"))
			{	throw new NKFException("mode not supported yet");
			}
			
			FileInputStream fis=new FileInputStream(aRestoreFile);
			GZIPInputStream zis=new GZIPInputStream(fis,1024);
			Reader r=new InputStreamReader(zis, "UTF-8");
			BufferedReader br=new BufferedReader(r);
			
			String line;
			String sensorId=null;
			DBCollection collection=null;
			boolean processErrors=false;
			boolean enableProcessing=false;
			while ((line=br.readLine())!=null)
			{
				if (line.startsWith("#"))
				{	sensorId=line.substring(1);
					if (sensorId.equals(BACKUP_ERROR_KEY))
					{
						deleteErrorDatainTimeRange(aSensors,aStart,aEnd);
						collection=getCollection("errors");
						processErrors=true;
					}
					else
					{	if (aSensors.contains(sensorId))
						{	deleteSensorDatainTimeRange(sensorId,aStart,aEnd);
							collection=getCollectionForSensor(sensorId);
							processErrors=false;
							enableProcessing=true;
						}
						else
						{	enableProcessing=false;
						}
					}
					
				}
				else if (!processErrors )
				{	
					if (enableProcessing)
					{
						try
						{
							DBObject capture=(DBObject)JSON.parse(line);
							Long t=(Long)capture.get("t");
							if (t>=aStart && t<=aEnd)
							{	BasicDBObject sensor=new BasicDBObject();
								sensor.append("t", t);
								sensor.append("v", capture.get("v"));
								WriteResult wr=collection.insert(sensor);
								//System.out.println("WRITE: "+sensorId+" "+capture+" "+wr.getN());
							}
						}
						catch (Exception e)
						{	MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, e.getMessage());
						}
					}
					mProgressNow++;
				}
				else if (processErrors)
				{
					DBObject error=(DBObject)JSON.parse(line);
					Long t=(Long)error.get("t");
					if (t>=aStart && t<=aEnd)
					{	WriteResult wr=collection.insert(error);
					}
					mProgressNow++;
				}
			}	
			
			mStatusMessage="Restore successful";
			mProgressNow=mProgressTotal;
		}
		catch (Throwable e)
		{	mStatusMessage=e.getClass().getName()+": "+e.getMessage();
			mProgressNow=mProgressTotal;
			throw new NKFException(ERROR_ID_MONGO_PERSISTENCE,null,e);
		}
	}
	
	private void deleteSensorDatainTimeRange(String aSensorId, long aFirst,long aLast) throws Exception
	{
		DBObject query=MDBUtils.getQuery(aFirst,aLast);
		WriteResult result=getCollectionForSensor(aSensorId).remove(query);
	}
	
	private void deleteErrorDatainTimeRange(List<String> aSensorList, long aFirst, long aLast) throws Exception
	{
		DBObject query=MDBUtils.getQuery(aSensorList, aFirst, aLast);
		WriteResult result=getCollection("errors").remove(query);
	}
	

}
