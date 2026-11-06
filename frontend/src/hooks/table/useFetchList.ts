export function useFetchList(fun: any, query: any) {
  const queryDataFun = fun
  const pagination = reactive({
    total: 0,
    current: 1,
    pageSize: 10,
    showPageSize: true,
    hideOnSinglePage: false
  })
  const tableData = ref<any[]>([])
  const loading = ref(false)
  const handleSearch = async () => {
    loading.value = true
    queryDataFun({
      ...query,
      pageIndex: pagination.current,
      pageSize: pagination.pageSize
    })
      .then((res: any) => {
        loading.value = false
        tableData.value = res.data.list || res.data
        // 树形/非分页接口（如菜单列表）没有 total，按返回数据长度兜底
        const total =
          typeof res.data.total === 'number'
            ? res.data.total
            : (res.data.list || res.data || []).length
        pagination.total = total
        // 非分页接口返回全量数据，没有翻页语义，数据不多时隐藏分页器
        pagination.hideOnSinglePage =
          typeof res.data.total !== 'number' && total <= pagination.pageSize
      })
      .finally(() => {
        loading.value = false
      })
  }

  const handleTableChange = (pageInfo: any) => {
    Object.assign(pagination, pageInfo)
    handleSearch()
  }

  const initTable = () => {
    pagination.current = 1
    pagination.pageSize = 10
    handleSearch()
  }

  // onMounted(() => {
  //   handleSearch()
  // })

  return {
    tableData,
    handleTableChange,
    handleSearch,
    pagination,
    loading,
    initTable
  }
}
