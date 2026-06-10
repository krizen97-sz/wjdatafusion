package com.hm.manage.mapper;

import java.util.List;
import com.hm.manage.domain.Novel;

/**
 * novelMapper接口
 * 
 * @author hm
 * @date 2026-03-24
 */
public interface NovelMapper 
{
    /**
     * 查询novel
     * 
     * @param id novel主键
     * @return novel
     */
    public Novel selectNovelById(Long id);

    /**
     * 查询novel列表
     * 
     * @param novel novel
     * @return novel集合
     */
    public List<Novel> selectNovelList(Novel novel);

    /**
     * 新增novel
     * 
     * @param novel novel
     * @return 结果
     */
    public int insertNovel(Novel novel);

    /**
     * 修改novel
     * 
     * @param novel novel
     * @return 结果
     */
    public int updateNovel(Novel novel);

    /**
     * 删除novel
     * 
     * @param id novel主键
     * @return 结果
     */
    public int deleteNovelById(Long id);

    /**
     * 批量删除novel
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteNovelByIds(Long[] ids);
}
