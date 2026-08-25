package com.hospital.spd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpdApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpdApplication.class, args);
    }
}
