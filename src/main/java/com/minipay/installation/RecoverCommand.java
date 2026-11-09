package com.minipay.installation;

import java.io.Console;
import java.util.Arrays;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.command", havingValue = "recover")
public class RecoverCommand implements ApplicationRunner {
    private final InstallationService installationService;

    public RecoverCommand(InstallationService installationService) {
        this.installationService = installationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Run the JAR in an interactive terminal");
        }

        char[] password = null;
        char[] confirmation = null;

        try {
            String email = console.readLine("Email: ");
            if (email == null) {
                throw new IllegalArgumentException("Input was interrupted");
            }

            password = console.readPassword("New password: ");
            if (password == null) {
                throw new IllegalArgumentException("Input was interrupted");
            }

            confirmation = console.readPassword("Confirm new password: ");
            if (confirmation == null) {
                throw new IllegalArgumentException("Input was interrupted");
            }

            if (!Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Passwords do not match");
            }

            installationService.recover(email, password);
            console.printf("Account recovery completed.%n");
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }
}
