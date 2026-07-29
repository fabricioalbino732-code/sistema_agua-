package com.aguasystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SistemaAguaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SistemaAguaApplication.class, args);
    }
}
