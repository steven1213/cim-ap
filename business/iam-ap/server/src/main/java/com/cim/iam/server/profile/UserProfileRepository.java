package com.cim.iam.server.profile;

import com.cim.iam.server.common.DataOrigin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

    Optional<UserProfile> findByUserId(String userId);

    List<UserProfile> findByUserIdIn(Collection<String> userIds);

    List<UserProfile> findBySource(DataOrigin source);
}
