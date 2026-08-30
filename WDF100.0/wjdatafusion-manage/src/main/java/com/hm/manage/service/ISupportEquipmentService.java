package com.hm.manage.service;

import java.util.List;
import com.hm.manage.domain.SupportEquipmentAsset;
import com.hm.manage.domain.bo.SupportEquipmentBatchBo;
import com.hm.manage.domain.bo.SupportEquipmentPlatformBindingBo;

public interface ISupportEquipmentService
{
    List<SupportEquipmentAsset> selectEquipmentAssetList(SupportEquipmentAsset query);

    int deleteEquipmentAssets(SupportEquipmentBatchBo command);

    int bindPlatform(SupportEquipmentPlatformBindingBo command);

    int unbindPlatform(SupportEquipmentPlatformBindingBo command);
}
