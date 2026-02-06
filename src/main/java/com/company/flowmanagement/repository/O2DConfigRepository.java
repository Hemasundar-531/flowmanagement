package com.company.flowmanagement.repository;

import com.company.flowmanagement.model.O2DConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface O2DConfigRepository extends MongoRepository<O2DConfig, String> {
}
