package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

import com.example.demo.model.OcrResult;

public interface OcrResultRepository extends CrudRepository<OcrResult, Long> {
}



