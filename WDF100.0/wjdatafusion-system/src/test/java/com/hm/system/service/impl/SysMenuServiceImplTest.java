package com.hm.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.core.domain.entity.SysMenu;
import com.hm.system.mapper.SysMenuMapper;

class SysMenuServiceImplTest
{
    @Test
    void rootPathMayMatchNestedPathsWhenRouteNamesAreUnique()
    {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        SysMenuServiceImpl service = serviceWith(mapper);
        SysMenu rootVersion = menu(2205L, 0L, "version", "SupportVersion", "版本记录");
        SysMenu siteVersion = menu(2207L, 2200L, "version", "SupportSiteVersion", "现场版本记录");
        SysMenu inspectionVersion = menu(2306L, 2300L, "version", "AutoInspectionVersion", "巡检版本记录");
        when(mapper.selectMenusByPathOrRouteName("version", "SupportVersion"))
            .thenReturn(List.of(rootVersion, siteVersion, inspectionVersion));

        assertTrue(service.checkRouteConfigUnique(rootVersion));
    }

    @Test
    void sameParentPathConflictIsStillRejected()
    {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        SysMenuServiceImpl service = serviceWith(mapper);
        SysMenu edited = menu(2205L, 0L, "version", "SupportVersion", "版本记录");
        SysMenu conflicting = menu(3300L, 0L, "version", "AnotherVersion", "重复顶级版本");
        when(mapper.selectMenusByPathOrRouteName("version", "SupportVersion"))
            .thenReturn(List.of(edited, conflicting));

        assertFalse(service.checkRouteConfigUnique(edited));
    }

    @Test
    void routeNameConflictAcrossDifferentParentsIsStillRejected()
    {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        SysMenuServiceImpl service = serviceWith(mapper);
        SysMenu edited = menu(2205L, 0L, "version", "SupportVersion", "版本记录");
        SysMenu conflicting = menu(3301L, 3300L, "release", "SupportVersion", "重复路由名称");
        when(mapper.selectMenusByPathOrRouteName("version", "SupportVersion"))
            .thenReturn(List.of(edited, conflicting));

        assertFalse(service.checkRouteConfigUnique(edited));
    }

    private SysMenuServiceImpl serviceWith(SysMenuMapper mapper)
    {
        SysMenuServiceImpl service = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(service, "menuMapper", mapper);
        return service;
    }

    private SysMenu menu(Long menuId, Long parentId, String path, String routeName, String menuName)
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(menuId);
        menu.setParentId(parentId);
        menu.setPath(path);
        menu.setRouteName(routeName);
        menu.setMenuName(menuName);
        return menu;
    }
}
