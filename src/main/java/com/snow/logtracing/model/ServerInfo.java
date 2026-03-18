package com.snow.logtracing.model;

import com.snow.logtracing.util.WebUtil;
import lombok.Getter;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;

/**
 * 服务器信息
 *
 * @author Chill
 */
@Getter
@AutoConfiguration
public class ServerInfo implements SmartInitializingSingleton {
    private final ServerProperties serverProperties;
    private String hostName;
    private String ip;
    private Integer port;
    private String ipWithPort;

    @Autowired(required = false)
    public ServerInfo(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        this.hostName = WebUtil.getHostName();
        this.ip = WebUtil.getIP();
        this.port = serverProperties.getPort();
        this.ipWithPort = String.format("%s:%d", ip, port);
    }
}
