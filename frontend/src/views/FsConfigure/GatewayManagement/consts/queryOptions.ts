  import { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '网关名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '网关类型',
    name: 'gatewayType',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: '非外线',
        value: 0
      },
      {
        label: '外线',
        value: 1
      }
    ]
  },
  {
    label: '认证地址',
    name: 'realm',
    type: 'input'
  },
  {
    label: '代理地址',
    name: 'proxy',
    type: 'input'
  },
  {
    label: '注册类型',
    name: 'register',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: '不注册',
        value: 0
      },
      {
        label: '注册',
        value: 1
      }
    ]
  },
  {
    label: '注册协议',
    name: 'transport',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: 'UDP',
        value: 1
      },
      {
        label: 'TCP',
        value: 2
      }
    ]
  },
  {
    label: '网关类型',
    name: 'type',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: 'Internal',
        value: 1
      },
      {
        label: 'External',
        value: 2
      }
    ]
  }
]
