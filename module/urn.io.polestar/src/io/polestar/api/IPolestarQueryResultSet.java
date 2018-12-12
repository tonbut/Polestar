package io.polestar.api;

/** Queries will return this type when query results in multiple samples over time */
public interface IPolestarQueryResultSet
{
	/** size of result set */
	int size();
	/** return timestamp for this index */
	long getTimestamp(int aIndex);
	/** return value for this index */
	Object getValue(int aIndex);
}
