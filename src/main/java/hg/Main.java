package hg;

import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import java.net.*;
import java.util.Base64;

public class Main {
    public static void main(String[] args) throws UnknownHostException {
        new Main().start();
    }

    static final String PROXY_USER = "PROXY_USER";
    static final String PROXY_PASSWORD = "PROXY_PASSWORD";
    static final String PROXY_HOST = "127.0.0.1";
    static final Integer PROXY_PORT = 8080;
    static final String AUTH_HEADER = new String(Base64.getEncoder().encode((PROXY_USER + ":" + PROXY_PASSWORD).getBytes()));

    public void start() throws UnknownHostException {
        System.setProperty("java.net.useSystemProxies", "true");
        // http
        System.setProperty("http.proxyHost", PROXY_HOST);
        System.setProperty("http.proxyPort", String.valueOf(PROXY_PORT));
        System.setProperty("http.proxyUser", PROXY_USER);
        System.setProperty("http.proxyPassword", PROXY_PASSWORD);
        // https
        System.setProperty("https.proxyHost", PROXY_HOST);
        System.setProperty("https.proxyPort", String.valueOf(PROXY_PORT));
        System.setProperty("https.proxyUser", PROXY_USER);
        System.setProperty("https.proxyPassword", PROXY_PASSWORD);
        // additional setting
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        System.setProperty("jdk.https.auth.tunneling.disabledSchemes", "");

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(PROXY_USER, PROXY_PASSWORD.toCharArray());
            }
        });

        DefaultHttpProxyServer.bootstrap()
                .withAddress(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 8081))
                .withAllowLocalOnly(false)
                .withChainProxyManager((httpRequest, queue) -> queue.add(new UpStreamProxy()))
                .start();
    }

    public static class UpStreamProxy extends ChainedProxyAdapter {
        @Override
        public InetSocketAddress getChainedProxyAddress() {
            try {
                return new InetSocketAddress(InetAddress.getByName(PROXY_HOST), 8080);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void filterRequest(HttpObject httpObject) {
            if (httpObject instanceof HttpRequest) {
                HttpRequest httpRequest = (HttpRequest) httpObject;
                httpRequest.headers().add("Proxy-Authorization", "Basic" + AUTH_HEADER);
            }
            super.filterRequest(httpObject);
        }
    }
}
