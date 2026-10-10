package com.cim.rms.server.device;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 设备台账仓储。 */
public interface DeviceRepository extends JpaRepository<Device, String> {

    Optional<Device> findByCodeAndDeletedFalse(String code);

    List<Device> findByDeletedFalse(Sort sort);

    List<Device> findByDeviceTypeIdAndDeletedFalse(String deviceTypeId);

    List<Device> findByAreaIdAndDeletedFalse(String areaId);

    /** 关键字（编码/名称模糊）+ 机型/区域过滤的台账查询。 */
    @Query("""
            select d from Device d
            where d.deleted = false
              and (:keyword is null or lower(d.code) like lower(:keyword) escape '\\' or lower(d.name) like lower(:keyword) escape '\\')
              and (:deviceTypeId is null or d.deviceTypeId = :deviceTypeId)
              and (:areaId is null or d.areaId = :areaId)
              and (:status is null or d.status = :status)
            """)
    List<Device> search(@Param("keyword") String keyword,
                        @Param("deviceTypeId") String deviceTypeId,
                        @Param("areaId") String areaId,
                        @Param("status") DeviceStatus status,
                        Sort sort);
}
