/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.outgiving;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.SystemClock;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.map.SafeConcurrentHashMap;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.keepbx.jpom.model.BaseIdModel;
import cn.keepbx.jpom.model.JsonMessage;
import cn.keepbx.jpom.plugins.IPlugin;
import com.alibaba.fastjson2.JSONObject;
import lombok.Builder;
import lombok.Lombok;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.common.Const;
import org.dromara.jpom.common.forward.NodeForward;
import org.dromara.jpom.common.forward.NodeUrl;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.common.i18n.I18nThreadUtil;
import org.dromara.jpom.model.AfterOpt;
import org.dromara.jpom.model.data.NodeModel;
import org.dromara.jpom.model.log.OutGivingLog;
import org.dromara.jpom.model.outgiving.OutGivingModel;
import org.dromara.jpom.model.outgiving.OutGivingNodeProject;
import org.dromara.jpom.model.user.UserModel;
import org.dromara.jpom.plugin.PluginFactory;
import org.dromara.jpom.service.outgiving.DbOutGivingLogService;
import org.dromara.jpom.service.outgiving.OutGivingServer;
import org.dromara.jpom.util.LogRecorder;
import org.dromara.jpom.util.StrictSyncFinisher;
import org.dromara.jpom.util.SyncFinisherUtil;
import org.dromara.jpom.webhook.DefaultWebhookPluginImpl;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * 分发线程
 *
 * @author bwcx_jzy
 * @since 2019/7/18
 **/
@Slf4j
@Builder
public class OutGivingRun {

    private static final Map<String, Map<String, String>> LOG_CACHE_MAP = new SafeConcurrentHashMap<>();

//      * @param id              分发id
//     * @param file            文件
//     * @param userModel       操作的用户
//     * @param stripComponents 剔除文件夹
//     * @param unzip           解压
//     * @param clearFile       是否清空文件

    private String id;
    private File file;
    private UserModel userModel;
    private boolean unzip;
    private int stripComponents;
    /**
     * 分发方式
     * upload: "手动上传",
     * download: "远程下载",
     * "build-trigger": "构建触发",
     * "use-build": "构建产物",
     */
    private String mode;
    private String modeData;
    /**
     * 是否删除发布文件
     */
    @Builder.Default
    private boolean doneDeleteFile = true;
    private String projectSecondaryDirectory;
    private LogRecorder logRecorder;

    /**
     * 取消分发
     *
     * @param id 分发id
     */
    public static void cancel(String id, UserModel userModel) {
        SyncFinisherUtil.cancel("outgiving:" + id);
        //
        Map<String, String> map = LOG_CACHE_MAP.remove(id);
        Optional.ofNullable(map).ifPresent(map1 -> {
            DbOutGivingLogService dbOutGivingLogService = SpringUtil.getBean(DbOutGivingLogService.class);
            for (String logId : map1.values()) {
                OutGivingLog outGivingLog = new OutGivingLog();
                outGivingLog.setId(logId);
                outGivingLog.setStatus(OutGivingNodeProject.Status.ArtificialCancel.getCode());
                outGivingLog.setResult(I18nMessageUtil.get("i18n.manual_cancel_distribution.7bf6"));
                dbOutGivingLogService.updateById(outGivingLog);
            }
            if (!map1.isEmpty()) {
                // 更新分发数据
                updateStatus(id, OutGivingModel.Status.CANCEL, null, userModel);
            }
        });

    }


    public static String getLogId(String outId, OutGivingNodeProject nodeProject) {
        Map<String, String> map = LOG_CACHE_MAP.get(outId);
        Assert.notNull(map, I18nMessageUtil.get("i18n.current_distribution_data_lost.f9f8"));
        String dataId = StrUtil.format("{}_{}", nodeProject.getNodeId(), nodeProject.getProjectId());
        String logId = map.get(dataId);
        Assert.hasText(logId, I18nMessageUtil.get("i18n.current_distribution_data_lost_record_id_not_exist.ca07"));
        return logId;
    }

    private void removeLogId(String outId, OutGivingNodeProject nodeProject) {
        Map<String, String> map = LOG_CACHE_MAP.get(outId);
        Assert.notNull(map, I18nMessageUtil.get("i18n.current_distribution_data_lost.f9f8"));
        String dataId = StrUtil.format("{}_{}", nodeProject.getNodeId(), nodeProject.getProjectId());
        map.remove(dataId);
    }

    /**
     * 标记系统取消
     *
     * @param cancelList  需要取消的 list
     * @param outGivingId 分发id
     */
    private static void systemCancel(String outGivingId, List<OutGivingNodeProject> cancelList) {
        if (CollUtil.isEmpty(cancelList)) {
            return;
        }
        DbOutGivingLogService dbOutGivingLogService = SpringUtil.getBean(DbOutGivingLogService.class);
        for (OutGivingNodeProject outGivingNodeProject : cancelList) {
            String logId = OutGivingRun.getLogId(outGivingId, outGivingNodeProject);
            OutGivingLog outGivingLog = new OutGivingLog();
            outGivingLog.setId(logId);
            outGivingLog.setStatus(OutGivingNodeProject.Status.Cancel.getCode());
            outGivingLog.setResult(I18nMessageUtil.get("i18n.previous_node_distribution_failure.d556"));
            dbOutGivingLogService.updateById(outGivingLog);
        }
    }


    /**
     * 开始异步执行分发任务
     *
     * @param select 只发布指定项目
     */
    public Future<OutGivingModel.Status> startRun(String select) {
        OutGivingServer outGivingServer = SpringUtil.getBean(OutGivingServer.class);
        OutGivingModel item = outGivingServer.getByKey(id);
        Objects.requireNonNull(item, I18nMessageUtil.get("i18n.no_distribution_exists.4425"));
        // 更新二级目录
        Opt.ofBlankAble(this.projectSecondaryDirectory).ifPresent(item::setSecondaryDirectory);
        //
        AfterOpt afterOpt = ObjectUtil.defaultIfNull(EnumUtil.likeValueOf(AfterOpt.class, item.getAfterOpt()), AfterOpt.No);
        StrictSyncFinisher syncFinisher;
        //
        List<OutGivingNodeProject> outGivingNodeProjects = item.outGivingNodeProjectList(select);
        Assert.notEmpty(outGivingNodeProjects, I18nMessageUtil.get("i18n.no_distribution_project.d4d1"));
        int projectSize = outGivingNodeProjects.size();
        final List<OutGivingNodeProject.Status> statusList = new ArrayList<>(projectSize);
        // 开启线程
        if (afterOpt == AfterOpt.Order_Restart || afterOpt == AfterOpt.Order_Must_Restart) {
            syncFinisher = SyncFinisherUtil.create("outgiving:" + id, 1);
            //new StrictSyncFinisher(1, 1);
            syncFinisher.addWorker(() -> {
                try {
                    // 截取睡眠时间
                    int sleepTime = ObjectUtil.defaultIfNull(item.getIntervalTime(), 10);
                    //
                    int nowIndex;
                    for (nowIndex = 0; nowIndex < outGivingNodeProjects.size(); nowIndex++) {
                        final OutGivingNodeProject outGivingNodeProject = outGivingNodeProjects.get(nowIndex);
                        final OutGivingItemRun outGivingRun = new OutGivingItemRun(item, outGivingNodeProject, file, unzip, sleepTime);
                        outGivingRun.setStripComponents(stripComponents);
                        OutGivingNodeProject.Status status = outGivingRun.call();
                        if (status != OutGivingNodeProject.Status.Ok) {
                            if (afterOpt == AfterOpt.Order_Must_Restart) {
                                // 完整重启，不再继续剩余的节点项目
                                break;
                            }
                        }
                        statusList.add(status);
                        // 删除标记 log
                        removeLogId(id, outGivingNodeProject);
                        // 休眠x秒 等待之前项目正常启动
                        ThreadUtil.sleep(sleepTime, TimeUnit.SECONDS);
                    }
                    // 取消后面的分发
                    List<OutGivingNodeProject> cancelList = CollUtil.sub(outGivingNodeProjects, nowIndex + 1, outGivingNodeProjects.size());
                    systemCancel(id, cancelList);
                } catch (Exception e) {
                    log.error(I18nMessageUtil.get("i18n.distribute_exception_with_detail.28fe"), id, e);
                }
            });
        } else if (afterOpt == AfterOpt.Restart || afterOpt == AfterOpt.No) {
            syncFinisher = SyncFinisherUtil.create("outgiving:" + id, projectSize);

            for (final OutGivingNodeProject outGivingNodeProject : outGivingNodeProjects) {
                final OutGivingItemRun outGivingItemRun = new OutGivingItemRun(item, outGivingNodeProject, file, unzip, null);
                outGivingItemRun.setStripComponents(stripComponents);
                syncFinisher.addWorker(() -> {
                    try {
                        statusList.add(outGivingItemRun.call());
                        // 删除标记 log
                        removeLogId(id, outGivingNodeProject);
                    } catch (Exception e) {
                        log.error(I18nMessageUtil.get("i18n.distribute_exception.da82"), e);
                    }
                });
            }
        } else {
            //
            throw new IllegalArgumentException("Not implemented " + afterOpt.getDesc());
        }
        String userId = Optional.ofNullable(userModel).map(BaseIdModel::getId).orElse(Const.SYSTEM_ID);
        // 更新维准备中
        allPrepare(userId, item, outGivingNodeProjects);
        // 异步执行
        Callable<OutGivingModel.Status> callable = createRunnable(syncFinisher, statusList, projectSize);
        return I18nThreadUtil.execAsync(callable);
    }

    private Callable<OutGivingModel.Status> createRunnable(StrictSyncFinisher syncFinisher,
                                                           List<OutGivingNodeProject.Status> statusList, int projectSize) {
        return () -> {
            OutGivingModel.Status status = null;
            try {
                // 阻塞执行
                Optional.ofNullable(logRecorder).ifPresent(logRecorder -> logRecorder.system(I18nMessageUtil.get("i18n.start_distribution_with_count.cdc7"), projectSize));
                syncFinisher.start();
                // 更新分发状态
                String msg;
                if (statusList.size() != projectSize) {
                    //
                    status = OutGivingModel.Status.FAIL;
                    msg = StrUtil.format(I18nMessageUtil.get("i18n.completed_count_insufficient.02e9"), statusList.size(), projectSize);
                } else {
                    int successCount = statusList.stream().mapToInt(value -> value == OutGivingNodeProject.Status.Ok ? 1 : 0).sum();
                    if (successCount == projectSize) {
                        status = OutGivingModel.Status.DONE;
                        msg = I18nMessageUtil.get("i18n.distribute_success.c689") + successCount;
                    } else {
                        status = OutGivingModel.Status.FAIL;
                        msg = StrUtil.format(I18nMessageUtil.get("i18n.completed_and_successful_count_insufficient.92fa"), successCount, projectSize);
                    }
                }
                Optional.ofNullable(logRecorder).ifPresent(logRecorder -> logRecorder.system(msg));
                updateStatus(id, status, msg, userModel);
            } catch (Exception e) {
                log.error(I18nMessageUtil.get("i18n.distribute_thread_exception.9725"), e);
                updateStatus(id, OutGivingModel.Status.FAIL, e.getMessage(), userModel);
            } finally {
                if (doneDeleteFile) {
                    // 删除分发的文件
                    FileUtil.del(file);
                }
                //
                SyncFinisherUtil.close("outgiving:" + id);
                LOG_CACHE_MAP.remove(id);
            }
            return status;
        };
    }

    /**
     * 将所有数据更新维准备中
     *
     * @param outGivingModel        分发
     * @param outGivingNodeProjects 要分发的项目信息
     * @param userId                用户id
     */
    private void allPrepare(String userId, OutGivingModel outGivingModel, List<OutGivingNodeProject> outGivingNodeProjects) {
        //
        String outGivingId = outGivingModel.getId();
        List<OutGivingLog> outGivingLogs = outGivingNodeProjects.stream()
            .map(outGivingNodeProject -> {
                OutGivingLog outGivingLog = new OutGivingLog();
                outGivingLog.setOutGivingId(outGivingId);
                outGivingLog.setWorkspaceId(outGivingModel.getWorkspaceId());
                outGivingLog.setNodeId(outGivingNodeProject.getNodeId());
                outGivingLog.setProjectId(outGivingNodeProject.getProjectId());
                outGivingLog.setModifyUser(userId);
                outGivingLog.setStartTime(SystemClock.now());
                outGivingLog.setStatus(OutGivingNodeProject.Status.Prepare.getCode());
                outGivingLog.setMode(mode);
                // 限制最大长度
                outGivingLog.setModeData(StrUtil.maxLength(modeData, 400));
                return outGivingLog;
            })
            .collect(Collectors.toList());
        DbOutGivingLogService dbOutGivingLogService = SpringUtil.getBean(DbOutGivingLogService.class);
        dbOutGivingLogService.insert(outGivingLogs);
        //
        Map<String, String> logIdMap = CollStreamUtil.toMap(outGivingLogs, outGivingLog -> StrUtil.format("{}_{}", outGivingLog.getNodeId(), outGivingLog.getProjectId()), BaseIdModel::getId);

        OutGivingRun.LOG_CACHE_MAP.put(outGivingId, logIdMap);

        // 更新分发数据
        updateStatus(outGivingId, OutGivingModel.Status.ING, null, userModel);
    }

    /**
     * 更新方法状态
     *
     * @param outGivingId 分发id
     * @param status      状态
     * @param msg         消息
     * @param userModel   操作人
     */
    private static void updateStatus(String outGivingId, OutGivingModel.Status status, String msg, UserModel userModel) {
        OutGivingServer outGivingServer = SpringUtil.getBean(OutGivingServer.class);
        OutGivingModel outGivingModel1 = new OutGivingModel();
        outGivingModel1.setId(outGivingId);
        outGivingModel1.setStatus(status.getCode());
        outGivingModel1.setStatusMsg(msg);
        outGivingServer.updateById(outGivingModel1);
        //
        OutGivingModel outGivingModel = outGivingServer.getByKey(outGivingId);
        Opt.ofNullable(outGivingModel)
            .map(outGivingModel2 ->
                Opt.ofBlankAble(outGivingModel2.getWebhook())
                    .orElse(null))
            .ifPresent(webhook ->
                I18nThreadUtil.execute(() -> {
                    // outGivingId、outGivingName、status、statusMsg、executeTime
                    Map<String, Object> map = new HashMap<>(10);
                    map.put("outGivingId", outGivingId);
                    map.put("outGivingName", outGivingModel.getName());
                    map.put("status", status.getCode());
                    map.put("statusMsg", msg);
                    // 操作人
                    String triggerUser = Optional.ofNullable(userModel).map(BaseIdModel::getId).orElse(UserModel.SYSTEM_ADMIN);
                    map.put("triggerUser", triggerUser);
                    map.put("executeTime", SystemClock.now());
                    try {
                        IPlugin plugin = PluginFactory.getPlugin("webhook");
                        map.put("JPOM_WEBHOOK_EVENT", DefaultWebhookPluginImpl.WebhookEvent.DISTRIBUTE);
                        plugin.execute(webhook, map);
                    } catch (Exception e) {
                        log.error(I18nMessageUtil.get("i18n.webhooks_invocation_error.9792"), e);
                    }
                }));
    }

    /**
     * 上传项目文件
     *
     * @param file       需要上传的文件
     * @param projectId  项目id
     * @param unzip      是否需要解压
     * @param afterOpt   是否需要重启
     * @param nodeModel  节点
     * @param clearOld   清空发布
     * @param levelName  文件夹层级
     * @param closeFirst 保存项目文件前先关闭项目
     * @return json
     */
    public static JsonMessage<String> fileUpload(File file, String levelName, String projectId,
                                                 boolean unzip,
                                                 AfterOpt afterOpt,
                                                 NodeModel nodeModel,
                                                 boolean clearOld,
                                                 Boolean closeFirst,
                                                 BiConsumer<Long, Long> streamProgress) {
        return fileUpload(file, levelName, projectId, unzip, afterOpt, nodeModel, clearOld, null, closeFirst, streamProgress);
    }

    /**
     * 上传项目文件
     *
     * @param file       需要上传的文件
     * @param projectId  项目id
     * @param unzip      是否需要解压
     * @param afterOpt   是否需要重启
     * @param nodeModel  节点
     * @param clearOld   清空发布
     * @param levelName  文件夹层级
     * @param sleepTime  休眠时间
     * @param closeFirst 保存项目文件前先关闭项目
     * @return json
     */
    public static JsonMessage<String> fileUpload(File file, String levelName, String projectId,
                                                 boolean unzip,
                                                 AfterOpt afterOpt,
                                                 NodeModel nodeModel,
                                                 boolean clearOld,
                                                 Integer sleepTime,
                                                 Boolean closeFirst,
                                                 BiConsumer<Long, Long> streamProgress) {
        return fileUpload(file, levelName, projectId, unzip, afterOpt, nodeModel, clearOld, sleepTime, closeFirst, 0, streamProgress);
    }

    /**
     * 上传项目文件
     *
     * @param file       需要上传的文件
     * @param projectId  项目id
     * @param unzip      是否需要解压
     * @param afterOpt   是否需要重启
     * @param nodeModel  节点
     * @param clearOld   清空发布
     * @param levelName  文件夹层级
     * @param sleepTime  休眠时间
     * @param closeFirst 保存项目文件前先关闭项目
     * @return json
     */
    public static JsonMessage<String> fileUpload(File file, String levelName, String projectId,
                                                 boolean unzip,
                                                 AfterOpt afterOpt,
                                                 NodeModel nodeModel,
                                                 boolean clearOld,
                                                 Integer sleepTime,
                                                 Boolean closeFirst, int stripComponents,
                                                 BiConsumer<Long, Long> streamProgress) {
        JSONObject data = new JSONObject();
        //  data.put("file", file);
        data.put("id", projectId);
        Opt.ofBlankAble(levelName).ifPresent(s -> data.put("levelName", s));
        Opt.ofNullable(sleepTime).ifPresent(integer -> data.put("sleepTime", integer));

        if (unzip) {
            // 解压
            data.put("type", "unzip");
            data.put("stripComponents", stripComponents);
        }
        if (clearOld) {
            // 清空
            data.put("clearType", "clear");
        }
        // 操作
        if (afterOpt != AfterOpt.No) {
            data.put("after", afterOpt.getCode());
        }
        data.put("closeFirst", closeFirst);
        try {
            return NodeForward.requestSharding(nodeModel, NodeUrl.Manage_File_Upload_Sharding, data, file,
                sliceData -> {
                    sliceData.putAll(data);
                    return NodeForward.request(nodeModel, NodeUrl.Manage_File_Sharding_Merge, sliceData);
                },
                streamProgress);
        } catch (IOException e) {
            throw Lombok.sneakyThrow(e);
        }

        //return NodeForward.request(nodeModel, NodeUrl.Manage_File_Upload, data);
    }
}
