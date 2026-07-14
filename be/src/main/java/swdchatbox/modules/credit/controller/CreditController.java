package swdchatbox.modules.credit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swdchatbox.modules.credit.dto.CreditResponse;
import swdchatbox.modules.credit.entity.UserCreditAccount;
import swdchatbox.modules.credit.service.CreditService;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.repository.UserRepository;
import swdchatbox.shared.exception.ResourceNotFoundException;

@RestController
@RequestMapping("/credits")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CreditController {

    private final CreditService creditService;
    private final UserRepository userRepository;

    @Operation(summary = "Lấy thông tin credit hiện tại của tôi")
    @GetMapping("/me")
    public ResponseEntity<CreditResponse> getMyCredit(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserCreditAccount account = creditService.getOrCreateAccount(user);

        return ResponseEntity.ok(CreditResponse.builder()
                .remainingCredits(account.getRemainingCredits())
                .periodStartedAt(account.getPeriodStartedAt())
                .nextResetAt(account.getNextResetAt())
                .build());
    }
}
