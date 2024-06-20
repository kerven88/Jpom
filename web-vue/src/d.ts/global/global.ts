///
/// Copyright (c) 2019 Of Him Code Technology Studio
/// Jpom is licensed under Mulan PSL v2.
/// You can use this software according to the terms and conditions of the Mulan PSL v2.
/// You may obtain a copy of Mulan PSL v2 at:
/// 			http://license.coscl.org.cn/MulanPSL2
/// THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
/// See the Mulan PSL v2 for more details.
///

import { GlobalWindow } from '@/interface/common'
import { message, notification, Modal } from 'ant-design-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useGuideStore } from '@/stores/guide'
import { ModalFuncProps } from 'ant-design-vue/es/modal/Modal'
import { increaseZIndex } from '@/utils/utils'

export const jpomWindow = () => {
  return window as unknown as GlobalWindow
}
// 注册全局的组件
export const $message = message
export const $notification = notification
//
export const $confirm = (props: ModalFuncProps) => {
  return Modal.confirm({ ...props, zIndex: increaseZIndex() })
}
export const $info = (props: ModalFuncProps) => {
  return Modal.info({ ...props, zIndex: increaseZIndex() })
}
export const $error = (props: ModalFuncProps) => {
  return Modal.error({ ...props, zIndex: increaseZIndex() })
}
export const $warning = (props: ModalFuncProps) => {
  return Modal.warning({ ...props, zIndex: increaseZIndex() })
}
export const $success = (props: ModalFuncProps) => {
  return Modal.success({ ...props, zIndex: increaseZIndex() })
}
// export const $route = useRoute()
// export const $router = useRouter()

$notification.config({
  top: '100px',
  duration: 4
})

$message.config({ duration: 4 })

export const appStore = () => {
  return useAppStore()
}

export const userStore = () => {
  return useUserStore()
}

export const guideStore = () => {
  return useGuideStore()
}

export const router = () => {
  return useRouter()
}

export const route = () => {
  return useRoute()
}
