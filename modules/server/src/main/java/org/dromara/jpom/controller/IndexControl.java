/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.controller;

import cn.hutool.cache.Cache;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.RegexPool;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.*;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.system.SystemUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import cn.keepbx.jpom.plugins.IPlugin;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.common.BaseServerController;
import org.dromara.jpom.common.JpomManifest;
import org.dromara.jpom.common.ServerConst;
import org.dromara.jpom.common.UrlRedirectUtil;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.common.interceptor.NotLogin;
import org.dromara.jpom.configuration.NodeConfig;
import org.dromara.jpom.configuration.WebConfig;
import org.dromara.jpom.db.DbExtConfig;
import org.dromara.jpom.func.user.controller.UserNotificationController;
import org.dromara.jpom.func.user.dto.UserNotificationDto;
import org.dromara.jpom.model.user.UserModel;
import org.dromara.jpom.permission.SystemPermission;
import org.dromara.jpom.plugin.PluginFactory;
import org.dromara.jpom.service.system.SystemParametersServer;
import org.dromara.jpom.service.user.UserService;
import org.dromara.jpom.system.ExtConfigBean;
import org.dromara.jpom.system.ServerConfig;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 首页
 *
 * @author bwcx_jzy
 */
@RestController
@RequestMapping(value = "/")
@Slf4j
public class IndexControl extends BaseServerController {

    private final UserService userService;
    private final SystemParametersServer systemParametersServer;
    private final WebConfig webConfig;
    private final NodeConfig nodeConfig;
    private final DbExtConfig dbExtConfig;

    public IndexControl(UserService userService,
                        SystemParametersServer systemParametersServer,
                        ServerConfig serverConfig,
                        DbExtConfig dbExtConfig) {
        this.userService = userService;
        this.systemParametersServer = systemParametersServer;
        this.webConfig = serverConfig.getWeb();
        this.nodeConfig = serverConfig.getNode();
        this.dbExtConfig = dbExtConfig;
    }


    /**
     * 加载首页
     *
     * @api {get} / 加载首页 服务端前端页面
     * @apiGroup index
     * @apiSuccess {String} BODY HTML
     */
    @GetMapping(value = {"index", "", "/", "index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    @NotLogin
    public void index(HttpServletResponse response, HttpServletRequest request) {
        this.toIndex(response, request, StrUtil.EMPTY);
    }

    @GetMapping(value = "oauth2-{provide}", produces = MediaType.TEXT_HTML_VALUE)
    @NotLogin
    public void oauth2(HttpServletResponse response, HttpServletRequest request, @PathVariable String provide) {
        this.toIndex(response, request, provide);
    }

    private void toIndex(HttpServletResponse response, HttpServletRequest request, String oauth2Provide) {
        InputStream inputStream = ResourceUtil.getStream("classpath:/dist/index.html");
        String html = IoUtil.read(inputStream, CharsetUtil.CHARSET_UTF_8);
        //<div id="jpomCommonJs"></div>
        String path = ExtConfigBean.getPath();
        File file = FileUtil.file(String.format("%s/script/common.js", path));
        if (file.exists()) {
            String jsCommonContext = FileUtil.readString(file, CharsetUtil.CHARSET_UTF_8);
            // <div id="jpomCommonJs"><!--Don't delete this line, place for public JS --></div>
            String[] commonJsTemps = new String[]{"<div id=\"jpomCommonJs\"><!--Don't delete this line, place for public JS --></div>", "<div id=\"jpomCommonJs\"></div>"};
            for (String item : commonJsTemps) {
                html = StrUtil.replace(html, item, jsCommonContext);
            }
        }
        String language = I18nMessageUtil.tryGetSystemLanguage();
        // <routerBase>
        String proxyPath = UrlRedirectUtil.getHeaderProxyPath(request, ServerConst.PROXY_PATH);
        html = StrUtil.replace(html, "<routerBase>", proxyPath);
        //
        html = StrUtil.replace(html, "<link rel=\"icon\" href=\"favicon.ico\">", "<link rel=\"icon\" href=\"" + proxyPath + "favicon.ico\">");
        // <apiTimeOut>
        int webApiTimeout = webConfig.getApiTimeout();
        html = StrUtil.replace(html, "<apiTimeout>", String.valueOf(TimeUnit.SECONDS.toMillis(webApiTimeout)));
        html = StrUtil.replace(html, "<uploadFileSliceSize>", String.valueOf(nodeConfig.getUploadFileSliceSize()));
        html = StrUtil.replace(html, "<uploadFileConcurrent>", String.valueOf(nodeConfig.getUploadFileConcurrent()));
        html = StrUtil.replace(html, "<oauth2Provide>", oauth2Provide);
        html = StrUtil.replace(html, "<transportEncryption>", webConfig.getTransportEncryption());
        html = StrUtil.replace(html, "<jpomDefaultLocale>", language);
        // 修改网页标题
        String title = ReUtil.get("<title>.*?</title>", html, 0);
        if (StrUtil.isNotEmpty(title)) {
            html = StrUtil.replace(html, title, "<title>" + webConfig.getName() + "</title>");
        }
        ServletUtil.write(response, html, ContentType.TEXT_HTML.getValue());
    }

    /**
     * logo 图片
     *
     * @api {get} logo_image logo 图片
     * @apiGroup index
     * @apiSuccess {Object} BODY image
     */
    @RequestMapping(value = "logo-image", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @NotLogin
    public IJsonMessage<String> logoImage(HttpServletResponse response) {
        String logoFile = webConfig.getLogoFile();
        String imageSrc = this.loadImageSrc(response, logoFile, "classpath:/logo/jpom.png", "jpg", "png", "gif");
        return JsonMessage.success("", imageSrc);
    }

    /**
     * logo 图片
     *
     * @api {get} logo_image logo 图片
     * @apiGroup index
     * @apiSuccess {Object} BODY image
     */
    @RequestMapping(value = "favicon.ico", method = RequestMethod.GET, produces = MediaType.IMAGE_PNG_VALUE)
    @NotLogin
    public void favicon(HttpServletResponse response) throws IOException {
        String iconFile = webConfig.getIconFile();
        this.loadImage(response, iconFile, "classpath:/logo/favicon.ico", "ico", "png");
    }

    private void loadImage(HttpServletResponse response, String imgFile, String defaultResource, String... suffix) throws IOException {
        if (StrUtil.isNotEmpty(imgFile)) {
            if (Validator.isMatchRegex(RegexPool.URL_HTTP, imgFile)) {
                // 重定向
                response.sendRedirect(imgFile);
                return;
            }
            File file = FileUtil.file(imgFile);
            if (FileUtil.isFile(file)) {
                String type = FileTypeUtil.getType(file);
                String extName = FileUtil.extName(file);
                if (StrUtil.equalsAnyIgnoreCase(type, suffix) || StrUtil.equalsAnyIgnoreCase(extName, suffix)) {
                    ServletUtil.write(response, file);
                    return;
                }
            }
        }
        // favicon ico
        InputStream inputStream = ResourceUtil.getStream(defaultResource);
        ServletUtil.write(response, inputStream, MediaType.IMAGE_PNG_VALUE);
    }

    private String loadImageSrc(HttpServletResponse response, String imgFile, String defaultResource, String... suffix) {
        if (StrUtil.isNotEmpty(imgFile)) {
            if (Validator.isMatchRegex(RegexPool.URL_HTTP, imgFile)) {
                // 重定向
                return imgFile;
            }
            File file = FileUtil.file(imgFile);
            if (FileUtil.isFile(file)) {
                String type = FileTypeUtil.getType(file);
                String extName = FileUtil.extName(file);
                if (StrUtil.equalsAnyIgnoreCase(type, suffix) || StrUtil.equalsAnyIgnoreCase(extName, suffix)) {
                    ServletUtil.write(response, file);
                    String encode = Base64.encode(file);
                    String mimeType = FileUtil.getMimeType(file.toPath());
                    return URLUtil.getDataUriBase64(mimeType, encode);
                }
            }
        }
        // favicon ico
        InputStream inputStream = ResourceUtil.getStream(defaultResource);
        String encode = Base64.encode(inputStream);
        return URLUtil.getDataUriBase64(MediaType.IMAGE_PNG_VALUE, encode);
    }


    /**
     * @return json
     * @author Hotstrip
     * <p>
     * check if need to init system
     * @api {get} check-system 检查是否需要初始化系统
     * @apiGroup index
     * @apiUse defResultJson
     * @apiSuccess {String} routerBase 二级地址
     * @apiSuccess {String} name 系统名称
     * @apiSuccess {String} subTitle 主页面副标题
     * @apiSuccess {String} loginTitle 登录也标题
     * @apiSuccess {String} disabledGuide 是否禁用引导
     * @apiSuccess (222) {Object}  data 系统还没有超级管理员需要初始化
     */
    @NotLogin
    @RequestMapping(value = ServerConst.CHECK_SYSTEM, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<JSONObject> checkSystem(HttpServletRequest request) {
        JSONObject data = new JSONObject();
        data.put("routerBase", UrlRedirectUtil.getHeaderProxyPath(request, ServerConst.PROXY_PATH));
        //
        data.put("name", webConfig.getName());
        data.put("subTitle", webConfig.getSubTitle());
        data.put("loginTitle", webConfig.getLoginTitle());
        data.put("disabledGuide", webConfig.isDisabledGuide());
        //data.put("disabledCaptcha", webConfig.isDisabledCaptcha());
        data.put("notificationPlacement", webConfig.getNotificationPlacement());
        data.put("installId", JpomManifest.getInstance().getInstallId());
        // 用于判断是否属于容器部署
        boolean inDocker = StrUtil.isNotEmpty(SystemUtil.get("JPOM_PKG"));
        List<String> extendPlugins = new ArrayList<>();
        if (inDocker) {
            extendPlugins.add("inDocker");
        }
        // 验证 git 仓库信息
        try {
            IPlugin plugin = PluginFactory.getPlugin("git-clone");
            Map<String, Object> map = new HashMap<>();
            boolean systemGit = (boolean) plugin.execute("systemGit", map);
            if (systemGit) {
                extendPlugins.add("system-git");
            }
        } catch (Exception e) {
            log.warn(I18nMessageUtil.get("i18n.check_git_client_exception.42a3"), e);
        }
        data.put("extendPlugins", extendPlugins);
        if (userService.canUse()) {
            return new JsonMessage<>(200, "", data);
        }
        return new JsonMessage<>(222, I18nMessageUtil.get("i18n.need_initialize_system.fb62"), data);
    }


    /**
     * @return json
     * @api {post} menus_data.json 获取系统菜单相关数据
     * @apiGroup index
     * @apiUse loginUser
     * @apiParam {String} nodeId 节点ID
     * @apiSuccess {JSON}  data 菜单相关字段
     */
    @RequestMapping(value = "menus_data.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<List<Object>> menusData(HttpServletRequest request) {
        UserModel userModel = getUserModel();
        String workspaceId = nodeService.getCheckUserWorkspace(request);
        JSONObject config = systemParametersServer.getConfigDefNewInstance(StrUtil.format("menus_config_{}", workspaceId), JSONObject.class);
        String language = I18nMessageUtil.tryGetNormalLanguage();
        // 菜单
        InputStream inputStream = ResourceUtil.getStream("classpath:/menus/" + language + "/index.json");
        JSONArray showArray = config.getJSONArray("serverMenuKeys");


        String json = IoUtil.read(inputStream, CharsetUtil.CHARSET_UTF_8);
        JSONArray jsonArray = JSONArray.parseArray(json);
        List<Object> collect1 = jsonArray.stream().filter(o -> {
            JSONObject jsonObject = (JSONObject) o;
            if (!testMenus(jsonObject, userModel, showArray, request)) {
                return false;
            }
            JSONArray childs = jsonObject.getJSONArray("childs");
            if (childs != null) {
                List<Object> collect = childs.stream().filter(o1 -> {
                    JSONObject jsonObject1 = (JSONObject) o1;
                    return testMenus(jsonObject1, userModel, showArray, request);
                }).collect(Collectors.toList());
                if (collect.isEmpty()) {
                    return false;
                }
                jsonObject.put("childs", collect);
            }
            return true;
        }).collect(Collectors.toList());
        Assert.notEmpty(jsonArray, I18nMessageUtil.get("i18n.no_menus_contact_admin.cfec"));
        return JsonMessage.success("", collect1);
    }

    /**
     * @return json
     * @api {post} menus_data.json 获取系统菜单相关数据
     * @apiGroup index
     * @apiUse loginUser
     * @apiParam {String} nodeId 节点ID
     * @apiSuccess {JSON}  data 菜单相关字段
     */
    @RequestMapping(value = "system_menus_data.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @SystemPermission
    public IJsonMessage<List<Object>> systemMenusData(HttpServletRequest request) {
        UserModel userModel = getUserModel();
        String language = I18nMessageUtil.tryGetNormalLanguage();
        // 菜单
        InputStream inputStream = ResourceUtil.getStream("classpath:/menus/" + language + "/system.json");
        String json = IoUtil.read(inputStream, CharsetUtil.CHARSET_UTF_8);
        JSONArray jsonArray = JSONArray.parseArray(json);
        List<Object> collect1 = jsonArray.stream().filter(o -> {
            JSONObject jsonObject = (JSONObject) o;
            if (!testMenus(jsonObject, userModel, null, request)) {
                return false;
            }
            JSONArray childs = jsonObject.getJSONArray("childs");
            if (childs != null) {
                List<Object> collect = childs.stream().filter(o1 -> {
                    JSONObject jsonObject1 = (JSONObject) o1;
                    return testMenus(jsonObject1, userModel, null, request);
                }).collect(Collectors.toList());
                if (collect.isEmpty()) {
                    return false;
                }
                jsonObject.put("childs", collect);
            }
            return true;
        }).collect(Collectors.toList());
        Assert.notEmpty(jsonArray, I18nMessageUtil.get("i18n.no_menus_contact_admin.cfec"));
        return JsonMessage.success("", collect1);
    }

    private boolean testMenus(JSONObject jsonObject, UserModel userModel, JSONArray showArray, HttpServletRequest request) {
        String storageMode = jsonObject.getString("storageMode");
        if (StrUtil.isNotEmpty(storageMode)) {
            if (!StrUtil.equals(dbExtConfig.getMode().name(), storageMode)) {
                return false;
            }
        }
        String role = jsonObject.getString("role");
        if (StrUtil.equals(role, UserModel.SYSTEM_ADMIN) && !userModel.isSuperSystemUser()) {
            // 超级理员权限
            return false;
        }
        // 判断菜单显示
        if (CollUtil.isNotEmpty(showArray) && !userModel.isSuperSystemUser()) {
            String id = jsonObject.getString("id");
            if (!CollUtil.contains(showArray, id)) {
                boolean present = showArray.stream().anyMatch(o -> {
                    String str = StrUtil.toString(o);
                    return StrUtil.startWith(str, id + StrUtil.COLON) || StrUtil.endWith(str, StrUtil.COLON + id);
                });
                if (!present) {
                    return false;
                }
            }
        }
        // 系统管理员权限
        boolean system = StrUtil.equals(role, "system");
        if (system) {
            return userModel.isSystemUser();
        }
        return true;
    }

    /**
     * 生成分片id
     *
     * @return json
     */
    @GetMapping(value = "generate-sharding-id", produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<String> generateShardingId() {
        Cache<String, String> shardingIds = BaseServerController.SHARDING_IDS;
        int size = shardingIds.size();
        Assert.state(size <= 100, I18nMessageUtil.get("i18n.max_concurrent_shard_ids.f89c"));
        String uuid = IdUtil.fastSimpleUUID();
        shardingIds.put(uuid, uuid);
        return JsonMessage.success(uuid, uuid);
    }

    /**
     * 获取通知
     *
     * @return json
     */
    @GetMapping(value = "system-notification", produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<UserNotificationDto> getNotification() {
        UserNotificationDto notificationDto = systemParametersServer.getConfigDefNewInstance(UserNotificationController.KEY, UserNotificationDto.class);
        return JsonMessage.success("", notificationDto);
    }
}
