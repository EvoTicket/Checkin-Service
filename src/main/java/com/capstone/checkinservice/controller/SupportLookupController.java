package com.capstone.checkinservice.controller;

import com.capstone.checkinservice.dto.common.BaseResponse;
import com.capstone.checkinservice.dto.response.OwnerInfoResponse;
import com.capstone.checkinservice.service.SupportLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checker")
@RequiredArgsConstructor
@Tag(
        name = "Checker Support",
        description = "Support-only APIs for masked ticket ownership lookup and dispute context"
)
public class SupportLookupController {
    private final SupportLookupService supportLookupService;

    @GetMapping("/tickets/{ticketAssetId}/owner-info")
    @Operation(
            summary = "Get masked ticket owner support context",
            description = """
                    Returns read-only, support-only ticket ownership and dispute context
                    for an assigned checker. The response does not expose full PII and
                    does not provide manual check-in or override actions.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner info fetched"),
            @ApiResponse(responseCode = "400", description = "Invalid ticket id"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Checker is not assigned to the ticket scope"),
            @ApiResponse(responseCode = "404", description = "Ticket access state not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected system error")
    })
    public ResponseEntity<BaseResponse<OwnerInfoResponse>> getOwnerInfo(
            @PathVariable Long ticketAssetId
    ) {
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Fetched ticket owner info successfully",
                supportLookupService.getOwnerInfo(ticketAssetId)
        ));
    }
}
