export interface HotDestination {
  name: string
  label: string
}

export const HOT_DESTINATIONS: HotDestination[] = [
  { name: '北京', label: '北京' },
  { name: '上海', label: '上海' },
  { name: '西安', label: '西安' },
  { name: '成都', label: '成都' },
  { name: '重庆', label: '重庆' },
  { name: '杭州', label: '杭州' },
  { name: '苏州', label: '苏州' },
  { name: '南京', label: '南京' },
  { name: '厦门', label: '厦门' },
  { name: '青岛', label: '青岛' },
  { name: '长沙', label: '长沙' },
  { name: '武汉', label: '武汉' },
  { name: '广州', label: '广州' },
  { name: '桂林', label: '桂林' },
  { name: '丽江', label: '丽江' },
  { name: '三亚', label: '三亚' },
]

const FOREIGN_HINTS = [
  '东京', '大阪', '京都', '北海道', '首尔', '釜山', '曼谷', '清迈', '普吉',
  '新加坡', '吉隆坡', '巴厘岛', '巴黎', '伦敦', '纽约', '洛杉矶', '旧金山',
  '悉尼', '墨尔本', '莫斯科', '罗马', '米兰', '迪拜', '开罗', '里约',
]

export function isLikelyDomesticDestination(value: string): boolean {
  const trimmed = value.trim()
  if (!trimmed) return false
  if (!/^[\u4e00-\u9fa5·\-\s]+$/.test(trimmed)) return false
  return !FOREIGN_HINTS.some((hint) => trimmed.includes(hint))
}
