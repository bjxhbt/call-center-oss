<script setup lang="ts">
import { RouterView } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import 'dayjs/locale/zh-cn'

const theme = {
  token: {
    colorPrimary: '#1677ff',
    colorInfo: '#1677ff',
    colorBgLayout: '#f3f6fa',
    colorText: '#1f2937',
    colorTextSecondary: '#667085',
    colorBorder: '#dfe5ec',
    colorBorderSecondary: '#e8edf3',
    borderRadius: 6,
    fontSize: 14,
    controlHeight: 34
  },
  components: {
    Table: {
      headerBg: '#f7f9fc',
      headerColor: '#344054',
      rowHoverBg: '#f7fbff',
      borderColor: '#e8edf3'
    },
    Drawer: {
      colorBgElevated: '#ffffff'
    },
    Tabs: {
      itemColor: '#667085',
      itemSelectedColor: '#1677ff'
    }
  }
}
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="theme">
    <RouterView />
  </a-config-provider>
</template>

<style scoped></style>
