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
package io.polestar.data.scripts;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.data.db.PersistenceFactory;
import io.polestar.data.util.MonitorUtils;

public class ReorderScriptsAccessor extends StandardAccessorImpl
{
	public ReorderScriptsAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String idString=aContext.source("arg:script",String.class);
		Long id=MonitorUtils.fromHexString(idString);
		Integer newPosition=aContext.source("arg:newPosition",Integer.class);
		
		PersistenceFactory.getPersistence(aContext).setScriptPosition(id,newPosition,aContext);
		MonitorUtils.cutGoldenThread(aContext, ListScriptsAccessor.GT_SCRIPT_LIST);
	}
}

