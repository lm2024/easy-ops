package com.ops.server.controller;

import com.ops.common.constant.ErrorCode;
import com.ops.common.model.TenantModel;
import com.ops.common.model.TenantUserModel;
import com.ops.common.response.Result;
import com.ops.server.interceptor.AuthInterceptor;
import com.ops.server.mapper.TenantMapper;
import com.ops.server.mapper.UserMapper;
import com.ops.server.util.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 租户管理（多租户能力）
 *
 * 权限模型：
 *  - SUPER_ADMIN（sys_user.role=admin）：管理所有租户/成员，可切换租户视角
 *  - TENANT_ADMIN：管理本租户成员
 *  - OPERATOR / VIEWER：无租户管理权限
 *
 * 端点均需登录（AuthInterceptor 校验），租户列表按角色收口。
 */
@RestController
@RequestMapping("/tenants")
public class TenantController {

    private static final Logger log = LoggerFactory.getLogger(TenantController.class);

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private SecurityContext securityContext;

    private static final Set<String> VALID_ROLES = new HashSet<>(Arrays.asList(
            "SUPER_ADMIN", "TENANT_ADMIN", "OPERATOR", "VIEWER"));

    /**
     * GET /api/tenants - 租户列表（含统计）
     * SUPER_ADMIN 看全部；TENANT_ADMIN 只看本人租户；其余 403
     */
    @GetMapping
    public Result<?> listTenants() {
        if (securityContext.isSuperAdmin()) {
            List<TenantModel> tenants = tenantMapper.findAll(null);
            List<Map<String, Object>> list = new ArrayList<>();
            for (TenantModel t : tenants) {
                list.add(withStats(t));
            }
            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("total", list.size());
            return Result.success(data);
        }
        Long tenantId = securityContext.getCurrentTenantId();
        if (tenantId != null && securityContext.isTenantAdmin()) {
            TenantModel tenant = tenantMapper.findById(tenantId);
            if (tenant != null) {
                List<Map<String, Object>> list = new ArrayList<>();
                list.add(withStats(tenant));
                Map<String, Object> data = new HashMap<>();
                data.put("list", list);
                data.put("total", 1);
                return Result.success(data);
            }
        }
        return Result.error(ErrorCode.FORBIDDEN, "无权限访问租户管理");
    }

    /**
     * GET /api/tenants/{id} - 租户详情
     */
    @GetMapping("/{id}")
    public Result<?> getTenant(@PathVariable Long id) {
        TenantModel tenant = requireTenantAccess(id);
        return Result.success(withStats(tenant));
    }

    /**
     * POST /api/tenants - 创建租户（仅 SUPER_ADMIN）
     */
    @PostMapping
    public Result<?> createTenant(@RequestBody TenantModel tenant) {
        if (!securityContext.isSuperAdmin()) {
            return Result.error(ErrorCode.FORBIDDEN, "仅平台管理员可创建租户");
        }
        if (tenant.getCode() == null || tenant.getCode().trim().isEmpty()
                || tenant.getName() == null || tenant.getName().trim().isEmpty()) {
            return Result.paramError("租户 code 和名称不能为空");
        }
        String code = tenant.getCode().trim();
        if (tenantMapper.findByCode(code) != null) {
            return Result.paramError("租户 code 已存在");
        }
        TenantModel newTenant = new TenantModel();
        newTenant.setCode(code);
        newTenant.setName(tenant.getName().trim());
        newTenant.setStatus(tenant.getStatus() == null ? 1 : tenant.getStatus());
        long now = System.currentTimeMillis();
        newTenant.setCreateTime(now);
        newTenant.setUpdateTime(now);
        tenantMapper.insert(newTenant);
        log.info("[Tenant] 创建租户 id={} code={} name={} by superAdmin", newTenant.getId(), code, newTenant.getName());
        return Result.success(newTenant);
    }

    /**
     * PUT /api/tenants/{id} - 编辑租户（改名/启停，仅 SUPER_ADMIN）
     */
    @PutMapping("/{id}")
    public Result<?> updateTenant(@PathVariable Long id, @RequestBody TenantModel tenant) {
        if (!securityContext.isSuperAdmin()) {
            return Result.error(ErrorCode.FORBIDDEN, "仅平台管理员可修改租户");
        }
        TenantModel existing = tenantMapper.findById(id);
        if (existing == null) {
            return Result.error(ErrorCode.PARAM_ERROR, "租户不存在");
        }
        if (tenant.getName() != null && !tenant.getName().trim().isEmpty()) {
            existing.setName(tenant.getName().trim());
        }
        if (tenant.getStatus() != null) {
            existing.setStatus(tenant.getStatus());
        }
        existing.setUpdateTime(System.currentTimeMillis());
        tenantMapper.update(existing);
        return Result.success();
    }

    /**
     * DELETE /api/tenants/{id} - 删除租户（仅 SUPER_ADMIN；default 及有资源的租户禁删）
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteTenant(@PathVariable Long id) {
        if (!securityContext.isSuperAdmin()) {
            return Result.error(ErrorCode.FORBIDDEN, "仅平台管理员可删除租户");
        }
        TenantModel existing = tenantMapper.findById(id);
        if (existing == null) {
            return Result.error(ErrorCode.PARAM_ERROR, "租户不存在");
        }
        if ("default".equals(existing.getCode())) {
            return Result.paramError("默认租户不可删除");
        }
        if (tenantMapper.countNodes(id) > 0 || tenantMapper.countProjects(id) > 0) {
            return Result.paramError("租户下仍有节点或项目，无法删除（可先停用）");
        }
        tenantMapper.deleteMembersByTenant(id); // 清理成员
        tenantMapper.deleteById(id);
        return Result.success();
    }

    /**
     * GET /api/tenants/{id}/members - 租户成员列表
     */
    @GetMapping("/{id}/members")
    public Result<?> listMembers(@PathVariable Long id) {
        requireTenantAccess(id);
        return Result.success(tenantMapper.listMembers(id));
    }

    /**
     * POST /api/tenants/{id}/members - 添加成员（body: { userId, role }）
     */
    @PostMapping("/{id}/members")
    public Result<?> addMember(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireTenantManageAccess(id);
        Long userId = body.get("userId") instanceof Number ? ((Number) body.get("userId")).longValue() : null;
        if (userId == null) {
            return Result.paramError("缺少 userId");
        }
        if (userMapper.findById(userId) == null) {
            return Result.paramError("用户不存在");
        }
        if (tenantMapper.findMember(id, userId) != null) {
            return Result.paramError("该用户已是租户成员");
        }
        String role = body.get("role") == null ? "OPERATOR" : String.valueOf(body.get("role")).toUpperCase(Locale.ROOT);
        if (!VALID_ROLES.contains(role)) {
            return Result.paramError("角色必须为 TENANT_ADMIN / OPERATOR / VIEWER");
        }
        long now = System.currentTimeMillis();
        TenantUserModel member = new TenantUserModel();
        member.setTenantId(id);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus(1);
        member.setCreateTime(now);
        member.setUpdateTime(now);
        tenantMapper.insertMember(member);
        return Result.success();
    }

    /**
     * PUT /api/tenants/{id}/members/{userId} - 修改成员角色/启停
     */
    @PutMapping("/{id}/members/{userId}")
    public Result<?> updateMember(@PathVariable Long id, @PathVariable Long userId,
                                  @RequestBody Map<String, Object> body) {
        requireTenantManageAccess(id);
        TenantUserModel existing = tenantMapper.findMember(id, userId);
        if (existing == null) {
            return Result.paramError("该用户不是租户成员");
        }
        if (body.get("role") != null) {
            String role = String.valueOf(body.get("role")).toUpperCase(Locale.ROOT);
            if (!VALID_ROLES.contains(role)) {
                return Result.paramError("角色必须为 TENANT_ADMIN / OPERATOR / VIEWER");
            }
            existing.setRole(role);
        }
        if (body.get("status") instanceof Number) {
            existing.setStatus(((Number) body.get("status")).intValue());
        }
        existing.setUpdateTime(System.currentTimeMillis());
        tenantMapper.updateMember(existing);
        return Result.success();
    }

    /**
     * DELETE /api/tenants/{id}/members/{userId} - 移除成员
     */
    @DeleteMapping("/{id}/members/{userId}")
    public Result<?> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        requireTenantManageAccess(id);
        tenantMapper.deleteMember(id, userId);
        return Result.success();
    }

    /**
     * POST /api/tenants/switch - 平台管理员切换当前生效租户（body: { tenantId }）
     * tenantId 为 0/null 时切回平台视图（全量）
     */
    @PostMapping("/switch")
    public Result<?> switchTenant(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!securityContext.isSuperAdmin()) {
            return Result.error(ErrorCode.FORBIDDEN, "仅平台管理员可切换租户");
        }
        Long tenantId = body.get("tenantId") instanceof Number ? ((Number) body.get("tenantId")).longValue() : null;
        String authHeader = request.getHeader("Authorization");
        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : authHeader;
        if (token == null) {
            return Result.error(ErrorCode.UNAUTHORIZED, "切换失败：登录状态无效");
        }
        if (!authInterceptor.switchTenant(token, tenantId)) {
            // switchTenant 内部已校验租户存在且启用，失败原因可能是：token无效 / 租户不存在 / 租户已禁用
            if (tenantId != null && tenantId > 0L) {
                TenantModel target = tenantMapper.findById(tenantId);
                if (target == null) {
                    return Result.paramError("目标租户不存在");
                }
                if (target.getStatus() == null || target.getStatus() != 1) {
                    return Result.paramError("目标租户已禁用，无法切换");
                }
            }
            return Result.error(ErrorCode.UNAUTHORIZED, "切换失败：登录状态无效");
        }
        if (tenantId == null || tenantId == 0L) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("tenantId", null);
            view.put("tenantName", "全部租户");
            view.put("tenantRole", null);
            return Result.success(view);
        }
        TenantModel tenant = tenantMapper.findById(tenantId);
        if (tenant == null) {
            return Result.paramError("租户不存在");
        }
        return Result.success(buildTenantView(tenant));
    }

    // ===================== 私有方法 =====================

    /** 校验租户可读权限：SUPER_ADMIN 任意；TENANT_ADMIN 本租户 */
    private TenantModel requireTenantAccess(Long tenantId) {
        TenantModel tenant = tenantMapper.findById(tenantId);
        if (tenant == null) {
            throw new com.ops.common.exception.BusinessException(ErrorCode.PARAM_ERROR, "租户不存在");
        }
        if (securityContext.isSuperAdmin()) {
            return tenant;
        }
        if (securityContext.isTenantAdmin()
                && tenantId.equals(securityContext.getCurrentTenantId())) {
            return tenant;
        }
        throw new com.ops.common.exception.BusinessException(ErrorCode.FORBIDDEN, "无权限访问该租户");
    }

    /** 校验租户可管理权限（成员 CRUD）：SUPER_ADMIN 任意；TENANT_ADMIN 本租户 */
    private void requireTenantManageAccess(Long tenantId) {
        if (securityContext.isSuperAdmin()) return;
        if (securityContext.isTenantAdmin() && tenantId.equals(securityContext.getCurrentTenantId())) return;
        throw new com.ops.common.exception.BusinessException(ErrorCode.FORBIDDEN, "无权限管理该租户");
    }

    /** 租户 + 统计（节点/项目/用户） */
    private Map<String, Object> withStats(TenantModel tenant) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tenant.getId());
        map.put("code", tenant.getCode());
        map.put("name", tenant.getName());
        map.put("status", tenant.getStatus());
        map.put("createTime", tenant.getCreateTime());
        map.put("updateTime", tenant.getUpdateTime());
        map.put("nodeCount", tenantMapper.countNodes(tenant.getId()));
        map.put("projectCount", tenantMapper.countProjects(tenant.getId()));
        map.put("memberCount", tenantMapper.countMembers(tenant.getId()));
        return map;
    }

    /** 切换成功后返回租户视角信息 */
    private Map<String, Object> buildTenantView(TenantModel tenant) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tenantId", tenant.getId());
        map.put("tenantName", tenant.getName());
        map.put("tenantRole", "VIEWER"); // 平台管理员切换后以租户视角查看，默认只读
        return map;
    }
}
