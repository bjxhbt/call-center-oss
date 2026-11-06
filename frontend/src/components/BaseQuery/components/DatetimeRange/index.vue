<script setup lang="ts">
const props = defineProps({
  config: {
    type: Object,
    default: () => ({})
  }
})
const emit = defineEmits(['change'])
const dateRange = ref<string[]>([])
const timeRange = ref<string[]>([])
const options = props.config.options || []
const AllkeyNames = options.map((item: any) => item.keyNames).flat()
const selectValue = ref<string | number>(options[0].value)
const selectItem = ref<any>(options[0])
const dateTimeValues = ref<any>({})
const handleChange = (val: any) => {
  selectItem.value = options.find((item: any) => item.value === val) || {}
  dateRange.value = []
  timeRange.value = []
  const concatObj = AllkeyNames.map((key: any) => [key, ''])
  dateTimeValues.value = Object.fromEntries(concatObj)
  emit('change', {
    [props.config.name]: val,
    ...dateTimeValues.value
  })
}
const handleChangeDateTimeRange = (val: string[]) => {
  const keyNames = selectItem.value.keyNames
  const dates = val ? val : ['', '']
  if (!Array.isArray(keyNames)) return
  const concatObj = keyNames.map((key, index) => [key, dates[index]])
  dateTimeValues.value = Object.fromEntries(concatObj)
  emit('change', {
    [props.config.name]: selectValue.value,
    ...dateTimeValues.value
  })
}
</script>
<template>
  <div class="datetime-range">
    <a-select
      v-model:value="selectValue"
      placeholder="请选择"
      style="width: 120px"
      @change="handleChange"
    >
      <a-select-option v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </a-select-option>
    </a-select>
    <a-range-picker
      v-show="selectItem.type === 'date'"
      v-model:value="dateRange"
      format="YYYY-MM-DD"
      valueFormat="YYYY-MM-DD"
      @change="handleChangeDateTimeRange"
    />
    <a-time-range-picker
      v-show="selectItem.type === 'time'"
      v-model:value="timeRange"
      style="width: 266px"
      format="HH:mm"
      valueFormat="HH:mm"
      @change="handleChangeDateTimeRange"
    />
  </div>
</template>
<style lang="less" scoped>
.datetime-range {
  display: flex;
  align-items: center;
}
</style>
