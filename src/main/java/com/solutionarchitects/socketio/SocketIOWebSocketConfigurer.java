package com.solutionarchitects.socketio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class SocketIOWebSocketConfigurer implements WebSocketConfigurer {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass().getName());

    @Autowired
    SocketIOWebSocketProtocolHandler socketIOWebSocketProtocolHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {

        LOGGER.info("Initializing Socket.IO WebSocket");

        webSocketHandlerRegistry.addHandler(socketIOWebSocketProtocolHandler,"/socket.io/*");

    }
}
