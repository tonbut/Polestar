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

import java.util.Random;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.data.db.IPolestarPersistence;
import io.polestar.data.db.PersistenceFactory;
import io.polestar.data.util.MonitorUtils;

public class ScriptDataAccessor extends StandardAccessorImpl
{
	public ScriptDataAccessor()
	{	declareThreadSafe();
	}

	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String idString=aContext.getThisRequest().getArgumentValue("id");
		Long id=MonitorUtils.fromHexString(idString);
		String fragment=null;
		if (aContext.getThisRequest().argumentExists("fragment"))
		{	fragment=aContext.getThisRequest().getArgumentValue("fragment");
		}

		IHDSDocument script=PersistenceFactory.getPersistence(aContext).getScript(id, aContext);
		if (fragment==null)
		{
			INKFResponse resp=aContext.createResponseFrom(script);
			MonitorUtils.attachGoldenThread(aContext, "gt:script:"+id, "gt:script:"+id+":state");
		}
		else
		{
			Object rep=null;
			IHDSReader r=script.getReader();
			if (fragment.equals("script"))
			{
				String code=(String)r.getFirstValue("/script/script");
				String name=(String)r.getFirstValue("/script/name");
				rep=code;
				MonitorUtils.attachGoldenThread(aContext, "gt:script:"+id);
			}
			else if (fragment.equals("state"))
			{
				rep=r.getFirstValue("/script/state");
				MonitorUtils.attachGoldenThread(aContext, "gt:script:"+id+":state");
			}
			else if (fragment.equals("name"))
			{
				rep=r.getFirstValue("/script/name");
				MonitorUtils.attachGoldenThread(aContext, "gt:script:"+id);
			}
			else if (fragment.equals("language"))
			{
				rep=r.getFirstValueOrNull("/script/language");
				if (rep == null || rep.equals(""))
				{
					// default for backwards compatibility
					rep = "polestar:script:groovy";
				}
				MonitorUtils.attachGoldenThread(aContext, "gt:script:"+id);
			}
			INKFResponse resp=aContext.createResponseFrom(rep);
		}
	}

	public void onSink(INKFRequestContext aContext) throws Exception
	{
		String idString=aContext.getThisRequest().getArgumentValue("id");
		Long id=MonitorUtils.fromHexString(idString);
		String fragment=null;
		if (aContext.getThisRequest().argumentExists("fragment"))
		{	fragment=aContext.getThisRequest().getArgumentValue("fragment");
		}

		IHDSDocument saveState;
		if (fragment==null)
		{	saveState=aContext.sourcePrimary(IHDSDocument.class);
		}
		else
		{	if (fragment.equals("state"))
			{
				Object state=aContext.sourcePrimary(String.class);
				IHDSMutator m=HDSFactory.newDocument();
				m.pushNode("script").addNode("state", state);
				saveState=m.toDocument(false);
			}
			else
			{	throw new NKFException("unknown fragment "+fragment);
			}
		}
		PersistenceFactory.getPersistence(aContext).setScript(id, saveState, aContext);

		boolean cutList=false;
		boolean cutState=false;
		for (IHDSReader childNode : saveState.getReader().getNodes("/script/*"))
		{
			String name=(String)childNode.getFirstValue("name()");
			if (!cutList && (name.equals("name") || name.equals("triggers") || name.equals("period") || name.equals("target") || name.equals("keywords")))
			{	cutList=true;
			}
			if (!cutState && name.equals("state"))
			{	cutState=true;
			}
		}

		MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id);
		if (cutList) MonitorUtils.cutGoldenThread(aContext, ListScriptsAccessor.GT_SCRIPT_LIST);
		if (cutState) MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id+":state");
	}

	public void onExists(INKFRequestContext aContext) throws Exception
	{	String idString=aContext.getThisRequest().getArgumentValue("id");
		Long id=MonitorUtils.fromHexString(idString);
		boolean result;
		try
		{
			IHDSDocument script=PersistenceFactory.getPersistence(aContext).getScript(id, aContext);
			result=true;
		}
		catch (NKFException e)
		{	result=false;
		}
		aContext.createResponseFrom(result).setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}

	public void onNew(INKFRequestContext aContext) throws Exception
	{	long id;
		String idString;
		if (aContext.getThisRequest().argumentExists("id"))
		{	idString=aContext.getThisRequest().getArgumentValue("id");
			id=MonitorUtils.fromHexString(idString);
		}
		else
		{	Random r=new Random();
			id=r.nextLong();
			idString=MonitorUtils.hexString(id);
		}

		IPolestarPersistence persistence=PersistenceFactory.getPersistence(aContext);

		int size=((Number)persistence.getScriptList(aContext).getReader().getFirstValue("count(/scripts/script)")).intValue();

		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("script")
			.addNode("name", "Script "+idString)
			.addNode("id", id)
			.addNode("triggers", "")
			.addNode("keywords", "")
			.addNode("script", "")
			.addNode("state", "<state/>")
			.addNode("public", "private")
			.addNode("language", "polestar:script:groovy")
			.addNode("order", size);

		persistence.addScript(m.toDocument(false),aContext);

		aContext.createResponseFrom("res:/md/script/"+idString);
		MonitorUtils.cutGoldenThread(aContext, ListScriptsAccessor.GT_SCRIPT_LIST);
	}

	public void onDelete(INKFRequestContext aContext) throws Exception
	{
		String idString=aContext.getThisRequest().getArgumentValue("id");
		Long id=MonitorUtils.fromHexString(idString);
		boolean deleted=PersistenceFactory.getPersistence(aContext).deleteScript(id, aContext);
		aContext.createResponseFrom(deleted);
		MonitorUtils.cutGoldenThread(aContext, "gt:script:"+id, ListScriptsAccessor.GT_SCRIPT_LIST);
	}
	
	
}
