package com.solutionarchitects;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableAutoConfiguration
public class SocketIOConfig {

    @Override
    public String toString() {
        return "SocketIOConfig{" +
                "pingTimeout=" + pingTimeout +
                ", pingInterval=" + pingInterval +
                '}';
    }

    @Value("${socketio.pingTimeout:60000}")
    public int pingTimeout;


    @Value("${socketio.pingInterval:25000}")
    public int pingInterval;


}
