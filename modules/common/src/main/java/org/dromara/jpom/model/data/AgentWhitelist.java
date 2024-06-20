/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.model.data;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.StrSplitter;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.model.BaseModel;
import org.dromara.jpom.system.ExtConfigBean;
import org.springframework.util.Assert;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 授权
 *
 * @author bwcx_jzy
 * @since 2019/4/16
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentWhitelist extends BaseModel {
    /**
     * 项目目录授权、日志文件授权
     */
    private List<String> project;
    /**
     * 运行编辑的后缀文件
     */
    private List<String> allowEditSuffix;

    /**
     * 格式化，判断是否与jpom 数据路径冲突
     *
     * @param list list
     * @return null 是有冲突的
     */
    public static List<String> covertToArray(List<String> list, String errorMsg) {
        return covertToArray(list, -1, errorMsg);
    }

    /**
     * 格式化，判断是否与jpom 数据路径冲突
     *
     * @param list list
     * @return null 是有冲突的
     */
    public static List<String> covertToArray(List<String> list, int maxLen, String errorMsg) {
        if (list == null) {
            return null;
        }
        return list.stream()
            .map(s -> {
                String val = FileUtil.normalize(s);
                Assert.state(FileUtil.isAbsolutePath(val), I18nMessageUtil.get("i18n.need_configure_absolute_path.f2e6") + val);
                File file = FileUtil.file(val);
                File parentFile = file.getParentFile();
                Assert.notNull(parentFile, I18nMessageUtil.get("i18n.cannot_configure_root_path.d86e") + val);
                // 判断是否保护jpom 路径
                Assert.state(!StrUtil.startWith(ExtConfigBean.getPath(), val), errorMsg);
                //
                if (maxLen > 0) {
                    Assert.state(StrUtil.length(val) <= maxLen, StrUtil.format(I18nMessageUtil.get("i18n.config_path_exceeds_length_limit.f684"), maxLen, val));
                }
                return val;
            })
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * 转换为字符串
     *
     * @param jsonArray jsonArray
     * @return str
     */
    public static String convertToLine(Collection<String> jsonArray) {
        return CollUtil.join(jsonArray, StrUtil.CRLF);
    }

    /**
     * 判断是否在授权列表中
     *
     * @param list list
     * @param path 对应项
     * @return false 不在列表中
     */
    public static boolean checkPath(List<String> list, String path) {
        if (list == null) {
            return false;
        }
        if (StrUtil.isEmpty(path)) {
            return false;
        }
        File file1, file2 = FileUtil.file(path);
        for (String item : list) {
            file1 = FileUtil.file(item);
            if (FileUtil.pathEquals(file1, file2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将字符串转为 list
     *
     * @param value    字符串
     * @param errorMsg 错误消息
     * @return list
     */
    public static List<String> parseToList(String value, String errorMsg) {
        return parseToList(value, false, errorMsg);
    }

    /**
     * 将字符串转为 list
     *
     * @param value    字符串
     * @param required 是否为必填
     * @param errorMsg 错误消息
     * @return list
     */
    public static List<String> parseToList(String value, boolean required, String errorMsg) {
        if (required) {
            Assert.hasLength(value, errorMsg);
        } else {
            if (StrUtil.isEmpty(value)) {
                return null;
            }
        }
        List<String> list = StrSplitter.splitTrim(value, StrUtil.LF, true);
        Assert.notEmpty(list, errorMsg);
        return list;
    }

    /**
     * 获取文件可以编辑的 文件编码格式
     *
     * @param filename 文件名
     * @return charset 不能编辑情况会抛出异常
     */
    public static Charset checkFileSuffix(List<String> allowEditSuffix, String filename) {
        Assert.notEmpty(allowEditSuffix, I18nMessageUtil.get("i18n.editable_suffixes_not_configured.5b41"));
        Charset charset = AgentWhitelist.parserFileSuffixMap(allowEditSuffix, filename);
        Assert.notNull(charset, I18nMessageUtil.get("i18n.disallowed_file_extension.eb05"));
        return charset;
    }

    /**
     * 静默判断是否可以编辑对应的文件
     *
     * @param filename 文件名
     * @return true 可以编辑
     */
    public static boolean checkSilentFileSuffix(List<String> allowEditSuffix, String filename) {
        if (CollUtil.isEmpty(allowEditSuffix)) {
            return false;
        }
        Charset charset = AgentWhitelist.parserFileSuffixMap(allowEditSuffix, filename);
        return charset != null;
    }

    /**
     * 根据文件名 和 可以配置列表 获取编码格式
     *
     * @param allowEditSuffix 允许编辑的配置
     * @param filename        文件名
     * @return 没有匹配到 返回 null，没有配置编码格式即使用系统默认编码格式
     */
    private static Charset parserFileSuffixMap(List<String> allowEditSuffix, String filename) {
        Map<String, Charset> map = CollStreamUtil.toMap(allowEditSuffix, s -> {
            List<String> split = StrUtil.split(s, StrUtil.AT);
            return CollUtil.getFirst(split);
        }, s -> {
            List<String> split = StrUtil.split(s, StrUtil.AT);
            if (split.size() > 1) {
                String last = CollUtil.getLast(split);
                return CharsetUtil.charset(last);
            } else {
                return CharsetUtil.defaultCharset();
            }
        });
        // 可能配置 所有
        Charset charset = map.get("*");
        if (charset != null) {
            return charset;
        }
        Set<Map.Entry<String, Charset>> entries = map.entrySet();
        for (Map.Entry<String, Charset> entry : entries) {
            if (StrUtil.endWithAnyIgnoreCase(filename, entry.getKey(), StrUtil.DOT + entry.getKey())) {
                return entry.getValue();
            }
            if (ReUtil.isMatch(entry.getKey(), filename)) {
                // 满足正则条件
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 检查授权包含关系
     *
     * @param jsonArray 要检查的对象
     * @return null 正常
     */
    public static String findStartsWith(List<String> jsonArray) {
        return findStartsWith(jsonArray, 0);
    }

    /**
     * 检查授权包含关系
     *
     * @param jsonArray 要检查的对象
     * @param start     检查的坐标
     * @return null 正常
     */
    private static String findStartsWith(List<String> jsonArray, int start) {
        if (jsonArray == null) {
            return null;
        }
        String str = jsonArray.get(start);
        int len = jsonArray.size();
        for (int i = 0; i < len; i++) {
            if (i == start) {
                continue;
            }
            String findStr = jsonArray.get(i);
            if (FileUtil.isSub(FileUtil.file(findStr), FileUtil.file(str))) {
                return str;
            }
        }
        if (start < len - 1) {
            return findStartsWith(jsonArray, start + 1);
        }
        return null;
    }
}
