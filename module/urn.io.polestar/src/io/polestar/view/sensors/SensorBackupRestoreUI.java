package io.polestar.view.sensors;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFRequestReadOnly;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.layer0.representation.ByteArrayRepresentation;
import org.netkernel.layer0.representation.IBinaryStreamRepresentation;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.view.template.TemplateWrapper;

public class SensorBackupRestoreUI extends StandardAccessorImpl
{
	private DateFormat mDateFormat=new SimpleDateFormat("dd/MM/yyyy");
	
	public SensorBackupRestoreUI()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		INKFRequestReadOnly req=aContext.getThisRequest();
		String action=null;
		if (req.argumentExists("action"))
		{	action=req.getArgumentValue("action");
			
			if (action.equals("backup"))
			{	onBackup(aContext);
			}
			else if (action.equals("restore"))
			{	onRestore(aContext);
			}
		}
	}
	
	public void onRestore(INKFRequestContext aContext) throws Exception
	{
		IHDSReader params=aContext.source("httpRequest:/params",IHDSDocument.class).getReader();
		//System.out.println(params);
		
		if (params.getFirstNodeOrNull("uploaded")!=null)
		{	onRestoreUpload(aContext);
		}
		else if ("confirm".equals(params.getFirstValueOrNull("action")))
		{	onRestoreExec(aContext,params);
		}
		else
		{	onRestoreForm(aContext);
		}
	}
	
	public void onRestoreForm(INKFRequestContext aContext) throws Exception
	{	INKFResponseReadOnly resp=aContext.sourceForResponse("res:/io/polestar/view/sensors/sensorRestoreForm.xml");
		INKFResponse respOut=aContext.createResponseFrom(resp);
		respOut.setHeader(TemplateWrapper.HEADER_WRAP, true);
	}

	public void onRestoreUpload(INKFRequestContext aContext) throws Exception
	{
		IBinaryStreamRepresentation rep=(IBinaryStreamRepresentation)aContext.source("httpRequest:/upload/uploaded",IBinaryStreamRepresentation.class);
		File tmp = File.createTempFile("polestar", ".bin");
		FileOutputStream os = new FileOutputStream(tmp);
		rep.write(os);
		os.close();
		String fileURI=tmp.toURI().toString();
		
		INKFRequest req=aContext.createRequest("active:polestarSensorRestoreInfo");
		req.addArgument("operator", fileURI);
		req.setRepresentationClass(IHDSDocument.class);
		IHDSDocument restoreInfo=(IHDSDocument)aContext.issueRequest(req);
		IHDSReader restoreInfoReader=restoreInfo.getReader();
		long oldest=(Long)restoreInfoReader.getFirstValue("oldest");
		long newest=(Long)restoreInfoReader.getFirstValue("newest");
		
		String newestString=mDateFormat.format(new Date(newest));
		String oldestString=mDateFormat.format(new Date(oldest));
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("root")
		.addNode("fileURI",fileURI)
		.addNode("oldest",oldestString)
		.addNode("newest",newestString)
		.addNode("oldestRaw",oldest)
		.addNode("newestRaw",newest);
		
		
		req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/sensors/styleSensorRestoreInfo.xsl");
		req.addArgumentByValue("operand", restoreInfo);
		req.addArgumentByValue("config", m.toDocument(false));
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
		resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
	}
	
	public void onRestoreExec(INKFRequestContext aContext, IHDSReader aParams) throws Exception
	{
		INKFRequest req=aContext.createRequest("active:polestarSensorRestore");
		IHDSMutator m=HDSFactory.newDocument();
		m.appendChildren(aParams);
		String startString=(String)aParams.getFirstValue("start");
		long start=mDateFormat.parse(startString).getTime();
		m.addNode("startTime",start);
		String endString=(String)aParams.getFirstValue("end");
		Date endDate=mDateFormat.parse(endString);
		endDate.setHours(23);
		endDate.setMinutes(59);
		endDate.setSeconds(59);
		long end=endDate.getTime();
		m.addNode("endTime",end);
		req.addArgumentByValue("operator", m.toDocument(false));
		aContext.issueRequest(req);
		
		aContext.createResponseFrom("done").setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	
	
	
	
	
	public void onBackup(INKFRequestContext aContext) throws Exception
	{
		IHDSReader params=aContext.source("httpRequest:/params",IHDSDocument.class).getReader();
		
		if ("form".equals(params.getFirstValueOrNull("/action")))
		{	onBackupSubmit(aContext,params);
		}
		else if ("confirm".equals(params.getFirstValueOrNull("/action")))
		{	onBackupExec(aContext,params);
		}
		else if ("status".equals(params.getFirstValueOrNull("/action")))
		{	onBackupStatus(aContext);
		}
		else if ("download".equals(params.getFirstValueOrNull("/action")))
		{	onBackupDownload(aContext);
		}
		else if ("delete2".equals(params.getFirstValueOrNull("/action")))
		{	onBackupDelete(aContext);
		}
		else
		{	onBackupForm(aContext);
		}
		
		//<identifier>active:polestarSensorBRStatus</identifier>
		//<identifier>active:polestarSensorBackupDownload</identifier>
		
	}
	
	public void onBackupForm(INKFRequestContext aContext) throws Exception
	{
		IHDSDocument info=aContext.source("active:polestarSensorInfo",IHDSDocument.class);
		//System.out.println(info);
		//determine first and last data
		long oldest=Long.MAX_VALUE;
		long newest=0;
		for (IHDSReader sensor : info.getReader().getNodes("/sensors/sensor"))
		{
			long first=(Long)sensor.getFirstValue("firstraw");
			long last=(Long)sensor.getFirstValue("lastraw");
			if (first>0 && first<oldest) oldest=first;
			if (last>newest) newest=last;
		}
		
		
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("root")
			.addNode("start", mDateFormat.format(new Date(oldest)))
			.addNode("end", mDateFormat.format(new Date(newest)));
			
		
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/sensors/styleSensorBackup.xsl");
		req.addArgument("operand", "active:polestarSensorConfig");
		req.addArgumentByValue("config", m.toDocument(false));
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
		resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
	}
	
	
	
	public void onBackupSubmit(INKFRequestContext aContext,IHDSReader aParams) throws Exception
	{
		//System.out.println(aParams);
		IHDSMutator bs=HDSFactory.newDocument();
		bs.pushNode("backupSpecification").pushNode("sensors");
		for (Object sensorId : aParams.getValues("/to"))
		{	bs.addNode("sensor", sensorId);
		}
		bs.popNode();
		String startString=(String)aParams.getFirstValue("start");
		long start=mDateFormat.parse(startString).getTime();
		String endString=(String)aParams.getFirstValue("end");
		Date endDate=mDateFormat.parse(endString);
		endDate.setHours(23);
		endDate.setMinutes(59);
		endDate.setSeconds(59);
		long end=endDate.getTime();
		bs.addNode("start", start);
		bs.addNode("end", end);
		String backupSpecificationString=aContext.transrept(bs.toDocument(false), String.class);
		
		INKFRequest req=aContext.createRequest("active:polestarSensorBackupInfo");
		req.addArgumentByValue("operator", bs.toDocument(false));
		req.setRepresentationClass(IHDSDocument.class);
		IHDSDocument backupInfo=(IHDSDocument)aContext.issueRequest(req);
		//System.out.println(backupInfo);
		
		req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/sensors/styleSensorBackupInfo.xsl");
		req.addArgumentByValue("operand", backupInfo);
		req.addArgumentByValue("config", backupSpecificationString);
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
		resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
		
	}
	
	public void onBackupExec(INKFRequestContext aContext,IHDSReader aParams) throws Exception
	{
		String backupSpecificationString=(String)aParams.getFirstValue("backupSpecification");
		IHDSDocument backupSpecification=aContext.transrept(backupSpecificationString, IHDSDocument.class);
		INKFRequest req=aContext.createRequest("active:polestarSensorBackup");
		req.addArgumentByValue("operator", backupSpecification);
		aContext.issueRequest(req);
		aContext.createResponseFrom("done").setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
		
	public void onBackupDownload(INKFRequestContext aContext) throws Exception
	{	
		INKFRequest req=aContext.createRequest("active:polestarSensorBackupDownload");
		req.setRepresentationClass(String.class);
		String backupFileURI = (String)aContext.issueRequest(req);
		
		IBinaryStreamRepresentation rep=aContext.source(backupFileURI,IBinaryStreamRepresentation.class);
		INKFResponse response=aContext.createResponseFrom(rep);
		response.setMimeType("application/octet-stream");
		response.setHeader("httpResponse:/header/Content-Disposition", "attachment; filename=polestar_sensors.zip");
	}
	
	public void onBackupDelete(INKFRequestContext aContext) throws Exception
	{	
		
		INKFRequest req=aContext.createRequest("active:polestarSensorBackupDownload");
		req.setRepresentationClass(String.class);
		String backupFileURI = (String)aContext.issueRequest(req);
		File f=new File(URI.create(backupFileURI));
		boolean deleted=f.delete();
		String msg="backup file delete "+backupFileURI+" "+deleted;
		aContext.logRaw(INKFLocale.LEVEL_INFO, msg);
		
		aContext.createResponseFrom("done").setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	public void onBackupStatus(INKFRequestContext aContext) throws Exception
	{
		IHDSDocument backupStatus=aContext.source("active:polestarSensorBRStatus",IHDSDocument.class);
		
		INKFRequest req=aContext.createRequest("active:JSONFromHDS");
		req.addArgumentByValue("operand", backupStatus);
		req.addArgumentByValue("operator", "<config><removeRootElement>true</removeRootElement></config>");
		INKFResponseReadOnly respIn=aContext.issueRequestForResponse(req);
		INKFResponse respOut=aContext.createResponseFrom(respIn);
	}
	
	
	
}
