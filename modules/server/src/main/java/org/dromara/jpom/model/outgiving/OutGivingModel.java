/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.model.outgiving;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.comparator.CompareUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.db.TableName;
import org.dromara.jpom.model.BaseEnum;
import org.dromara.jpom.model.BaseGroupModel;
import org.dromara.jpom.model.node.ProjectInfoCacheModel;
import org.dromara.jpom.util.FileUtils;
import org.dromara.jpom.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分发实体
 *
 * @author bwcx_jzy
 * @since 2019/4/21
 */
@TableName(value = "OUT_GIVING",
    nameKey = "i18n.node_distribution.ae68")
@Data
@EqualsAndHashCode(callSuper = true)
public class OutGivingModel extends BaseGroupModel {

    /**
     * @param group 分组
     * @see ProjectInfoCacheModel#setGroup(String)
     */
    @Override
    public void setGroup(String group) {
        super.setGroup(group);
    }

    /**
     * 名称
     */
    private String name;
    /**
     * 分发间隔时间
     */
    private Integer intervalTime;
    /**
     * 节点下的项目列表
     */
    private String outGivingNodeProjectList;
    /**
     * 分发后的操作
     */
    private Integer afterOpt;
    /**
     * 是否清空旧包发布
     */
    private Boolean clearOld;
    /**
     * 是否为单独创建的分发项目
     */
    private Boolean outGivingProject;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 二级目录
     */
    private String secondaryDirectory;
    /**
     * 保存项目文件前先关闭
     */
    private Boolean uploadCloseFirst;
    /**
     * 状态消息
     */
    private String statusMsg;

    /**
     * 构建发布状态通知
     */
    private String webhook;
    /**
     * 分发方式
     * upload: "手动上传",
     * download: "远程下载",
     * "build-trigger": "构建触发",
     * "use-build": "构建产物",
     * "file-storage":"文件中心"
     * "static-file-storage“ 静态文件
     */
    private String mode;
    private String modeData;

    public boolean clearOld() {
        return clearOld != null && clearOld;
    }

    public boolean outGivingProject() {
        return outGivingProject != null && outGivingProject;
    }

    public void setSecondaryDirectory(String secondaryDirectory) {
        this.secondaryDirectory = Opt.ofBlankAble(secondaryDirectory).map(s -> {
            FileUtils.checkSlip(s, e -> new IllegalArgumentException(I18nMessageUtil.get("i18n.second_level_directory_cannot_skip_levels.c9fb") + e.getMessage()));
            return s;
        }).orElse(StrUtil.EMPTY);
    }

    public List<OutGivingNodeProject> outGivingNodeProjectList() {
        return outGivingNodeProjectList(StrUtil.EMPTY);
    }

    public List<OutGivingNodeProject> outGivingNodeProjectList(String select) {
        List<OutGivingNodeProject> outGivingNodeProjects = StringUtil.jsonConvertArray(outGivingNodeProjectList, OutGivingNodeProject.class);
        if (outGivingNodeProjects != null) {
            // 排序
            for (int i = 0; i < outGivingNodeProjects.size(); i++) {
                OutGivingNodeProject outGivingNodeProject = outGivingNodeProjects.get(i);
                if (outGivingNodeProject.getSortValue() != null) {
                    outGivingNodeProject.setSortValue(ObjectUtil.defaultIfNull(outGivingNodeProject.getSortValue(), i));
                }
            }
            List<String> list = StrUtil.splitTrim(select, StrUtil.COMMA);
            outGivingNodeProjects = outGivingNodeProjects.stream()
                .filter(nodeProject -> {
                    if (CollUtil.isEmpty(list)) {
                        return true;
                    }
                    return list.stream()
                        .anyMatch(s -> StrUtil.equals(s, StrUtil.format("{}@{}", nodeProject.getProjectId(), nodeProject.getNodeId())
                        ));
                })
                .sorted((o1, o2) -> CompareUtil.compare(o1.getSortValue(), o2.getSortValue()))
                .collect(Collectors.toList());
        }
        return outGivingNodeProjects;
    }

    public void outGivingNodeProjectList(List<OutGivingNodeProject> outGivingNodeProjectList) {
        if (outGivingNodeProjectList == null) {
            this.outGivingNodeProjectList = null;
        } else {
            this.outGivingNodeProjectList = JSON.toJSONString(outGivingNodeProjectList);
        }
    }

    /**
     * 判断是否包含某个项目id
     *
     * @param projectId 项目id
     * @return true 包含
     */
    public boolean checkContains(String nodeId, String projectId) {
        return getNodeProject(nodeId, projectId) != null;
    }

    /**
     * 获取节点的项目信息
     *
     * @param nodeId    节点
     * @param projectId 项目
     * @return outGivingNodeProject
     */
    public OutGivingNodeProject getNodeProject(String nodeId, String projectId) {
        List<OutGivingNodeProject> thisPs = outGivingNodeProjectList();
        return getNodeProject(thisPs, nodeId, projectId);
    }

    /**
     * 从指定数组中获取对应信息
     *
     * @param outGivingNodeProjects 节点项目列表
     * @param nodeId                节点id
     * @param projectId             项目id
     * @return 实体
     */
    public static OutGivingNodeProject getNodeProject(List<OutGivingNodeProject> outGivingNodeProjects, String nodeId, String projectId) {
        if (outGivingNodeProjects == null) {
            return null;
        }
        for (OutGivingNodeProject outGivingNodeProject1 : outGivingNodeProjects) {
            if (StrUtil.equalsIgnoreCase(outGivingNodeProject1.getProjectId(), projectId) && StrUtil.equalsIgnoreCase(outGivingNodeProject1.getNodeId(), nodeId)) {
                return outGivingNodeProject1;
            }
        }
        return null;
    }

    /**
     * 获取已经删除的节点项目
     *
     * @param newsProject 要比较的分发项目
     * @return 已经删除过的
     */
    public List<OutGivingNodeProject> getDelete(List<OutGivingNodeProject> newsProject) {
        List<OutGivingNodeProject> old = outGivingNodeProjectList();
        if (old == null || old.isEmpty()) {
            return null;
        }
        List<OutGivingNodeProject> delete = new ArrayList<>();
        old.forEach(outGivingNodeProject -> {
            if (getNodeProject(newsProject, outGivingNodeProject.getNodeId(), outGivingNodeProject.getProjectId()) != null) {
                return;
            }
            delete.add(outGivingNodeProject);
        });
        return delete;
    }

    /**
     * 状态
     */
    @Getter
    public enum Status implements BaseEnum {
        /**
         *
         */
        NO(0, "未分发"),
        ING(1, "分发中"),
        DONE(2, "分发结束"),
        CANCEL(3, "已取消"),
        FAIL(4, "分发失败"),
        ;
        private final int code;
        private final String desc;

        Status(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }
}
