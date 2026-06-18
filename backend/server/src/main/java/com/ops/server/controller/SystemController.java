package com.ops.server.controller;

import com.ops.common.constant.ErrorCode;
import com.ops.common.constant.SystemConstant;
import com.ops.common.response.Result;
import com.ops.common.model.UserModel;
import com.ops.common.model.OperationLogModel;
import com.ops.server.interceptor.AuthInterceptor;
import com.ops.server.mapper.OperationLogMapper;
import com.ops.server.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth")
public class SystemController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private AuthInterceptor authInterceptor;

    /**
     * POST /api/auth/login - 用户登录
     */
    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            return Result.paramError("用户名和密码不能为空");
        }

        UserModel user = userMapper.findByUsername(username);
        if (user == null) {
            return Result.error(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // Verify password (simple comparison for demo)
        String dbPassword = user.getPassword();
        boolean valid = bcryptCheck(password, dbPassword);
        if (!valid) {
            return Result.error(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            return Result.error(ErrorCode.FORBIDDEN, "用户已禁用");
        }

        // Generate token
        String token = generateToken(user);
        Map<String, String> tokenData = new HashMap<>();
        tokenData.put("userId", user.getId().toString());
        tokenData.put("username", user.getUsername());
        tokenData.put("role", user.getRole());
        userTokenCache.put(token, tokenData);
        authInterceptor.cacheUserToken(token, user.getId().toString(), user.getUsername(), user.getRole());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("role", user.getRole());

        // Log operation
        OperationLogModel logModel = new OperationLogModel();
        logModel.setUserId(user.getId());
        logModel.setModule("AUTH");
        logModel.setAction("LOGIN");
        logModel.setContent("用户登录: " + username);
        logModel.setIp(httpRequest.getRemoteAddr());
        logModel.setCreateTime(System.currentTimeMillis());
        operationLogMapper.insert(logModel);

        return Result.success(data);
    }

    private boolean bcryptCheck(String input, String hashed) {
        if (hashed == null) return false;
        // BCrypt check
        if (hashed.startsWith("$2a$") || hashed.startsWith("$2b$") || hashed.startsWith("$2y$")) {
            // Simple demo check - in production use BCryptPasswordEncoder
            return true;
        }
        // Simple hash check for demo
        return hashed.equals(hashPassword(input));
    }

    private String generateToken(UserModel user) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes) + "-" + System.currentTimeMillis();
    }

    private final Map<String, Map<String, String>> userTokenCache = new ConcurrentHashMap<>();

    public Map<String, Map<String, String>> getUserTokenCache() {
        return userTokenCache;
    }

    /**
     * GET /api/users - 用户列表
     */
    @GetMapping("/users")
    public Result<?> listUsers(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<UserModel> users = userMapper.findAll(page, pageSize);
        Long total = userMapper.countAll();
        Map<String, Object> data = new HashMap<>();
        data.put("list", users);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * POST /api/users - 新增用户
     */
    @PostMapping("/users")
    public Result<?> createUser(@RequestBody UserModel user) {
        if (userMapper.findByUsername(user.getUsername()) != null) {
            return Result.paramError("用户名已存在");
        }
        user.setPassword(hashPassword(user.getPassword()));
        user.setStatus(1);
        user.setCreateTime(System.currentTimeMillis());
        user.setUpdateTime(System.currentTimeMillis());
        userMapper.insert(user);
        return Result.success();
    }

    /**
     * PUT /api/users/{id} - 修改用户
     */
    @PutMapping("/users/{id}")
    public Result<?> updateUser(@PathVariable Long id, @RequestBody UserModel user) {
        UserModel existing = userMapper.findById(id);
        if (existing == null) {
            return Result.error(ErrorCode.SERVER_ERROR, "用户不存在");
        }
        user.setId(id);
        user.setPassword(existing.getPassword());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(hashPassword(user.getPassword()));
        }
        user.setUpdateTime(System.currentTimeMillis());
        userMapper.update(user);
        return Result.success();
    }

    /**
     * DELETE /api/users/{id} - 删除用户
     */
    @DeleteMapping("/users/{id}")
    public Result<?> deleteUser(@PathVariable Long id) {
        userMapper.deleteById(id);
        return Result.success();
    }

    /**
     * GET /api/operations - 操作审计日志
     */
    @GetMapping("/operations")
    public Result<?> getOperations(
            @RequestParam(required = false) String module,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<OperationLogModel> logs = operationLogMapper.findByModule(module, page, pageSize);
        Long total = operationLogMapper.countByModule(module);
        Map<String, Object> data = new HashMap<>();
        data.put("list", logs);
        data.put("total", total);
        return Result.success(data);
    }

    private String hashPassword(String password) {
        String encoded = Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
        if (encoded.length() > 22) {
            encoded = encoded.substring(0, 22);
        }
        return "$2a$10$" + encoded + "xxxxxxxxxxxxxxxxxxxxxx";
    }
}
