import { codeToText, regionData } from 'element-china-area-data'
import { pinyin } from 'pinyin-pro'

const REGION_SUFFIX_PATTERN = /(特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|自治州|自治县|省|市|区|县|旗|盟|地区)$/u
const REGION_ABBR_OVERRIDES = {
  香港特别行政区: 'HK',
  澳门特别行政区: 'MO',
  台湾省: 'TW',
  内蒙古自治区: 'NMG',
  广西壮族自治区: 'GX',
  宁夏回族自治区: 'NX',
  新疆维吾尔自治区: 'XJ',
  西藏自治区: 'XZ'
}
const GENERIC_CITY_NAMES = new Set(['市辖区', '县', '自治区直辖县级行政区划', '省直辖县级行政区划'])

export const supportSiteRegionOptions = regionData

export function resolveSiteRegion(regionCodes = []) {
  const [provinceCode, cityCode, districtCode] = Array.isArray(regionCodes) ? regionCodes : []
  return {
    provinceCode: provinceCode || null,
    provinceName: provinceCode ? codeToText[provinceCode] || null : null,
    cityCode: cityCode || null,
    cityName: cityCode ? codeToText[cityCode] || null : null,
    districtCode: districtCode || null,
    districtName: districtCode ? codeToText[districtCode] || null : null
  }
}

export function formatSiteRegion(site = {}) {
  return [site.provinceName, site.cityName, site.districtName].filter(Boolean).join(' / ')
}

export function buildSiteCodePrefixPreview(site = {}) {
  if (!site?.provinceName || !site?.cityName || !site?.districtName) return ''
  const provinceAbbr = getRegionAbbreviation(site.provinceName)
  const citySource = GENERIC_CITY_NAMES.has(site.cityName) ? site.provinceName : site.cityName
  const cityAbbr = getRegionAbbreviation(citySource)
  const districtAbbr = getRegionAbbreviation(site.districtName)
  return [provinceAbbr, cityAbbr, districtAbbr].filter(Boolean).join('_')
}

function getRegionAbbreviation(regionName) {
  if (!regionName) return ''
  if (REGION_ABBR_OVERRIDES[regionName]) return REGION_ABBR_OVERRIDES[regionName]
  const normalized = regionName.replace(REGION_SUFFIX_PATTERN, '') || regionName
  return pinyin(normalized, { pattern: 'first', toneType: 'none' }).replace(/\s+/g, '').toUpperCase()
}
