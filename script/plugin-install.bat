@REM
@REM Copyright (c) 2019 Code Technology Studio
@REM Jpom is licensed under Mulan PSL v2.
@REM You can use this software according to the terms and conditions of the Mulan PSL v2.
@REM You may obtain a copy of Mulan PSL v2 at:
@REM 			http://license.coscl.org.cn/MulanPSL2
@REM THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
@REM See the Mulan PSL v2 for more details.
@REM

mvn clean install -Pinstall-plugin-profile

mvn clean install -DskipTests=true deploy -Pinstall-plugin-profile



mvn clean install -DskipTests=true deploy -Prelease-plugin-profile