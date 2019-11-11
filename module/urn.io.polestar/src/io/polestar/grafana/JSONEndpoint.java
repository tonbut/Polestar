package io.polestar.grafana;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.layer0.util.Utils;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.api.IPolestarContext;
import io.polestar.api.IPolestarQuery;
import io.polestar.api.IPolestarQueryResultSet;
import io.polestar.api.QueryType;
import io.polestar.data.api.PolestarContext;

public class JSONEndpoint extends StandardAccessorImpl
{
	private SimpleDateFormat mISO8601date = new SimpleDateFormat(
		    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
		
	public JSONEndpoint()
	{	this.declareThreadSafe();
		mISO8601date.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String type=aContext.getThisRequest().getArgumentValue("type");
		
		
		try
		{
			INKFRequest req=aContext.createRequest("active:JSONToHDS");
			req.addArgument("operand", "httpRequest:/body");
			req.setRepresentationClass(IHDSDocument.class);
			IHDSReader s=((IHDSDocument)aContext.issueRequest(req)).getReader();
			//System.out.println(String.format("Grafana.%s: %s",type,s.toString()));
			switch(type)
			{	case "search":
					onSearch(s,aContext);
					break;
				case "query":
					onQuery(s,aContext);
					break;
			}
		}
		catch (Exception e)
		{
			String msg=String.format("Unhandled Grafana error handing %s:\n%s", type, Utils.throwableToString(e));
			aContext.logRaw(INKFLocale.LEVEL_WARNING, msg);
		}
	}
	
	public void onQuery(IHDSReader aBody, INKFRequestContext aContext) throws Exception
	{
		String fromString=(String)aBody.getFirstValue("/range/from");
		long from=mISO8601date.parse(fromString).getTime();
		String toString=(String)aBody.getFirstValue("/range/to");
		long to=mISO8601date.parse(toString).getTime();
		final long YEAR=1000L*60*60*24*366;
		if (to-from>YEAR)  // sometimes Grafana requests big periods of time which are very slow to query?!
		{
			String msg=String.format("Grafana requested query for period of %s",Utils.formatPeriod(to-from, false));
			aContext.logRaw(INKFLocale.LEVEL_WARNING, msg);
			return;
		}
		Long interval=Long.parseLong(aBody.getFirstValue("/intervalMs").toString());
		IPolestarContext ctx = PolestarContext.createContext(aContext, "onQuery");
		
		StringBuilder sb=new StringBuilder(2048);
		sb.append("[");
		boolean already2=false;
		for (IHDSReader target : aBody.getNodes("/targets__A/targets"))
		{
			String sensorId=(String)target.getFirstValue("target");
			IPolestarQuery query = ctx.createQuery(sensorId, QueryType.AVERAGE);
			query.setStart(from);
			query.setEnd(to);
			query.setResultSetPeriod(interval);
			IPolestarQueryResultSet result=(IPolestarQueryResultSet)query.execute();
			
			if (already2) sb.append(",");
			already2=true;
			sb.append(String.format("{\"target\":\"%s\",\"datapoints\":[", sensorId));

			boolean already=false;
			for (int i=0; i<result.size(); i++)
			{
				Object value=result.getValue(i);
				if (value!=null)
				{
					if (already) sb.append(",");
					already=true;
					String vString=value.toString();
					String tString=Long.toString(result.getTimestamp(i));
					sb.append(String.format("[%s,%s]", vString,tString));
				}
			}
			
			sb.append("] }");
		}
		sb.append("]");
		
		String json=sb.toString();
		//System.out.println(json);
		INKFResponse resp=aContext.createResponseFrom(json);
		resp.setMimeType("application/json");
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		
	}
	
	public void onSearch(IHDSReader aBody, INKFRequestContext aContext) throws Exception
	{
		String target=(String)aBody.getFirstValue("/target");
		target=target.toLowerCase();
		IHDSDocument sensors=aContext.source("active:polestarSensorConfig",IHDSDocument.class);
		//System.out.println(sensors);
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensor__A");
		for (IHDSReader sensor : sensors.getReader().getNodes("/sensors/sensor"))
		{
			String id=(String)sensor.getFirstValue("id");
			String name=(String)sensor.getFirstValue("name");
			if (target!=null && (!id.toLowerCase().contains(target) && !name.toLowerCase().contains(target)))
			{	continue;
			}
					
			m.pushNode("sensor")
				.addNode("value", id)
				.addNode("text", name)
				.popNode();
		}
		
		//System.out.println(m);
		
		INKFRequest req=aContext.createRequest("active:JSONFromHDS");
		req.addArgumentByValue("operand", m.toDocument(false));
		req.addArgumentByValue("operator", "<config><removeRootElement>true</removeRootElement></config>");
		req.setRepresentationClass(String.class);
		String json=(String)aContext.issueRequest(req);
		INKFResponse resp=aContext.createResponseFrom(json);
		resp.setMimeType("application/json");
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
}
