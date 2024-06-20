/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.service.dblog;

import cn.hutool.core.bean.BeanPath;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.common.i18n.I18nThreadUtil;
import org.dromara.jpom.model.PageResultDto;
import org.dromara.jpom.model.data.MonitorModel;
import org.dromara.jpom.model.data.MonitorUserOptModel;
import org.dromara.jpom.model.data.WorkspaceModel;
import org.dromara.jpom.model.log.UserOperateLogV1;
import org.dromara.jpom.model.user.UserModel;
import org.dromara.jpom.monitor.NotifyUtil;
import org.dromara.jpom.permission.ClassFeature;
import org.dromara.jpom.permission.MethodFeature;
import org.dromara.jpom.service.h2db.BaseDbService;
import org.dromara.jpom.service.h2db.BaseWorkspaceService;
import org.dromara.jpom.service.monitor.MonitorUserOptService;
import org.dromara.jpom.service.system.WorkspaceService;
import org.dromara.jpom.service.user.UserService;
import org.dromara.jpom.system.init.OperateLogController;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 操作日志
 *
 * @author bwcx_jzy
 * @since 2019/7/20
 */
@Service
@Slf4j
public class DbUserOperateLogService extends BaseWorkspaceService<UserOperateLogV1> {

    private final MonitorUserOptService monitorUserOptService;
    private final UserService userService;
    private final WorkspaceService workspaceService;
    /**
     * 通用 bean 的名称字段 bean-path
     */
    private static final BeanPath[] NAME_BEAN_PATHS = new BeanPath[]{BeanPath.create("name"), BeanPath.create("title")};

    public DbUserOperateLogService(MonitorUserOptService monitorUserOptService,
                                   UserService userService,
                                   WorkspaceService workspaceService) {
        this.monitorUserOptService = monitorUserOptService;
        this.userService = userService;
        this.workspaceService = workspaceService;
    }

    /**
     * 查询指定用户的操作日志
     *
     * @param request 请求信息
     * @param userId  用户id
     * @return page
     */
    public PageResultDto<UserOperateLogV1> listPageByUserId(HttpServletRequest request, String userId) {
        Map<String, String> paramMap = ServletUtil.getParamMap(request);
        paramMap.put("userId", userId);
        return super.listPage(paramMap);
    }

    /**
     * 根据 数据ID 和 节点ID 查询相关数据名称
     *
     * @param classFeature     功能
     * @param cacheInfo        操作缓存
     * @param userOperateLogV1 操作日志
     * @return map
     */
    private Map<String, Object> buildDataMsg(ClassFeature classFeature, OperateLogController.CacheInfo cacheInfo, UserOperateLogV1 userOperateLogV1) {
        Map<String, Object> optDataNameMap = cacheInfo.getOptDataNameMap();
        if (optDataNameMap != null) {
            return optDataNameMap;
        }
        return this.buildDataMsg(classFeature, userOperateLogV1.getDataId(), userOperateLogV1.getNodeId());
    }

    /**
     * 根据 数据ID 和 节点ID 查询相关数据名称
     *
     * @param classFeature 功能
     * @param dataId       数据ID
     * @param nodeId       节点ID
     * @return map
     */
    public Map<String, Object> buildDataMsg(ClassFeature classFeature, String dataId, String nodeId) {
        if (classFeature == null) {
            return null;
        }
        Class<? extends BaseDbService<?>> dbService = classFeature.getDbService();
        if (dbService == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(I18nMessageUtil.get("i18n.data_id_label.81b6"), dataId);
        BaseDbService<?> baseDbCommonService = SpringUtil.getBean(dbService);
        Object data = baseDbCommonService.getData(nodeId, dataId);
        map.put(I18nMessageUtil.get("i18n.data_name_label.5a14"), this.tryGetBeanName(data));
        //
        map.put(I18nMessageUtil.get("i18n.node_id.c90a"), nodeId);
        ClassFeature parent = classFeature.getParent();
        if (parent == ClassFeature.NODE) {
            Class<? extends BaseDbService<?>> dbServiceParent = parent.getDbService();
            BaseDbService<?> baseDbCommonServiceParent = SpringUtil.getBean(dbServiceParent);
            Object dataParent = baseDbCommonServiceParent.getData(nodeId, dataId);
            map.put(I18nMessageUtil.get("i18n.node_name.b178"), this.tryGetBeanName(dataParent));
        }
        return map;
    }

    private Object tryGetBeanName(Object data) {
        for (BeanPath beanPath : NAME_BEAN_PATHS) {
            Object o = beanPath.get(data);
            if (o != null) {
                return o;
            }
        }
        return null;
    }

    private String buildContent(UserModel optUserItem, Map<String, Object> dataMap, WorkspaceModel workspaceModel, String optTypeMsg, UserOperateLogV1 userOperateLogV1) {
        Map<String, Object> map = new LinkedHashMap<>(10);
        map.put(I18nMessageUtil.get("i18n.operation_user.4c89"), optUserItem.getName());
        map.put(I18nMessageUtil.get("i18n.operation_status_code.8231"), userOperateLogV1.getOptStatus());
        map.put(I18nMessageUtil.get("i18n.operation_type.de9c"), optTypeMsg);
        if (workspaceModel != null) {
            map.put(I18nMessageUtil.get("i18n.associated_workspace.885b"), workspaceModel.getName());
        }
        map.put(I18nMessageUtil.get("i18n.operation_ip.cbd4"), userOperateLogV1.getIp());
        map.put(I18nMessageUtil.get("i18n.operation_time.7e95"), DateTime.now().toString());
        if (dataMap != null) {
            map.putAll(dataMap);
        }
        List<String> list = map.entrySet()
            .stream()
            .filter(entry -> entry.getValue() != null)
            .map(entry -> entry.getKey() + "：" + entry.getValue())
            .collect(Collectors.toList());
        //
        return CollUtil.join(list, StrUtil.LF);
    }

    /**
     * 判断当前操作是否需要报警
     *
     * @param userOperateLogV1 操作信息
     * @param cacheInfo        操作缓存相关
     * @return 解析后的相关数据
     */
    private Map<String, Object> checkMonitor(UserOperateLogV1 userOperateLogV1, OperateLogController.CacheInfo cacheInfo) {
        ClassFeature classFeature = EnumUtil.fromString(ClassFeature.class, userOperateLogV1.getClassFeature(), null);
        MethodFeature methodFeature = EnumUtil.fromString(MethodFeature.class, userOperateLogV1.getMethodFeature(), null);
        UserModel optUserItem = userService.getByKey(userOperateLogV1.getUserId());
        if (classFeature == null || methodFeature == null || optUserItem == null) {
            return null;
        }
        Map<String, Object> dataMap = this.buildDataMsg(classFeature, cacheInfo, userOperateLogV1);
        WorkspaceModel workspaceModel = workspaceService.getByKey(userOperateLogV1.getWorkspaceId());

        String optTypeMsg = StrUtil.format(" 【{}】->【{}】", I18nMessageUtil.get(classFeature.getName().get()), I18nMessageUtil.get(methodFeature.getName().get()));
        List<MonitorUserOptModel> monitorUserOptModels = monitorUserOptService.listByType(userOperateLogV1.getWorkspaceId(),
            classFeature,
            methodFeature,
            userOperateLogV1.getUserId());
        if (CollUtil.isEmpty(monitorUserOptModels)) {
            return dataMap;
        }
        String context = this.buildContent(optUserItem, dataMap, workspaceModel, optTypeMsg, userOperateLogV1);
        for (MonitorUserOptModel monitorUserOptModel : monitorUserOptModels) {
            List<String> notifyUser = monitorUserOptModel.notifyUser();
            if (CollUtil.isEmpty(notifyUser)) {
                continue;
            }
            for (String userId : notifyUser) {
                UserModel item = userService.getByKey(userId);
                if (item == null) {
                    continue;
                }
                // 邮箱
                String email = item.getEmail();
                if (StrUtil.isNotEmpty(email)) {
                    MonitorModel.Notify notify1 = new MonitorModel.Notify(MonitorModel.NotifyType.mail, email);
                    I18nThreadUtil.execute(() -> {
                        try {
                            NotifyUtil.send(notify1, I18nMessageUtil.get("i18n.user_operation_alarm.15b9"), context);
                        } catch (Exception e) {
                            log.error(I18nMessageUtil.get("i18n.send_alert_error.cd38"), e);
                        }
                    });

                }
                // dingding
                String dingDing = item.getDingDing();
                if (StrUtil.isNotEmpty(dingDing)) {
                    MonitorModel.Notify notify1 = new MonitorModel.Notify(MonitorModel.NotifyType.dingding, dingDing);
                    I18nThreadUtil.execute(() -> {
                        try {
                            NotifyUtil.send(notify1, I18nMessageUtil.get("i18n.user_operation_alarm.15b9"), context);
                        } catch (Exception e) {
                            log.error(I18nMessageUtil.get("i18n.send_alert_error.cd38"), e);
                        }
                    });
                }
                // 企业微信
                String workWx = item.getWorkWx();
                if (StrUtil.isNotEmpty(workWx)) {
                    MonitorModel.Notify notify1 = new MonitorModel.Notify(MonitorModel.NotifyType.workWx, workWx);
                    I18nThreadUtil.execute(() -> {
                        try {
                            NotifyUtil.send(notify1, I18nMessageUtil.get("i18n.user_operation_alarm.15b9"), context);
                        } catch (Exception e) {
                            log.error(I18nMessageUtil.get("i18n.send_alert_error.cd38"), e);
                        }
                    });
                }
            }
        }
        return dataMap;
    }

    /**
     * 插入操作日志
     *
     * @param userOperateLogV1 日志信息
     * @param cacheInfo        当前操作相关信息
     */
    public void insert(UserOperateLogV1 userOperateLogV1, OperateLogController.CacheInfo cacheInfo) {
        super.insert(userOperateLogV1);
        I18nThreadUtil.execute(() -> {
            // 更新用户名和工作空间名
            try {
                UserOperateLogV1 update = new UserOperateLogV1();
                update.setId(userOperateLogV1.getId());
                UserModel userModel = userService.getByKey(userOperateLogV1.getUserId());
                Optional.ofNullable(userModel).ifPresent(userModel1 -> update.setUsername(userModel1.getName()));
                WorkspaceModel workspaceModel = workspaceService.getByKey(userOperateLogV1.getWorkspaceId());
                Optional.ofNullable(workspaceModel).ifPresent(workspaceModel1 -> update.setWorkspaceName(workspaceModel1.getName()));
                this.updateById(update);
            } catch (Exception e) {
                log.error(I18nMessageUtil.get("i18n.update_operation_log_failed.d348"), e);
            }
            // 检查操作监控
            try {
                Map<String, Object> monitor = this.checkMonitor(userOperateLogV1, cacheInfo);
                if (monitor != null) {
                    String dataName = Optional.ofNullable(monitor.get(I18nMessageUtil.get("i18n.data_name_label.5a14"))).map(StrUtil::toStringOrNull).orElse(StrUtil.DASHED);
                    UserOperateLogV1 userOperateLogV11 = new UserOperateLogV1();
                    userOperateLogV11.setDataName(dataName);
                    userOperateLogV11.setId(userOperateLogV1.getId());
                    super.updateById(userOperateLogV11);
                }
            } catch (Exception e) {
                log.error(I18nMessageUtil.get("i18n.operation_monitoring_error.8036"), e);
            }
        });
    }

    @Override
    public PageResultDto<UserOperateLogV1> listPage(HttpServletRequest request) {
        // 验证工作空间权限
        Map<String, String> paramMap = ServletUtil.getParamMap(request);
        //String workspaceId = this.getCheckUserWorkspace(request);
        //paramMap.put("workspaceId:in", workspaceId + StrUtil.COMMA + StrUtil.EMPTY);
        return super.listPage(paramMap);
    }

    @Override
    public String getCheckUserWorkspace(HttpServletRequest request) {
        // 忽略检查
        return BaseWorkspaceService.getWorkspaceId(request);
        // String header = ServletUtil.getHeader(request, Const.WORKSPACE_ID_REQ_HEADER, CharsetUtil.CHARSET_UTF_8);
        // return ObjectUtil.defaultIfNull(header, StrUtil.EMPTY);
    }

    @Override
    protected void checkUserWorkspace(String workspaceId, UserModel userModel) {
        // 忽略检查
    }

    @Override
    protected String[] clearTimeColumns() {
        return new String[]{"optTime", "createTimeMillis"};
    }
}
