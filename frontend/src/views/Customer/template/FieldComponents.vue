<template>
  <div>
    <!-- 文本字段 -->
    <template v-if="component === 'TextField'">
      <div class="field-wrapper">
        <label class="field-label">
          <span v-if="field.required" class="required">*</span>
          {{ field.fieldLabel }}
        </label>
        <a-input :placeholder="`请输入${field.fieldLabel}`" />
      </div>
    </template>

    <!-- 电话字段 -->
    <template v-if="component === 'PhoneField'">
      <div class="field-wrapper">
        <label class="field-label">
          <span v-if="field.required" class="required">*</span>
          {{ field.fieldLabel }}
        </label>
        <a-input :placeholder="`请输入${field.fieldLabel}`" />
      </div>
    </template>

    <!-- 数字字段 -->
    <template v-if="component === 'NumberField'">
      <div class="field-wrapper">
        <label class="field-label">
          <span v-if="field.required" class="required">*</span>
          {{ field.fieldLabel }}
        </label>
        <a-input-number :placeholder="`请输入${field.fieldLabel}`" style="width: 100%" />
      </div>
    </template>

    <!-- 单选字-->
    <template v-if="component === 'RadioField'">
      <div class="field-wrapper">
        <label class="field-label">
          <span v-if="field.required" class="required">*</span>
          {{ field.fieldLabel }}
        </label>
        <a-select >
          <a-select-option
              v-for="option in getFieldOptions(field)"
              :key="option.value"
              :value="option.value"
          >{{ option.value }}
          </a-select-option>
        </a-select>
      </div>

    </template>

    <!-- 多选字-->
    <template v-if="component === 'CheckboxField'">
      <div class="field-wrapper">
        <label class="field-label">
          <span v-if="field.required" class="required">*</span>
          {{ field.fieldLabel }}
        </label>
        <a-select  mode="multiple">
          <a-select-option
              v-for="option in getFieldOptions(field)"
              :key="option.value"
              :value="option.value"
          >{{ option.value }}
          </a-select-option>
        </a-select>
      </div>
    </template>

    <!-- 邮箱字段 -->
    <template v-if="component === 'EmailField'">
      <div class="field-wrapper">
        <label class="field-label">
          <span v-if="field.required" class="required">*</span>
          {{ field.fieldLabel }}
        </label>
        <a-input :placeholder="`请输入${field.fieldLabel}`" />
      </div>
    </template>

    <!-- 日期字段 -->
    <template v-if="component === 'DateField'">
      <div class="field-wrapper">
        <label class="field-label">
          <span v-if="field.required" class="required">*</span>
          {{ field.fieldLabel }}
        </label>
        <a-date-picker :placeholder="`请选择${field.fieldLabel}`" style="width: 100%" />
      </div>
    </template>

    <!-- 日期时间字段 -->
    <template v-if="component === 'DateTimeField'">
      <div class="field-wrapper">
        <label class="field-label">
          <span v-if="field.required" class="required">*</span>
          {{ field.fieldLabel }}
        </label>
        <a-date-picker
          show-time
          :placeholder="`请选择${field.fieldLabel}`"
          style="width: 100%"
        />
      </div>
    </template>

    <!-- 时间字段 -->
    <template v-if="component === 'TimeField'">
      <div class="field-wrapper">
        <label class="field-label">
          <span v-if="field.required" class="required">*</span>
          {{ field.fieldLabel }}
        </label>
        <a-time-picker :placeholder="`请选择${field.fieldLabel}`" style="width: 100%" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
// @ts-nocheck
const props = defineProps({
  component: {
    type: String,
    required: true
  },
  field: {
    type: Object,
    required: true
  }
})

// 解析字段选项
const getFieldOptions = (field) => {
  try {
    return JSON.parse(field.options || '[]')
  } catch {
    return []
  }
}
</script>

<style scoped lang="less">
.field-wrapper {
  .field-label {
    display: block;
    margin-bottom: 8px;
    font-weight: 500;
    color: #262626;

    .required {
      color: #ff4d4f;
      margin-right: 4px;
    }
  }
}
</style>
