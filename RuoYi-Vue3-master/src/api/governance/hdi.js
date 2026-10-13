import request from '@/utils/request'

export const createHdiSession = () => request({
  url: '/governance/hdi/session',
  method: 'post',
  timeout: 30000,
  headers: { repeatSubmit: false }
})
