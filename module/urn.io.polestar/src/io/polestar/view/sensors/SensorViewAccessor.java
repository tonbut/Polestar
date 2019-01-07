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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatConversionException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFRequestReadOnly;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.layer0.representation.IHDSNode;
import org.netkernel.layer0.util.Utils;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;


import io.polestar.api.IPolestarContext;
import io.polestar.api.QueryType;
import io.polestar.data.api.PolestarContext;
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
			else if (action.equals("info"))
			{	onInfo(aContext);
			}
			else if (action.equals("detail") && id!=null)
			{	onDetail(aContext,id);
			}
			else if (action.equals("detailChart") && id!=null)
			{	onDetailChart(aContext,id,false);
			}
			else if (action.equals("errorChart") && id!=null)
			{	onDetailChart(aContext,id,true);
			}
		}
	}
	
	public void onDetailChart(INKFRequestContext aContext, String aId, boolean aError) throws Exception
	{
		//aContext.createResponseFrom("<div>chart</div>");
		
		IPolestarContext context=PolestarContext.createContext(aContext,null);
		
		int width=640;
		final long DAY=1000L*60*60*24;
		long period=1000L*60*60*24;
		long samplePeriod=1000L*60*60;
		String timeFormat="kk:mm";
		long xAxisTicks=samplePeriod;
		int offset=0;
		String endSnap=null;
		Long endTime=null;
		try
		{
			IHDSNode params=aContext.source("httpRequest:/params",IHDSNode.class);
			String chartJSON=params.getFirstNode("/*").getName();
			INKFRequest req=aContext.createRequest("active:JSONToHDS");
			req.addArgumentByValue("operand", chartJSON);
			//req.addArgumentByValue("operator", "<config><addRootElement>chart</addRootElement><convertToString>true</convertToString></config>");
			req.setRepresentationClass(IHDSDocument.class);
			IHDSReader chartHDS=((IHDSDocument)aContext.issueRequest(req)).getReader();
			//System.out.println(chartHDS);
		
			width=Integer.parseInt(chartHDS.getFirstValue("/width2").toString());
			offset=Integer.parseInt(chartHDS.getFirstValue("/offset").toString());
			
			String periodString=(String)chartHDS.getFirstValue("/period");		
			
			if (periodString.equals("hour"))
			{	period=3600000L;
				samplePeriod=period/60;
				xAxisTicks=period/12;
			}
			if (periodString.equals("day"))
			{	period=86400000L;
				samplePeriod=period/48;
				xAxisTicks=period/12;
			}
			if (periodString.equals("week"))
			{	period=604800000L;
				samplePeriod=period/(12*7);
				xAxisTicks=period/7;
				timeFormat="E";
				endSnap="day";
			}
			if (periodString.equals("month"))
			{	period=2592000000L;
				samplePeriod=period/30;
				xAxisTicks=samplePeriod*3;
				timeFormat="d MMM";
				endSnap="day";
				endTime=-DAY;
			}
			if (periodString.equals("year"))
			{	period=31104000000L;
				samplePeriod=period/120;
				xAxisTicks=period/12;
				timeFormat="d MMM";
				endSnap="day";
				endTime=-DAY;
			}

		} catch (Exception e)
		{;}
		
		int height=aError?(width/16):(width/4);
		
		IHDSReader state=aContext.source("active:polestarSensorState",IHDSDocument.class).getReader();
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		IHDSReader configNode=config.getFirstNodeOrNull("key('byId','"+aId+"')");
		
		//IHDSReader stateNode=state.getFirstNodeOrNull("key('byId','"+aId+"')");
		//Object value=stateNode.getFirstValueOrNull("value");
		//IPolestarContext pctx=PolestarContext.createContext(aContext);
		
		
		String format=(String)configNode.getFirstValueOrNull("format");
		String chartType=(String)configNode.getFirstValueOrNull("chart-type");
		if (chartType==null) chartType="";
		//String mergeAction=getMergeActionForSensor(value, format);
		
		int detail=128;
		//samplePeriod=period/detail;
		
		
		
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("chart")
		.addNode("type", "TimeSeriesData")
		.addNode("chartPeriod", Long.toString(period))
		.addNode("endOffset", Integer.toString(offset))
		.addNode("samplePeriod", Long.toString(samplePeriod))
		.addNode("timeFormat", timeFormat)
		.addNode("width", Integer.toString(width))
		.addNode("height", Integer.toString(height))
		.addNode("xAxisTicks", Long.toString(xAxisTicks));
		if (endSnap!=null) m.addNode("endSnap", endSnap);
		if (endTime!=null) m.addNode("endTime", Long.toString(endTime));
		if (aError)
		{	m.addNode("yAxisTicks", "1");
		}
		
		m.pushNode("sensors");
		
		if (aError)
		{
			m.pushNode("sensor")
			.addNode("id", aId)
			.addNode("dname", aId+"#_ERROR")
			.addNode("type","area")
			.addNode("interpolate","step-before")
			.addNode("mergeAction", "max")
			.addNode("fill","rgba(0,0,0,0.08)")
			.addNode("lineWidth", "3")
			.addNode("stroke","rgb(217, 83, 79)")
			;
		}
		else
		{
			Object value=context.createQuery(aId, QueryType.LAST_VALUE).setStart(-1000L*60*60*24*365).execute();
			boolean isNumeric=false;
			boolean isBoolean=false;
			boolean isMap=false;
			if (value instanceof Number)
			{	isNumeric=true;
			}
			else if (value instanceof Boolean)
			{	isBoolean=true;
			}
			else if (value instanceof Map)
			{	isMap=true;
			}
		
			if (isNumeric || isBoolean)
			{
				m.pushNode("sensor")
				.addNode("id", aId)
				.addNode("fill","rgba(0,0,0,0.08)")
				.addNode("lineWidth", "3")
				.addNode("stroke","#448")
				.addNode("type","area")
				;
			}
			
			if (isNumeric)
			{	if ("count".equals(chartType))
				{	m.addNode("interpolate","step-before");
					m.addNode("mergeAction", "positive_diff");
				}
				else if ("discrete".equals(chartType))
				{	m.addNode("interpolate","step-before");
					m.addNode("mergeAction", "average");
				}
				else
				{	m.addNode("interpolate","basis");
					m.addNode("mergeAction", "average");
				}
				m.addNode("type","area");
			}
			if (isBoolean)
			{	if ("analog".equals(chartType))
				{	m.addNode("interpolate","step-before");
					m.addNode("mergeAction", "average");
				}
				else
				{	m.addNode("interpolate","step-before");
					m.addNode("mergeAction", "boolean_change");
				}
			}
			if (isMap)
			{
				Set<String> keys=((Map)value).keySet();
				int index=0;
				
				if ("discrete".equals(chartType))
				{
					for (String key : keys)
					{
						m.pushNode("sensor")
							.addNode("id", aId)
							.addNode("dname", key+"#"+key)
							.addNode("interpolate","step-before")
							.addNode("mergeAction", "average")
							.addNode("type","area")
							.addNode("lineWidth", "2")
							.addNode("stroke",MonitorUtils.getColourScheme(index))
							.addNode("fill","rgba(0,0,0,0.05)")
						.popNode();
						index++;
					}
					m.popNode();
				}
				else if ("stacked".equals(chartType))
				{
					for (String key : keys)
					{
						m.pushNode("sensor")
							.addNode("id", aId)
							.addNode("dname", key+"#"+key)
							.addNode("interpolate","step-before")
							.addNode("stacked", "true")
							.addNode("mergeAction", "average")
							.addNode("type","bar")
							.addNode("lineWidth", "2")
							.addNode("fill",MonitorUtils.getColourScheme(index))
						.popNode();
						index++;
					}
					m.popNode();
				}
				else
				{
					for (String key : keys)
					{
						m.pushNode("sensor")
							.addNode("id", aId)
							.addNode("dname", key+"#"+key)
							.addNode("interpolate","basis")
							.addNode("mergeAction", "average")
							.addNode("type","area")
							.addNode("lineWidth", "2")
							.addNode("stroke",MonitorUtils.getColourScheme(index))
							.addNode("fill","rgba(0,0,0,0.05)")
						.popNode();
						index++;
					}
					m.popNode();
				}
				
				m.addNode("legend", "true");
			}
		}
		
		
		
		INKFRequest req2=aContext.createRequest("active:polestarDeclarativeChart");
		req2.addArgumentByValue("operator",m.toDocument(false));
		INKFResponseReadOnly respIn=aContext.issueRequestForResponse(req2);

		INKFResponse respOut=aContext.createResponseFrom(respIn);
		
	}
	
	
	
	
	public void onDetail(INKFRequestContext aContext, String aId) throws Exception
	{
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		IHDSReader configNode=config.getFirstNodeOrNull("key('byId','"+aId+"')");
		IHDSReader state=aContext.source("active:polestarSensorState",IHDSDocument.class).getReader();
		IHDSReader stateNode=state.getFirstNodeOrNull("key('byId','"+aId+"')");
		IHDSReader info=aContext.source("active:polestarSensorInfo",IHDSDocument.class).getReader();
		IHDSReader infoNode=info.getFirstNodeOrNull("key('byId','"+aId+"')");
		//System.out.println(stateNode);
		IHDSMutator m=stateNode.toDocument().getMutableClone();
		IHDSMutator m2=m.getFirstNode("/sensor");
		long now=System.currentTimeMillis();
		addDerivedSensorNodes(m2,configNode,now,true);
		if (infoNode!=null)
		{
			m2.pushNode("info").appendChildren(infoNode).popNode();
		}
				
		INKFRequest req=aContext.createRequest("active:polestarSensorErrorInfo");
		req.addArgument("id", aId);
		String THREE_MONTHS=Long.toString(1000L*60*60*24*91);
		req.addArgument("duration",THREE_MONTHS);
		req.setRepresentationClass(IHDSDocument.class);
		IHDSReader error=((IHDSDocument)aContext.issueRequest(req)).getReader();
		m2.pushNode("errors").appendChildren(error.getFirstNode("/sensors/sensor")).popNode();
		
		//format percent as decimal just in case default toString() uses scientific notation which XSLT can't handle
		IHDSMutator errorPercentNode=m2.getFirstNode("errors/errorPercent");
		double errorPercent=(Double)errorPercentNode.getFirstValue(".");
		String errorPercentString=String.format("%.6f",errorPercent);
		errorPercentNode.setValue(errorPercentString);
		//IHDSReader config=aContext.source("active:polestarSensorErrorInfo",IHDSDocument.class).getReader();
		
		//System.out.println(m);
		
		
		req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/sensors/styleSensorDetail.xsl");
		req.addArgumentByValue("operand", m.toDocument(false));
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);
		
		INKFResponse resp=aContext.createResponseFrom(subresp);
		resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
	}
	
	public void onInfo(INKFRequestContext aContext) throws Exception
	{
		//System.out.println("onInfo");
		String toDelete=aContext.source("httpRequest:/param/delete",String.class);
		if (toDelete!=null)
		{
			//System.out.println("delete "+toDelete);
			INKFRequest req=aContext.createRequest("active:polestarSensorInfoDelete");
			req.addArgumentByValue("state", toDelete);
			aContext.issueRequest(req);
		}
		
		
		IHDSDocument state=aContext.source("active:polestarSensorInfo",IHDSDocument.class);
		//System.out.println(state);
		
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/sensors/styleSensorInfo.xsl");
		req.addArgumentByValue("operand", state);
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);
		
		INKFResponse resp=aContext.createResponseFrom(subresp);
		resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
	}
	
	public void onTicker(INKFRequestContext aContext) throws Exception
	{
		
		
		IHDSReader state=aContext.source("active:polestarSensorState",IHDSDocument.class).getReader();
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		
		Long period=null;
		Long samplePeriod=null;
		try
		{	IHDSReader mconf=aContext.source("res:/md/execute/named/Configuration",IHDSDocument.class).getReader();
			Object periodValue=mconf.getFirstValueOrNull("sensor-ticker/period");
			if (periodValue!=null) period=Long.parseLong(periodValue.toString());
			Object samplePeriodValue=mconf.getFirstValueOrNull("sensor-ticker/samplePeriod");
			if (samplePeriodValue!=null) samplePeriod=Long.parseLong(samplePeriodValue.toString());
		}
		catch (Exception e)
		{	//e.printStackTrace();
		}
		if (period==null) period=1000L*60*60*24;
		if (samplePeriod==null) samplePeriod=1000L*60*30;
		
		//build query config
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("query");
		m.addNode("start",-period);
		m.addNode("samplePeriod",samplePeriod);
		m.addNode("json","");
		m.pushNode("sensors");
		
		StringBuilder sbID=new StringBuilder();
		sbID.append("[");
		StringBuilder sbInterpolate=new StringBuilder();
		sbInterpolate.append("[");
		
		for (IHDSReader sensor : config.getNodes("/sensors/sensor"))
		{	Object id=sensor.getFirstValue("id");
			Object value=state.getFirstValue("key('byId','"+id+"')/value");
			String mergeAction="sample";
			String interpolation="basis";
			if (value!=null && (value instanceof Float || value instanceof Double))
			{	mergeAction="average";
			}
			if (value!=null && (value instanceof Map))
			{	mergeAction="average_map";
			}
			
			String format=(String)sensor.getFirstValueOrNull("chart-type");
			if ("count".equals(format))
			{	mergeAction="positive_diff";
				//interpolation="step-before";
			}
			if (value!=null && value instanceof Boolean)
			{	
				if ("analog".equals(format))
				{	mergeAction="average";
					//interpolation="step-before";
				}
				else
				{	mergeAction="boolean_change";
				}
			}
			sbID.append(" '").append(id).append("',");
			sbInterpolate.append(" '").append(interpolation).append("',");
			//System.out.println(id+" "+value);
			m.pushNode("sensor").addNode("id",id).addNode("mergeAction",mergeAction).popNode();
		}
		sbID.append(" ]");
		sbInterpolate.append(" ]");
		m.popNode();
		
		
		//INKFRequest req=aContext.createRequest("active:polestarHistoricalQuery");
		INKFRequest req=aContext.createRequest("active:polestarSensorQuery");
		req.addArgumentByValue("operator",m.toDocument(false));
		req.setRepresentationClass(String.class);
		String data=(String)aContext.issueRequest(req);
		//System.out.println(data);
		
		String s=aContext.source("res:/io/polestar/view/sensors/ticker.xml",String.class);
		s=s.replace("%ID%",sbID.toString());
		s=s.replace("%INTERPOLATE%",sbInterpolate.toString());
		s=s.replace("%D%",data);
		
		INKFResponse resp=aContext.createResponseFrom(s);
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		//resp.setExpiry(INKFResponse.EXPIRY_CONSTANT,System.currentTimeMillis()+120000L);
		//resp.setMimeType("text/plain");
	}
	
	

	//store and retrieve sort order from session
	private static String processSort(String aSort, INKFRequestContext aContext) throws Exception
	{	if (aSort.length()==0)
		{	String sort=aContext.source("session:/sensorSort",String.class);
			if (sort!=null)
			{	aSort=sort;
			}
		}
		else
		{	aContext.sink("session:/sensorSort", aSort);
		}
		return aSort;
	}
	
	private IHDSMutator getFilteredList(String aFilter, String aSort, INKFRequestContext aContext) throws Exception
	{	long now=System.currentTimeMillis();

		//get filtered list of sensors
		IHDSReader ss=aContext.source("active:polestarSensorState",IHDSDocument.class).getReader();
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		List<String> ids=new ArrayList<>();
		boolean isError=aFilter.indexOf("error")>=0;
		for (IHDSReader sensorNode : ss.getNodes("/sensors/sensor"))
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
			
			if (include) ids.add(id);
		}
		
		//now sort
		if (aSort.equals("alpha"))
		{	SensorComparator c=new SensorComparator()
			{	public int scriptCompare(IHDSReader s1, IHDSReader s2)
				{	String n1=((String)s1.getFirstValue("name")).toLowerCase();
					String n2=((String)s2.getFirstValue("name")).toLowerCase();
					return n1.compareTo(n2);
				}
			};
			c.setList(config);
			ids.sort(c);
		}
		if (aSort.equals("lastUpd"))
		{	SensorComparator c=new SensorComparator()
			{	public int scriptCompare(IHDSReader s1, IHDSReader s2)
				{	Long n1=((Long)s1.getFirstValue("lastUpdated"));
					Long n2=((Long)s2.getFirstValue("lastUpdated"));
					if (n1==null) n1=Long.valueOf(0);
					if (n2==null) n2=Long.valueOf(0);
					return n2.compareTo(n1);
				}
			};
			c.setList(ss);
			ids.sort(c);
		}
		if (aSort.equals("lastMod"))
		{	SensorComparator c=new SensorComparator()
			{	public int scriptCompare(IHDSReader s1, IHDSReader s2)
				{	Long n1=((Long)s1.getFirstValue("lastModified"));
					Long n2=((Long)s2.getFirstValue("lastModified"));
					if (n1==null) n1=Long.valueOf(0);
					if (n2==null) n2=Long.valueOf(0);
					return n2.compareTo(n1);
				}
			};
			c.setList(ss);
			ids.sort(c);
		}
		if (aSort.equals("lastErr"))
		{	SensorComparator c=new SensorComparator()
			{	public int scriptCompare(IHDSReader s1, IHDSReader s2)
				{	Long n1=((Long)s1.getFirstValue("errorLastModified"));
					Long n2=((Long)s2.getFirstValue("errorLastModified"));
					if (n1==null) n1=Long.valueOf(0);
					if (n2==null) n2=Long.valueOf(0);
					return n2.compareTo(n1);
				}
			};
			c.setList(ss);
			ids.sort(c);
		}
		
		
		
		//now generate list
		IHDSMutator result=HDSFactory.newDocument();
		result.pushNode("sensors");
		for (String id : ids)
		{	IHDSReader sensorNode=ss.getFirstNode("key('byId','"+id+"')");
			result.append(sensorNode);
			IHDSReader sensorDef=config.getFirstNodeOrNull(String.format("key('byId','"+id+"')"));
			IHDSMutator cursor=result.getFirstNode(result.getCursorXPath());
			addDerivedSensorNodes(cursor,sensorDef,now,false);
			result.popNode();
		}
		
		return result;
	}
	
	private static void addDerivedSensorNodes(IHDSMutator sensorNode, IHDSReader sensorDef, long now, boolean aConstraints)
	{
		sensorNode.pushNode("defn");
		sensorNode.appendChildren(sensorDef);
		sensorNode.popNode();
		
		sensorNode.addNode("webId", getWebId((String)sensorDef.getFirstValue("id")));
		
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
		long lastError=(Long)sensorNode.getFirstValue("errorLastModified");
		if (lastError==0)
		{	period="Never";
		}
		else
		{	period=MonitorUtils.formatPeriod(now-lastError)+" ago";
		}
		sensorNode.addNode("lastErrorModifiedHuman", period);
		
		String format=(String)sensorDef.getFirstValueOrNull("format");
		Object value=sensorNode.getFirstValue("value");
		
		String[] vhh=getHumanReadable(value, format);
		String valueHuman=vhh[0];
		String valueHTML=vhh[1];
		
		sensorNode.addNode("valueHuman", valueHuman);
		if (valueHTML!=null) sensorNode.addNode("valueHTML", valueHTML);
		
		//constraints
		if (aConstraints)
		{
			sensorNode.pushNode("constraints");
			for (Map.Entry<String, Boolean> ce : sConstraints.entrySet())
			{	
				Object cp=sensorDef.getFirstValueOrNull(ce.getKey());
				if (cp!=null)
				{	String cps;
					if (ce.getValue())
					{	cps=MonitorUtils.formatPeriod(((Long)cp)*1000L);
					}
					else
					{	cps=cp.toString();
					}
					sensorNode.pushNode("constraint").addNode("name", ce.getKey()).addNode("value", cps).popNode();
				}
			}
			sensorNode.popNode();
		}
		
		
	}
	
	private static Map<String,Boolean> sConstraints=new HashMap();
	static
	{
		sConstraints.put("errorIfNoReadingsFor",true);
		sConstraints.put("errorIfNotModifiedFor",true);
		sConstraints.put("errorOnlyAfter",true);
		sConstraints.put("errorIfGreaterThan",false);
		sConstraints.put("errorClearIfLessThan",false);
		sConstraints.put("errorIfLessThan",false);
		sConstraints.put("errorClearIfGreaterThan",false);
		sConstraints.put("errorIfEquals",false);
		sConstraints.put("errorIfNotEquals",false);
	}
	
	
	
	public static String[] getHumanReadable(Object value, String format)
	{
		String valueHuman=null;
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
		return new String[] {valueHuman,valueHTML};
	}
	
	public void onList(INKFRequestContext aContext) throws Exception	
	{	
		IHDSDocument sensorList;
		String sort=processSort("",aContext);
		try
		{	IHDSMutator list=getFilteredList("", sort, aContext);
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
		
		//build list of keywords
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		Set<String> keywordSet=new HashSet<>();
		for (IHDSReader sensor : config.getNodes("/sensors/sensor"))
		{	String keywords=(String)sensor.getFirstValueOrNull("keywords");
			if (keywords!=null)
			{	String[] kws=Utils.splitString(keywords, ", ");
				for (String kw : kws)
				{	keywordSet.add(kw);
				}
			}
		}
		List<String> keywordList=new ArrayList<>(keywordSet);
		Collections.sort(keywordList);
		IHDSMutator keywords=HDSFactory.newDocument();
		keywords.pushNode("keywords");
		for (String keyword: keywordList)
		{	keywords.addNode("keyword", keyword);
		}
		
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/sensors/styleSensors.xsl");
		req.addArgumentByValue("operand", sensorList);
		req.addArgumentByValue("sort", sort);
		req.addArgumentByValue("keywords", keywords.toDocument(false));
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
		String sort=aContext.source("httpRequest:/param/sort",String.class);
		sort=processSort(sort,aContext);
		IHDSMutator list=getFilteredList(f, sort, aContext);
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/sensors/styleSensors.xsl");
		req.addArgumentByValue("operand", list.toDocument(false));
		req.addArgumentByValue("sort", sort);
		req.addArgumentByValue("filtered", Boolean.TRUE);
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
	}
	
	private static String getWebId(String aId)
	{	return aId.replaceAll("\\:", "%3A");
	}
	
	
	private static abstract class SensorComparator implements Comparator<String>
	{
		private IHDSReader mList;
		
		public void setList(IHDSReader aList)
		{	mList=aList;
		}
		
		public int compare(String o1, String o2)
		{	IHDSReader sensor1=mList.getFirstNodeOrNull("key('byId','"+o1+"')");
			IHDSReader sensor2=mList.getFirstNodeOrNull("key('byId','"+o2+"')");
			return scriptCompare(sensor1,sensor2);
		}
		
		public abstract int scriptCompare(IHDSReader s1, IHDSReader s2);
	}
	
}
