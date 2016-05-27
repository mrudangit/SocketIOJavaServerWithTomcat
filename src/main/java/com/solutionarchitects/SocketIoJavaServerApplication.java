package com.solutionarchitects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@SpringBootApplication
@EnableAutoConfiguration
@EnableWebSocket
public class SocketIoJavaServerApplication {

	private static Logger logger = LoggerFactory.getLogger(SocketIoJavaServerApplication.class.getName());


	public static void main(String[] args) {

		ConfigurableApplicationContext run = SpringApplication.run(SocketIoJavaServerApplication.class, args);

		logger.info("Application Context Created : {}",run);
	}
}
