package com.minh.paymentsystem.user.controller;

import com.minh.paymentsystem.common.dto.PageResponse;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.user.dto.LockUserRequest;
import com.minh.paymentsystem.user.dto.UserResponse;
import com.minh.paymentsystem.user.service.UserService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "Admin API for managing users")
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Get all users", description = "Retrieve a paginated list of all users in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved users"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<UserResponse> response = userService.getAllUsers(pageable);
        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(response));
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
            @PathVariable Long id,
            @RequestBody(required = false) LockUserRequest request
    ) {
        userService.lockUser(id);
        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(null));
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
            @PathVariable Long id
    ) {
        userService.unlockUser(id);
        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(null));
    }
}
