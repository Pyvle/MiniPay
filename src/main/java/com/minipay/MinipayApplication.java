package com.minipay;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MinipayApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MinipayApplication.class);
        boolean commandMode = Arrays.stream(args)
        .anyMatch(arg -> arg.equals("--app.command=init")
                || arg.equals("--app.command=recover"));

        if (commandMode) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }

        var context = application.run(args);

        if (commandMode) {
            context.close();
        }
    }

}
