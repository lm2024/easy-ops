package com.ops.server.mapper;

import com.ops.common.model.UserModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM sys_user");
    }

    @Test
    @DisplayName("findById - 返回正确用户")
    void findById_returnsCorrectUser() {
        UserModel user = createUser("alice", "ADMIN", 1);
        userMapper.insert(user);

        UserModel found = userMapper.findById(user.getId());
        assertNotNull(found);
        assertEquals("alice", found.getUsername());
        assertEquals("ADMIN", found.getRole());
    }

    @Test
    @DisplayName("findByUsername - 按用户名查找")
    void findByUsername_returnsUser() {
        userMapper.insert(createUser("bob", "USER", 1));
        UserModel found = userMapper.findByUsername("bob");
        assertNotNull(found);
        assertEquals("bob", found.getUsername());
    }

    @Test
    @DisplayName("findByUsername - 不存在的用户返回null")
    void findByUsername_nonExisting_returnsNull() {
        assertNull(userMapper.findByUsername("nobody"));
    }

    @Test
    @DisplayName("countAll - 返回用户总数")
    void countAll_returnsTotal() {
        assertEquals(0L, userMapper.countAll());
        userMapper.insert(createUser("a", "USER", 1));
        userMapper.insert(createUser("b", "USER", 1));
        assertEquals(2L, userMapper.countAll());
    }

    @Test
    @DisplayName("insert - 自增ID并返回")
    void insert_generatesId() {
        UserModel user = createUser("newuser", "USER", 1);
        int rows = userMapper.insert(user);
        assertEquals(1, rows);
        assertNotNull(user.getId());
        assertTrue(user.getId() > 0);
    }

    @Test
    @DisplayName("update - 更新用户信息")
    void update_modifiesUser() {
        UserModel user = createUser("updater", "USER", 1);
        userMapper.insert(user);
        user.setUsername("updated");
        user.setRole("ADMIN");
        int rows = userMapper.update(user);
        assertEquals(1, rows);

        UserModel found = userMapper.findById(user.getId());
        assertEquals("updated", found.getUsername());
        assertEquals("ADMIN", found.getRole());
    }

    @Test
    @DisplayName("deleteById - 删除用户")
    void deleteById_removesUser() {
        UserModel user = createUser("deleteme", "USER", 1);
        userMapper.insert(user);
        assertEquals(1, userMapper.deleteById(user.getId()));
        assertNull(userMapper.findById(user.getId()));
    }

    @Test
    @DisplayName("getUsernameById - 返回用户名")
    void getUsernameById_returnsName() {
        UserModel user = createUser("nametest", "USER", 1);
        userMapper.insert(user);

        String name = userMapper.getUsernameById(user.getId());
        assertEquals("nametest", name);
    }

    private UserModel createUser(String username, String role, int status) {
        UserModel user = new UserModel();
        user.setUsername(username);
        user.setPassword("encrypted_" + username);
        user.setRole(role);
        user.setStatus(status);
        user.setCreateTime(System.currentTimeMillis());
        user.setUpdateTime(System.currentTimeMillis());
        return user;
    }
}
