<template>
  <div>
    <a-form
      ref="releaseFileForm"
      :rules="releaseFileRules"
      :model="temp"
      :label-col="{ span: 4 }"
      :wrapper-col="{ span: 20 }"
    >
      <a-form-item label="任务名" name="name">
        <a-input v-model:value="temp.name" placeholder="请输入任务名" :max-length="50" />
      </a-form-item>

      <a-form-item label="发布方式" name="taskType">
        <a-radio-group v-model:value="temp.taskType" @change="taskTypeChange">
          <a-radio :value="0"> SSH </a-radio>
          <a-radio :value="1"> 节点 </a-radio>
        </a-radio-group>
        <template #help>
          <template v-if="temp.taskType === 0"
            >发布后的文件名是：文件ID.后缀，并非文件真实名称 （可以使用上传后脚本随意修改）
          </template>
        </template>
      </a-form-item>

      <a-form-item v-if="temp.taskType === 0" name="taskDataIds" label="发布的SSH">
        <a-row>
          <a-col :span="22">
            <a-select
              v-model:value="temp.taskDataIds"
              show-search
              :filter-option="
                (input, option) => {
                  const children = option.children && option.children()
                  return (
                    children &&
                    children[0].children &&
                    children[0].children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                  )
                }
              "
              mode="multiple"
              placeholder="请选择SSH"
            >
              <a-select-option v-for="ssh in sshList" :key="ssh.id">
                <a-tooltip :title="ssh.name"> {{ ssh.name }}</a-tooltip>
              </a-select-option>
            </a-select>
          </a-col>
          <a-col :span="1" style="margin-left: 10px">
            <ReloadOutlined @click="loadSshList" />
          </a-col>
        </a-row>
      </a-form-item>
      <a-form-item v-else-if="temp.taskType === 1" name="taskDataIds" label="发布的节点">
        <a-row>
          <a-col :span="22">
            <a-select
              v-model:value="temp.taskDataIds"
              show-search
              :filter-option="
                (input, option) => {
                  const children = option.children && option.children()
                  return (
                    children &&
                    children[0].children &&
                    children[0].children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                  )
                }
              "
              mode="multiple"
              placeholder="请选择节点"
            >
              <a-select-option v-for="ssh in nodeList" :key="ssh.id">
                <a-tooltip :title="ssh.name"> {{ ssh.name }}</a-tooltip>
              </a-select-option>
            </a-select>
          </a-col>
          <a-col :span="1" style="margin-left: 10px">
            <ReloadOutlined @click="loadNodeList" />
          </a-col>
        </a-row>
      </a-form-item>

      <a-form-item name="releasePathParent" label="发布目录">
        <template #help>
          <a-tooltip title="需要配置授权目录（授权才能正常使用发布）,授权目录主要是用于确定可以发布到哪些目录中"
            ><a-button
              size="small"
              type="link"
              @click="
                () => {
                  configDir = true
                }
              "
            >
              <InfoCircleOutlined />配置目录
            </a-button>
          </a-tooltip>
        </template>
        <a-input-group compact>
          <a-select
            v-model:value="temp.releasePathParent"
            show-search
            allow-clear
            style="width: 30%"
            placeholder="请选择发布的一级目录"
          >
            <a-select-option v-for="item in accessList" :key="item">
              <a-tooltip :title="item">{{ item }}</a-tooltip>
            </a-select-option>
            <template #suffixIcon>
              <ReloadOutlined @click="loadAccesList" />
            </template>
          </a-select>
          <a-form-item-rest>
            <a-input v-model:value="temp.releasePathSecondary" style="width: 70%" placeholder="请填写发布的二级目录" />
          </a-form-item-rest>
        </a-input-group>
      </a-form-item>

      <a-form-item name="releaseBeforeCommand">
        <template #label>
          执行脚本
          <a-tooltip>
            <template #title>
              <ul>
                <li>支持变量引用：${TASK_ID}、${FILE_ID}、${FILE_NAME}、${FILE_EXT_NAME}</li>
                <li>可以引用工作空间的环境变量 变量占位符 ${xxxx} xxxx 为变量名称</li>
                <li>建议在上传后的脚本中对文件进行自定义更名，SSH 上传默认为：${FILE_ID}.${FILE_EXT_NAME}</li>
              </ul>
            </template>
            <QuestionCircleOutlined />
          </a-tooltip>
        </template>
        <template #help>
          <div v-if="scriptTabKey === 'before'">文件上传前需要执行的脚本(非阻塞命令)</div>
          <div v-else-if="scriptTabKey === 'after'">文件上传成功后需要执行的脚本(非阻塞命令)</div>
        </template>
        <a-form-item-rest>
          <a-tabs v-model:activeKey="scriptTabKey" tab-position="right" type="card">
            <a-tab-pane key="before" tab="上传前">
              <code-editor
                v-model:content="temp.beforeScript"
                height="40vh"
                :show-tool="true"
                :options="{
                  mode: 'shell'
                }"
              >
                <template #tool_before>
                  <a-tag><b>上传前</b>执行</a-tag>
                </template>
              </code-editor>
            </a-tab-pane>
            <a-tab-pane key="after" tab="上传后">
              <code-editor
                v-model:content="temp.afterScript"
                height="40vh"
                :show-tool="true"
                :options="{
                  mode: 'shell'
                }"
              >
                <template #tool_before> <a-tag>上传后执行</a-tag></template>
              </code-editor>
            </a-tab-pane>
          </a-tabs>
        </a-form-item-rest>
      </a-form-item>
    </a-form>

    <a-modal
      v-model:value="configDir"
      destroy-on-close
      :title="`配置授权目录`"
      :footer="null"
      :mask-closable="false"
      @cancel="
        () => {
          configDir = false
        }
      "
    >
      <whiteList
        v-if="configDir"
        @cancel="
          () => {
            configDir = false
            loadAccesList()
          }
        "
      ></whiteList>
    </a-modal>
  </div>
</template>

<script>
import { getSshListAll } from '@/api/ssh'
import { getDispatchWhiteList } from '@/api/dispatch'
import { getNodeListAll } from '@/api/node'
import codeEditor from '@/components/codeEditor'
import whiteList from '@/pages/dispatch/white-list.vue'
export default {
  components: {
    codeEditor,
    whiteList
  },
  emits: ['commit'],
  data() {
    return {
      temp: {},
      releaseFileRules: {
        name: [{ required: true, message: '请输入文件任务名', trigger: 'blur' }],
        taskType: [{ required: true, message: '请选择发布方式', trigger: 'blur' }],
        releasePath: [
          {
            required: true,
            message: '请选择发布的一级目录和填写二级目录',
            trigger: 'blur'
          }
        ],
        taskDataIds: [{ required: true, message: '请选择发布的SSH', trigger: 'blur' }]
      },
      sshList: [],
      accessList: [],
      nodeList: [],
      configDir: false,
      scriptTabKey: 'before'
    }
  },
  created() {
    this.temp = { taskType: 0 }
    this.taskTypeChange(0)
    this.loadAccesList()
  },
  methods: {
    taskTypeChange() {
      const value = this.temp.taskType
      this.temp = { ...this.temp, taskDataIds: undefined }
      if (value === 0) {
        this.loadSshList()
      } else if (value === 1) {
        this.loadNodeList()
      }
    },
    // 创建任务
    tryCommit() {
      this.$refs['releaseFileForm'].validate().then(() => {
        this.$emit('commit', {
          ...this.temp,
          taskDataIds: this.temp.taskDataIds?.join(',')
        })
      })
    },
    // 加载项目授权列表
    loadAccesList() {
      getDispatchWhiteList().then((res) => {
        if (res.code === 200) {
          this.accessList = res.data.outGivingArray || []
        }
      })
    },
    // 加载 SSH 列表
    loadSshList() {
      return new Promise((resolve) => {
        this.sshList = []
        getSshListAll().then((res) => {
          if (res.code === 200) {
            this.sshList = res.data
            resolve()
          }
        })
      })
    },
    // 加载节点
    loadNodeList() {
      getNodeListAll().then((res) => {
        if (res.code === 200) {
          this.nodeList = res.data
        }
      })
    }
  }
}
</script>
<style scoped>
:deep(.ant-tabs-tabpane) {
  padding-right: 0 !important;
}
</style>
