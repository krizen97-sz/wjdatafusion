import request from '@/utils/request'
import { getAddressGrid } from './index.js'

export function getWorkbookCatalog() {
  return request({ url: '/ipam/workbook/catalog', method: 'get' })
}

export function getWorkbookNetworkPage(networkId, pageNum, pageSize) {
  return getAddressGrid(networkId, pageNum, pageSize)
}

export function listWorkbookCommunityRows(communityName, pageNum = 1, pageSize = 1000) {
  return request({
    url: '/ipam/workbook/community/list',
    method: 'get',
    params: { communityName, pageNum, pageSize }
  })
}

export function commitWorkbook(sheets) {
  return request({
    url: '/ipam/workbook/commit',
    method: 'post',
    headers: { repeatSubmit: false },
    data: { sheets }
  })
}
