package io.polestar.data.sensors;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipOutputStream;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.layer0.urii.ParsedIdentifierImpl;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;

import io.polestar.data.db.MongoUtils;
import io.polestar.data.util.MonitorUtils;

public class SensorBackupAccessor extends StandardAccessorImpl
{
	public static final String BACKUP_ERROR_KEY="_ERRORS_";
	
	public SensorBackupAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String type=aContext.getThisRequest().getArgumentValue(ParsedIdentifierImpl.ARG_ACTIVE_TYPE);
		if (type.equals("polestarSensorBackupInfo"))
		{	onSensorBackupInfo(aContext);
		}
		else if (type.equals("polestarSensorBackup"))
		{	onSensorBackup(aContext);
		}
		else if (type.equals("polestarSensorRestore"))
		{	onSensorRestore(aContext);
		}
		else if (type.equals("polestarSensorRestoreInfo"))
		{	onSensorRestoreInfo(aContext);
		}
	}
	
	
	private BasicDBObject getQueryFor(long aStart, long aEnd) throws Exception
	{	BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",aStart));
		BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",aEnd));
		BasicDBList listO=new BasicDBList();
		listO.add(startO);
		listO.add(endO);
		BasicDBObject queryO=new BasicDBObject("$and", listO);
		return queryO;
	}
	
	private void deleteSensorDatainTimeRange(String aSensorId, long aFirst,long aLast) throws Exception
	{
		DBObject query=getQueryFor(aFirst,aLast);
		WriteResult result=MongoUtils.getCollectionForSensor(aSensorId).remove(query);
	}
	
	private void deleteErrorDatainTimeRange(List<String> aSensorList, long aFirst, long aLast) throws Exception
	{
		DBObject query=SensorErrorAccessor.getQuery(aSensorList, aFirst, aLast);
		WriteResult result=MongoUtils.getCollection("errors").remove(query);
	}
	
	
	public void onSensorRestore(INKFRequestContext aContext) throws Exception
	{
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader();
		String fileURI=(String)operator.getFirstValue("fileURI");
		File f=new File(URI.create(fileURI));
		String mode=(String)operator.getFirstValue("mode");
		//Replace Selective Add
		if (!mode.equals("Replace"))
		{	throw new NKFException("mode not supported yet");
		}
		long first=Long.parseLong((String)operator.getFirstValue("first"));
		long last=Long.parseLong((String)operator.getFirstValue("last"));
		//System.out.println(mode+" "+first+" "+last);
		
		FileInputStream fis=new FileInputStream(f);
		GZIPInputStream zis=new GZIPInputStream(fis,1024);
		Reader r=new InputStreamReader(zis, "UTF-8");
		BufferedReader br=new BufferedReader(r);
		
		String line;
		String sensorId=null;
		DBCollection collection=null;
		boolean processErrors=false;
		List<String> sensorList=new ArrayList<>();
		while ((line=br.readLine())!=null)
		{
			if (line.startsWith("#"))
			{	sensorId=line.substring(1);
				if (sensorId.equals(BACKUP_ERROR_KEY))
				{
					deleteErrorDatainTimeRange(sensorList,first,last);
					collection=MongoUtils.getCollection("errors");
					processErrors=true;
				}
				else
				{	sensorList.add(sensorId);
					deleteSensorDatainTimeRange(sensorId,first,last);
					collection=MongoUtils.getCollectionForSensor(sensorId);
					processErrors=false;
				}
				
			}
			else if (!processErrors)
			{	DBObject capture=(DBObject)JSON.parse(line);
				BasicDBObject sensor=new BasicDBObject();
				sensor.append("t", capture.get("t"));
				sensor.append("v", capture.get("v"));
				WriteResult wr=collection.insert(sensor);
				//System.out.println("WRITE: "+sensorId+" "+capture+" "+wr.getN());
			}
			else if (processErrors)
			{
				DBObject error=(DBObject)JSON.parse(line);
				WriteResult wr=collection.insert(error);
			}
		}
		
		//refresh sensors to show current state
		INKFRequest req=aContext.createRequest("active:polestarSensorStateRefresh");
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		for (String id : sensorList)
		{	m.addNode("sensor", id);
		}
		req.addArgumentByValue("state", m.toDocument(false));
		aContext.issueRequest(req);		
		
		
	}
	
	public void onSensorRestoreInfo(INKFRequestContext aContext) throws Exception
	{
		
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		
		String operator=aContext.getThisRequest().getArgumentValue("operator");
		//System.out.println("restore "+operator);
		File f=new File(URI.create(operator));
		
		FileInputStream fis=new FileInputStream(f);
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
					IHDSReader sensorDef=config.getFirstNodeOrNull(String.format("key('byId','"+sensorId+"')"));
					m.pushNode("sensor")
						.addNode("id", sensorId)
						.addNode("count", count)
						.addNode("overlap", checkSensorDataOverlap(sensorId,oldest,newest))
						.addNode("exists", sensorDef!=null)
						.popNode();
				}
				sensorId=line.substring(1);
				count=0;
			}
			else
			{	
				DBObject capture=(DBObject)JSON.parse(line);
				long ts=(Long)capture.get("t");
				if (ts>newest) newest=ts;
				if (ts<oldest) oldest=ts;
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
		
		
		fis.close();
		aContext.createResponseFrom(m.toDocument(false));
		
	}
	
	private boolean checkSensorDataOverlap(String aSensorId,long aStart, long aEnd) throws Exception
	{
		DBObject query=getQueryFor(aStart,aEnd);
		DBCursor cursor=MongoUtils.getCollectionForSensor(aSensorId).find(query);
		int count=cursor.count();
		//System.out.println("count for "+aSensorId+"="+count);
		return count>0;
	}
	
	
	public void onSensorBackup(INKFRequestContext aContext) throws Exception
	{
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader();
		long start=(Long)operator.getFirstValue("/*/start");
		long end=(Long)operator.getFirstValue("/*/end");
		
		File f=File.createTempFile("polestar_export", ".bin");
		FileOutputStream fos=new FileOutputStream(f);
		//BufferedOutputStream bos=new BufferedOutputStream(fos);
		GZIPOutputStream zos=new GZIPOutputStream(fos,1024);
		Writer w=new OutputStreamWriter(zos, "UTF-8");
		
		List<Object> ids=operator.getValues("/*/sensors/sensor");
		List<String> idss =new ArrayList(ids);
		
		Pattern p=Pattern.compile("\"_id.*} ,");
		Matcher m=p.matcher("");
		
		for (String sensorId : idss)
		{
			DBObject query=getQueryFor(start,end);
			DBCursor cursor=MongoUtils.getCollectionForSensor(sensorId).find(query);

			
			
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
			}
		}
		
		//backup errors
		DBObject errorQuery=SensorErrorAccessor.getQuery(idss, start, end);
		DBCursor cursor=MongoUtils.getCollection("errors").find(errorQuery);
		w.write("#"+BACKUP_ERROR_KEY+"\n");
		while (cursor.hasNext())
		{	
			DBObject capture=cursor.next();
			//Integer level=(Integer)capture.get("l");
			//String msg=(String)capture.get("m");
			//Long time=(Long)capture.get("t");
			//String id=(String)capture.get("i");
			String json=JSON.serialize(capture);
			m.reset(json);
			json=m.replaceFirst("");
			w.write(json);
			w.write("\n");
		}
		

		
		
		w.flush();
		zos.finish();
		fos.flush();
		fos.close();
		
		INKFResponse resp=aContext.createResponseFrom(f.toURI().toString());
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		
	}
	
	
	
	public void onSensorBackupInfo(INKFRequestContext aContext) throws Exception
	{
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader();
		long start=(Long)operator.getFirstValue("/*/start");
		long end=(Long)operator.getFirstValue("/*/end");

		IHDSReader info=aContext.source("active:polestarSensorInfo",IHDSDocument.class).getReader();
		
		//System.out.println(operator);
		
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("backupInfo");
		long totalCount=0;
		long totalSize=0;
		List<Object> ids=operator.getValues("/*/sensors/sensor");
		List<String> idss =new ArrayList(ids);
		for (String sensorId : idss)
		{
			DBObject query=getQueryFor(start,end);
			DBCursor cursor=MongoUtils.getCollectionForSensor(sensorId).find(query);
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
		
		DBObject errorQuery=SensorErrorAccessor.getQuery(idss, start, end);
		DBCursor cursor=MongoUtils.getCollection("errors").find(errorQuery);
		long errorCount=cursor.count();
		m.pushNode("sensor")
		.addNode("id", BACKUP_ERROR_KEY)
		.addNode("count", errorCount)
		.addNode("size",errorCount*256) //guess
		.popNode();
		
		m.pushNode("totals")
			.addNode("count",totalCount)
			.addNode("size",totalSize)
			.popNode();
		
		INKFResponse resp=aContext.createResponseFrom(m.toDocument(false));
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		
	}
	
}
