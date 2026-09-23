package cn.zqkj.platform.masterdata.service.icd10;

import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10SourceRecord;
import cn.zqkj.platform.masterdata.domain.icd10.model.Icd10ValidationResult;
import java.util.List;

/**
 * 定义公共ICD10来源记录进入当前目录前的数量与质量校验边界。
 */
public interface Icd10ValidationService {

    /**
     * 核对100-007声明数量、100-006实际取得数量及单类别来源记录质量。
     *
     * <p>任何不一致都阻止该类别进入后续写入；本方法不推断跨同步唯一键，也不写入数据库。</p>
     *
     * @param declaredCount 100-007在完全相同查询范围内声明的行数
     * @param records 100-006分页取得的全部记录；空值按空列表处理
     * @return 已接受记录和受控校验结论
     */
    Icd10ValidationResult validate(long declaredCount, List<Icd10SourceRecord> records);
}
