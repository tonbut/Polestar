package io.polestar.data.adapter;

public interface IResultSet
{
	boolean hasNext();
	IResultRow next();
	void close();
}
