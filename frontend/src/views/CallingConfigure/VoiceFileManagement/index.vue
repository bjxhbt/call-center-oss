<script setup lang="ts">
import {
  addVoiceFile,
  deleteVoiceFile,
  editVoiceFile,
  getPageList,
  getVoiceFileDetail
} from '@/apis/callingConfigure/voiceFile'
import { message, UploadChangeParam } from 'ant-design-vue'
import { useFetchList } from '@/hooks/table/useFetchList'
import {
  columns,
  ttsEnum,
  typeEnum
} from '@/views/CallingConfigure/VoiceFileManagement/consts/tableColumns'
import { PlusOutlined } from '@ant-design/icons-vue'
// import { uploadFile } from '@/apis/system/sysFile'
import { getToken } from '@/utils/local'
import { UploadOutlined } from '@ant-design/icons-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import { queryOptions } from './consts/queryOptions'

const formRef = ref()
let open = ref<boolean>(false)
let drawTitle = ref<string>('新增语音文件')

let formState = reactive({
  name: '',
  type: '1',
  tts: '',
  speechText: '',
  fileId: '',
  fileList: [] as any[],
  id: ''
})

const query = reactive({
  name: '',
  type: undefined,
  tts: undefined
})
const { tableData, handleSearch, handleTableChange, pagination, loading, initTable } = useFetchList(
  getPageList,
  query
)

onMounted(() => {
  handleSearch()
})

const handlerDelete = async (id: string | number) => {
  const res = await deleteVoiceFile({ id })
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}

const handlerAdd = () => {
  drawTitle.value = '新增语音文件'
  open.value = true
}

const handlerEdit = async (record: any) => {
  const res = await getVoiceFileDetail({ id: record.id })
  if (res.code === 200) {
    drawTitle.value = '编辑语音文件'
    formState = reactive({ ...res.data })
    formState.type = res.data.type + ''
    formState.tts = res.data?.tts ? res.data.tts + '' : ''
    formState.fileList = [
      {
        uid: res.data.fileId,
        name: res.data.fileName,
        status: 'done',
        url: res.data.filePath
      }
    ]
    open.value = true
    return
  }
}

const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    name: '',
    type: '1',
    tts: '',
    speechText: '',
    fileId: '',
    fileList: [] as any[],
    id: ''
  })
  open.value = false
}

const handleSubmit = () => {
  formRef.value
    .validate()
    .then(async () => {
      const fn = formState.id ? editVoiceFile : addVoiceFile
      const res = await fn({
        ...formState,
        fileList: []
      })
      if (res.code === 200) {
        message.success('操作成功！')
        closeDrawer()
        formState.id ? handleSearch() : initTable()
        return
      }
      message.error('操作失败！')
    })
    .catch(() => {
      console.log('error')
    })
}

const handleUploadChange = (info: UploadChangeParam) => {
  if (info.file.status !== 'uploading') {
    console.log(info)
  }
  if (info.file.status === 'done') {
    message.success(`${info.file.name} file uploaded successfully`)
    formState.fileId = info.file.response.data.id
    formState.fileList = info.fileList
    console.log(formState, 'formState')
  } else if (info.file.status === 'error') {
    message.error(`${info.file.name} file upload failed.`)
  }
}

const rules = reactive({
  name: [{ required: true, message: '请输入文件名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
})

const typeMap: Record<string, any> = {
  '1': { color: '#bd47da', text: '手动上传' },
  '2': { color: '#d7c5c5', text: '语音合成' }
}
const ttsMap: Record<string, any> = {
  '1': { color: '#bd47da', text: '腾讯云' },
  '2': { color: '#d7c5c5', text: '阿里云' },
  '3': { color: '#2957df', text: '讯飞' }
}
</script>

<template>
  <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleSearch" />
  <div class="base-table-content">
    <a-button class="mb8 fr" @click="handlerAdd">
      <PlusOutlined />
      新增
    </a-button>
    <a-table
      :columns="columns"
      :data-source="tableData"
      bordered
      :pagination="pagination"
      @change="handleTableChange"
      :loading="loading"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'operate'">
          <a-button @click="handlerEdit(record)" type="link">
            <FormOutlined />
            编辑
          </a-button>
          <a-button @click="handlerDelete(record.id)" type="link">
            <DeleteOutlined />
            删除
          </a-button>
        </template>
        <template v-if="column.dataIndex === 'type'">
          <a-tag :color="typeMap[record.type]?.color">
            {{ typeMap[record.type]?.text }}
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'tts'">
          <a-tag v-if="record.tts" :color="ttsMap[record.tts]?.color">
            {{ ttsMap[record.tts]?.text }}
          </a-tag>
        </template>
        <!--音频播放-->
        <template v-if="column.dataIndex === 'filePath'">
          <audio :src="record.filePath" controls>
            您的浏览器不支持 audio 元素。
          </audio>
        </template>

      </template>
    </a-table>
    <a-drawer
      v-model:open="open"
      class="custom-class"
      root-class-name="root-class-name"
      :title="drawTitle"
      placement="right"
      :closable="false"
      width="500"
    >
      <a-form
        ref="formRef"
        :model="formState"
        name="basic"
        :label-col="{ span: 8 }"
        :wrapper-col="{ span: 15 }"
        autocomplete="off"
        :rules="rules"
      >
        <a-form-item label="文件名称" name="name">
          <a-input v-model:value="formState.name" />
        </a-form-item>
        <a-form-item label="类型" name="type">
          <a-select v-model:value="formState.type" placeholder="请选择类型">
            <a-select-option v-for="(item, index) in typeEnum" :key="index" :value="index">{{
              item
            }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="formState.type === '2'" label="tts" name="tts">
          <a-select
            v-model:value="formState.tts"
            :disabled="formState.type !== '2'"
            placeholder="请选择tts"
          >
            <a-select-option v-for="(item, index) in ttsEnum" :key="index" :value="index">{{
              item
            }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item
          label="合成文本"
          name="speechText"
          :rules="[{ required: true, message: '请输入合成文本' }]"
          v-if="formState.type === '2'"
        >
          <a-input v-model:value="formState.speechText" />
        </a-form-item>
        <a-form-item
          label="上传文件"
          name="fileId"
          :rules="[{ required: true, message: '请上传文件' }]"
          v-if="formState.type !== '2'"
        >
          <!--仅支持语音文件上传-->
          <a-upload
            v-model:file-list="formState.fileList"
            accept=".mp3,.wav"
            name="file"
            action="/system/v1/file/upload"
            :headers="{ Authorization: `Bearer ${getToken()}` }"
            @change="handleUploadChange"
          >
            <a-button type="primary">
              <template #icon><UploadOutlined /></template>
              上传语音文件
            </a-button>
          </a-upload>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button key="back" style="margin-right: 16px" @click="closeDrawer">取消</a-button>
        <a-button key="submit" type="primary" @click="handleSubmit">提交</a-button>
      </template>
    </a-drawer>
  </div>
</template>
