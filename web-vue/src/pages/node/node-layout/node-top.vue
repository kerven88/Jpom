<template>
  <div>
    <div ref="filter" class="filter">
      <a-space>
        <a-range-picker
          v-model:value="timeRange"
          :presets="[
            { label: '今天', value: [dayjs().startOf('day'), dayjs()] },
            { label: '昨天', value: [dayjs().add(-1, 'days').startOf('day'), dayjs().add(-1, 'days').endOf('day')] }
          ]"
          :disabled-date="
            (current) => {
              return current && current >= dayjs().endOf('day')
            }
          "
          class="filter-item"
          :show-time="{ format: 'HH:mm:ss' }"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
        <a-button type="primary" @click="handleFilter">搜索</a-button>
        <a-tooltip>
          <template #title>
            <div>
              <ul>
                <li>
                  如果在 Linux 中实际运行内存可能和您直接使用 free -h 命令查询到 free 和 total
                  字段计算出数值相差过大那么此时就是您当前服务器中的交换内存引起的
                </li>
                <li>系统 采用 oshi 库来监控系统，在 oshi 中使用 /proc/meminfo 来获取内存使用情况。</li>
                <li>
                  文件中如果存在：MemAvailable、MemTotal 这两个字段，那么 oshi 直接使用，所以本系统
                  中内存占用计算方式：内存占用=(total-available)/total
                </li>
                <li>
                  文件中如果不存在：MemAvailable，那么 MemAvailable =
                  MemFree+Active(file)+Inactive(file)+SReclaimable，所以本系统
                  中内存占用计算方式：内存占用=(total-(MemFree+Active(file)+Inactive(file)+SReclaimable))/total
                </li>
              </ul>
            </div>
          </template>
          <QuestionCircleOutlined />
        </a-tooltip>
      </a-space>
    </div>
    <div v-if="nodeMonitorLoadStatus == 1" id="historyChart" class="historyChart">loading...</div>
    <a-empty
      v-else-if="nodeMonitorLoadStatus == -1"
      :image="Empty.PRESENTED_IMAGE_SIMPLE"
      description="未查询到任何数据"
    >
    </a-empty>
    <a-skeleton v-else />
  </div>
</template>

<script>
import { nodeMonitorData } from '@/api/node'
import { drawChart, generateNodeTopChart, generateNodeNetworkTimeChart, generateNodeNetChart } from '@/api/node-stat'
import dayjs from 'dayjs'
import { useGuideStore } from '@/stores/guide'
import { mapState } from 'pinia'
import { Empty } from 'ant-design-vue'
export default {
  components: {},
  props: {
    nodeId: {
      type: String,
      default: ''
    },
    machineId: {
      type: String,
      default: ''
    },
    type: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      Empty,
      timeRange: null,
      historyData: [],
      historyChart: null,
      nodeMonitorLoadStatus: 0
    }
  },
  computed: {
    ...mapState(useGuideStore, ['getThemeView'])
  },
  watch: {},
  mounted() {
    this.handleFilter()
    window.addEventListener('resize', this.resize)
  },
  unmounted() {
    window.removeEventListener('resize', this.resize)
  },
  methods: {
    dayjs,
    // 刷新
    handleFilter() {
      const params = {
        nodeId: this.nodeId,
        machineId: this.machineId
        // time: this.timeRange
      }
      if (this.timeRange && this.timeRange[0]) {
        params.startTime = this.timeRange[0]
        params.endTime = this.timeRange[1]
      } else {
        params.startTime = ''
        params.endTime = ''
      }
      // 加载数据
      nodeMonitorData(params)
        .then((res) => {
          if (res.code === 200) {
            if (res.data && res.data.length) {
              this.nodeMonitorLoadStatus = 1
              this.$nextTick(() => {
                if (this.type === 'networkDelay') {
                  this.historyChart = drawChart(
                    res.data,
                    'historyChart',
                    generateNodeNetworkTimeChart,
                    this.getThemeView()
                  )
                } else if (this.type === 'network-stat') {
                  this.historyChart = drawChart(res.data, 'historyChart', generateNodeNetChart, this.getThemeView())
                } else {
                  this.historyChart = drawChart(res.data, 'historyChart', generateNodeTopChart, this.getThemeView())
                }
              })

              return
            }
          }
          this.nodeMonitorLoadStatus = -1
        })
        .catch(() => {
          this.nodeMonitorLoadStatus = -1
        })
    },
    resize() {
      this.historyChart?.resize()
    }
  }
}
</script>

<style scoped>
.historyChart {
  height: 50vh;
  margin-top: 10px;
}
</style>
