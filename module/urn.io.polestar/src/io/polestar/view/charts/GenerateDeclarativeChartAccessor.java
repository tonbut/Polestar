package io.polestar.view.charts;

import java.util.Calendar;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
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
		onTimeSeriesData(aContext,operator);
	}
	
	private void onTimeSeriesData(INKFRequestContext aContext, IHDSReader aOp) throws NKFException
	{
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
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
		//long elementCount=period/samplesPeriod;

		//String yAxisTop=(String)aOp.getFirstValueOrNull("yAxisTop");
		//String yAxisBottom=(String)aOp.getFirstValueOrNull("yAxisBottom");
		String timeFormat=(String)aOp.getFirstValue("timeFormat");
		//String yAxisTicks=(String)aOp.getFirstValueOrNull("yAxisTicks");
		//String xAxisTicks=(String)aOp.getFirstValueOrNull("xAxisTicks");
		
		String stackElements="";
		
		ChartSensorData csd=new ChartSensorData(aContext, aOp, endTime, period, samplesPeriod, timeFormat);
		IHDSDocument data=csd.getData();
		double min=csd.getMin();
		double max=csd.getMax();
		
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("chart")
		.addNode("xAxis", "tf");
		
		copyThrough(aOp,m,"backgroundColor","rgba(255,255,255,0)");
		copyThrough(aOp,m,"textColor","#000000");
		copyThrough(aOp,m,"axisColor","#CCCCCC");
		copyThrough(aOp,m,"width","640");
		copyThrough(aOp,m,"height","240");
		copyThrough(aOp,m,"legend","false");
		copyThrough(aOp,m,"title",null);
		copyThrough(aOp,m,"excludeJS",null);
		copyThrough(aOp,m,"yAxisTop",Double.toString(max));
		copyThrough(aOp,m,"yAxisBottom",Double.toString(min));
		copyThrough(aOp,m,"yAxisTicks",null);
		copyThrough(aOp,m,"xAxisTicks",null);
		
		m.pushNode("dataSets");
		int i=0;
		for (IHDSReader sensor : aOp.getNodes("sensors/sensor"))
		{
			String id=(String)sensor.getFirstValue("id");
			m.pushNode("dataSet")
			.addNode("id", "s"+i);
			
			String dname=(String)sensor.getFirstValueOrNull("dname");
			if (dname!=null)
			{	int si=dname.indexOf('#');
				if (si>=0) dname=dname.substring(0,si);
			}
			else
			{	dname=(String)config.getFirstValue("key('byId','"+id+"')/name");
			}
			m.addNode("name", dname);
			copyThrough(sensor,m,"interpolate",null);
			String type=(String)copyThrough(sensor,m,"type",null);
			if (type.equals("line"))
			{	copyThrough(sensor,m,"fill","stroke", null);
				m.addNode("fill", "rgba(255,255,255,0)");
			}
			else
			{	Object fill=copyThrough(sensor,m,"fill",null);
				copyThrough(sensor,m,"stroke",fill);
			}
			copyThrough(sensor,m,"lineWidth",null);
			copyThrough(sensor,m,"shape",null);
			copyThrough(sensor,m,"strokeDasharray",null);
			copyThrough(sensor,m,"baseline",null);
			copyThrough(sensor,m,"stacked",null);
			m.popNode();
			i++;
		}

		INKFRequest req2=aContext.createRequest("active:declarativeTimeseriesChart");
		req2.addArgumentByValue("operator",m.toDocument(false));
		req2.addArgumentByValue("operand",data);
		INKFResponseReadOnly respIn=aContext.issueRequestForResponse(req2);

		INKFResponse respOut=aContext.createResponseFrom(respIn);

	}
	
	private Object copyThrough(IHDSReader aOp, IHDSMutator m, String aNodeName, Object aDefault)
	{
		Object o=aOp.getFirstValueOrNull(aNodeName);
		if (o==null) o=aDefault;
		if (o!=null) m.addNode(aNodeName, o);
		return o;
	}
	
	private Object copyThrough(IHDSReader aOp, IHDSMutator m, String aFrom, String aTo, Object aDefault)
	{
		Object o=aOp.getFirstValueOrNull(aFrom);
		if (o==null) o=aDefault;
		if (o!=null) m.addNode(aTo, o);
		return o;
	}
	
}
