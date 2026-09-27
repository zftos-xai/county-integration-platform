package cn.zqkj.platform.system.configuration.service.impl;

import cn.zqkj.platform.system.configuration.domain.model.ExternalSystemInboundCredential;
import cn.zqkj.platform.system.configuration.mapper.ConfigurationMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证县医院ID/Key认证只识别已登记且启用的系统身份。 */
class ExternalSystemInboundKeyAuthenticatorImplTest {

    /** 验证系统代码忽略首尾空白和大小写，认证成功只返回系统身份。 */
    @Test
    void authenticatesEnabledSystemByNormalizedCodeAndKey() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.findExternalSystemInboundCredential("COUNTY_HOSPITAL"))
                .thenReturn(Optional.of(new ExternalSystemInboundCredential(
                        "COUNTY_HOSPITAL", "县医院", true, "bcrypt-hash")));
        when(encoder.matches("presented-key", "bcrypt-hash")).thenReturn(true);
        var authenticator = new ExternalSystemInboundKeyAuthenticatorImpl(mapper, encoder);

        var caller = authenticator.authenticate(" county_hospital ", "presented-key");

        assertTrue(caller.isPresent());
        assertEquals("COUNTY_HOSPITAL", caller.orElseThrow().systemCode());
        assertEquals("县医院", caller.orElseThrow().systemName());
        verify(encoder).matches("presented-key", "bcrypt-hash");
    }

    /** 验证空凭证、停用系统和错误Key均不能建立调用方身份。 */
    @Test
    void rejectsMissingDisabledOrIncorrectCredential() {
        ConfigurationMapper mapper = mock(ConfigurationMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.findExternalSystemInboundCredential("DISABLED"))
                .thenReturn(Optional.of(new ExternalSystemInboundCredential("DISABLED", "停用系统", false, "hash")));
        when(mapper.findExternalSystemInboundCredential("ENABLED"))
                .thenReturn(Optional.of(new ExternalSystemInboundCredential("ENABLED", "启用系统", true, "hash")));
        when(encoder.matches("wrong-key", "hash")).thenReturn(false);
        var authenticator = new ExternalSystemInboundKeyAuthenticatorImpl(mapper, encoder);

        assertTrue(authenticator.authenticate("ENABLED", " ").isEmpty());
        assertTrue(authenticator.authenticate("DISABLED", "presented-key").isEmpty());
        assertTrue(authenticator.authenticate("ENABLED", "wrong-key").isEmpty());
    }
}
