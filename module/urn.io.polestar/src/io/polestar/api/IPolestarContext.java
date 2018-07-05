package io.polestar.api;

import org.netkernel.layer0.nkf.INKFRequestContext;

/** Both IPolestarAPI and INKFRequestContext - this is what each script receives as it's context object */
public interface IPolestarContext extends INKFRequestContext, IPolestarAPI
{
	
}
