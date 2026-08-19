package com.karmaq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KarmaQApplication {

    public static void main(String[] args) {
        SpringApplication.run(KarmaQApplication.class, args);
    }
}
