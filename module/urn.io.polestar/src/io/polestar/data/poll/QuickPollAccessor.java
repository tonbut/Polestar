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

public class QuickPollAccessor extends StandardAccessorImpl
{
	private AtomicBoolean mBusy=new AtomicBoolean(false);
	private AtomicBoolean mBusyFirstError=new AtomicBoolean(true);
	
	public QuickPollAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{	if (!MonitorUtils.inhibitPolling())
		{	if (mBusy.compareAndSet(false, true))
			{	try
				{	IHDSReader changes=aContext.source("active:polestarSensorChanges",IHDSDocument.class).getReader();
					List<String> changedSensors=new ArrayList(changes.getValues("/sensors/sensor"));
					changedSensors.add("1s");
					MonitorUtils.executeTriggeredScripts(changedSensors, false, aContext);
				}
				finally
				{	mBusy.set(false);
					if (mBusyFirstError.compareAndSet(false, true))
					{	aContext.logRaw(INKFLocale.LEVEL_INFO, "Scripts on 1s trigger restarted");
					}
				}
			}
		else
		{	if (mBusyFirstError.compareAndSet(true, false))
			{	aContext.logRaw(INKFLocale.LEVEL_WARNING, "Scripts on 1s trigger stopped due to blockage");
			}
		}
		}
	}
}

