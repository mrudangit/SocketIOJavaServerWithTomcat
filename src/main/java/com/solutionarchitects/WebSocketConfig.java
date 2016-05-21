package com.solutionarchitects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass().getName());


    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {

        LOGGER.info("Initializing WebSocket");

        webSocketHandlerRegistry.addHandler(new WebSocketHandler(),"/socket.io/*");

    }
}
