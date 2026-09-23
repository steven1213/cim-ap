package com.cim.jpa.it;

import com.cim.jpa.support.BaseRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocRevisionRepository extends BaseRepository<DocRevision, String> {
}
