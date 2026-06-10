package com.hm.manage.util;

import java.util.Map;
import java.util.regex.Pattern;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.StringUtils;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

public final class SupportSiteCodeUtils
{
    private static final Pattern REGION_SUFFIX_PATTERN = Pattern.compile("(特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|自治州|自治县|省|市|区|县|旗|盟|地区)$");

    private static final Map<String, String> REGION_ABBR_OVERRIDES = Map.of(
        "香港特别行政区", "HK",
        "澳门特别行政区", "MO",
        "台湾省", "TW",
        "内蒙古自治区", "NMG",
        "广西壮族自治区", "GX",
        "宁夏回族自治区", "NX",
        "新疆维吾尔自治区", "XJ",
        "西藏自治区", "XZ"
    );

    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();

    static
    {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        PINYIN_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private SupportSiteCodeUtils()
    {
    }

    public static String toAreaAbbreviation(String regionName)
    {
        if (StringUtils.isBlank(regionName))
        {
            throw new ServiceException("地区名称不能为空，无法生成现场编码");
        }

        String trimmed = regionName.trim();
        if (REGION_ABBR_OVERRIDES.containsKey(trimmed))
        {
            return REGION_ABBR_OVERRIDES.get(trimmed);
        }

        String normalized = REGION_SUFFIX_PATTERN.matcher(trimmed).replaceFirst("");
        if (StringUtils.isBlank(normalized))
        {
            normalized = trimmed;
        }

        StringBuilder builder = new StringBuilder();
        for (char ch : normalized.toCharArray())
        {
            if (Character.isWhitespace(ch))
            {
                continue;
            }
            if (ch < 128 && Character.isLetterOrDigit(ch))
            {
                builder.append(Character.toUpperCase(ch));
                continue;
            }
            String[] pinyinArray;
            try
            {
                pinyinArray = PinyinHelper.toHanyuPinyinStringArray(ch, PINYIN_FORMAT);
            }
            catch (BadHanyuPinyinOutputFormatCombination e)
            {
                throw new ServiceException("地区名称“" + regionName + "”无法生成编码简称");
            }
            if (pinyinArray != null && pinyinArray.length > 0 && StringUtils.isNotBlank(pinyinArray[0]))
            {
                builder.append(pinyinArray[0].charAt(0));
            }
        }

        if (builder.length() == 0)
        {
            throw new ServiceException("地区名称“" + regionName + "”无法生成编码简称");
        }
        return builder.toString();
    }
}
