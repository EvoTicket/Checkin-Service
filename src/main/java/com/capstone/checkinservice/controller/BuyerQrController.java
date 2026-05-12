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
        description = "Các API cấp mã QR Token động ngắn hạn cho chủ sở hữu vé"
)
public class BuyerQrController {
    private final BuyerQrService buyerQrService;

    @GetMapping("/{ticketAssetId}/qr-token")
    @Operation(
            summary = "Cấp mã QR Token động",
            description = """
                    Cấp một mã QR token đã ký, có thời hạn ngắn cho chủ sở hữu vé hiện tại.
                    Mã QR token này không phải là bản thân chiếc vé và không được cấp cho các vé đã SỬ DỤNG (USED),
                    đang khóa để bán lại (LOCKED_RESALE), hoặc đã bị HỦY (CANCELLED).
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
