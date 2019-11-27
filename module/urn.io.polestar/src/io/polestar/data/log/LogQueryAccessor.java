package io.polestar.data.log;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.data.db.PersistenceFactory;

public class LogQueryAccessor extends StandardAccessorImpl
{
	public static final String LOG_COLLECTION="log";
	public LogQueryAccessor()
	{	this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		IHDSReader operator=aContext.source("arg:operator",IHDSDocument.class).getReader();
		Integer start=(Integer)operator.getFirstValue("start");
		Integer length=(Integer)operator.getFirstValue("length");
		String search=(String)operator.getFirstValue("search");
		
		IHDSDocument searchResults=PersistenceFactory.getPersistence(aContext).logQuery(start,length,search,aContext);
		aContext.createResponseFrom(searchResults).setExpiry(INKFResponse.EXPIRY_ALWAYS);
	}
}
