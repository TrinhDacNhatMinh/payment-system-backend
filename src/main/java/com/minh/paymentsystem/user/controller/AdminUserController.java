package com.minh.paymentsystem.user.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minh.paymentsystem.common.constants.PaginationConstants;
import com.minh.paymentsystem.common.dto.PageResponse;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.user.dto.LockUserRequest;
import com.minh.paymentsystem.user.dto.UserResponse;
import com.minh.paymentsystem.user.service.UserService;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "Admin API for managing users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Get all users", description = "Retrieve a paginated list of all users in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved users"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<PageResponse<UserResponse>>> getAllUsers(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Number of items per page (1-100)") @RequestParam(defaultValue = "10") @Min(1) @Max(PaginationConstants.MAX_SIZE) int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<UserResponse> response = userService.getAllUsers(pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(BaseResponse.success(response));
    }

    @Operation(summary = "Lock a user", description = "Lock a specific user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User successfully locked"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PatchMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> lockUser(
            @Parameter(description = "ID of the user account to lock", example = "123") @PathVariable Long id,
            @RequestBody(required = false) LockUserRequest request) {
        userService.lockUser(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(BaseResponse.success(null));
    }

    @Operation(summary = "Unlock a user", description = "Unlock a specific user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User successfully unlocked"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PatchMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> unlockUser(
            @Parameter(description = "ID of the user account to unlock", example = "123") @PathVariable Long id) {
        userService.unlockUser(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(BaseResponse.success(null));
    }
}
