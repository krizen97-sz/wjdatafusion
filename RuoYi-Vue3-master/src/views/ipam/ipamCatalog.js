export const STATUS_META = {
  FREE: { label: '空闲', type: 'info', className: 'is-free', color: '#64748b' },
  RESERVED: { label: '保留', type: 'warning', className: 'is-reserved', color: '#d97706' },
  ALLOCATED: { label: '已占用', type: 'primary', className: 'is-allocated', color: '#2563eb' },
  ISSUED: { label: '已下发', type: 'success', className: 'is-issued', color: '#059669' },
  DISABLED: { label: '禁用', type: 'danger', className: 'is-disabled', color: '#dc2626' }
}

export const DEVICE_TYPE_OPTIONS = [
  { label: '录像机', value: 'RECORDER' },
  { label: '摄像机', value: 'CAMERA' },
  { label: 'NVR', value: 'NVR' },
  { label: 'CVR', value: 'CVR' },
  { label: '平台', value: 'PLATFORM' },
  { label: '存储服务器', value: 'STORAGE_SERVER' },
  { label: '解码器', value: 'DECODER' },
  { label: '门禁', value: 'ACCESS_CONTROL' },
  { label: '人脸设备', value: 'FACE_DEVICE' },
  { label: '道闸', value: 'BARRIER_GATE' },
  { label: 'IAC', value: 'IAC' },
  { label: '网络映射设备', value: 'MAPPING_DEVICE' },
  { label: '其他', value: 'OTHER' }
]

export const MANUFACTURER_OPTIONS = ['海康', '大华', '宇视', '华龙', '恒信和安', '天地伟业', '科达', '其他']
  .map((item) => ({ label: item, value: item }))

export const SCENARIO_TYPE_OPTIONS = [
  { label: '社会面场景', value: 'SOCIAL' },
  { label: '公安内网场景', value: 'INTERNAL' }
]

export function getStatusMeta(status) {
  return STATUS_META[status] || STATUS_META.FREE
}

export function getTargetTypeLabel(value) {
  if (!value) return '-'
  return String(value).split(/[、,，\s]+/).filter(Boolean).map((item) => {
    return DEVICE_TYPE_OPTIONS.find((option) => option.value === item || option.label === item)?.label || item
  }).join('、')
}
