<template>
  <Teleport to="#home-mobile-navigation" :disabled="!isMobilePreview" defer>
    <nav class="home-navigation page-header-actions" aria-label="页面导航">
      <el-button
        :disabled="isGithubPagesBuild"
        :title="isGithubPagesBuild ? '演示版暂不支持余票监控' : ''"
        @click="goToMonitor"
      >余票监控</el-button>
      <el-button
        :disabled="isGithubPagesBuild"
        :title="isGithubPagesBuild ? '演示版暂不支持车次查询' : ''"
        @click="goToTrainQuery"
      >车次查询</el-button>
      <el-button
        :disabled="isGithubPagesBuild"
        :title="isGithubPagesBuild ? '演示版暂不支持车站大屏' : ''"
        @click="goToStation"
      >车站大屏</el-button>
      <el-button :disabled="isGithubPagesBuild" @click="goToHistory()">
        <el-icon><User /></el-icon>历史记录
      </el-button>
    </nav>
  </Teleport>
  <el-container class="main-layout">
    <el-aside width="350px" class="main-aside">
      <h2
        style="margin-bottom: 20px; font-family: 'Roboto', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif"
      >Sam-Lab CR Ticket Maker</h2>
      <el-form
          :model="ticket"
          :rules="rules"
          ref="formRef"
          label-width="100px"
          label-position="left"
      >
        <el-form-item label="票号" :required="true">
          <el-input v-model="ticket.number" placeholder="例如：E351822734"></el-input>
        </el-form-item>

        <el-form-item label="起点" :required="true">
          <el-autocomplete
              v-model="ticket.from"
              :fetch-suggestions="querySearch"
              placeholder="输入站名或拼音"
              clearable
              @select="handleSelect('from', $event)"
          />
        </el-form-item>

        <el-form-item label="终点" :required="true">
          <el-autocomplete
              v-model="ticket.to"
              :fetch-suggestions="querySearch"
              placeholder="输入站名或拼音"
              clearable
              @select="handleSelect('to', $event)"
          />
        </el-form-item>

        <el-form-item label="车次" prop="trainNo" :required="true">
          <el-input
              v-model="ticket.trainNo"
              placeholder="例如：G25 或 1461"
          />
        </el-form-item>

        <el-form-item label="开车日期" :required="true">
          <el-date-picker
              v-model="ticket.date"
              type="date"
              placeholder="请选择乘车日期"
              format="YYYY/MM/DD"
              value-format="YYYY年MM月DD日"
          />
        </el-form-item>

        <el-form-item label="开车时间" :required="true">
          <el-time-picker
              v-model="ticket.time"
              placeholder="选择开车时间"
              format="HH:mm"
              value-format="HH:mm"
          />
        </el-form-item>

        <el-form-item label="票价" :required="true">
          <el-input-number v-model="ticket.price" placeholder="请输入数字" :min="0" :step="0.5">
            <template #prefix>
              <span>￥</span>
            </template>
          </el-input-number>
        </el-form-item>

        <el-form-item label="使用积分">
          <el-switch
              v-model="credit"
              inline-prompt
              :active-icon="Check"
              :inactive-icon="Close"
          />
        </el-form-item>

        <el-form-item label="席位名称" :required="true">
          <el-select
              v-model="ticket.seatType"
              placeholder="Select"
          >
            <el-option
                v-for="item in options"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="空调选择">
          <el-switch
              v-model="value3"
              :disabled="airSwitchDisabled"
              inline-prompt
              :active-icon="Check"
              :inactive-icon="Close"
          />
        </el-form-item>

        <el-form-item label="座位号" :required="true">
          <el-input v-model="ticket.seatNo" placeholder="03车12A号"></el-input>
        </el-form-item>

        <el-form-item label="售票地点" :required="true">
          <el-input v-model="ticket.sellPlace" placeholder="XX站"></el-input>
        </el-form-item>

        <el-form-item label="检票/候车">
          <el-input v-model="ticket.gate" placeholder="检票：1A / 候车：一候"></el-input>
        </el-form-item>

        <el-form-item label="提示语" :required="true">
          <el-input
              v-model="ticket.message"
              :rows="3"
              type="textarea"
              placeholder="输入提示语"
          />
        </el-form-item>

        <el-form-item label="选择背景" :required="true">
          <el-select
              v-model="ticket.theme"
              placeholder="请选择主题"
              style="width: 240px"
          >
            <el-option
                v-for="item in themeOptions"
                :key="item.id"
                :label="item.label"
                :value="item.id"
                :disabled="item.disabled"
            />
          </el-select>
        </el-form-item>

        <el-form-item
            label="里程"
            prop="distance"
            :required="true"
            :rules="distanceRules"
        >
          <el-tooltip
              content="点击页面导航中的“车次查询”可获取里程"
              placement="top"
          >
            <el-input-number
                v-model="ticket.distance"
                class="mileage-input"
                :min="0"
                placeholder="请输入里程"
                aria-label="运转里程（km）"
            >
              <template #suffix>
                <span>km</span>
              </template>
            </el-input-number>
          </el-tooltip>
        </el-form-item>

      </el-form>
      <h3 style="font-family: Consolas, 'Courier New', monospace">Version 261013</h3>
      <h3 style="font-family: Consolas, 'Courier New', monospace">Station Version 10118</h3>

      <div class="home-tools">
        <div id="home-mobile-navigation" class="home-mobile-navigation"></div>
        <div class="home-info-actions">
          <el-button type="primary" @click="note">更新内容</el-button>
          <el-button color="black" @click="goToGitHub()"><el-icon><Star /></el-icon><span>GitHub</span></el-button>
        </div>
      </div>

    </el-aside>

    <el-main class="main-center">
      <el-card class="ticket-preview" shadow="always">
        <div ref="previewShell" class="ticket-preview-shell" :style="{ height: `${ticketScaledHeight}px` }">
          <div class="ticket-preview-scale" :style="{ transform: `scale(${ticketScale})` }">
            <div ref="ticketRef" class="ticket-container">
            <div class="ticket-bg" :style="{ backgroundImage: `url(./${ticket.theme})` }">
              <div class="ticket-number">{{ticket.number}}</div>
            </div>

            <div class="station-block">
              <div class="station-left">
                <div class="station-name">{{ formatStationName(ticket.from) }}<span class="small">站</span></div>
                <div class="station-en">{{ getStationEnglish(ticket.from) }}</div>
              </div>

              <div class="station-middle">
                <div class="train-no">{{ ticket.trainNo }}</div>
                <div class="arrow">
                  <span class="line"></span>
                  <span class="head"></span>
                </div>
              </div>

              <div class="station-right">
                <div class="station-name">{{ formatStationName(ticket.to) }}<span class="small">站</span></div>
                <div class="station-en">{{ getStationEnglish(ticket.to) }}</div>
              </div>
            </div>

            <div class="ticket-gate">{{ticket.gate}}</div>

            <div class="date-time">{{ticket.date}}&nbsp;&nbsp; {{ticket.time}}开</div>

            <div class="price">￥{{facePrice}}元</div>

            <div class="tip"><strong>限乘当日当次车</strong></div>

            <div class="seat">{{ticket.seatNo}}</div>

            <div class="seat-class">{{finalSeatType}}</div>

            <div class="ticket-message">
              <p>{{ ticket.message }}</p>
            </div>

            <div class="ticket-type">{{ specialTicketType }}</div>
            <div class="credit">{{ useCredit}}</div>

            <div class="ticket-qrcode" v-if="qrCodeUrl">
              <img :src="qrCodeUrl" alt="QR Code" />
            </div>

            <div class="sell-place">{{ticket.sellPlace}}售</div>
          </div>

          </div>
        </div>
        <div class="download-buttons">
          <el-button type="primary" :loading="isSavingTicket" @click="downloadPDF">
            <el-icon><Document /></el-icon>
            保存 PDF
          </el-button>

          <el-button type="success" :disabled="isSavingTicket" @click="downloadPNG">
            <el-icon><Picture /></el-icon>
            保存图片
          </el-button>
          <el-button :disabled="isSavingTicket" @click="generator.clear">清空</el-button>
        </div>
      </el-card>

    </el-main>
  </el-container>
</template>

<script setup>
import {computed, h, onMounted, onUnmounted, reactive, ref, watch} from 'vue'
import {ElButton, ElIcon, ElMessage, ElMessageBox, ElNotification} from 'element-plus'
import QRCode from 'qrcode'


import {Check, Close, Document, Picture, Star, User} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useTicketGenerator } from '@/composables/useTicketGenerator.js'
import { useUserStore } from '@/stores/user.js'
import { isGithubPagesBuild } from '@/config/deploy.js'
import { native } from '@/android/platform.js'
import {
  createDefaultTicket,
  seatOptions,
  themeOptions,
  ticketRules,
  ticketQRCodeOptions,
  ticketFacePrice,
} from '@/utils/ticketShared.js'
import {
  formatStationName as formatTicketStationName,
  getStationEnglish as getTicketStationEnglish,
  handleStationSelect,
  queryStationSearch,
  useSeatType,
} from '@/composables/useTicketShared.js'

const userStore = useUserStore()
userStore.init()

const ticket = reactive(createDefaultTicket())
const formRef = ref(null)
const rules = ticketRules
const options = seatOptions

const {
  value3,
  credit,
  airSwitchDisabled,
  finalSeatType,
  specialTicketType,
  useCreditLabel,
} = useSeatType(ticket)

const useCredit = computed(() => useCreditLabel.value)
const facePrice = computed(() => ticketFacePrice(ticket.price, credit.value))
const querySearch = queryStationSearch
const handleSelect = (field, item) => handleStationSelect(ticket, field, item)
const formatStationName = (name) => formatTicketStationName(name)
const getStationEnglish = (name) => getTicketStationEnglish(name)

const distanceRules = [
  {
    required: true,
    type: 'number',
    min: 0,
    message: '请输入有效的里程（km）',
    trigger: ['blur', 'change'],
  },
]

const qrCodeUrl = ref('')

async function generateQRCode(text) {
  if (!text) {
    qrCodeUrl.value = ''
    return
  }
  const url = await QRCode.toDataURL(text, ticketQRCodeOptions()).catch(() => '')
  if (ticket.number === text) qrCodeUrl.value = url
}

watch(() => ticket.number, (newVal) => {
  generateQRCode(newVal)
})

const note = () => {
  ElNotification({
    title: '新增车次列表、列车参考地图、车站大屏；Android软件开始测试',
    message: '现在您可以查看全部车次的列表，并查看列车运行参考地图。点击车站名称可以跳转至车站大屏和参考地图。Android端软件已经准备好，可供测试。',
    type: 'info',
    position: 'bottom-right'
  })
}

const star = () => {
  ElNotification({
    title: '我们的项目需要更多星标！',
    dangerouslyUseHTMLString: true,
    message: h('div', null, [
      h('p', { style: 'margin-bottom: 10px;' }, '请点击下面的图标，为我们的项目标一个免费的星星⭐！'),
      h(
          ElButton,
          {
            color: 'black',
            onClick: goToGitHub
          },
          {
            default: () => [
              h(ElIcon, null, { default: () => h(Star) }),
              h('span', { style: 'margin-left: 4px;' }, 'GitHub')
            ]
          }
      )
    ]),
    offset: 30,
    duration: 0,
  })
}

const ticketRef = ref(null)
const previewShell = ref(null)
let previewObserver

const TICKET_BASE_WIDTH = 430
const TICKET_BASE_HEIGHT = Math.round(TICKET_BASE_WIDTH * 54 / 86)
const ticketScale = ref(1)
const ticketScaledHeight = computed(() => Math.round(TICKET_BASE_HEIGHT * ticketScale.value))

function updateTicketScale() {
  const viewportWidth = window.innerWidth || TICKET_BASE_WIDTH
  const availableWidth = previewShell.value?.clientWidth || viewportWidth - 24
  const nextScale = Math.min(1, availableWidth / TICKET_BASE_WIDTH)
  ticketScale.value = Number.isFinite(nextScale) && nextScale > 0 ? nextScale : 1
}

const isMobilePreview = ref(false)

function updateMobilePreviewFlag() {
  const viewportWidth = window.innerWidth || 1024
  isMobilePreview.value = viewportWidth <= 768
}

onMounted(() => {
  generateQRCode(ticket.number)
  updateTicketScale()
  updateMobilePreviewFlag()
  previewObserver = new ResizeObserver(updateTicketScale)
  previewObserver.observe(previewShell.value)
  window.addEventListener('resize', updateTicketScale, { passive: true })
  window.addEventListener('resize', updateMobilePreviewFlag, { passive: true })
  if (!native && !sessionStorage.getItem('notified')) {
    note()
    star()
    sessionStorage.setItem('notified', '1')
  }
})

onUnmounted(() => {
  previewObserver?.disconnect()
  window.removeEventListener('resize', updateTicketScale)
  window.removeEventListener('resize', updateMobilePreviewFlag)
})

const generator = useTicketGenerator({
  ticket, credit, conditioner: value3, ticketRef,
  owner: () => JSON.parse(localStorage.getItem('user') || 'null'),
  prepare: () => generateQRCode(ticket.number),
})
const isSavingTicket = generator.busy

async function saveGeneratedTicket(format) {
  try {
    const result = await generator.saveFormat(format)
    if (result?.exported) ElMessage.success('车票已保存到账户并导出')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || '保存失败，请重试')
  }
}
const downloadPDF = () => saveGeneratedTicket('pdf')
const downloadPNG = () => saveGeneratedTicket('image')

const router = useRouter()
const goToMonitor = () => {
  if (isGithubPagesBuild) return
  router.push('/monitor')
}
const goToTrainQuery = () => {
  if (isGithubPagesBuild) return
  router.push('/train')
}
const goToStation = () => {
  if (isGithubPagesBuild) return
  router.push('/station')
}
const goToHistory = () => {
  if (isGithubPagesBuild) return
  router.push('/history')
}
const goToGitHub = () => {
  ElMessageBox.alert(
      '<strong>谢谢您为我的项目标星！</strong>' +
      '<img src="GitHub.gif" alt="GitHub" style="max-width: 100%; height: auto;"/>',
      '访问本项目源代码',
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '前往GitHub',

      }
  )
      .then(() => {
        window.open('https://github.com/sam28403/CRTicket', '_blank')
      })
}

</script>

<style scoped>
@import "../assets/styles/App.css";

.home-navigation { position: absolute; top: 1%; right: 1%; display: flex; z-index: 2; }
.home-mobile-navigation { display: none; }
.home-info-actions { display: flex; gap: 8px; }
.home-info-actions .el-button + .el-button { margin-left: 0; }
@media (max-width: 768px) {
  .home-tools {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid var(--el-border-color-light);
  }

  .home-mobile-navigation,
  .home-navigation,
  .home-info-actions { display: contents; }

  .home-tools :deep(.theme-select) {
    width: 100%;
    min-width: 0;
  }

  .home-tools :deep(.el-select__wrapper) {
    min-height: 40px;
    border-radius: 8px;
  }

  .home-tools .el-button {
    width: 100%;
    min-width: 0;
    height: 40px;
    margin: 0;
    border-radius: 8px;
  }

  .home-tools .el-button .el-icon { margin-right: 4px; }
}

.main-layout {
  height: 100vh;
}

.main-aside {
  background: var(--app-surface, #f8f9fa);
  padding: 20px;
}

.mileage-input {
  width: 100%;
}

.ticket-preview-shell {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.ticket-preview {
  width: 100%;
  max-width: 470px;
  box-sizing: border-box;
}

.ticket-preview :deep(.el-card__body) {
  width: 100%;
  box-sizing: border-box;
}

.ticket-preview-scale {
  width: 430px;
  flex-shrink: 0;
  transform-origin: top center;
}

@media (max-width: 768px) {
  .main-layout {
    height: auto;
    min-height: 100vh;
    flex-direction: column;
  }

  .main-aside {
    width: 100% !important;
    padding: 14px 12px;
  }

  .main-center {
    padding: 16px 12px;
  }

}
</style>
