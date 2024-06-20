/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.func.openapi.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.common.BaseJpomController;
import org.dromara.jpom.common.BaseServerController;
import org.dromara.jpom.common.ServerOpenApi;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.common.interceptor.NotLogin;
import org.dromara.jpom.model.script.ScriptExecuteLogModel;
import org.dromara.jpom.model.script.ScriptModel;
import org.dromara.jpom.model.user.UserModel;
import org.dromara.jpom.service.script.ScriptExecuteLogServer;
import org.dromara.jpom.service.script.ScriptServer;
import org.dromara.jpom.service.user.TriggerTokenLogServer;
import org.dromara.jpom.socket.ServerScriptProcessBuilder;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 服务端脚本触发器
 *
 * @author bwcx_jzy
 * @since 2022/7/25
 */
@RestController
@NotLogin
@Slf4j
public class ServerScriptTriggerApiController extends BaseJpomController {

    private final ScriptServer scriptServer;
    private final ScriptExecuteLogServer scriptExecuteLogServer;
    private final TriggerTokenLogServer triggerTokenLogServer;

    public ServerScriptTriggerApiController(ScriptServer scriptServer,
                                            ScriptExecuteLogServer scriptExecuteLogServer,
                                            TriggerTokenLogServer triggerTokenLogServer) {
        this.scriptServer = scriptServer;
        this.scriptExecuteLogServer = scriptExecuteLogServer;
        this.triggerTokenLogServer = triggerTokenLogServer;
    }

    /**
     * 执行脚本
     *
     * @param id    构建ID
     * @param token 构建的token
     * @return json
     */
    @RequestMapping(value = ServerOpenApi.SERVER_SCRIPT_TRIGGER_URL, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<JSONObject> trigger2(@PathVariable String id, @PathVariable String token, HttpServletRequest request) {
        ScriptModel item = scriptServer.getByKey(id);
        Assert.notNull(item, I18nMessageUtil.get("i18n.no_data_found.4ffb"));
        Assert.state(StrUtil.equals(token, item.getTriggerToken()), I18nMessageUtil.get("i18n.trigger_token_error_or_expired.8976"));
        //
        UserModel userModel = triggerTokenLogServer.getUserByToken(token, scriptServer.typeName());
        //
        Assert.notNull(userModel, I18nMessageUtil.get("i18n.trigger_token_error_or_expired_with_code.393b"));

        try {
            BaseServerController.resetInfo(userModel);
            // 解析参数
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            Map<String, String> newParamMap = new HashMap<>(10);
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                String key = StrUtil.format("trigger_{}", entry.getKey());
                key = StrUtil.toUnderlineCase(key);
                newParamMap.put(key, entry.getValue());
            }
            // 创建记录
            ScriptExecuteLogModel nodeScriptExecLogModel = scriptExecuteLogServer.create(item, 2);
            // 执行
            ServerScriptProcessBuilder.create(item, nodeScriptExecLogModel.getId(), item.getDefArgs(), newParamMap);
            //
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("logId", nodeScriptExecLogModel.getId());
            return JsonMessage.success(I18nMessageUtil.get("i18n.start_execution.00d7"), jsonObject);
        } catch (Exception e) {
            log.error(I18nMessageUtil.get("i18n.trigger_auto_execute_server_script_exception.8e84"), e);
            return new JsonMessage<>(500, I18nMessageUtil.get("i18n.general_execution_exception.62e9") + e.getMessage());
        }
    }


    /**
     * 构建触发器
     * <p>
     * 参数 <code>[
     * {
     * "id":"1",
     * "token":"a"
     * }
     * ]</code>
     * <p>
     * 响应 <code>[
     * {
     * "id":"1",
     * "token":"a",
     * "logId":"1",
     * "msg":"没有对应数据",
     * }
     * ]</code>
     *
     * @return json
     */
    @PostMapping(value = ServerOpenApi.SERVER_SCRIPT_TRIGGER_BATCH, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<List<Object>> triggerBatch(HttpServletRequest request) {
        try {
            String body = ServletUtil.getBody(request);
            JSONArray jsonArray = JSONArray.parseArray(body);
            List<Object> collect = jsonArray.stream().peek(o -> {
                JSONObject jsonObject = (JSONObject) o;
                String id = jsonObject.getString("id");
                String token = jsonObject.getString("token");
                ScriptModel item = scriptServer.getByKey(id);
                if (item == null) {
                    String value = I18nMessageUtil.get("i18n.no_data_found.4ffb");
                    jsonObject.put("msg", value);
                    return;
                }
                UserModel userModel = triggerTokenLogServer.getUserByToken(token, scriptServer.typeName());
                if (userModel == null) {
                    String value = I18nMessageUtil.get("i18n.user_not_exist_trigger_invalid.f375");
                    jsonObject.put("msg", value);
                    return;
                }
                //
                if (!StrUtil.equals(token, item.getTriggerToken())) {
                    String value = I18nMessageUtil.get("i18n.trigger_token_error_or_expired.8976");
                    jsonObject.put("msg", value);
                    return;
                }
                BaseServerController.resetInfo(userModel);
                try {
                    // 创建记录
                    ScriptExecuteLogModel nodeScriptExecLogModel = scriptExecuteLogServer.create(item, 2);
                    // 执行
                    ServerScriptProcessBuilder.create(item, nodeScriptExecLogModel.getId(), item.getDefArgs());
                    jsonObject.put("logId", nodeScriptExecLogModel.getId());
                } catch (Exception e) {
                    log.error(I18nMessageUtil.get("i18n.trigger_auto_execute_command_template_exception.4e01"), e);
                    jsonObject.put("msg", I18nMessageUtil.get("i18n.general_execution_exception.62e9") + e.getMessage());
                }
                //
            }).collect(Collectors.toList());
            return JsonMessage.success(I18nMessageUtil.get("i18n.trigger_success.f9d1"), collect);
        } catch (Exception e) {
            log.error(I18nMessageUtil.get("i18n.batch_trigger_script_exception.8fb4"), e);
            return new JsonMessage<>(500, I18nMessageUtil.get("i18n.trigger_exception.d624") + e.getMessage());
        }
    }
}
