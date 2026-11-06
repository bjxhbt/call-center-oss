<script lang="ts" setup>
import { QueryOptionsConfig } from '@/consts/models'
import { SearchOutlined } from '@ant-design/icons-vue'
import DateTimeRange from './components/DatetimeRange/index.vue'
const props = defineProps({
  queryOptions: {
    type: Array as () => QueryOptionsConfig[],
    default: () => []
  },
  query: {
    type: Object,
    default: () => ({})
  }
})
const dateRange = ref<string[]>([])
const emit = defineEmits(['search'])
const handleSearch = () => {
  emit('search', props.query)
}
const queryKey = (key: string | Array<string>) => {
  if (Array.isArray(key)) {
    return ''
  }
  return key
}
const handleChangeDateRange = (val: string[]) => {
  const keyNames = props.queryOptions.find(
    (item: QueryOptionsConfig) => item.type === 'dateRange'
  )?.name
  const dates = val ? val : ['', '']
  if (!Array.isArray(keyNames)) return
  const concatObj = keyNames.map((key, index) => [key, dates[index]])
  Object.assign(props.query, Object.fromEntries(concatObj))
}
const handleGetDateTimeRange = (val: any) => {
  Object.assign(props.query, val)
}
</script>
<template>
  <div class="base-search base-query-container">
    <a-form class="base-search-form" layout="inline">
      <a-form-item v-for="item in queryOptions" :label="item.label" :key="item.name" class="pd8">
        <a-input
          v-if="item.type === 'input'"
          v-model:value="query[queryKey(item.name)]"
          placeholder="请输入"
          :style="item.style"
          allowClear
          :key="item.name"
        />
        <a-select
          v-if="item.type === 'select'"
          v-model:value="query[queryKey(item.name)]"
          placeholder="请选择"
          :style="item.style"
          allowClear
          :key="item.name"
        >
          <a-select-option v-for="option in item.options" :key="option.value" :value="option.value">
            {{ option.label }}
          </a-select-option>
        </a-select>
        <a-range-picker
          v-if="item.type === 'dateRange'"
          v-model:value="dateRange"
          format="YYYY-MM-DD"
          valueFormat="YYYY-MM-DD"
          @change="handleChangeDateRange"
        />
        <DateTimeRange
          v-if="item.type === 'dateTimeRange'"
          :config="item"
          @change="handleGetDateTimeRange"
        />
      </a-form-item>
    </a-form>
    <div class="base-query-actions">
      <a-button type="primary" @click="handleSearch">
        <SearchOutlined />
        查询
      </a-button>
      <slot name="extraBtn"></slot>
    </div>
  </div>
</template>
<style lang="less" scoped>
.base-query-container {
}
</style>
