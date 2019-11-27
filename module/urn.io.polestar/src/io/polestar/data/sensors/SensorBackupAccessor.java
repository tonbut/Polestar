package io.polestar.data.sensors;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.layer0.urii.ParsedIdentifierImpl;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import org.netkernel.util.Utils;

import io.polestar.data.db.PersistenceFactory;

public class SensorBackupAccessor extends StandardAccessorImpl
{
	public static final String BACKUP_ERROR_KEY="_ERRORS_";
	private enum State { READY, BACKUP_INPROGRESS, BACKUP_COMPLETE, BACKUP_FAILED, RESTORE_INPROGRESS, RESTORE_COMPLETE, RESTORE_FAILED };
	private State mState = State.READY;
	private File mBackupFile;
	
	public SensorBackupAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String type=aContext.getThisRequest().getArgumentValue(ParsedIdentifierImpl.ARG_ACTIVE_TYPE);
		if (type.equals("polestarSensorBackupInfo"))
		{	onSensorBackupInfo(aContext);
		}
		else if (type.equals("polestarSensorBackup"))
		{	onSensorBackup(aContext);
		}
		else if (type.equals("polestarSensorRestore"))
		{	onSensorRestore(aContext);
		}
		else if (type.equals("polestarSensorRestoreInfo"))
		{	onSensorRestoreInfo(aContext);
		}
		else if (type.equals("polestarSensorBRStatus"))
		{	onStatus(aContext);
		}
		else if (type.equals("polestarSensorBackupDownload"))
		{	onSensorBackupDownload(aContext);
		}
	}
	
	public void onSensorBackupDownload(INKFRequestContext aContext) throws Exception
	{	
		if (mBackupFile!=null)
		{	INKFResponse resp=aContext.createResponseFrom(mBackupFile.toURI().toString());
			resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		}
		else
		{	throw new NKFException("No backup available to download");
		}
	}
		
	
	public void onStatus(INKFRequestContext aContext) throws Exception
	{
		IHDSMutator m=PersistenceFactory.getPersistence(aContext).getSensorBackupRestoreStatus().getMutableClone();
		m.addNode("state", mState.toString());
		INKFResponse resp=aContext.createResponseFrom(m.toDocument(false));
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	
	public void onSensorRestore(INKFRequestContext aContext) throws Exception
	{
		if (mState==State.BACKUP_INPROGRESS || mState==State.RESTORE_INPROGRESS)
		{	throw new IllegalStateException("Cannot restore whilst another backup or restore in progress");
		}
		mState=State.RESTORE_INPROGRESS;
		
		File f=null;
		try
		{
			IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader();
			String fileURI=(String)operator.getFirstValue("fileURI");
			f=new File(URI.create(fileURI));
			String mode=(String)operator.getFirstValue("mode");
			long first=(Long)operator.getFirstValue("startTime");
			long last=(Long)operator.getFirstValue("endTime");
			List<String> sensorList=new ArrayList<>();
			for (Object sensorId : operator.getValues("to"))
			{	sensorList.add((String)sensorId);
			}
			
			PersistenceFactory.getPersistence(aContext).sensorRestore(f, mode, first, last, sensorList, aContext);
			mState=State.RESTORE_COMPLETE;
			
			//refresh sensors to show current state
			INKFRequest req=aContext.createRequest("active:polestarSensorStateRefresh");
			IHDSMutator m=HDSFactory.newDocument();
			m.pushNode("sensors");
			for (String id : sensorList)
			{	m.addNode("sensor", id);
			}
			req.addArgumentByValue("state", m.toDocument(false));
			aContext.issueRequest(req);	
			
		}
		catch (Exception e)
		{	
			aContext.logRaw(INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
			mState=State.RESTORE_FAILED;
		}
		if (f!=null) f.delete();
	}
	
	public void onSensorRestoreInfo(INKFRequestContext aContext) throws Exception
	{
		String operator=aContext.getThisRequest().getArgumentValue("operator");
		File restoreFile=new File(URI.create(operator));
		IHDSDocument restoreInfo=PersistenceFactory.getPersistence(aContext).getSensorRestoreInfo(restoreFile, aContext);
		
		//add exists check to info
		IHDSMutator m=restoreInfo.getMutableClone();
		IHDSReader config=aContext.source("active:polestarSensorConfig",IHDSDocument.class).getReader();
		
		for (IHDSMutator sensor : m.getNodes("/sensor"))
		{
			String sensorId=(String)sensor.getFirstValue("id");
			if (sensorId.equals(this.BACKUP_ERROR_KEY)) continue;
			IHDSReader sensorDef=config.getFirstNodeOrNull(String.format("key('byId','"+sensorId+"')"));
			sensor.addNode("exists", sensorDef!=null);
		}
		
		aContext.createResponseFrom(m.toDocument(false));
	}
	
	
	public void onSensorBackup(INKFRequestContext aContext) throws Exception
	{
		if (mState==State.BACKUP_INPROGRESS || mState==State.RESTORE_INPROGRESS)
		{	throw new IllegalStateException("Cannot backup whilst another backup or restore in progress");
		}
		mState=State.BACKUP_INPROGRESS;
		
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader();
		long start=(Long)operator.getFirstValue("/*/start");
		long end=(Long)operator.getFirstValue("/*/end");
		List<String> ids=new ArrayList<>();
		for (Object id : operator.getValues("/*/sensors/sensor"))
		{	ids.add((String)id);
		}
		
		File f=null;
		try
		{	
			f=PersistenceFactory.getPersistence(aContext).sensorBackup(start, end, ids, aContext);
			mState=State.BACKUP_COMPLETE;
			mBackupFile=f;
		}
		catch (Throwable e)
		{

			aContext.logRaw(INKFLocale.LEVEL_WARNING, Utils.throwableToString(e));
			mState=State.BACKUP_FAILED;
			if (f!=null) f.delete();
		}	
	}

	public void onSensorBackupInfo(INKFRequestContext aContext) throws Exception
	{
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader();
		long start=(Long)operator.getFirstValue("/*/start");
		long end=(Long)operator.getFirstValue("/*/end");
		List<Object> ids=operator.getValues("/*/sensors/sensor");
		List<String> idss =new ArrayList(ids);
		
		IHDSDocument backupInfo=PersistenceFactory.getPersistence(aContext).getSensorBackupInfo(start, end, idss, aContext);
		INKFResponse resp=aContext.createResponseFrom(backupInfo);
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
}
