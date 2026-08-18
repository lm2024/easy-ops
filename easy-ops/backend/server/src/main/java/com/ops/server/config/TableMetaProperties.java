package com.ops.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 表元数据配置属性（对应 application.yml 的 easyops.data.table-meta）
 *
 * 这是「表分类 / 识别 / 清空策略」的单一事实来源：
 *   - categories：分类定义（key -> label/icon），前端分组渲染用
 *   - tables：每张表的 label/category/type/source
 *
 * type 取值：
 *   BASE      基础数据表（用户/节点/项目/租户/配置...），禁止清空
 *   CONFIG    配置/状态表（探针/锁/策略...），禁止清空
 *   FLOW      流水表（日志/统计/记录...），可一键清空
 *   AGENT_SYNC agent 同步上报表（快照/上报数据...），可一键清空
 *
 * source 可选：标记数据来源（agent/nginx/script/kb...），用于识别 agent 同步表
 */
@Component
@ConfigurationProperties(prefix = "easyops.data.table-meta")
public class TableMetaProperties {

    /** 分类定义：key -> {label, icon} */
    private Map<String, CategoryDef> categories = new LinkedHashMap<>();

    /** 表定义：表名(小写) -> {label, category, type, source} */
    private Map<String, TableDef> tables = new LinkedHashMap<>();

    public Map<String, CategoryDef> getCategories() {
        return categories;
    }

    public void setCategories(Map<String, CategoryDef> categories) {
        this.categories = categories;
    }

    public Map<String, TableDef> getTables() {
        return tables;
    }

    public void setTables(Map<String, TableDef> tables) {
        this.tables = tables;
    }

    public static class CategoryDef {
        private String label;
        private String icon;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }
    }

    public static class TableDef {
        private String label;
        private String category;
        private String type;
        private String source;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }
}
