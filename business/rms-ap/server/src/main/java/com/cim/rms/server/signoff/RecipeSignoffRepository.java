package com.cim.rms.server.signoff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 签核单仓储。 */
public interface RecipeSignoffRepository extends JpaRepository<RecipeSignoff, String> {

    List<RecipeSignoff> findByDeletedFalseOrderByCreateTimeDesc();

    List<RecipeSignoff> findByDeletedFalseAndStatusOrderByCreateTimeDesc(SignoffStatus status);

    /** 某版本的指定状态签核单（判定是否已发起/是否仍有待审批）。 */
    Optional<RecipeSignoff> findFirstByVersionIdAndStatusAndDeletedFalse(String versionId, SignoffStatus status);

    /** 某版本处于任一给定状态的签核单（用于阻止重复发起）。 */
    Optional<RecipeSignoff> findFirstByVersionIdAndStatusInAndDeletedFalse(String versionId,
                                                                          Collection<SignoffStatus> statuses);
}
