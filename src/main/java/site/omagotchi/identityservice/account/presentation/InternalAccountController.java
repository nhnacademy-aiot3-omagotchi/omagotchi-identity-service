package site.omagotchi.identityservice.account.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.account.application.AccountQueryService;
import site.omagotchi.identityservice.account.presentation.request.InternalAccountBatchRequest;
import site.omagotchi.identityservice.account.presentation.response.InternalAccountResponse;
import site.omagotchi.identityservice.account.presentation.response.InternalAccountSearchResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/accounts")
@RequiredArgsConstructor
public class InternalAccountController {

    private final AccountQueryService accountQueryService;

    @GetMapping("/{accountId}")
    public ResponseEntity<InternalAccountResponse> getAccount(
            @PathVariable UUID accountId
    ) {
        return ResponseEntity.ok(
                InternalAccountResponse.from(accountQueryService.getById(accountId))
        );
    }

    @PostMapping("/batch")
    public ResponseEntity<List<InternalAccountResponse>> getAccounts(
            @Valid @RequestBody InternalAccountBatchRequest request
    ) {
        List<InternalAccountResponse> accounts = accountQueryService
                .findAllByIds(request.accountIds())
                .stream()
                .map(InternalAccountResponse::from)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/search")
    public ResponseEntity<List<InternalAccountSearchResponse>> searchAccounts(
            @RequestParam String query
    ) {
        List<InternalAccountSearchResponse> accounts = accountQueryService
                .searchByNameOrEmail(query)
                .stream()
                .map(InternalAccountSearchResponse::from)
                .toList();
        return ResponseEntity.ok(accounts);
    }
}
