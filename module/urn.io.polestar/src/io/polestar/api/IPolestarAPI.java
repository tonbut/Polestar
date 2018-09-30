package io.polestar.api;

import org.netkernel.layer0.nkf.NKFException;

/** Extended context for Polestar scripts to make common functions more convenient
 */
public interface IPolestarAPI
{
	/** @return true if sensor exists
	 */
	boolean sensorExists(String aSensorId) throws NKFException;
	
	/** Get the current value of a sensor
	 * @param aSensorId the sensor to get the value of
	 * @throws NKFException if sensor doesn't exist
	 */
	Object getSensorValue(String aSensorId) throws NKFException;
	
	/** Get the current value of a sensor and format as string according to specification for this sensor
	 * @param aSensorId the sensor to get the value of
	 * @throws NKFException if sensor doesn't exist
	 */
	String formatSensorValue(String aSensorId) throws NKFException;
	
	/** Get the current error of a sensor
	 * @param aSensorId the sensor to get the error for
	 * @return null if no error is current for sensor or a java.lang.String error message
	 * @throws NKFException if sensor doesn't exist
	 */
	String getSensorError(String aSensorId) throws NKFException;
	
	/** Get the time the sensor was last modified
	 * @param aSensorId the sensor to get the error for
	 * @return null if no sensor never modified otherwise milliseconds from datum as System.currentTimeMillis()
	 * @throws NKFException if sensor doesn't exist
	 */
	Long getSensorLastModified(String aSensorId) throws NKFException;
	
	/** Get the time the sensor was last updated (even if not modified)
	 * @param aSensorId the sensor to get the error for
	 * @return null if no sensor never updated otherwise milliseconds from datum as System.currentTimeMillis()
	 * @throws NKFException if sensor doesn't exist
	 */
	Long getSensorLastUpdated(String aSensorId) throws NKFException;
	
	/** Set the value of a sensor
	 * @param aSensorId the sensor to set the value of
	 * @param aValue the value to set the sensor to
	 * @throws NKFException if sensor doesn't exist
	 */
	void setSensorValue(String aSensorId, Object aValue) throws NKFException;
	
	/** Set the value of a sensor at particular time - useful for when event occurs for data in past
	 * @param aSensorId the sensor to set the value of
	 * @param aValue the value to set the sensor to
	 * @param aTime - time in millseconds since 1970
	 * @throws NKFException if sensor doesn't exist
	 */
	void setSensorValueAtTime(String aSensorId, Object aValue, long aTime) throws NKFException;

	/** Set or update the value of a sensor at particular time - useful for when event occurs for data in past
	 * @param aSensorId the sensor to set the value of
	 * @param aValue the value to set the sensor to
	 * @param aTime time in millseconds since 1970
	 * @param aWindow millisecond time window to find an existing value to replace 
	 * @return true if an existing value was updated, false if new value was inserted
	 * @throws NKFException if sensor doesn't exist
	 */
	boolean updateSensorValueAtTime(String aSensorId, Object aValue, long aTime, long aWindow) throws NKFException;
	
	/** Set or clear an error on a sensor
	 * @param aSensorId the sensor to set the value of
	 * @param aError the error message to raise on sensor or null to clear
	 * @throws NKFException if sensor doesn't exist
	 */
	void setSensorError(String aSensorId, String aError) throws NKFException;
	
	/** Increment the value of a sensor by one
	 * @param aSensorId the sensor to set the value of
	 * @return the updated value
	 * @throws NKFException if sensor doesn't exist or current sensor value isn't a Long or Integer
	 */
	Number incrementSensor(String aSensorId) throws NKFException;
	
	/** Get the value of a script state variable
	 * @param aStatePath XPath into state document, for example "var1" or "sub/var1"
	 * @param aDefault the value to return if no state exists on the given path
	 * @throws NKFException
	 */
	Object getScriptState(String aStatePath, Object aDefault) throws NKFException;
	
	/** Set the value of a script state variable
	 * @param aStatePath XPath into state document, for example "var1" or "sub/var1"
	 * @param aValue the value to set the state variable to
	 * @throws NKFException
	 */
	void setScriptState(String aStatePath, Object aValue) throws NKFException;
	
	/** Call another script
	 * @param aId id of script to call
	 * @param aArgs matched pairs of String argument names and Object argument values
	 * @return response representation from other script
	 * @throws NKFException if other script can't be found or fails
	 */
	Object callScriptById(String aId, Object... aArgs) throws NKFException;
	
	/** Call another script
	 * @param aName full name of script to call
	 * @param aArgs matched pairs of String argument names and Object argument values
	 * @return response representation from other script
	 * @throws NKFException if other script can't be found or fails
	 */
	Object callScriptByName(String aName, Object... aArgs) throws NKFException;
	
	/** Execute a query on a given sensor
	 * @param aSensorId sensor to query
	 * @param aType type of query to perform
	 * @return a query to further refine or execute
	 * @throws NKFException if sensor doesn't exist
	 */
	IPolestarQuery createQuery(String aSensorId, QueryType aType) throws NKFException;
	
	/** Detect changes in an analog value rising and falling below a trigger level with hysteresis
	 * @return a two element boolean array, first value is if value is in in true threshold, second
	 *  value is true only when result changes 
	 * @param value input value to test
	 * @param falseThreshold outside this value result will be false
	 * @param trueThreshold outside this value result will be true
	 * @param aStatePath XPath into state document to store state, for example "var1" or "sub/var1"
	 * @throws NKFException
	 */
	boolean[] analogueChangeDetect(double value, double falseThreshold, double trueThreshold, String aStatePath) throws NKFException;
	
	/** Detect changes in a boolean value with time hysteresis
	 * @return a two element boolean array, first value is if value is the output value, second
	 * value is true only when output value changes 
	 * @param value input value 
	 * @param falseDelay value must have been false for at least this long before it can become true
	 * @param trueDelay value must have been true for at least this long before it can become false
	 * @param aStatePath XPath into state document to store state, for example "var1" or "sub/var1" 
	 * @throws NKFException
	 */
	boolean[] booleanChangeDetect(boolean value, long falseDelay, long trueDelay, String aStatePath) throws NKFException;
	
	/** Only trigger a true at most once every period
	 * @return true if triggered
	 * @param period the period in milliseconds
	 * @param requireQuiet if true then period is reset each time and will only return true if no calls
	 * for at least the period
	 * @param aStatePath XPath into state document to store state, for example "var1" or "sub/var1"
	 */
	boolean atMostEvery(long period, boolean requireQuiet, String aStatePath) throws NKFException;
	
	
}
