package io.polestar.data.adapter;

import java.util.List;

public interface IQuery
{
	IQuery setTable(String aTableName);
	IQuery setStartTime(long aStart);
	IQuery setEndTime(long aEnd);
	IQuery setInList(List<String> aIn); //used by sensor error
	IQuery reverseSort(boolean aReverse);
	IResultSet execute();
}
