package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.Novel;

/**
 * novelService接口
 * 
 * @author hm
 * @date 2026-03-24
 */
public interface INovelService 
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
     * 批量删除novel
     * 
     * @param ids 需要删除的novel主键集合
     * @return 结果
     */
    public int deleteNovelByIds(Long[] ids);

    /**
     * 删除novel信息
     * 
     * @param id novel主键
     * @return 结果
     */
    public int deleteNovelById(Long id);
}
