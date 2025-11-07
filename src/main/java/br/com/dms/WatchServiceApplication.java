package br.com.dms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WatchServiceApplication.class, args);
    }
}
