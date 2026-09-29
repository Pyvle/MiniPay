package com.minipay.transaction;

import com.minipay.common.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.minipay.transaction.dto.TransactionResponse;

@Tag(name = "Транзакции", description = "Просмотр финансовых операций")
@RestController
@RequestMapping("api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "Получить транзакцию по ID", description = "Возвращает сведения об одной сохранённой операции.")
    @ApiResponse(responseCode = "400", description = "Некорректный формат ID транзакции",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Транзакция не найдена",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("{id}")
    public TransactionResponse getTransactionById(
            @Parameter(description = "ID транзакции", example = "25") @PathVariable("id") Long id) {
        Transaction transaction = transactionService.getTransactionById(id);
        return new TransactionResponse(transaction);
    }

}
