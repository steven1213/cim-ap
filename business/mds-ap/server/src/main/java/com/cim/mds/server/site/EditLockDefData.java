package com.cim.mds.server.site;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 位置主数据编辑锁基类——五层位置表（Site/Fab/Area/Bay/Stage）统一继承。
 *
 * <p><b>语义是「人 vs 人」的编辑互斥，不是引用保护</b>（location-design.md §8.1）：
 * 修改前先判 {@code is_occupied}——有值且占用者不是本人则拒绝编辑；
 * 保存成功后三列在<b>同一事务</b>内清除；取消/关闭编辑弹窗由前端显式释放；
 * 异常残留靠 {@code occupied_at} 超时（默认 30 分钟，{@link EditLockProperties}）自动过期。</p>
 *
 * <p>放 MDS 域内而非平台 {@link BaseDefData}：编辑锁目前只有位置主数据需要，
 * 不污染其他域的定义数据。</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class EditLockDefData extends BaseDefData {

    /** 该条正被某人编辑（编辑锁）。 */
    @Column(name = "is_occupied", nullable = false)
    private Boolean occupied = Boolean.FALSE;

    /** 占用者账号（IAM username）。 */
    @Column(name = "occupied_by", length = 64)
    private String occupiedBy;

    /** 抢锁时间（超时判定依据）。 */
    @Column(name = "occupied_at")
    private LocalDateTime occupiedAt;
}
