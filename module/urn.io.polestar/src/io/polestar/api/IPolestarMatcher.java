package io.polestar.api;

/** A Matcher for passing to specific queries */
public interface IPolestarMatcher
{
	/** match the given value at timestamp
	 * @param aValue the value of a sensor
	 * @param aTimestamp at this timestamp
	 * @return true if value matches, false otherwise
	 */
	boolean matches(Object aValue, long aTimestamp);
}
