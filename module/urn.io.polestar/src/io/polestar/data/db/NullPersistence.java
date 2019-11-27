package io.polestar.data.db;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.netkernel.layer0.nkf.NKFException;


public class NullPersistence implements InvocationHandler
{
	
	private static Constructor sProxyConstructor;
	static
	{	Class[] classes=new Class[]{IPolestarPersistence.class};
		Class proxyClass = Proxy.getProxyClass(NullPersistence.class.getClassLoader(), classes);
		try
		{	sProxyConstructor = proxyClass.getConstructor(new Class[] { InvocationHandler.class });
		}
		catch (Exception e)
		{	e.printStackTrace();
		}
	}
	
	public static IPolestarPersistence getInstance() throws IllegalAccessException, InstantiationException, IllegalArgumentException, InvocationTargetException
	{
		NullPersistence np=new NullPersistence();
		IPolestarPersistence proxy = (IPolestarPersistence)sProxyConstructor.newInstance(new Object[] {np} );
		return proxy;
	}
	
	
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
	{
		if (method.getName().equals("log"))
		{	return null;
		}
		else
		{	throw new NKFException("No persistence found");
		}
	}
}
