/* Copyright 2015 1060 Research Ltd

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package io.polestar.data.poll;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.netkernel.layer0.nkf.*;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.util.MonitorUtils;

public class PeriodPollAccessor extends StandardAccessorImpl
{
	private Map<String,AtomicBoolean> mBusyMap = new HashMap<String, AtomicBoolean>();
	private Map<String,AtomicInteger> mFirstErrorMap = new HashMap<String, AtomicInteger>();
	private static PeriodPollAccessor sInstance;
	
	
	public PeriodPollAccessor()
	{	declareThreadSafe();
		sInstance=this;
	}
	
	public static IHDSDocument getPollingState()
	{	IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("polls");
		for (Map.Entry<String, AtomicBoolean> e : sInstance.mBusyMap.entrySet())
		{	m.pushNode("poll");
			m.addNode("period",e.getKey());
			boolean busy=e.getValue().get();
			int count=sInstance.mFirstErrorMap.get(e.getKey()).get();
			m.addNode("error", busy && count>0 );
			m.addNode("count", count);
			m.popNode();
		}
		return m.toDocument(false);
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{	
		if (!MonitorUtils.inhibitPolling())
		{
			String period=aContext.getThisRequest().getArgumentValue("period");
			
			AtomicBoolean busyFlag=mBusyMap.get(period);
			AtomicInteger firstErrorFlag=mFirstErrorMap.get(period);
			int errorCountBeforeMsg=(Long.parseLong(period)<=5000)?3:1;
			if (busyFlag==null)
			{	busyFlag=new AtomicBoolean(false);
				mBusyMap.put(period, busyFlag);
				firstErrorFlag=new AtomicInteger(0);
				mFirstErrorMap.put(period, firstErrorFlag);
			}
			if (busyFlag.compareAndSet(false, true))
			{	try
				{
					if (period.equals("250"))
					{	//propagate any changes
						IHDSReader changes=aContext.source("active:polestarSensorChanges",IHDSDocument.class).getReader();
						List<String> changedSensors=new ArrayList(changes.getValues("/sensors/sensor"));
						MonitorUtils.executeTriggeredScripts(changedSensors, false, aContext);
					}
					else if (period.equals("30000")) //30sec
					{	//check for any non updated sensors
						aContext.source("active:polestarSensorReadingCheck");
					}
					else if (period.equals("1800000")) //30minutes 
					{	//save sensor state to db
						aContext.source("active:polestarSensorStatePersist");
					}
					else if (period.equals("86400000")) // 1day
					{
						aContext.source("active:polestarLogCleanup");
					}
					MonitorUtils.executePeriodicScripts(period, true, aContext);
				}
				finally
				{	busyFlag.set(false);
					if (firstErrorFlag!=null && firstErrorFlag.getAndSet(0)>=errorCountBeforeMsg)
					{	MonitorUtils.log(aContext,null,INKFLocale.LEVEL_INFO, "Period scripts for "+period+" restarted");
					}
				}
			}
			else
			{	int count=firstErrorFlag.incrementAndGet();
				if (count==errorCountBeforeMsg)
				{	MonitorUtils.log(aContext,null,INKFLocale.LEVEL_WARNING, "Periodic scripts for "+period+" stopped due to blockage");
				}
			}
		}
	}
}

