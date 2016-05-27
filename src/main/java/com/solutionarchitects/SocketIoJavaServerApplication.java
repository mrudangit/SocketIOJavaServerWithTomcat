package com.solutionarchitects;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@SpringBootApplication
@EnableAutoConfiguration
@EnableWebSocket
public class SocketIoJavaServerApplication {

	public static void main(String[] args) {

		ConfigurableApplicationContext run = SpringApplication.run(SocketIoJavaServerApplication.class, args);
	}
}
