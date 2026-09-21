package cn.zqkj.platform.his.domain.protocol.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证基层HIS响应只能表示明确成功或明确业务失败，不能形成矛盾状态。
 */
class PhisResponseTest {

    /** 验证成功工厂只产生含数据且不含错误说明的响应。 */
    @Test
    void createsConsistentSuccessResponse() {
        PhisResponse<List<String>> response = PhisResponse.success("1", List.of("data"));

        assertTrue(response.success());
        assertEquals(List.of("data"), response.data());
        assertNull(response.errorMessage());
    }

    /** 验证失败工厂只产生含错误说明且不含业务数据的响应。 */
    @Test
    void createsConsistentFailureResponse() {
        PhisResponse<List<String>> response = PhisResponse.failure("0", "HIS拒绝请求");

        assertTrue(!response.success());
        assertNull(response.data());
        assertEquals("HIS拒绝请求", response.errorMessage());
    }

    /** 验证构造器拒绝成功无数据、成功带错误和失败带数据等矛盾状态。 */
    @Test
    void rejectsContradictoryResponseState() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhisResponse<>(true, "1", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PhisResponse<>(true, "1", List.of(), "错误"));
        assertThrows(IllegalArgumentException.class,
                () -> new PhisResponse<>(false, "0", List.of(), "错误"));
        assertThrows(IllegalArgumentException.class,
                () -> new PhisResponse<>(false, "0", null, " "));
    }
}
