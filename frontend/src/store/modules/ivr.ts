import { defineStore } from 'pinia'
import { store } from '../index'

import playBackImg from '@/assets/ivr/playback.svg'
import menuImg from '@/assets/ivr/menu.svg'
import receiveImg from '@/assets/ivr/receive.svg'
import transferImg from '@/assets/ivr/transfer.svg'
import hangupImg from '@/assets/ivr/hangup.svg'
import satisfactionImg from '@/assets/ivr/satisfaction.svg'
import httpImg from '@/assets/ivr/http.svg'

export const useIvrStore = defineStore('ivr', {
  state: () => {
    return {
      //画布
      lfRef: null,
      //node list
      nodeList: [
        {
          type: 'playback-node',
          name: '放音',
          des: '用于播放音频文件或文字转语音，通常用作开场的欢迎语和模块间的过渡使用',
          bgc: 'linear-gradient(270deg, #9258f7 0%, #3370FF 100%)',
          img: playBackImg,
          businessType: 2
        },
        {
          type: 'menu-node',
          name: '菜单',
          des: '用于引导流程走向，提供操作选项，实现分支处理',
          bgc: '#229dff',
          img: menuImg,
          businessType: 3
        },
        {
          type: 'receive-node',
          name: '收号',
          des: '用于保存用户输入的按键内容，赋值给自定义参数，传递到分支模块进行处理',
          bgc: '#f759ab',
          img: receiveImg,
          businessType: 4
        },
        {
          type: 'transfer-node',
          name: '转接',
          des: '将客户来电转至相应的技能组、外部电话等',
          bgc: '#14c0ff',
          img: transferImg,
          businessType: 5
        },
        {
          type: 'hangup-node',
          name: '挂机',
          des: '流程结束表示',
          bgc: '#0dbf7d',
          img: hangupImg,
          businessType: 6
        },
        {
          type: 'satisfaction-node',
          name: '满意度',
          des: '用于收集用户满意度，提升用户体验',
          bgc: '#9d66e1',
          img: satisfactionImg,
          businessType: 7
        },
        {
          type: 'http-node',
          name: '外部调用',
          des: 'http接口调用访问第三方系统，实现客户的自助服务',
          bgc: '#bebf6e',
          img: httpImg,
          businessType: 8
        }
      ],
      // 画布节点
      nodes: {
        'start-node': {
          id: 'start',
          type: 'start-node',
          x: 400,
          y: 400,
          properties: {
            width: 400,
            height: 300,
            recording: false,
            asrEngine: '',
            ttEngine: '',
            name: '开始',
            businessType: 0
          }
        },
        'end-node': {
          id: 'end',
          type: 'end-node',
          x: 1500,
          y: 400,
          properties: {
            width: 400,
            height: 200,
            hangup: false,
            name: '结束',
            businessType: 1
          }
        },
        'playback-node': {
          id: 'playback_',
          type: 'playback-node',
          x: 850,
          y: 400,
          properties: {
            width: 400,
            height: 370,
            hangup: false,
            name: '放音',
            businessType: 2,
            playbackType: 1,
            fileId: '',
            content: '',
            num: ''
          }
        },
        'menu-node': {
          id: 'menu_',
          type: 'menu-node',
          x: 1200,
          y: 400,
          properties: {
            width: 500,
            height: 660,
            hangup: false,
            name: '菜单',
            businessType: 3,
            interrupt: false,
            playbackType: 1,
            fileId: '',
            content: '',
            menuButtons: [
              {
                key: 0,
                buttonName: '',
                buttonValue: ''
              }
            ],
            notPlaybackType: 1,
            notFileId: '',
            notContent: '',

            errorPlaybackType: 1,
            errorFileId: '',
            errorContent: '',

            timeout: 1000,
            maxRetries: 3
          }
        },
        'receive-node': {
          id: 'receive_',
          type: 'receive-node',
          x: 1200,
          y: 400,
          properties: {
            width: 400,
            height: 660,
            name: '收号',
            businessType: 4,
            interrupt: false,
            playbackType: 1,
            fileId: '',
            content: '',
            numMax: 0,
            numMin: 0,
            endPoint: '#',
            notPlaybackType: 1,
            notFileId: '',
            notContent: '',
            errorPlaybackType: 1,
            errorFileId: '',
            errorContent: '',
            timeout: 1000,
            maxRetries: 3
          }
        },
        'transfer-node': {
          id: 'transfer_',
          type: 'transfer-node',
          x: 1200,
          y: 400,
          properties: {
            width: 400,
            height: 260,
            name: '转接',
            businessType: 5,
            routeType: 1,
            routeValue: ''
          }
        },
        'hangup-node': {
          id: 'hangup_',
          type: 'hangup-node',
          x: 1200,
          y: 400,
          properties: {
            width: 400,
            height: 100,
            name: '挂机',
            businessType: 6
          }
        },
        'satisfaction-node': {
          id: 'satisfaction_',
          type: 'satisfaction-node',
          x: 1200,
          y: 400,
          properties: {
            width: 400,
            height: 100,
            name: '满意度',
            businessType: 7
          }
        },
        'http-node': {
          id: 'http_',
          type: 'http-node',
          x: 1200,
          y: 400,
          properties: {
            width: 500,
            height: 480,
            name: '外部调用',
            businessType: 8,
            method: 1,
            params: '',
            result: '',
            header: [
              {
                key: 0,
                name: '',
                value: ''
              }
            ]
          }
        }
      }
    }
  },
  getters: {
    getLf(): any { return this.lfRef },
    getNodeList(): any { return this.nodeList },
    getNodes(): any { return this.nodes }
  },
  actions: {
    async setLfRef(value: any) {
      this.lfRef = value
    },
    async setNodes(keys: any, value: Object) {
      this.nodes[keys] = value
    }
  }
})

export const useIvrStoreWithOut = () => {
  return useIvrStore(store)
}
