package com.ops.common;

import com.ops.common.enums.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnumTest {

    @Test
    void nodeStatus() {
        assertEquals(0, NodeStatus.OFFLINE.getCode());
        assertEquals(1, NodeStatus.ONLINE.getCode());
        assertEquals("离线", NodeStatus.OFFLINE.getDesc());
        assertEquals("在线", NodeStatus.ONLINE.getDesc());
    }

    @Test
    void nodeStatus_fromCode() {
        assertEquals(NodeStatus.ONLINE, NodeStatus.fromCode(1));
        assertEquals(NodeStatus.OFFLINE, NodeStatus.fromCode(0));
        assertEquals(NodeStatus.OFFLINE, NodeStatus.fromCode(999));
    }

    @Test
    void deployStatus() {
        assertEquals(0, DeployStatus.PROCESSING.getCode());
        assertEquals(1, DeployStatus.SUCCESS.getCode());
        assertEquals(2, DeployStatus.FAILED.getCode());
        assertEquals(3, DeployStatus.ROLLBACK.getCode());
        assertEquals("进行中", DeployStatus.PROCESSING.getDesc());
        assertEquals("成功", DeployStatus.SUCCESS.getDesc());
        assertEquals("失败", DeployStatus.FAILED.getDesc());
        assertEquals("回滚", DeployStatus.ROLLBACK.getDesc());
    }

    @Test
    void fileType() {
        assertEquals("yml", FileType.YML.getExt());
        assertEquals("log", FileType.LOG.getExt());
        assertEquals("jar", FileType.JAR.getExt());
    }

    @Test
    void fileAction() {
        assertEquals("view", FileAction.VIEW.getCode());
        assertEquals("edit", FileAction.EDIT.getCode());
        assertEquals("download", FileAction.DOWNLOAD.getCode());
    }

    @Test
    void userRole() {
        assertEquals("admin", UserRole.ADMIN.getCode());
        assertEquals("operator", UserRole.OPERATOR.getCode());
    }
}
