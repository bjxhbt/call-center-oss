export const callingConfigureRoutes = [
  {
    path: '/agentManagement',
    name: '',
    meta: {
      title: '坐席管理'
    },
    component: () => import('@/views/CallingConfigure/AgentManagement/index.vue')
  },
  {
    path: '/phoneNumberRoute',
    name: '',
    meta: {
      title: '号码路由管理'
    },
    component: () => import('@/views/CallingConfigure/PhoneNumberRoute/index.vue')
  },
  {
    path: '/sipPhoneNumber',
    name: '',
    meta: {
      title: 'SIP号码管理'
    },
    component: () => import('@/views/CallingConfigure/SIPPhoneNumber/index.vue')
  },
  {
    path: '/phoneNumber',
    name: '',
    meta: {
      title: '号码管理'
    },
    component: () => import('@/views/CallingConfigure/PhoneNumber/index.vue')
  },
  {
    path: '/phonePool',
    name: '',
    meta: {
      title: '号码池管理'
    },
    component: () => import('@/views/CallingConfigure/PhonePool/index.vue')
  },
  {
    path: '/voiceFile',
    name: '',
    meta: {
      title: '语音文件管理'
    },
    component: () => import('@/views/CallingConfigure/VoiceFileManagement/index.vue')
  },
  {
    path: '/callRecordManagement',
    name: '',
    meta: {
      title: '话单管理'
    },
    component: () => import('@/views/CallingConfigure/CallRecordManagement/index.vue')
  },
  {
    path: '/scheduleManagement',
    name: '',
    meta: {
      title: '日程管理'
    },
    component: () => import('@/views/CallingConfigure/ScheduleManagement/index.vue')
  },
  {
    path: '/skillGroupManagement',
    name: '',
    meta: {
      title: '技能组管理'
    },
    component: () => import('@/views/CallingConfigure/SkillManagement/index.vue')
  },
  {
    path: '/aiEngineManagement',
    name: '',
    meta: {
      title: 'AI引擎管理'
    },
    component: () => import('@/views/CallingConfigure/AIEngineManagement/index.vue')
  },
  {
    path: '/recordingManagement',
    name: '',
    meta: {
      title: '录音管理'
    },
    component: () => import('@/views/CallingConfigure/RecordingManagement/index.vue')
  },
  {
    path: '/storageManagement',
    name: '',
    meta: {
      title: '存储管理'
    },
    component: () => import('@/views/CallingConfigure/StorageManagement/index.vue')
  },
  {
    path: '/realtimeMonitor',
    name: '',
    meta: {
      title: '实时监控'
    },
    component: () => import('@/views/CallingConfigure/RealtimeMonitor/index.vue')
  },
  {
    path: '/workOrder',
    name: '',
    meta: {
      title: '工单管理'
    },
    component: () => import('@/views/CallingConfigure/WorkOrder/index.vue')
  },
  {
    path: '/holidayManagement',
    name: '',
    meta: { title: '假期管理' },
    component: () => import('@/views/CallingConfigure/HolidayManagement/index.vue')
  }
]
