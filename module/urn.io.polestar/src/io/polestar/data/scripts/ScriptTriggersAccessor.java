package io.polestar.data.scripts;

import java.util.Iterator;
import java.util.List;

import org.netkernel.layer0.nkf.*;
import org.netkernel.layer0.util.MultiMap;
import org.netkernel.layer0.util.Utils;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import io.polestar.data.util.MonitorUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class ScriptTriggersAccessor extends StandardAccessorImpl
{
	public ScriptTriggersAccessor()
	{	declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		IHDSReader scriptList=aContext.source("active:polestarListScripts",IHDSDocument.class).getReader();
		MultiMap mm=new MultiMap(40, 8);
		for (IHDSReader scriptNode : scriptList.getNodes("/scripts/script"))
		{	String scriptId=(String)scriptNode.getFirstValueOrNull("id");
			for (Object trigger : scriptNode.getValues("triggers/trigger"))
			{	mm.put(trigger, scriptId);
			}
		}
		IHDSMutator m=HDSFactory.newDocument();
		m.pushNode("sensors");
		for (Iterator<String> i=mm.keyIterator(); i.hasNext(); )
		{	String sensor=i.next();
			m.pushNode("sensor");
			m.addNode("id",sensor);
			m.pushNode("scripts");
			List<String> scripts=mm.get(sensor);
			for (String script : scripts)
			{	m.addNode("script",script);
			}
			m.popNode();
			m.popNode();
		}
		m.declareKey("byId", "/sensors/sensor", "id");
		aContext.createResponseFrom(m.toDocument(false));
	}
}

