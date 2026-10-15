package com.cim.rms.server.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** IF-E2 下发任务仓储。 */
public interface RecipeDownloadJobRepository extends JpaRepository<RecipeDownloadJob, String> {

    Optional<RecipeDownloadJob> findFirstByCorrelationIdAndDeletedFalse(String correlationId);

    List<RecipeDownloadJob> findByDeletedFalseOrderByCreateTimeDesc();

    List<RecipeDownloadJob> findByStageAndDeletedFalseOrderByCreateTimeDesc(DownloadStage stage);

    List<RecipeDownloadJob> findByBatchIdAndDeletedFalseOrderByCreateTimeDesc(String batchId);

    List<RecipeDownloadJob> findByDeviceIdAndDeletedFalseOrderByCreateTimeDesc(String deviceId);

    /** 按机台 + PPID 定位任务（PP_VERIFICATION_FAILED 事件据此把在途下发判失败）。 */
    List<RecipeDownloadJob> findByDeviceIdAndPpidOnToolAndDeletedFalseOrderByCreateTimeDesc(
            String deviceId, String ppidOnTool);

    /** 未闭环任务（非终态），供「待完成下发」看板。 */
    List<RecipeDownloadJob> findByStageNotInAndDeletedFalseOrderByCreateTimeDesc(List<DownloadStage> stages);
}
