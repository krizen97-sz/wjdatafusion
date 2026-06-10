package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.WhitelistPlate;
import com.hm.manage.service.IWhitelistPlateService;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.system.service.ISysUserService;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WhitelistPlateServiceImpl implements IWhitelistPlateService
{
    private static final DateTimeFormatter BY1_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern VEHICLE_PLATE_PATTERN = Pattern.compile(
            "^[\\u4e00-\\u9fa5A-Z0-9挂学警港澳使领]{5,12}$",
            Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate whitelistPostgresJdbcTemplate;
    private final ISysUserService sysUserService;

    public WhitelistPlateServiceImpl(@Qualifier("whitelistPostgresJdbcTemplate") JdbcTemplate whitelistPostgresJdbcTemplate,
            ISysUserService sysUserService)
    {
        this.whitelistPostgresJdbcTemplate = whitelistPostgresJdbcTemplate;
        this.sysUserService = sysUserService;
    }

    @Override
    public List<WhitelistPlate> selectWhitelistPlateList(WhitelistPlate whitelistPlate)
    {
        try
        {
            StringBuilder sql = new StringBuilder(
                    "select vehicleplate as vehiclePlate, vehicleplate as originalVehiclePlate, alarmtype as alarmType, "
                            + "id as statusFlag, remark as remark, by1 as createTimeText from whitelist where 1=1");
            if (StringUtils.isNotEmpty(whitelistPlate.getVehiclePlate()))
            {
                sql.append(" and vehicleplate like ?");
            }
            if (StringUtils.isNotEmpty(whitelistPlate.getRemark()))
            {
                sql.append(" and remark like ?");
            }
            if (whitelistPlate.getStatusFlag() != null)
            {
                sql.append(" and id = ?");
            }
            if (!SecurityUtils.isAdmin())
            {
                sql.append(" and position(? in concat(',', coalesce(alarmtype, ''), ',')) > 0");
            }
            sql.append(" order by coalesce(by1, '') desc, vehicleplate asc");
            List<WhitelistPlate> list = whitelistPostgresJdbcTemplate.query(con -> {
                var ps = con.prepareStatement(sql.toString());
                int index = 1;
                if (StringUtils.isNotEmpty(whitelistPlate.getVehiclePlate()))
                {
                    ps.setString(index++, "%" + normalizePlate(whitelistPlate.getVehiclePlate()) + "%");
                }
                if (StringUtils.isNotEmpty(whitelistPlate.getRemark()))
                {
                    ps.setString(index++, "%" + whitelistPlate.getRemark().trim() + "%");
                }
                if (whitelistPlate.getStatusFlag() != null)
                {
                    ps.setString(index++, String.valueOf(whitelistPlate.getStatusFlag()));
                }
                if (!SecurityUtils.isAdmin())
                {
                    ps.setString(index, getCurrentUserOwnerToken());
                }
                return ps;
            }, BeanPropertyRowMapper.newInstance(WhitelistPlate.class));
            fillOwnerDisplay(list);
            return list;
        }
        catch (CannotGetJdbcConnectionException e)
        {
            throw wrapConnectionException(e);
        }
    }

    @Override
    public WhitelistPlate selectWhitelistPlateByVehiclePlate(String vehiclePlate)
    {
        try
        {
            StringBuilder sql = new StringBuilder(
                    "select vehicleplate as vehiclePlate, vehicleplate as originalVehiclePlate, alarmtype as alarmType, "
                            + "id as statusFlag, remark as remark, by1 as createTimeText from whitelist where vehicleplate = ?");
            if (!SecurityUtils.isAdmin())
            {
                sql.append(" and position(? in concat(',', coalesce(alarmtype, ''), ',')) > 0");
            }
            List<WhitelistPlate> list = whitelistPostgresJdbcTemplate.query(con -> {
                var ps = con.prepareStatement(sql.toString());
                ps.setString(1, normalizePlate(vehiclePlate));
                if (!SecurityUtils.isAdmin())
                {
                    ps.setString(2, getCurrentUserOwnerToken());
                }
                return ps;
            }, BeanPropertyRowMapper.newInstance(WhitelistPlate.class));
            fillOwnerDisplay(list);
            return list.isEmpty() ? null : list.get(0);
        }
        catch (CannotGetJdbcConnectionException e)
        {
            throw wrapConnectionException(e);
        }
    }

    @Override
    public List<String> selectAuthorizedVehiclePlates()
    {
        try
        {
            if (SecurityUtils.isAdmin())
            {
                return whitelistPostgresJdbcTemplate.queryForList("select vehicleplate from whitelist", String.class);
            }
            return whitelistPostgresJdbcTemplate.queryForList(
                    "select vehicleplate from whitelist where position(? in concat(',', coalesce(alarmtype, ''), ',')) > 0",
                    String.class,
                    getCurrentUserOwnerToken());
        }
        catch (CannotGetJdbcConnectionException e)
        {
            throw wrapConnectionException(e);
        }
    }

    @Override
    public int insertWhitelistPlate(WhitelistPlate whitelistPlate)
    {
        try
        {
            String plate = normalizePlate(whitelistPlate.getVehiclePlate());
            validatePlate(plate);
            Integer statusFlag = whitelistPlate.getStatusFlag() == null ? 2 : whitelistPlate.getStatusFlag();
            WhitelistPlate existingPlate = selectWhitelistPlateByVehiclePlateAnyScope(plate);
            if (existingPlate != null)
            {
                if (hasOwner(existingPlate.getAlarmType(), getCurrentUserOwnerFlag()))
                {
                    return 1;
                }
                String mergedOwnerFlags = mergeOwnerFlags(existingPlate.getAlarmType(), getCurrentUserOwnerFlag());
                String remark = buildRemarkForPersistence(whitelistPlate, existingPlate);
                return whitelistPostgresJdbcTemplate.update(
                        "update whitelist set alarmtype = ?, remark = ? where vehicleplate = ?",
                        mergedOwnerFlags, remark, plate);
            }
            String remark = buildRemarkForPersistence(whitelistPlate, null);
            return whitelistPostgresJdbcTemplate.update(
                    "insert into whitelist(id, vehicleplate, alarmtype, remark, by1) values (?, ?, ?, ?, ?)",
                    String.valueOf(statusFlag), plate, getCurrentUserOwnerFlag(), remark, currentBy1Value());
        }
        catch (CannotGetJdbcConnectionException e)
        {
            throw wrapConnectionException(e);
        }
    }

    @Override
    public int updateWhitelistPlate(WhitelistPlate whitelistPlate)
    {
        try
        {
            String originalPlate = normalizePlate(whitelistPlate.getOriginalVehiclePlate());
            String currentPlate = normalizePlate(whitelistPlate.getVehiclePlate());
            validatePlate(currentPlate);
            if (StringUtils.isEmpty(originalPlate))
            {
                throw new ServiceException("原始车牌不能为空");
            }
            WhitelistPlate original = selectWhitelistPlateByVehiclePlate(originalPlate);
            if (original == null)
            {
                throw new ServiceException("无权修改该车牌记录或记录不存在");
            }
            Integer statusFlag = whitelistPlate.getStatusFlag() == null ? 2 : whitelistPlate.getStatusFlag();
            if (existsPlate(currentPlate, originalPlate))
            {
                throw new ServiceException("该车牌已存在于白名单中");
            }
            String remark = buildRemarkForPersistence(whitelistPlate, original);
            return whitelistPostgresJdbcTemplate.update(
                    "update whitelist set id = ?, vehicleplate = ?, alarmtype = ?, remark = ? where vehicleplate = ?",
                    String.valueOf(statusFlag), currentPlate, getCurrentUserOwnerFlag(), remark, originalPlate);
        }
        catch (CannotGetJdbcConnectionException e)
        {
            throw wrapConnectionException(e);
        }
    }

    @Override
    public int deleteWhitelistPlateByVehiclePlates(String[] vehiclePlates)
    {
        try
        {
            int rows = 0;
            for (String vehiclePlate : vehiclePlates)
            {
                String normalizedPlate = normalizePlate(vehiclePlate);
                if (SecurityUtils.isAdmin())
                {
                    rows += whitelistPostgresJdbcTemplate.update("delete from whitelist where vehicleplate = ?", normalizedPlate);
                    continue;
                }
                WhitelistPlate current = selectWhitelistPlateByVehiclePlateAnyScope(normalizedPlate);
                if (current == null || !hasOwner(current.getAlarmType(), getCurrentUserOwnerFlag()))
                {
                    continue;
                }
                String remainingOwnerFlags = removeOwnerFlag(current.getAlarmType(), getCurrentUserOwnerFlag());
                if (StringUtils.isEmpty(remainingOwnerFlags))
                {
                    rows += whitelistPostgresJdbcTemplate.update("delete from whitelist where vehicleplate = ?", normalizedPlate);
                }
                else
                {
                    rows += whitelistPostgresJdbcTemplate.update(
                            "update whitelist set alarmtype = ? where vehicleplate = ?",
                            remainingOwnerFlags, normalizedPlate);
                }
            }
            return rows;
        }
        catch (CannotGetJdbcConnectionException e)
        {
            throw wrapConnectionException(e);
        }
    }

    @Override
    public int changeStatus(WhitelistPlate whitelistPlate)
    {
        try
        {
            String vehiclePlate = normalizePlate(StringUtils.isNotEmpty(whitelistPlate.getOriginalVehiclePlate())
                    ? whitelistPlate.getOriginalVehiclePlate() : whitelistPlate.getVehiclePlate());
            if (StringUtils.isEmpty(vehiclePlate) || whitelistPlate.getStatusFlag() == null)
            {
                throw new ServiceException("状态变更参数不完整");
            }
            String sql = SecurityUtils.isAdmin()
                    ? "update whitelist set id = ? where vehicleplate = ?"
                    : "update whitelist set id = ? where vehicleplate = ? and position(? in concat(',', coalesce(alarmtype, ''), ',')) > 0";
            return SecurityUtils.isAdmin()
                    ? whitelistPostgresJdbcTemplate.update(sql, String.valueOf(whitelistPlate.getStatusFlag()), vehiclePlate)
                    : whitelistPostgresJdbcTemplate.update(sql, String.valueOf(whitelistPlate.getStatusFlag()), vehiclePlate, getCurrentUserOwnerToken());
        }
        catch (CannotGetJdbcConnectionException e)
        {
            throw wrapConnectionException(e);
        }
    }

    @Override
    public String importWhitelistPlate(List<WhitelistPlate> whitelistPlateList, boolean updateSupport, String operName)
    {
        if (StringUtils.isNull(whitelistPlateList) || whitelistPlateList.isEmpty())
        {
            throw new ServiceException("导入车牌数据不能为空");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        for (int i = 0; i < whitelistPlateList.size(); i++)
        {
            WhitelistPlate whitelistPlate = whitelistPlateList.get(i);
            int excelRowNum = i + 2;
            try
            {
                String plate = normalizePlate(whitelistPlate.getVehiclePlate());
                whitelistPlate.setVehiclePlate(plate);
                whitelistPlate.setAlarmType(getCurrentUserOwnerFlag());
                whitelistPlate.setStatusFlag(whitelistPlate.getStatusFlag() == null ? 2 : whitelistPlate.getStatusFlag());
                WhitelistPlate current = selectWhitelistPlateByVehiclePlate(plate);
                WhitelistPlate anyScopeCurrent = current != null ? current : selectWhitelistPlateByVehiclePlateAnyScope(plate);
                if (anyScopeCurrent == null)
                {
                    insertWhitelistPlate(whitelistPlate);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("、第 ").append(excelRowNum).append(" 行车牌 ").append(plate).append(" 导入成功");
                }
                else if (updateSupport)
                {
                    if (SecurityUtils.isAdmin() || hasOwner(anyScopeCurrent.getAlarmType(), getCurrentUserOwnerFlag()))
                    {
                        whitelistPlate.setOriginalVehiclePlate(anyScopeCurrent.getVehiclePlate());
                        updateWhitelistPlate(whitelistPlate);
                    }
                    else
                    {
                        insertWhitelistPlate(whitelistPlate);
                    }
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("、第 ").append(excelRowNum).append(" 行车牌 ").append(plate).append(" 更新成功");
                }
                else
                {
                    insertWhitelistPlate(whitelistPlate);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("、第 ").append(excelRowNum).append(" 行车牌 ").append(plate).append(" 导入成功");
                }
            }
            catch (Exception e)
            {
                failureNum++;
                String plate = normalizePlate(whitelistPlate.getVehiclePlate());
                failureMsg.append("<br/>").append(failureNum).append("、第 ").append(excelRowNum).append(" 行车牌 ")
                        .append(StringUtils.isEmpty(plate) ? "空值" : plate).append(" 导入失败：")
                        .append(e.getMessage());
            }
        }
        if (failureNum > 0)
        {
            failureMsg.insert(0, "很抱歉，导入失败，共 " + failureNum + " 条数据格式或内容有问题，请修正后重试。错误如下：");
            throw new ServiceException(failureMsg.toString());
        }
        successMsg.insert(0, "导入完成，共成功处理 " + successNum + " 条车牌数据。");
        return successMsg.toString();
    }

    private boolean existsPlate(String currentPlate, String originalPlate)
    {
        try
        {
            Integer count;
            if (StringUtils.isEmpty(originalPlate))
            {
                count = whitelistPostgresJdbcTemplate.queryForObject(
                        "select count(1) from whitelist where vehicleplate = ?", Integer.class, currentPlate);
            }
            else
            {
                count = whitelistPostgresJdbcTemplate.queryForObject(
                        "select count(1) from whitelist where vehicleplate = ? and vehicleplate <> ?", Integer.class, currentPlate, originalPlate);
            }
            return count != null && count > 0;
        }
        catch (CannotGetJdbcConnectionException e)
        {
            throw wrapConnectionException(e);
        }
    }

    private WhitelistPlate selectWhitelistPlateByVehiclePlateAnyScope(String vehiclePlate)
    {
        List<WhitelistPlate> list = whitelistPostgresJdbcTemplate.query(
                "select vehicleplate as vehiclePlate, vehicleplate as originalVehiclePlate, alarmtype as alarmType, "
                        + "id as statusFlag, remark as remark, by1 as createTimeText from whitelist where vehicleplate = ?",
                BeanPropertyRowMapper.newInstance(WhitelistPlate.class),
                normalizePlate(vehiclePlate));
        fillOwnerDisplay(list);
        return list.isEmpty() ? null : list.get(0);
    }

    private String buildRemarkForPersistence(WhitelistPlate incoming, WhitelistPlate existing)
    {
        if (SecurityUtils.isAdmin())
        {
            return existing == null ? null : existing.getRemark();
        }
        return StringUtils.isEmpty(incoming.getRemark()) ? null : incoming.getRemark().trim();
    }

    private String currentBy1Value()
    {
        return LocalDateTime.now().format(BY1_FORMATTER);
    }

    private ServiceException wrapConnectionException(CannotGetJdbcConnectionException e)
    {
        return new ServiceException("外部 PostgreSQL 白名单库连接失败，请检查 whitelist.postgres 配置、网络连通性和账号认证信息")
                .setDetailMessage(e.getMostSpecificCause() == null ? e.getMessage() : e.getMostSpecificCause().getMessage());
    }

    private void fillOwnerDisplay(List<WhitelistPlate> list)
    {
        if (!SecurityUtils.isAdmin() || list == null || list.isEmpty())
        {
            return;
        }
        Map<String, String> ownerNameMap = list.stream()
                .map(WhitelistPlate::getAlarmType)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .map(ownerFlags -> Map.entry(ownerFlags, buildOwnerDisplay(ownerFlags)))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left));

        for (WhitelistPlate item : list)
        {
            item.setCreateBy(ownerNameMap.getOrDefault(item.getAlarmType(), item.getAlarmType()));
        }
    }

    private void validatePlate(String plate)
    {
        if (StringUtils.isEmpty(plate))
        {
            throw new ServiceException("车牌号码不能为空");
        }
        if (!VEHICLE_PLATE_PATTERN.matcher(plate).matches())
        {
            throw new ServiceException("请输入正确的车牌号码");
        }
    }

    private String normalizePlate(String plate)
    {
        return plate == null ? null : plate.trim().toUpperCase();
    }

    private String getCurrentUserOwnerFlag()
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null)
        {
            throw new ServiceException("无法获取当前用户信息");
        }
        return String.valueOf(userId);
    }

    private String getCurrentUserOwnerToken()
    {
        return "," + getCurrentUserOwnerFlag() + ",";
    }

    private boolean hasOwner(String ownerFlags, String ownerFlag)
    {
        if (StringUtils.isEmpty(ownerFlags) || StringUtils.isEmpty(ownerFlag))
        {
            return false;
        }
        for (String item : ownerFlags.split(","))
        {
            if (ownerFlag.equals(item.trim()))
            {
                return true;
            }
        }
        return false;
    }

    private String mergeOwnerFlags(String ownerFlags, String ownerFlag)
    {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (StringUtils.isNotEmpty(ownerFlags))
        {
            for (String item : ownerFlags.split(","))
            {
                if (StringUtils.isNotEmpty(item))
                {
                    values.add(item.trim());
                }
            }
        }
        if (StringUtils.isNotEmpty(ownerFlag))
        {
            values.add(ownerFlag.trim());
        }
        return String.join(",", values);
    }

    private String removeOwnerFlag(String ownerFlags, String ownerFlag)
    {
        if (StringUtils.isEmpty(ownerFlags))
        {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (String item : ownerFlags.split(","))
        {
            String normalized = item == null ? null : item.trim();
            if (StringUtils.isEmpty(normalized) || normalized.equals(ownerFlag))
            {
                continue;
            }
            values.add(normalized);
        }
        return values.isEmpty() ? null : String.join(",", values);
    }

    private String buildOwnerDisplay(String ownerFlags)
    {
        if (StringUtils.isEmpty(ownerFlags))
        {
            return "";
        }
        List<String> ownerNames = new ArrayList<>();
        for (String ownerFlag : ownerFlags.split(","))
        {
            String normalized = ownerFlag == null ? null : ownerFlag.trim();
            if (StringUtils.isEmpty(normalized))
            {
                continue;
            }
            try
            {
                SysUser user = sysUserService.selectUserById(Long.valueOf(normalized));
                if (user != null)
                {
                    String ownerName = StringUtils.isNotEmpty(user.getNickName()) ? user.getNickName() : user.getUserName();
                    ownerNames.add(ownerName + "（" + user.getUserName() + "）");
                    continue;
                }
            }
            catch (Exception ignored)
            {
            }
            ownerNames.add(normalized);
        }
        return String.join("，", ownerNames);
    }
}
