/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.model.data;

import cn.hutool.core.util.StrUtil;
import cn.keepbx.jpom.model.BaseJsonModel;
import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.dromara.jpom.db.TableName;
import org.dromara.jpom.model.BaseEnum;
import org.dromara.jpom.model.BaseWorkspaceModel;
import org.dromara.jpom.util.StringUtil;

import java.util.List;

/**
 * 监控管理实体
 *
 * @author Arno
 */
@EqualsAndHashCode(callSuper = true)
@TableName(value = "MONITOR_INFO",
    nameKey = "i18n.monitor_info.f299")
@Data
public class MonitorModel extends BaseWorkspaceModel {

    private String name;
    /**
     * 监控的项目
     */
    private String projects;
    /**
     * 报警联系人
     */
    private String notifyUser;
    /**
     * 异常后是否自动重启
     */
    private Boolean autoRestart;
    /**
     * 监控周期
     *
     * @see io.jpom.model.Cycle
     */
    @Deprecated
    private Integer cycle;
    /**
     * 监控定时周期
     */
    private String execCron;
    /**
     * 监控开启状态
     */
    private Boolean status;
    /**
     * 报警状态
     */
    private Boolean alarm;
    /**
     * webhook
     */
    private String webhook;

    public String getExecCron() {
        if (execCron == null) {
            // 兼容旧版本
            if (cycle != null) {
                return String.format("0 0/%s * * * ?", cycle);
            }
        }
        return execCron;
    }

    public boolean autoRestart() {
        return autoRestart != null && autoRestart;
    }

    /**
     * 开启状态
     *
     * @return true 启用
     */
    public boolean status(String autoExecCron) {
        return status != null && status && StrUtil.isNotEmpty(autoExecCron);
    }

    public List<NodeProject> projects() {
        return StringUtil.jsonConvertArray(projects, NodeProject.class);
    }


    public String getProjects() {
        List<NodeProject> projects = projects();
        return projects == null ? null : JSON.toJSONString(projects);
    }

    public void projects(List<NodeProject> projects) {
        if (projects == null) {
            this.projects = null;
        } else {
            this.projects = JSON.toJSONString(projects);
        }
    }

    public List<String> notifyUser() {
        return StringUtil.jsonConvertArray(notifyUser, String.class);
    }

    public String getNotifyUser() {
        List<String> object = notifyUser();
        return object == null ? null : JSON.toJSONString(object);
    }

    public void notifyUser(List<String> notifyUser) {
        if (notifyUser == null) {
            this.notifyUser = null;
        } else {
            this.notifyUser = JSON.toJSONString(notifyUser);
        }
    }

    public boolean checkNodeProject(String nodeId, String projectId) {
        List<NodeProject> projects = projects();
        if (projects == null) {
            return false;
        }
        for (NodeProject project : projects) {
            if (project.getNode().equals(nodeId)) {
                List<String> projects1 = project.getProjects();
                if (projects1 == null) {
                    return false;
                }
                for (String s : projects1) {
                    if (projectId.equals(s)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Getter
    public enum NotifyType implements BaseEnum {
        /**
         * 通知方式
         */
        dingding(0, "钉钉"),
        mail(1, "邮箱"),
        workWx(2, "企业微信"),
        webhook(3, "webhook"),
        ;

        private final int code;
        private final String desc;

        NotifyType(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

    /**
     * 通知
     */
    public static class Notify extends BaseJsonModel {
        private int style;
        private String value;

        public Notify() {
        }

        public Notify(NotifyType style, String value) {
            this.style = style.getCode();
            this.value = value;
        }

        public int getStyle() {
            return style;
        }

        public void setStyle(int style) {
            this.style = style;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class NodeProject extends BaseJsonModel {
        /**
         * 节点 ID
         */
        private String node;
        /**
         * 被监控的项目ID
         */
        private List<String> projects;

        public String getNode() {
            return node;
        }

        public void setNode(String node) {
            this.node = node;
        }

        public List<String> getProjects() {
            return projects;
        }

        public void setProjects(List<String> projects) {
            this.projects = projects;
        }
    }
}
