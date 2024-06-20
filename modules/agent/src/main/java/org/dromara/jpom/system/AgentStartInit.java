/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.system;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateException;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.text.CharPool;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.keepbx.jpom.event.ISystemTask;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.JpomApplication;
import org.dromara.jpom.common.ILoadEvent;
import org.dromara.jpom.common.RemoteVersion;
import org.dromara.jpom.common.commander.ProjectCommander;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.configuration.AgentAuthorize;
import org.dromara.jpom.configuration.AgentConfig;
import org.dromara.jpom.configuration.ProjectLogConfig;
import org.dromara.jpom.cron.CronUtils;
import org.dromara.jpom.model.RunMode;
import org.dromara.jpom.model.data.NodeProjectInfoModel;
import org.dromara.jpom.script.BaseRunScript;
import org.dromara.jpom.service.manage.ProjectInfoService;
import org.dromara.jpom.socket.ConsoleCommandOp;
import org.dromara.jpom.util.CommandUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自动备份控制台日志，防止日志文件过大
 *
 * @author bwcx_jzy
 * @since 2019/3/17
 */
@Slf4j
@Configuration
public class AgentStartInit implements ILoadEvent, ISystemTask {

    private static final String ID = "auto_back_log";
    private final ProjectInfoService projectInfoService;
    private final AgentConfig agentConfig;
    private final AgentAuthorize agentAuthorize;
    private final JpomApplication jpomApplication;
    private final ProjectCommander projectCommander;
    private final ProjectLogConfig projectLogConfig;


    public AgentStartInit(ProjectInfoService projectInfoService,
                          AgentConfig agentConfig,
                          JpomApplication jpomApplication,
                          ProjectCommander projectCommander) {
        this.projectInfoService = projectInfoService;
        this.agentConfig = agentConfig;
        this.agentAuthorize = agentConfig.getAuthorize();
        this.jpomApplication = jpomApplication;
        this.projectCommander = projectCommander;
        projectLogConfig = agentConfig.getProject().getLog();
    }


    private void startAutoBackLog() {
        // 获取cron 表达式
        String cron = Opt.ofBlankAble(projectLogConfig.getAutoBackupConsoleCron()).orElse("0 0/10 * * * ?");
        //
        CronUtils.upsert(ID, cron, () -> {
            try {
                List<NodeProjectInfoModel> list = projectInfoService.list();
                if (list == null) {
                    return;
                }
                //
                list.forEach(this::checkProject);
            } catch (Exception e) {
                log.error(I18nMessageUtil.get("i18n.scheduled_backup_log_failure.a0d7"), e);
            }
        });
    }

    private void checkProject(NodeProjectInfoModel nodeProjectInfoModel) {
        File file = projectInfoService.resolveAbsoluteLogFile(nodeProjectInfoModel);
        if (!file.exists()) {
            return;
        }
        DataSize autoBackSize = projectLogConfig.getAutoBackupSize();
        autoBackSize = Optional.ofNullable(autoBackSize).orElseGet(() -> DataSize.ofMegabytes(50));
        long len = file.length();
        if (len > autoBackSize.toBytes()) {
            try {
                projectCommander.backLog(nodeProjectInfoModel);
            } catch (Exception e) {
                log.warn("auto back log", e);
            }
        }
        // 清理过期的文件
        File logFile = projectInfoService.resolveLogBack(nodeProjectInfoModel);
        DateTime nowTime = DateTime.now();
        List<File> files = FileUtil.loopFiles(logFile, pathname -> {
            DateTime dateTime = DateUtil.date(pathname.lastModified());
            long days = DateUtil.betweenDay(dateTime, nowTime, false);
            long saveDays = projectLogConfig.getSaveDays();
            return days > saveDays;
        });
        files.forEach(FileUtil::del);
    }

    @Override
    public void executeTask() {
        // 启动加载
        RemoteVersion.loadRemoteInfo();
        // 清空脚本缓存
        BaseRunScript.clearRunScript();
        // 清理临时文件
        File tempPath = agentConfig.getTempPath();
        if (FileUtil.exist(tempPath)) {
            File[] files = tempPath.listFiles((dir, name) -> {
                try {
                    DateTime dateTime = DateUtil.parse(name);
                    long between = DateUtil.between(dateTime, DateTime.now(), DateUnit.DAY);
                    // 保留一天以内的
                    return between > 1;
                } catch (DateException dateException) {
                    return false;
                }
            });
            Optional.ofNullable(files).ifPresent(files1 -> {
                for (File file : files1) {
                    CommandUtil.systemFastDel(file);
                }
            });
        }
    }

    /**
     * 尝试开启项目
     */
    private void autoStartProject() {
        List<NodeProjectInfoModel> allProject = projectInfoService.list();
        if (CollUtil.isEmpty(allProject)) {
            return;
        }
        List<NodeProjectInfoModel> startList = allProject.stream()
            .filter(nodeProjectInfoModel -> nodeProjectInfoModel.getAutoStart() != null && nodeProjectInfoModel.getAutoStart())
            .collect(Collectors.toList());
        ThreadUtil.execute(() -> {
            for (NodeProjectInfoModel nodeProjectInfoModel : startList) {
                try {
                    if (!projectCommander.isRun(nodeProjectInfoModel)) {
                        projectCommander.execCommand(ConsoleCommandOp.start, nodeProjectInfoModel);
                    }
                } catch (Exception e) {
                    log.warn(I18nMessageUtil.get("i18n.auto_start_project_failed.c7b5"), nodeProjectInfoModel.getId(), e.getMessage());
                }
            }
        });
        // 迁移备份日志文件
        allProject.stream()
            .filter(nodeProjectInfoModel -> nodeProjectInfoModel.getRunMode() != RunMode.Link)
            .filter(nodeProjectInfoModel -> StrUtil.isEmpty(nodeProjectInfoModel.getLogPath()))
            .forEach(nodeProjectInfoModel -> {
                String logPath = new File(nodeProjectInfoModel.allLib()).getParent();
                String log1 = FileUtil.normalize(String.format("%s/%s.log", logPath, nodeProjectInfoModel.getId()));
                File logBack = new File(log1 + "_back");
                if (FileUtil.isDirectory(logBack)) {
                    File resolveLogBack = projectInfoService.resolveLogBack(nodeProjectInfoModel);
                    FileUtil.mkdir(resolveLogBack);
                    log.info(I18nMessageUtil.get("i18n.auto_migrate_exist_backup_logs.dc33"), logBack.getAbsolutePath(), resolveLogBack);
                    FileUtil.moveContent(logBack, resolveLogBack, true);
                    FileUtil.del(logBack);
                }
                if (FileUtil.isFile(log1)) {
                    if (projectCommander.isRun(nodeProjectInfoModel)) {
                        log.warn(I18nMessageUtil.get("i18n.old_version_project_logs_exist_while_running.75ab"), nodeProjectInfoModel.getName(), log1);
                    } else {
                        File resolveLogBack = projectInfoService.resolveLogBack(nodeProjectInfoModel);
                        FileUtil.mkdir(resolveLogBack);
                        log.info(I18nMessageUtil.get("i18n.auto_migrate_exist_logs.c169"), log1, resolveLogBack);
                        FileUtil.move(FileUtil.file(log1), resolveLogBack, true);
                    }
                }
            });
    }


    /**
     * 自动推送插件端信息到服务端
     *
     * @param url 服务端url
     */
    public void autoPushToServer(String url) {
        url = StrUtil.removeSuffix(url, CharPool.SINGLE_QUOTE + "");
        url = StrUtil.removePrefix(url, CharPool.SINGLE_QUOTE + "");
        UrlBuilder urlBuilder = UrlBuilder.ofHttp(url);
        String networkName = (String) urlBuilder.getQuery().get("networkName");
        //
        LinkedHashSet<InetAddress> localAddressList = NetUtil.localAddressList(networkInterface -> StrUtil.isEmpty(networkName) || StrUtil.equals(networkName, networkInterface.getName()), address -> {
            // 非loopback地址，指127.*.*.*的地址
            return !address.isLoopbackAddress()
                // 需为IPV4地址
                && address instanceof Inet4Address;
        });
        if (StrUtil.isNotEmpty(networkName) && CollUtil.isEmpty(localAddressList)) {
            log.warn("No usable IP found by NIC name,{}", networkName);
        }
        Set<String> ips = localAddressList.stream().map(InetAddress::getHostAddress).filter(StrUtil::isNotEmpty).collect(Collectors.toSet());
        urlBuilder.addQuery("ips", CollUtil.join(ips, StrUtil.COMMA));
        urlBuilder.addQuery("loginName", agentAuthorize.getAgentName());
        urlBuilder.addQuery("loginPwd", agentAuthorize.getAgentPwd());
        int port = jpomApplication.getPort();
        urlBuilder.addQuery("port", port + "");
        //
        String build = urlBuilder.build();
        try (HttpResponse execute = HttpUtil.createGet(build, true).execute()) {
            String body = execute.body();
            log.info("推送注册结果:{}", body);
        }
    }

    @Override
    public void afterPropertiesSet(ApplicationContext applicationContext) throws Exception {
        this.startAutoBackLog();
        this.autoStartProject();
    }
}
