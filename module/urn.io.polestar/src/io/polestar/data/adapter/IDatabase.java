package io.polestar.data.adapter;

public interface IDatabase
{
	IQuery getQuery();
	IQuery getLogQuery(int aOffset, int aCount, String aFilter);
}
