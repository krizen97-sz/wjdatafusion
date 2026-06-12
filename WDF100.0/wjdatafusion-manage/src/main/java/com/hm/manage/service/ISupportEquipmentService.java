package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.SupportEquipmentAsset;

public interface ISupportEquipmentService
{
    List<SupportEquipmentAsset> selectEquipmentAssetList(SupportEquipmentAsset query);
}
