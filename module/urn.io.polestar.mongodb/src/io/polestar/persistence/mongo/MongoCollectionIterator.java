package io.polestar.persistence.mongo;

import java.net.UnknownHostException;
import java.util.Map;

import org.netkernel.layer0.nkf.NKFException;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import io.polestar.data.api.ICollectionIterator;

public class MongoCollectionIterator
{
	enum State { INIT, PRE, MIDDLE, END }; 
	
	public static final String ERROR_COLLECTION="errors";
	
	private static Object getSensorValue(DBObject aCapture, String aFragment)
	{	Object v=aCapture.get("v");
		if (v instanceof Map)
		{	v=((Map)v).get(aFragment);		
		}
		return v;
	}
	
	public static ICollectionIterator getSensorForwardIterator(final String aSensor, final long aStart, final long aEnd, final String fragment, MongoPersistence aPersistence)
	{
		return new ICollectionIterator()
		{
			private DBCursor mCursor;
			private State mState=State.INIT;
			private Long mTime;
			private Object mValue;
			
			@Override
			public void reset()
			{
				mState=State.INIT;
			}
			
			@Override
			public boolean next()
			{	boolean result=false;
				try
				{
					if (mState==State.INIT)
					{
						DBCollection col=aPersistence.getCollectionForSensor(aSensor);
						BasicDBObject startI=new BasicDBObject("t", new BasicDBObject("$lt",aStart));
						DBCursor cursor = col.find(startI).sort(new BasicDBObject("t",-1)).limit(1);
						if (cursor.hasNext())
						{	DBObject capture=cursor.next();
							mTime=(Long)capture.get("t");
							mValue=getSensorValue(capture,fragment);
						}
						else
						{	mTime=null;
							mValue=null;
						}
						mState=State.PRE;
						result=true;
					}
					else if (mState==State.PRE)
					{	DBCollection col=aPersistence.getCollectionForSensor(aSensor);
						BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",aStart));
						BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",aEnd));
						BasicDBList listO=new BasicDBList();
						listO.add(startO);
						listO.add(endO);
						BasicDBObject queryO=new BasicDBObject("$and", listO);
						mCursor = col.find(queryO);
						if (mCursor.hasNext())
						{	mState=State.MIDDLE;
							result=true;
						}
						else
						{	mState=State.END;
							result=false;
						}
					}
					else if (mState==State.END)
					{	result=false;
					}
					
					if (mState==State.MIDDLE)
					{	DBObject capture=mCursor.next();
						mTime=(Long)capture.get("t");
						mValue=getSensorValue(capture,fragment);
						result=true;
						if (!mCursor.hasNext())
						{	mState=State.END;
						}
					}
				}
				catch (UnknownHostException e)
				{
				}
				return result;
			}
			
			@Override
			public Object getValue()
			{	return mValue;
			}
			
			@Override
			public long getTime()
			{	return mTime;
			}
		};
	}
	
	public static ICollectionIterator getSensorBackwardIterator(final String aSensor, final long aStart, final long aEnd, final String fragment, MongoPersistence aPersistence)
	{
		return new ICollectionIterator()
		{
			private DBCursor mCursor;
			private State mState=State.INIT;
			private Long mTime;
			private Object mValue;
			
			@Override
			public void reset()
			{	mState=State.INIT;
			}
			
			@Override
			public boolean next()
			{	boolean result=false;
				try
				{
					if (mState==State.INIT)
					{
						DBCollection col=aPersistence.getCollectionForSensor(aSensor);
						BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",aStart));
						BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",aEnd));
						BasicDBList listO=new BasicDBList();
						listO.add(startO);
						listO.add(endO);
						BasicDBObject queryO=new BasicDBObject("$and", listO);
						mCursor = col.find(queryO).sort(new BasicDBObject("t",-1));
						if (mCursor.hasNext())
						{	mState=State.MIDDLE;
							result=true;
						}
						else
						{	mState=State.PRE;
						}
					}
					if (mState==State.MIDDLE)
					{	DBObject capture=mCursor.next();
						mTime=(Long)capture.get("t");
						mValue=getSensorValue(capture,fragment);
						result=true;
						if (!mCursor.hasNext())
						{	mState=State.PRE;
						}
					}
					
					if (mState==State.PRE)
					{
						DBCollection col=aPersistence.getCollectionForSensor(aSensor);
						BasicDBObject startI=new BasicDBObject("t", new BasicDBObject("$lt",aStart));
						DBCursor cursor = col.find(startI).sort(new BasicDBObject("t",-1)).limit(1);
						if (cursor.hasNext())
						{	DBObject capture=cursor.next();
							mTime=(Long)capture.get("t");
							mValue=getSensorValue(capture,fragment);
						}
						else
						{	mTime=null;
							mValue=null;
						}
						mState=State.END;
					}
					
					if (mState==State.END)
					{	result=false;
					}
				}
				catch (UnknownHostException e)
				{
				}
				//System.out.println("NEXT "+result);
				return result;
			}
			
			@Override
			public Object getValue()
			{	return mValue;
			}
			
			@Override
			public long getTime()
			{	return mTime;
			}
		};
	}
	
	public static ICollectionIterator getErrorForwardIterator(final String aSensor, final long aStart, final long aEnd, MongoPersistence aPersistence)
	{
		return new ICollectionIterator()
		{
			private DBCursor mCursor;
			private State mState=State.INIT;
			private Long mTime;
			private Object mValue;
			
			@Override
			public void reset()
			{
				mState=State.INIT;
			}
			
			@Override
			public boolean next()
			{	boolean result=false;
				try
				{
					if (mState==State.INIT)
					{
						DBCollection col=aPersistence.getCollection(ERROR_COLLECTION);
						
						BasicDBList inO=new BasicDBList();
						inO.add(aSensor);
						BasicDBObject idEqualsO=new BasicDBObject("i", new BasicDBObject("$in",inO));
						BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$lt",aStart));
						BasicDBList listO=new BasicDBList();
						listO.add(idEqualsO);
						listO.add(startO);
						BasicDBObject queryO=new BasicDBObject("$and", listO);
						DBCursor cursor = col.find(queryO).sort(new BasicDBObject("t",-1)).limit(1);
						
						if (cursor.hasNext())
						{	DBObject capture=cursor.next();
							mTime=(Long)capture.get("t");
							mValue=capture.get("l");
						}
						else
						{	mTime=null;
							mValue=0;
						}
						mState=State.PRE;
						result=true;
					}
					else if (mState==State.PRE)
					{	
											
						DBCollection col=aPersistence.getCollection(ERROR_COLLECTION);
						BasicDBList inO=new BasicDBList();
						inO.add(aSensor);
						BasicDBObject idEqualsO=new BasicDBObject("i", new BasicDBObject("$in",inO));
						BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",aStart));
						BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",aEnd));
						BasicDBList listO=new BasicDBList();
						listO.add(idEqualsO);
						listO.add(startO);
						listO.add(endO);
						BasicDBObject queryO=new BasicDBObject("$and", listO);
						mCursor = col.find(queryO);
						
						if (mCursor.hasNext())
						{	mState=State.MIDDLE;
							result=true;
						}
						else
						{	mState=State.END;
							result=false;
						}
					}
					else if (mState==State.END)
					{	result=false;
					}
					
					if (mState==State.MIDDLE)
					{	DBObject capture=mCursor.next();
						mTime=(Long)capture.get("t");
						mValue=capture.get("l");
						result=true;
						if (!mCursor.hasNext())
						{	mState=State.END;
						}
					}
				}
				catch (UnknownHostException e)
				{
				}
				return result;
			}
			
			@Override
			public Object getValue()
			{	return mValue;
			}
			
			@Override
			public long getTime()
			{	return mTime;
			}
		};
	}

	public static ICollectionIterator getErrorBackwardIterator(final String aSensor, final long aStart, final long aEnd, MongoPersistence aPersistence) throws NKFException
	{
		throw new NKFException("Not Implemented");
		//return null;
	}
	
}
