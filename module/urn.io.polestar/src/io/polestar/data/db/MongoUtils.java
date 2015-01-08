package io.polestar.data.db;

import java.net.UnknownHostException;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;

public class MongoUtils
{
	static MongoClient sMongoClient;
	static
	{
		try
		{	sMongoClient = new MongoClient( "localhost" , 27017 );
		}
		catch (UnknownHostException e)
		{	e.printStackTrace();
		}
	}
	
	public static DBCollection getCollection(String aName) throws UnknownHostException
	{
		DB db = sMongoClient.getDB( "homemonitor" );
		return db.getCollection(aName);
	}
}
