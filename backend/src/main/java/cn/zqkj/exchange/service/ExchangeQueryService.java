package cn.zqkj.exchange.service;

import cn.zqkj.exchange.model.ExchangeRecordSummary;
import cn.zqkj.exchange.repository.ExchangeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExchangeQueryService {

    private final ExchangeRecordRepository repository;

    public ExchangeQueryService(ExchangeRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ExchangeRecordSummary> findRecent(String organizationCode, int limit) {
        return repository.findRecent(organizationCode, Math.min(limit, 100));
    }
}
