package com.ops.server.controller;

import com.ops.common.constant.ErrorCode;
import com.ops.common.response.Result;
import com.ops.common.model.UserModel;
import com.ops.common.model.OperationLogModel;
import com.ops.common.util.PasswordValidator;
import com.ops.server.interceptor.AuthInterceptor;
import com.ops.server.mapper.OperationLogMapper;
import com.ops.server.mapper.SysConfigMapper;
import com.ops.server.mapper.UserMapper;
import com.ops.server.mapper.TenantMapper;
import com.ops.common.model.TenantUserModel;
import com.ops.server.service.CaptchaService;
import com.ops.server.service.LoginAttemptService;
import com.ops.server.config.AdminConfig;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;

@RestController
@RequestMapping("/auth")
public class SystemController {

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private AdminConfig adminConfig;

    // ===== 白名单自动登录（调试/自动化专用，默认关闭） =====
    @Value("${easyops.auth.auto-login-enabled:false}")
    private boolean autoLoginEnabled;

    @Value("${easyops.auth.auto-login-username:admin}")
    private String autoLoginUsername;

    @Value("${easyops.auth.auto-login-whitelist:}")
    private String autoLoginWhitelistRaw;

    /**
     * GET /api/auth/captcha - 获取登录验证码
     */
    @GetMapping("/captcha")
    public Result<?> captcha() {
        return Result.success(captchaService.generate());
    }

    /**
     * POST /api/auth/login - 用户登录
     */
    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String username = request.get("username");
        String password = request.get("password");
        String captchaId = request.get("captchaId");
        String captchaCode = request.get("captchaCode");

        if (username == null || password == null) {
            return Result.paramError("用户名和密码不能为空");
        }

        // SEC: 检查账号是否已被锁定
        String lockMsg = loginAttemptService.checkLocked(username);
        if (lockMsg != null) {
            return Result.error(ErrorCode.FORBIDDEN, lockMsg);
        }

        if (!captchaService.verify(captchaId, captchaCode)) {
            loginAttemptService.onFailure(username);
            return Result.paramError("验证码错误或已过期");
        }

        UserModel user = userMapper.findByUsername(username);
        if (user == null) {
            loginAttemptService.onFailure(username);
            return Result.error(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // Verify password (BCrypt)
        String dbPassword = user.getPassword();
        boolean valid = bcryptCheck(password, dbPassword);
        if (!valid) {
            loginAttemptService.onFailure(username);
            return Result.error(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            return Result.error(ErrorCode.FORBIDDEN, "用户已禁用");
        }

        // 登录成功，清除失败记录
        loginAttemptService.onSuccess(username);

        // Generate token
        String token = generateToken();
        Map<String, String> tokenData = new HashMap<>();
        tokenData.put("userId", user.getId().toString());
        tokenData.put("username", user.getUsername());
        tokenData.put("role", user.getRole());
        userTokenCache.put(token, tokenData);
        authInterceptor.cacheUserToken(token, user.getId().toString(), user.getUsername(), user.getRole());
        AuthInterceptor.UserAuthContext loginAuth = authInterceptor.lookupUserAuth(token);
        Long tenantId = loginAuth == null ? null : loginAuth.getTenantId();
        String tenantRole = loginAuth == null ? null : loginAuth.getTenantRole();
        String tenantName = tenantId == null ? null : resolveTenantName(tenantId);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        data.put("tenantId", tenantId);
        data.put("tenantRole", tenantRole);
        data.put("tenantName", tenantName);

        // Log operation (non-critical, suppress error if table doesn't exist yet)
        try {
            OperationLogModel logModel = new OperationLogModel();
            logModel.setUserId(user.getId());
            logModel.setModule("AUTH");
            logModel.setAction("LOGIN");
            logModel.setContent("用户登录: " + username);
            logModel.setIp(httpRequest.getRemoteAddr());
            logModel.setCreateTime(System.currentTimeMillis());
            operationLogMapper.insert(logModel);
        } catch (Exception e) {
            System.err.println("[Auth] Failed to write login log: " + e.getMessage());
        }

        return Result.success(data);
    }

    /**
     * POST /api/auth/auto-login - 白名单自动登录（调试/自动化专用）
     *
     * 携带白名单内的 key（请求体 key 或请求头 X-Auto-Login-Key）即可直接拿到已登录 token，
     * 完全跳过账号密码与验证码，返回结构与 /auth/login 一致。
     *
     * 安全约束（默认全部不满足，等同于不存在该接口）：
     *   1. easyops.auth.auto-login-enabled 必须为 true（默认 false）
     *   2. easyops.auth.auto-login-whitelist 必须配置至少一个非空 key
     *   3. 请求携带的 key 必须精确命中白名单
     * 三者任一不满足均直接拒绝，避免外网裸奔。
     */
    @PostMapping("/auto-login")
    public Result<?> autoLogin(@RequestBody(required = false) Map<String, String> request,
                               HttpServletRequest httpRequest) {
        if (!autoLoginEnabled) {
            return Result.error(ErrorCode.FORBIDDEN, "自动登录未启用（easyops.auth.auto-login-enabled=false）");
        }
        Set<String> whitelist = Arrays.stream(autoLoginWhitelistRaw.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
        if (whitelist.isEmpty()) {
            return Result.error(ErrorCode.FORBIDDEN, "自动登录白名单为空，拒绝请求");
        }

        String key = request != null ? request.get("key") : null;
        if (key == null || key.isEmpty()) {
            key = httpRequest.getHeader("X-Auto-Login-Key");
        }
        if (key == null || !whitelist.contains(key.trim())) {
            return Result.error(ErrorCode.FORBIDDEN, "key 不在白名单内");
        }

        UserModel user = userMapper.findByUsername(autoLoginUsername);
        if (user == null) {
            return Result.error(ErrorCode.SERVER_ERROR, "自动登录目标用户不存在: " + autoLoginUsername);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return Result.error(ErrorCode.FORBIDDEN, "用户已禁用");
        }

        // 复用与正常登录一致的 token 生成/缓存逻辑，保证后续接口鉴权透明无差异
        String token = generateToken();
        Map<String, String> tokenData = new HashMap<>();
        tokenData.put("userId", user.getId().toString());
        tokenData.put("username", user.getUsername());
        tokenData.put("role", user.getRole());
        userTokenCache.put(token, tokenData);
        authInterceptor.cacheUserToken(token, user.getId().toString(), user.getUsername(), user.getRole());
        AuthInterceptor.UserAuthContext autoAuth = authInterceptor.lookupUserAuth(token);
        Long tenantId = autoAuth == null ? null : autoAuth.getTenantId();
        String tenantRole = autoAuth == null ? null : autoAuth.getTenantRole();
        String tenantName = tenantId == null ? null : resolveTenantName(tenantId);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        data.put("tenantId", tenantId);
        data.put("tenantRole", tenantRole);
        data.put("tenantName", tenantName);

        try {
            OperationLogModel logModel = new OperationLogModel();
            logModel.setUserId(user.getId());
            logModel.setModule("AUTH");
            logModel.setAction("AUTO_LOGIN");
            logModel.setContent("白名单自动登录: " + autoLoginUsername);
            logModel.setIp(httpRequest.getRemoteAddr());
            logModel.setCreateTime(System.currentTimeMillis());
            operationLogMapper.insert(logModel);
        } catch (Exception e) {
            System.err.println("[Auth] Failed to write auto-login log: " + e.getMessage());
        }
        return Result.success(data);
    }

    /**
     * POST /api/auth/reset - 将管理员密码重置为默认密码（需管理员身份）
     */
    @PostMapping("/reset")
    public Result<?> resetAdminPassword(HttpServletRequest httpRequest) {
        // SEC: 校验管理员 token
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error(ErrorCode.FORBIDDEN, "无权限：需要管理员身份");
        }
        String token = authHeader.substring(7);
        AuthInterceptor.UserAuthContext userAuth = authInterceptor.lookupUserAuth(token);
        if (userAuth == null || !"admin".equalsIgnoreCase(userAuth.getRole())) {
            return Result.error(ErrorCode.FORBIDDEN, "无权限：需要管理员身份");
        }

        UserModel admin = userMapper.findByUsername("admin");
        if (admin == null) {
            return Result.error(ErrorCode.SERVER_ERROR, "管理员用户不存在");
        }
        String defaultPwd = adminConfig.getDefaultPassword();
        String hashed = BCrypt.hashpw(defaultPwd, BCrypt.gensalt(10));
        admin.setPassword(hashed);
        admin.setUpdateTime(System.currentTimeMillis());
        userMapper.update(admin);

        try {
            OperationLogModel logModel = new OperationLogModel();
            logModel.setUserId(admin.getId());
            logModel.setModule("AUTH");
            logModel.setAction("RESET_PASSWORD");
            logModel.setContent("管理员密码已重置为默认密码 " + defaultPwd);
            logModel.setCreateTime(System.currentTimeMillis());
            operationLogMapper.insert(logModel);
        } catch (Exception e) {
            System.err.println("[Auth] Failed to write reset log: " + e.getMessage());
        }

        return Result.success("密码已重置为默认密码 " + defaultPwd + "（可通过 app.admin.default-password 配置）");
    }

    private boolean bcryptCheck(String input, String hashed) {
        if (hashed == null) return false;
        return BCrypt.checkpw(input, hashed);
    }

    private String generateToken() {
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
     * 从请求中提取当前登录用户身份（复用 AuthInterceptor 的 token 解析）。
     */
    private AuthInterceptor.UserAuthContext getCurrentUserAuth(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) return null;
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return authInterceptor.lookupUserAuth(token);
    }

    private boolean isAdmin(AuthInterceptor.UserAuthContext auth) {
        return auth != null && "admin".equalsIgnoreCase(auth.getRole());
    }

    /** 合法租户角色 */
    private static final Set<String> TENANT_ROLES = new HashSet<>(Arrays.asList(
            "TENANT_ADMIN", "OPERATOR", "VIEWER"));

    /** 为用户补充主租户绑定信息（transient 字段，不落库） */
    private void enrichTenantBinding(UserModel user) {
        if (user == null) return;
        user.setPassword(null);
        try {
            com.ops.common.model.TenantUserModel member = tenantMapper.findFirstActiveMember(user.getId());
            if (member != null) {
                user.setTenantId(member.getTenantId());
                user.setTenantRole(member.getRole());
                user.setTenantName(resolveTenantName(member.getTenantId()));
            }
        } catch (Exception ignored) {
        }
    }

    /** 租户名称解析（供登录/用户列表展示） */
    private String resolveTenantName(Long tenantId) {
        try {
            if (tenantId == null) return null;
            com.ops.common.model.TenantModel tenant = tenantMapper.findById(tenantId);
            return tenant == null ? null : tenant.getName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * GET /api/users - 用户列表
     * SUPER_ADMIN 看全量（带租户绑定）；TENANT_ADMIN 看本租户成员；OPERATOR/VIEWER 只看自己
     */
    @GetMapping("/users")
    public Result<?> listUsers(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        AuthInterceptor.UserAuthContext auth = getCurrentUserAuth(request);
        if (auth == null) {
            return Result.error(ErrorCode.FORBIDDEN, "未登录或登录已失效");
        }
        boolean superAdmin = isAdmin(auth);
        boolean tenantAdmin = "TENANT_ADMIN".equalsIgnoreCase(auth.getTenantRole());

        if (superAdmin) {
            List<UserModel> users = userMapper.findAll(page, pageSize);
            Long total = userMapper.countAll();
            for (UserModel u : users) {
                enrichTenantBinding(u);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("list", users);
            data.put("total", total);
            return Result.success(data);
        }
        if (tenantAdmin && auth.getTenantId() != null) {
            List<com.ops.common.model.TenantUserModel> members = tenantMapper.listMembers(auth.getTenantId());
            List<UserModel> users = new ArrayList<>();
            for (com.ops.common.model.TenantUserModel m : members) {
                UserModel u = userMapper.findById(m.getUserId());
                if (u == null) continue;
                u.setPassword(null);
                u.setTenantId(m.getTenantId());
                u.setTenantRole(m.getRole());
                u.setTenantName(resolveTenantName(m.getTenantId()));
                users.add(u);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("list", users);
            data.put("total", (long) users.size());
            return Result.success(data);
        }
        // 普通用户只能看到自己这一行（用于修改个人资料）
        UserModel self = null;
        try {
            self = userMapper.findById(Long.parseLong(auth.getUserId()));
        } catch (NumberFormatException ignored) {
        }
        if (self != null) {
            enrichTenantBinding(self);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", self == null ? Collections.emptyList() : Arrays.asList(self));
        data.put("total", self == null ? 0L : 1L);
        return Result.success(data);
    }

    /**
     * GET /api/users/{id} - 用户详情
     */
    @GetMapping("/users/{id}")
    public Result<?> getUser(@PathVariable Long id, HttpServletRequest request) {
        AuthInterceptor.UserAuthContext auth = getCurrentUserAuth(request);
        if (auth == null) {
            return Result.error(ErrorCode.FORBIDDEN, "未登录或登录已失效");
        }
        // 普通用户只能查看自己的信息
        if (!isAdmin(auth) && !String.valueOf(id).equals(auth.getUserId())) {
            return Result.error(ErrorCode.FORBIDDEN, "只能查看自己的信息");
        }
        UserModel user = userMapper.findById(id);
        if (user == null) {
            return Result.error(ErrorCode.SERVER_ERROR, "用户不存在");
        }
        enrichTenantBinding(user); // 带租户绑定信息（transient）
        return Result.success(user);
    }

    /**
     * POST /api/users - 新增用户
     * SUPER_ADMIN 可指定 tenantId + tenantRole；TENANT_ADMIN 只能建本租户成员；其余无权限
     */
    @PostMapping("/users")
    public Result<?> createUser(@RequestBody UserModel user, HttpServletRequest request) {
        AuthInterceptor.UserAuthContext auth = getCurrentUserAuth(request);
        if (auth == null) {
            return Result.error(ErrorCode.FORBIDDEN, "未登录或登录已失效");
        }
        boolean superAdmin = isAdmin(auth);
        boolean tenantAdmin = "TENANT_ADMIN".equalsIgnoreCase(auth.getTenantRole());
        if (!superAdmin && !tenantAdmin) {
            return Result.error(ErrorCode.FORBIDDEN, "无权限：需要平台管理员或租户管理员");
        }
        if (userMapper.findByUsername(user.getUsername()) != null) {
            return Result.paramError("用户名已存在");
        }
        String pwdError = PasswordValidator.validate(user.getPassword());
        if (pwdError != null) {
            return Result.paramError(pwdError);
        }
        user.setPassword(hashPassword(user.getPassword()));
        // 默认角色为 operator；仅管理员可创建用户，且角色不可为空
        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            user.setRole("operator");
        }
        user.setStatus(1);
        user.setCreateTime(System.currentTimeMillis());
        user.setUpdateTime(System.currentTimeMillis());
        userMapper.insert(user);

        // 租户绑定：SUPER_ADMIN 可指定 tenantId/tenantRole（缺省绑默认租户）；TENANT_ADMIN 强制本租户
        Long bindTenantId = null;
        String bindRole = "OPERATOR";
        if (superAdmin) {
            bindTenantId = user.getTenantId();
            if (bindTenantId == null) {
                com.ops.common.model.TenantModel defaultTenant = tenantMapper.findDefault();
                bindTenantId = defaultTenant == null ? null : defaultTenant.getId();
            }
            if (user.getTenantRole() != null && !user.getTenantRole().trim().isEmpty()) {
                bindRole = user.getTenantRole().trim().toUpperCase(Locale.ROOT);
            }
        } else if (tenantAdmin) {
            bindTenantId = auth.getTenantId();
        }
        if (bindTenantId != null) {
            if (!TENANT_ROLES.contains(bindRole)) {
                bindRole = "OPERATOR";
            }
            com.ops.common.model.TenantUserModel member = new com.ops.common.model.TenantUserModel();
            member.setTenantId(bindTenantId);
            member.setUserId(user.getId());
            member.setRole(bindRole);
            member.setStatus(1);
            member.setCreateTime(user.getCreateTime());
            member.setUpdateTime(user.getUpdateTime());
            tenantMapper.insertMember(member);
        }
        return Result.success();
    }

    /**
     * PUT /api/users/{id} - 修改用户
     * SUPER_ADMIN 可改任意用户及租户绑定；TENANT_ADMIN 只能改本租户成员；
     * OPERATOR/VIEWER 只能改自己资料（防自我提权）
     */
    @PutMapping("/users/{id}")
    public Result<?> updateUser(@PathVariable Long id, @RequestBody UserModel user, HttpServletRequest request) {
        AuthInterceptor.UserAuthContext auth = getCurrentUserAuth(request);
        if (auth == null) {
            return Result.error(ErrorCode.FORBIDDEN, "未登录或登录已失效");
        }
        boolean superAdmin = isAdmin(auth);
        boolean tenantAdmin = "TENANT_ADMIN".equalsIgnoreCase(auth.getTenantRole());
        // 权限：SUPER_ADMIN 可改他人；TENANT_ADMIN 本租户成员；普通用户只能改自己
        if (!superAdmin && !tenantAdmin && !String.valueOf(id).equals(auth.getUserId())) {
            return Result.error(ErrorCode.FORBIDDEN, "只能修改自己的信息");
        }
        UserModel existing = userMapper.findById(id);
        if (existing == null) {
            return Result.error(ErrorCode.SERVER_ERROR, "用户不存在");
        }
        // TENANT_ADMIN 修改他人时，须确认目标在本租户
        if (tenantAdmin && !superAdmin && !String.valueOf(id).equals(auth.getUserId())
                && auth.getTenantId() != null
                && tenantMapper.findMember(auth.getTenantId(), id) == null) {
            return Result.error(ErrorCode.FORBIDDEN, "只能管理本租户成员");
        }
        user.setId(id);
        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            user.setUsername(existing.getUsername());
        }
        // 普通用户禁止修改角色/状态（防自我提权）；管理员可改
        if (!superAdmin && !tenantAdmin) {
            user.setRole(existing.getRole());
            user.setStatus(existing.getStatus());
        } else {
            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole(existing.getRole());
            }
            if (user.getStatus() == null) {
                user.setStatus(existing.getStatus());
            }
        }
        String newPassword = user.getPassword();
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            String pwdError = PasswordValidator.validate(newPassword);
            if (pwdError != null) {
                return Result.paramError(pwdError);
            }
            user.setPassword(hashPassword(newPassword));
        } else {
            user.setPassword(existing.getPassword());
        }
        user.setUpdateTime(System.currentTimeMillis());
        userMapper.update(user);

        // SUPER_ADMIN 可调整目标用户的租户绑定（tenantId/tenantRole）
        // tenantId 缺省时取用户现有主租户，支持仅改 tenantRole
        Long bindTenantId = user.getTenantId();
        if (superAdmin) {
            if (bindTenantId == null && user.getTenantRole() != null && !user.getTenantRole().trim().isEmpty()) {
                com.ops.common.model.TenantUserModel cur = tenantMapper.findFirstActiveMember(id);
                if (cur != null) bindTenantId = cur.getTenantId();
            }
            if (bindTenantId != null) {
                String bindRole = user.getTenantRole() == null || user.getTenantRole().trim().isEmpty()
                        ? "OPERATOR" : user.getTenantRole().trim().toUpperCase(Locale.ROOT);
                if (!TENANT_ROLES.contains(bindRole)) bindRole = "OPERATOR";
                com.ops.common.model.TenantUserModel member = tenantMapper.findMember(bindTenantId, id);
                long now = System.currentTimeMillis();
                if (member == null) {
                    member = new com.ops.common.model.TenantUserModel();
                    member.setTenantId(bindTenantId);
                    member.setUserId(id);
                    member.setRole(bindRole);
                    member.setStatus(1);
                    member.setCreateTime(now);
                    member.setUpdateTime(now);
                    tenantMapper.insertMember(member);
                } else {
                    member.setRole(bindRole);
                    member.setUpdateTime(now);
                    tenantMapper.updateMember(member);
                }
            }
        }
        return Result.success();
    }

    /**
     * DELETE /api/users/{id} - 删除用户
     * SUPER_ADMIN 全权限；TENANT_ADMIN 只能删本租户成员；平台 admin 账号受保护
     */
    @DeleteMapping("/users/{id}")
    public Result<?> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        AuthInterceptor.UserAuthContext auth = getCurrentUserAuth(request);
        if (auth == null) {
            return Result.error(ErrorCode.FORBIDDEN, "未登录或登录已失效");
        }
        boolean superAdmin = isAdmin(auth);
        boolean tenantAdmin = "TENANT_ADMIN".equalsIgnoreCase(auth.getTenantRole());
        if (!superAdmin && !tenantAdmin) {
            return Result.error(ErrorCode.FORBIDDEN, "无权限：需要平台管理员或租户管理员");
        }
        UserModel target = userMapper.findById(id);
        if (target == null) {
            return Result.error(ErrorCode.SERVER_ERROR, "用户不存在");
        }
        // 保护平台 admin 账号不被误删
        if ("admin".equalsIgnoreCase(target.getUsername())) {
            return Result.paramError("平台管理员账号不可删除");
        }
        // TENANT_ADMIN 只能删本租户成员
        if (tenantAdmin && !superAdmin && auth.getTenantId() != null
                && tenantMapper.findMember(auth.getTenantId(), id) == null) {
            return Result.error(ErrorCode.FORBIDDEN, "只能删除本租户成员");
        }
        userMapper.deleteById(id);
        tenantMapper.deleteMembersByUser(id); // 清理所有租户成员关系
        return Result.success();
    }

    /**
     * GET /api/operations - 操作审计日志
     */
    @GetMapping("/operations")
    public Result<?> getOperations(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<OperationLogModel> logs = operationLogMapper.findByModule(module, userId, page, pageSize);
        Long total = operationLogMapper.countByModule(module, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("list", logs);
        data.put("total", total);
        return Result.success(data);
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }
}
