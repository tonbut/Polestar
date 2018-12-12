package io.polestar.api;

import org.netkernel.layer0.nkf.NKFException;

/** A sensor query to perform a historical query on sensor state */
public interface IPolestarQuery
{
	/** Set start time for period in which query is processed in milliseconds from datum (i.e. System.currentTimeMillis() ).
	 * Negative values offset from current time.
	 * Default start time is exactly one day before current time.
	 * @param aStartTime start time
	 */
	IPolestarQuery setStart(long aStartTime);
	
	/** Set end time for period in which query is processed in milliseconds from datum (i.e. System.currentTimeMillis() ).
	 * Negative values offset from current time.
	 * Default end time is current time.
	 * @param aEndTime end time
	 */
	IPolestarQuery setEnd(long aEndTime);
	
	/** Set a parameter for a specific query
	 * @param aParameter value for specific query type.
	 */
	IPolestarQuery setQueryParameter(Object aParameter) throws NKFException;
	
	/** Set a period in milliseconds to sub-divide time between query start and end and return back multiple results.
	 * If not specified then whole period is sampled and a single result returned.
	 * Queries that return time will return time offset from start of period when setResultSetPeriod is used.
	 * @param aPeriod period in milliseconds
	 * @throws NKFException if period cannot be set on this type of query
	 */
	IPolestarQuery setResultSetPeriod(long aPeriod) throws NKFException;
	
	/** Set a matcher for a specific query
	 * @param aMatcher matcher for specific query type.
	 */
	IPolestarQuery setQueryMatcher(IPolestarMatcher aMatcher) throws NKFException;
	
	/** Execute query over a series of start-to-end periods and merge results.
	 * Merge operation defaults to AVERAGE but can be specified with setTimeMergeOp()
	 */
	IPolestarQuery setTimeMerge(int aPeriodCount) throws NKFException;
	
	/** Specifies how multiple resultsets are merged 
	 * Merge operation defaults to AVERAGE but can be specified with setTimeMergeOp()
	 */
	IPolestarQuery setTimeMergeOp(QueryType aOp) throws NKFException;

	/** Set a period in milliseconds to separate merge periods. By default merge
	 * periods are consecutive, so TimeMergePeriod is equal to (End-Start) 
	 */
	IPolestarQuery setTimeMergePeriod(long aPeriod) throws NKFException;

	
	/** Execute the query
	 * @throws NKFException if query fails
	 * @return value, timestamp, duration, or IPolestarQueryResultSet from query
	 */
	Object execute() throws NKFException;
}
