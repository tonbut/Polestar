package io.polestar.data.scripts;

import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.INKFRequestReadOnly;
import org.netkernel.layer0.nkf.INKFResolutionContext;
import org.netkernel.layer0.nkf.INKFResponse;
import org.netkernel.layer0.nkf.INKFResponseReadOnly;
import org.netkernel.layer0.nkf.impl.NKFCombinedEndpointSpaceImpl;
import org.netkernel.request.IRequestScopeLevel;

public class ScriptStateBuffer extends NKFCombinedEndpointSpaceImpl
{
	private final IRequestScopeLevel mOriginalScope;
	private Object mSinkState;
	private String mSinkIdentifier;
	
	public ScriptStateBuffer(IRequestScopeLevel aOriginalScope)
	{	declareThreadSafe();
		mOriginalScope=aOriginalScope;
	}

	@Override
	public void onResolve(INKFResolutionContext aContext) throws Exception
	{	INKFRequestReadOnly req=aContext.getRequestToResolve();
		int verb=req.getVerb();
		if(	(verb==INKFRequestReadOnly.VERB_SINK || verb==INKFRequestReadOnly.VERB_SOURCE)
			&& req.getIdentifier().startsWith("res:/md/script/") 
			&& req.getIdentifier().endsWith("#state") )
		{	aContext.createResolutionResponse(this);			
		}
	}
	
	public void onRequest(INKFRequestContext aContext) throws Exception
	{
		INKFRequestReadOnly thisReq=aContext.getThisRequest();
		if (thisReq.getVerb()==INKFRequestReadOnly.VERB_SINK)
		{
			//System.out.println("sinking state "+thisReq.getIdentifier());
			mSinkState=aContext.sourcePrimary(Object.class);
			mSinkIdentifier=thisReq.getIdentifier();
		}
		else
		{
			//System.out.println("source state "+thisReq.getIdentifier());
			if (mSinkIdentifier==null)
			{	aContext.getKernelContext().setRequestScope(mOriginalScope);
				INKFResponseReadOnly respIn=aContext.sourceForResponse(thisReq.getIdentifier(),thisReq.getRepresentationClass());
				INKFResponse respOut=aContext.createResponseFrom(respIn);
				respOut.setNoCache();
			}
			else
			{	INKFResponse respOut=aContext.createResponseFrom(mSinkState);
				respOut.setNoCache();
			}
		}
	}
	
	void sinkIfModified(INKFRequestContext aContext) throws Exception
	{
		if (mSinkState!=null && mSinkIdentifier!=null)
		{	//System.out.println("real sinking state "+mSinkIdentifier);
			aContext.getKernelContext().setRequestScope(mOriginalScope);
			aContext.sink(mSinkIdentifier, mSinkState);
		}
	}

	public String toString()
	{	return "ScriptStateBuffer";
	}
}
