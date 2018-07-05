package io.polestar.api;

import org.netkernel.layer0.nkf.NKFException;

/** A sensor query to perform a historical query on sensor state */
public interface IPolestarQuery
{
	/** Set start time for period in which query is processed in milliseconds from datum (i.e. System.currentTimeMillis() ).
	 * Default start time is exactly one day before current time
	 * @param aStartTime start time
	 */
	IPolestarQuery setStart(long aStartTime);
	
	/** Set end time for period in which query is processed in milliseconds from datum (i.e. System.currentTimeMillis() ).
	 * Default end time is now.
	 * @param aEndTime end time
	 */
	IPolestarQuery setEnd(long aEndTime);
	
	/** Set a parameter for a specific query
	 * @param aParameter value for specific query type.
	 */
	IPolestarQuery setQueryParameter(Object aParameter) throws NKFException;
	
	/** Set a matcher for a specific query
	 * @param aMatcher matcher for specific query type.
	 */
	IPolestarQuery setQueryMatcher(IPolestarMatcher aMatcher) throws NKFException;
	
	/** Execute the query
	 * @throws NKFException if query fails
	 * @return value, timestamp, or duration from query
	 */
	Object execute() throws NKFException;
}
