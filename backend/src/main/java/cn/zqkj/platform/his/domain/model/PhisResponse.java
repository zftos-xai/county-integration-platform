package cn.zqkj.platform.his.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 基层HIS {@code PHIS_Interface}统一业务响应。
 *
 * @param success {@code result/Result}为1时为真
 * @param resultCode 规范化后的目标结果码，目前只接受0或1
 * @param message {@code msg/Msg}业务消息；目标未返回时为空JSON节点
 */
public record PhisResponse(boolean success, String resultCode, JsonNode message) {
}
