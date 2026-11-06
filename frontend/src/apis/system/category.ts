import Request from '@/utils/axios'

//分类树管理
export function treeList(data: any){
   return  Request.post('/system/v1/category/tree/list', data)
}

// edit
export function editCategory(data: any){
   return  Request.post('/system/v1/category/edit', data)
}

// add
export function addCategory(data: any){
   return  Request.post('/system/v1/category/add', data)
}

// delete
export function deleteCategory(data: any){
   return  Request.post('/system/v1/category/delete', data)
}
