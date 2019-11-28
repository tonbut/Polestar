package io.polestar.data.db;


import java.util.List;

import org.netkernel.layer0.nkf.INKFLocale;
import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.layer0.urii.SimpleIdentifierImpl;
import org.netkernel.mod.hds.IHDSDocument;
import org.netkernel.mod.hds.IHDSReader;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;
import org.netkernel.urii.ISpace;
import org.netkernel.urii.impl.Version;
import org.netkernel.util.Utils;

public class PersistenceFactory extends StandardAccessorImpl
{
	private static IPolestarPersistence sPersistence;	
	
	public static IPolestarPersistence getPersistence(INKFRequestContext aContext) throws NKFException
	{	
		if (sPersistence==null)
		{
			synchronized(PersistenceFactory.class)
			{
				setup(aContext);
			}
			
			
		}
		return sPersistence;
	}
	
	/** post commission discovers persistence implementation in deployed modules
	 **/
	//@Override
	public static void setup(INKFRequestContext aContext)
	{
		try
		{
			INKFRequest req=aContext.createRequest("active:spaceAggregateHDS");
			req.addArgument("uri", "res:/etc/PolestarPersistence.xml");
			req.setRepresentationClass(IHDSDocument.class);
			IHDSReader hds=((IHDSDocument)aContext.issueRequest(req)).getReader();
			List<IHDSReader> configs=hds.getNodes("/spaces/space[config]");
			if (configs.size()==0)
			{	aContext.logRaw(INKFLocale.LEVEL_WARNING, "No Persistence Implementation found for polestar");
				sPersistence=NullPersistence.getInstance();
			}
			else if (configs.size()>1)
			{	aContext.logRaw(INKFLocale.LEVEL_WARNING, "Multiple Persistence Implementations found for polestar");
				sPersistence=NullPersistence.getInstance();
			}
			else
			{
				IHDSReader config=configs.get(0);
				String requestURI=(String)config.getFirstValue("config");
				SimpleIdentifierImpl spaceURI=new SimpleIdentifierImpl((String)config.getFirstValue("id"));
				Version version=new Version((String)config.getFirstValue("version"));
				ISpace space=aContext.getKernelContext().getKernel().getSpace(spaceURI,version,version);
				INKFRequest req2=aContext.createRequest(requestURI);
				req2.injectRequestScope(space);
				req2.setRepresentationClass(IPolestarPersistence.class);
				sPersistence=(IPolestarPersistence)aContext.issueRequest(req2);
				sPersistence.prime(aContext);
			}
		}
		catch (Exception e)
		{
			aContext.logRaw(INKFLocale.LEVEL_WARNING, "Unhandled exception discovering persistence\n"+Utils.throwableToString(e));
			try
			{	sPersistence=NullPersistence.getInstance();
			} catch (Exception e2) {;}
		}

	}
}
