/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.socket.handler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.NioUtil;
import cn.hutool.core.map.SafeConcurrentHashMap;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.extra.ssh.ChannelType;
import cn.hutool.extra.ssh.JschUtil;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONValidator;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.common.i18n.I18nThreadUtil;
import org.dromara.jpom.func.assets.model.MachineSshModel;
import org.dromara.jpom.model.data.SshModel;
import org.dromara.jpom.model.user.UserModel;
import org.dromara.jpom.permission.ClassFeature;
import org.dromara.jpom.permission.Feature;
import org.dromara.jpom.permission.MethodFeature;
import org.dromara.jpom.service.dblog.SshTerminalExecuteLogService;
import org.dromara.jpom.service.node.ssh.SshService;
import org.dromara.jpom.service.user.UserBindWorkspaceService;
import org.dromara.jpom.util.SocketSessionUtil;
import org.dromara.jpom.util.StringUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ssh 处理2
 *
 * @author bwcx_jzy
 * @since 2019/8/9
 */
@Feature(cls = ClassFeature.SSH_TERMINAL, method = MethodFeature.EXECUTE)
@Slf4j
public class SshHandler extends BaseTerminalHandler {

    private static final ConcurrentHashMap<String, HandlerItem> HANDLER_ITEM_CONCURRENT_HASH_MAP = new SafeConcurrentHashMap<>();
    private static SshTerminalExecuteLogService sshTerminalExecuteLogService;
    private static UserBindWorkspaceService userBindWorkspaceService;
    private static SshService sshService;

    private static void init() {
        if (sshTerminalExecuteLogService == null) {
            sshTerminalExecuteLogService = SpringUtil.getBean(SshTerminalExecuteLogService.class);
        }
        if (userBindWorkspaceService == null) {
            userBindWorkspaceService = SpringUtil.getBean(UserBindWorkspaceService.class);
        }
        if (sshService == null) {
            sshService = SpringUtil.getBean(SshService.class);
        }
    }

    @Override
    public void afterConnectionEstablishedImpl(WebSocketSession session) throws Exception {
        super.afterConnectionEstablishedImpl(session);
        init();
        Map<String, Object> attributes = session.getAttributes();
        MachineSshModel machineSshModel = (MachineSshModel) attributes.get("machineSsh");
        SshModel sshModel = (SshModel) attributes.get("dataItem");
        //
        UserModel userInfo = (UserModel) attributes.get("userInfo");
        if (sshModel != null) {
            // 判断是没有任何限制
            String workspaceId = sshModel.getWorkspaceId();
            boolean sshCommandNotLimited = userBindWorkspaceService.exists(userInfo, workspaceId + UserBindWorkspaceService.SSH_COMMAND_NOT_LIMITED);
            attributes.put("sshCommandNotLimited", sshCommandNotLimited);
        } else {
            // 通过资产管理方式进入
            attributes.put("sshCommandNotLimited", true);
        }
        //
        HandlerItem handlerItem;
        try {
            //
            handlerItem = new HandlerItem(session, machineSshModel, sshModel);
            handlerItem.startRead();
        } catch (Exception e) {
            // 输出超时日志 @author jzy
            log.error(I18nMessageUtil.get("i18n.ssh_console_connection_timeout.8eb3"), e);
            sendBinary(session, I18nMessageUtil.get("i18n.ssh_console_connection_timeout.8eb3"));
            this.destroy(session);
            return;
        }
        HANDLER_ITEM_CONCURRENT_HASH_MAP.put(session.getId(), handlerItem);
        //
        Thread.sleep(1000);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        HandlerItem handlerItem = HANDLER_ITEM_CONCURRENT_HASH_MAP.get(session.getId());
        if (handlerItem == null) {
            sendBinary(session, I18nMessageUtil.get("i18n.already_offline.d3b5"));
            IoUtil.close(session);
            return;
        }
        String payload = message.getPayload();

        JSONValidator.Type type = StringUtil.validatorJson(payload);
        if (type == JSONValidator.Type.Object) {
            JSONObject jsonObject = JSONObject.parseObject(payload);
            String data = jsonObject.getString("data");
            if (StrUtil.equals(data, "jpom-heart")) {
                // 心跳消息不转发
                return;
            }
            if (StrUtil.equals(data, "resize")) {
                // 缓存区大小
                handlerItem.resize(jsonObject);
                return;
            }
        }
        //
        Map<String, Object> attributes = session.getAttributes();
        UserModel userInfo = (UserModel) attributes.get("userInfo");
        boolean sshCommandNotLimited = (boolean) attributes.get("sshCommandNotLimited");
        try {
            this.sendCommand(handlerItem, payload, userInfo, sshCommandNotLimited);
        } catch (Exception e) {
            sendBinary(session, "Failure:" + e.getMessage());
            log.error(I18nMessageUtil.get("i18n.command_execution_exception.4ccd"), e);
        }
    }

    private void sendCommand(HandlerItem handlerItem, String data, UserModel userInfo, boolean sshCommandNotLimited) throws Exception {
        if (handlerItem.checkInput(data, userInfo, sshCommandNotLimited)) {
            handlerItem.outputStream.write(data.getBytes());
        } else {
            handlerItem.outputStream.write(I18nMessageUtil.get("i18n.no_permission_to_execute_command.04d4").getBytes());
            handlerItem.outputStream.flush();
            handlerItem.outputStream.write(new byte[]{3});
        }
        handlerItem.outputStream.flush();
    }

    /**
     * 记录终端执行记录
     *
     * @param session 回话
     * @param command 命令行
     * @param refuse  是否拒绝
     */
    private void logCommands(WebSocketSession session, String command, boolean refuse) {
        List<String> split = StrUtil.splitTrim(command, StrUtil.CR);
        // 最后一个是否为回车, 最后一个不是回车表示还未提交，还在缓存去待确认
        boolean all = StrUtil.endWith(command, StrUtil.CR);
        int size = split.size();
        split = CollUtil.sub(split, 0, all ? size : size - 1);
        if (CollUtil.isEmpty(split)) {
            return;
        }
        // 获取基础信息
        Map<String, Object> attributes = session.getAttributes();
        UserModel userInfo = (UserModel) attributes.get("userInfo");
        String ip = (String) attributes.get("ip");
        String userAgent = (String) attributes.get(HttpHeaders.USER_AGENT);
        MachineSshModel machineSshModel = (MachineSshModel) attributes.get("machineSsh");
        SshModel sshItem = (SshModel) attributes.get("dataItem");
        //
        sshTerminalExecuteLogService.batch(userInfo, machineSshModel, sshItem, ip, userAgent, refuse, split);
    }

    private class HandlerItem implements Runnable, AutoCloseable {
        private final WebSocketSession session;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final Session openSession;
        private final ChannelShell channel;
        private final SshModel sshItem;
        private final MachineSshModel machineSshModel;
        private final StringBuilder nowLineInput = new StringBuilder();

        HandlerItem(WebSocketSession session, MachineSshModel machineSshModel, SshModel sshModel) throws IOException {
            this.session = session;
            this.sshItem = sshModel;
            this.machineSshModel = machineSshModel;
            this.openSession = sshService.getSessionByModel(machineSshModel);
            this.channel = (ChannelShell) JschUtil.createChannel(openSession, ChannelType.SHELL);
            this.inputStream = channel.getInputStream();
            this.outputStream = channel.getOutputStream();
        }

        void startRead() throws JSchException {
            this.channel.connect(machineSshModel.timeout());
            I18nThreadUtil.execute(this);
        }

        /**
         * 调整 缓存区大小
         *
         * @param jsonObject 参数
         */
        private void resize(JSONObject jsonObject) {
            Integer rows = Convert.toInt(jsonObject.getString("rows"), 10);
            Integer cols = Convert.toInt(jsonObject.getString("cols"), 10);
            Integer wp = Convert.toInt(jsonObject.getString("wp"), 10);
            Integer hp = Convert.toInt(jsonObject.getString("hp"), 10);
            this.channel.setPtySize(cols, rows, wp, hp);
        }

        /**
         * 添加到命令队列
         *
         * @param msg 输入
         * @return 当前待确认待所有命令
         */
        private String append(String msg) {
            char[] x = msg.toCharArray();
            if (x.length == 1 && x[0] == 127) {
                // 退格键
                int length = nowLineInput.length();
                if (length > 0) {
                    nowLineInput.delete(length - 1, length);
                }
            } else {
                nowLineInput.append(msg);
            }
            return nowLineInput.toString();
        }

        /**
         * 检查输入是否包含禁止命令，记录执行记录
         *
         * @param msg                  输入
         * @param userInfo             用户
         * @param sshCommandNotLimited 是否解除限制
         * @return true 没有任何限制
         */
        public boolean checkInput(String msg, UserModel userInfo, boolean sshCommandNotLimited) {
            String allCommand = this.append(msg);
            boolean refuse;
            // 超级管理员不限制,有权限都不限制
            boolean systemUser = userInfo.isSuperSystemUser() || sshCommandNotLimited;
            if (StrUtil.equalsAny(msg, StrUtil.CR, StrUtil.TAB)) {
                String join = nowLineInput.toString();
                if (StrUtil.equals(msg, StrUtil.CR)) {
                    nowLineInput.setLength(0);
                }
                // sshItem 可能为空
                refuse = sshItem == null || SshModel.checkInputItem(sshItem, join);
            } else {
                // 复制输出
                refuse = sshItem == null || SshModel.checkInputItem(sshItem, msg);
            }
            // 执行命令行记录
            logCommands(session, allCommand, refuse);
            return systemUser || refuse;
        }


        @Override
        public void run() {
            try {
                byte[] buffer = new byte[1024];
                int i;
                //如果没有数据来，线程会一直阻塞在这个地方等待数据。
                while ((i = inputStream.read(buffer)) != NioUtil.EOF) {
                    sendBinary(session, new String(Arrays.copyOfRange(buffer, 0, i), machineSshModel.charset()));
                }
            } catch (Exception e) {
                if (!this.openSession.isConnected()) {
                    log.error(I18nMessageUtil.get("i18n.ssh_error_string.6bdb"), e.getMessage());
                    return;
                }
                log.error(I18nMessageUtil.get("i18n.read_error.7fa5"), e);
                SshHandler.this.destroy(this.session);
            }
        }

        @Override
        public void close() throws Exception {
            IoUtil.close(this.inputStream);
            IoUtil.close(this.outputStream);
            JschUtil.close(this.channel);
            JschUtil.close(this.openSession);
        }
    }

    @Override
    public void destroy(WebSocketSession session) {
        HandlerItem handlerItem = HANDLER_ITEM_CONCURRENT_HASH_MAP.get(session.getId());
        IoUtil.close(handlerItem);
        IoUtil.close(session);
        HANDLER_ITEM_CONCURRENT_HASH_MAP.remove(session.getId());
        SocketSessionUtil.close(session);
    }
}
