package io.polestar.data.db;

import java.io.File;
import java.util.List;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.mod.hds.IHDSDocument;

import io.polestar.data.api.ICollectionIterator;

/** Persistence interface that is for all sensor, script, logging etc storage
 **/
public interface IPolestarPersistence
{
	void prime(INKFRequestContext aContext) throws NKFException;
	
	/* Logging methods */
	void log(String aOrigin, int aLevel, String aMessage, INKFRequestContext aContext);
	IHDSDocument logQuery(long start, long length, String search, INKFRequestContext aContext) throws NKFException;
	int logRemoveOlderThan(long aTime, INKFRequestContext aContext) throws NKFException;
	
	/* Authentication methods */
	IHDSDocument getAuthentication(INKFRequestContext aContext) throws NKFException;
	void setAuthentication(IHDSDocument aState, INKFRequestContext aContext) throws NKFException;
	
	/* Script methods */
	IHDSDocument getScriptList(INKFRequestContext aContext) throws NKFException;
	void setScriptPosition(long aScriptId, int aNewPosition, INKFRequestContext aContext) throws NKFException;
	IHDSDocument getScript(long aScriptId, INKFRequestContext aContext) throws NKFException;
	void setScript(long aScriptId, IHDSDocument aSaveState, INKFRequestContext aContext) throws NKFException;
	void addScript(IHDSDocument aNewState, INKFRequestContext aContext) throws NKFException;
	boolean deleteScript(long aScriptId, INKFRequestContext aContext) throws NKFException;
	Long findScriptWithName(String aName, INKFRequestContext aContext) throws NKFException;
	
	/* Script statistic methods */
	void resetScriptStats(INKFRequestContext aContext) throws NKFException;
	IHDSDocument getScriptStats(INKFRequestContext aContext) throws NKFException;
	void updateScriptStats(long aScriptId, boolean aIsEdit, String aError, long aTime) throws NKFException;
	
	/* Sensor current state methods */
	IHDSDocument getCurrentSensorState(INKFRequestContext aContext) throws NKFException;
	void setCurrentSensorState(IHDSDocument aNewState, INKFRequestContext aContext) throws NKFException;

	/* General sensor methods */
	void deleteSensor(String aSensorId, INKFRequestContext aContext) throws NKFException;
	IHDSDocument getSensorInfo(INKFRequestContext aContext) throws NKFException;
	boolean setSensorValue(String aSensorId, Object aNewValue, long aUpdateTime, Long aUpdateWindow, INKFRequestContext aContext)  throws NKFException;
	void setSensorError(String aSensorId, String aError, int aLevel, long aTime, INKFRequestContext aContext) throws NKFException;
	IHDSDocument getSensorErrorSummary(String aSensorId, long StartTime, INKFRequestContext aContext) throws NKFException;
	
	/* Sensor query methods */
	ICollectionIterator getSensorForwardIterator(String aSensorId, long aStart, long aEnd, String aFragment, INKFRequestContext aContext)  throws NKFException;
	ICollectionIterator getErrorForwardIterator(String aSensorId, long aStart, long aEnd, INKFRequestContext aContext) throws NKFException;
	ICollectionIterator getSensorBackwardIterator(String aSensorId, long aStart, long aEnd, String aFragment, INKFRequestContext aContext)  throws NKFException;
	ICollectionIterator getErrorBackwardIterator(String aSensorId, long aStart, long aEnd, INKFRequestContext aContext) throws NKFException;
	
	/* Sensor Back/Restore methods */
	IHDSDocument getSensorBackupRestoreStatus();
	IHDSDocument getSensorBackupInfo(long aStart, long aEnd, List<String> aSensorIds, INKFRequestContext aContext) throws NKFException;
	File sensorBackup(long aStart, long aEnd, List<String> aSensorIds, INKFRequestContext aContext) throws NKFException;
	IHDSDocument getSensorRestoreInfo(File aRestoreFile, INKFRequestContext aContext) throws NKFException;
	void sensorRestore(File aRestoreFile, String aMode, long aStart, long aEnd, List<String> aSensors, INKFRequestContext aContext) throws NKFException;
	
}
	