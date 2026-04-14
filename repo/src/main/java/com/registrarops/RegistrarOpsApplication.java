package com.registrarops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RegistrarOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistrarOpsApplication.class, args);
    }
}
