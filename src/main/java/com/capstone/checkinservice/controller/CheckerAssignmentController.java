package com.capstone.checkinservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.capstone.checkinservice.dto.common.BaseResponse;
import com.capstone.checkinservice.dto.response.CheckerAssignmentResponse;
import com.capstone.checkinservice.service.CheckerAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checker")
@RequiredArgsConstructor
@Tag(
        name = "Checker Assignments",
        description = "Các API truy xuất danh sách sự kiện, suất diễn, cổng và ca trực được phân công cho nhân viên kiểm soát (checker)"
)
public class CheckerAssignmentController {
    private final CheckerAssignmentService checkerAssignmentService;

    @GetMapping("/assignments")
    @Operation(
            summary = "Lấy danh sách phân công của checker",
            description = """
                    Trả về các sự kiện, suất diễn, cổng và ca trực được phân công cho checker hiện tại.
                    Checker phải chọn phạm vi phân công (assignment scope) trước khi thực hiện quét vé.
                    """
    )
    public ResponseEntity<BaseResponse<CheckerAssignmentResponse>> getAssignments() {
        return ResponseEntity.ok(BaseResponse.of(
                HttpStatus.OK.value(),
                "Fetched checker assignments successfully",
                checkerAssignmentService.getAssignmentsForCurrentChecker()
        ));
    }
}
