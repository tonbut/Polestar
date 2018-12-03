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
package io.polestar.view.scripts;

import org.netkernel.layer0.nkf.INKFExpiryFunction;
import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFRequestReadOnly;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.layer0.representation.ByteArrayRepresentation;
import org.netkernel.layer0.representation.IBinaryStreamRepresentation;
import org.netkernel.layer0.representation.IReadableBinaryStreamRepresentation;
import org.netkernel.layer0.util.Utils;
import org.netkernel.mod.hds.HDSFactory;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSMutator;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.data.scripts.ListScriptsAccessor;
import io.polestar.data.util.MonitorUtils;
import io.polestar.view.charts.ChartViewAccessor;
import io.polestar.view.template.TemplateWrapper;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.*;

public class ScriptAccessor extends StandardAccessorImpl
{
	public ScriptAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		INKFRequestReadOnly req=aContext.getThisRequest();
		String action=null, id=null;
		if (req.argumentExists("action"))
		{	action=req.getArgumentValue("action");
			if (req.argumentExists("id"))
			{	id=req.getArgumentValue("id");
			}
		}
		if (action==null)
		{	onList(aContext);
		}
		else
		{	if (action.equals("new"))
			{	onNewScript(aContext);
			}
			else if (action.equals("edit"))
			{	onEdit(id,aContext);
			}
			else if (action.equals("reorder"))
			{	int i=id.indexOf('/');
				String scriptId=id.substring(0, i);
				int newPosition=Integer.parseInt(id.substring(i+1));
				onReorder(aContext,scriptId,newPosition);
			}
			else if (action.equals("execute"))
			{	onExecute(id,aContext,false);
			}
			else if (action.equals("webhook"))
			{	onExecute(id,aContext,true);
			}
			else if (action.equals("delete"))
			{	onDelete(id,aContext);
			}
			else if (action.equals("filtered"))
			{	onFilteredList(aContext);
			}
			else if (action.equals("backup"))
			{	onBackup(aContext);
			}
			else if (action.equals("restore"))
			{	onRestore(aContext);
			}
			else if (action.equals("resetStats"))
			{	onResetStats(aContext);
			}
				
		}
	}
	
	public void onResetStats(INKFRequestContext aContext) throws Exception
	{	MonitorUtils.assertAdmin(aContext);
		aContext.source("active:polestarScriptExecutionReset");
		aContext.createResponseFrom("done");
	}
	
	public void onRestore(INKFRequestContext aContext) throws Exception
	{	MonitorUtils.assertAdmin(aContext);
		IBinaryStreamRepresentation rep=(IBinaryStreamRepresentation)aContext.source("httpRequest:/upload/uploaded",IBinaryStreamRepresentation.class);
		File tmp = File.createTempFile("polestar", ".zip");
		FileOutputStream os = new FileOutputStream(tmp);
		rep.write(os);
		os.close();
		ZipFile zf = new ZipFile(tmp);
		for (Enumeration<? extends ZipEntry> e=zf.entries(); e.hasMoreElements(); )
		{
			ZipEntry entry=e.nextElement();
			String path=entry.getName();
			if (path.startsWith("script/"))
			{
				String idname=path.substring(7,path.length()-4);
				int i=idname.indexOf("+");
				String id=idname.substring(i+1);
				String dataIdentifier="res:/md/script/"+id;
				boolean exists=aContext.exists(dataIdentifier);
				if (!exists)
				{
					MonitorUtils.log(aContext,null,INKFLocale.LEVEL_INFO, "Restoring script "+id);
					InputStream is=zf.getInputStream(entry);
					ByteArrayOutputStream baos=new ByteArrayOutputStream(4096);
					Utils.pipe(is, baos);
					baos.flush();
					aContext.requestNew(dataIdentifier, null);
					aContext.sink(dataIdentifier, new ByteArrayRepresentation(baos));
				}
			}
		}
		zf.close();
		tmp.delete();
		MonitorUtils.cutGoldenThread(aContext, ListScriptsAccessor.GT_SCRIPT_LIST);
		INKFResponse resp=aContext.createResponseFrom("done");
		resp.setExpiry(INKFResponse.EXPIRY_ALWAYS);
		aContext.sink("httpResponse:/redirect","/polestar/scripts");

	}

	public void onBackup(INKFRequestContext aContext) throws Exception
	{	MonitorUtils.assertAdmin(aContext);
		IHDSReader scripts=aContext.source("active:polestarListScripts",IHDSDocument.class).getReader();
		
		ByteArrayOutputStream os = new ByteArrayOutputStream(4096);
		NoCloseZipOutputStream zos = new NoCloseZipOutputStream(os);
		
		for (IHDSReader script : scripts.getNodes("/scripts/script"))
		{
			String id=(String)script.getFirstValue("id");
			String name=(String)script.getFirstValue("name");
			name=safeName(name);
			String dataIdentifier="res:/md/script/"+id;
			IBinaryStreamRepresentation scriptData=aContext.source(dataIdentifier,IBinaryStreamRepresentation.class);
			zos.putNextEntry(new ZipEntry("script/"+name+"+"+id+".xml"));
			scriptData.write(zos);
			zos.closeEntry();
		}
		zos.reallyClose();
		ByteArrayRepresentation rep = new ByteArrayRepresentation(os);
		INKFResponse response=aContext.createResponseFrom(rep);
		response.setMimeType("application/zip");
		response.setHeader("httpResponse:/header/Content-Disposition", "attachment; filename=polestar-scripts.zip");
	}
	
	private String safeName(String aName)
	{	char[] ca=new char[aName.length()];
		for (int i=0; i<ca.length; i++)
		{	char c=aName.charAt(i);
			if (!Character.isLetterOrDigit(c)) c='_';
			ca[i]=c;
		}
		return new String(ca);
	}
	
	//store and retrieve sort order from session
	private static String processSort(String aSort, INKFRequestContext aContext) throws Exception
	{	if (aSort.length()==0)
		{	String sort=aContext.source("session:/scriptSort",String.class);
			if (sort!=null)
			{	aSort=sort;
			}
		}
		else
		{	aContext.sink("session:/scriptSort", aSort);
		}
		return aSort;
	}
	
	
	public void onList(INKFRequestContext aContext) throws Exception
	{
		MonitorUtils.isLoggedIn(aContext);
		String sort=processSort("",aContext);
		IHDSMutator list=getScriptList("", false, sort, aContext);
		IHDSDocument keywords=getKeywordSet(list.toDocument(false));
		
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/scripts/styleScripts.xsl");
		req.addArgumentByValue("operand", list.toDocument(false));
		req.addArgumentByValue("tags", keywords);
		req.addArgumentByValue("sort", sort);
		req.addArgument("polling", "active:polestarPollingState");
		String filter=aContext.source("httpRequest:/param/filter",String.class);
		if (filter!=null)
		{	req.addArgumentByValue("filter", filter);
		}
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
		resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
	}
	
	
	
	public void onFilteredList(INKFRequestContext aContext) throws Exception
	{
		String f=aContext.source("httpRequest:/param/f",String.class);
		String flow=f.toLowerCase();
		String tts=aContext.source("httpRequest:/param/tts",String.class).toLowerCase();
		String sort=aContext.source("httpRequest:/param/sort",String.class);

		sort=processSort(sort,aContext);		
		IHDSMutator list=getScriptList(flow, "true".equals(tts), sort, aContext);
		list.setCursor("/scripts").addNode("@filtered", "true");
		
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/scripts/styleScripts.xsl");
		req.addArgumentByValue("sort", sort);
		req.addArgumentByValue("operand", list.toDocument(true));
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
	}
	
	private IHDSDocument getKeywordSet(IHDSDocument aList)
	{	Set<String> keywordSet=new HashSet<>();
		for (IHDSReader scriptNode : aList.getReader().getNodes("/scripts/script"))
		{	
			for (Object keyword : scriptNode.getValues("keywords/keyword"))
			{	keywordSet.add((String)keyword);
			}
		}
		List<String> keywordList=new ArrayList<>(keywordSet);
		Collections.sort(keywordList);
		IHDSMutator keywords=HDSFactory.newDocument();
		keywords.pushNode("tags").pushNode("keywords");
		for (String keyword: keywordList)
		{	keywords.addNode("keyword", keyword);
		}
		return keywords.toDocument(false);
	}
	
	
	private static abstract class ScriptComparator implements Comparator<String>
	{
		private IHDSReader mList;
		
		public void setList(IHDSReader aList)
		{	mList=aList;
		}
		
		public int compare(String o1, String o2)
		{	IHDSReader script1=mList.getFirstNodeOrNull("key('byId','"+o1+"')");
			IHDSReader script2=mList.getFirstNodeOrNull("key('byId','"+o2+"')");
			return scriptCompare(script1,script2);
		}
		
		public abstract int scriptCompare(IHDSReader s1, IHDSReader s2);
	}
	
	
	private IHDSMutator getScriptList(String aFilter, boolean aFullTextSearch, String aSort, INKFRequestContext aContext) throws Exception
	{
		boolean isAdmin=MonitorUtils.isAdmin(aContext);		
		
		
		
		long now=System.currentTimeMillis();
		IHDSReader executionData=aContext.source("active:polestarScriptExecutionStatus",IHDSDocument.class).getReader();
		IHDSDocument dList=aContext.source("active:polestarListScripts",IHDSDocument.class);
		IHDSReader rList=dList.getReader();
		IHDSMutator list=HDSFactory.newDocument(); //dList.getMutableClone();
		list.pushNode("scripts");
		
		List<String> ids=new ArrayList<>();
		for (IHDSReader scriptNode : rList.getNodes("/scripts/script"))
		{	String id=(String)scriptNode.getFirstValue("id");
			ids.add(id);
		}
		
		if (aSort.equals("alpha"))
		{	ScriptComparator c=new ScriptComparator()
			{	public int scriptCompare(IHDSReader s1, IHDSReader s2)
				{	String n1=((String)s1.getFirstValue("name")).toLowerCase();
					String n2=((String)s2.getFirstValue("name")).toLowerCase();
					return n1.compareTo(n2);
				}
			};
			c.setList(rList);
			ids.sort(c);
		}
		else if (aSort.equals("lastExec"))
		{	ScriptComparator c=new ScriptComparator()
			{	public int scriptCompare(IHDSReader s1, IHDSReader s2)
				{	Long n1=s1!=null?(Long)s1.getFirstValueOrNull("lastExecTime"):null;
					Long n2=s2!=null?(Long)s2.getFirstValueOrNull("lastExecTime"):null;
					if (n1==null) n1=Long.valueOf(0);
					if (n2==null) n2=Long.valueOf(0);
					return n2.compareTo(n1);
				}
			};
			c.setList(executionData);
			ids.sort(c);
		}
		else if (aSort.equals("lastErr"))
		{	ScriptComparator c=new ScriptComparator()
			{	public int scriptCompare(IHDSReader s1, IHDSReader s2)
				{	Long n1=s1!=null?(Long)s1.getFirstValueOrNull("lastErrorTime"):null;
					Long n2=s2!=null?(Long)s2.getFirstValueOrNull("lastErrorTime"):null;
					if (n1==null) n1=Long.valueOf(0);
					if (n2==null) n2=Long.valueOf(0);
					return n2.compareTo(n1);
				}
			};
			c.setList(executionData);
			ids.sort(c);
		}
		else if (aSort.equals("countExec"))
		{	ScriptComparator c=new ScriptComparator()
			{	public int scriptCompare(IHDSReader s1, IHDSReader s2)
				{	Integer n1=s1!=null?(Integer)s1.getFirstValueOrNull("count"):null;
					Integer n2=s2!=null?(Integer)s2.getFirstValueOrNull("count"):null;
					if (n1==null) n1=Integer.valueOf(0);
					if (n2==null) n2=Integer.valueOf(0);
					return n2.compareTo(n1);
				}
			};
			c.setList(executionData);
			ids.sort(c);
		}
		else if (aSort.equals("countErr"))
		{	ScriptComparator c=new ScriptComparator()
			{	public int scriptCompare(IHDSReader s1, IHDSReader s2)
				{	Integer n1=s1!=null?(Integer)s1.getFirstValueOrNull("errors"):null;
					Integer n2=s2!=null?(Integer)s2.getFirstValueOrNull("errors"):null;
					if (n1==null) n1=Integer.valueOf(0);
					if (n2==null) n2=Integer.valueOf(0);
					return n2.compareTo(n1);
				}
			};
			c.setList(executionData);
			ids.sort(c);
		}
		else if (aSort.equals("lastEdit"))
		{	ScriptComparator c=new ScriptComparator()
			{	public int scriptCompare(IHDSReader s1, IHDSReader s2)
				{	Long n1=s1!=null?(Long)s1.getFirstValueOrNull("lastEdited"):null;
					Long n2=s2!=null?(Long)s2.getFirstValueOrNull("lastEdited"):null;
					if (n1==null) n1=Long.valueOf(0);
					if (n2==null) n2=Long.valueOf(0);
					return n2.compareTo(n1);
				}
			};
			c.setList(executionData);
			ids.sort(c);
		}
		
		
		
		/*<li><a onclick="onSort('default')">Default</a></li>
	    <li><a onclick="onSort('alpha')">Alphabetical</a></li>
	    <li><a onclick="onSort('lastExec')">Last executed</a></li>
	    <li><a onclick="onSort('lastErr')">Last error</a></li>
	    <li><a onclick="onSort('countExec')">Execution count</a></li>
	    <li><a onclick="onSort('countErr')">Error count</a></li>
	    */
		
		
		
		//for (IHDSMutator scriptNode : list.getNodes("/scripts/script"))
		for (String id : ids)
		{	
				
			boolean found=aFilter.length()==0;
			//String id=(String)scriptNode.getFirstValue("id");
			IHDSReader rScriptNode=rList.getFirstNode("key('byId','"+id+"')");
			IHDSMutator scriptNode=rScriptNode.toDocument().getMutableClone().getFirstNode("/*");
			
			String name=(String)scriptNode.getFirstValue("name");
			if (name.toLowerCase().contains(aFilter)) found=true;
			String keywords=(String)scriptNode.getFirstValue("keywords");
			
			
			
			if (keywords!=null && keywords.toLowerCase().contains(aFilter)) found=true;
			String triggers=(String)scriptNode.getFirstValue("triggers");
			if (triggers!=null && triggers.toLowerCase().contains(aFilter)) found=true;
			
			if (aFullTextSearch)
			{				
				IHDSReader script=aContext.source("res:/md/script/"+id,IHDSDocument.class).getReader();
				String scriptSrc=(String)script.getFirstValue("/script/script");
				if (scriptSrc.contains(aFilter)) found=true;
			}
			
			if (keywords!=null && keywords.contains(ChartViewAccessor.KEYWORD_CHART))
			{	found=false;
			}
			
			boolean delete=false;
			Object ip=scriptNode.getFirstValueOrNull("public");
			if (!isAdmin && "secret".equals(ip))
			{	delete=true;
			}
			
			if (!found)
			{	delete=true;
			}
			//if (delete)
			//{	scriptNode.delete();
			//}
			//else
			if (!delete)
			{	//append execution stats
				IHDSReader ed=executionData.getFirstNodeOrNull("key('byId','"+id+"')");
				if (ed!=null)
				{	scriptNode.appendChildren(ed);
				}
				
				Long lastExecuted=ed==null?null:(Long)ed.getFirstValueOrNull("lastExecTime");
				String lastExecHuman=lastExecuted==null?"Never":(MonitorUtils.formatPeriod(now-lastExecuted)+" ago");
				scriptNode.addNode("lastExecHuman", lastExecHuman);
				
				Long lastError=ed==null?null:(Long)ed.getFirstValueOrNull("lastErrorTime");
				String lastErrorHuman=lastError==null?"Never":(MonitorUtils.formatPeriod(now-lastError)+" ago");
				scriptNode.addNode("lastErrorHuman", lastErrorHuman);
			}
			if (!delete)
			{	list.append(scriptNode).popNode();
			}
		}
		return list;
		
	}
	
	
	
	
	public void onNewScript(INKFRequestContext aContext) throws Exception
	{	MonitorUtils.assertAdmin(aContext);
		String newId=aContext.requestNew("res:/md/script/", null);
		newId=newId.substring(newId.lastIndexOf('/')+1);
		aContext.sink("httpResponse:/redirect","/polestar/scripts/edit/"+newId);
	}
	
	public void onDelete(String aId,INKFRequestContext aContext) throws Exception
	{	MonitorUtils.assertAdmin(aContext);
		aContext.delete("res:/md/script/"+aId);
		aContext.sink("httpResponse:/redirect","/polestar/scripts");
	}
	public void onExecute(String aId,INKFRequestContext aContext, boolean aIsWebhook) throws Exception
	{
		//check that we are logged in or that script is public
		IHDSReader scriptData=aContext.source("res:/md/script/"+aId,IHDSDocument.class).getReader();
		Object ip=scriptData.getFirstValueOrNull("/script/public");
		if (ip instanceof String)
		{	String isPublic=(String)ip;
			if (isPublic.equals("public"))
			{
			}
			else if (isPublic.equals("private"))
			{	MonitorUtils.assertAdmin(aContext);
			}
			else if (isPublic.equals("secret"))
			{	if (!aIsWebhook)
				{	MonitorUtils.assertAdmin(aContext);
				}
			}
			else if (isPublic.equals("guest"))
			{	if (!MonitorUtils.isLoggedIn(aContext))
				{	throw new NKFException("Not Authorized");
				}
			}
		}
		else if (ip instanceof Boolean)
		{	if (!MonitorUtils.isLoggedIn(aContext))
			{	throw new NKFException("Not Authorized");
			}
			else
			{	MonitorUtils.assertAdmin(aContext);
			}
		}
		
		INKFRequest req=aContext.createRequest("res:/md/execute/"+aId);
		INKFResponseReadOnly resp=aContext.issueRequestForResponse(req);
		aContext.createResponseFrom(resp);
	}

	public void onEdit(String aId,INKFRequestContext aContext) throws Exception
	{
		IHDSReader params=aContext.source("httpRequest:/params",IHDSDocument.class).getReader();
		if (params.getFirstNodeOrNull("cancel")!=null)
		{	aContext.sink("httpResponse:/redirect","/polestar/scripts");
			return;
		}
		else if (params.getFirstNodeOrNull("save")!=null || params.getFirstNodeOrNull("execute")!=null)
		{	MonitorUtils.assertAdmin(aContext);
			IHDSMutator m=HDSFactory.newDocument();
			m.pushNode("script");
			m.addNode("name",params.getFirstValue("name"));
			m.addNode("triggers",params.getFirstValue("triggers"));
			m.addNode("period",params.getFirstValue("period"));
			m.addNode("target",params.getFirstValue("target"));
			m.addNode("keywords",params.getFirstValue("keywords"));
			m.addNode("script",params.getFirstValue("script"));
			m.addNode("public",params.getFirstValue("public"));
			aContext.sink("res:/md/script/"+aId, m.toDocument(false));
			updateScriptExecutionData(aId,aContext);
			
			if (params.getFirstNodeOrNull("execute")!=null)
			{	aContext.sink("httpResponse:/redirect","/polestar/scripts/execute/"+aId);
			}
			else
			{	aContext.sink("httpResponse:/redirect","/polestar/scripts");
			}
			return;
		}
		else
		{
			IHDSReader script=aContext.source("res:/md/script/"+aId,IHDSDocument.class).getReader();
			String access="";
			try
			{	access=(String)script.getFirstValue("/script/public");
			} catch (Exception e) {}
			if (access!=null && access.equals("secret"))
			{	MonitorUtils.assertAdmin(aContext);
			}
		}
		
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/scripts/styleEdit.xsl");
		req.addArgument("operand", "res:/md/script/"+aId);
		req.addArgument("sensors","active:polestarSensorConfig");
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
		resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
	
	}
	public void onReorder(INKFRequestContext aContext, String aScriptId, int aNewPosition) throws Exception
	{	MonitorUtils.assertAdmin(aContext);
		INKFRequest req = aContext.createRequest("active:polestarReorderScripts");
		req.addArgumentByValue("script", aScriptId);
		req.addArgumentByValue("newPosition", aNewPosition);
		aContext.issueRequest(req);
		aContext.createResponseFrom("").setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
	
	private void updateScriptExecutionData(String aId, INKFRequestContext aContext) throws Exception
	{	
		INKFRequest req=aContext.createRequest("active:polestarScriptExecutionUpdate");
		req.setHeader(INKFRequest.HEADER_EXCLUDE_DEPENDENCIES, true); //don't stop caching
		req.addArgumentByValue("id", aId);
		req.addArgument("edit", "");
		aContext.issueRequest(req);
	}
	
	private class NoCloseZipOutputStream extends ZipOutputStream
	{
		public NoCloseZipOutputStream(OutputStream aStream)
		{	super(aStream);
		}
		
		public void close()
		{
		}
		
		public void reallyClose() throws IOException
		{	super.close();
		}
	}
}
