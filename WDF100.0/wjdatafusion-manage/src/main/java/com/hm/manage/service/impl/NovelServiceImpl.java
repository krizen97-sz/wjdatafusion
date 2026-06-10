package com.hm.manage.service.impl;

import java.util.List;
import com.hm.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hm.manage.mapper.NovelMapper;
import com.hm.manage.domain.Novel;
import com.hm.manage.service.INovelService;

/**
 * novelService业务层处理
 * 
 * @author hm
 * @date 2026-03-24
 */
@Service
public class NovelServiceImpl implements INovelService 
{
    @Autowired
    private NovelMapper novelMapper;

    /**
     * 查询novel
     * 
     * @param id novel主键
     * @return novel
     */
    @Override
    public Novel selectNovelById(Long id)
    {
        return novelMapper.selectNovelById(id);
    }

    /**
     * 查询novel列表
     * 
     * @param novel novel
     * @return novel
     */
    @Override
    public List<Novel> selectNovelList(Novel novel)
    {
        return novelMapper.selectNovelList(novel);
    }

    /**
     * 新增novel
     * 
     * @param novel novel
     * @return 结果
     */
    @Override
    public int insertNovel(Novel novel)
    {
        novel.setCreateTime(DateUtils.getNowDate());
        return novelMapper.insertNovel(novel);
    }

    /**
     * 修改novel
     * 
     * @param novel novel
     * @return 结果
     */
    @Override
    public int updateNovel(Novel novel)
    {
        novel.setUpdateTime(DateUtils.getNowDate());
        return novelMapper.updateNovel(novel);
    }

    /**
     * 批量删除novel
     * 
     * @param ids 需要删除的novel主键
     * @return 结果
     */
    @Override
    public int deleteNovelByIds(Long[] ids)
    {
        return novelMapper.deleteNovelByIds(ids);
    }

    /**
     * 删除novel信息
     * 
     * @param id novel主键
     * @return 结果
     */
    @Override
    public int deleteNovelById(Long id)
    {
        return novelMapper.deleteNovelById(id);
    }
}
