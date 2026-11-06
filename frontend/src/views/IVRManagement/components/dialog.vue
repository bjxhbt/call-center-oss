<template>
  <a-modal v-model:open="visible" title="新增" @ok="handleOk">
    <a-form ref="formRef" :model="formState" :rules="rules">
      <a-form-item label="名称" name="name">
        <a-input v-model:value="formState.name" />
      </a-form-item>
      <a-form-item label="描述" name="desc">
        <a-textarea v-model:value="formState.desc" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-radio-group v-model:value="formState.status">
          <a-radio :value="0">草稿</a-radio>
          <a-radio :value="1">待发布</a-radio>
          <a-radio :value="2">已发布</a-radio>
        </a-radio-group>
      </a-form-item>
    </a-form>
  </a-modal>
</template>
<script lang="ts" setup>
import { addIvr } from '@/apis/callingConfigure/ivr'
import { message } from 'ant-design-vue'

const visible = ref(false)
const formRef = ref()

interface FormState {
  groupId: string
  name: string
  desc: string
  status: string
  flowData: string
}

const formState = reactive<FormState>({
  groupId: '',
  name: '',
  desc: '',
  status: '',
  flowData: '[]'
})
const rules = {
  name: [{ required: true, message: '请输入ivr名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择流程状态', trigger: 'change' }],
  desc: [{ required: true, message: '请输入ivr描述', trigger: 'blur' }]
}
const open = (id: string) => {
  formState.groupId = id
  visible.value = true
}

defineExpose({ open })

const emit = defineEmits(['success'])
const handleOk = async () => {
  await formRef.value.validate()
  try {
    const res = await addIvr(formState)
    if (res.code == 200) {
      message.success('新增成功')
      emit('success')
      visible.value = false
    }
  } catch {
    message.error('新增失败')
  }
}
</script>
<style scoped></style>
