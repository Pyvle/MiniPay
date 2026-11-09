package com.minipay.installation;

import java.io.Console;
import java.util.Arrays;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.command", havingValue = "init")
public class InitCommand implements ApplicationRunner {

    private final InstallationService installationService;
    private final InstallationStateRepository installationStateRepository;

    public InitCommand(InstallationService installationService,
            InstallationStateRepository installationStateRepository) {
        this.installationService = installationService;
        this.installationStateRepository = installationStateRepository;
    }
    @Override
    public void run(ApplicationArguments args) {
        if (installationStateRepository.existsById(1L)) {
            System.out.println("Первоначальная настройка уже выполнена. Команда init завершена");
            return;
        }
        Console console = System.console();

        if (console == null) {
            throw new IllegalStateException(
                    "Запустите JAR в интерактивном терминале");
        }
        String name = console.readLine("Имя: ");
        String email = console.readLine("Email: ");
        char[] password = null;
        char[] confirmation = null;
        try {
            password = console.readPassword("Пароль: ");
            confirmation = console.readPassword("Повторите пароль: ");

            if (name == null || email == null || password == null || confirmation == null) {
                throw new IllegalArgumentException("Ввод прерван. Первоначальная настройка отменена");
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException("Имя не должно быть пустым");
            }
            if (email.isBlank()) {
                throw new IllegalArgumentException("Email не должен быть пустым");
            }
            if (password.length < 8 || password.length > 128) {
                throw new IllegalArgumentException("Пароль должен содержать от 8 до 128 символов");
            }
            boolean onlyWhitespace = true;
            for (char character : password) {
                if (!Character.isWhitespace(character)) {
                    onlyWhitespace = false;
                    break;
                }
            }
            if (onlyWhitespace) {
                throw new IllegalArgumentException("Пароль не должен состоять только из пробелов");
            }
            if (!Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Пароль и подтверждение не совпадают");
            }

            installationService.initialize(name, email, password);
            console.printf("Первоначальная настройка завершена%n");
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
