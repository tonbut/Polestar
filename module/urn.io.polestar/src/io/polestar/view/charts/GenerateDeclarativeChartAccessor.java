package io.polestar.view.charts;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;


public class GenerateDeclarativeChartAccessor extends StandardAccessorImpl
{
	public GenerateDeclarativeChartAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws NKFException
	{
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader().getFirstNode("/*");
		
		String type=(String)operator.getFirstValue("type");
		if ("TimeSeriesData".equals(type))
		{	onTimeSeriesData(aContext,operator);
		}
		else
		{	INKFResponse respOut=aContext.createResponseFrom("<html></html>");
			respOut.setMimeType("text/html");
			respOut.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		}
	}
	
	private void onTimeSeriesData(INKFRequestContext aContext, IHDSReader aOp) throws NKFException
	{
		long period=Long.parseLong((String)aOp.getFirstValue("chartPeriod"));
		long endTime;
		/*
		String endTimeString=(String)aOp.getFirstValueOrNull("endTime");
		if (endTimeString==null)
		{	endTime=System.currentTimeMillis();
		}
		else
		{	endTime=Long.parseLong(endTimeString);
			if (endTime<=0)
			{	endTime+=System.currentTimeMillis();
			}
		}
		*/
		
		String endSnap=(String)aOp.getFirstValueOrNull("endSnap");
		if (endSnap==null) endSnap="now";
		String endOffsetString=(String)aOp.getFirstValueOrNull("endOffset");
		if (endOffsetString==null) endOffsetString="0";
		int endOffset=Integer.parseInt(endOffsetString);
		Calendar cal = Calendar.getInstance();
		switch(endSnap)
		{
			case "now":
			default:
				endTime=cal.getTimeInMillis()+period*endOffset;
				break;
			case "hour":
				cal.clear(Calendar.MINUTE);
				cal.clear(Calendar.SECOND);
				cal.clear(Calendar.MILLISECOND);
				endTime=cal.getTimeInMillis()+1000L*60*60*(endOffset+1);
				break;
			case "day":
				cal.set(Calendar.HOUR_OF_DAY, 0); // ! clear would not reset the hour of day !
				cal.clear(Calendar.MINUTE);
				cal.clear(Calendar.SECOND);
				cal.clear(Calendar.MILLISECOND);
				endTime=cal.getTimeInMillis()+1000L*60*60*24*(endOffset+1);
				break;
			case "week":
				cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
				cal.set(Calendar.HOUR_OF_DAY, 0); // ! clear would not reset the hour of day !
				cal.clear(Calendar.MINUTE);
				cal.clear(Calendar.SECOND);
				cal.clear(Calendar.MILLISECOND);
				endTime=cal.getTimeInMillis()+1000L*60*60*24*7*(endOffset+1);
				break;
			case "month":
				cal.set(Calendar.DAY_OF_MONTH, 1);
				cal.set(Calendar.HOUR_OF_DAY, 0); // ! clear would not reset the hour of day !
				cal.clear(Calendar.MINUTE);
				cal.clear(Calendar.SECOND);
				cal.clear(Calendar.MILLISECOND);
				cal.add(Calendar.MONTH, endOffset+1);
				endTime=cal.getTimeInMillis();
				break;
				
		}
		/* "endSnap": {
        	"type": "string",
            "title": "Chart End",
            "enum": [ "now", "hour", "day", "week", "month", "year" ],
            "default": "now"
        },
        "endOffset": */
		
		
		
		long samplesPeriod=Long.parseLong((String)aOp.getFirstValue("samplePeriod"));
		long mergeCount=samplesPeriod/(1000L*60*5);
		long elementCount=period/samplesPeriod;
		//System.out.println(elementCount);
		boolean normalizeYAxis="true".equals(aOp.getFirstValueOrNull("normalizeYAxis"));
		String yAxisTop=(String)aOp.getFirstValueOrNull("yAxisTop");
		if (yAxisTop==null) yAxisTop="null";
		String yAxisBottom=(String)aOp.getFirstValueOrNull("yAxisBottom");
		if (yAxisBottom==null) yAxisBottom="null";
		
		String timeFormat=(String)aOp.getFirstValue("timeFormat");
		
		
		//Request historical data
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("query");
		m.addNode("start",endTime-period);
		m.addNode("end",endTime);
		m.addNode("merge",(int)mergeCount);
		m.addNode("samplePeriod",samplesPeriod);
		if (timeFormat!=null) m.addNode("timeFormat",timeFormat);
		m.addNode("json",true);
		m.pushNode("sensors");
		
		for (IHDSReader sensorNode : aOp.getNodes("sensors/sensor"))
		{
			String sensorId=(String)sensorNode.getFirstValue("id");
			String dname=(String)sensorNode.getFirstValueOrNull("dname");
			if (dname!=null && dname.indexOf('#')>=0)
			{	String fragment=dname.substring(dname.indexOf('#')+1);
				sensorId=sensorId+"#"+fragment;
			}
			String mergeAction=(String)sensorNode.getFirstValue("mergeAction");
			m.pushNode("sensor").addNode("id",sensorId).addNode("mergeAction",mergeAction).popNode();
		}
		
		//INKFRequest req=aContext.createRequest("active:polestarHistoricalQuery");
		INKFRequest req=aContext.createRequest("active:polestarSensorQuery");
		req.addArgumentByValue("operator",m.toDocument(false));
		req.setRepresentationClass(String.class);
		String data=(String)aContext.issueRequest(req);
		
		//chart layout
		float width=640.0f;
		float height=240.0f;
		String widthString=(String)aOp.getFirstValueOrNull("width");
		if (widthString!=null) width=Float.parseFloat(widthString);
		String heightString=(String)aOp.getFirstValueOrNull("height");
		if (heightString!=null) height=Float.parseFloat(heightString);
		
		//generate chart elements
		StringBuilder sb2=new StringBuilder();
		int n=2;
		for (IHDSReader sensorNode : aOp.getNodes("sensors/sensor"))
		{
			String sensorId=(String)sensorNode.getFirstValue("id");
			String type=(String)sensorNode.getFirstValue("type");
			
			String fill=(String)sensorNode.getFirstValueOrNull("fill");
			String stroke=(String)sensorNode.getFirstValueOrNull("stroke");
			String shape=(String)sensorNode.getFirstValueOrNull("shape");
			if (fill==null)
				fill="null";
			else
				fill="\""+fill+"\"";
			if (stroke==null)
				stroke="null";
			else
				stroke="\""+stroke+"\"";
			if (shape==null)
				shape="null";
			else
				shape="\""+shape+"\"";
			String interpolate=(String)sensorNode.getFirstValueOrNull("interpolate");
			if (interpolate!=null) interpolate="\""+interpolate+"\"";
			
			String valueMultiplyString=(String)sensorNode.getFirstValueOrNull("valueMultiply");
			if (valueMultiplyString==null) valueMultiplyString="1";
			String valueOffsetString=(String)sensorNode.getFirstValueOrNull("valueOffset");
			if (valueOffsetString==null) valueOffsetString="0";
			//String function="return (d==null)?null:y(d*"+valueMultiplyString+"+"+valueOffsetString+")";
			String function="return (d==null)?null:y((d+"+valueOffsetString+")*"+valueMultiplyString+")";
			
			String baseline="0";
			String baselineString=(String)sensorNode.getFirstValueOrNull("baseline");
			if (baselineString!=null)
			{	baseline=baselineString;
			}
			//baseline=Float.toString((Float.parseFloat(baseline)-Float.parseFloat(valueOffsetString))/Float.parseFloat(valueMultiplyString));
			
			String js="";
			if (type.equals("area"))
			{
				js="drawArea("+n+","+fill+", "+stroke+", "+shape+", function(d) { "+function+" },"+baseline+","+interpolate+",gd,vis);\n";
			}
			else if (type.equals("line"))
			{
				String lineWidth=(String)sensorNode.getFirstValueOrNull("lineWidth");
				String strokeDasharray=(String)sensorNode.getFirstValueOrNull("strokeDasharray");
				if (lineWidth==null)
					lineWidth="2";
				if (strokeDasharray==null)
					strokeDasharray="null";
				else
					strokeDasharray="\""+strokeDasharray+"\"";
				
				js="drawLine("+n+","+fill+", "+stroke+", "+strokeDasharray+", "+lineWidth+", "+shape+", function(d) { "+function+" },"+interpolate+",gd,vis);\n";
			}
			else if (type.equals("bar"))
			{	float barWidth=width/(elementCount*2);
				js="drawBar("+n+","+fill+", "+stroke+",function(d) { "+function+" },"+baseline+","+barWidth+",gd,vis);\n";
			}
			else if (type.equals("boolean"))
			{	js="drawBoolean("+n+","+fill+", "+stroke+", "+valueOffsetString+", "+valueMultiplyString+",gd,vis);\n";
				
			}
			
			sb2.append(js);
			n++;
		}
		String chartElements=sb2.toString();
		
		//Colours
		String backgroundColor=(String)aOp.getFirstValueOrNull("backgroundColor");
		if (backgroundColor==null) backgroundColor="#FFF";
		String textColor=(String)aOp.getFirstValueOrNull("textColor");
		if (textColor==null) textColor="#000";
		String gridColor=(String)aOp.getFirstValueOrNull("axisColor");
		if (gridColor==null) gridColor="#CCC";
		
		
		//legend
		String legend="";
		String bottomMargin="20";
		String topMargin="5";
		
		String doLegendString=(String)aOp.getFirstValueOrNull("legend");
		boolean doLegend="true".equals(doLegendString);
		if (doLegend)
		{
			StringBuilder sb=new StringBuilder();
			IHDSReader sensorConfig=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
			//System.out.println(sensorConfig);
			int n2=0;
			int lx=5;
			int row=1;
			for (IHDSReader sensorNode : aOp.getNodes("sensors/sensor"))
			{
				String id=(String)sensorNode.getFirstValue("id");
				
				String sensorName=(String)sensorNode.getFirstValueOrNull("dname");
				if (sensorName==null || sensorName.length()==0 || sensorName.indexOf('#')==0 )
				{	IHDSReader sensorConfigNode=sensorConfig.getFirstNode("key('byId','"+id+"')");
					sensorName=(String)sensorConfigNode.getFirstValue("name");
				}
				else if (sensorName.indexOf('#')>0)
				{	int i=sensorName.indexOf('#');
					sensorName=sensorName.substring(0,i);
				}
				
				String fill=(String)sensorNode.getFirstValueOrNull("fill");
				String stroke=(String)sensorNode.getFirstValueOrNull("stroke");
				String shape=(String)sensorNode.getFirstValueOrNull("shape");
				if (fill==null)
					fill="null";
				else
					fill="\""+fill+"\"";
				if (stroke==null)
					stroke="null";
				else
					stroke="\""+stroke+"\"";
				if (shape==null)
					shape="square";
				
				
				int legendLength=25+sensorName.length()*6;
				if ((lx+legendLength)>width)
				{	row++;
					lx=5;
				}
				String js2="vis.add(pv.Dot).left("+lx+").bottom("+(-5+row*-20)+").shape(\""+shape+"\").fillStyle("+fill+").strokeStyle("+stroke+").anchor(\"right\").add(pv.Label).textStyle(\""+textColor+"\").text(\""+sensorName+"\")\n";
				lx+=legendLength;
				sb.append(js2);
				n2++;
			}
			legend=sb.toString();
			bottomMargin=Integer.toString(20*(1+row));
		}
		
		//title
		String titleJS="";
		String titleText=(String)aOp.getFirstValueOrNull("title");
		if (titleText!=null)
		{
			topMargin="36";
			titleJS="vis.add(pv.Label).font(\"bold 12px sans-serif\").left(x(0)).bottom(h+18).textAlign(\"left\").textStyle(textColor).text(\""+titleText+"\");"; 
			//endTime-period
			DateFormat df=DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
			String endTimeS=df.format(new Date(endTime));
			String startTimeS=df.format(new Date(endTime-period));
			String subTitle="from "+startTimeS+" to "+endTimeS;
			titleJS+="vis.add(pv.Label).font(\"10px sans-serif\").left(x(0)).bottom(h+4).textAlign(\"left\").textStyle(textColor).text(\""+subTitle+"\");";
		}
		
		
		
		String template=aContext.source("res:/io/polestar/view/charts/timeSeriesDataTemplate.txt",String.class);
		
		
		Map<String,String> tokens = new HashMap<String,String>();
		tokens.put("DATA", data);
		tokens.put("ELEMENTS", chartElements);
		tokens.put("WIDTH", Float.toString(width));
		tokens.put("HEIGHT", Float.toString(height));
		tokens.put("CWIDTH", Float.toString(width+40));
		tokens.put("CHEIGHT", Float.toString(height+Float.parseFloat(topMargin)+Float.parseFloat(bottomMargin)));
		tokens.put("BACKGROUND_COLOR", backgroundColor);
		tokens.put("TEXT_COLOR", textColor);
		tokens.put("GRID_COLOR", gridColor);
		tokens.put("TOP", yAxisTop);
		tokens.put("BOTTOM", yAxisBottom);
		tokens.put("TOPMARGIN", topMargin);
		tokens.put("BOTTOMMARGIN", bottomMargin);
		tokens.put("LEGEND", legend);
		tokens.put("TITLE", titleJS);

		// Create pattern of the format "%(cat|beverage)%"
		String patternString = "%(.+?)%";
		Pattern pattern = Pattern.compile(patternString);
		Matcher matcher = pattern.matcher(template);

		StringBuffer sb = new StringBuffer();
		while(matcher.find()) {
		    String keyword=matcher.group(1);
		    String replacement=tokens.get(keyword);
			matcher.appendReplacement(sb, replacement);
		}
		matcher.appendTail(sb);
		
		INKFResponse respOut=aContext.createResponseFrom(sb.toString());
		respOut.setMimeType("text/html");
		respOut.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		
	}
}
