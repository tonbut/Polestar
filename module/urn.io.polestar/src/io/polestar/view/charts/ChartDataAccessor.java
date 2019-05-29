package io.polestar.view.charts;

import java.util.Calendar;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;


public class ChartDataAccessor extends StandardAccessorImpl
{
	public ChartDataAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws NKFException
	{
		IHDSReader aOp=aContext.source("arg:operator",IHDSDocument.class).getReader().getFirstNode("/*");

		long period=Long.parseLong((String)aOp.getFirstValue("chartPeriod"));
		long endTime;
		
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
		String timeFormat=(String)aOp.getFirstValueOrNull("timeFormat");
		ChartSensorData data=new ChartSensorData(aContext, aOp, endTime, period, samplesPeriod, timeFormat);
		aContext.createResponseFrom(data.getData()).setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
}
