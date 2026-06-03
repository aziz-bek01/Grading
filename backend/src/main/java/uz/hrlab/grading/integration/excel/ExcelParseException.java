package uz.hrlab.grading.integration.excel;

import uz.hrlab.grading.common.exception.BaseDomainException;

public class ExcelParseException extends BaseDomainException {
    public ExcelParseException(String code, String safeMessage) {
        super(code, safeMessage);
    }
}
