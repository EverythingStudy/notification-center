package com.notification.handler;

import com.notification.model.enums.ReadStatusEnum;
import org.apache.ibatis.type.EnumOrdinalTypeHandler;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(ReadStatusEnum.class)
public class ReadStatusTypeHandler extends EnumOrdinalTypeHandler<ReadStatusEnum> {

    public ReadStatusTypeHandler() {
        super(ReadStatusEnum.class);
    }
}
