package com.registrarops.repository;

import com.registrarops.entity.SearchTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchTermRepository extends JpaRepository<SearchTerm, Long> {
    Optional<SearchTerm> findByTerm(String term);
    List<SearchTerm> findTop10ByOrderBySearchCountDesc();
}
