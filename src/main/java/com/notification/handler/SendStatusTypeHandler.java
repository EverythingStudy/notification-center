package com.notification.handler;

import com.notification.model.enums.SendStatusEnum;
import org.apache.ibatis.type.EnumOrdinalTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(SendStatusEnum.class)
public class SendStatusTypeHandler extends EnumOrdinalTypeHandler<SendStatusEnum> {

    public SendStatusTypeHandler() {
        super(SendStatusEnum.class);
    }
}
