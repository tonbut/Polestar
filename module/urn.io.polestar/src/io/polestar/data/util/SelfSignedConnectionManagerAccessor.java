package io.polestar.data.util;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

public class SelfSignedConnectionManagerAccessor extends StandardAccessorImpl
{
    @Override
    public void onSource(INKFRequestContext aContext) throws Exception
    {   SSLContextBuilder builder = new SSLContextBuilder();
        //builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        builder.loadTrustMaterial(null, new TrustStrategy()
			{	@Override
				public boolean isTrusted(X509Certificate[] arg0, String arg1)
						throws CertificateException
				{
					return true;
				}
				
			});
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
                
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new PlainConnectionSocketFactory())
                .register("https", sslsf)
                .build();
        
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
        cm.setMaxTotal(2000);//max connection
        
        aContext.createResponseFrom(cm);
    }
}