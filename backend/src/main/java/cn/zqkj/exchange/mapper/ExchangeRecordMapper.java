package cn.zqkj.exchange.mapper;

import cn.zqkj.exchange.model.ExchangeRecordSummary;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExchangeRecordMapper {

    List<ExchangeRecordSummary> findRecentByOrganization(
            @Param("organizationCode") String organizationCode,
            @Param("limit") int limit
    );
}
