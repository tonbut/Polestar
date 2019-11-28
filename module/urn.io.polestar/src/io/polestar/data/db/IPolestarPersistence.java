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
	/** Called once at startup to do any setup/configuration needed. This should
	 * be idempotent as will be called on each restart
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void prime(INKFRequestContext aContext) throws NKFException;
	
	/*******************/
	/* Logging methods */
	/*******************/
	
	/** Log a message into persistent log
	 * @param aOrigin the endpoint or script which created the message
	 * @param aLevel the log level as per org.netkernel.layer0.nkf.INKFLocale
	 * @param aMessage the message text to log
	 * @param aContext NetKernel context
	 */
	void log(String aOrigin, int aLevel, String aMessage, INKFRequestContext aContext);
	
	/** Retrieve log messages between start and end with optional filter
	 * @param start the start time in milliseconds from datum
	 * @param length the end time in milliseconds from datum
	 * @param search an optional search filter - does message contain this text, may be null
	 * @param aContext NetKernel context
	 * @return a HDSDocument of type LogQueryResult
	 * @throws NKFException thrown if anything goes wrong
	 */
	IHDSDocument logQuery(long start, long length, String search, INKFRequestContext aContext) throws NKFException;
	
	/** Remove old log entries
	 * @param aTime the time before which all log entries should be deleted - milliseconds from datum
	 * @param aContext NetKernel context
	 * @return the number of entries deleted
	 * @throws NKFException thrown if anything goes wrong
	 */
	int logRemoveOlderThan(long aTime, INKFRequestContext aContext) throws NKFException;
	
	/*******************/
	/* Authentication methods */
	/*******************/
	
	/** Retrieve authentication document
	 * @param aContext NetKernel context
	 * @return a HDSDocument of type AuthenticationState
	 * @throws NKFException thrown if anything goes wrong
	 */
	IHDSDocument getAuthentication(INKFRequestContext aContext) throws NKFException;
	
	/** Update authentication document
	 * @param aState a HDSDocument of type AuthenticationState
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void setAuthentication(IHDSDocument aState, INKFRequestContext aContext) throws NKFException;
	
	/*******************/
	/* Script methods */
	/*******************/
	
	/** Retrieve a list of scripts with their metadata
	 * @param aContext NetKernel context
	 * @return a HDSDocument of type ScriptList
	 * @throws NKFException thrown if anything goes wrong
	 */
	IHDSDocument getScriptList(INKFRequestContext aContext) throws NKFException;
	
	/** Move the script to a specific position in list
	 * @param aScriptId the script to move
	 * @param aNewPosition the new position
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void setScriptPosition(long aScriptId, int aNewPosition, INKFRequestContext aContext) throws NKFException;
	
	/** Retrieve the state of a single script
	 * @param aScriptId the id of script
	 * @param aContext NetKernel context
	 * @return a HDSDocument of type Script
	 * @throws NKFException thrown if anything goes wrong
	 */
	IHDSDocument getScript(long aScriptId, INKFRequestContext aContext) throws NKFException;
	
	/** Set the state of a single script
	 * @param aScriptId the id of script
	 * @param aSaveState a HDSDocument of type Script
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void setScript(long aScriptId, IHDSDocument aSaveState, INKFRequestContext aContext) throws NKFException;
	
	/** Create a new script
	 * @param aNewState a HDSDocument of type Script (must contain a unique id)
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void addScript(IHDSDocument aNewState, INKFRequestContext aContext) throws NKFException;
	
	/** Delete a script
	 * @param aScriptId the id of script
	 * @param aContext NetKernel context
	 * @return true of script was successfully deleted
	 * @throws NKFException thrown if anything goes wrong
	 */
	boolean deleteScript(long aScriptId, INKFRequestContext aContext) throws NKFException;
	
	/** Find a script with a specific name
	 * @param aName the name to find
	 * @param aContext NetKernel context
	 * @return the id of script with that name or null
	 * @throws NKFException thrown if anything goes wrong
	 */
	Long findScriptWithName(String aName, INKFRequestContext aContext) throws NKFException;
	
	/*******************/
	/* Script statistic methods */
	/*******************/
	
	/** Reset to statistics return by getScriptStats to initialised status
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void resetScriptStats(INKFRequestContext aContext) throws NKFException;
	
	/** Return the execution and editing statistics for all script
	 * @param aContext NetKernel context
	 * @return a HDSDocument of type ScriptStats
	 * @throws NKFException thrown if anything goes wrong
	 */
	IHDSDocument getScriptStats(INKFRequestContext aContext) throws NKFException;
	
	/** Update script stats when a script is executed or edited
	 * @param aScriptId the id of the script to update
	 * @param aIsEdit true if script was just edited
	 * @param aError true if the script throw an exception when executed
	 * @param aTime the time of execution or editing
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void updateScriptStats(long aScriptId, boolean aIsEdit, String aError, long aTime, INKFRequestContext aContext) throws NKFException;
	
	/*******************/
	/* Sensor current state methods */
	/*******************/
	
	/** Retrieves the current cached state of all sensors
	 * @param aContext NetKernel context
	 * @return a HDSDocument of type SensorState
	 * @throws NKFException thrown if anything goes wrong
	 */
	IHDSDocument getCurrentSensorState(INKFRequestContext aContext) throws NKFException;
	
	/** Sets the current cached state of all sensors
	 * @param aNewState a HDSDocument of type SensorState
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void setCurrentSensorState(IHDSDocument aNewState, INKFRequestContext aContext) throws NKFException;

	/*******************/
	/* General sensor methods */
	/*******************/
	
	/** Delete the history and storage associated with a sensor
	 * @param aSensorId the id of sensor to delete
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void deleteSensor(String aSensorId, INKFRequestContext aContext) throws NKFException;
	
	/**
	 * @param aContext NetKernel context
	 * @return
	 * @throws NKFException thrown if anything goes wrong
	 */
	IHDSDocument getSensorInfo(INKFRequestContext aContext) throws NKFException;
	
	/** Update the value of a sensor in historical record
	 * @param aSensorId the id of sensor
	 * @param aNewValue the value of the sensor
	 * @param aUpdateTime the time at which the sensors value changed - milliseconds from datum
	 * @param aUpdateWindow optional update window in milliseconds in which a possible
	 *  existing value should be updated
	 * @param aContext NetKernel context
	 * @return true if existing value was replaced rather than adding a new value
	 * @throws NKFException thrown if anything goes wrong
	 */
	boolean setSensorValue(String aSensorId, Object aNewValue, long aUpdateTime, Long aUpdateWindow, INKFRequestContext aContext)  throws NKFException;
	
	/** Update the error state of a sensor in historical record
	 * @param aSensorId the id of sensor
	 * @param aError the error message or null to clear an error
	 * @param aLevel the level of the error
	 * @param aTime the time at which the sensors error changed - milliseconds from datum
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void setSensorError(String aSensorId, String aError, int aLevel, long aTime, INKFRequestContext aContext) throws NKFException;
	
	/** Get summary of a sensors error history from a time up till now
	 * @param aSensorId the id of sensor
	 * @param StartTime the time to start analysing sensor history
	 * @param aContext NetKernel context
	 * @return a HDSDocument of type SensorErrorSummary
	 * @throws NKFException thrown if anything goes wrong
	 */
	IHDSDocument getSensorErrorSummary(String aSensorId, long StartTime, INKFRequestContext aContext) throws NKFException;
	
	/*******************/
	/* Sensor query methods */
	/*******************/
	
	/** Create a forward (old to new) dataset iterator over sensor values
	 * @param aSensorId the id of sensor
	 * @param aStart the start time in milliseconds from datum
	 * @param aEnd the end time in milliseconds from datum
	 * @param aFragment optional fragment of map valued sensor
	 * @param aContext NetKernel context
	 * @return a dataset iterator
	 * @throws NKFException thrown if anything goes wrong
	 */
	ICollectionIterator getSensorForwardIterator(String aSensorId, long aStart, long aEnd, String aFragment, INKFRequestContext aContext)  throws NKFException;
	
	/** Create a forward (old to new) dataset iterator over sensor errors
	 * @param aSensorId the id of sensor
	 * @param aStart the start time in milliseconds from datum
	 * @param aEnd the end time in milliseconds from datum
	 * @param aContext NetKernel context
	 * @return a dataset iterator
	 * @throws NKFException thrown if anything goes wrong
	 */
	ICollectionIterator getErrorForwardIterator(String aSensorId, long aStart, long aEnd, INKFRequestContext aContext) throws NKFException;
	
	/** Create a backward (new to old) dataset iterator over sensor values
	 * @param aSensorId the id of sensor
	 * @param aStart the start time in milliseconds from datum
	 * @param aEnd the end time in milliseconds from datum
	 * @param aFragment optional fragment of map valued sensor
	 * @param aContext NetKernel context
	 * @return a dataset iterator
	 * @throws NKFException thrown if anything goes wrong
	 */
	ICollectionIterator getSensorBackwardIterator(String aSensorId, long aStart, long aEnd, String aFragment, INKFRequestContext aContext)  throws NKFException;
	
	/** Create a backward (new to old) dataset iterator over sensor errors
	 * @param aSensorId the id of sensor
	 * @param aStart the start time in milliseconds from datum
	 * @param aEnd the end time in milliseconds from datum
	 * @param aContext NetKernel context
	 * @return a dataset iterator
	 * @throws NKFException thrown if anything goes wrong
	 */
	ICollectionIterator getErrorBackwardIterator(String aSensorId, long aStart, long aEnd, INKFRequestContext aContext) throws NKFException;
	
	/*******************/
	/* Sensor Back/Restore methods */
	/*******************/
	
	/** Get the execution state of a currently executing backup or restore process
	 * @return a HDSDocument of type SensorBackupRestoreStatus
	 */
	IHDSDocument getSensorBackupRestoreStatus();
	
	/** Analyse backup and return information of size of data
	 * @param aStart the start time in milliseconds from datum
	 * @param aEnd the end time in milliseconds from datum
	 * @param aSensorIds a list of sensor ids
	 * @param aContext NetKernel context
	 * @return a HDSDocument of type SensorBackupInfo
	 * @throws NKFException thrown if anything goes wrong
	 */
	IHDSDocument getSensorBackupInfo(long aStart, long aEnd, List<String> aSensorIds, INKFRequestContext aContext) throws NKFException;
	
	/** Generate backup as ZIP file
	 * @param aStart the start time in milliseconds from datum
	 * @param aEnd the end time in milliseconds from datum
	 * @param aSensorIds a list of sensor ids
	 * @param aContext NetKernel context
	 * @return a file
	 * @throws NKFException thrown if anything goes wrong
	 */
	File sensorBackup(long aStart, long aEnd, List<String> aSensorIds, INKFRequestContext aContext) throws NKFException;
	
	/** Analyse backup file
	 * @param aRestoreFile the backup file to restore
	 * @param aContext NetKernel context
	 * @return a HDSDocument of type SensorRestoreInfo
	 * @throws NKFException thrown if anything goes wrong
	 */
	IHDSDocument getSensorRestoreInfo(File aRestoreFile, INKFRequestContext aContext) throws NKFException;
	
	/** Apply sensor restore file
	 * @param aRestoreFile the backup file to restore
	 * @param aMode mode of dealing with conflicts - Replace/Selective/Add
	 * @param aStart the start time in milliseconds from datum, data before start will be ignored
	 * @param aEnd the end time in milliseconds from datum, data after the end will be ignored
	 * @param aSensors a list of sensor ids to restore, other sensors will be ignored
	 * @param aContext NetKernel context
	 * @throws NKFException thrown if anything goes wrong
	 */
	void sensorRestore(File aRestoreFile, String aMode, long aStart, long aEnd, List<String> aSensors, INKFRequestContext aContext) throws NKFException;
	
}
	