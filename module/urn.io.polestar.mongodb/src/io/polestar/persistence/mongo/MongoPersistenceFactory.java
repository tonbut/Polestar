package io.polestar.persistence.mongo;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

import io.polestar.data.db.IPolestarPersistence;

public class MongoPersistenceFactory extends StandardAccessorImpl
{
	public MongoPersistenceFactory()
	{
		this.declareThreadSafe();
	}
	
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		IPolestarPersistence p=new MongoPersistence();
		aContext.createResponseFrom(p);
	}
}
