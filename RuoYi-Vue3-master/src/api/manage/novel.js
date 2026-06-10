import request from '@/utils/request'

// 查询novel列表
export function listNovel(query) {
  return request({
    url: '/manage/novel/list',
    method: 'get',
    params: query
  })
}

// 查询novel详细
export function getNovel(id) {
  return request({
    url: '/manage/novel/' + id,
    method: 'get'
  })
}

// 新增novel
export function addNovel(data) {
  return request({
    url: '/manage/novel',
    method: 'post',
    data: data
  })
}

// 修改novel
export function updateNovel(data) {
  return request({
    url: '/manage/novel',
    method: 'put',
    data: data
  })
}

// 删除novel
export function delNovel(id) {
  return request({
    url: '/manage/novel/' + id,
    method: 'delete'
  })
}
