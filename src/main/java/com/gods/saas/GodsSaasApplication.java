package com.gods.saas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@EnableFeignClients
@EnableAsync

public class GodsSaasApplication {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String hash = encoder.encode("d3rw1n");

        System.out.println("===================================");
        System.out.println("PASSWORD HASH: " + hash);
        System.out.println("===================================");

        SpringApplication.run(GodsSaasApplication.class, args);
    }
}
