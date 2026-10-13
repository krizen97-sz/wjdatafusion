import request from '@/utils/request'

export const createHdiSession = (renew = false) => request({
  url: '/governance/hdi/session',
  method: 'post',
  data: { renew: renew === true },
  timeout: 30000,
  headers: { repeatSubmit: false }
})
