package com.demo.user.controller;

import com.demo.user.model.UserEntity;
import com.demo.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User API", description = "會員管理")
public class UserController {

    private final UserService userService;

    @Operation(summary = "查詢會員資料")
    @GetMapping("/{userId}")
    public ResponseEntity<UserEntity> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @Operation(summary = "健康確認用 - 內部服務呼叫")
    @GetMapping("/internal/{userId}")
    public ResponseEntity<UserEntity> getUserInternal(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }
}



 
 
// trigger rebuild 2026年 3月31日 星期二 19時16分11秒 CST
// 2026年 3月31日 星期二 19時26分15秒 CST
// 2026年 3月31日 星期二 19時32分40秒 CST
// 2026年 3月31日 星期二 19時37分10秒 CST
// 2026年 3月31日 星期二 19時42分06秒 CST
// 2026年 3月31日 星期二 19時47分45秒 CST
// 2026年 3月31日 星期二 19時51分17秒 CST
// 2026年 3月31日 星期二 19時51分23秒 CST
// 2026年 3月31日 星期二 19時54分09秒 CST
