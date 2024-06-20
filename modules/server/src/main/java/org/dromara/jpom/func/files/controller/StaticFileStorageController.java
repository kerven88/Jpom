/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.func.files.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import org.dromara.jpom.common.BaseServerController;
import org.dromara.jpom.common.ServerConst;
import org.dromara.jpom.common.ServerOpenApi;
import org.dromara.jpom.common.UrlRedirectUtil;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.common.validator.ValidatorItem;
import org.dromara.jpom.func.files.model.StaticFileStorageModel;
import org.dromara.jpom.func.files.service.FileStorageService;
import org.dromara.jpom.func.files.service.StaticFileStorageService;
import org.dromara.jpom.model.PageResultDto;
import org.dromara.jpom.model.user.UserModel;
import org.dromara.jpom.permission.ClassFeature;
import org.dromara.jpom.permission.Feature;
import org.dromara.jpom.permission.MethodFeature;
import org.dromara.jpom.service.user.TriggerTokenLogServer;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author bwcx_jzy
 * @since 23/12/28 028
 */
@RestController
@RequestMapping(value = "/file-storage/static")
@Feature(cls = ClassFeature.STATIC_FILE_STORAGE)
public class StaticFileStorageController extends BaseServerController {

    private final StaticFileStorageService staticFileStorageService;
    private final FileStorageService fileStorageService;
    private final TriggerTokenLogServer triggerTokenLogServer;

    public StaticFileStorageController(StaticFileStorageService staticFileStorageService,
                                       FileStorageService fileStorageService,
                                       TriggerTokenLogServer triggerTokenLogServer) {
        this.staticFileStorageService = staticFileStorageService;
        this.fileStorageService = fileStorageService;
        this.triggerTokenLogServer = triggerTokenLogServer;
    }

    /**
     * 分页列表
     *
     * @return json
     */
    @PostMapping(value = "list", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public IJsonMessage<PageResultDto<StaticFileStorageModel>> list(HttpServletRequest request) {
        //
        String workspace = fileStorageService.getCheckUserWorkspace(request);
        PageResultDto<StaticFileStorageModel> listPage = staticFileStorageService.listPage(request, workspace);
        return JsonMessage.success("", listPage);
    }

    @GetMapping(value = "del", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.DEL)
    public IJsonMessage<String> del(String id, String ids, @ValidatorItem Boolean thorough, HttpServletRequest request) throws IOException {
        this.delItem(id, thorough, request);
        List<String> list = StrUtil.splitTrim(ids, StrUtil.COMMA);
        for (String s : list) {
            this.delItem(s, thorough, request);
        }
        return JsonMessage.success(I18nMessageUtil.get("i18n.delete_success.0007"));
    }

    /**
     * 删除数据
     *
     * @param id       id
     * @param thorough 是否彻底删除
     * @param request  请求
     */
    private void delItem(String id, Boolean thorough, HttpServletRequest request) {
        if (StrUtil.isEmpty(id)) {
            return;
        }
        StaticFileStorageModel storageModel = staticFileStorageService.getByKey(id);
        if (storageModel == null) {
            return;
        }
        this.checkStaticDir(storageModel, request);
        //
        if (thorough != null && thorough) {
            FileUtil.del(storageModel.getAbsolutePath());
        }
        //
        staticFileStorageService.delByKey(id);
    }

    /**
     * 判断是否有权限操作
     *
     * @param storageModel 静态文件
     */
    private void checkStaticDir(StaticFileStorageModel storageModel, HttpServletRequest request) {
        String workspace = fileStorageService.getCheckUserWorkspace(request);
        staticFileStorageService.checkStaticDir(storageModel, workspace);
    }

    /**
     * get a trigger url
     *
     * @param id id
     * @return json
     */
    @GetMapping(value = "trigger-url", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EDIT)
    public IJsonMessage<Map<String, String>> getTriggerUrl(@ValidatorItem String id, String rest, HttpServletRequest request) {
        UserModel user = getUser();
        // 查询当前工作空间
        StaticFileStorageModel item = staticFileStorageService.getByKey(id);

        this.checkStaticDir(item, request);
        //
        StaticFileStorageModel updateInfo;
        if (StrUtil.isEmpty(item.getTriggerToken()) || StrUtil.isNotEmpty(rest)) {
            updateInfo = new StaticFileStorageModel();
            updateInfo.setId(id);
            updateInfo.setTriggerToken(triggerTokenLogServer.restToken(item.getTriggerToken(), staticFileStorageService.typeName(),
                item.getId(), user.getId()));
            staticFileStorageService.updateById(updateInfo);
        } else {
            updateInfo = item;
        }
        Map<String, String> map = this.getBuildToken(updateInfo, request);
        String string = I18nMessageUtil.get("i18n.reset_success.faa3");
        return JsonMessage.success(StrUtil.isEmpty(rest) ? "ok" : string, map);
    }

    private Map<String, String> getBuildToken(StaticFileStorageModel item, HttpServletRequest request) {
        String contextPath = UrlRedirectUtil.getHeaderProxyPath(request, ServerConst.PROXY_PATH);
        Map<String, String> map = new HashMap<>(10);
        {
            String url = ServerOpenApi.STATIC_FILE_STORAGE_DOWNLOAD.
                replace("{id}", item.getId()).
                replace("{token}", item.getTriggerToken());
            String triggerBuildUrl = String.format("/%s/%s", contextPath, url);
            map.put("triggerDownloadUrl", FileUtil.normalize(triggerBuildUrl));
        }
        map.put("id", item.getId());
        map.put("token", item.getTriggerToken());
        return map;
    }

    @PostMapping(value = "edit", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EDIT)
    public IJsonMessage<String> edit(@ValidatorItem String id,
                                     String description,
                                     HttpServletRequest request) throws IOException {
        StaticFileStorageModel storageModel = staticFileStorageService.getByKey(id);
        this.checkStaticDir(storageModel, request);
        StaticFileStorageModel fileStorageModel = new StaticFileStorageModel();
        fileStorageModel.setId(id);
        fileStorageModel.setDescription(description);
        staticFileStorageService.updateById(fileStorageModel);
        return JsonMessage.success(I18nMessageUtil.get("i18n.modify_success.69be"));
    }

    /**
     * 判断是否存在文件
     *
     * @param fileId 文件id
     * @return json
     */
    @GetMapping(value = "has-file", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public IJsonMessage<StaticFileStorageModel> hasFile(@ValidatorItem String fileId, HttpServletRequest request) {
        StaticFileStorageModel storageModel = staticFileStorageService.getByKey(fileId);
        this.checkStaticDir(storageModel, request);
        return JsonMessage.success("", storageModel);
    }

    /**
     * 重新扫描
     *
     * @return json
     */
    @GetMapping(value = "scanner", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public IJsonMessage<String> scanner(HttpServletRequest request) {
        boolean scanning = staticFileStorageService.isScanning();
        Assert.state(!scanning, I18nMessageUtil.get("i18n.scanning_in_progress.7444"));
        String workspace = fileStorageService.getCheckUserWorkspace(request);
        staticFileStorageService.scanByWorkspace(workspace);
        return JsonMessage.success(I18nMessageUtil.get("i18n.scan_succeeded.7975"));
    }
}
