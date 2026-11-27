package com.cim.mds.server.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 机台组成员仓库。 */
public interface CimToolGroupMemberRepository extends JpaRepository<CimToolGroupMember, String> {

    List<CimToolGroupMember> findByGroupIdAndDeletedFalse(String groupId);

    boolean existsByGroupIdAndEquipmentIdAndDeletedFalse(String groupId, String equipmentId);

    void deleteByGroupId(String groupId);

    void deleteByEquipmentId(String equipmentId);
}
