package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.WhitelistPlate;

public interface IWhitelistPlateService
{
    List<WhitelistPlate> selectWhitelistPlateList(WhitelistPlate whitelistPlate);

    WhitelistPlate selectWhitelistPlateByVehiclePlate(String vehiclePlate);

    List<String> selectAuthorizedVehiclePlates();

    int insertWhitelistPlate(WhitelistPlate whitelistPlate);

    int updateWhitelistPlate(WhitelistPlate whitelistPlate);

    int deleteWhitelistPlateByVehiclePlates(String[] vehiclePlates);

    int changeStatus(WhitelistPlate whitelistPlate);

    String importWhitelistPlate(List<WhitelistPlate> whitelistPlateList, boolean updateSupport, String operName);
}
