package com.ujjwal.Stream_Backend;

import org.springframework.boot.SpringApplication;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication()
public class StreamBackendApplication {
	
	static {
	Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry ->
            System.setProperty(entry.getKey(), entry.getValue())
        );
    }
	
	public static void main(String[] args) {
		SpringApplication.run(StreamBackendApplication.class, args);
	}

}
