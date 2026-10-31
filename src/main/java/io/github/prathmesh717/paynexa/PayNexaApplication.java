package io.github.prathmesh717.paynexa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class PayNexaApplication {
    public static void main(String[] args) {
        SpringApplication.run(PayNexaApplication.class, args);
    }
}
