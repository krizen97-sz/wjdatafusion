package com.hm.manage.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.hm.common.annotation.Excel;
import com.hm.common.core.domain.BaseEntity;

/**
 * novel对象 tb_novel
 *
 * @author hm
 * @date 2026-03-24
 */
public class Novel extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 小说名称 */
    @Excel(name = "小说名称")
    private String novelName;

    /** 小说分类 */
    @Excel(name = "小说分类")
    private String novelCategroy;

    /** 内容 */
    @Excel(name = "内容")
    private String content;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setNovelName(String novelName)
    {
        this.novelName = novelName;
    }

    public String getNovelName()
    {
        return novelName;
    }

    public void setNovelCategroy(String novelCategroy)
    {
        this.novelCategroy = novelCategroy;
    }

    public String getNovelCategroy()
    {
        return novelCategroy;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getContent()
    {
        return content;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("novelName", getNovelName())
                .append("novelCategroy", getNovelCategroy())
                .append("content", getContent())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
