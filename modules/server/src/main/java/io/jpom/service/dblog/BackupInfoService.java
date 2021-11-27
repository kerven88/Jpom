/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 码之科技工作室
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.jpom.service.dblog;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.jiangzeyin.common.DefaultSystemLog;
import io.jpom.JpomApplication;
import io.jpom.common.Const;
import io.jpom.model.data.BackupInfoModel;
import io.jpom.model.enums.BackupStatusEnum;
import io.jpom.model.enums.BackupTypeEnum;
import io.jpom.service.h2db.BaseDbService;
import io.jpom.service.h2db.H2BackupService;
import io.jpom.system.ExtConfigBean;
import io.jpom.system.ServerExtConfigBean;
import io.jpom.system.db.DbConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 备份数据库 service
 * @author Hotstrip
 * @date 2021-11-18
 **/
@Service
public class BackupInfoService extends BaseDbService<BackupInfoModel> {

	@Resource
	private H2BackupService h2BackupService;

	/**
	 * 备份数据库 SQL 文件
	 * @param backupSqlPath 备份 SQL 文件路径，默认为数据库文件的同级 backup 目录
	 * @param tableNameList 需要备份的表名称列表，如果是全库备份，则不需要
	 */
	public void backupToSql(String backupSqlPath, final List<String> tableNameList) {
		// 判断备份文件路径是否为空
		if (StrUtil.isBlank(backupSqlPath)) {
			// 设置默认备份 SQL 的目录
			File file = FileUtil.file(ExtConfigBean.getInstance().getPath(), "db", Const.BACKUP_DIRECTORY_NAME);
			backupSqlPath = FileUtil.getAbsolutePath(file);
		}
		// 数据源参数
		final String url = DbConfig.getInstance().getDbUrl();

		ServerExtConfigBean serverExtConfigBean = ServerExtConfigBean.getInstance();
		final String user = serverExtConfigBean.getDbUserName();
		final String pass = serverExtConfigBean.getDbUserPwd();

		// 先构造备份信息插入数据库
		BackupInfoModel backupInfoModel = new BackupInfoModel();
		backupInfoModel.setId(IdUtil.fastSimpleUUID());
		backupInfoModel.setName(LocalDateTimeUtil.format(LocalDateTimeUtil.now(), DatePattern.PURE_DATETIME_PATTERN));
		// 判断备份类型
		int backupType = BackupTypeEnum.ALL.getCode();
		if (!CollectionUtils.isEmpty(tableNameList)) {
			backupType = BackupTypeEnum.PART.getCode();
		}
		backupInfoModel.setBackupType(backupType);
		backupInfoModel.setFilePath(backupSqlPath);
		insert(backupInfoModel);

		// 开启一个子线程去执行任务，任务完成之后修改对应的数据库备份信息
		ExecutorService service =  Executors.newSingleThreadExecutor();
		final String finalBackupSqlPath = backupSqlPath;
		service.submit(() -> {
			try {
				DefaultSystemLog.getLog().info("start a new Thread to execute H2 Database backup...start");
				h2BackupService.backupSql(url, user, pass, finalBackupSqlPath, tableNameList);
				// 修改备份任务执行完成
				File file = new File(finalBackupSqlPath);
				backupInfoModel.setFileSize(FileUtil.size(file));
				backupInfoModel.setSha1Sum(SecureUtil.sha1(file));
				backupInfoModel.setStatus(BackupStatusEnum.SUCCESS.getCode());
				update(backupInfoModel);
				DefaultSystemLog.getLog().info("start a new Thread to execute H2 Database backup...success");
			} catch (SQLException e) {
				// 记录错误日志信息，修改备份任务执行失败
				DefaultSystemLog.getLog().info("start a new Thread to execute H2 Database backup...catch exception...message: {}, cause: {}",
						e.getMessage(), e.getCause());
				backupInfoModel.setStatus(BackupStatusEnum.FAILED.getCode());
				update(backupInfoModel);
			}
		});
		service.shutdown();
	}

	/**
	 * 根据 SQL 文件还原数据库
	 * 还原数据库时只能同步，防止该过程中修改数据造成数据不一致
	 * @param backupSqlPath
	 */
	public boolean restoreWithSql(String backupSqlPath) {
		try {
			long startTs = System.currentTimeMillis();
			h2BackupService.restoreBackupSql(backupSqlPath);
			long endTs = System.currentTimeMillis();
			DefaultSystemLog.getLog().info("restore H2 Database backup...success...cast {} ms",
					endTs - startTs);
			return true;
		} catch (Exception e) {
			// 记录错误日志信息，返回数据库备份还原执行失败
			DefaultSystemLog.getLog().info("restore H2 Database backup...catch exception...message: {}, cause: {}",
					e.getMessage(), e.getCause());
			return false;
		}
	}
}