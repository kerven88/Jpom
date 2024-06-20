/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.db;

import cn.hutool.db.ds.DSFactory;
import cn.hutool.setting.Setting;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.system.JpomRuntimeException;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据库实现
 *
 * @author bwcx_jzy
 * @since 2023/1/5
 */
public interface IStorageService extends AutoCloseable, IMode {

    /**
     * 初始化数据库
     *
     * @param dbExtConfig 配置参数信息
     * @return 数据库连接工厂
     */
    DSFactory init(DbExtConfig dbExtConfig);

    /**
     * 创建数据库连接工厂
     *
     * @param dbExtConfig 数据库配置
     * @param url         url
     * @param user        用户名
     * @param pass        密码
     * @return 数据库连接工厂
     */
    DSFactory create(DbExtConfig dbExtConfig, String url, String user, String pass);

    /**
     * 创建数据库配置参数
     *
     * @param dbExtConfig 数据库配置
     * @param url         url
     * @param user        用户名
     * @param pass        密码
     * @return 配置
     */
    Setting createSetting(DbExtConfig dbExtConfig, String url, String user, String pass);

    /**
     * 获取数据库连接工厂
     *
     * @return DSFactory
     */
    DSFactory getDsFactory();

    /**
     * 是否存在数据库文件
     *
     * @return true 存在
     * @throws Exception 异常
     */
    default boolean hasDbData() throws Exception {
        throw new UnsupportedOperationException(I18nMessageUtil.get("i18n.no_implemented_feature.af80"));
    }

    /**
     * 恢复数据库
     *
     * @return 恢复后的 sql 路径
     * @throws Exception 异常
     */
    default File recoverDb() throws Exception {
        throw new UnsupportedOperationException(I18nMessageUtil.get("i18n.no_implemented_feature.af80"));
    }

    /**
     * 删除数据库
     *
     * @return 删除后的 sql 路径
     * @throws Exception 异常
     */
    default String deleteDbFiles() throws Exception {
        throw new UnsupportedOperationException(I18nMessageUtil.get("i18n.no_implemented_feature.af80"));
    }

    /**
     * 转换 sql 文件内容,低版本兼容高版本
     *
     * @param sqlFile sql 文件
     */
    default void transformSql(File sqlFile) {
        throw new UnsupportedOperationException(I18nMessageUtil.get("i18n.no_implemented_feature.af80"));
    }

    /**
     * 恢复数据库
     *
     * @param dsFactory      数据库连接
     * @param recoverSqlFile 要恢复的数据库文件
     * @throws Exception 异常
     */
    default void executeRecoverDbSql(DSFactory dsFactory, File recoverSqlFile) throws Exception {
        if (recoverSqlFile == null) {
            return;
        }
        throw new UnsupportedOperationException(I18nMessageUtil.get("i18n.no_implemented_feature.af80"));
    }

    /**
     * 修改账号 密码
     *
     * @param oldUes 旧的账号
     * @param newUse 新的账号
     * @param newPwd 新密码
     * @throws SQLException sql 异常
     */
    default void alterUser(String oldUes, String newUse, String newPwd) throws SQLException {
        throw new UnsupportedOperationException(I18nMessageUtil.get("i18n.no_implemented_feature.af80"));
    }

    /**
     * 备份数据库
     *
     * @param url           url
     * @param user          账号
     * @param pass          密码
     * @param backupSqlPath sql 存放路径
     * @param tableName     备份的表名
     * @throws Exception 异常
     */
    default void backupSql(String url, String user, String pass, String backupSqlPath, List<String> tableName) throws Exception {
        throw new UnsupportedOperationException(I18nMessageUtil.get("i18n.no_implemented_feature.af80"));
    }

    /**
     * 数据库地址
     *
     * @return url
     */
    String dbUrl();

    /**
     * 游标查询批处理数量
     *
     * @return mysql 和 h2 不一致
     */
    int getFetchSize();

    /**
     * 异常转换
     *
     * @param e 异常
     * @return 转换后
     */
    JpomRuntimeException warpException(Exception e);
}
