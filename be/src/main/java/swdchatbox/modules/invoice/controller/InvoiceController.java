package swdchatbox.modules.invoice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.modules.invoice.dto.response.InvoiceResponse;
import swdchatbox.modules.invoice.service.InvoiceService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @Operation(summary = "Lịch sử hóa đơn subscription của tôi")
    @GetMapping("/me")
    public ResponseEntity<List<InvoiceResponse>> myInvoices(Authentication authentication) {
        return ResponseEntity.ok(invoiceService.findMyInvoices(authentication.getName()));
    }

    @Operation(summary = "Chi tiết hóa đơn subscription")
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(invoiceService.findById(id, authentication.getName()));
    }
}
