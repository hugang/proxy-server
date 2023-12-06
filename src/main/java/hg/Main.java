package hg;

import cn.hutool.setting.Setting;
import cn.hutool.setting.SettingUtil;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.Base64;

public class Main {

    public static void main(String[] args) throws Exception {
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            return;
        }
        final PopupMenu popup = new PopupMenu();
        final TrayIcon trayIcon = new TrayIcon(createImage("/icon.png", "tray icon"));
        final SystemTray tray = SystemTray.getSystemTray();

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        popup.add(exitItem);
        trayIcon.setPopupMenu(popup);

        // start proxy server
        new Main().start();

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("TrayIcon could not be added.");
        }
    }

    protected static Image createImage(String path, String description) {
        URL imageURL = Main.class.getResource(path);

        if (imageURL == null) {
            System.err.println("Resource not found: " + path);
            return null;
        } else {
            return (new ImageIcon(imageURL, description)).getImage();
        }
    }

    static final Setting settings = SettingUtil.get("proxy.setting");
    static final String PROXY_USER = settings.getStr("PROXY_USER");
    static final String PROXY_PASSWORD = settings.getStr("PROXY_PASSWORD");
    static final String PROXY_HOST = settings.getStr("PROXY_HOST");
    static final Integer PROXY_PORT = Integer.valueOf(settings.get("PROXY_PORT"));
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
            if (httpObject instanceof HttpRequest httpRequest) {
                httpRequest.headers().add("Proxy-Authorization", "Basic" + AUTH_HEADER);
            }
            super.filterRequest(httpObject);
        }
    }
}
