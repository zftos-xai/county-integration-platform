package cn.zqkj.platform.his.client;

import cn.zqkj.platform.his.domain.model.PhisResponse;

import java.net.URI;

/**
 * 调用基层HIS {@code PHIS_Interface}的协议适配边界。
 */
public interface PhisClient {

    /**
     * 发送一次SOAP交易。
     *
     * @param serviceUri WSDL公布的WebService地址，不含操作查询参数
     * @param connectTimeoutMs 连接超时毫秒
     * @param readTimeoutMs 读取超时毫秒
     * @param tradeCode 正式交易码
     * @param inputParameter JSON业务参数
     * @return 已完成协议校验的业务响应
     */
    PhisResponse invoke(URI serviceUri, int connectTimeoutMs, int readTimeoutMs,
                        String tradeCode, String inputParameter);
}
