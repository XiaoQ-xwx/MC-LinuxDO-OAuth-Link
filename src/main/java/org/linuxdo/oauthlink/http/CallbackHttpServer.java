package org.linuxdo.oauthlink.http;

import com.sun.net.httpserver.HttpServer;
import org.linuxdo.oauthlink.config.OAuthConfig;
import org.linuxdo.oauthlink.oauth.OAuthError;
import org.linuxdo.oauthlink.oauth.OAuthException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lifecycle wrapper for the embedded JDK HttpServer that receives OAuth callbacks.
 */
public class CallbackHttpServer {

    private final OAuthConfig config;
    private final OAuthCallbackHandler handler;
    private final Logger logger;
    private HttpServer server;
    private ExecutorService httpExecutor;

    public CallbackHttpServer(OAuthConfig config, OAuthCallbackHandler handler, Logger logger) {
        this.config = config;
        this.handler = handler;
        this.logger = logger;
    }

    /**
     * Starts the embedded HTTP server on the configured host:port.
     *
     * @throws OAuthException if the port is already in use
     */
    public void start() throws IOException {
        try {
            httpExecutor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "OAuth-Callback-Worker");
                t.setDaemon(true);
                return t;
            });
            server = HttpServer.create(
                    new InetSocketAddress(config.getCallbackHost(), config.getCallbackPort()), 0);
            server.createContext(config.getCallbackPath(), handler);
            server.setExecutor(httpExecutor);
            server.start();
            logger.info("OAuth 回调服务器已启动: http://"
                    + config.getCallbackHost() + ":" + config.getCallbackPort()
                    + config.getCallbackPath());
        } catch (IOException e) {
            throw new OAuthException(OAuthError.HTTP_SERVER_FAILED,
                    "端口 " + config.getCallbackPort() + " 已被占用，请修改 callback.port 配置", e);
        }
    }

    /**
     * Stops the HTTP server gracefully.
     */
    public void stop() {
        if (server != null) {
            server.stop(2);
            logger.info("OAuth 回调服务器已停止");
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
        }
    }

    /**
     * Returns true if the server is currently running.
     */
    public boolean isRunning() {
        return server != null;
    }
}
