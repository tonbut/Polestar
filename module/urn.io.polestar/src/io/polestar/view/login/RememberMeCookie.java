package io.polestar.view.login;

import javax.servlet.http.Cookie;

import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.nkf.NKFException;
import org.netkernel.layer0.util.Utils;

public class RememberMeCookie extends Cookie
{
	public static final String NAME="NETKERNEL_REMEMBERME";
	
	private String mUsername;
	private final String mExpiry;
	private final String mHash;
	
	/** Construct a new remember me cookie after the user is authenticated, this cookie should be sent back to browser
	 * @param aPath
	 * @param aExpiry
	 * @param aUsername
	 * @param aPassword
	 * @param aHostname
	 * @param aContext
	 * @throws Exception
	 */
	public RememberMeCookie(String aPath, long aExpiry, String aUsername, String aPassword, String aHostname, INKFRequestContext aContext) throws Exception
	{	super(NAME,generateCookieValue(aUsername,aExpiry,generateHash(aPassword,aHostname,aExpiry,aContext)));
		mUsername=aUsername;
		mExpiry=Long.toString(aExpiry);
		mHash=null;
		int maxAge=(int)(aExpiry-System.currentTimeMillis())/1000;
		super.setMaxAge(maxAge);
		super.setPath(aPath);
	}
	
	/** Construct a remember me cookie to wrap an incoming cookie supplied by browser
	 * @param aCookie
	 * @param aContext
	 * @throws Exception
	 */
	public RememberMeCookie(Cookie aCookie, INKFRequestContext aContext) throws Exception
	{	super(aCookie.getName(),aCookie.getValue());
		if (!aCookie.getName().equals(NAME)) throw new NKFException("Expected "+NAME+" cookie, got "+aCookie.getName());
		String[] values=Utils.splitString(super.getValue(),"|");
		if (values.length!=3) throw new NKFException("malformed cookie");
		mUsername=values[0];
		mExpiry=values[1];
		mHash=values[2];
		super.setMaxAge(aCookie.getMaxAge());
		super.setPath(aCookie.getPath());
	}
	
	/** Construct and expired cookie that will clear the existing cookie. This should be sent back to browser 
	 * @param aPath
	 */
	public RememberMeCookie(String aPath)
	{	super(NAME,"");
		super.setPath(aPath);
		super.setMaxAge(0);
		mUsername=null;
		mExpiry=null;
		mHash=null;
	}
	
	/** Test if the cookie is valid by comparing the hash against one generated from the supplied  password and hostname
	 * @param aPassword
	 * @param aHostname
	 * @param aContext
	 * @return
	 * @throws Exception
	 */
	public boolean isValid(String aPassword, String aHostname, INKFRequestContext aContext) throws Exception
	{
		INKFRequest req=aContext.createRequest("active:checkPasswordHash");
		req.addArgumentByValue("password",generateHashSource(aPassword,aHostname,mExpiry));
		req.addArgumentByValue("hash",mHash);
		req.setRepresentationClass(Boolean.class);
		boolean valid=(Boolean)aContext.issueRequest(req);
		if (valid)
		{	valid=System.currentTimeMillis()<Long.parseLong(mExpiry);
		}
		return valid;
	}
	
	/** Return the username stored in the cookie
	 * @return
	 */
	public String getUsername()
	{	return mUsername;
	}
	
	/** Generate the input to the hash function by concatenating password, remote host and expiry time
	 * @param aPassword
	 * @param aRemoteHost
	 * @param aExpiry
	 * @return
	 */
	private static String generateHashSource(String aPassword, String aRemoteHost, String aExpiry)
	{	return aPassword+aRemoteHost+aExpiry;
	}
	
	/** Generate a salted hash of the password, remote host and expiry time concatenated
	 * @param aPassword
	 * @param aRemoteHost
	 * @param aExpiry
	 * @param aContext
	 * @return
	 * @throws NKFException
	 */
	private static String generateHash(String aPassword, String aRemoteHost, long aExpiry, INKFRequestContext aContext) throws NKFException
	{	INKFRequest req=aContext.createRequest("active:generatePasswordHash");
		req.addArgumentByValue("password",generateHashSource(aPassword,aRemoteHost,Long.toString(aExpiry)));
		req.setRepresentationClass(String.class);
		return (String)aContext.issueRequest(req);
	}
	
	/** Construct cookie value containing username, expiry time and hash concatenated with |
	 * @param aUsername
	 * @param aExpiry
	 * @param aHash
	 * @return
	 * @throws Exception
	 */
	private static String generateCookieValue(String aUsername, long aExpiry, String aHash)
	{	StringBuilder sb=new StringBuilder(aHash.length()+aUsername.length()+32);
		sb.append(aUsername);
		sb.append("|");
		sb.append(Long.toString(aExpiry));
		sb.append("|");
		sb.append(aHash);
		return sb.toString();
	}
	
	
}
