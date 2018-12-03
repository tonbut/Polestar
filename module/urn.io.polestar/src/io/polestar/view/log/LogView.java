package io.polestar.view.log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFRequestReadOnly;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.layer0.representation.IBinaryStreamRepresentation;
import org.netkernel.layer0.representation.IHDSNode;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

public class LogView extends StandardAccessorImpl
{
	private DateFormat mDateFormat;
	
	public LogView()
	{
		this.declareThreadSafe();
		mDateFormat=new SimpleDateFormat("dd-MM-yy HH:mm:ss");
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		//IHDSDocument log=aContext.source("netkernel:/log/io.polestar/0/60",IHDSDocument.class);
		//aContext.createResponseFrom(log);
	
		
		IHDSReader params=aContext.source("httpRequest:/params",IHDSDocument.class).getReader();
		//System.out.println(params);
		if (params.getFirstNodeOrNull("_")!=null)
		{	onTableData(aContext,params);
		}
		else
		{
		
			INKFResponseReadOnly respIn=aContext.sourceForResponse("res:/io/polestar/view/log/logView.xml");
			INKFResponse respOut=aContext.createResponseFrom(respIn);
			respOut.setHeader("polestar_wrap", true);
		}
		
		
	}
	
	public void onTableData(INKFRequestContext aContext, IHDSReader aParams) throws Exception
	{
		
		Integer draw=Integer.parseInt((String)aParams.getFirstValue("draw"));
		Integer start=Integer.parseInt((String)aParams.getFirstValue("start"));
		Integer length=Integer.parseInt((String)aParams.getFirstValue("length"));
		String search=null;
		for (IHDSReader r : aParams.getNodes("*"))
		{
			String name=(String)r.getFirstValue("name()");
			if (name.equals("search[value]"))
			{	search=(String)r.getFirstValue(".");
			}
		}
		//System.out.println("search=["+search+"]");
		
		
		IHDSMutator q=HDSFactory.newDocument();
		q.addNode("start", start);
		q.addNode("length", length);
		q.addNode("search", search);
		INKFRequest req=aContext.createRequest("active:polestarLogQuery");
		req.addArgumentByValue("operator", q.toDocument(false));
		IHDSReader logResults=((IHDSDocument)aContext.issueRequest(req)).getReader();
		//System.out.println(logResults);
		
		IHDSMutator m= HDSFactory.newDocument();
		m.addNode("recordsTotal", logResults.getFirstValue("length"));
		m.addNode("recordsFiltered", logResults.getFirstValue("filterLength"));
		m.addNode("draw",draw);
		m.pushNode("data__A");
		for (IHDSReader entry : logResults.getNodes("entry"))
		{
			Long time=(Long)entry.getFirstValue("time");
			String timeString=mDateFormat.format(new Date(time));
			m.pushNode("data")
			.addNode("time",timeString)
			.addNode("level",entry.getFirstValue("level"))
			.addNode("origin",entry.getFirstValue("origin"))
			.addNode("msg",entry.getFirstValue("msg"))
			.popNode();
		}
		
		req=aContext.createRequest("active:JSONFromHDS");
		req.addArgumentByValue("operand", m.toDocument(false));
		req.addArgumentByValue("operator", "<config><removeRootElement>true</removeRootElement></config>");
		req.setRepresentationClass(IBinaryStreamRepresentation.class);
		IBinaryStreamRepresentation json=(IBinaryStreamRepresentation)aContext.issueRequest(req);
		
		INKFResponse respOut=aContext.createResponseFrom(json);
		respOut.setMimeType("text/plain");
	}
	
}
