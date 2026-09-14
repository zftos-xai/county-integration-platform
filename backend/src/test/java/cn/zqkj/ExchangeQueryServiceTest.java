package cn.zqkj;

import cn.zqkj.exchange.repository.ExchangeRecordRepository;
import cn.zqkj.exchange.service.ExchangeQueryService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExchangeQueryServiceTest {

    @Test
    void limitsPageSizeToOneHundred() {
        ExchangeRecordRepository repository = mock(ExchangeRecordRepository.class);
        ExchangeQueryService service = new ExchangeQueryService(repository);

        service.findRecent("ORG001", 500);

        verify(repository).findRecent("ORG001", 100);
    }
}
