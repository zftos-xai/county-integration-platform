package cn.zqkj.exchange.repository;

import cn.zqkj.exchange.mapper.ExchangeRecordMapper;
import cn.zqkj.exchange.model.ExchangeRecordSummary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ExchangeRecordRepository {

    private final ExchangeRecordMapper mapper;

    public ExchangeRecordRepository(ExchangeRecordMapper mapper) {
        this.mapper = mapper;
    }

    public List<ExchangeRecordSummary> findRecent(String organizationCode, int limit) {
        return mapper.findRecentByOrganization(organizationCode, limit);
    }
}
