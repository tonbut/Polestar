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

import org.netkernel.layer0.nkf.*;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.util.MonitorUtils;

public class PeriodPollAccessor extends StandardAccessorImpl
{
	private Map<String,AtomicBoolean> mBusyMap = new HashMap<String, AtomicBoolean>();
	private Map<String,AtomicBoolean> mFirstErrorMap = new HashMap<String, AtomicBoolean>();
	
	public PeriodPollAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{	
		if (!MonitorUtils.inhibitPolling())
		{
			String period=aContext.getThisRequest().getArgumentValue("period");
			AtomicBoolean busyFlag=mBusyMap.get(period);
			AtomicBoolean firstErrorFlag=mFirstErrorMap.get(period);
			if (busyFlag==null)
			{	busyFlag=new AtomicBoolean(false);
				mBusyMap.put(period, busyFlag);
				firstErrorFlag=new AtomicBoolean(true);
				mFirstErrorMap.put(period, firstErrorFlag);
			}
			if (busyFlag.compareAndSet(false, true))
			{	try
				{	MonitorUtils.executePeriodicScripts(period, true, aContext);
				}
				finally
				{	busyFlag.set(false);
					if (firstErrorFlag.compareAndSet(false, true))
					{	aContext.logRaw(INKFLocale.LEVEL_INFO, "Period scripts for "+period+" restarted");
					}
				}
			}
			else
			{	if (firstErrorFlag.compareAndSet(true, false))
				{	aContext.logRaw(INKFLocale.LEVEL_WARNING, "Periodic scripts for "+period+" stopped due to blockage");
				}
			}
		}
			
		
		
		//long t=System.currentTimeMillis();
		//System.out.println(t);
		//System.out.println(t+" "+period);
		
		//IHDSReader triggers=aContext.source("active:polestarScriptTriggers",IHDSDocument.class).getReader();
		
		
		
		//System.out.println("TestPoll");
		//System.out.println("S TestPoll "+aContext.getThisRequest().getArgumentValue("v")+" "+new Date());
		//Thread.sleep(2000);
		//System.out.println("E TestPoll");
	}
}

