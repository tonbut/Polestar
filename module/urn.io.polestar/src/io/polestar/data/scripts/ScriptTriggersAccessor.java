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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.util.MultiMap;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

public class ScriptTriggersAccessor extends StandardAccessorImpl
{
	private Map<String,String> mLegacyPeriodLookup=new HashMap<>();
	
	public ScriptTriggersAccessor()
	{	
		declareThreadSafe();
		
		mLegacyPeriodLookup.put("1s", "1000");
		mLegacyPeriodLookup.put("5s", "5000");
		mLegacyPeriodLookup.put("30s", "30000");
		mLegacyPeriodLookup.put("5m", "300000");		
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		IHDSReader scriptList=aContext.source("active:polestarListScripts",IHDSDocument.class).getReader();
		MultiMap trigMap=new MultiMap(40, 8);
		MultiMap periodMap=new MultiMap(40, 8);
		for (IHDSReader scriptNode : scriptList.getNodes("/scripts/script"))
		{	String scriptId=(String)scriptNode.getFirstValueOrNull("id");
			boolean hasPeriod=false;
			String period=(String)scriptNode.getFirstValueOrNull("period");
			String target=(String)scriptNode.getFirstValueOrNull("target");
			if ((period!=null && period.length()>0) && (target==null || target.length()==0 || target.equals("None")))
			{	periodMap.put(period, scriptId);
				hasPeriod=true;
			}
			for (Object trigger : scriptNode.getValues("triggers/trigger"))
			{	trigMap.put(trigger, scriptId);
			
				//legacy triggers converted into period
				if (!hasPeriod)
				{	period=mLegacyPeriodLookup.get(trigger);
					if (period!=null)
					{	periodMap.put(period, scriptId);
						hasPeriod=true;
					}
				}
			}
			
		}
		
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("triggers");
		for (Iterator<String> i=trigMap.keyIterator(); i.hasNext(); )
		{	String sensor=i.next();
			m.pushNode("trigger");
			m.addNode("id",sensor);
			m.pushNode("scripts");
			List<String> scripts=trigMap.get(sensor);
			for (String script : scripts)
			{	m.addNode("script",script);
			}
			m.popNode();
			m.popNode();
		}
		m.popNode();
		m.pushNode("periods");
		for (Iterator<String> i=periodMap.keyIterator(); i.hasNext(); )
		{	String period=i.next();
			m.pushNode("period");
			m.addNode("id",period);
			m.pushNode("scripts");
			List<String> scripts=periodMap.get(period);
			for (String script : scripts)
			{	m.addNode("script",script);
			}
			m.popNode();
			m.popNode();
		}
		
		m.declareKey("scriptsForTrigger", "/triggers/trigger", "id");
		m.declareKey("scriptsForPeriod", "/periods/period", "id");
		aContext.createResponseFrom(m.toDocument(false));
	}
}

