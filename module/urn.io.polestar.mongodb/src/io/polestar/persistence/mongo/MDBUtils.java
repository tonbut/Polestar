package io.polestar.persistence.mongo;

import java.util.List;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class MDBUtils
{
	public static String hexString(long aLong)
	{	return String.format("%016X",aLong);
	}
	
	/** Format given period as a human readable string
	 * @param aPeriod
	 * @param doSeconds if true display seconds
	 * @return
	 */
	public static String formatPeriod(long aPeriod)
	{
		long period=aPeriod;
	    long second=1000;
	    long minute=second*60;
	    long hour=minute*60;
	    long day=hour*24;
	    long week=day*7;
	    long year=day*365;
	    String message="";
	    long years=period/year;
	    do
	    {	boolean already=false;
		    if (years>0)
		    {   message+=years;
		        message+=(years==1)?" year":" years";
		        period-=years*year;
		        already=true;
		    }
		    long weeks=period/week;
		    if (weeks>0 || years>0)
		    {   if (already) message+=", ";
		    	message+=weeks;
		        message+=(weeks==1)?" week":" weeks";
		        period-=weeks*week;
		        if (years>0) break;
		        already=true;
		    }
		    long days=period/day;
		    if (days>0 || weeks>0 || years>0)
		    {   if (already) message+=", ";
		    	message+=days;
		        message+=(days==1)?" day":" days";
		        period-=days*day;
		        if (weeks>0) break;
		        already=true;
		    }
		    long hours=period/hour;
		    if (hours>0 || days>0 || weeks>0 || years>0)
		    {   if (already) message+=", ";
		    	message+=hours;
		        message+=(hours==1)?" hour":" hours";
		        period-=hours*hour;
		        if (days>0) break;
		        already=true;
		    }
		    long minutes=period/minute;
		    if (minutes>0 || hours>0 || days>0 || weeks>0 || years>0)
		    {   if (already) message+=", ";
		    	message+=minutes;
		        message+=(minutes==1)?" minute":" minutes";
		        period-=minutes*minute;
		        if (hours>0) break;
		        already=true;
		    }
		    {   if (already) message+=", ";
		        long seconds=period/second;
		        {   message+=seconds;
		            message+=(seconds==1)?" second":" seconds";
		        }
		    }
	    } while (false);
	    return message;
	}
	
	public static BasicDBObject getQuery(List<String> ids, long start, long end)
	{
		//mongodb 2.4 doesn't support $eq
		BasicDBList inO=new BasicDBList();
		for (String id : ids)
		{	inO.add(id);
		}
		BasicDBObject idEqualsO=new BasicDBObject("i", new BasicDBObject("$in",inO));
		BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",start));
		BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",end));
		BasicDBList listO=new BasicDBList();
		listO.add(idEqualsO);
		listO.add(startO);
		listO.add(endO);
		BasicDBObject queryO=new BasicDBObject("$and", listO);
		return queryO;
	}
	
	public static BasicDBObject getQuery(long aStart, long aEnd) throws Exception
	{	BasicDBObject startO=new BasicDBObject("t", new BasicDBObject("$gte",aStart));
		BasicDBObject endO=new BasicDBObject("t", new BasicDBObject("$lt",aEnd));
		BasicDBList listO=new BasicDBList();
		listO.add(startO);
		listO.add(endO);
		BasicDBObject queryO=new BasicDBObject("$and", listO);
		return queryO;
	}
}
