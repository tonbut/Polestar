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
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.data.db.PersistenceFactory;
import io.polestar.data.util.MonitorUtils;

public class ScriptQueryAccessor extends StandardAccessorImpl
{
	public ScriptQueryAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{	if (aContext.getThisRequest().argumentExists("name"))
		{	String name=aContext.source("arg:name",String.class);
			Long scriptId=PersistenceFactory.getPersistence(aContext).findScriptWithName(name, aContext);
			if (scriptId==null)
			{	throw new NKFException("Script not found","with name="+name);
			}
			aContext.createResponseFrom(MonitorUtils.hexString(scriptId));
			MonitorUtils.attachGoldenThread(aContext, ListScriptsAccessor.GT_SCRIPT_LIST);
		}
	}
}

