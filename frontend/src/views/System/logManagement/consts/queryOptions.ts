import { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '日志标题',
    name: 'title',
    type: 'input'
  },
  {
    label: '操作人员',
    name: 'name',
    type: 'input'
  },
  {
    label: '操作地址',
    name: 'path',
    type: 'input'
  },
  {
    label: '日志状态',
    name: 'status',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: '正常',
        value: 0
      },
      {
        label: '异常',
        value: 1
      }
    ]
  },
  {
    label: '日志类型',
    name: 'type',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        value: '',
        label: '全部'
      },
      {
        value: 1,
        label: '新增'
      },
      {
        value: 2,
        label: '修改'
      },
      {
        value: 3,
        label: '删除'
      },
      {
        value: 4,
        label: '查询'
      },
      {
        value: 5,
        label: '导出'
      },
      {
        value: 6,
        label: '导入'
      },
      {
        value: 7,
        label: '登录'
      },
      {
        value: 8,
        label: '登出'
      },
      {
        value: 0,
        label: '其它'
      }
    ]
  }
]
