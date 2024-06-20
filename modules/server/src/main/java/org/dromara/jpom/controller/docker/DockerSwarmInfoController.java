/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.controller.docker;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.common.validator.ValidatorItem;
import org.dromara.jpom.controller.docker.base.BaseDockerSwarmInfoController;
import org.dromara.jpom.func.assets.model.MachineDockerModel;
import org.dromara.jpom.func.assets.server.MachineDockerServer;
import org.dromara.jpom.model.PageResultDto;
import org.dromara.jpom.model.docker.DockerInfoModel;
import org.dromara.jpom.model.docker.DockerSwarmInfoMode;
import org.dromara.jpom.permission.ClassFeature;
import org.dromara.jpom.permission.Feature;
import org.dromara.jpom.permission.MethodFeature;
import org.dromara.jpom.service.docker.DockerInfoService;
import org.dromara.jpom.service.docker.DockerSwarmInfoService;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author bwcx_jzy
 * @since 2022/2/13
 */
@RestController
@Feature(cls = ClassFeature.DOCKER_SWARM)
@RequestMapping(value = "/docker/swarm")
@Slf4j
public class DockerSwarmInfoController extends BaseDockerSwarmInfoController {

    private final DockerSwarmInfoService dockerSwarmInfoService;
    private final MachineDockerServer machineDockerServer;
    private final DockerInfoService dockerInfoService;

    public DockerSwarmInfoController(DockerSwarmInfoService dockerSwarmInfoService,
                                     MachineDockerServer machineDockerServer,
                                     DockerInfoService dockerInfoService) {
        this.dockerSwarmInfoService = dockerSwarmInfoService;
        this.machineDockerServer = machineDockerServer;
        this.dockerInfoService = dockerInfoService;
    }

    /**
     * @return json
     */
    @PostMapping(value = "list", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public IJsonMessage<PageResultDto<DockerSwarmInfoMode>> list(HttpServletRequest request) {
        // load list with page
        PageResultDto<DockerSwarmInfoMode> resultDto = dockerSwarmInfoService.listPage(request);
        resultDto.each(dockerSwarmInfoMode -> {
            String swarmId = dockerSwarmInfoMode.getSwarmId();
            MachineDockerModel machineDocker = machineDockerServer.tryMachineDockerBySwarmId(swarmId);
            dockerSwarmInfoMode.setMachineDocker(machineDocker);
        });
        return JsonMessage.success("", resultDto);
    }

    /**
     * @return json
     */
    @GetMapping(value = "list-all", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public IJsonMessage<List<DockerSwarmInfoMode>> listAll(HttpServletRequest request) {
        // load list with all
        List<DockerSwarmInfoMode> swarmInfoModes = dockerSwarmInfoService.listByWorkspace(request);
        return JsonMessage.success("", swarmInfoModes);
    }

    @PostMapping(value = "edit", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EDIT)
    public IJsonMessage<Object> edit(@ValidatorItem String id,
                                    @ValidatorItem String name,
                                    @ValidatorItem String tag,
                                    HttpServletRequest request) throws Exception {
        String workspaceId = dockerSwarmInfoService.getCheckUserWorkspace(request);
        DockerSwarmInfoMode dockerSwarmInfoMode1 = dockerSwarmInfoService.getByKey(id, request);
        Assert.notNull(dockerSwarmInfoMode1, I18nMessageUtil.get("i18n.cluster_not_exist.4098"));
        // 更新集群信息
        DockerSwarmInfoMode dockerSwarmInfoMode = new DockerSwarmInfoMode();
        dockerSwarmInfoMode.setId(id);
        dockerSwarmInfoMode.setName(name);
        dockerSwarmInfoMode.setTag(tag);
        dockerSwarmInfoService.updateById(dockerSwarmInfoMode);
        // 更新集群关联的 docker 工作空间的 tag
        MachineDockerModel dockerModel = new MachineDockerModel();
        dockerModel.setSwarmId(dockerSwarmInfoMode1.getSwarmId());
        List<MachineDockerModel> machineDockerModels = machineDockerServer.listByBean(dockerModel);
        Assert.notEmpty(machineDockerModels, I18nMessageUtil.get("i18n.docker_info_not_found.4f64"));
        for (MachineDockerModel machineDockerModel : machineDockerModels) {
            DockerInfoModel queryWhere = new DockerInfoModel();
            queryWhere.setMachineDockerId(machineDockerModel.getId());
            queryWhere.setWorkspaceId(workspaceId);
            List<DockerInfoModel> dockerInfoModels = dockerInfoService.listByBean(queryWhere);
            for (DockerInfoModel dockerInfoModel : dockerInfoModels) {
                // 处理标签
                Collection<String> allTag = StrUtil.splitTrim(dockerInfoModel.getTags(), StrUtil.COLON);
                allTag = ObjectUtil.defaultIfNull(allTag, new ArrayList<>());
                if (!allTag.contains(tag)) {
                    allTag.add(tag);
                }
                allTag = allTag.stream().filter(StrUtil::isNotEmpty).collect(Collectors.toSet());
                String newTags = CollUtil.join(allTag, StrUtil.COLON, StrUtil.COLON, StrUtil.COLON);
                //
                DockerInfoModel update = new DockerInfoModel();
                update.setId(dockerInfoModel.getId());
                update.setTags(newTags);
                dockerInfoService.updateById(update);
            }
        }
        //
        return JsonMessage.success(I18nMessageUtil.get("i18n.modify_success.69be"));
    }

    @GetMapping(value = "del", produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.DEL)
    public IJsonMessage<Object> del(@ValidatorItem String id, HttpServletRequest request) throws Exception {
        dockerSwarmInfoService.delByKey(id, request);
        return JsonMessage.success(I18nMessageUtil.get("i18n.delete_success.0007"));
    }


    @Override
    protected Map<String, Object> toDockerParameter(String id) {
        return machineDockerServer.dockerParameter(id);
    }
}
