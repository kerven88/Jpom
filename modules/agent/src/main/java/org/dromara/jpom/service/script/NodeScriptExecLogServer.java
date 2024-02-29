/*
 * Copyright (c) 2019 Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.service.script;

import org.dromara.jpom.common.AgentConst;
import org.dromara.jpom.model.data.NodeScriptExecLogModel;
import org.dromara.jpom.service.BaseOperService;
import org.springframework.stereotype.Service;

/**
 * @author bwcx_jzy
 * @since 2021/12/26
 */
@Service
public class NodeScriptExecLogServer extends BaseOperService<NodeScriptExecLogModel> {

	public NodeScriptExecLogServer() {
		super(AgentConst.SCRIPT_LOG);
	}
}
