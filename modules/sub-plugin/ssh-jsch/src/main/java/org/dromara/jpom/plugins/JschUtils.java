/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.plugins;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.LineHandler;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.ChannelType;
import cn.hutool.extra.ssh.JschUtil;
import cn.hutool.extra.ssh.Sftp;
import com.jcraft.jsch.*;
import lombok.Lombok;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.common.i18n.I18nMessageUtil;
import org.dromara.jpom.system.ExtConfigBean;
import org.dromara.jpom.util.StringUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Function;

/**
 * hutool 默认封装的 SSH 工具，仅支持传入 SSL Private Key File，不支持  Private Key Content。
 * <p>
 * 本实现用于直接采用 Private Key Content 登录。
 *
 * <pre>
 *
 * Created by zhenqin.
 * User: zhenqin
 * Date: 2022/3/25
 * Time: 下午2:56
 * Email: zhzhenqin@163.com
 *
 * </pre>
 *
 * @author zhenqin
 */
@Slf4j
public class JschUtils {


    public final static String HEADER = "-----BEGIN RSA PRIVATE KEY-----";

    public final static String FOOTER = "-----END RSA PRIVATE KEY-----";

    /**
     * GETKEYTYPENAME_METHOD getKeyTypeName 是私有的
     *
     * @see KeyPair#getKeyTypeName
     */
    private static final Method GET_KEY_TYPE_NAME_METHOD = ReflectUtil.getMethod(KeyPair.class, "getKeyTypeName");

    private static class ContentIdentity implements Identity {

        private KeyPair kpair;
        private final String identity;


        static ContentIdentity newInstance(byte[] prvContent, byte[] pubContent, String username, JSch jsch) throws Exception {
            KeyPair kpair = KeyPair.load(jsch, prvContent, pubContent);
            return new ContentIdentity(username, kpair);
        }

        private ContentIdentity(String name, KeyPair kpair) {
            this.identity = name;
            this.kpair = kpair;
        }


        /**
         * Decrypts this identity with the specified pass-phrase.
         *
         * @param passphrase the pass-phrase for this identity.
         * @return <tt>true</tt> if the decryption is succeeded
         * or this identity is not cyphered.
         */
        @Override
        public boolean setPassphrase(byte[] passphrase) {
            return kpair.decrypt(passphrase);
        }

        /**
         * Returns the public-key blob.
         *
         * @return the public-key blob
         */
        @Override
        public byte[] getPublicKeyBlob() {
            return kpair.getPublicKeyBlob();
        }

        /**
         * Signs on data with this identity, and returns the result.
         *
         * @param data data to be signed
         * @return the signature
         */
        @Override
        public byte[] getSignature(byte[] data) {
            return kpair.getSignature(data);
        }

        /**
         * @see #setPassphrase(byte[] passphrase)
         * @deprecated This method should not be invoked.
         */
        @Override
        public boolean decrypt() {
            throw new RuntimeException("not implemented");
        }

        /**
         * Returns the name of the key algorithm.
         *
         * @return "ssh-rsa" or "ssh-dss"
         */
        @Override
        public String getAlgName() {
            try {
                byte[] name = ReflectUtil.invoke(kpair, GET_KEY_TYPE_NAME_METHOD);
                return new String(name, CharsetUtil.CHARSET_UTF_8);
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * Returns the name of this identity.
         * It will be useful to identify this object in the {@link IdentityRepository}.
         */
        @Override
        public String getName() {
            return identity;
        }

        /**
         * Returns <tt>true</tt> if this identity is cyphered.
         *
         * @return <tt>true</tt> if this identity is cyphered.
         */
        @Override
        public boolean isEncrypted() {
            return kpair.isEncrypted();
        }

        /**
         * Disposes internally allocated data, like byte array for the private key.
         */
        @Override
        public void clear() {
            kpair.dispose();
            kpair = null;
        }

        /**
         * Returns an instance of {@link KeyPair} used in this {@link Identity}.
         *
         * @return an instance of {@link KeyPair} used in this {@link Identity}.
         */
        public KeyPair getKeyPair() {
            return kpair;
        }
    }


    /**
     * 通过私钥获取 Session
     *
     * @param sshHost    ssh 目标节点IP、域名、机器名
     * @param sshPort    ssh 服务端口号，一般为 22
     * @param sshUser    ssh 目标节点登录用户
     * @param privateKey 采用的私钥，一般由 ssh-keygen 生成。必须包含完整的前后缀：-----BEGIN RSA PRIVATE KEY-----
     * @param passphrase 密码
     * @return Session
     */
    public static Session createSession(String sshHost, int sshPort, String sshUser, String privateKey, byte[] passphrase) {
        final JSch jsch = new JSch();
        try {
            byte[] privateKeyByte = StrUtil.bytes(privateKey);
            Identity identity = ContentIdentity.newInstance(privateKeyByte, null, sshUser, jsch);
            jsch.addIdentity(identity, passphrase);
        } catch (Exception e) {
            throw Lombok.sneakyThrow(e);
        }
        return JschUtil.createSession(jsch, sshHost, sshPort, sshUser);
    }

    /**
     * ssh 执行模版命令(并自动删除执行后的命令脚本文件)
     *
     * @param charset  编码格式
     * @param session  会话
     * @param timeout  超时时间
     * @param function 回调，是为了保证在执行完成后能自动删除名
     * @param command  命令
     * @throws IOException io
     */
    public static int uploadCommandCallback(Session session, Charset charset, int timeout, Function<String, Integer> function, String command) throws IOException {
        if (StrUtil.isEmpty(command)) {
            return -100;
        }
        ChannelSftp channel = null;
        try {
            Sftp sftp = new Sftp(session, charset, timeout);
            channel = sftp.getClient();
            String tempId = IdUtil.fastSimpleUUID();
            File tmpDir = FileUtil.getTmpDir();
            File buildSsh = FileUtil.file(tmpDir, "ssh_temp", tempId + ".sh");
            try (InputStream sshExecTemplateInputStream = ExtConfigBean.getConfigResourceInputStream("/ssh/template.sh")) {
                String sshExecTemplate = IoUtil.readUtf8(sshExecTemplateInputStream);
                FileUtil.writeString(sshExecTemplate + command + StrUtil.LF, buildSsh, charset);
            }
            // 上传文件
            String path = StrUtil.format("{}/.jpom/", sftp.home());
            String destFile = StrUtil.format("{}{}.sh", path, tempId);
            sftp.mkDirs(path);
            sftp.upload(destFile, buildSsh);
            // 执行命令
            try {
                String commandSh = "bash " + destFile;
                return function.apply(commandSh);
            } finally {
                try {
                    // 删除 ssh 中临时文件
                    sftp.delFile(destFile);
                } catch (Exception e) {
                    log.warn(I18nMessageUtil.get("i18n.delete_ssh_temp_file_failure.6e5f"), e);
                }
                // 删除临时文件
                FileUtil.del(buildSsh);
            }
        } finally {
            JschUtil.close(channel);
        }
    }

    /**
     * 执行命令回调
     *
     * @param session           会话
     * @param charset           字符编码格式
     * @param timeout           超时时间
     * @param lineHandler       消息回调
     * @param command           命令
     * @param commandParamsLine 执行参数
     * @throws IOException io
     */
    public static void execCallbackLine(Session session, Charset charset, int timeout, String command, String commandParamsLine, Map<String, String> env, LineHandler lineHandler) throws IOException {
        String command2 = StringUtil.formatStrByMap(command, env);
        execCallbackLine(session, charset, timeout, command2, commandParamsLine, lineHandler);
    }

    /**
     * 执行命令回调
     *
     * @param session           会话
     * @param charset           字符编码格式
     * @param timeout           超时时间
     * @param lineHandler       消息回调
     * @param command           命令
     * @param commandParamsLine 执行参数
     * @throws IOException io
     */
    public static int execCallbackLine(Session session, Charset charset, int timeout, String command, String commandParamsLine, LineHandler lineHandler) throws IOException {
        //
        return execCallbackLine(session, charset, timeout, command, commandParamsLine, lineHandler, lineHandler);
    }

    /**
     * 执行命令回调
     *
     * @param session           会话
     * @param charset           字符编码格式
     * @param timeout           超时时间
     * @param normal            消息回调
     * @param error             错误消息
     * @param command           命令
     * @param commandParamsLine 执行参数
     * @throws IOException io
     */
    public static int execCallbackLine(Session session, Charset charset, int timeout, String command, String commandParamsLine, LineHandler normal, LineHandler error) throws IOException {
        //
        return JschUtils.uploadCommandCallback(session, charset, timeout, (s) -> {
            ChannelExec channel = (ChannelExec) JschUtil.createChannel(session, ChannelType.EXEC);
            channel.setCommand(StrUtil.bytes(s + StrUtil.SPACE + commandParamsLine, charset));
            channel.setInputStream(null);
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                channel.setErrStream(outputStream, true);
                try (InputStream in = channel.getInputStream()) {
                    // 不添加超时，添加超时后可能存在阻塞
                    channel.connect();
                    IoUtil.readLines(in, charset, normal);
                }
                // 输出错误信息
                int size = outputStream.size();
                if (size > 0) {
                    error.handle(outputStream.toString(charset.name()));
                }
                return channel.getExitStatus();
            } catch (Exception e) {
                throw Lombok.sneakyThrow(e);
            } finally {
                JschUtil.close(channel);
            }

        }, command);
    }
}
