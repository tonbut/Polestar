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
import io.polestar.data.util.MonitorUtils;
import io.polestar.view.template.TemplateWrapper;

import java.io.*;
import java.util.Date;
import java.util.Enumeration;
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
			{	onExecute(id,aContext);
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
				
		}
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
					aContext.logRaw(INKFLocale.LEVEL_INFO, "Restoring script "+id);
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
			//System.out.println(scriptData);
			zos.putNextEntry(new ZipEntry("script/"+name+"+"+id+".xml"));
			scriptData.write(zos);
			zos.closeEntry();
		}
		zos.reallyClose();
		ByteArrayRepresentation rep = new ByteArrayRepresentation(os);
		INKFResponse response=aContext.createResponseFrom(rep);
		response.setMimeType("application/zip");
		response.setHeader("httpResponse:/header/Content-Disposition", "attachment; filename=polestar.zip");
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
	
	public void onList(INKFRequestContext aContext) throws Exception
	{
		MonitorUtils.isLoggedIn(aContext);
		
		IHDSDocument list=aContext.source("active:polestarListScripts",IHDSDocument.class);
		
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/scripts/styleScripts.xsl");
		req.addArgumentByValue("operand", list);
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
		resp.setHeader(TemplateWrapper.HEADER_WRAP, true);
	}
	
	public void onFilteredList(INKFRequestContext aContext) throws Exception
	{
		String f=aContext.source("httpRequest:/param/f",String.class).toLowerCase();
		IHDSMutator list=aContext.source("active:polestarListScripts",IHDSDocument.class).getMutableClone();
		if (f.length()>0)
		{	for (IHDSMutator scriptNode : list.getNodes("/scripts/script"))
			{	boolean found=false;
				String name=(String)scriptNode.getFirstValue("name");
				if (name.toLowerCase().contains(f)) found=true;
				String keywords=(String)scriptNode.getFirstValue("keywords");
				if (keywords!=null && keywords.toLowerCase().contains(f)) found=true;
				
				if (!found)
				{	scriptNode.delete();
				}
			}
		}
		list.setCursor("/scripts").addNode("@filtered", "true");
		
		INKFRequest req = aContext.createRequest("active:xslt");
		req.addArgument("operator", "res:/io/polestar/view/scripts/styleScripts.xsl");
		req.addArgumentByValue("operand", list.toDocument(true));
		INKFResponseReadOnly subresp = aContext.issueRequestForResponse(req);		
		INKFResponse resp=aContext.createResponseFrom(subresp);
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
	public void onExecute(String aId,INKFRequestContext aContext) throws Exception
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
		//System.out.println(params);
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
			m.addNode("keywords",params.getFirstValue("keywords"));
			m.addNode("script",params.getFirstValue("script"));
			m.addNode("public",params.getFirstValue("public"));
			aContext.sink("res:/md/script/"+aId, m.toDocument(false));
			
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
