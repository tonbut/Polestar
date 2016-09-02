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
package io.polestar.data.poll;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.netkernel.layer0.nkf.*;
import org.netkernel.layer0.util.MultiMap;
import org.netkernel.layer0.util.PairList;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.mod.hds.impl.HDSMutatorImpl;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.db.MongoUtils;
import io.polestar.data.poll.MergeAction.Type;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

public class CaptureQueryAccessor extends StandardAccessorImpl
{
	//public enum MergeAction
	//{	AVERAGE, MAX, MIN, DIFF, SUM, ROTATION_360_AVERAGE	
	//}
	
	public CaptureQueryAccessor()
	{	declareThreadSafe();
	}
	
	
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{	
		//process input configuration
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader().getFirstNode("query");
		boolean jsonMode=operator.getFirstNodeOrNull("json")!=null;
		DateFormat df;
		String timeFormat=(String)operator.getFirstValueOrNull("timeFormat");
		if (timeFormat==null)
		{	df=new SimpleDateFormat("kk:mm");
		}
		else
		{	df=new SimpleDateFormat(timeFormat);
		}
		long now=System.currentTimeMillis();
		long start=now-1000*60*60*24;
		long end=now;
		int merge=1;
		Long startValue=(Long)operator.getFirstValueOrNull("start");
		if (startValue!=null)
		{	if (startValue<0)
			{	start=now+startValue;
			}
			else
			{	start=startValue;
			}
		}
		Long endValue=(Long)operator.getFirstValueOrNull("end");
		if (endValue!=null)
		{	if (endValue<0)
			{	end=now+endValue;
			}
			else
			{	end=endValue;
			}
		}
		Integer mergeValue=(Integer)operator.getFirstValueOrNull("merge");
		if (mergeValue!=null)
		{	merge=mergeValue;
		}
		
		//assume 5 minute samples
		long samplePeriod=1000L*60*5*merge;
		long nSamples=(end-start)/samplePeriod;
		
		
		//process sensors
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		List<IHDSReader> sensors=operator.getNodes("sensors/sensor");
		//Map<String, MergeAction> actions=new HashMap<String, MergeAction>();
		MultiMap actions=new MultiMap(16, 1);
		List<MergeAction> actionList=new ArrayList<MergeAction>();
		if (sensors.size()>0)
		{	int c=1;
			for (IHDSReader sensor : sensors)
			{	String id=(String)sensor.getFirstValue("id");
				String name=(String)sensor.getFirstValueOrNull("name");
				if (name==null)
				{	name="sensor"+(c++);
				}
				String format=(String)config.getFirstValueOrNull("key('byId','"+id+"')/format");
				if (format!=null && format.equals("rgb"))
				{	format="%.3f";
				}
				else if (format!=null && !format.contains("%"))
				{	format=null;
				}
				
				String mergeActionString=(String)sensor.getFirstValueOrNull("mergeAction");
				MergeAction mergeAction;
				if (mergeActionString==null)
				{	mergeAction=MergeAction.getMergeAction(id,name,format,MergeAction.Type.SAMPLE);
				}
				else
				{	Type mergeActionType=Type.valueOf(mergeActionString.toUpperCase());
					mergeAction=MergeAction.getMergeAction(id,name,format,mergeActionType);
				}
				actions.put(id, mergeAction);
				actionList.add(mergeAction);
			}
		}
		
		//perform query
		DBCollection col=MongoUtils.getCollection("capture");
		BasicDBObject startO=new BasicDBObject("time", new BasicDBObject("$gt",start));
		BasicDBObject endO=new BasicDBObject("time", new BasicDBObject("$lt",end));
		BasicDBList listO=new BasicDBList();
		listO.add(startO);
		listO.add(endO);
		BasicDBObject queryO=new BasicDBObject("$and", listO);
		DBCursor cursor = col.find(queryO);
		
		/*
		int size=cursor.size();
		int results=size/merge;
		if (results>0)
		{	int skip=size%merge;
			for (int i=0; i<skip; i++)
			{	cursor.next();
			}
			//System.out.println("size="+size+" results="+results+" skip="+skip);
		}
		*/
		
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("rows");
		StringBuilder sb=new StringBuilder(4096);
		sb.append("[ ");
		long lastSampleTime=start;
		
		//build result
		try
		{
			DBObject capture=null;
			for (long t=start; t<end; t+=samplePeriod)
			{	long sampleEndTime=t+samplePeriod;
				do
				{	
					if (capture==null)
					{	if (cursor.hasNext())
						{	capture=cursor.next();
						}
						else
						{	break;
						}
					}
					long time=(Long)capture.get("time");
					lastSampleTime=time;
					if (time<sampleEndTime)
					{
						//process merge action on sensors
						BasicDBList list=(BasicDBList)capture.get("sensors");
						for (int i=0; i<list.size(); i++)
						{	DBObject sensor=(DBObject)list.get(i);
							String id=(String)sensor.get("id");
							List<MergeAction> mas=actions.get(id);
							for (MergeAction ma : mas)
							{	Object v=sensor.get("value");
								try
								{	ma.update(v);
								}
								catch (Exception e)
								{ //ignore
								}
							}
						}
						capture=null;
					}
					else
					{	break; //move into next sample
					}
				} while(true);
				
				//now create a record
				if (jsonMode)
				{	sb.append("[ ");
					long time=t;
					sb.append(time);
					sb.append(", '");
					sb.append(df.format(new Date(time)));
					sb.append("', ");
					int cc=0;
					for (MergeAction ma : actionList)
					{	if (cc++!=0) sb.append(", ");
						Object v=ma.getValue();
						if (v==null)
						{	sb.append("null");
						}
						else if (v instanceof Map)
						{	String format=ma.getFormat();
							Map<String,Object> bdo=(Map)v;
							sb.append("{");
							for (Map.Entry<String, Object> entry : bdo.entrySet())
							{	sb.append("\"");
								sb.append(entry.getKey());
								sb.append("\": ");
								outputValue(sb,entry.getValue(),format);
								sb.append(", ");
							}
							sb.append("}");
						}
						else
						{	String format=ma.getFormat();
							outputValue(sb,v,format);
							
						}
					}
					
					sb.append("],\n");
				}
				else
				{	
					m.pushNode("row");
					m.addNode("time",lastSampleTime);
					m.addNode("timeString",df.format(new Date(lastSampleTime)));
					for (MergeAction ma : actionList)
					{	Object v=ma.getValue();
						if (v instanceof BasicDBObject)
						{	BasicDBObject bdo=(BasicDBObject)v;
							m.pushNode(ma.getName());
							for (Map.Entry<String, Object> entry : bdo.entrySet())
							{	m.addNode(entry.getKey(), entry.getValue());
							}
							m.popNode();
						}
						else if (v instanceof Map)
						{	Map<String,Object> bdo=(Map)v;
							m.pushNode(ma.getName());
							for (Map.Entry<String, Object> entry : bdo.entrySet())
							{	m.addNode(entry.getKey(), entry.getValue());
							}
							m.popNode();
						}
						else
						{	m.addNode(ma.getName(), v);
						}
					}
					m.popNode();
				}
			}				
		
		
		} finally
		{	cursor.close();
		}
		
		if (!jsonMode)
		{	INKFResponse resp=aContext.createResponseFrom(m.toDocument(false));
			resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		}
		else
		{	sb.append(" ]");
			INKFResponse resp=aContext.createResponseFrom(sb.toString());
			resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		}
		//System.out.println("created "+n+" samples");
	}
	
	public void onSource2(INKFRequestContext aContext) throws Exception
	{	
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader().getFirstNode("query");
		long now=System.currentTimeMillis();
		long start=now-1000*60*60*24;
		long end=now;
		int merge=1;
		Long startValue=(Long)operator.getFirstValueOrNull("start");
		if (startValue!=null)
		{	if (startValue<0)
			{	start=now+startValue;
			}
			else
			{	start=startValue;
			}
		}
		Long endValue=(Long)operator.getFirstValueOrNull("end");
		if (endValue!=null)
		{	if (endValue<0)
			{	end=now+endValue;
			}
			else
			{	end=endValue;
			}
		}
		Integer mergeValue=(Integer)operator.getFirstValueOrNull("merge");
		if (mergeValue!=null)
		{	merge=mergeValue;
		}
		
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		List<IHDSReader> sensors=operator.getNodes("sensors/sensor");
		//Map<String, MergeAction> actions=new HashMap<String, MergeAction>();
		MultiMap actions=new MultiMap(16, 1);
		List<MergeAction> actionList=new ArrayList<MergeAction>();
		if (sensors.size()>0)
		{	int c=1;
			for (IHDSReader sensor : sensors)
			{	String id=(String)sensor.getFirstValue("id");
				String name=(String)sensor.getFirstValueOrNull("name");
				if (name==null)
				{	name="sensor"+(c++);
				}
				String format=(String)config.getFirstValueOrNull("key('byId','"+id+"')/format");
				if (format!=null && format.equals("rgb"))
				{	format="%.3f";
				}
				else if (format!=null && !format.contains("%"))
				{	format=null;
				}
				
				String mergeActionString=(String)sensor.getFirstValueOrNull("mergeAction");
				MergeAction mergeAction;
				if (mergeActionString==null)
				{	mergeAction=MergeAction.getMergeAction(id,name,format,MergeAction.Type.SAMPLE);
				}
				else
				{	Type mergeActionType=Type.valueOf(mergeActionString.toUpperCase());
					mergeAction=MergeAction.getMergeAction(id,name,format,mergeActionType);
				}
				actions.put(id, mergeAction);
				actionList.add(mergeAction);
			}
		}
		
		DBCollection col=MongoUtils.getCollection("capture");
		BasicDBObject startO=new BasicDBObject("time", new BasicDBObject("$gt",start));
		BasicDBObject endO=new BasicDBObject("time", new BasicDBObject("$lt",end));
		BasicDBList listO=new BasicDBList();
		listO.add(startO);
		listO.add(endO);
		BasicDBObject queryO=new BasicDBObject("$and", listO);
		DBCursor cursor = col.find(queryO);
		int size=cursor.size();
		int results=size/merge;
		if (results>0)
		{	int skip=size%merge;
			for (int i=0; i<skip; i++)
			{	cursor.next();
			}
			//System.out.println("size="+size+" results="+results+" skip="+skip);
		}
		
		boolean jsonMode=operator.getFirstNodeOrNull("json")!=null;
		DateFormat df;
		String timeFormat=(String)operator.getFirstValueOrNull("timeFormat");
		if (timeFormat==null)
		{	df=new SimpleDateFormat("kk:mm");
		}
		else
		{	df=new SimpleDateFormat(timeFormat);
		}
		
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("rows");
		StringBuilder sb=new StringBuilder(4096);
		sb.append("[ ");
		
		try
		{	int c=0;
			while(cursor.hasNext())
			{	DBObject capture=cursor.next();
			
				BasicDBList list=(BasicDBList)capture.get("sensors");
				for (int i=0; i<list.size(); i++)
				{	DBObject sensor=(DBObject)list.get(i);
					String id=(String)sensor.get("id");
					//MergeAction ma=actions.get(id);
					List<MergeAction> mas=actions.get(id);
					for (MergeAction ma : mas)
					{	Object v=sensor.get("value");
						try
						{	ma.update(v);
						}
						catch (Exception e)
						{ //ignore
						}
					}
				}
				
				int c2=(++c)%merge;
				if (c2==0 || (results==0 && !cursor.hasNext()))
				{	
					if (jsonMode)
					{	sb.append("[ ");
						long time=(Long)capture.get("time");
						sb.append(time);
						sb.append(", '");
						sb.append(df.format(new Date(time)));
						sb.append("', ");
						int cc=0;
						for (MergeAction ma : actionList)
						{	if (cc++!=0) sb.append(", ");
							Object v=ma.getValue();
							if (v==null)
							{	sb.append("null");
							}
							else if (v instanceof Map)
							{	String format=ma.getFormat();
								Map<String,Object> bdo=(Map)v;
								sb.append("{");
								for (Map.Entry<String, Object> entry : bdo.entrySet())
								{	sb.append("\"");
									sb.append(entry.getKey());
									sb.append("\": ");
									outputValue(sb,entry.getValue(),format);
									sb.append(", ");
								}
								sb.append("}");
							}
							else
							{	String format=ma.getFormat();
								outputValue(sb,v,format);
								
							}
						}
						
						sb.append("],\n");
					}
					else
					{	m.pushNode("row");
						long time=(Long)capture.get("time");
						m.addNode("time",time);
						m.addNode("timeString",df.format(new Date(time)));
						for (MergeAction ma : actionList)
						{	Object v=ma.getValue();
							if (v instanceof BasicDBObject)
							{	BasicDBObject bdo=(BasicDBObject)v;
								m.pushNode(ma.getName());
								for (Map.Entry<String, Object> entry : bdo.entrySet())
								{	m.addNode(entry.getKey(), entry.getValue());
								}
								m.popNode();
							}
							else if (v instanceof Map)
							{	Map<String,Object> bdo=(Map)v;
								m.pushNode(ma.getName());
								for (Map.Entry<String, Object> entry : bdo.entrySet())
								{	m.addNode(entry.getKey(), entry.getValue());
								}
								m.popNode();
							}
							else
							{	m.addNode(ma.getName(), v);
							}
						}
						m.popNode();
					}
				}
			}
		} finally
		{	cursor.close();
		}
		
		if (!jsonMode)
		{	INKFResponse resp=aContext.createResponseFrom(m.toDocument(false));
			resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		}
		else
		{	sb.append(" ]");
			INKFResponse resp=aContext.createResponseFrom(sb.toString());
			resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		}
		
	}
	
	private void outputValue(StringBuilder sb, Object v, String format)
	{
		boolean needsQuotes=(v instanceof String);
		if (needsQuotes) sb.append("'");
		if (format==null)
		{	sb.append(v);
		}
		else
		{	String sf;
			try
			{	sf=String.format(format, v);
			} catch (Exception e)
			{	sf=v.toString();
			}
			sb.append(sf);
		}
		
		if (needsQuotes) sb.append("'");
	}
}

