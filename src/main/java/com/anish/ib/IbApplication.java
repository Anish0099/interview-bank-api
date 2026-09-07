package com.anish.ib;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class IbApplication {
    public static void main(String[] args) {
        SpringApplication.run(IbApplication.class, args);
    }
}
