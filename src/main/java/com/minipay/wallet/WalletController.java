package com.minipay.wallet;

import com.minipay.common.ErrorResponse;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minipay.wallet.dto.CreateWalletRequest;
import com.minipay.wallet.dto.AmountRequest;
import com.minipay.wallet.dto.TransferRequest;
import com.minipay.wallet.dto.WalletResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.minipay.idempotency.IdempotencyResponse;
import com.minipay.transaction.TransactionService;
import com.minipay.transaction.TransactionStatus;
import com.minipay.transaction.TransactionType;
import com.minipay.transaction.dto.TransactionResponse;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.UUID;

import com.minipay.common.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Счета", description = "Взаимодействие со счетами")
@RestController
@RequestMapping("/api/wallets")
public class WalletController {
    private final WalletService walletService;
    private final TransactionService transactionService;

    public WalletController(WalletService walletService, TransactionService transactionService) {
        this.walletService = walletService;
        this.transactionService = transactionService;
    }

    @Operation(summary = "Создать кошелек по ID пользователя", description = "Возвращает информацию о созданном кошельке")
    @ApiResponse(responseCode = "400", description = "Некорректное тело запроса или отсутствует ID пользователя",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Пользователь не найден",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "У пользователя уже есть кошелёк",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(@Valid @RequestBody CreateWalletRequest request) {
        Wallet wallet = walletService.createWalletForUser(request.getUserId());
        return new WalletResponse(wallet);
    }

    @Operation(summary = "Пополнить кошелек по ID на указанную сумму", description = "Возвращает информацию о транзакции")
    @ApiResponse(responseCode = "400", description = "Некорректный ID, JSON, сумма; отсутствует или неверен UUID Idempotency-Key",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Кошелёк не найден",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Превышен лимит баланса, конфликт ключа или параллельных операций",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{id}/deposit")
    @ApiResponse(responseCode = "200", description = "Созданная транзакция. Повтор возвращает сохранённый ответ; старые ключи могут содержать прежний JSON кошелька.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionResponse.class)))
    public ResponseEntity<String> deposit(
            @Parameter(description = "ID кошелька", example = "25") @PathVariable("id") Long id,
            @Valid @RequestBody AmountRequest request,
            @Parameter(description = "Обязательный UUID: новый ключ для новой операции. Повтор с теми же параметрами возвращает первоначальные статус и JSON без повторного движения денег; другие параметры с прежним ключом дают 409.", required = true, schema = @Schema(type = "string", format = "uuid"))
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {

        IdempotencyResponse result = walletService.deposit(
                id, request.getAmount(), idempotencyKey);

        return ResponseEntity.status(result.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.body());
    }

    @Operation(summary = "Вывести с кошелька по ID указанную сумму", description = "Возвращает информацию о транзакции")
    @ApiResponse(responseCode = "400", description = "Некорректный ID, JSON, сумма; отсутствует или неверен UUID Idempotency-Key",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Кошелёк не найден",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Недостаточно средств, конфликт ключа или параллельных операций",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{id}/withdraw")
    @ApiResponse(responseCode = "200", description = "Созданная транзакция. Повтор возвращает сохранённый ответ; старые ключи могут содержать прежний JSON кошелька.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionResponse.class)))
    public ResponseEntity<String> withdraw(
            @Parameter(description = "ID кошелька", example = "25") @PathVariable("id") Long id,
            @Valid @RequestBody AmountRequest request,
            @Parameter(description = "Обязательный UUID: новый ключ для новой операции. Повтор с теми же параметрами возвращает первоначальные статус и JSON без повторного движения денег; другие параметры с прежним ключом дают 409.", required = true, schema = @Schema(type = "string", format = "uuid"))
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {

        IdempotencyResponse result = walletService.withdraw(
                id, request.getAmount(), idempotencyKey);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.body());
    }

    @Operation(summary = "Перевести с одного кошелька на другой по ID кошельков указанную сумму", description = "Возвращает информацию о транзакции")
    @ApiResponse(responseCode = "400", description = "Некорректный ID, JSON, сумма или UUID; отсутствует заголовок или получатель; перевод самому себе",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Кошелёк отправителя или получателя не найден",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Недостаточно средств, превышен лимит баланса получателя, конфликт ключа или параллельных операций",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{fromWalletId}/transfer")
    @ApiResponse(responseCode = "200", description = "Созданная транзакция. Повтор возвращает сохранённый ответ; старые ключи могут содержать прежний JSON кошелька.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionResponse.class)))
    public ResponseEntity<String> transfer(
            @Parameter(description = "ID кошелька с которого будет произведен перевод", example = "25") @PathVariable("fromWalletId") Long fromWalletId,
            @Valid @RequestBody TransferRequest request,
            @Parameter(description = "Обязательный UUID: новый ключ для новой операции. Повтор с теми же параметрами возвращает первоначальные статус и JSON без повторного движения денег; другие параметры с прежним ключом дают 409.", required = true, schema = @Schema(type = "string", format = "uuid"))
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {

        IdempotencyResponse result = walletService.transfer(fromWalletId,
                request.getToWalletId(),
                request.getAmount(),
                idempotencyKey);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.body());
    }

    @Operation(summary = "Получить информацию о кошельке по ID", description = "Возвращает информацию о кошельке")
    @ApiResponse(responseCode = "400", description = "Некорректный формат ID кошелька",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Кошелёк не найден",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public WalletResponse getWalletById(
            @Parameter(description = "ID кошелька", example = "25") @PathVariable("id") Long id) {
        Wallet wallet = walletService.getWalletById(id);
        return new WalletResponse(wallet);
    }

    @Operation(summary = "Получить информацию о кошельке по ID пользователя", description = "Возвращает информацию о кошельке")
    @ApiResponse(responseCode = "400", description = "Некорректный формат ID пользователя",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Кошелёк пользователя не найден",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/by-user/{userId}")
    public WalletResponse getWalletByUserId(
            @Parameter(description = "ID пользователя", example = "25") @PathVariable("userId") Long userId) {
        Wallet wallet = walletService.getWalletByUserId(userId);
        return new WalletResponse(wallet);
    }

    @Operation(summary = "Получить страницу истории кошелька", description = "Возвращает входящие и исходящие операции, отсортированные по createdAt DESC, затем id DESC. Нумерация страниц с нуля, размер от 1 до 100. Фильтры необязательны и комбинируются; пустые значения снимают ограничение. Время без часового пояса, диапазон [from, to): from включительно, to исключительно. Равные границы дают пустую страницу, обратный диапазон — 400. Счётчики учитывают все фильтры; страница за концом истории пуста.")
    @ApiResponse(responseCode = "400", description = "Некорректный ID, page, size, enum или дата; from позже to",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Кошелёк не найден",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}/transactions")
    public PageResponse<TransactionResponse> getTransactionByWalletId(
            @Parameter(description = "ID кошелька", example = "25") @PathVariable("id") Long id,
            @RequestParam(name = "type", required = false) TransactionType type,
            @RequestParam(name = "status", required = false) TransactionStatus status,
            @Parameter(description = "Номер страницы с нуля", schema = @Schema(minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Размер страницы; превышение 100 отклоняется с 400", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(name = "size", defaultValue = "20") int size,
            @Parameter(description = "Нижняя граница включительно, без часового пояса", example = "2026-09-01T00:00:00")
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Верхняя граница исключительно, без часового пояса", example = "2026-10-01T00:00:00")
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return PageResponse.from(transactionService.getTransactionsByWalletId(id, type, status, page, size, from, to));
    }

}
