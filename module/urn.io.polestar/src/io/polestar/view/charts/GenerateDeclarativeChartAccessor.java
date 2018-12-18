package io.polestar.view.charts;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

import io.polestar.data.util.MonitorUtils;


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
		String xTicks="";
		String yTicks="";
		
		String endSnap=(String)aOp.getFirstValueOrNull("endSnap");
		if (endSnap==null) endSnap="now";
		String endOffsetString=(String)aOp.getFirstValueOrNull("endOffset");
		String endTimeString=(String)aOp.getFirstValueOrNull("endTime");
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
				endTime=cal.getTimeInMillis();
				endTime+=period*endOffset+1000L*60*60;
				break;
			case "day":
				cal.set(Calendar.HOUR_OF_DAY, 0); // ! clear would not reset the hour of day !
				cal.clear(Calendar.MINUTE);
				cal.clear(Calendar.SECOND);
				cal.clear(Calendar.MILLISECOND);
				endTime=cal.getTimeInMillis();
				endTime+=period*(endOffset)+1000L*60*60*24;
				break;
			case "week":
				cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
				cal.set(Calendar.HOUR_OF_DAY, 0); // ! clear would not reset the hour of day !
				cal.clear(Calendar.MINUTE);
				cal.clear(Calendar.SECOND);
				cal.clear(Calendar.MILLISECOND);
				endTime=cal.getTimeInMillis();
				endTime+=period*(endOffset)+1000L*60*60*24;
				break;
			case "month":
				cal.set(Calendar.DAY_OF_MONTH, 1);
				cal.set(Calendar.HOUR_OF_DAY, 0); // ! clear would not reset the hour of day !
				cal.clear(Calendar.MINUTE);
				cal.clear(Calendar.SECOND);
				cal.clear(Calendar.MILLISECOND);
				cal.add(Calendar.MONTH, endOffset+1);
				endTime=cal.getTimeInMillis();
				endTime+=period*(endOffset)+1000L*60*60*24;
				break;	
		}
		if (endTimeString!=null)
		{	endTime+=Long.parseLong(endTimeString);
		}
		
		long samplesPeriod=Long.parseLong((String)aOp.getFirstValue("samplePeriod"));
		long elementCount=period/samplesPeriod;

		String yAxisTop=(String)aOp.getFirstValueOrNull("yAxisTop");
		String yAxisBottom=(String)aOp.getFirstValueOrNull("yAxisBottom");
		String timeFormat=(String)aOp.getFirstValue("timeFormat");
		String yAxisTicks=(String)aOp.getFirstValueOrNull("yAxisTicks");
		String xAxisTicks=(String)aOp.getFirstValueOrNull("xAxisTicks");
		
		String stackElements="";
		
		ChartSensorData csd=new ChartSensorData(aContext, aOp, endTime, period, samplesPeriod, timeFormat);
		String data=csd.getJSON();
		double min=csd.getMin();
		double max=csd.getMax();
		
		//axis
		if (yAxisTop==null) yAxisTop=Double.toString(max);
		if (yAxisBottom==null) yAxisBottom=Double.toString(min);
		if (yAxisTicks!=null)
		{	yTicks="y.ticks("+Integer.toString((int)Math.round(Math.abs(max-min)/Double.parseDouble(yAxisTicks)))+")";
		}
		else
		{	yTicks="y.ticks()";
		}
		if (xAxisTicks!=null)
		{	
			long c=period/Long.parseLong(xAxisTicks);
			//System.out.println("ticks="+c+" "+elementCount);
			long inc=elementCount/c;
			StringBuilder sb=new StringBuilder();
			sb.append("[");
			for (long tt=0; tt<elementCount; tt+=inc)
			{
				if (tt>0) sb.append(",");
				sb.append(Long.toString(tt));
			}
			sb.append("]");
			
			//System.out.println(sb.toString());
			xTicks=sb.toString();
			//xTicks="x.ticks("+Long.toString(c)+")";
		}
		else
		{	long tp=period/samplesPeriod;
			while (tp>30) tp=tp/2;
			xTicks="x.ticks("+Long.toString(tp)+")";
		}
		
		
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
		boolean hasStack=false;
		for (IHDSReader sensorNode : aOp.getNodes("sensors/sensor"))
		{
			String sensorId=(String)sensorNode.getFirstValue("id");
			String type=(String)sensorNode.getFirstValue("type");
			String stackedString=(String)sensorNode.getFirstValueOrNull("stacked");
			boolean stacked="true".equals(stackedString);
			if (!stacked)
			{
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
				//String function="return (d==null)?null:y((d+"+valueOffsetString+")*"+valueMultiplyString+")";
				
				String function="return (d==null)?null:y(d)";
				
				String baseline;
				String baselineString=(String)sensorNode.getFirstValueOrNull("baseline");
				if (baselineString!=null)
				{	baseline=baselineString;
				}
				else
				{	//protovis bug rendering area that stops at baseline
					double yRange=Math.abs(max-min);
					baseline=Double.toString( Double.parseDouble(yAxisBottom)-yRange*0.01 );
				}
				
				String js="";
				
				String lineWidth=(String)sensorNode.getFirstValueOrNull("lineWidth");
				String strokeDasharray=(String)sensorNode.getFirstValueOrNull("strokeDasharray");
				if (lineWidth==null)
					lineWidth="2";
				if (strokeDasharray==null)
					strokeDasharray="null";
				else
					strokeDasharray="\""+strokeDasharray+"\"";
				
				if (type.equals("area"))
				{
					js="drawArea("+n+","+fill+", "+stroke+", "+shape+", function(d) { "+function+" },"+baseline+","+interpolate+",gd,vis);\n";
					js+="drawLine("+n+","+stroke+", "+fill+", "+strokeDasharray+", "+lineWidth+", "+shape+", function(d) { "+function+" },"+interpolate+",gd,vis);\n";
				}
				else if (type.equals("line"))
				{
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
				
			}
			else
			{	//stacked
				hasStack=true;
			}
			n++;
		}
		String chartElements=sb2.toString();
		
		if (hasStack)
		{
			stackElements=generateStackElement(aOp,width,(int)elementCount);
		}
		
		
		
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
			int rowHeight=16;
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
				String js2="vis.add(pv.Dot).left("+lx+").bottom("+(-5+row*-rowHeight)+").shape(\""+shape+"\").fillStyle("+fill+").strokeStyle("+stroke+").anchor(\"right\").add(pv.Label).textStyle(\""+textColor+"\").text(\""+sensorName+"\")\n";
				lx+=legendLength;
				sb.append(js2);
				n2++;
			}
			legend=sb.toString();
			bottomMargin=Integer.toString(rowHeight*(1+row));
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
		
		String chartCanvas="canvas"+Integer.toHexString(aContext.hashCode());
		
		String template=aContext.source("res:/io/polestar/view/charts/timeSeriesDataTemplate.txt",String.class);
		
		
		Map<String,String> tokens = new HashMap<String,String>();
		tokens.put("DATA", data);
		tokens.put("ELEMENTS", chartElements);
		tokens.put("STACK_ELEMENTS", stackElements);
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
		tokens.put("CHARTCANVAS", chartCanvas);
		tokens.put("XTICKS", xTicks);
		tokens.put("YTICKS", yTicks);
		

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
	
	private String generateStackElement(IHDSReader aOp, float aWidth, int aElementCount)
	{
		int n=2;
		String stackData="[";
		String fillData="[";
		String strokeData="[";
		String functionData="[";
		String chartType=null;
		for (IHDSReader sensorNode : aOp.getNodes("sensors/sensor"))
		{
			String stackedString=(String)sensorNode.getFirstValueOrNull("stacked");
			boolean stacked="true".equals(stackedString);
			if (stacked)
			{	
				String sensorId=(String)sensorNode.getFirstValue("id");
				String type=(String)sensorNode.getFirstValue("type");
				
				String baselineString=(String)sensorNode.getFirstValueOrNull("baseline");
				//if (baselineString!=null)
				//{	baseline=FlbaselineString;
				//}
				
				
				if (stackData.length()>1) stackData+=",";
				stackData+=Integer.toString(n);
				
				String fill=(String)sensorNode.getFirstValueOrNull("fill");
				if (fillData.length()>1) fillData+=",";
				fillData+="\""+fill+"\"";
				
				String stroke=(String)sensorNode.getFirstValueOrNull("stroke");
				if (strokeData.length()>1) strokeData+=",";
				strokeData+="\""+stroke+"\"";
			
				if (chartType==null)
				{	
					if (type.equals("bar"))
					{	float barWidth=3*aWidth/(aElementCount*4);
					
						chartType=".layer.add(pv.Bar)\n";
						chartType+="    .width( function() { return (this.index==0 || this.index=="+(aElementCount-1)+")?"+barWidth/2+":"+barWidth+"; } )\n";
						chartType+="    .left(function() { return x(this.index)-((this.index==0)?0:("+barWidth*0.5+")); } )\n";
						
					}
					else if (type.equals("area"))
					{
						String interpolate=(String)sensorNode.getFirstValueOrNull("interpolate");
						
						chartType=".layer.add(pv.Area)\n";
						chartType+="    .interpolate(\""+interpolate+"\")\n";
					}
					
				}
				
			}
			n++;
		}
		stackData+="]";
		fillData+="]";
		strokeData+="]";
		functionData+="]";
		
		StringBuilder sb=new StringBuilder();
		sb.append("sd=extractStackArray(gd,"+stackData+");\n");
		sb.append("stackFill="+fillData+";\n");
		sb.append("stackStroke="+strokeData+";\n");
		sb.append("stackFunctions="+functionData+";\n");
		//sb.append("console.log(stackFunctions[0])\n");
		sb.append("vis.add(pv.Layout.Stack)\n");
		// TODO can baseline be set to anything other than 0?
		sb.append("    .layers(sd)\n");
		sb.append("    .x(function() { return x(this.index);} )\n");
		//sb.append("    .y(function(d) { return stackFunctions[this.parent.index](d); })\n");
		sb.append("    .y(function(d) { return y(d); })\n");
		
		sb.append(chartType);
		sb.append("    .fillStyle(function(d) { return stackFill[this.parent.index];})\n");
		sb.append("    .strokeStyle(function(d) { return stackStroke[this.parent.index];})\n");
		
		//System.out.println(sb.toString());
		return sb.toString();
	}
	
}
