package io.polestar.data.api;

import java.util.List;

import io.polestar.api.IPolestarQueryResultSet;

public class PolestarQueryResultSet implements IPolestarQueryResultSet
{
	private final List<Long> mTimes;
	private final List<Object> mValues;
	
	public PolestarQueryResultSet(List<Long> aTimes, List<Object> aValues)
	{	mTimes=aTimes;
		mValues=aValues;
	}

	@Override
	public int size()
	{	return mTimes.size();
	}

	@Override
	public long getTimestamp(int aIndex)
	{	return mTimes.get(aIndex);
	}

	@Override
	public Object getValue(int aIndex)
	{	return mValues.get(aIndex);
	}
	
	public String toString()
	{
		StringBuilder sb=new StringBuilder();
		for (int i=0; i<size(); i++)
		{
			sb.append(getTimestamp(i));
			sb.append(" ");
			sb.append(getValue(i));
			sb.append("\n");
		}
		return sb.toString();
	}
}
