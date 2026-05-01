package com.capstone.checkinservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.capstone.checkinservice.dto.common.BaseResponse;
import com.capstone.checkinservice.dto.response.QrTokenResponse;
import com.capstone.checkinservice.service.BuyerQrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "Buyer QR",
        description = "APIs for issuing short-lived Dynamic QR tokens for ticket owners"
)
public class BuyerQrController {
    private final BuyerQrService buyerQrService;

    @GetMapping("/{ticketAssetId}/qr-token")
    @Operation(
            summary = "Issue Dynamic QR token",
            description = """
                    Issues a short-lived signed QR token for the current ticket owner.
                    The QR token is not the ticket itself and must not be issued for USED,
                    LOCKED_RESALE, or CANCELLED tickets.
                    """
    )
    public ResponseEntity<BaseResponse<QrTokenResponse>> issueQrToken(@PathVariable Long ticketAssetId) {
        QrTokenResponse response = buyerQrService.issueQrToken(ticketAssetId);
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "QR token issued successfully",
                response
        ));
    }
}
