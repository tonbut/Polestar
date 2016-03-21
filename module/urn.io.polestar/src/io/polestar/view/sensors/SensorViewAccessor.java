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
package io.polestar.view.sensors;

import java.util.IllegalFormatConversionException;
import java.util.Map;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFRequestReadOnly;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.layer0.util.Utils;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import com.mongodb.BasicDBObject;

import io.polestar.data.util.MonitorUtils;
import io.polestar.view.template.TemplateWrapper;

public class SensorViewAccessor extends StandardAccessorImpl
{
	public SensorViewAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		INKFRequestReadOnly req=aContext.getThisRequest();
		String action=null, id=null;
		if (req.argumentExists("action"))
		{	action=req.getArgumentValue("action");
			if (req.argumentExists("id"))
			{	id=req.getArgumentValue("id");
			}
		}
		if (action==null)
		{	onList(aContext);
		}
		else
		{	if (action.equals("filtered"))
			{	onFilteredList(aContext);
			}
			else if (action.equals("ticker"))
			{	onTicker(aContext);
			}
		}
	}
	
	public void onTicker(INKFRequestContext aContext) throws Exception
	{
		//System.out.println("ticker");
		
		IHDSReader state=aContext.source("active:polestarSensorState",IHDSDocument.class).getReader();
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		
		Long period=null;
		Integer merge=null;
		try
		{	IHDSReader mconf=aContext.source("res:/md/execute/named/Configuration",IHDSDocument.class).getReader();
			Object periodValue=mconf.getFirstValueOrNull("sensor-ticker/period");
			if (periodValue!=null) period=Long.parseLong(periodValue.toString());
			Object mergeValue=mconf.getFirstValueOrNull("sensor-ticker/merge");
			if (mergeValue!=null) merge=Integer.parseInt(mergeValue.toString());
		}
		catch (Exception e)
		{	//e.printStackTrace();
		}
		if (period==null) period=1000L*60*60*24;
		if (merge==null) merge=6;
		
		//build query config
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("query");
		m.addNode("start",-period);
		m.addNode("merge",merge);
		m.addNode("json","");
		m.pushNode("sensors");
		
		StringBuilder sb=new StringBuilder();
		sb.append("[");
		for (IHDSReader sensor : config.getNodes("/sensors/sensor"))
		{	Object id=sensor.getFirstValue("id");
			Object value=state.getFirstValue("key('byId','"+id+"')/value");
			String mergeAction="sample";
			if (value!=null && (value instanceof Float || value instanceof Double))
			{	mergeAction="average";
			}
			if (value!=null && (value instanceof Map))
			{	mergeAction="average_map";
			}
			
			String format=(String)sensor.getFirstValueOrNull("format");
			if ("count".equals(format))
			{	mergeAction="diff";
			}
			if (value!=null && value instanceof Boolean)
			{	mergeAction="boolean_change";
			}
			sb.append(" '").append(id).append("',");
			//System.out.println(id+" "+value);
			m.pushNode("sensor").addNode("id",id).addNode("mergeAction",mergeAction).popNode();
		}
		sb.append(" ]");
		m.popNode();
		
		
		INKFRequest req=aContext.createRequest("active:polestarHistoricalQuery");
		req.addArgumentByValue("operator",m.toDocument(false));
		req.setRepresentationClass(String.class);
		String data=(String)aContext.issueRequest(req);
		//System.out.println(data);
		
		String s=aContext.source("res:/io/polestar/view/sensors/ticker.xml",String.class);
		s=s.replace("%ID%",sb.toString());
		s=s.replace("%D%",data);
		
		INKFResponse resp=aContext.createResponseFrom(s);
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		//resp.setExpiry(INKFResponse.EXPIRY_CONSTANT,System.currentTimeMillis()+120000L);
		//resp.setMimeType("text/plain");
	}
	
	
	private IHDSMutator getFilteredList(String aFilter, INKFRequestContext aContext) throws Exception
	{	long now=System.currentTimeMillis();
		IHDSMutator state=aContext.source("active:polestarSensorState",IHDSDocument.class).getMutableClone();
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		boolean isError=aFilter.indexOf("error")>=0;
		for (IHDSMutator sensorNode : state.getNodes("/sensors/sensor"))
		{	
			String id=(String)sensorNode.getFirstValue("id");
			IHDSReader sensorDef=config.getFirstNodeOrNull(String.format("key('byId','"+id+"')"));
			
			boolean include;
			if (aFilter.length()>0)
			{	include=false;
				String name=(String)sensorDef.getFirstValue("name");
				if (name.toLowerCase().contains(aFilter))
				{	include=true;
				}
				String keywords=(String)sensorDef.getFirstValueOrNull("keywords");
				if (keywords!=null && keywords.toLowerCase().contains(aFilter))
				{	include=true;
				}
				if (isError && sensorNode.getFirstNodeOrNull("error")!=null)
				{	include=true;
				}
			}
			else
			{	include=true;
			}
			
			if (!include)
			{	sensorNode.delete();
			}
			else
			{	sensorNode.pushNode("defn");
				sensorNode.appendChildren(sensorDef);
				sensorNode.popNode();
				
				String keywords=(String)sensorDef.getFirstValueOrNull("keywords");
				if (keywords!=null)
				{	String[] kws=Utils.splitString(keywords, ", ");
					sensorNode.pushNode("keywordList");
					for (String kw : kws)
					{	sensorNode.addNode("keyword",kw);
					}
					sensorNode.popNode();
				}
				
				String period;
				long lastUpdated=(Long)sensorNode.getFirstValue("lastUpdated");
				if (lastUpdated==0)
				{	period="Never";
				}
				else
				{	period=MonitorUtils.formatPeriod(now-lastUpdated)+" ago";
				}
				sensorNode.addNode("lastUpdatedHuman", period);
				long lastModified=(Long)sensorNode.getFirstValue("lastModified");
				if (lastModified==0)
				{	period="Never";
				}
				else
				{	period=MonitorUtils.formatPeriod(now-lastModified)+" ago";
				}
				sensorNode.addNode("lastModifiedHuman", period);
				
				String format=(String)sensorDef.getFirstValueOrNull("format");
				Object value=sensorNode.getFirstValue("value");
				String valueHuman;
				String valueHTML=null;
				try
				{
					if (format!=null && value!=null)
					{	if ("open".equals(format))
						{	valueHuman=((Boolean)value)?"open":"closed";
						}
						else if ("on".equals(format))
						{	valueHuman=((Boolean)value)?"on":"off";
						}
						else if ("rgb".equals(format))
						{	valueHuman=null;
							Map m=(Map)value;
							double r=((Number)m.get("r")).doubleValue();
							double g=((Number)m.get("g")).doubleValue();
							double b=((Number)m.get("b")).doubleValue();
							String rgb="rgb("+((int)(r*255.9))+","+((int)(g*255.9))+","+((int)(b*255.9))+")";
							valueHTML="<div class='rgb' style='background: "+rgb+"'/>";
						}
						else if (format.contains("%"))
						{	
							if (value instanceof Number)
							{
								try
								{	valueHuman=String.format(format,value);
								} catch (IllegalFormatConversionException e)
								{	valueHuman=value.toString();
								}
							}
							else if (value instanceof Map)
							{	StringBuilder sb=new StringBuilder();
								Map<Object,Object> bdo=(Map)value;
								boolean first=true;
								for (Map.Entry entry : bdo.entrySet())
								{	if (first) first=false; else sb.append(", ");
									sb.append(entry.getKey());
									sb.append(": ");
									sb.append(String.format(format,entry.getValue()));
								}
								valueHuman=sb.toString();
							}
							else
							{	valueHuman="?"+value.getClass();
							}
						}
						else
						{	valueHuman=value.toString();
						}
					}
					else
					{	if (value==null)
						{	valueHuman="NULL";
						}
						else
						{	valueHuman=value.toString();
						}
					}
				}
				catch (Exception e)
				{	valueHuman=	value.toString();
					e.printStackTrace();
				}
				sensorNode.addNode("valueHuman", valueHuman);
				if (valueHTML!=null) sensorNode.addNode("valueHTML", valueHTML);
			}
		}
		return state;
	}
	
	public void onList(INKFRequestContext aContext) throws Exception	
	{	
		IHDSDocument sensorList;
		try
		{	IHDSMutator list=getFilteredList("", aContext);
			sensorList=list.toDocument(false);
		}
		catch (NKFException e)
		{	IHDSMutator list=HDSFactory.newDocument();
			String error=e.getDeepestId();
			String msg;
			if (error.contains("Script not found"))
			{	msg="No SensorList script has been configured yet.";
			}
			else
			{	msg=e.getDeepestId()+" : "+e.getDeepestMessage();
			}
			list.addNode("error", msg);
			sensorList=list.toDocument(false);
		}
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/sensors/styleSensors.xsl");
		req.addArgumentByValue("operand", sensorList);
		String filter=aContext.source("httpRequest:/param/filter",String.class);
		if (filter!=null)
		{	req.addArgumentByValue("filter", filter);
		}
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
		resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
	}

	public void onFilteredList(INKFRequestContext aContext) throws Exception
	{
		String f=aContext.source("httpRequest:/param/f",String.class).toLowerCase();
		IHDSMutator list=getFilteredList(f, aContext);
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/sensors/styleSensors.xsl");
		req.addArgumentByValue("operand", list.toDocument(false));
		req.addArgumentByValue("filtered", Boolean.TRUE);
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
	}
}
