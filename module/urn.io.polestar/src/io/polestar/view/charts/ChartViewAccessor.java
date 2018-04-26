package io.polestar.view.charts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFRequestReadOnly;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.layer0.representation.IBinaryStreamRepresentation;
import org.netkernel.layer0.representation.IHDSNode;
import org.netkernel.layer0.representation.IReadableBinaryStreamRepresentation;
import org.netkernel.layer0.util.Utils;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.data.util.MonitorUtils;
import io.polestar.view.template.TemplateWrapper;

public class ChartViewAccessor extends StandardAccessorImpl
{
	public final static String KEYWORD_CHART="__PolestarChart";
	private final Matcher mMatcher;
	public ChartViewAccessor()
	{	this.declareThreadSafe();
		mMatcher=Pattern.compile("\"\"\"(.*)\"\"\"",Pattern.DOTALL|Pattern.MULTILINE).matcher("");
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
			else if (action.equals("new"))
			{	onNewChart(aContext);
			}
			else if (action.equals("edit"))
			{	onEdit(id,aContext);
			}
			else if (action.equals("schema"))
			{	onSchema(aContext);
			}
			else if (action.equals("options"))
			{	onOptions(aContext);
			}
			else if (action.equals("data"))
			{	onData(id,aContext);
			}
			else if (action.equals("preview"))
			{	onPreview(id,aContext);
			}
			else if (action.equals("save"))
			{	onSave(id,aContext);
			}
			else if (action.equals("saveas"))
			{	onSaveAs(id,aContext);
			}
			else if (action.equals("delete"))
			{	onDelete(id,aContext);
			}
			
		}
	}
	
	public void onList(INKFRequestContext aContext) throws Exception
	{
		MonitorUtils.isLoggedIn(aContext);
		
		IHDSMutator list=aContext.source("active:polestarListScripts",IHDSDocument.class).getMutableClone();
		
		//build list of keywords and remove non chart scripts
		Set<String> keywordSet=new HashSet<>();
		for (IHDSMutator sensor : list.getNodes("/scripts/script"))
		{	String keywords=(String)sensor.getFirstValueOrNull("keywords");
			if (keywords!=null)
			{	String[] kws=Utils.splitString(keywords, ", ");
				boolean isChart=false;
				for (String kw : kws)
				{	if (kw.equals(KEYWORD_CHART))
					{	isChart=true;
						break;
					}
				}
				if (!isChart)
				{	sensor.delete();
				}
				else
				{	for (String kw : kws)
					{	keywordSet.add(kw);
					}
				}
			}
			else
			{	sensor.delete();
			}
		}
		List<String> keywordList=new ArrayList<>(keywordSet);
		Collections.sort(keywordList);
		IHDSMutator keywords=HDSFactory.newDocument();
		keywords.pushNode("tags").pushNode("keywords");
		for (String keyword: keywordList)
		{	keywords.addNode("keyword", keyword);
		}
		
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/charts/styleCharts.xsl");
		req.addArgumentByValue("operand", list.toDocument(false));
		req.addArgumentByValue("tags", keywords.toDocument(false));
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
		IHDSMutator list=aContext.source("active:polestarListScripts",IHDSDocument.class).getMutableClone();
		//if (f.length()>0)
		{	for (IHDSMutator scriptNode : list.getNodes("/scripts/script"))
			{	boolean found=false;
				String keywords=(String)scriptNode.getFirstValue("keywords");
				if (keywords!=null && keywords.contains(KEYWORD_CHART))
				{
					String name=(String)scriptNode.getFirstValue("name");
					if (name.toLowerCase().contains(f)) found=true;
					if (keywords!=null && keywords.toLowerCase().contains(f)) found=true;
				}
				
				if (!found)
				{	scriptNode.delete();
				}
			}
		}
		list.setCursor("/scripts").addNode("@filtered", "true");
		
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/charts/styleCharts.xsl");
		req.addArgumentByValue("operand", list.toDocument(true));
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
	}
	
	public void onNewChart(INKFRequestContext aContext) throws Exception
	{	MonitorUtils.assertAdmin(aContext);
	
		String guid=innerOnNew(aContext);
		aContext.sink("httpResponse:/redirect","/polestar/charts/edit/"+guid);
	}
	
	private String innerOnNew(INKFRequestContext aContext) throws Exception
	{
		String newId=aContext.requestNew("res:/md/script/", null);
		String guid=newId.substring(newId.lastIndexOf('/')+1);
		
		//initialise state of chart
		IHDSMutator m=aContext.source(newId,IHDSDocument.class).getMutableClone();
		//System.out.println(m);
		m.setCursor("/script/keywords").setValue(KEYWORD_CHART);
		String scriptName="Chart "+guid;
		m.setCursor("/script/name").setValue(scriptName);
		String scriptSource=aContext.source("res:/io/polestar/view/charts/chartScriptSource.txt",String.class);
		m.setCursor("/script/script").setValue(scriptSource);
		aContext.sink(newId, m.toDocument(false));
		return guid;
	}
	
	public void onData(String aId,INKFRequestContext aContext) throws Exception
	{
		IHDSReader scriptData=aContext.source("res:/md/script/"+aId,IHDSDocument.class).getReader();
		String scriptCode=(String)scriptData.getFirstValue("/script/script");
		mMatcher.reset(scriptCode);
		mMatcher.find();
		String chartDefn=mMatcher.group(1);
		IHDSDocument chartHDSDoc=aContext.transrept(chartDefn, IHDSDocument.class);
		IHDSMutator chartHDS=chartHDSDoc.getMutableClone();
		
		//fix naming of sensor array
		for (IHDSMutator sensor : chartHDS.getNodes("/chart/sensors/sensor"))
		{	sensor.rename("sensors");
		}
		try
		{	chartHDS.setCursor("/chart/sensors").rename("sensors__A");
		} catch (Exception e)
		{	//maybe none
		}
		
		//append script fields
		String keywords=(String)scriptData.getFirstValue("/script/keywords");
		keywords=keywords.replace(KEYWORD_CHART, "").trim();
		chartHDS.setCursor("/chart")
			.createIfNotExists("keywords").setValue(keywords);
		chartHDS.setCursor("/chart")
			.createIfNotExists("public").setValue(scriptData.getFirstValue("/script/public"));
		chartHDS.setCursor("/chart/title").setValue(scriptData.getFirstValue("/script/name"));
		
		INKFRequest req=aContext.createRequest("active:JSONFromHDS");
		req.addArgumentByValue("operand", chartHDS.toDocument(false));
		req.addArgumentByValue("operator", "<config><removeRootElement>true</removeRootElement></config>");
		req.setRepresentationClass(IBinaryStreamRepresentation.class);
		IBinaryStreamRepresentation json=(IBinaryStreamRepresentation)aContext.issueRequest(req);
		//System.out.println(json);
		INKFResponse resp=aContext.createResponseFrom(json);
		resp.setMimeType("text/plain");
	}
		
	private IHDSMutator getFormHDS(INKFRequestContext aContext) throws Exception
	{
		IHDSNode params=aContext.source("httpRequest:/params",IHDSNode.class);
		String chartJSON=params.getFirstNode("/*").getName();
		INKFRequest req=aContext.createRequest("active:JSONToHDS");
		req.addArgumentByValue("operand", chartJSON);
		req.addArgumentByValue("operator", "<config><addRootElement>chart</addRootElement><convertToString>true</convertToString></config>");
		req.setRepresentationClass(IHDSDocument.class);
		IHDSDocument chartHDS=(IHDSDocument)aContext.issueRequest(req);
		IHDSMutator m=HDSFactory.newDocument();
		m.appendChildren(chartHDS.getReader());
		for (IHDSMutator sensor : m.getNodes("/chart/sensors__A/sensors"))
		{	sensor.rename("sensor");
		}
		m.setCursor("/chart/sensors__A").rename("sensors");
		return m;
	}
	
	public void onPreview(String aId,INKFRequestContext aContext) throws Exception
	{
		IHDSMutator m=getFormHDS(aContext);
		
		INKFRequest req=aContext.createRequest("active:polestarDeclarativeChart");
		req.addArgumentByValue("operator",m.toDocument(false));
		INKFResponseReadOnly respIn=aContext.issueRequestForResponse(req);

		INKFResponse respOut=aContext.createResponseFrom(respIn);
	}
	
	public void onSaveAs(String aId,INKFRequestContext aContext) throws Exception
	{
		MonitorUtils.assertAdmin(aContext);		
		String guid=innerOnNew(aContext);
		onSave(guid,aContext);
		INKFResponse respOut=aContext.createResponseFrom("{\"id\":\""+guid+"\"}");
		respOut.setMimeType("application/json");
	}
		
	public void onSave(String aId,INKFRequestContext aContext) throws Exception
	{
		MonitorUtils.assertAdmin(aContext);
		
		IHDSMutator m=getFormHDS(aContext);
		//System.out.println(m);
		IReadableBinaryStreamRepresentation bs=aContext.transrept(m.toDocument(false), IReadableBinaryStreamRepresentation.class);
		String serializedChart=aContext.transrept(bs, String.class);
		//System.out.println(serializedChart);	
		
		IHDSMutator m2=aContext.source("res:/md/script/"+aId,IHDSDocument.class).getMutableClone();
		
		//update script with new chart configuration
		String script=(String)m2.getFirstValue("/script/script");
		mMatcher.reset(script);
		script=mMatcher.replaceFirst("\"\"\""+serializedChart+"\n\"\"\"");
		m2.setCursor("/script/script").setValue(script);
		
		//set other fields
		m2.setCursor("/script/name").setValue(m.getFirstValue("/chart/title"));
		String keywords=(String)m.getFirstValueOrNull("/chart/keywords");
		if (keywords==null) keywords="";
		keywords+=" "+KEYWORD_CHART;
		m2.setCursor("/script/keywords").setValue(keywords);
		m2.setCursor("/script/public").setValue(m.getFirstValue("/chart/public"));		
		
		aContext.sink("res:/md/script/"+aId, m2.toDocument(false));
		INKFResponse respOut=aContext.createResponseFrom("done");
	}
	
	
	public void onEdit(String aId,INKFRequestContext aContext) throws Exception
	{
		MonitorUtils.assertAdmin(aContext);
		String page=aContext.source("res:/io/polestar/view/charts/editPage.xml",String.class);
		page=page.replace("%ID%", aId);
		INKFResponse resp=aContext.createResponseFrom(page);
		resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
	}

	public void onDelete(String aId,INKFRequestContext aContext) throws Exception
	{	MonitorUtils.assertAdmin(aContext);
		aContext.delete("res:/md/script/"+aId);
		aContext.sink("httpResponse:/redirect","/polestar/charts");
	}
	
	public void onSchema(INKFRequestContext aContext) throws Exception
	{	String schema=aContext.source("res:/io/polestar/view/charts/schema.json",String.class);
	
		IHDSReader sensors=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		StringBuilder sb=new StringBuilder();
		boolean first=true;
		for (IHDSReader sensor : sensors.getNodes("/sensors/sensor"))
		{
			String sid=(String)sensor.getFirstValue("id");
			//String name=(String)sensor.getFirstValue("name");
			if (first)
			{	first=false;
			}
			else
			{	sb.append(",");
			}
			sb.append("\""+sid+"\"");
		}
		schema=schema.replace("%SENSORS%", sb.toString());
		
		INKFResponse respOut=aContext.createResponseFrom(schema);
		respOut.setMimeType("application/json");
	}
	
	public void onOptions(INKFRequestContext aContext) throws Exception
	{
		String options=aContext.source("res:/io/polestar/view/charts/options.json",String.class);
		
		IHDSReader sensors=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		StringBuilder sb=new StringBuilder();
		boolean first=true;
		for (IHDSReader sensor : sensors.getNodes("/sensors/sensor"))
		{
			//String sid=(String)sensor.getFirstValue("id");
			String name=(String)sensor.getFirstValue("name");
			if (first)
			{	first=false;
			}
			else
			{	sb.append(",");
			}
			sb.append("\""+name+"\"");
		}
		options=options.replace("%SENSOR_NAMES%", sb.toString());
		
		INKFResponse respOut=aContext.createResponseFrom(options);
		respOut.setMimeType("application/json");
	}
	
}
