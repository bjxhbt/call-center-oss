import Request from '@/utils/axios'

// 获取存储桶列表
export function getBuckets() {
  return Request.get('/v1/storage/buckets')
}

// 获取对象列表
export function getObjects(params: any) {
  return Request.get('/v1/storage/objects', null, { params })
}

// 获取存储统计
export function getStorageStats() {
  return Request.get('/v1/storage/stats')
}

// 删除对象
export function deleteObject(bucket: string, objectKey: string) {
  return Request.delete('/v1/storage/object', { params: { bucket, objectKey } })
}

// 获取下载URL
export function getDownloadUrl(bucket: string, objectKey: string) {
  return Request.get('/v1/storage/download-url', null, { params: { bucket, objectKey } })
}
