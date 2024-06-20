/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.controller.node.script;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import org.dromara.jpom.common.BaseServerController;
import org.dromara.jpom.common.ServerConst;
import org.dromara.jpom.common.ServerOpenApi;
import org.dromara.jpom.common.UrlRedirectUtil;
import org.dromara.jpom.common.forward.NodeForward;
import org.dromara.jpom.common.forward.NodeUrl;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.common.validator.ValidatorItem;
import org.dromara.jpom.model.PageResultDto;
import org.dromara.jpom.model.data.NodeModel;
import org.dromara.jpom.model.node.NodeScriptCacheModel;
import org.dromara.jpom.model.node.ProjectInfoCacheModel;
import org.dromara.jpom.model.user.UserModel;
import org.dromara.jpom.permission.*;
import org.dromara.jpom.service.node.script.NodeScriptExecuteLogServer;
import org.dromara.jpom.service.node.script.NodeScriptServer;
import org.dromara.jpom.service.user.TriggerTokenLogServer;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 脚本管理
 *
 * @author bwcx_jzy
 * @since 2019/4/24
 */
@RestController
@RequestMapping(value = "/node/script")
@Feature(cls = ClassFeature.NODE_SCRIPT)
@NodeDataPermission(cls = NodeScriptServer.class)
public class NodeScriptController extends BaseServerController {

    private final NodeScriptServer nodeScriptServer;
    private final NodeScriptExecuteLogServer nodeScriptExecuteLogServer;
    private final TriggerTokenLogServer triggerTokenLogServer;

    public NodeScriptController(NodeScriptServer nodeScriptServer,
                                NodeScriptExecuteLogServer nodeScriptExecuteLogServer,
                                TriggerTokenLogServer triggerTokenLogServer) {
        this.nodeScriptServer = nodeScriptServer;
        this.nodeScriptExecuteLogServer = nodeScriptExecuteLogServer;
        this.triggerTokenLogServer = triggerTokenLogServer;
    }

    /**
     * load node script list
     * 加载节点脚本列表
     *
     * @return json
     * @author Hotstrip
     */
    @PostMapping(value = "list_all", produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<PageResultDto<NodeScriptCacheModel>> listAll(HttpServletRequest request) {
        PageResultDto<NodeScriptCacheModel> modelPageResultDto = nodeScriptServer.listPage(request);
        return JsonMessage.success("", modelPageResultDto);
    }


    private void checkProjectPermission(String id, HttpServletRequest request, NodeModel node) {
        if (StrUtil.isEmpty(id)) {
            return;
        }
        String workspaceId = nodeScriptServer.getCheckUserWorkspace(request);
        String fullId = ProjectInfoCacheModel.fullId(workspaceId, node.getId(), id);
        boolean exists = nodeScriptServer.exists(fullId);
        if (!exists) {
            // 判断全局脚本
            NodeScriptCacheModel nodeScriptCacheModel = new NodeScriptCacheModel();
            nodeScriptCacheModel.setScriptId(id);
            nodeScriptCacheModel.setWorkspaceId(ServerConst.WORKSPACE_GLOBAL);
            exists = nodeScriptServer.exists(nodeScriptCacheModel);
            if (exists) {
                return;
            }
        }
        Assert.state(exists, I18nMessageUtil.get("i18n.no_corresponding_data_or_permission.1291"));
    }

    @GetMapping(value = "item.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public IJsonMessage<Object> item(HttpServletRequest request, String id) {
        NodeModel node = getNode();
        this.checkProjectPermission(id, request, node);
        return NodeForward.request(node, request, NodeUrl.Script_Item);
    }

    /**
     * 保存脚本
     *
     * @return json
     */
    @RequestMapping(value = "save.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EDIT)
    public IJsonMessage<Object> save(String id, String autoExecCron, HttpServletRequest request) {
        NodeModel node = getNode();
        this.checkProjectPermission(id, request, node);
        this.checkCron(autoExecCron);
        JsonMessage<Object> jsonMessage = NodeForward.request(node, request, NodeUrl.Script_Save, new String[]{}, "nodeId", node.getId());
        if (jsonMessage.success()) {
            nodeScriptServer.syncNode(node);
        }
        return jsonMessage;
    }

    @RequestMapping(value = "del.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.DEL)
    public IJsonMessage<Object> del(@ValidatorItem String id, HttpServletRequest request) {
        NodeModel node = getNode();
        this.checkProjectPermission(id, request, node);
        JsonMessage<Object> requestData = NodeForward.request(node, request, NodeUrl.Script_Del);
        if (requestData.success()) {
            nodeScriptServer.syncNode(node);
            // 删除日志
            nodeScriptExecuteLogServer.delCache(id, node.getId(), request);
        }
        return requestData;
    }

    /**
     * 同步脚本模版
     *
     * @return json
     */
    @GetMapping(value = "sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.DEL)
    public IJsonMessage<Object> syncProject(HttpServletRequest request) {
        //
        NodeModel node = getNode();
        int cache = nodeScriptServer.delCache(node.getId(), request);
        String msg = nodeScriptServer.syncExecuteNode(node);
        return JsonMessage.success(I18nMessageUtil.get("i18n.active_clearance.5870") + cache + StrUtil.SPACE + msg);
    }

    /**
     * 释放脚本关联的节点
     *
     * @param id 脚本ID
     * @return json
     */
    @RequestMapping(value = "unbind.json", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.DEL)
    @SystemPermission
    public IJsonMessage<String> unbind(@ValidatorItem String id, HttpServletRequest request) {
        nodeScriptServer.delByKey(id, request);
        return JsonMessage.success(I18nMessageUtil.get("i18n.unbind_success.1c43"));
    }

    /**
     * get a trigger url
     *
     * @param id id
     * @return json
     */
    @RequestMapping(value = "trigger-url", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EDIT)
    public IJsonMessage<Map<String, String>> getTriggerUrl(String id, String rest, HttpServletRequest request) {
        NodeScriptCacheModel item = nodeScriptServer.getByKeyAndGlobal(id, request);
        UserModel user = getUser();
        NodeScriptCacheModel updateInfo;
        if (StrUtil.isEmpty(item.getTriggerToken()) || StrUtil.isNotEmpty(rest)) {
            updateInfo = new NodeScriptCacheModel();
            updateInfo.setId(id);
            updateInfo.setTriggerToken(triggerTokenLogServer.restToken(item.getTriggerToken(), nodeScriptServer.typeName(),
                item.getId(), user.getId()));
            nodeScriptServer.updateById(updateInfo);
        } else {
            updateInfo = item;
        }
        Map<String, String> map = this.getBuildToken(updateInfo, request);
        String string = I18nMessageUtil.get("i18n.reset_success.faa3");
        return JsonMessage.success(StrUtil.isEmpty(rest) ? "ok" : string, map);
    }

    private Map<String, String> getBuildToken(NodeScriptCacheModel item, HttpServletRequest request) {
        String contextPath = UrlRedirectUtil.getHeaderProxyPath(request, ServerConst.PROXY_PATH);
        String url = ServerOpenApi.NODE_SCRIPT_TRIGGER_URL.
            replace("{id}", item.getId()).
            replace("{token}", item.getTriggerToken());
        String triggerBuildUrl = String.format("/%s/%s", contextPath, url);
        Map<String, String> map = new HashMap<>(10);
        map.put("triggerUrl", FileUtil.normalize(triggerBuildUrl));
        String batchTriggerBuildUrl = String.format("/%s/%s", contextPath, ServerOpenApi.NODE_SCRIPT_TRIGGER_BATCH);
        map.put("batchTriggerUrl", FileUtil.normalize(batchTriggerBuildUrl));

        map.put("id", item.getId());
        map.put("token", item.getTriggerToken());
        return map;
    }
}
